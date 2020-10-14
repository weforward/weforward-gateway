/**
 * Copyright (c) 2019,2020 honintech
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package cn.weforward.gateway.ops.access.system;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.GcCleanable;
import cn.weforward.common.ResultPage;
import cn.weforward.common.crypto.Hex;
import cn.weforward.common.sys.GcCleaner;
import cn.weforward.common.util.LruCache;
import cn.weforward.common.util.LruCache.CacheNode;
import cn.weforward.common.util.LruCache.Loader;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.TransResultPage;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.gateway.ops.access.AccessManage;
import cn.weforward.gateway.ops.access.AccessProvider;
import cn.weforward.gateway.util.SimpleIdGenerator;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.ops.AccessExt;

/**
 * {@linkplain Access#KIND_SERVICE}的Access提供者
 * 
 * @author zhangpengji
 *
 */
public class ServiceAccessProvider implements AccessProvider, PluginListener, ServiceAccessVoFactory.ReloadListener,
		MasterKeyVoFactory.ReloadListener, GcCleanable {
	static final Logger _Logger = LoggerFactory.getLogger(ServiceAccessProvider.class);

	LruCache<String, ServiceAccess> m_SystemAccessCache;
	LruCache.Loader<String, ServiceAccess> m_SystemAccessLoader;
	volatile Map<String, MasterKey> m_MasterKeys;
	Object m_MasterKeysLock = new Object();
	SimpleIdGenerator m_IdGenerator;

	ServiceAccessVoFactory m_AccessVoFactory;
	MasterKeyVoFactory m_MasterKeyVoFactory;

	public ServiceAccessProvider(String gatewayId) {
		m_SystemAccessCache = new LruCache<>("access");
		m_SystemAccessCache.setReachable(true);
		m_SystemAccessCache.setTimeout(24 * 60 * 60);
		m_SystemAccessCache.setNullTimeout(5);

		m_SystemAccessLoader = new Loader<String, ServiceAccess>() {

			@Override
			public ServiceAccess load(String key, CacheNode<String, ServiceAccess> node) {
				ServiceAccessVoFactory accessVoFactory = m_AccessVoFactory;
				MasterKeyVoFactory masterKeyVoFactory = m_MasterKeyVoFactory;
				if (null == accessVoFactory || null == masterKeyVoFactory) {
					throw new IllegalStateException("未初始化VoFactory:" + m_AccessVoFactory + "," + m_MasterKeyVoFactory);
				}
				ServiceAccessVo vo = m_AccessVoFactory.get(key);
				if (null == vo) {
					return null;
				}
				return new ServiceAccess(ServiceAccessProvider.this, vo);
			}
		};

		m_IdGenerator = new SimpleIdGenerator(gatewayId);
		
		GcCleaner.register(this);
	}

	public void setPluginContainer(PluginContainer container) {
		container.register(this);
	}

	public void setAccessManage(AccessManage am) {
		am.registerProvider(this);
	}

	@Override
	public String getKind() {
		return Access.KIND_SERVICE;
	}

	@Override
	public void onPluginLoad(Pluginable plugin) {
		if (plugin instanceof ServiceAccessVoFactory) {
			m_AccessVoFactory = (ServiceAccessVoFactory) plugin;
			m_AccessVoFactory.registerReloadListener(this);
		} else if (plugin instanceof MasterKeyVoFactory) {
			m_MasterKeyVoFactory = (MasterKeyVoFactory) plugin;
			m_MasterKeyVoFactory.registerReloadListener(this);
		}
	}

	@Override
	public void onPluginUnload(Pluginable plugin) {
		if (plugin instanceof ServiceAccessVoFactory) {
			m_AccessVoFactory = null;
		} else if (plugin instanceof MasterKeyVoFactory) {
			m_MasterKeyVoFactory = null;
		}
	}

	@Override
	public AccessExt createAccess(String groupId) {
		if (StringUtil.isEmpty(groupId)) {
			throw new IllegalArgumentException("'groupId'不能为空");
		}
		MasterKey mk;
		try {
			mk = openMasterKey(groupId);
		} catch (Exception e) {
			throw new IllegalStateException("创建主密钥失败，groupId:" + groupId, e);
		}
		// 先创建vo
		String ordinal = m_IdGenerator.genId(null);
		String id = genAccessId(getKind(), mk, ordinal);
		if (null != m_AccessVoFactory.get(id)) {
			throw new IllegalStateException("Access已存在：" + id);
		}
		ServiceAccessVo vo = new ServiceAccessVo();
		vo.id = id;
		vo.createTime = new Date();
		m_AccessVoFactory.put(vo);
		// 再由缓存加载
		return getAccessInner(id);
	}

	static String genAccessId(String kind, MasterKey mk, String ordinal) {
		short hash = genHash(ordinal, mk.getKey());
		String mid = mk.getId();
		int len = 37;// kind.length() + 1 + mid.length() + 1 + 4 + ordinal.length()
		StringBuilder sb = new StringBuilder(len);
		sb.append(kind).append(Access.SPEARATOR_STR).append(mid).append(Access.SPEARATOR_STR);
		Hex.toHexFixed(hash, sb);
		sb.append(ordinal);
		return sb.toString();
	}

	static final short genHash(String ordinal, byte[] secret) {
		int init = 0;
		byte[] data = ordinal.getBytes(StandardCharsets.UTF_8);
		for (int i = 0; i < data.length + secret.length; i++) {
			byte b = (i < data.length) ? data[i] : secret[i - data.length];
			init ^= (b & 0xff);

			for (int j = 0; j < 8; j++) {
				int flag = init & 0x0001;
				init = init >> 1;
				if (flag == 1)
					init ^= 0xa001;
			}
		}
		return (short) (init ^ 0);
	}

	boolean verifyAccessId(String id) {
		if (null == id || id.length() < 27 || id.length() > 37) {
			return false;
		}
		int sp1 = id.indexOf(Access.SPEARATOR);
		if (-1 == sp1) {
			return false;
		}
		int sp2 = id.indexOf(Access.SPEARATOR, sp1 + 1);
		if (-1 == sp2 || sp2 + 13 > id.length()) {
			return false;
		}
		MasterKey mk = getMasterKey(id.substring(sp1 + 1, sp2));
		if (null == mk) {
			return false;
		}

		if (Configure.getInstance().isNotVerifyAccessId()) {
			// 不校验hash
			return true;
		}
		int hash = Hex.parseHex(id, sp2 + 1, sp2 + 5, Integer.MAX_VALUE);
		if (Integer.MAX_VALUE == hash) {
			return false;
		}
		String ordinal = id.substring(sp2 + 5);
		if ((short) hash != genHash(ordinal, mk.getKey())) {
			return false;
		}
		return true;
	}

	public MasterKey openMasterKey(String groupId) throws NoSuchAlgorithmException {
		synchronized (m_MasterKeysLock) {
			MasterKey key = getMasterKeyByGroupId(groupId);
			if (null == key) {
				String id = m_IdGenerator.genId(null);
				if (null != m_MasterKeyVoFactory.get(id)) {
					throw new IllegalStateException("MasterKey已存在：" + id);
				}
				key = new MasterKey(m_MasterKeyVoFactory, id, groupId);
				m_MasterKeys.put(key.getId(), key);
			}
			return key;
		}
	}

	public MasterKey getMasterKey(String id) {
		initMasterKeys();
		return m_MasterKeys.get(id);
	}

	public MasterKey getMasterKeyByAccessId(String id) {
		int sp1 = id.indexOf(Access.SPEARATOR);
		if (-1 == sp1) {
			return null;
		}
		int sp2 = id.indexOf(Access.SPEARATOR, sp1 + 1);
		if (-1 == sp2) {
			return null;
		}
		return getMasterKey(id.substring(sp1 + 1, sp2));
	}

	public MasterKey getMasterKeyByGroupId(String groupId) {
		initMasterKeys();

		if (StringUtil.isEmpty(groupId)) {
			return null;
		}
		// XXX 主密钥不会太多，直接遍历
		for (MasterKey k : m_MasterKeys.values()) {
			if (k.isValid() && StringUtil.eq(k.getGroupId(), groupId)) {
				return k;
			}
		}
		return null;
	}

	private void initMasterKeys() {
		if (null == m_MasterKeys) {
			synchronized (m_MasterKeysLock) {
				if (null == m_MasterKeys) {
					Map<String, MasterKey> keys = new HashMap<>();
					ResultPage<MasterKeyVo> rp = m_MasterKeyVoFactory.startsWith(null);
					for (MasterKeyVo vo : ResultPageHelper.toForeach(rp)) {
						MasterKey mk = new MasterKey(m_MasterKeyVoFactory, vo);
						keys.put(mk.getId(), mk);
					}
					m_MasterKeys = keys;
				}
			}
		}
	}

	@Override
	public AccessExt getAccess(String id) {
		ServiceAccess acc = m_SystemAccessCache.get(id);
		if (null != acc) {
			return acc;
		}
		// 校验id是否有效
		if (!verifyAccessId(id)) {
			return null;
		}
		return getAccessInner(id);
	}

	AccessExt getAccessInner(String id) {
		return m_SystemAccessCache.getHintLoad(id, m_SystemAccessLoader);
	}

	@Override
	public ResultPage<AccessExt> listAccess(String groupId, String keyword) {
		StringBuilder prefix = new StringBuilder();
		prefix.append(getKind()).append(Access.SPEARATOR);
		if (!StringUtil.isEmpty(groupId)) {
			MasterKey mk = getMasterKeyByGroupId(groupId);
			if (null == mk) {
				return ResultPageHelper.empty();
			}
			prefix.append(mk.getId()).append(Access.SPEARATOR);
		}

		ResultPage<String> ids = m_AccessVoFactory.startsWithOfId(prefix.toString());
		ResultPage<AccessExt> accesses = new TransResultPage<AccessExt, String>(ids) {

			@Override
			protected AccessExt trans(String src) {
				return getAccessInner(src);
			}
		};
		if (StringUtil.isEmpty(keyword)) {
			return accesses;
		}
		List<AccessExt> list = new ArrayList<>();
		for (AccessExt acc : ResultPageHelper.toForeach(accesses)) {
			String summary = acc.getSummary();
			if (StringUtil.isEmpty(summary) || !summary.contains(keyword)) {
				continue;
			}
			list.add(acc);
		}
		return ResultPageHelper.toResultPage(list);
	}

	@Override
	public List<String> listAccessGroup() {
		initMasterKeys();
		if (0 == m_MasterKeys.size()) {
			return Collections.emptyList();
		}
		List<String> groups = new ArrayList<String>(m_MasterKeys.size());
		for (MasterKey mk : m_MasterKeys.values()) {
			groups.add(mk.getGroupId());
		}
		return groups;
	}

	@Override
	public void onReload(ServiceAccessVo accessVo) {
		if (_Logger.isTraceEnabled()) {
			_Logger.trace("onReload:" + accessVo.id);
		}
		ServiceAccess acc = m_SystemAccessCache.get(accessVo.id);
		if (null == acc) {
			// 不在缓存，略过
			return;
		}
		acc.updateVo(accessVo);
	}

	@Override
	public void onReload(MasterKeyVo vo) {
		if (_Logger.isTraceEnabled()) {
			_Logger.trace("onReload:" + vo.id);
		}

		initMasterKeys();
		synchronized (m_MasterKeysLock) {
			MasterKey mk = m_MasterKeys.get(vo.id);
			if (null == mk) {
				mk = new MasterKey(m_MasterKeyVoFactory, vo);
				m_MasterKeys.put(mk.getId(), mk);
			} else {
				mk.update(vo);
			}
		}
	}

	@Override
	public void onGcCleanup(int policy) {
		if (POLICY_CRITICAL != policy) {
			return;
		}
		if (null != m_SystemAccessCache) {
			m_SystemAccessCache.onGcCleanup(policy);
		}
	}
}
