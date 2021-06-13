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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.json.JsonNode;
import cn.weforward.common.json.JsonObject;
import cn.weforward.common.json.JsonPair;
import cn.weforward.common.json.JsonParseAbort;
import cn.weforward.common.json.JsonUtil;
import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.VersionUtil;
import cn.weforward.gateway.Pipe;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.StreamPipe;
import cn.weforward.gateway.StreamTunnel;
import cn.weforward.gateway.Tunnel;
import cn.weforward.gateway.exception.DebugServiceException;
import cn.weforward.gateway.ops.trace.ServiceTracer;
import cn.weforward.gateway.util.SyncTunnel;
import cn.weforward.metrics.WeforwardMetrics;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.Request;
import cn.weforward.protocol.RequestConstants;
import cn.weforward.protocol.ResponseConstants;
import cn.weforward.protocol.aio.netty.NettyHttpClientFactory;
import cn.weforward.protocol.client.ext.RequestInvokeObject;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.ops.trace.ServiceTraceToken;
import cn.weforward.protocol.ops.traffic.TrafficTableItem;
import cn.weforward.protocol.support.datatype.FriendlyObject;
import cn.weforward.protocol.support.datatype.SimpleDtObject;
import cn.weforward.protocol.support.doc.ServiceDocumentVo;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

/**
 * 微服务实例的端点
 * 
 * @author zhangpengji
 *
 */
public abstract class ServiceEndpoint extends BalanceElement {
	protected final static Logger _Logger = LoggerFactory.getLogger(ServiceEndpoint.class);

	protected ServiceInstanceBalance m_Balance;
	protected ServiceInstance m_Service;
	/** 连接超时，秒 */
	protected int m_ConnectTimeout;
	/** 读取超时，秒 */
	protected int m_ReadTimeout;

	protected ServiceEndpoint(ServiceInstanceBalance balance, ServiceInstance service, TrafficTableItem rule) {
		super(rule.getWeight());
		setMaxFails(rule.getMaxFails());
		setFailTimeout(rule.getFailTimeout() * 1000);
		setMaxConcurrent(rule.getMaxConcurrent());
		m_Balance = balance;
		m_Service = service;
		m_ConnectTimeout = rule.getConnectTimeout();
		m_ReadTimeout = rule.getReadTimeout();
	}

	public static ServiceEndpoint openEndpoint(ServiceInstanceBalance group, ServiceInstance service,
			TrafficTableItem rule) {
		if (ListUtil.isEmpty(service.getUrls())) {
			return null;
		}
		return new ServiceEndpointImpl(group, service, rule);
	}

	void startGauge(String gatewayId, MeterRegistry registry) {
		Tags tags = WeforwardMetrics.TagHelper.of(WeforwardMetrics.TagHelper.gatewayId(gatewayId),
				WeforwardMetrics.TagHelper.serviceName(getName()),
				WeforwardMetrics.TagHelper.serviceNo(m_Service.getNo()));
		Gauge.builder(WeforwardMetrics.GATEWAY_SERVICE_RPC_COUNT, this, ServiceEndpoint::getTimes).tags(tags)
				.register(registry);
		Gauge.builder(WeforwardMetrics.GATEWAY_SERVICE_RPC_CONCURRENT, this, ServiceEndpoint::getConcurrent).tags(tags)
				.register(registry);
		Gauge.builder(WeforwardMetrics.GATEWAY_SERVICE_RPC_FAIL, this, ServiceEndpoint::getFailTotal).tags(tags)
				.register(registry);
	}

	public String getId() {
		return m_Service.getId();
	}

	ServiceInstance getService() {
		return m_Service;
	}

	String getName() {
		return m_Service.getName();
	}

	@Override
	public String getElementName() {
		return m_Service.toStringNameNo();
	}

	NettyHttpClientFactory getHttpClientFactory() {
		return m_Balance.getHttpClientFactory();
	}

	Access getValidAccess(String accessId) {
		return m_Balance.getValidAccess(accessId);
	}

	Access getInternalAccess() {
		return m_Balance.getInternalAccess();
	}

	int getResourceRight(Access access, String resId) {
		return m_Balance.getResourceRight(access, resId);
	}

	/**
	 * 更新微服务信息
	 * 
	 * @param service
	 * @return 若可以更新自身则返回true，否则返回false
	 */
	boolean updateService(ServiceInstance service) {
		if (!checkService(service)) {
			return false;
		}

		// 继承原状态
		if (m_Service.isOverload()) {
			service.setOverload(true);
		}
		if (m_Service.isUnavailable()) {
			service.setUnavailable(true);
		}
		m_Service = service;
		return true;
	}

