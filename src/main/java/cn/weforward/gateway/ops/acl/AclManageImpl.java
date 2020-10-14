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
package cn.weforward.gateway.ops.acl;

import cn.weforward.common.GcCleanable;
import cn.weforward.common.sys.GcCleaner;
import cn.weforward.common.util.LruCache;
import cn.weforward.common.util.LruCache.CacheNode;
import cn.weforward.common.util.LruCache.Loader;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.protocol.Access;

/**
 * <code>AclManage</code>实现
 * 
 * @author zhangpengji
 *
 */
public class AclManageImpl implements AclManage, PluginListener, AclTableVoFactory.ReloadListener, GcCleanable {

	LruCache<String, AclTable> m_AclTableCache;
	LruCache.Loader<String, AclTable> m_AclTableLoader;

	AclTableVoFactory m_VoFactory;

	public AclManageImpl() {
		m_AclTableCache = new LruCache<>("acl_table");
		m_AclTableCache.setReachable(true);
		m_AclTableCache.setTimeout(60 * 60);
		m_AclTableCache.setNullTimeout(5);

		m_AclTableLoader = new Loader<String, AclTable>() {

			@Override
			public AclTable load(String key, CacheNode<String, AclTable> node) {
				AclTableVoFactory factory = m_VoFactory;
				if (null == factory) {
					_Logger.warn("初始化VoFactory");
					return null;
				}
				AclTableVo vo = factory.get(key);
				if (null == vo) {
					return null;
				}
				return new AclTable(factory, vo);
			}
		};

		GcCleaner.register(this);
	}

	public void setPluginContainer(PluginContainer container) {
		container.register(this);
	}

	@Override
	public void onPluginLoad(Pluginable plugin) {
		if (plugin instanceof AclTableVoFactory) {
			m_VoFactory = (AclTableVoFactory) plugin;
			m_VoFactory.registerReloadListener(this);
		}
	}

	@Override
	public void onPluginUnload(Pluginable plugin) {
		if (plugin instanceof AclTableVoFactory) {
			m_VoFactory = null;
		}
	}

	@Override
	public AclTable openAclTable(String serviceName) {
		if (null == serviceName) {
			throw new IllegalArgumentException("服务名不能为空");
		}
		AclTable table = getAclTableByName(serviceName);
		if (null != table) {
			return table;
		}
		String id;
		synchronized (this) {
			// double check
			table = getAclTableByName(serviceName);
			if (null != table) {
				return table;
			}
			// 先创建vo
			AclTableVo vo = new AclTableVo();
			vo.id = AclTable.genId(serviceName);
			vo.name = serviceName;
			m_VoFactory.put(vo);
			id = vo.id;
		}
		// 再由缓存加载
		// return getAclTableByNameById(id);
		return m_AclTableCache.getAndLoad(id, m_AclTableLoader, 0);
	}

	@Override
	public AclTable getAclTableByName(String serviceName) {
		if (StringUtil.isEmpty(serviceName)) {
			return null;
		}
		String id = AclTable.genId(serviceName);
		return getAclTableByNameById(id);
	}

	public AclTable getAclTableByNameById(String id) {
		return m_AclTableCache.getHintLoad(id, m_AclTableLoader);
	}

	@Override
	public int findResourceRight(String serviceName, Access access, String resId) {
		AclTable table = getAclTableByName(serviceName);
		if (null == table) {
			return 0;
		}
		return table.findResourceRight(access, resId);
	}

	@Override
	public void onReload(AclTableVo aclTableVo) {
		if (_Logger.isTraceEnabled()) {
			_Logger.trace("onReload:" + aclTableVo.id);
		}
		AclTable table = m_AclTableCache.get(aclTableVo.id);
		if (null == table) {
			// 不在缓存，略过
			return;
		}
		table.updateVo(aclTableVo);
	}

	@Override
	public void onGcCleanup(int policy) {
		if (POLICY_CRITICAL != policy) {
			return;
		}
		if (null != m_AclTableCache) {
			m_AclTableCache.onGcCleanup(policy);
		}
	}

}
