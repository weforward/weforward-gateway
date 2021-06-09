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
package cn.weforward.gateway.mesh;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.weforward.common.Destroyable;
import cn.weforward.common.ResultPage;
import cn.weforward.common.json.JsonArray;
import cn.weforward.common.json.JsonObject;
import cn.weforward.common.json.JsonUtil;
import cn.weforward.common.json.StringInput;
import cn.weforward.common.sys.Shutdown;
import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.TransList;
import cn.weforward.gateway.GatewayExt;
import cn.weforward.gateway.MeshNode;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.ServiceListener;
import cn.weforward.gateway.distribute.ServiceInstanceMapper;
import cn.weforward.gateway.ops.VoFactory;
import cn.weforward.gateway.ops.access.AccessManage;
import cn.weforward.gateway.ops.access.system.MasterKeyVo;
import cn.weforward.gateway.ops.access.system.MasterKeyVoFactory;
import cn.weforward.gateway.ops.access.system.ServiceAccessVo;
import cn.weforward.gateway.ops.access.system.ServiceAccessVoFactory;
import cn.weforward.gateway.ops.right.RightTableVo;
import cn.weforward.gateway.ops.right.RightTableVoFactory;
import cn.weforward.gateway.ops.traffic.TrafficTableVo;
import cn.weforward.gateway.ops.traffic.TrafficTableVoFactory;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.Response;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.client.ServiceInvoker;
import cn.weforward.protocol.client.ServiceInvokerFactory;
import cn.weforward.protocol.client.execption.ServiceInvokeException;
import cn.weforward.protocol.client.ext.RemoteResultPage;
import cn.weforward.protocol.client.ext.RequestInvokeObject;
import cn.weforward.protocol.datatype.DtList;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.ext.ObjectMapper;
import cn.weforward.protocol.support.BeanObjectMapperSet;
import cn.weforward.protocol.support.datatype.AppendableDtObject;
import cn.weforward.protocol.support.datatype.SimpleDtList;

/**
 * MeshManage的实现
 * 
 * @author zhangpengji
 *
 */