	private boolean checkService(ServiceInstance service) {
		ServiceInstance s1 = this.getService();
		ServiceInstance s2 = service;
		if (s1.isTimeout()) {
			// 旧的已超时，需要重置failtimeout
			return false;
		}
		// 检查no、version
		if (!StringUtil.eq(s1.getNo(), s2.getNo()) || !StringUtil.eq(s1.getVersion(), s2.getVersion())) {
			// 有变化
			return false;
		}
		// 检查url
		List<String> url1 = s1.getUrls();
		List<String> url2 = s2.getUrls();
		if (url1.size() != url2.size()) {
			return false;
		}
		if (url1.size() > 1) {
			// 拷贝、排序后在比较
			url1 = new ArrayList<String>(url1);
			url2 = new ArrayList<String>(url2);
			Collections.sort(url1);
			Collections.sort(url2);
		}
		if (!url1.equals(url2)) {
			return false;
		}
		if(s1.getClientChannel() != s2.getClientChannel()) {
			return false;
		}
		// 若所在网格变了，编号、链接应该都变了，故省略以下判断
		// if(!StringUtil.eq(s1.getMeshNodeId(), s2.getMeshNodeId())) {
		// return false;
		// }
		return true;
	}

	boolean matchVersion(String tag) {
		if (StringUtil.isEmpty(tag)) {
			return true;
		}
		String src = m_Service.getVersion();
		int c = VersionUtil.compareTo(src, tag);
		if (0 == c) {
			// 版本相同
			return true;
		}
		if (c < 0) {
			// 微服务实例的版本较低
			return false;
		}
		// 目标版本比实例的版本低，接着比较实例的兼容版本
		String compatible = m_Service.getCompatibleVersion();
		if (StringUtil.isEmpty(compatible)) {
			return false;
		}
		return VersionUtil.compareTo(compatible, tag) <= 0;
	}

	boolean matchNo(String no) {
		return StringUtil.eq(m_Service.getNo(), no);
	}

	boolean matchNos(List<String> nos) {
		if (ListUtil.isEmpty(nos)) {
			return false;
		}
		return nos.contains(m_Service.getNo());
	}

	boolean isSelfMesh() {
		return m_Service.isSelfMesh();
	}

	/**
	 * 与微服务端建立连接
	 * 
	 * @param tunnel
	 * @param supportForward 是否支持转发
	 */
	public Pipe connect(Tunnel tunnel, boolean supportForward) {
		return openPipe(tunnel, supportForward);
	}

	protected ServiceTraceToken createTraceToken(Tunnel tunnel) {
		ServiceTracer tracer = m_Balance.getServiceTracer();
		return tracer.onBegin(tunnel.getTraceToken(), getService());
	}

	protected abstract Pipe openPipe(Tunnel tunnel, boolean supportForward);

	/**
	 * 与微服务端建立连接
	 * 
	 * @param tunnel
	 * @param supportForward 是否支持转发
	 */
	public StreamPipe connect(StreamTunnel tunnel) {
		return openPipe(tunnel);
	}

	protected abstract StreamPipe openPipe(StreamTunnel tunnel);

	protected String toStringNameNo() {
		ServiceInstance service = getService();
		// return service.getName() + "(" + StringUtil.toString(service.getNo()) + ")";
		return service.toStringNameNo();
	}

	boolean isTrust() {
		// FIXME 暂时假设所有微服务的通信都是安全的
		return true;
	}

	Executor getRpcExecutor() {
		return m_Balance.getRpcExecutor();
	}

	int getReadTimeout() {
		return m_ReadTimeout;
	}

	void end(Pipe pipe, int state, ServiceTraceToken token) {
		m_Balance.free(this, state);
		m_Balance.getServiceTracer().onFinish(token);
	}

	void end(StreamPipe pipe, int state) {
		m_Balance.free(this, state);
	}

	@Override
	boolean isOverload() {
		if (super.isOverload()) {
			if (!m_Service.isOverload()) {
				m_Service.setOverload(true);
				m_Balance.onEndpointOverload(this);
			}
			return true;
		}
		m_Service.setOverload(false);
		return false;
	}

