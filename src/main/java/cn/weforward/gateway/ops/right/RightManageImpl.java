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
package cn.weforward.gateway.ops.right;

import cn.weforward.common.GcCleanable;
import cn.weforward.common.sys.GcCleaner;
import cn.weforward.common.util.LruCache;
import cn.weforward.common.util.LruCache.CacheNode;
import cn.weforward.common.util.LruCache.Loader;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.gateway.Tunnel;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.AccessLoader;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.exception.AuthException;
import cn.weforward.protocol.exception.InvokeDeniedException;
import cn.weforward.protocol.ops.AccessExt;

/**
 * <code>RightManage</code>实现
 * 
 * @author zhangpengji
 *
 */
public class RightManageImpl implements RightManage, PluginListener, RightTableVoFactory.ReloadListener<RightTableVo>, GcCleanable {

	InternalRightTable m_KeeperTable;
	InternalRightTable m_DistributedTable;
	InternalRightTable m_MeshTable;
	// Map<String, RightTableExt> m_InternalRights;
	LruCache<String, RightTable> m_RightTableCache;
	LruCache.Loader<String, RightTable> m_RightTableLoader;

	AccessLoader m_AccessLoader;
	RightTableVoFactory m_VoFactory;

	public RightManageImpl() {
		// 内置权限
		m_KeeperTable = genKeeperRightTable();
		m_DistributedTable = genDistributedRightTable();
		m_MeshTable = genMeshRightTable();
		// m_InternalRights = new HashMap<>();
		// m_InternalRights.put(keeperTable.getName(), keeperTable);
		// m_InternalRights.put(distributed.getName(), distributed);

		m_RightTableCache = new LruCache<>("right_table");
		m_RightTableCache.setReachable(true);
		m_RightTableCache.setTimeout(24 * 60 * 60);
		m_RightTableCache.setNullTimeout(10);

		m_RightTableLoader = new Loader<String, RightTable>() {

			@Override
			public RightTable load(String key, CacheNode<String, RightTable> node) {
				RightTableVoFactory factory = m_VoFactory;
				if (null == factory) {
					_Logger.warn("未初始化VoFactory");
					return null;
				}
				RightTableVo vo = factory.get(key);
				if (null == vo) {
					return null;
				}
				return new RightTable(factory, vo);
			}
		};

		GcCleaner.register(this);
	}

	private InternalRightTable genKeeperRightTable() {
		InternalRightTable table = new InternalRightTable(this, ServiceName.KEEPER.name);
		table.addInternalItem(AccessExt.GATEWAY_INTERNAL_ACCESS_ID);
		return table;
	}

	private InternalRightTable genDistributedRightTable() {
		InternalRightTable table = new InternalRightTable(this, ServiceName.DISTRIBUTED.name);
		table.addInternalItem(AccessExt.GATEWAY_INTERNAL_ACCESS_ID);
		return table;
	}

	private InternalRightTable genMeshRightTable() {
		InternalRightTable table = new InternalRightTable(this, ServiceName.MESH.name);
		table.addInternalItem(AccessExt.GATEWAY_INTERNAL_ACCESS_ID);
		return table;
	}

	public void setAccessLoader(AccessLoader loader) {
		m_AccessLoader = loader;
	}

	public void setPluginContainer(PluginContainer container) {
		container.register(this);
	}

	@Override
	public void onPluginLoad(Pluginable plugin) {
		if (plugin instanceof RightTableVoFactory) {
			m_VoFactory = (RightTableVoFactory) plugin;
			m_VoFactory.registerReloadListener(this);
		}
	}

	@Override
	public void onPluginUnload(Pluginable plugin) {
		if (plugin instanceof RightTableVoFactory) {
			m_VoFactory = null;
		}
	}

	@Override
	public void verifyAccess(Header header) throws AuthException {
		String service = header.getService();
		RightTableExt table = getRightTable(service);
		if (null != table) {
			Access access = null;
			String accessId = header.getAccessId();
			if (!StringUtil.isEmpty(accessId)) {
				access = m_AccessLoader.getValidAccess(accessId);
			}
			if (table.isAllow(access)) {
				// 允许调用
				return;
			}
		}

		throw new InvokeDeniedException("拒绝调用此服务[" + header.getService() + "]");
	}

	@Override
	public void verifyAccess(Tunnel tunnel) throws AuthException {
		if (tunnel.isFromGatewayInternal()) {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace("内部请求，略过权限检查：" + tunnel);
			}
			return;
		}
		verifyAccess(tunnel.getHeader());
	}

	@Override
	public RightTableExt openRightTable(String serviceName) {
		if (m_DistributedTable.getName().equals(serviceName)) {
			return m_DistributedTable;
		}
		if (m_KeeperTable.getName().equals(serviceName)) {
			return m_KeeperTable;
		}
		return openRightTableInner(serviceName);
	}

	protected RightTable openRightTableInner(String serviceName) {
		if (StringUtil.isEmpty(serviceName)) {
			return null;
		}
		String id = RightTable.genId(serviceName);
		RightTable table = getRightTableById(id);
		if (null != table) {
			return table;
		}
		synchronized (this) {
			table = getRightTableById(id);
			if (null != table) {
				return table;
			}
			// 先创建vo
			RightTableVo vo = new RightTableVo();
			vo.id = id;
			vo.name = serviceName;
			m_VoFactory.put(vo);
		}
		// 再由缓存加载
		// return getRightTableById(id);
		return m_RightTableCache.getAndLoad(id, m_RightTableLoader, 0);
	}

	@Override
	public RightTableExt getRightTable(String serviceName) {
		if (StringUtil.isEmpty(serviceName)) {
			return null;
		}

		if (m_DistributedTable.getName().equals(serviceName)) {
			return m_DistributedTable;
		}
		if (m_MeshTable.getName().equals(serviceName)) {
			return m_MeshTable;
		}
		if (m_KeeperTable.getName().equals(serviceName)) {
			return m_KeeperTable;
		}

		String id = RightTable.genId(serviceName);
		RightTableExt table = getRightTableById(id);
		return table;
	}

	public RightTable getRightTableById(String id) {
		return m_RightTableCache.getHintLoad(id, m_RightTableLoader);
	}

	@Override
	public void onReload(RightTableVo rightTableVo) {
		if (_Logger.isTraceEnabled()) {
			_Logger.trace("onReload:" + rightTableVo.id);
		}
		RightTable table = m_RightTableCache.get(rightTableVo.id);
		if (null == table) {
			// 不在缓存，略过
			return;
		}
		table.updateVo(rightTableVo);
	}

	@Override
	public void onGcCleanup(int policy) {
		if (POLICY_CRITICAL != policy) {
			return;
		}
		if (null != m_RightTableCache) {
			m_RightTableCache.onGcCleanup(policy);
		}
	}

}
