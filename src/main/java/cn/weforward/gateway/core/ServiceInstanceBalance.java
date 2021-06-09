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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.NameItem;
import cn.weforward.common.util.LruCache;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.TransList;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.StreamTunnel;
import cn.weforward.gateway.Tunnel;
import cn.weforward.gateway.exception.BalanceException;
import cn.weforward.gateway.exception.DebugServiceException;
import cn.weforward.gateway.exception.QuotasException;
import cn.weforward.gateway.ops.trace.ServiceTracer;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.ResponseConstants;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.aio.netty.NettyHttpClientFactory;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.doc.ServiceDocument;
import cn.weforward.protocol.exception.WeforwardException;
import cn.weforward.protocol.ext.Producer;
import cn.weforward.protocol.ops.AccessExt;
import cn.weforward.protocol.ops.ServiceExt;
import cn.weforward.protocol.ops.traffic.TrafficTableItem;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * 微服务实例的调度器
 * 
 * @author zhangpengji
 *
 */
public class ServiceInstanceBalance {
	static final Logger _Logger = LoggerFactory.getLogger(ServiceInstanceBalance.class);

	protected GatewayImpl m_Gateway;
	protected String m_Name;

	/** 微服务端点列表 */
	protected ServiceEndpoint[] m_Endpoints;
	/** 有效的端点数 */
	protected volatile int m_EndpointValids;
	/** 所有端点的并发数 */
	protected AtomicInteger m_Concurrent;
	/** 微服务文档的加载锁 */
	private final Object m_DocLock = new Object();

	public ServiceInstanceBalance(GatewayImpl gateway, String name) {
		m_Gateway = gateway;
		m_Name = name;
		m_Concurrent = new AtomicInteger(0);
	}
	
	public String getName() {
		return m_Name;
	}

	public synchronized void reinit(List<ServiceInstance> services) {
		ServiceEndpoint[] eps = new ServiceEndpoint[services.size()];
		int offset = 0;
		for (ServiceInstance s : services) {
			ServiceEndpoint ep = openEndpoint(s);
			if (null != ep) {
				eps[offset++] = ep;
			}
		}
		reinit(Arrays.copyOf(eps, offset));
	}

	private synchronized void reinit(ServiceEndpoint... elements) {
		if (null == elements || 0 == elements.length) {
			m_Endpoints = null;
			return;
		}
		m_Endpoints = elements;
	}

	public long getRpcCount() {
		ServiceEndpoint[] endpoints = m_Endpoints;
		if (null == endpoints) {
			return 0;
		}
		long count = 0;
		for (ServiceEndpoint ep : endpoints) {
			count += ep.times;
		}
		return count;
	}

	public long getRpcConcurrent() {
		ServiceEndpoint[] endpoints = m_Endpoints;
		if (null == endpoints) {
			return 0;
		}
		long count = 0;
		for (ServiceEndpoint ep : endpoints) {
			count += ep.concurrent;
		}
		return count;
	}

	public long getRpcFail() {
		ServiceEndpoint[] endpoints = m_Endpoints;
		if (null == endpoints) {
			return 0;
		}
		long count = 0;
		for (ServiceEndpoint ep : endpoints) {
			count += ep.failTotal;
		}
		return count;
	}

	public int getEndpointCount() {
		return null == m_Endpoints ? 0 : m_Endpoints.length;
	}

	public synchronized void put(ServiceInstance service) {
		if (null == m_Endpoints) {
			ServiceEndpoint element = openEndpoint(service);
			if (null != element) {
				reinit(element);
			}
			return;
		}

		ServiceEndpoint[] endpoints = m_Endpoints;
		int idx = indexOf(endpoints, service);
		if (-1 == idx) {
			// 新服务项
			ServiceEndpoint ep = openEndpoint(service);
			if (null != ep) {
				add(ep);
			}
			return;
		}
		ServiceEndpoint agent = endpoints[idx];
		if (agent.updateService(service)) {
			// 微服务实例无变化
			return;
		}
		// 更新资源项
		ServiceEndpoint ep = openEndpoint(service);
		if (null != ep) {
			// 替换
			replace(idx, ep);
		} else {
			// 移除
			remove(idx);
		}
	}

	public synchronized void remove(ServiceInstance service) {
		if (null == m_Endpoints) {
			return;
		}
		ServiceEndpoint[] endpoints = m_Endpoints;
		int idx = indexOf(endpoints, service);
		if (-1 != idx) {
			remove(idx);
		}
	}

