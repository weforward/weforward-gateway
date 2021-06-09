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
package cn.weforward.gateway.ops.traffic;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import cn.weforward.common.GcCleanable;
import cn.weforward.common.sys.GcCleaner;
import cn.weforward.common.util.LruCache;
import cn.weforward.common.util.LruCache.CacheNode;
import cn.weforward.common.util.LruCache.Loader;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.ops.traffic.TrafficTableItem;

/**
 * <code>TrafficManage</code>实现
 * 
 * @author zhangpengji
 *
 */
public class TrafficManageImpl
		implements TrafficManage, PluginListener, TrafficTableVoFactory.ReloadListener<TrafficTableVo>, GcCleanable {

	LruCache<String, TrafficTable> m_TrafficTableCache;
	LruCache.Loader<String, TrafficTable> m_TrafficTableLoader;

	List<TrafficListener> m_TrafficListeners;
	TrafficTableVoFactory m_VoFactory;

	public TrafficManageImpl() {
		m_TrafficTableCache = new LruCache<>(1000, "traffic_table");
		m_TrafficTableCache.setReachable(true);
		m_TrafficTableCache.setTimeout(60 * 60);
		m_TrafficTableCache.setNullTimeout(5);

		m_TrafficTableLoader = new Loader<String, TrafficTable>() {

			@Override
			public TrafficTable load(String key, CacheNode<String, TrafficTable> node) {
				TrafficTableVoFactory factory = m_VoFactory;
				if (null == factory) {
					_Logger.warn("未初始化VoFactory");
					return null;
				}
				TrafficTableVo vo = factory.get(key);
				if (null == vo) {
					if (_Logger.isTraceEnabled()) {
						_Logger.trace("找不到：" + key);
					}
					return null;
				}
				return new TrafficTable(TrafficManageImpl.this, factory, vo);
			}
		};

		m_TrafficListeners = new CopyOnWriteArrayList<>();

		GcCleaner.register(this);
	}

	public void setPluginContainer(PluginContainer container) {
		container.register(this);
	}

	@Override
	public void onPluginLoad(Pluginable plugin) {
		if (plugin instanceof TrafficTableVoFactory) {
			m_VoFactory = (TrafficTableVoFactory) plugin;
			m_VoFactory.registerReloadListener(this);
		}
	}

	@Override
	public void onPluginUnload(Pluginable plugin) {
		if (plugin instanceof TrafficTableVoFactory) {
			m_VoFactory = null;
		}
	}

	@Override
	public TrafficTableItem findTrafficRule(Service service) {
		TrafficTable table = getTrafficTable(service.getName());
		if (null != table) {
			return table.findRule(service.getNo(), service.getVersion());
		}
		// 默认使用轮询方式
		return TrafficTableItem.DEFAULT;
	}

	@Override
	public TrafficTable openTrafficTable(String serviceName) {
		if (null == serviceName) {
			throw new IllegalArgumentException("服务名不能为空");
		}
		TrafficTable table = getTrafficTable(serviceName);
		if (null != table) {
			return table;
		}
		String id;
		synchronized (this) {
			// double check
			table = getTrafficTable(serviceName);
			if (null != table) {
				return table;
			}
			// 先创建vo
			TrafficTableVo vo = new TrafficTableVo();
			vo.id = TrafficTable.genId(serviceName);
			vo.name = serviceName;
			m_VoFactory.put(vo);
			id = vo.id;
		}
		// 再由缓存加载
		// return getTrafficTableById(id);
		return m_TrafficTableCache.getAndLoad(id, m_TrafficTableLoader, 0);
	}

	@Override
	public TrafficTable getTrafficTable(String serviceName) {
		if (StringUtil.isEmpty(serviceName)) {
			return null;
		}
		String id = TrafficTable.genId(serviceName);
		return getTrafficTableById(id);
	}

	public TrafficTable getTrafficTableById(String id) {
		return m_TrafficTableCache.getHintLoad(id, m_TrafficTableLoader);
	}

	@Override
	public void registerListener(TrafficListener listener) {
		if (m_TrafficListeners.contains(listener)) {
			return;
		}
		m_TrafficListeners.add(listener);
	}

	@Override
	public void unregisterListener(TrafficListener listener) {
		m_TrafficListeners.remove(listener);
	}

	@Override
	public void onReload(TrafficTableVo trafficTableVo) {
		if (_Logger.isTraceEnabled()) {
			_Logger.trace("onReload:" + trafficTableVo.id);
		}
		TrafficTable table = m_TrafficTableCache.getHintLoad(trafficTableVo.id, m_TrafficTableLoader);
		if (null == table) {
			// 不在缓存，略过
			return;
		}
		table.updateVo(trafficTableVo);
	}

	public void onTrafficRuleChange(TrafficTable table) {
		for (TrafficListener l : m_TrafficListeners) {
			try {
				l.onTrafficRuleChange(table);
			} catch (Exception e) {
				_Logger.error(e.toString(), e);
			}
		}
	}

	@Override
	public void onGcCleanup(int policy) {
		if (POLICY_CRITICAL != policy) {
			return;
		}
		if (null != m_TrafficTableCache) {
			m_TrafficTableCache.onGcCleanup(policy);
		}
	}

}
