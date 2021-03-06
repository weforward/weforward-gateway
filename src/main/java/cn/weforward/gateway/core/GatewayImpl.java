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
package cn.weforward.gateway.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import cn.weforward.common.GcCleanable;
import cn.weforward.common.NameItem;
import cn.weforward.common.ResultPage;
import cn.weforward.common.sys.GcCleaner;
import cn.weforward.common.util.LruCache;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.TaskExecutor;
import cn.weforward.common.util.TaskExecutor.Task;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.GatewayExt;
import cn.weforward.gateway.MeshNode;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.ServiceListener;
import cn.weforward.gateway.StreamTunnel;
import cn.weforward.gateway.Tunnel;
import cn.weforward.gateway.exception.DebugServiceException;
import cn.weforward.gateway.ops.access.AccessManage;
import cn.weforward.gateway.ops.acl.AclManage;
import cn.weforward.gateway.ops.right.RightManage;
import cn.weforward.gateway.ops.trace.ServiceTracer;
import cn.weforward.gateway.ops.traffic.TrafficListener;
import cn.weforward.gateway.ops.traffic.TrafficManage;
import cn.weforward.gateway.util.ServiceNameMatcher;
import cn.weforward.metrics.WeforwardMetrics;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.aio.ClientChannel;
import cn.weforward.protocol.aio.netty.NettyHttpClientFactory;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.doc.ServiceDocument;
import cn.weforward.protocol.exception.AuthException;
import cn.weforward.protocol.exception.WeforwardException;
import cn.weforward.protocol.ext.Producer;
import cn.weforward.protocol.ext.ServiceRuntime;
import cn.weforward.protocol.gateway.ServiceSummary;
import cn.weforward.protocol.ops.AccessExt;
import cn.weforward.protocol.ops.trace.ServiceTraceToken;
import cn.weforward.protocol.ops.traffic.TrafficTable;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;

/**
 * 网关的实现
 * 
 * @author zhangpengji
 *
 */
public class GatewayImpl implements GatewayExt, TrafficListener, PluginListener, GcCleanable {

	protected final String m_ServerId;

	protected AccessManage m_AccessManage;
	protected TrafficManage m_TrafficManage;
	protected RightManage m_RightManage;
	protected AclManage m_AclManage;
	protected ServiceTracer m_ServiceTracer;
	protected TaskExecutor m_BackgroundExecutor;
	protected Producer m_Producer;
	protected Executor m_RpcExecutor;
	protected MeterRegistry m_MeterRegistry;

	// 已注册的服务集合
	private Map<String, ServiceInstance> m_Services = new ConcurrentHashMap<>();

	// 按名称分组合并后的服务集合
	private Map<String, ServiceInstanceBalance> m_ServiceBalances = new ConcurrentHashMap<>();
	// 微服务配额
	private ServiceQuotas m_ServiceQuotas;
	// 允许服务心跳连续缺失的次数
	private int m_HeartbeatMissing = 3;
	// 已注册的微服务监听器
	private List<ServiceListener> m_ServiceListeners;
	// 微服务文档缓存
	private LruCache<String, SimpleDocumentImpl> m_ServiceDocCache;
	// 是否已就绪
	private boolean m_Ready;
	// NettyHttpClient工厂
	private volatile NettyHttpClientFactory m_HttpClientFactory;
	// 清理任务
	private Task m_ClearTask;

	public GatewayImpl(String serverId, MeterRegistry meterRegistry) {
		m_ServerId = serverId;
		m_MeterRegistry = meterRegistry;

		m_ServiceListeners = new CopyOnWriteArrayList<>();

		m_ServiceDocCache = new LruCache<>(1000, "service_doc");
		m_ServiceDocCache.setTimeout(60 * 60);

		Configure cfg = Configure.getInstance();
		NettyHttpClientFactory factory = new NettyHttpClientFactory();
		factory.setConnectTimeout(5 * 1000);
		if (cfg.isNettyDebug()) {
			factory.setDebugEnabled(true);
		}
		m_HttpClientFactory = factory;

		int maxQuotas = cfg.getRpcChannelMaxConcurrent() + cfg.getStreamChannelMaxConcurrent();
		int reserveQuotas = maxQuotas * (100 - cfg.getSingleServiceConcurrentPercent()) / 100;
		m_ServiceQuotas = new ServiceQuotas(maxQuotas, reserveQuotas);

		new Thread("wait_ready") {
			public void run() {
				synchronized (this) {
					try {
						wait(30 * 1000);
					} catch (InterruptedException e) {
						_Logger.error(e.toString(), e);
					}
					m_Ready = true;
				}
			};
		}.start();

		GcCleaner.register(this);
	}