	public synchronized void timeout(ServiceInstance service) {
		if (null == m_Endpoints) {
			return;
		}
		ServiceEndpoint[] endpoints = m_Endpoints;
		int idx = indexOf(endpoints, service);
		if (-1 != idx) {
			endpoints[idx].setFailTimeout(24 * 3600 * 1000);
		}
	}

	private static int indexOf(ServiceEndpoint[] endpoints, ServiceInstance service) {
		int idx = -1;
		for (int i = 0; i < endpoints.length; i++) {
			ServiceEndpoint ep = endpoints[i];
			if (ep.getId().equals(service.getId())) {
				idx = i;
				break;
			}
		}
		return idx;
	}

	private synchronized void add(ServiceEndpoint ep) {
		ServiceEndpoint[] eps = cloneEndpoints(1);
		eps[eps.length - 1] = ep;
		reinit(eps);
	}

	private ServiceEndpoint[] cloneEndpoints(int extend) {
		ServiceEndpoint[] old = m_Endpoints;
		if (null == old) {
			return null;
		}
		return Arrays.copyOf(old, old.length + extend);
	}

	private synchronized void replace(int idx, ServiceEndpoint ep) {
		ServiceEndpoint[] eps = cloneEndpoints(0);
		eps[idx] = ep;
		reinit(eps);
	}

	private synchronized void remove(int idx) {
		ServiceEndpoint[] oldArr = m_Endpoints;
		if (1 == oldArr.length) {
			// 只有一项，直接清空
			reinit();
			return;
		}
		ServiceEndpoint[] newArr = new ServiceEndpoint[oldArr.length - 1];
		if (idx > 0) {
			System.arraycopy(oldArr, 0, newArr, 0, idx);
		}
		if (newArr.length - idx > 0) {
			System.arraycopy(oldArr, idx + 1, newArr, idx, newArr.length - idx);
		}
		reinit(newArr);
	}

	/**
	 * 打开一个资源项。若流量规则无效，则返回null
	 * 
	 * @param ownerAccessId
	 * @param info
	 * @return
	 */
	private ServiceEndpoint openEndpoint(ServiceInstance service) {
		TrafficTableItem rule = findRule(service);
		if (null == rule || 0 == rule.getWeight()) {
			// 实例不可达
			service.setInaccessible(true);
			return null;
		}
		service.setInaccessible(false);

		ServiceEndpoint ep = ServiceEndpoint.openEndpoint(this, service, rule);
		MeterRegistry registry = m_Gateway.m_MeterRegistry;
		if (null != ep && null != registry) {
			ep.startGauge(m_Gateway.m_ServerId, registry);
		}
		return ep;
	}

	TrafficTableItem findRule(Service service) {
		return m_Gateway.m_TrafficManage.findTrafficRule(service);
	}

	AccessExt getAccess(String id) {
		return m_Gateway.m_AccessManage.getAccess(id);
	}

	Access getValidAccess(String id) {
		return m_Gateway.m_AccessManage.getValidAccess(id);
	}

	Access getInternalAccess() {
		return m_Gateway.m_AccessManage.getInternalAccess();
	}

	// int getEndpointValids(int max) {
	// int valids = m_EndpointValids;
	// if (valids >= max) {
	// return valids;
	// }
	// ServiceEndpoint[] eps = m_Endpoints;
	// if (valids >= eps.length || 1 == eps.length) {
	// return valids;
	// }
	// // 只能重新算了
	// valids = 0;
	// for (ServiceEndpoint ep : eps) {
	// if (ep.isOverload() || ep.isFailDuring()) {
	// continue;
	// }
	// if (++valids >= max) {
	// break;
	// }
	// }
	// return valids;
	// }
	
	ServiceEndpoint get(String no, String version) throws BalanceException {
		return get(no, version, Collections.emptyList(), false);
	}
	
	ServiceEndpoint get(String no, String version, List<String> excludeNos) throws BalanceException {
		return get(no, version, excludeNos, false);
	}

