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
package cn.weforward.gateway.distribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.Destroyable;
import cn.weforward.common.ResultPage;
import cn.weforward.common.json.JsonArray;
import cn.weforward.common.json.JsonObject;
import cn.weforward.common.json.JsonUtil;
import cn.weforward.common.json.StringInput;
import cn.weforward.common.sys.Shutdown;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.GatewayExt;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.ServiceListener;
import cn.weforward.gateway.ops.access.AccessManage;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.AccessLoader;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.Response;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.client.ServiceInvoker;
import cn.weforward.protocol.client.SingleServiceInvoker;
import cn.weforward.protocol.client.execption.ServiceInvokeException;
import cn.weforward.protocol.client.ext.RemoteResultPage;
import cn.weforward.protocol.client.ext.RequestInvokeObject;
import cn.weforward.protocol.datatype.DtList;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.ext.Producer;
import cn.weforward.protocol.ops.ServiceExt;
import cn.weforward.protocol.support.PageDataMapper;
import cn.weforward.protocol.support.SimpleProducer;
import cn.weforward.protocol.support.datatype.SimpleDtList;

/**
 * <code>DistributeManage</code>实现
 * 
 * @author zhangpengji
 *
 */
public class DistributeManageImpl
		implements DistributeManage, ServiceListener, GatewayNodes, Destroyable, PluginListener {
	static final Logger _Logger = LoggerFactory.getLogger(DistributeManageImpl.class);

	// protected DataHub m_DataHub;
	protected GatewayExt m_Gateway;
	protected AccessManage m_AccessManage;
	/** 当前节点 */
	protected NodeSelf m_SelfNode;
	/** 兄弟节点 */
	protected Map<String, NodeAgent> m_BrotherNodes;
	/** 兄弟节点的操作锁 */
	protected final Object m_BrotherNodesLock = new Object();
	// 已注册的网关节点监听器
	private List<GatewayNodeListener> m_GatewayNodeListeners;

	/** 注册到此节点的微服务 */
	protected Map<String, ServiceInstance> m_RegServices;
	/** 从此节点注销的微服务 */
	protected Map<String, ServiceInstance> m_UnregServices;
	/** m_RegServices、m_UnregServices的操作锁 */
	protected final Object m_ServicesLock = new Object();
	/** 最后收集时间 */
	protected long m_LastCollect;

	/** 定时任务间隔（毫秒） */
	protected int m_Interval;
	/** 执行定时任务的线程 */
	protected Thread m_Task;

	public DistributeManageImpl(String id, String host, int port) {
		// String id = System.getProperty("gateway.id");
		// if (StringUtil.isEmpty(id)) {
		// throw new IllegalArgumentException("请使用'-Dgateway.id=x0001'指定网关标识");
		// }
		m_SelfNode = new NodeSelf(id, host, port);
		m_BrotherNodes = Collections.emptyMap();
		m_GatewayNodeListeners = new CopyOnWriteArrayList<>();

		Shutdown.register(this);
	}

	public void setBrothers(String json) throws IOException {
		synchronized (m_BrotherNodesLock) {
			if (StringUtil.isEmpty(json)) {
				m_BrotherNodes = Collections.emptyMap();
				return;
			}
			Map<String, NodeAgent> nodes = new HashMap<>();
			JsonArray array = (JsonArray) JsonUtil.parse(new StringInput(json), null);
			for (int i = 0; i < array.size(); i++) {
				JsonObject jo = (JsonObject) array.item(i);
				String id = (String) jo.property("id").getValue();
				String hostName = (String) jo.property("h").getValue();
				Number port = (Number) jo.property("p").getValue();
				NodeAgent node = new NodeAgent(id, hostName, port.intValue(), true);
				nodes.put(node.getId(), node);
			}
			m_BrotherNodes = nodes;
		}

		new Thread("distributed_init") {
			public void run() {
				try {
					Thread.sleep(10 * 1000);
				} catch (InterruptedException e) {
					_Logger.error("Init interrupted.");
					return;
				}
				reinit();
				_Logger.info("Init finished.");
			};
		}.start();
	}

	public void setGateway(GatewayExt gw) {
		m_Gateway = gw;
		gw.addListener(this);
	}

	public void setAccessManage(AccessManage am) {
		m_AccessManage = am;
	}

	/**
	 * 设置定时任务间隔，单位：秒
	 * 
	 * @param interval
	 */
	public void setInterval(int interval) {
		if (interval >= 0) {
			m_Interval = interval * 1000;
			startTask();
		}
	}

	public void setPluginContainer(PluginContainer container) {
		container.register(this);
	}

	/**
	 * 重新初始化当前节点
	 */
	public void reinit() {
		if (m_BrotherNodes.isEmpty()) {
			return;
		}
		List<ServiceExt> services = null;
		Collection<NodeAgent> brothers = m_BrotherNodes.values();
		for (NodeAgent brother : brothers) {
			if (!brother.isValid()) {
				continue;
			}
			try {
				// 从兄弟节点获取微服务
				ResultPage<ServiceExt> rp = brother.getServices();
				if (0 == rp.getCount()) {
					continue;
				}
				services = new ArrayList<ServiceExt>(rp.getCount());
				for (ServiceExt s : ResultPageHelper.toForeach(rp)) {
					services.add(s);
				}
				break;
			} catch (Exception e) {
				_Logger.error(e.toString(), e);
			}
		}
		m_Gateway.syncServices(services, null, true);
	}

	@Override
	public void syncFromBrother(List<GatewayNode> nodes, List<ServiceExt> regServices, List<ServiceExt> unregServices) {
		// 同步微服务
		try {
			m_Gateway.syncServices(regServices, unregServices, false);
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
		}
		// 更新兄弟节点
		updateBrothers(nodes);
	}

	/**
	 * 更新兄弟节点
	 * 
	 * @param nodes
	 */
	protected void updateBrothers(List<GatewayNode> nodes) {
		if (null == nodes || 0 == nodes.size()) {
			return;
		}

		Map<String, NodeAgent> brothers = m_BrotherNodes;
		// 大部分情况都不会有变化
		int i = 0;
		for (; i < nodes.size(); i++) {
			GatewayNode node = nodes.get(i);
			if (m_SelfNode.id.equals(node.getId())) {
				continue;
			}
			NodeAgent agent = brothers.get(node.getId());
			if (null != agent && agent.equals(node)) {
				if (0 == i) {
					// 第一个是发起此次同步的兄弟
					agent.success();
				}
				// 没有变化
				continue;
			}
			break;
		}
		if (i >= nodes.size()) {
			return;
		}
		// 加锁继续
		synchronized (m_BrotherNodesLock) {
			brothers = m_BrotherNodes;
			// 采用cow的方式，提高读取性能
			Map<String, NodeAgent> copy = new HashMap<>(brothers);
			for (; i < nodes.size(); i++) {
				GatewayNode node = nodes.get(i);
				if (m_SelfNode.id.equals(node.getId())) {
					continue;
				}
				NodeAgent exist = brothers.get(node.getId());
				if (null != exist && exist.equals(node)) {
					if (0 == i) {
						// 第一个是发起此次同步的兄弟
						exist.success();
					}
					// 没有变化
					continue;
				}
				NodeAgent agent = new NodeAgent(node, exist);
				copy.put(agent.getId(), agent);
			}
			m_BrotherNodes = copy;
		}
	}

	Access getInternalAccess() {
		if (null == m_AccessManage) {
			return null;
		}
		return m_AccessManage.getInternalAccess();
	}

	class NodeAgent implements GatewayNode {

		GatewayNodeVo m_Vo;
		volatile int m_Hit;
		boolean m_Permanent;
		boolean m_Losted;

		ServiceInvoker m_Invoker;

		NodeAgent(String id, String hostName, int port, boolean permanent) {
			GatewayNodeVo vo = new GatewayNodeVo();
			vo.setId(id);
			vo.setHostName(hostName);
			vo.setPort(port);
			m_Vo = vo;
			m_Hit = 3;
			m_Permanent = permanent;
		}

		NodeAgent(GatewayNode info, boolean permanent) {
			m_Vo = new GatewayNodeVo(info);
			m_Hit = 3;
			m_Permanent = permanent;
		}

		NodeAgent(GatewayNode info, NodeAgent old) {
			this(info, (null == old ? false : old.m_Permanent));
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof GatewayNode)) {
				return false;
			}
			GatewayNode other = (GatewayNode) obj;
			return getId().equals(other.getId()) && getHostName().equals(other.getHostName())
					&& (getPort() == other.getPort());
		}

		ServiceInvoker getInvoker() {
			if (null == m_Invoker) {
				Access access = getInternalAccess();
				if (null == access) {
					_Logger.error("缺少内置的Access");
					return null;
				}
				String preUrl = "http://" + getHostName() + ":" + getPort() + "/";
				SingleServiceInvoker invoker = new SingleServiceInvoker(preUrl, ServiceName.DISTRIBUTED.name);
				invoker.setConnectTimeout(3000);
				invoker.setReadTimeout(5000);
				if (Configure.getInstance().isCompatMode()) {
					Producer producer = new SimpleProducer(AccessLoader.EMPTY);
					invoker.setProducer(producer);
					invoker.setAuthType(Header.AUTH_TYPE_NONE);
				} else {
					Producer producer = new SimpleProducer(new AccessLoader.Single(access));
					invoker.setProducer(producer);
					invoker.setAuthType(Header.AUTH_TYPE_SHA2);
					invoker.setAccessId(access.getAccessId());
				}
				m_Invoker = invoker;
			}
			return m_Invoker;
		}

		boolean isValid() {
			if (m_Permanent) {
				return true;
			}
			return m_Hit >= 0;
		}

		void success() {
			if (m_Hit >= 10) {
				return;
			}
			m_Hit++;
			if (m_Losted && m_Hit >= 10) {
				m_Losted = false;
			}
		}

		void fail() {
			if (m_Hit <= -1) {
				// 失败计数不能太大，否则很难恢复到有效状态
				return;
			}
			m_Hit--;
			if (!m_Losted && m_Hit <= -1) {
				m_Losted = true;
				onNodeLost(this);
			}
		}

		ResultPage<ServiceExt> getServices() {
			ServiceInvoker invoker = getInvoker();
			if (null == invoker) {
				return ResultPageHelper.empty();
			}
			String method = "get_services";
			PageDataMapper pageDataMapper = new PageDataMapper(ServiceExt.class, ServiceExtMapper.INSTANCE);
			RemoteResultPage<ServiceExt> rp = new RemoteResultPage<>(pageDataMapper, 1, 1000, invoker, method);
			return rp;
		}

		void sync(List<GatewayNode> nodes, Collection<ServiceInstance> regServices,
				Collection<ServiceInstance> unregServices) {
			ServiceInvoker invoker = getInvoker();
			if (null == invoker) {
				return;
			}
			RequestInvokeObject invokeObj = new RequestInvokeObject("sync");
			if (null != nodes && !nodes.isEmpty()) {
				DtList list = SimpleDtList.toDtList(nodes, nodes.size(), GatewayNodeMapper.INSTANCE);
				invokeObj.putParam("nodes", list);
			}
			if (null != regServices && !regServices.isEmpty()) {
				DtList list = SimpleDtList.toDtList(regServices, regServices.size(), ServiceExtMapper.INSTANCE);
				invokeObj.putParam("reg_services", list);
			}
			if (null != unregServices && !unregServices.isEmpty()) {
				DtList list = SimpleDtList.toDtList(unregServices, unregServices.size(), ServiceExtMapper.INSTANCE);
				invokeObj.putParam("unreg_services", list);
			}
			Response resp = invoker.invoke(invokeObj.toDtObject());
			if (0 != resp.getResponseCode()) {
				throw new ServiceInvokeException(resp);
			}
			DtObject result = resp.getServiceResult();
			int code = result.getNumber("code").valueInt();
			if (0 != code) {
				throw new ServiceInvokeException(code + "/" + result.getString("msg").value());
			}
		}

		@Override
		public String toString() {
			return getId() + "," + getHostName() + "," + getPort();
		}

		@Override
		public String getId() {
			return m_Vo.id;
		}

		@Override
		public String getHostName() {
			return m_Vo.hostName;
		}

		@Override
		public int getPort() {
			return m_Vo.port;
		}

		@Override
		public boolean isSelf() {
			return false;
		}
	}

	static class NodeSelf extends GatewayNodeVo implements GatewayNode {

		NodeSelf(String serverId, String host, int port) {
			this.id = serverId;
			// try {
			// int idx = host.indexOf(":");
			// this.hostName = host.substring(0, idx);
			// this.port = Integer.parseInt(host.substring(idx + 1));
			// } catch (RuntimeException e) {
			// throw new IllegalArgumentException("网关节点格式错误：" + host, e);
			// }
			this.hostName = host;
			this.port = port;
		}

		@Override
		public boolean isSelf() {
			return true;
		}

	}

	synchronized void startTask() {
		Thread task = m_Task;
		if (m_Interval > 0 && null == task || !task.isAlive()) {
			task = new Thread(m_SelfNode.getId() + "-node") {
				@Override
				public void run() {
					_Logger.info(this + " running...");
					while (!isInterrupted()) {
						try {
							sleep(m_Interval);
						} catch (InterruptedException e) {
							// 中断了
							_Logger.warn(this + " 中断了");
							break;
						}
						try {
							// 同步到兄弟节点
							syncToBrothers();
						} catch (Throwable e) {
							_Logger.error(e.toString(), e);
						}
					}
					m_Task = null;
					_Logger.info(this + " done.");
				}
			};
			task.setDaemon(true);
			task.start();
			m_Task = task;
		}
	}

	/**
	 * 同步资源到兄弟节点
	 */
	protected void syncToBrothers() {
		// 最近注册的微服务
		Collection<ServiceInstance> regServices = dumpRegServices();
		// 最近注销的微服务
		Collection<ServiceInstance> unregServices = dumpUnregServices();

		if (m_BrotherNodes.isEmpty()) {
			return;
		}

		Collection<NodeAgent> brothers = m_BrotherNodes.values();
		// 已知的兄弟节点
		List<GatewayNode> nodes = new ArrayList<>(brothers.size() + 1);
		nodes.add(m_SelfNode);
		for (NodeAgent b : brothers) {
			if (!b.isValid()) {
				if (_Logger.isTraceEnabled()) {
					_Logger.trace("ignore brother[" + b.getId() + "]");
				}
				continue;
			}
			nodes.add(b);
		}
		brothers = null; // let gc do it work
		if (nodes.size() <= 1) {
			// 只有一个节点，应该是自己
			return;
		}

		for (int i = 1; i < nodes.size(); i++) {
			NodeAgent brother = (NodeAgent) nodes.get(i);
			try {
				brother.sync(nodes, regServices, unregServices);
				brother.success();
				if (_Logger.isTraceEnabled()) {
					_Logger.trace(
							"sync to brother[" + brother + "],r=" + regServices.size() + ",ur=" + unregServices.size());
				}
			} catch (Throwable e) {
				// Throwable cause = e.getCause();
				// if (null != cause && cause instanceof HttpTransportException) {
				// _Logger.error("sync to brother[" + brother + "] failed:" + cause.toString());
				// } else {
				_Logger.error("sync to brother[" + brother + "] failed", e);
				// }
				brother.fail();
				continue;
			}
		}
	}

	@Override
	public void onServiceRegister(ServiceInstance service, boolean foreign) {
		if (foreign) {
			return;
		}
		synchronized (m_ServicesLock) {
			if (null == m_RegServices) {
				m_RegServices = new HashMap<String, ServiceInstance>();
			}
			m_RegServices.put(service.getId(), service);
			if (null != m_UnregServices) {
				m_UnregServices.remove(service.getId());
			}
		}
	}

	@Override
	public void onServiceUnregister(ServiceInstance service, boolean foreign) {
		if (foreign) {
			return;
		}
		synchronized (m_ServicesLock) {
			if (null != m_RegServices) {
				m_RegServices.remove(service.getId());
			}
			if (null == m_UnregServices) {
				m_UnregServices = new HashMap<String, ServiceInstance>();
			}
			m_UnregServices.put(service.getId(), service);
		}
	}

	protected Collection<ServiceInstance> dumpRegServices() {
		Collection<ServiceInstance> services = Collections.emptyList();
		synchronized (m_ServicesLock) {
			if (null != m_RegServices) {
				services = m_RegServices.values();
				m_RegServices = null;
			}
		}
		return services;
	}

	protected Collection<ServiceInstance> dumpUnregServices() {
		Collection<ServiceInstance> services = Collections.emptyList();
		synchronized (m_ServicesLock) {
			if (null != m_UnregServices) {
				services = m_UnregServices.values();
				m_UnregServices = null;
			}
		}
		return services;
	}

	@Override
	public void destroy() {
		Thread task = m_Task;
		if (null != task && task.isAlive()) {
			task.interrupt();
		}
	}

	@Override
	public ResultPage<ServiceExt> getServices() {
		return m_Gateway.listService(null);
	}

	@Override
	public void onServiceTimeout(ServiceInstance service) {
		// nothing to do
	}

	@Override
	public List<GatewayNode> getAllNode() {
		Collection<NodeAgent> brothers = m_BrotherNodes.values();
		if (brothers.isEmpty()) {
			return Collections.singletonList(m_SelfNode);
		}
		List<GatewayNode> nodes = new ArrayList<>(brothers.size() + 1);
		nodes.add(m_SelfNode);
		for (NodeAgent b : brothers) {
			nodes.add(b);
		}
		return nodes;
	}

	@Override
	public GatewayNode getSelfNode() {
		return m_SelfNode;
	}

	@Override
	public void onServiceUnavailable(ServiceInstance service) {
		// nothing to do
	}

	@Override
	public void onServiceOverload(ServiceInstance service) {
		// nothing to do
	}

	public void addListener(GatewayNodeListener listener) {
		if (m_GatewayNodeListeners.contains(listener)) {
			return;
		}
		m_GatewayNodeListeners.add(listener);
	}

	public void removeListener(GatewayNodeListener listener) {
		m_GatewayNodeListeners.remove(listener);
	}

	protected void onNodeLost(NodeAgent node) {
		_Logger.error("网关[" + node.getId() + "]失联");
		for (GatewayNodeListener l : m_GatewayNodeListeners) {
			l.onGatewayNodeLost(node);
		}
	}

	@Override
	public void onPluginLoad(Pluginable plugin) {
		if (plugin instanceof GatewayNodeListener) {
			addListener((GatewayNodeListener) plugin);
		}
	}

	@Override
	public void onPluginUnload(Pluginable plugin) {
		if (plugin instanceof GatewayNodeListener) {
			removeListener((GatewayNodeListener) plugin);
		}
	}
}