	@Override
	boolean isFailDuring() {
		if (super.isFailDuring()) {
			if (!m_Service.isUnavailable()) {
				m_Service.setUnavailable(true);
				m_Balance.onEndpointUnavailable(this);
			}
			return true;
		}
		m_Service.setUnavailable(false);
		return false;
	}

	SimpleDocumentImpl getDocument() {
		ServiceInstance service = getService();
		String name = service.getName();
		String method = service.getDocumentMethod();
		if (StringUtil.isEmpty(method)) {
			return null;
		}
		SimpleDtObject invokeObject = new SimpleDtObject();
		invokeObject.put(RequestConstants.METHOD, method);
		SyncTunnel tunnel = new SyncTunnel(name, invokeObject);
		tunnel.setAccess(getInternalAccess());
		tunnel.setWaitTimeout(10);
		connect(tunnel, false);

		FriendlyObject result;
		try {
			int code = tunnel.getCode();
			if (0 != code) {
				String errMsg = code + "/" + tunnel.getMsg();
				_Logger.warn("微服务[" + toStringNameNo() + "]获取文档出错：" + errMsg);
				return SimpleDocumentImpl.loadFail(service, errMsg);
			}
			result = FriendlyObject.valueOf(tunnel.getResult());
		} catch (Throwable e) {
			String errMsg = "微服务[" + toStringNameNo() + "]获取文档出错";
			_Logger.warn(errMsg, e);
			return SimpleDocumentImpl.loadFail(service, e.toString());
		}
		int resultCode = result.getInt("code", 0);
		if (0 != resultCode) {
			String errMsg = resultCode + "/" + result.getString("msg");
			_Logger.warn("微服务[" + toStringNameNo() + "]获取文档出错：" + errMsg);
			return SimpleDocumentImpl.loadFail(service, errMsg);
		}
		DtObject resultContent = result.getObject("content");
		if (null == resultContent) {
			return null;
		}
		ServiceDocumentVo vo;
		try {
			vo = ServiceDocumentVo.MAPPER.fromDtObject(resultContent);
		} catch (Exception e) {
			String errMsg = "微服务[" + toStringNameNo() + "]解析文档出错";
			_Logger.error(errMsg, e);
			return SimpleDocumentImpl.loadFail(service, errMsg);
		}
		return new SimpleDocumentImpl(vo);
	}

	DtObject debug(String source, String name, String args) throws DebugServiceException {
		ServiceInstance service = getService();
		String methodName = service.getDebugMethod();
		if (StringUtil.isEmpty(methodName)) {
			throw new DebugServiceException("微服务[" + toStringNameNo() + "]不支持调试");
		}
		RequestInvokeObject invokeObject = new RequestInvokeObject(methodName);
		invokeObject.putParam("src", source);
		invokeObject.putParam("name", name);
		invokeObject.putParam("args", args);
		SyncTunnel tunnel = new SyncTunnel(service.getName(), invokeObject.toDtObject());
		tunnel.setAccess(getInternalAccess());
		connect(tunnel, false);

		try {
			int code = tunnel.getCode();
			if (0 != code) {
				throw new DebugServiceException("微服务[" + toStringNameNo() + "]调试出错：" + code + "/" + tunnel.getMsg());
			}
			FriendlyObject result;
			result = FriendlyObject.valueOf(tunnel.getResult());
			int resultCode = result.getInt("code", 0);
			if (0 != resultCode) {
				throw new DebugServiceException(
						"微服务[" + toStringNameNo() + "]调试出错：" + resultCode + "/" + result.getString("msg"));
			}
			return result.getObject("content");
		} catch (DebugServiceException e) {
			throw e;
		} catch (Throwable e) {
			throw new DebugServiceException("微服务[" + toStringNameNo() + "]调试出错：" + e.toString(), e);
		}
	}