	protected NettyHttpClientFactory getHttpClientFactory() {
		return m_HttpClientFactory;
	}

	@Override
	public boolean isReady() {
		return m_Ready;
	}

	public void setAccessManage(AccessManage am) {
		m_AccessManage = am;
	}

	public void setTrafficManage(TrafficManage tm) {
		m_TrafficManage = tm;
		tm.registerListener(this);
	}

	public void setRightManage(RightManage rm) {
		m_RightManage = rm;
	}

	public void setAclManage(AclManage am) {
		m_AclManage = am;
	}

	public void setServiceTracer(ServiceTracer tracer) {
		m_ServiceTracer = tracer;
	}

	public synchronized void setBackgroundExecutor(TaskExecutor executor) {
		m_BackgroundExecutor = executor;
		if (null != m_ClearTask) {
			m_ClearTask.cancel();
			m_ClearTask = null;
		}
		if (null == executor) {
			return;
		}
		m_ClearTask = executor.execute(new Runnable() {

			@Override
			public void run() {
				clear();
			}
		}, TaskExecutor.OPTION_NONE, 60 * 1000, 60 * 1000);
	}

	public void setProducer(Producer p) {
		m_Producer = p;
	}

	public void setRpcExecutor(Executor executor) {
		m_RpcExecutor = executor;
	}

	public void setPluginContainer(PluginContainer container) {
		container.register(this);
	}

	public void setMeterRegistry(MeterRegistry registry) {
		m_MeterRegistry = registry;
	}

	@Override
	public void addListener(ServiceListener listener) {
		if (m_ServiceListeners.contains(listener)) {
			return;
		}
		m_ServiceListeners.add(listener);
	}

	@Override
	public void removeListener(ServiceListener listener) {
		m_ServiceListeners.remove(listener);
	}

	/**
	 * 清理
	 */
	public void clear() {
		for (ServiceInstance s : m_Services.values()) {
			if (!s.isTimeout()) {
				if (s.isHeartbeatTimeout(m_HeartbeatMissing)) {
					// 心跳超时
					s.setTimeout(true);
					timeoutService(s);
				}
			} else {
				if (s.isHeartbeatTimeout(m_HeartbeatMissing * 100)) {
					// 移除太久没心跳的
					_Logger.error("微服务[" + s.toStringNameNo() + "]心跳超时，被移除");
					removeService(s);
				}
			}
		}
	}

	@Override
	public void joint(Tunnel tunnel) {
		// if (!isReady()) {
		// tunnel.responseError(null, WeforwardException.CODE_UNREADY, "网关未就绪");
		// return;
		// }
		// 中继
		if(tunnel.isRelay()) {
			jointByRelay(tunnel);
			return;
		}
		
		// topic信道
		Header header = tunnel.getHeader();
		if (Header.CHANNEL_TOPIC.equals(header.getChannel())) {
			jointTopic(tunnel);
			return;
		}
		
		String serviceName = header.getService();
		ServiceInstanceBalance balance = m_ServiceBalances.get(serviceName);
		if (null == balance) {
			tunnel.responseError(null, WeforwardException.CODE_SERVICE_NOT_FOUND, "服务不存在：" + serviceName);
			return;
		}
		try {
			m_RightManage.verifyAccess(tunnel);
		} catch (AuthException e) {
			_Logger.error(e.toString());
			tunnel.responseError(null, e.getCode(), e.getMessage());
			return;
		}
		
		int depth = ServiceTraceToken.Helper.getDepth(tunnel.getTraceToken());
		int maxDepth = Configure.getInstance().getServiceInvokeMaxDepth();
		if (depth > maxDepth) {
			tunnel.responseError(null, WeforwardException.CODE_SERVICE_TOO_DEPTH, "调用栈溢出：" + depth + " > " + maxDepth);
			return;
		}
		balance.joint(tunnel);
	}