	/**
	 * 根据负载均衡规则获取微服务实例端点。
	 * <p>
	 * 使用完成后回调<code>free()</code>方法
	 * 
	 * @param no
	 * @param version
	 * @param excludeNos
	 * @return
	 * @see #free(ServiceEndpoint, int)
	 * @throws BalanceException
	 */
	ServiceEndpoint get(String no, String version, List<String> excludeNos, boolean onlySelfMesh) throws BalanceException {
		int concurrent = m_Concurrent.get();
		int quota = getQuotas().getQuota(m_Name, concurrent);
		if (concurrent > quota) {
			throw QuotasException.fullQuotas(m_Name,
					"{满额:" + concurrent + "/" + quota + ", max:" + getQuotas().getMax());
		}

		boolean onlyBackup = false;
		if (StringUtil.isEmpty(no)) {
			no = null;
		} else if (ResponseConstants.FORWARD_TO_BACKUP.equals(no)) {
			onlyBackup = true;
			no = null;
		}
		if(null == excludeNos) {
			excludeNos = Collections.emptyList();
		}
		ServiceEndpoint[] eps = m_Endpoints;
		if (1 == eps.length) {
			// 只有一项时，其它的逻辑都是多余的
			ServiceEndpoint best = eps[0];
			if (null == best) {
				// 还有这种事？
				m_EndpointValids = 0;
				return null;
			}
			synchronized (eps) {
				if (best.isOverload()) {
					m_EndpointValids = 0;
					throw BalanceException.overload(m_Name, String.valueOf(best));
				}
				if (best.isFailDuring()) {
					m_EndpointValids = 0;
					// 先重罢失败状态，避免下次还是没能获取
					best.resetAtFail(BalanceElement.FAIL_RESET_MIN_TIMEOUT);
					throw BalanceException.failDuring(m_Name, String.valueOf(best));
				}
				m_EndpointValids = 1;
				if (!best.matchVersion(version)) {
					throw BalanceException.versionNotMatch(m_Name, String.valueOf(best));
				}
				if ((onlyBackup && !best.isBackup()) || best.matchNos(excludeNos)) {
					throw BalanceException.exclude(m_Name, String.valueOf(best));
				}
				if (onlySelfMesh && !best.isSelfMesh()) {
					throw BalanceException.noSelfMesh(m_Name, String.valueOf(best));
				}
				// best.use();
				use(best);
				return best;
			}
		}

		ServiceEndpoint best = null;
		int overloadCount = 0;
		int failCount = 0;
		int ew;
		int total = 0;
		boolean haveBackup = false;
		boolean isReferto = false;
		int valids = 0;

		// 以下这段算法参考自Nginx RR算法
		// ngx_http_upstream_get_peer(ngx_http_upstream_rr_peer_data_t *rrp)
		for (int i = 0; i < eps.length; i++) {
			ServiceEndpoint element = eps[i];
			if (element.isBackup()) {
				haveBackup = true;
			}
			if (element.isOverload()) {
				++overloadCount;
				// 略过过载项
				// _Logger.warn("overload:" + element);
				continue;
			}
			if (element.isFailDuring()) {
				++failCount;
				// 略过失败项
				// _Logger.warn("overload:" + element);
				continue;
			}
			valids++;
			// 最后再比较版本，让overloadCount、failCount可以正常统计
			if (!element.matchVersion(version)) {
				continue;
			}
			if (element.matchNos(excludeNos)) {
				continue;
			}
			if (onlySelfMesh && !element.isSelfMesh()) {
				continue;
			}

			ew = (element.effectiveWeight > 0) ? element.effectiveWeight : 0;
			element.currentWeight += ew;
			total += ew;
			if (element.effectiveWeight < element.weight) {
				element.effectiveWeight++;
			}

			if (null != no && element.matchNo(no)) {
				// 使用符合referto的资源项
				if (!isReferto || element.currentWeight > best.currentWeight) {
					best = element;
					isReferto = true;
				}
				continue;
			}
			// if((onlyBackup && !element.isBackup()) || (!onlyBackup &&
			// element.isBackup()))
			if (onlyBackup != element.isBackup()) {
				continue;
			}

			if (best == null || (!isReferto && element.currentWeight > best.currentWeight)) {
				best = element;
			}
		}

		if (best == null && haveBackup && !onlyBackup) {
			// 只好在在后备资源中找
			for (int i = eps.length - 1; i >= 0; i--) {
				ServiceEndpoint element = eps[i];
				if (element.isOverload()) {
					// 过载保护
					continue;
				}
				if (element.isFailDuring()) {
					// 略过失败的项
					continue;
				}
				// 最后再比较版本，让overloadCount、failCount可以正常统计
				if (!element.matchVersion(version)) {
					continue;
				}
				if (element.matchNos(excludeNos)) {
					continue;
				}
				if (onlySelfMesh && !element.isSelfMesh()) {
					continue;
				}

				ew = (element.effectiveWeight > 0) ? element.effectiveWeight : 0;
				element.currentWeight += ew;
				// if (element.isBackup()) {
				total += ew;
				// }
				if (element.effectiveWeight < element.weight) {
					element.effectiveWeight++;
				} else if (0 == element.effectiveWeight) {
					element.effectiveWeight = 1;
				}

				if (best == null || element.currentWeight > best.currentWeight) {
					best = element;
				}
			}
		}

		if (best == null) {
			// 没有best，重置所有失败项，避免下次还是没能获取
			if (eps.length == failCount) {
				for (int i = eps.length - 1; i >= 0; i--) {
					ServiceEndpoint element = eps[i];
					element.resetAtFail(BalanceElement.FAIL_RESET_MIN_TIMEOUT);
				}
				String err;
				// Quotas quotas = m_Quotas;
				// if (null == quotas) {
				err = "全失败{fail:" + failCount + ",over:" + overloadCount + "}";
				// } else {
				// err = "全失败{fail:" + failCount + ",over:" + overloadCount
				// + ",quotas:"
				// + quotas + "}";
				// }
				// throw new FailException(err);
				throw BalanceException.allFail(m_Name, err);
			}
			if (eps.length == overloadCount) {
				String err;
				// Quotas quotas = m_Quotas;
				// if (null == quotas) {
				err = "全过载{fail:" + failCount + ",over:" + overloadCount + "}";
				// } else {
				// err = "全过载{fail:" + failCount + ",over:" + overloadCount
				// + ",quotas:"
				// + quotas + "}";
				// }
				// throw new OverloadException(err);
				throw BalanceException.allOverload(m_Name, err);
			}
			String err;
			// Quotas quotas = m_Quotas;
			// if (null == quotas) {
			err = "全忙{fail:" + failCount + ",over:" + overloadCount + ",exclude:" + excludeNos + ",backup:" + onlyBackup
					+ ",mesh:" + onlySelfMesh + ",res:" + Arrays.toString(eps) + "}";
			// } else {
			// err = "全忙{fail:" + failCount + ",over:" + overloadCount +
			// ",quotas:" + quotas
			// + "res:" + element + "}";
			// }
			throw BalanceException.allBusy(m_Name, err);
		}

		m_EndpointValids = valids;
		best.currentWeight -= total;
		// best.use();
		use(best);
		return best;
	}