public class MeshManageImpl
		implements MeshManage, ServiceListener, Destroyable, PluginListener, VoFactory.ChangeListener<Object> {

	protected GatewayExt m_Gateway;
	protected AccessManage m_AccessManage;

	/** 当前节点 */
	protected NodeSelf m_SelfNode;
	/** 兄弟节点 */
	protected Map<String, NodeAgent> m_BrotherNodes;
	/** 兄弟节点的操作锁 */
	protected final Object m_BrotherNodesLock = new Object();

	/** 注册到此节点的微服务 */
	protected Map<String, ServiceInstance> m_RegServices;
	/** 从此节点注销的微服务 */
	protected Map<String, ServiceInstance> m_UnregServices;
	/** m_RegServices、m_UnregServices的操作锁 */
	protected final Object m_ServicesLock = new Object();

	protected MasterKeyVoFactory m_MasterKeyVoFactory;
	protected ServiceAccessVoFactory m_ServiceAccessVoFactory;
	protected RightTableVoFactory m_RightTableVoFactory;
	protected TrafficTableVoFactory m_TrafficTableVoFactory;

	/**
	 * 已变化对象的队列。<br/>
	 * 由Master节点收集，并同步到其他节点
	 */
	protected ChangedObjectQueue m_ChangedObjectQueue;

	/** 定时任务间隔（毫秒） */
	protected int m_Interval;
	/** 执行定时任务的线程 */
	protected Thread m_Task;

	public MeshManageImpl(String id, String urls, boolean isMaster) {
		m_SelfNode = new NodeSelf(id, urls, isMaster);
		m_BrotherNodes = Collections.emptyMap();

		Shutdown.register(this);
	}

	public void setGateway(GatewayExt gw) {
		m_Gateway = gw;
		gw.addListener(this);
	}

	public void setAccessManage(AccessManage am) {
		m_AccessManage = am;
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
				String urlsStr = (String) jo.property("urls").getValue();
				List<String> urls = Arrays.asList(urlsStr.split(";"));
				NodeAgent node = new NodeAgent(id, urls, true);
				nodes.put(node.getId(), node);
			}
			m_BrotherNodes = nodes;
		}
	}

	public void setPluginContainer(PluginContainer container) {
		container.register(this);
	}

	/**
	 * 设置定时任务间隔，单位：秒
	 * 
	 * @param interval
	 */
	public void setInterval(int interval) {
		if (interval > 0) {
			m_Interval = interval * 1000;
			if (m_SelfNode.isMaster()) {
				m_ChangedObjectQueue = new ChangedObjectQueue();
			}
			startTask();
		}
	}

	synchronized void startTask() {
		Thread task = m_Task;
		if (m_Interval > 0 && (null == task || !task.isAlive())) {
			task = new Thread("mesh-node-" + m_SelfNode.getId()) {
				@Override
				public void run() {
					_Logger.info(this + " running...");
					while (!isInterrupted()) {
						ChangedObjectQueue.Changeds changes = null;
						try {
							if (null != m_ChangedObjectQueue) {
								changes = m_ChangedObjectQueue.poll(m_Interval);
							} else {
								sleep(m_Interval);
							}
						} catch (InterruptedException e) {
							// 中断了
							_Logger.warn(this + " 中断了");
							break;
						}
						try {
							// 同步到兄弟节点
							syncToBrothers(changes);
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

	public void setInitByMesh(boolean init) {
		// XXX 使用keeper api初始化
		if (!init) {
			return;
		}

		new Thread("mesh_init") {
			public void run() {
				try {
					Thread.sleep(60 * 1000);
				} catch (InterruptedException e) {
					_Logger.error("Init interrupted.");
					return;
				}
				initByMesh();
				_Logger.info("Init finished.");
			};
		}.start();
	}

	/**
	 * 重新初始化当前节点
	 */
	public void initByMesh() {
		if (m_BrotherNodes.isEmpty()) {
			return;
		}
		NodeAgent brother = m_BrotherNodes.values().iterator().next();
		ResultPage<MasterKeyVo> keys = brother.searchMasterKeyVo(null, null);
		for (MasterKeyVo vo : ResultPageHelper.toForeach(keys)) {
			m_MasterKeyVoFactory.put(vo);
		}
		_Logger.info("sync master key:" + keys.getCount());

		ResultPage<ServiceAccessVo> accesses = brother.searchServiceAccessVo(null, null);
		for (ServiceAccessVo vo : ResultPageHelper.toForeach(accesses)) {
			m_ServiceAccessVoFactory.put(vo);
		}
		_Logger.info("sync service access:" + accesses.getCount());

		ResultPage<RightTableVo> rights = brother.searchRightTableVo(null, null);
		for (RightTableVo vo : ResultPageHelper.toForeach(rights)) {
			m_RightTableVoFactory.put(vo);
		}
		_Logger.info("sync right table:" + rights.getCount());

		ResultPage<TrafficTableVo> traffics = brother.searchTrafficTableVo(null, null);
		for (TrafficTableVo vo : ResultPageHelper.toForeach(traffics)) {
			m_TrafficTableVoFactory.put(vo);
		}
		_Logger.info("sync traffic table:" + traffics.getCount());
	}

	Access getInternalAccess() {
		if (null == m_AccessManage) {
			return null;
		}
		return m_AccessManage.getInternalAccess();
	}

	/**
	 * 同步资源到兄弟节点
	 */
	protected void syncToBrothers(ChangedObjectQueue.Changeds changes) {
		// 最近注册的微服务
		Collection<ServiceInstance> regServices = dumpRegServices();
		// 最近注销的微服务
		Collection<ServiceInstance> unregServices = dumpUnregServices();

		if (m_BrotherNodes.isEmpty()) {
			return;
		}

		Collection<NodeAgent> brothers = m_BrotherNodes.values();
		// 已知的兄弟节点
		List<MeshNode> nodes = new ArrayList<>(brothers.size() + 1);
		nodes.add(m_SelfNode);
		for (NodeAgent b : brothers) {
			if (!b.isValid()) {
				if (_Logger.isTraceEnabled()) {
					_Logger.trace("ignore mesh[" + b.getId() + "]");
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
			NodeAgent mesh = (NodeAgent) nodes.get(i);
			try {
				mesh.sync(nodes, regServices, unregServices, changes);
				mesh.success();
				if (_Logger.isTraceEnabled()) {
					_Logger.trace("sync to mesh[" + mesh + "],r=" + regServices.size() + ",ur=" + unregServices.size()
							+ ",changes=" + changes);
				}
			} catch (Throwable e) {
				// Throwable cause = e.getCause();
				// if (null != cause && cause instanceof HttpTransportException) {
				// _Logger.error("sync to brother[" + brother + "] failed:" + cause.toString());
				// } else {
				_Logger.error("sync to mesh[" + mesh + "] failed", e);
				// }
				mesh.fail();
				continue;
			}
		}
	}

	@Override
	public void syncFromBrother(List<MeshNode> nodes, List<ServiceInstance> regServices,
			List<ServiceInstance> unregServices, List<Object> updatedObjects) {
		// 更新兄弟节点
		updateBrothers(nodes);
		// 同步微服务
		try {
			m_Gateway.syncServices(nodes.get(0), regServices, unregServices);
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
		}
		// 更新数据
		if (!ListUtil.isEmpty(updatedObjects)) {
			for (Object obj : updatedObjects) {
				if (obj instanceof MasterKeyVo) {
					m_MasterKeyVoFactory.updateByMesh((MasterKeyVo) obj);
				} else if (obj instanceof ServiceAccessVo) {
					m_ServiceAccessVoFactory.updateByMesh((ServiceAccessVo) obj);
				} else if (obj instanceof RightTableVo) {
					m_RightTableVoFactory.updateByMesh((RightTableVo) obj);
				} else if (obj instanceof TrafficTableVo) {
					m_TrafficTableVoFactory.updateByMesh((TrafficTableVo) obj);
				}
			}
		}
	}

	/**
	 * 更新兄弟节点
	 * 
	 * @param nodes
	 */
	protected void updateBrothers(List<MeshNode> nodes) {
		/*
		 * 更新策略：
		 * 若节点是配置的，则无法修改；
		 * 若节点是同步而来，则允许该节点修改自身信息
		 */
		if (null == nodes || 0 == nodes.size()) {
			return;
		}

		Map<String, NodeAgent> brothers = m_BrotherNodes;
		// 大部分情况都不会有变化
		int i = 0;
		for (; i < nodes.size(); i++) {
			MeshNode node = nodes.get(i);
			if (m_SelfNode.isSame(node)) {
				continue;
			}
			NodeAgent agent = brothers.get(node.getId());
			if (null != agent && agent.equals(node)) {
				if (0 == i) {
					// 第一个是发起此次同步的兄弟
					agent.update(node);
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
				MeshNode node = nodes.get(i);
				if (m_SelfNode.isSame(node)) {
					continue;
				}
				NodeAgent exist = brothers.get(node.getId());
				if (null != exist && exist.equals(node)) {
					if (0 == i) {
						// 第一个是发起此次同步的兄弟
						exist.update(node);
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

	@Override
	public void onServiceRegister(ServiceInstance service, boolean foreign) {
		if (null != service.getMeshNode()) {
			// 其他网格的微服务，忽略之
			return;
		}
		if (null == m_Task) {
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
		if (null != service.getMeshNode()) {
			// 其他网格的微服务，忽略之
			return;
		}
		if (null == m_Task) {
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
	public void onServiceTimeout(ServiceInstance service) {
	}

	@Override
	public void onServiceUnavailable(ServiceInstance service) {
	}

	@Override
	public void onServiceOverload(ServiceInstance service) {
	}

	@Override
	public void destroy() {
		Thread task = m_Task;
		m_Task = null;
		if (null != task && task.isAlive()) {
			task.interrupt();
		}
	}

	static class NodeSelf extends MeshNodeVo implements MeshNode {
		boolean master;

		NodeSelf(String id, String urls, boolean master) {
			this.id = id;
			this.urls = Arrays.asList(urls.split(";"));
			this.master = master;
		}

		@Override
		public boolean isSelf() {
			return true;
		}

		boolean isSame(MeshNode other) {
			return getId().equals(other.getId());
		}

		boolean isMaster() {
			return this.master;
		}

	}

	class NodeAgent implements MeshNode {

		MeshNodeVo m_Vo;
		volatile int m_Hit;
		boolean m_Permanent;

		ServiceInvoker m_Invoker;

		NodeAgent(String id, List<String> urls, boolean permanent) {
			MeshNodeVo vo = new MeshNodeVo();
			vo.setId(id);
			vo.setUrls(urls);
			m_Vo = vo;
			m_Hit = 3;
			m_Permanent = permanent;
		}

		NodeAgent(MeshNode info, boolean permanent) {
			m_Vo = new MeshNodeVo(info);
			m_Hit = 3;
			m_Permanent = permanent;
		}

		NodeAgent(MeshNode info, NodeAgent old) {
			this(info, (null == old ? false : old.m_Permanent));
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof MeshNode)) {
				return false;
			}
			MeshNode other = (MeshNode) obj;
			return getId().equals(other.getId());
		}

		void update(MeshNode info) {
			if (m_Permanent) {
				return;
			}
			if (!ListUtil.eq(getUrls(), info.getUrls())) {
				m_Vo.urls = info.getUrls();
				m_Invoker = null;
			}
		}

		@Override
		public String getId() {
			return m_Vo.getId();
		}

		@Override
		public List<String> getUrls() {
			return m_Vo.getUrls();
		}

		@Override
		public boolean isSelf() {
			return false;
		}

		public boolean isValid() {
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
		}

		void fail() {
			if (m_Hit <= -1) {
				// 失败计数不能太大，否则很难恢复到有效状态
				return;
			}
			m_Hit--;
		}

		ServiceInvoker getInvoker() {
			if (null == m_Invoker) {
				Access access = getInternalAccess();
				if (null == access) {
					_Logger.error("缺少内置的Access");
					return null;
				}
				List<String> urls = getUrls();
				ServiceInvoker invoker = ServiceInvokerFactory.create(ServiceName.MESH.name, urls, access.getAccessId(),
						access.getAccessKeyHex());
				invoker.setConnectTimeout(3000);
				invoker.setReadTimeout(10000);
				m_Invoker = invoker;
			}
			return m_Invoker;
		}

		RequestInvokeObject createRequest(String method) {
			RequestInvokeObject invokeObj = new RequestInvokeObject(method);
			invokeObj.setMappers(BeanObjectMapperSet.INSTANCE);
			return invokeObj;
		}

		void sync(List<MeshNode> nodes, Collection<ServiceInstance> regServices,
				Collection<ServiceInstance> unregServices, ChangedObjectQueue.Changeds changes) {
			ServiceInvoker invoker = getInvoker();
			if (null == invoker) {
				return;
			}
			RequestInvokeObject invokeObj = createRequest("sync");
			if (null != nodes && !nodes.isEmpty()) {
				List<MeshNodeVo> vos = new TransList<MeshNodeVo, MeshNode>(nodes) {

					@Override
					protected MeshNodeVo trans(MeshNode src) {
						return MeshNodeVo.valueOf(src);
					}
				};
				invokeObj.putParam("nodes", vos);
			}
			if (null != regServices && !regServices.isEmpty()) {
				DtList list = SimpleDtList.toDtList(regServices, regServices.size(), ServiceInstanceMapper.INSTANCE);
				invokeObj.putParam("reg_services", list);
			}
			if (null != unregServices && !unregServices.isEmpty()) {
				DtList list = SimpleDtList.toDtList(unregServices, unregServices.size(),
						ServiceInstanceMapper.INSTANCE);
				invokeObj.putParam("unreg_services", list);
			}
			if (null != changes) {
				invokeObj.putParam("updated_objects", toDtList(changes.updates));
				// 先忽略
				// invokeObj.putParam("deleted_objects", toDtList(changes.deletes));
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

		DtList toDtList(List<Object> objects) {
			if (ListUtil.isEmpty(objects)) {
				return null;
			}
			SimpleDtList list = new SimpleDtList(objects.size());
			for (Object obj : objects) {
				@SuppressWarnings("unchecked")
				ObjectMapper<Object> mapper = (ObjectMapper<Object>) BeanObjectMapperSet.INSTANCE
						.getObjectMapper(obj.getClass());
				DtObject dtObj = mapper.toDtObject(obj);
				AppendableDtObject append = new AppendableDtObject(dtObj, false);
				append.put("__mapper", mapper.getName());
				list.addItem(append);
			}
			return list;
		}

		ResultPage<MasterKeyVo> searchMasterKeyVo(Date begin, Date end) {
			ServiceInvoker invoker = getInvoker();
			if (null == invoker) {
				return ResultPageHelper.empty();
			}
			RemoteResultPage<MasterKeyVo> rp = new RemoteResultPage<>(MasterKeyVo.class, invoker, "search_master_key");
			return rp;
		}

		ResultPage<ServiceAccessVo> searchServiceAccessVo(Date begin, Date end) {
			ServiceInvoker invoker = getInvoker();
			if (null == invoker) {
				return ResultPageHelper.empty();
			}
			RemoteResultPage<ServiceAccessVo> rp = new RemoteResultPage<>(ServiceAccessVo.class, invoker,
					"search_service_access");
			return rp;
		}

		ResultPage<RightTableVo> searchRightTableVo(Date begin, Date end) {
			ServiceInvoker invoker = getInvoker();
			if (null == invoker) {
				return ResultPageHelper.empty();
			}
			RemoteResultPage<RightTableVo> rp = new RemoteResultPage<>(RightTableVo.class, invoker,
					"search_right_table");
			return rp;
		}

		ResultPage<TrafficTableVo> searchTrafficTableVo(Date begin, Date end) {
			ServiceInvoker invoker = getInvoker();
			if (null == invoker) {
				return ResultPageHelper.empty();
			}
			RemoteResultPage<TrafficTableVo> rp = new RemoteResultPage<>(TrafficTableVo.class, invoker,
					"search_traffic_table");
			return rp;
		}

		@Override
		public String toString() {
			return getId();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void onPluginLoad(Pluginable plugin) {
		VoFactory<?> factory = null;
		if (plugin instanceof ServiceAccessVoFactory) {
			m_ServiceAccessVoFactory = (ServiceAccessVoFactory) plugin;
			factory = m_ServiceAccessVoFactory;
		} else if (plugin instanceof MasterKeyVoFactory) {
			m_MasterKeyVoFactory = (MasterKeyVoFactory) plugin;
			factory = m_MasterKeyVoFactory;
		} else if (plugin instanceof RightTableVoFactory) {
			m_RightTableVoFactory = (RightTableVoFactory) plugin;
			factory = m_RightTableVoFactory;
		} else if (plugin instanceof TrafficTableVoFactory) {
			m_TrafficTableVoFactory = (TrafficTableVoFactory) plugin;
			factory = m_TrafficTableVoFactory;
		}

		if (null != factory && m_SelfNode.isMaster()) {
			((VoFactory<Object>) factory).registerChangeListener(this);
		}
	}

	@Override
	public void onPluginUnload(Pluginable plugin) {
		if (plugin instanceof ServiceAccessVoFactory) {
			m_ServiceAccessVoFactory = null;
		} else if (plugin instanceof MasterKeyVoFactory) {
			m_MasterKeyVoFactory = null;
		} else if (plugin instanceof RightTableVoFactory) {
			m_RightTableVoFactory = null;
		} else if (plugin instanceof TrafficTableVoFactory) {
			m_TrafficTableVoFactory = null;
		}
	}

	@Override
	public ResultPage<MasterKeyVo> searchMasterKeyVo(Date begin, Date end) {
		if (null == m_MasterKeyVoFactory) {
			return ResultPageHelper.empty();
		}
		return m_MasterKeyVoFactory.search(begin, end);
	}

	@Override
	public ResultPage<ServiceAccessVo> searchServiceAccessVo(Date begin, Date end) {
		if (null == m_ServiceAccessVoFactory) {
			return ResultPageHelper.empty();
		}
		return m_ServiceAccessVoFactory.search(begin, end);
	}

	@Override
	public ResultPage<RightTableVo> searchRightTableVo(Date begin, Date end) {
		if (null == m_RightTableVoFactory) {
			return ResultPageHelper.empty();
		}
		return m_RightTableVoFactory.search(begin, end);
	}

	@Override
	public ResultPage<TrafficTableVo> searchTrafficTableVo(Date begin, Date end) {
		if (null == m_TrafficTableVoFactory) {
			return ResultPageHelper.empty();
		}
		return m_TrafficTableVoFactory.search(begin, end);
	}

	@Override
	public void onChanged(Object vo, int type) {
		if (null == m_ChangedObjectQueue) {
			return;
		}
		if (MasterKeyVoFactory.CHANGE_TYPE_UPDATE == type) {
			m_ChangedObjectQueue.putUpdated(vo);
		}
		if (MasterKeyVoFactory.CHANGE_TYPE_DELETE == type) {
			m_ChangedObjectQueue.putDeleted(vo);
		}

	}
}