	private void jointTopic(Tunnel tunnel) {
		TopicBridger bridger = new TopicBridger();
		try {
			bridger.jonit(this, tunnel);
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
			tunnel.responseError(null, WeforwardException.CODE_INTERNAL_ERROR, "内部错误");
			return;
		}
		bridger.connect();
	}
	
	private void jointByRelay(Tunnel tunnel) {
		String serviceName = tunnel.getHeader().getService();
		ServiceInstanceBalance balance = m_ServiceBalances.get(serviceName);
		if (null == balance) {
			tunnel.responseError(null, WeforwardException.CODE_SERVICE_NOT_FOUND, "服务不存在：" + serviceName);
			return;
		}
		balance.jointByRelay(tunnel);
	}

	@Override
	public void joint(StreamTunnel tunnel) {
		// if (!isReady()) {
		// tunnel.responseError(null, StreamTunnel.CODE_UNAVAILABLE, "网关未就绪");
		// return;
		// }
		String serviceName = tunnel.getServiceName();
		ServiceInstanceBalance balance = m_ServiceBalances.get(serviceName);
		if (null == balance) {
			tunnel.responseError(null, StreamTunnel.CODE_NOT_FOUND, "服务不存在：" + serviceName);
			return;
		}
		if (tunnel.isRelay()) {
			balance.jointByRelay(tunnel);
			return;
		}
		balance.joint(tunnel);
	}

	@Override
	public void registerService(String ownerAccessId, Service info, ServiceRuntime runtime, ClientChannel clientChannel) {
		ServiceInstance service = new ServiceInstance(info, ownerAccessId, new Date());
		service.setClientChannel(clientChannel);
		registerServiceInner(service, false);

		gaugeServiceRuntime(service, runtime);
	}

	private void gaugeServiceRuntime(ServiceInstance service, ServiceRuntime runtime) {
		MeterRegistry registry = m_MeterRegistry;
		if (null == runtime || null == registry) {
			return;
		}
		try {
			Tags tags = WeforwardMetrics.TagHelper.of(WeforwardMetrics.ONE_METRICS_TAG,
					WeforwardMetrics.TagHelper.serviceName(service.getName()),
					WeforwardMetrics.TagHelper.serviceNo(service.getNo()));
			Gauge.builder(WeforwardMetrics.MEMORY_MAX, runtime, ServiceRuntime::getMemoryMax).tags(tags)
					.strongReference(true).register(registry);
			Gauge.builder(WeforwardMetrics.MEMORY_ALLOC, runtime, ServiceRuntime::getMemoryAlloc).tags(tags)
					.strongReference(true).register(registry);
			Gauge.builder(WeforwardMetrics.MEMORY_USED, runtime, ServiceRuntime::getMemoryUsed).tags(tags)
					.strongReference(true).register(registry);
			Gauge.builder(WeforwardMetrics.GC_FULL_COUNT, runtime, ServiceRuntime::getGcFullCount).tags(tags)
					.strongReference(true).register(registry);
			Gauge.builder(WeforwardMetrics.GC_FULL_TIME, runtime, ServiceRuntime::getGcFullTime).tags(tags)
					.strongReference(true).register(registry);
			Gauge.builder(WeforwardMetrics.THREAD_COUNT, runtime, ServiceRuntime::getThreadCount).tags(tags)
					.strongReference(true).register(registry);
			Gauge.builder(WeforwardMetrics.CPU_USAGE_RATE, runtime, ServiceRuntime::getCpuUsageRate).tags(tags)
					.strongReference(true).register(registry);
			TimeGauge.builder(WeforwardMetrics.START_TIME, runtime, TimeUnit.MILLISECONDS, ServiceRuntime::getStartTime)
					.tags(tags).register(registry);
			TimeGauge.builder(WeforwardMetrics.UP_TIME, runtime, TimeUnit.MILLISECONDS, ServiceRuntime::getUpTime)
					.tags(tags).register(registry);
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
		}
	}