	protected WfReq createWfReq(Tunnel tunnel, ServiceTraceToken traceToken, boolean supportForward) {
		WfReq req = createWfReq();
		Access access = getValidAccess(tunnel.getHeader().getAccessId());
		if (null != access) {
			req.setClientAccess(access.getAccessId());
			String tenant = access.getTenant();
			if (!StringUtil.isEmpty(tenant)) {
				req.setTenant(tenant);
			}
			String openid = access.getOpenid();
			if (!StringUtil.isEmpty(openid)) {
				req.setOpenid(openid);
			}
		}
		String addr = tunnel.getAddr();
		if (!StringUtil.isEmpty(addr)) {
			req.setClientAddr(addr);
		}
		String resId = tunnel.getResId();
		if (!StringUtil.isEmpty(resId)) {
			req.setResId(resId);
			int right = getResourceRight(access, resId);
			req.setResRight(right);
		}
		String token = traceToken.getToken();
		if (!StringUtil.isEmpty(token)) {
			req.setTraceToken(token);
		}
		int waitTimeout = tunnel.getWaitTimeout();
		if (waitTimeout > 0) {
			if (waitTimeout > 5) {
				waitTimeout -= 2;
			}
			req.setWaitTimeout(waitTimeout);
		}
		int marks = 0;
		if (supportForward) {
			marks |= Request.MARK_SUPPORT_FORWARD;
		}
		if (0 != marks) {
			req.setMarks(marks);
		}
		return req;
	}

	protected WfReq createWfReq() {
		return new WfReq();
	}

	// wf_req节点
	protected static class WfReq implements JsonObject {

		Map<String, JsonPair> m_Items;

		protected WfReq() {
			m_Items = new HashMap<>();
		}

		protected String getName() {
			return RequestConstants.WF_REQ;
		}

		private void add(String name, Object value) {
			JsonPair pair = new JsonPair(name, value);
			m_Items.put(name, pair);
		}

		protected void setClientAccess(String accessId) {
			add(RequestConstants.CLIENT_ACCESS, accessId);
		}

		protected void setTenant(String tenant) {
			add(RequestConstants.TENANT, tenant);
		}

		protected void setOpenid(String openid) {
			add(RequestConstants.OPENID, openid);
		}

		protected void setClientAddr(String ip) {
			add(RequestConstants.CLIENT_ADDR, ip);
		}

		protected void setResId(String resId) {
			add(RequestConstants.RESOURCE_ID, resId);
		}

		protected void setResRight(int right) {
			add(RequestConstants.RESOURCE_RIGHT, right);
		}

		protected void setTraceToken(String traceToken) {
			add(RequestConstants.TRACE_TOKEN, traceToken);
		}

		protected void setWaitTimeout(int waitTimeout) {
			add(RequestConstants.WAIT_TIMEOUT, waitTimeout);
		}

		protected void setMarks(int marks) {
			add(RequestConstants.MARKS, marks);
		}

		@Override
		public int size() {
			return m_Items.size();
		}

		@Override
		public JsonPair property(String name) {
			return m_Items.get(name);
		}

		@Override
		public Iterable<JsonPair> items() {
			return m_Items.values();
		}

	}

	protected WfResp createWfResp() {
		return new WfResp();
	}

	// WfReq节点
	protected static class WfResp implements JsonUtil.Listener {
		int code;
		String msg;
		String resId;
		long resExpire;
		String resService;
		String forwardTo;
		int marks;
		/** 是否找到wf_resp节点 */
		boolean found;
		/** 下个节点的位置 */
		int nextPosition;

		protected String getName() {
			return ResponseConstants.WF_RESP;
		}

		@Override
		public void foundNode(JsonNode value, String name, int depth) throws JsonParseAbort {
			if (getName().equals(name) && (value instanceof JsonObject)) {
				try {
					foundWfResp((JsonObject) value);
					found = true;
				} catch (Exception e) {
					throw new JsonParseAbort(getName() + "节点解析异常", e);
				}
			}
			throw JsonParseAbort.MATCHED;
		}

		protected void foundWfResp(JsonObject obj) {
			for (JsonPair p : obj.items()) {
				Object v = p.getValue();
				if (null == v) {
					continue;
				}
				String k = p.getKey();
				if (ResponseConstants.WF_CODE.equals(k)) {
					this.code = ((Number) v).intValue();
				} else if (ResponseConstants.WF_MSG.equals(k)) {
					this.msg = (String) v;
				} else if (ResponseConstants.RESOURCE_ID.equals(k)) {
					this.resId = (String) v;
				} else if (ResponseConstants.RESOURCE_EXPIRE.equals(k)) {
					this.resExpire = ((Number) v).longValue();
				} else if (ResponseConstants.RESOURCE_SERVICE.equals(k)) {
					this.resService = (String) v;
				} else if (ResponseConstants.FORWARD_TO.equals(k)) {
					this.forwardTo = (String) v;
				} else if (ResponseConstants.MARKS.equals(k)) {
					this.marks = ((Number) v).intValue();
				}
			}
		}
	}

}