	/**
	 * 指定编号获取微服务实例端点
	 * <p>
	 * 使用完成后回调<code>free()</code>方法
	 *
	 * @param no
	 * @param excludes
	 * @return
	 * @see #free(ServiceEndpoint, int)
	 * @throws BusyException
	 */
	ServiceEndpoint select(String no) throws BalanceException {
		if (StringUtil.isEmpty(no)) {
			throw BalanceException.noNotMatch(m_Name, "无匹配的编号:" + no);
		}
		
		ServiceEndpoint[] eps = m_Endpoints;
		ServiceEndpoint best = null;
		for (ServiceEndpoint ep : eps) {
			if (!ep.matchNo(no)) {
				continue;
			}
			best = ep;
			if (best.isOverload()) {
				throw BalanceException.overload(m_Name, String.valueOf(best));
			}
			if (best.isFailDuring()) {
				throw BalanceException.failDuring(m_Name, String.valueOf(best));
			}
			break;
		}
		if (null == best) {
			throw BalanceException.noNotMatch(m_Name, "无匹配的编号:" + no);
		}
		// best.use();
		use(best);
		return best;
	}

	private ServiceQuotas getQuotas() {
		return m_Gateway.getServiceQuotas();
	}

	/**
	 * 列举所有可用的端点
	 * 
	 * @return
	 */
	List<ServiceEndpoint> list() {
		ServiceEndpoint[] eps = m_Endpoints;
		if (null == eps || 0 == eps.length) {
			return Collections.emptyList();
		}
		List<ServiceEndpoint> result = new ArrayList<ServiceEndpoint>();
		for (ServiceEndpoint ep : eps) {
			if (ep.isOverload() || ep.isFailDuring()) {
				continue;
			}
			result.add(ep);
		}
		return result;
	}

	private void use(ServiceEndpoint endpoint) throws BalanceException {
		int concurrent = m_Concurrent.get();
		getQuotas().use(m_Name, concurrent);

		m_Concurrent.incrementAndGet();

		endpoint.use();
	}