	protected void registerServiceInner(ServiceInstance service, boolean foreign) {
		ServiceInstance exist = m_Services.get(service.getId());
		if (null != exist) {
			if (exist.getHeartbeatMills() >= service.getHeartbeatMills()) {
				return;
			}
			if (foreign && !exist.isTimeout() && null != exist.getClientChannel() && exist.getClientChannel().isValid()) {
				// 原实例与本网关有建立专用信道，故保留原实例
				return;
			}
		}
		m_Services.put(service.getId(), service);

		String name = service.getName();
		ServiceInstanceBalance balance = m_ServiceBalances.get(name);
		if (null == balance) {
			// 并发时只会保留最后一个，重新注册一遍就行，不做同步
			balance = new ServiceInstanceBalance(this, name);
			m_ServiceBalances.put(name, balance);
		}
		balance.put(service);

		for (ServiceListener l : m_ServiceListeners) {
			try {
				l.onServiceRegister(service, foreign);
			} catch (Throwable e) {
				_Logger.error("通知监听器失败：" + l, e);
			}
		}
	}

	@Override
	public void unregisterService(String ownerAccessId, Service service) {
		unregisterServiceInner(ownerAccessId, service, false);
	}

	protected ServiceInstance unregisterServiceInner(String ownerAccessId, Service service, boolean foreign) {
		ServiceInstance exist = m_Services.get(ServiceInstance.getId(service));
		if (null == exist || !StringUtil.eq(exist.getOwner(), ownerAccessId)) {
			return null;
		}
		_Logger.info("微服务[" + exist.toStringNameNo() + "]注销" + (foreign ? "(R)" : "(L)"));
		removeService(exist);

		for (ServiceListener l : m_ServiceListeners) {
			try {
				l.onServiceUnregister(exist, foreign);
			} catch (Throwable e) {
				_Logger.error("通知监听器失败：" + l, e);
			}
		}

		return exist;
	}

	private void timeoutService(ServiceInstance service) {
		_Logger.error("微服务[" + service.toStringNameNo() + "]心跳超时");

		ServiceInstanceBalance balance = m_ServiceBalances.get(service.getName());
		if (null != balance) {
			balance.timeout(service);
		}

		notifyServiceTimeout(service);
	}

	private void removeService(ServiceInstance service) {
		m_Services.remove(service.getId());

		ServiceInstanceBalance balance = m_ServiceBalances.get(service.getName());
		if (null != balance) {
			balance.remove(service);
			if (0 == balance.getEndpointCount()) {
				// 与注册并发时，可能抢先移除，重新注册一遍就行，不做同步
				m_ServiceBalances.remove(service.getName());
			}
		}
	}

	@Override
	public void syncServices(List<ServiceInstance> reg, List<ServiceInstance> unreg, boolean complete) {
		if (_Logger.isTraceEnabled()) {
			_Logger.trace(
					"同步微服务：reg=" + (null == reg ? 0 : reg.size()) + ",unreg=" + (null == unreg ? 0 : unreg.size()));
		}
		if (null != reg && reg.size() > 0) {
			for (ServiceInstance s : reg) {
				if (null == s) {
					continue;
				}
				registerServiceInner(new ServiceInstance(s), true);
			}
		}
		if (null != unreg && unreg.size() > 0) {
			for (ServiceInstance s : unreg) {
				if (null == s) {
					continue;
				}
				unregisterServiceInner(s.getOwner(), s, true);
			}
		}

		if (complete) {
			m_Ready = true;
		}
	}
	
	@Override
	public void syncServices(MeshNode meshNode, List<ServiceInstance> reg, List<ServiceInstance> unreg) {
		if (_Logger.isTraceEnabled()) {
			_Logger.trace("同步Mesh[" + meshNode.getId() + "]微服务：reg=" + (null == reg ? 0 : reg.size()) + ",unreg="
					+ (null == unreg ? 0 : unreg.size()));
		}
		if (null != reg && reg.size() > 0) {
			for (ServiceInstance s : reg) {
				if (null == s) {
					continue;
				}
				ServiceInstance si = new ServiceInstance(s);
				si.setMeshNode(meshNode);
				registerServiceInner(si, false);
			}
		}
		if (null != unreg && unreg.size() > 0) {
			for (ServiceInstance s : unreg) {
				if (null == s) {
					continue;
				}
				unregisterServiceInner(s.getOwner(), s, false);
			}
		}
	}