	/**
	 * 释放微服务实例端点
	 * 
	 * @param endpoint
	 * @param state
	 */
	void free(ServiceEndpoint endpoint, int state) {
		getQuotas().free(m_Name);

		m_Concurrent.decrementAndGet();

		endpoint.free(state);
	}

	void onEndpointOverload(ServiceEndpoint ep) {
		m_Gateway.notifyServiceOverload(ep.getService());
	}

	void onEndpointUnavailable(ServiceEndpoint ep) {
		m_Gateway.notifyServiceUnavailable(ep.getService());
	}

	public void joint(Tunnel tunnel) {
		if (null == m_Endpoints) {
			tunnel.responseError(null, WeforwardException.CODE_SERVICE_NOT_FOUND, "微服务[" + m_Name + "]无可用实例");
			return;
		}
		
		if (Header.CHANNEL_NOTIFY.equals(tunnel.getHeader().getChannel())) {
			// 由NotifyBridger接管通知处理
			NotifyBridger bridger;
			try {
				bridger = new NotifyBridger(this, tunnel);
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
				tunnel.responseError(null, WeforwardException.CODE_INTERNAL_ERROR, "内部错误");
				return;
			}
			bridger.connect();
			return;
		}

		ServiceEndpoint ep;
		String serviceNo = tunnel.getHeader().getTag();
		String serviceVersion = tunnel.getVersion();
		try {
			ep = get(serviceNo, serviceVersion);
		} catch (BalanceException e) {
			_Logger.warn(e.toString());
			int code = (e instanceof QuotasException) ? WeforwardException.CODE_GATEWAY_BUSY
					: WeforwardException.CODE_SERVICE_BUSY;
			tunnel.responseError(null, code, "微服务[" + m_Name + "]忙：" + e.getKeyword());
			return;
		}

		if (!ep.getService().isForwardEnable() || m_Endpoints.length <= 1) {
			// 没启用转发，或者只有一个Endpoint
			ep.connect(tunnel, false);
			return;
		}
		int endpointValids = m_EndpointValids;
		if (endpointValids <= 1) {
			ep.connect(tunnel, false);
			return;
		}
		// 由ForwardBridger接管转发处理
		int maxForward = Math.min(Configure.getInstance().getServiceForwardCount(), endpointValids - 1);
		ForwardBridger forwardBridger;
		try {
			forwardBridger = new ForwardBridger(this, tunnel, maxForward);
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
			tunnel.responseError(null, WeforwardException.CODE_INTERNAL_ERROR, "内部错误");
			return;
		}
		forwardBridger.connect(ep);
	}
	
	public void jointByMesh(Tunnel tunnel) {
		String serviceNo = tunnel.getHeader().getServiceNo();
		ServiceEndpoint ep;
		try {
			ep = select(serviceNo);
		} catch (BalanceException e) {
			_Logger.warn(e.toString());
			int code;
			if(BalanceException.CODE_NO_NOT_MATCH.id == e.getCode() || BalanceException.CODE_FAIL_DURING.id == e.getCode()) {
				code =  WeforwardException.CODE_SERVICE_UNAVAILABLE;
			} else {
				code = WeforwardException.CODE_SERVICE_BUSY;
			}
			tunnel.responseError(null, code, "微服务[" + m_Name + "]网格中继失败：" + e.getKeyword());
			return;
		}
		if (null != ep.getService().getMeshNode()) {
			tunnel.responseError(null, WeforwardException.CODE_INVOKE_DENIED,
					"微服务实例[" + ep.getService().toStringNameNo() + "]不在此网格");
			return;
		}
		ep.connect(tunnel, false);
		return;
	}

	public void joint(StreamTunnel tunnel) {
		if (null == m_Endpoints) {
			tunnel.responseError(null, StreamTunnel.CODE_NOT_FOUND, "微服务[" + m_Name + "]无可用实例");
			return;
		}
		ServiceEndpoint ep;
		try {
			ep = get(null, null, null, true);
		} catch (BalanceException e) {
			_Logger.warn("微服务[" + m_Name + "]忙：" + e.getMessage());
			int code = (e instanceof QuotasException) ? StreamTunnel.CODE_UNAVAILABLE
					: StreamTunnel.CODE_INTERNAL_ERROR;
			tunnel.responseError(null, code, "微服务[" + m_Name + "]忙：" + e.getKeyword());
			return;
		}
		ep.connect(tunnel);
	}

	int getResourceRight(Access access, String resId) {
		return m_Gateway.m_AclManage.findResourceRight(m_Name, access, resId);
	}