	@Override
	public ResultPage<String> listServiceName(String keyword, String accessGroup) {
		if (m_ServiceBalances.isEmpty()) {
			return ResultPageHelper.empty();
		}
		List<String> result;
		if (StringUtil.isEmpty(keyword)) {
			result = new ArrayList<>(m_ServiceBalances.keySet());
		} else if (!ServiceNameMatcher.hasWildcard(keyword)) {
			if (m_ServiceBalances.containsKey(keyword)) {
				result = Collections.singletonList(keyword);
			} else {
				result = Collections.emptyList();
			}
		} else {
			Set<String> names = m_ServiceBalances.keySet();
			ServiceNameMatcher matcher = ServiceNameMatcher.getInstance(keyword);
			result = new ArrayList<>();
			for (String n : names) {
				if (!matcher.match(n)) {
					continue;
				}
				result.add(n);
			}
		}
		if (!StringUtil.isEmpty(accessGroup) && result.size() > 0) {
			// 按所属access group过滤
			List<String> filterByGroup = new ArrayList<>(result.size());
			for(String name : result) {
				if(isExistService(name, accessGroup)) {
					filterByGroup.add(name);
				}
			}
			result = filterByGroup;
		}
		if (result.size() > 1) {
			Collections.sort(result);
		}
		return ResultPageHelper.toResultPage(result);
	}

	@Override
	public ResultPage<ServiceInstance> listService(String name, String accessGroup) {
		List<ServiceInstance> list = listService0(name, accessGroup, Integer.MAX_VALUE);
		if (list.size() > 1) {
			Collections.sort(list, Service.CMP_BY_NAME);
		}
		return ResultPageHelper.toResultPage(list);
	}
	
	private List<ServiceInstance> listService0(String name, String accessGroup, int maxSize) {
		if (maxSize <= 0) {
			maxSize = Integer.MAX_VALUE;
		}
		List<ServiceInstance> list = null;
		if (StringUtil.isEmpty(name)) {
			name = null;
		}
		if (StringUtil.isEmpty(accessGroup)) {
			accessGroup = null;
		}
		for (ServiceInstance s : m_Services.values()) {
			if (null != name && !s.getName().equals(name)) {
				continue;
			}
			if (null != accessGroup) {
				AccessExt access = m_AccessManage.getAccess(s.getOwner());
				if (null == access || !accessGroup.equals(access.getGroupId())) {
					continue;
				}
			}
			if (1 == maxSize) {
				return Collections.singletonList(s);
			}
			if (null == list) {
				list = new ArrayList<>();
			}
			list.add(s);
			if (list.size() >= maxSize) {
				break;
			}
		}
		return null == list ? Collections.emptyList() : list;
	}
	
	@Override
	public List<ServiceInstance> listValidService(String name) {
		ServiceInstanceBalance balance = m_ServiceBalances.get(name);
		if(null == balance) {
			return Collections.emptyList();
		}
		return balance.listServiceInstance();
	}

	@Override
	public ResultPage<ServiceInstance> searchService(String keyword, String runningId) {
		List<ServiceInstance> list = new ArrayList<>();
		keyword = StringUtil.isEmpty(keyword) ? null : keyword;
		runningId = StringUtil.isEmpty(runningId) ? null : runningId;
		for (ServiceInstance s : m_Services.values()) {
			if (null != keyword && !s.getName().contains(keyword)) {
				continue;
			}
			if (null != runningId && !StringUtil.eq(s.getRunningId(), runningId)) {
				continue;
			}
			list.add(s);
		}
		if (list.size() > 1) {
			Collections.sort(list, Service.CMP_BY_NAME);
		}
		return ResultPageHelper.toResultPage(list);
	}

	@Override
	public void onTrafficRuleChange(TrafficTable table) {
		String serviceName = table.getName();
		ServiceInstanceBalance balance = m_ServiceBalances.get(serviceName);
		if (null == balance) {
			return;
		}
		List<ServiceInstance> services = new ArrayList<>();
		for (ServiceInstance s : m_Services.values()) {
			if (!StringUtil.eq(s.getName(), serviceName)) {
				continue;
			}
			services.add(s);
		}
		balance.reinit(services);
	}

	@Override
	public List<ServiceDocument> getDocuments(String serviceName) {
		ServiceInstanceBalance balance = m_ServiceBalances.get(serviceName);
		if (null == balance) {
			return Collections.emptyList();
		}
		return balance.getDocuments();
	}

	protected void notifyServiceTimeout(ServiceInstance service) {
		for (ServiceListener l : m_ServiceListeners) {
			try {
				l.onServiceTimeout(service);
			} catch (Throwable e) {
				_Logger.error("通知监听器失败：" + l, e);
			}
		}
	}

	protected void notifyServiceOverload(ServiceInstance service) {
		for (ServiceListener l : m_ServiceListeners) {
			try {
				l.onServiceOverload(service);
			} catch (Throwable e) {
				_Logger.error("通知监听器失败：" + l, e);
			}
		}
	}

	protected void notifyServiceUnavailable(ServiceInstance service) {
		for (ServiceListener l : m_ServiceListeners) {
			try {
				l.onServiceUnavailable(service);
			} catch (Throwable e) {
				_Logger.error("通知监听器失败：" + l, e);
			}
		}
	}

	@Override
	public void onPluginLoad(Pluginable plugin) {
		if (plugin instanceof ServiceListener) {
			addListener((ServiceListener) plugin);
		}
	}

	@Override
	public void onPluginUnload(Pluginable plugin) {
		if (plugin instanceof ServiceListener) {
			removeListener((ServiceListener) plugin);
		}
	}

	public LruCache<String, SimpleDocumentImpl> getServiceDocCache() {
		return m_ServiceDocCache;
	}

	public ServiceQuotas getServiceQuotas() {
		return m_ServiceQuotas;
	}

	@Override
	public DtObject debugService(String serviceName, String serviceNo, String scriptSource, String scriptName,
			String scriptArgs) throws DebugServiceException {
		if (StringUtil.isEmpty(serviceName) || StringUtil.isEmpty(serviceNo)) {
			throw new DebugServiceException("未指定微服务名与微服务编号");
		}
		ServiceInstanceBalance balance = m_ServiceBalances.get(serviceName);
		if (null == balance) {
			throw new DebugServiceException("服务不存在：" + serviceName);
		}
		return balance.debug(serviceNo, scriptSource, scriptName, scriptArgs);
	}

	@Override
	public void onGcCleanup(int policy) {
		if (POLICY_LOW != policy && POLICY_CRITICAL != policy) {
			return;
		}
		if (null != m_ServiceDocCache) {
			m_ServiceDocCache.onGcCleanup(policy);
		}
	}

	@Override
	public ResultPage<ServiceSummary> listServiceSummary(String keyword, String accessGroup) {
		ResultPage<String> names = listServiceName(keyword, accessGroup);
		List<ServiceSummary> summarys = new ArrayList<ServiceSummary>(names.getCount());
		for (String name : ResultPageHelper.toForeach(names)) {
			ServiceInstanceBalance balance = m_ServiceBalances.get(name);
			if (null == balance) {
				continue;
			}
			NameItem status = balance.getEndpointSummary();
			ServiceSummary s = new ServiceSummary(name);
			s.setStatus(status.id);
			s.setSummary(status.name);
			summarys.add(s);
		}
		Collections.sort(summarys, ServiceSummary.CMP_DEFAULT);
		return ResultPageHelper.toResultPage(summarys);
	}
	
	protected ServiceInstanceBalance getServiceInstanceBalance(String name) {
		return m_ServiceBalances.get(name);
	}
	
	@Override
	public boolean isExistService(String name, String accessGroup) {
		return listService0(name, accessGroup, 1).size() > 0;
	}
}