	ServiceTracer getServiceTracer() {
		return m_Gateway.m_ServiceTracer;
	}

	NettyHttpClientFactory getHttpClientFactory() {
		return m_Gateway.getHttpClientFactory();
	}

	Producer getProducer() {
		return m_Gateway.m_Producer;
	}

	public List<ServiceDocument> getDocuments() {
		ServiceEndpoint[] eps = m_Endpoints;
		if (null == eps) {
			return Collections.emptyList();
		}
		synchronized (m_DocLock) {
			// 单实例
			if (1 == eps.length) {
				ServiceEndpoint ep = eps[0];
				String ver = StringUtil.toString(ep.getService().getVersion());
				SimpleDocument doc = getDocumentInCache(ver);
				if (null == doc || doc.isTimeout()) {
					doc = ep.getDocument();
					if (null != doc) {
						putDocumentToCache(ver, doc);
					}
				}
				if (null != doc) {
					return Collections.singletonList(doc);
				} else {
					return Collections.emptyList();
				}
			}
			// 多实例
			Map<String, SimpleDocument> docs = new HashMap<String, SimpleDocument>();
			for (ServiceEndpoint ep : eps) {
				String ver = StringUtil.toString(ep.getService().getVersion());
				SimpleDocument doc = docs.get(ver);
				if (null != doc) {
					continue;
				}
				doc = getDocumentInCache(ver);
				if (null == doc || doc.isTimeout()) {
					try {
						// 选最合适的实例加载文档
						doc = get(null, ver).getDocument();
					} catch (BalanceException e) {
						doc = SimpleDocument.loadFail(m_Name, ver, e.getMessage());
					}
					putDocumentToCache(ver, doc);
				}
				docs.put(ver, doc);
			}
			return new ArrayList<ServiceDocument>(docs.values());
		}
	}

	private SimpleDocument getDocumentInCache(String version) {
		LruCache<String, SimpleDocument> cache = m_Gateway.getServiceDocCache();
		if (null == cache) {
			return null;
		}
		String key = m_Name + "-" + version;
		return cache.get(key);
	}

	private void putDocumentToCache(String version, SimpleDocument doc) {
		LruCache<String, SimpleDocument> cache = m_Gateway.getServiceDocCache();
		if (null == cache) {
			return;
		}
		String key = m_Name + "-" + version;
		cache.put(key, doc);
	}

	Executor getRpcExecutor() {
		return m_Gateway.m_RpcExecutor;
	}

	DtObject debug(String no, String source, String name, String args) throws DebugServiceException {
		ServiceEndpoint[] eps = m_Endpoints;
		if (null == eps) {
			throw new DebugServiceException("微服务[" + m_Name + "]无可用实例");
		}
		ServiceEndpoint target = null;
		for (ServiceEndpoint ep : eps) {
			if (StringUtil.eq(ep.getService().getNo(), no)) {
				target = ep;
				break;
			}
		}
		if (null == target) {
			throw new DebugServiceException("微服务[" + m_Name + "]无此实例[" + no + "]");
		}
		return target.debug(source, name, args);
	}

	public NameItem getEndpointSummary() {
		ServiceEndpoint[] eps = m_Endpoints;
		if (null == eps || 0 == eps.length) {
			return NameItem.valueOf("无可用实例", 9);
		}
		boolean allNorm = true;
		boolean allAbnorm = true;
		for (ServiceEndpoint ep : eps) {
			int state = ep.getService().getState();
			if (0 == state) {
				allAbnorm = false;
				continue;
			}
			if (0 != state && ServiceExt.STATE_INACCESSIBLE != state) {
				allNorm = false;
				continue;
			}
		}
		if (allNorm) {
			return NameItem.valueOf("正常", 0);
		}
		if (allAbnorm) {
			return NameItem.valueOf("全异常", 9);
		}
		return NameItem.valueOf("部分异常", 5);
	}
	
	public List<ServiceInstance> listServiceInstance(){
		List<ServiceEndpoint> eps = list();
		if(eps.isEmpty()) {
			return Collections.emptyList();
		}
		return new TransList<ServiceInstance, ServiceEndpoint>(eps) {

			@Override
			protected ServiceInstance trans(ServiceEndpoint src) {
				return src.getService();
			}
		};
	}

	@Override
	public String toString() {
		ServiceEndpoint[] eps = m_Endpoints;
		return m_Name + ",eps:" + (null == eps ? 0 : eps.length);
	}
}
