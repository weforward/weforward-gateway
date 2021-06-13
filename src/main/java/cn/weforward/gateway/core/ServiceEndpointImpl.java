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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import cn.weforward.common.crypto.Base64;
import cn.weforward.common.execption.InvalidFormatException;
import cn.weforward.common.io.BytesOutputStream;
import cn.weforward.common.json.JsonInput;
import cn.weforward.common.json.JsonInputStream;
import cn.weforward.common.json.JsonObject;
import cn.weforward.common.json.JsonPair;
import cn.weforward.common.json.JsonParseAbort;
import cn.weforward.common.json.JsonUtil;
import cn.weforward.common.util.Bytes;
import cn.weforward.common.util.StringBuilderPool;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.MeshNode;
import cn.weforward.gateway.Pipe;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.StreamPipe;
import cn.weforward.gateway.StreamTunnel;
import cn.weforward.gateway.Tunnel;
import cn.weforward.gateway.util.CloseUtil;
import cn.weforward.gateway.util.WithoutLastJsonOutput;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.AccessLoader;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.Header.HeaderOutput;
import cn.weforward.protocol.Response;
import cn.weforward.protocol.aio.ClientChannel;
import cn.weforward.protocol.aio.ClientContext;
import cn.weforward.protocol.aio.ClientHandler;
import cn.weforward.protocol.aio.Headers;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpHeaderHelper;
import cn.weforward.protocol.aio.http.HttpHeaderOutput;
import cn.weforward.protocol.auth.AuthExceptionWrap;
import cn.weforward.protocol.auth.AutherOutputStream;
import cn.weforward.protocol.exception.AuthException;
import cn.weforward.protocol.exception.WeforwardException;
import cn.weforward.protocol.ops.trace.ServiceTraceToken;
import cn.weforward.protocol.ops.traffic.TrafficTableItem;

/**
 * 微服务端点的实现
 * 
 * @author zhangpengji
 *
 */
public class ServiceEndpointImpl extends ServiceEndpoint {

	// @SuppressWarnings("serial")
	// static final IOException REQUEST_ABORT = new IOException("请求中断") {
	// public synchronized Throwable fillInStackTrace() {
	// return this;
	// };
	// };

	List<EndpointUrl> m_EndpointUrls;

	public ServiceEndpointImpl(ServiceInstanceBalance group, ServiceInstance service, TrafficTableItem rule) {
		super(group, service, rule);
		initUrls();
	}

	private void initUrls() {
		List<String> urls;
		if (!isSelfMesh()) {
			MeshNode node = m_Service.getMeshNode();
			List<String> prefixs = node.getUrls();
			urls = new ArrayList<>(prefixs.size());
			for (String prefix : prefixs) {
				String url = prefix + m_Service.getName();
				urls.add(url);
			}
		} else {
			urls = m_Service.getUrls();
		}
		List<EndpointUrl> endpointUrls;
		if (1 == urls.size()) {
			endpointUrls = Collections.singletonList(new EndpointUrl(urls.get(0)));
		} else {
			endpointUrls = new ArrayList<>(urls.size());
			for (String url : urls) {
				endpointUrls.add(new EndpointUrl(url));
			}
		}
		m_EndpointUrls = endpointUrls;
	}

	@Override
	protected Pipe openPipe(Tunnel tunnel, boolean supportForward) {
		EndpointPipe pipe;
		if (tunnel.isFromMeshForward()) {
			pipe = new MeshForwardEndpointPipe(tunnel, getEndpointUrl(), getService());
		} else {
			ServiceTraceToken token = createTraceToken(tunnel);
			pipe = new EndpointPipe(tunnel, getEndpointUrl(), getService(), token, supportForward);
		}
		ClientChannel channel = m_Service.getClientChannel();
		if(null == channel) {
			channel = getHttpClientFactory();
		}
		pipe.open(channel, m_ReadTimeout);
		return pipe;
	}

	@Override
	protected StreamPipe openPipe(StreamTunnel tunnel) {
		EndpointStreamPipe pipe = new EndpointStreamPipe(tunnel, getEndpointUrl(), getService());
		pipe.open(getHttpClientFactory(), Configure.getInstance().getServiceMaxReadTimeout());
		return pipe;
	}

	private EndpointUrl getEndpointUrl() {
		EndpointUrl base = null;
		List<EndpointUrl> urls = m_EndpointUrls;
		for (EndpointUrl url : urls) {
			if (null == base || base.weight < url.weight) {
				base = url;
			}
		}
		return base;
	}

	static class EndpointUrl {
		String url;
		volatile int weight;

		EndpointUrl(String url) {
			this.url = url;
		}

		void success() {
			if (this.weight < 3) {
				this.weight++;
			}
		}

		void fail() {
			if (this.weight > -3) {
				this.weight--;
			}
		}
	}

	protected void writeHeader(HttpHeaderOutput output, Header header) throws IOException {
		HttpHeaderHelper.outHeaders(header, output);
	}

	protected void writeHeader(HttpHeaderOutput output, String name, String value) throws IOException {
		output.put(name, value);
	}

	protected void readHeader(Headers hs, Header header) {
		HttpHeaderHelper.fromHttpHeaders(hs, header);
	}

	protected String readHeader(Headers hs, String name) {
		return hs.getHeaderRaw(name);
	}

	/**
	 * Http下的微服务管道
	 * 
	 * @author zhangpengji
	 *
	 */
	class EndpointPipe implements Pipe, ClientHandler, HeaderOutput, HttpHeaderOutput, AccessLoader, Runnable {
		/** 进度 - 已开始输出 */
		static final int SCHEDULE_OUT_REQ_HEADER = 1;
		/** 进度 - 已完成输出 */
		static final int SCHEDULE_REQUEST_COMPLETED = 5;
		/** 进度 - 已开始读取响应头 */
		static final int SCHEDULE_PARES_RESP_HEADER = 10;
		/** 进度 - 已开始读取wf_resp */
		static final int SCHEDULE_PARES_WF_RESP = 20;
		/** 进度 - 已开始转发响应内容 */
		static final int SCHEDULE_RESPONSE_TRANSFERED = 30;
		/** 进度 - 已结束 */
		static final int SCHEDULE_END = 40;

		Tunnel m_Tunnel;
		EndpointUrl m_Url;
		ServiceInstance m_Service;
		ServiceTraceToken m_TraceToken;
		boolean m_SupportForward;
		boolean m_Trust;
		Access m_Access;
		int m_PreparedSize;

		ClientContext m_Context;
		// 当前进度
		volatile int m_Schedule;
		// 请求输出流（输出到服务端）
		volatile OutputStream m_RequestOutput;
		// 请求输出流的验证器
		volatile AutherOutputStream m_RequestOutputAuther;
		// 响应头
		volatile Header m_Header;
		// 响应输入流的验证器
		volatile AutherOutputStream m_ResponseInputAuther;
		volatile WfResp m_WfResp;

		EndpointPipe(Tunnel tunnel, EndpointUrl url, ServiceInstance service, ServiceTraceToken token,
				boolean supportForward) {
			m_Tunnel = tunnel;
			m_Url = url;
			m_Service = service;
			m_TraceToken = token;
			m_SupportForward = supportForward;

			int requestMaxSize = getService().getRequestMaxSize();
			if (requestMaxSize <= 0) {
				requestMaxSize = Configure.getInstance().getServiceRequestDefaultSize();
			}
			m_Tunnel.requestInit(this, requestMaxSize);
		}

		// 初始化依赖
		void init() {
			m_Trust = ServiceEndpointImpl.this.isTrust();
			m_Access = ServiceEndpointImpl.this.getValidAccess(m_Service.getOwner());
			m_PreparedSize = Configure.getInstance().getWfRespPreparedSize();
		}

		void open(ClientChannel channel, int timeout) {
			int waitTimeout = m_Tunnel.getWaitTimeout();
			if (waitTimeout > 0) {
				if (waitTimeout >= 5) {
					waitTimeout -= 2;
				} else if (waitTimeout >= 3) {
					waitTimeout -= 1;
				}
				timeout = waitTimeout;
			}
			if (timeout <= 0 || timeout > Configure.getInstance().getServiceMaxReadTimeout()) {
				timeout = Configure.getInstance().getServiceMaxReadTimeout();
			}
			try {
				m_Context = channel.request(this, m_Url.url, HttpConstants.METHOD_POST);
				m_Context.setTimeout(timeout * 1000);
			} catch (Exception e) {
				m_Url.fail();
				responseError(e);
			}
		}

		@Override
		public Header getHeader() {
			return m_Header;
		}

		@Override
		public String getTag() {
			String tag;
			if (Response.Helper.isMark(Response.MARK_KEEP_SERVICE_ORIGIN, m_WfResp.marks)) {
				// 需要回源
				tag = getServiceNo();
			} else if (Response.Helper.isMark(Response.MARK_FORGET_SERVICE_ORIGIN, m_WfResp.marks)) {
				// 不再回源
				tag = null;
			} else {
				// 回源不变
				tag = m_Tunnel.getHeader().getTag();
			}
			return tag;
		}

		@Override
		public ServiceInstance getService() {
			return m_Service;
		}

		@Override
		public String getResourceId() {
			return m_WfResp.resId;
		}

		@Override
		public long getResourceExpire() {
			return m_WfResp.resExpire;
		}

		@Override
		public String getResourceService() {
			if (!StringUtil.isEmpty(m_WfResp.resService)) {
				return m_WfResp.resService;
			}
			return getService().getName();
		}

		@Override
		public String getForwardTo() {
			return m_WfResp.forwardTo;
		}

		@Override
		public List<String> getNotifyReceives() {
			return Collections.emptyList();
		}

		// @Override
		String getServiceNo() {
			return m_Service.getNo();
		}

		/**
		 * 管道已连接
		 * <p>
		 * 这里包含业务相关的处理
		 */
		void connected() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_OUT_REQ_HEADER) {
					return;
				}
				m_Schedule = SCHEDULE_OUT_REQ_HEADER;
			}
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " connected.");
			}

			try {
				init();

				// 组织请求头
				ServiceInstance service = getService();
				Header header = new Header(service.getName());
				String authType;
				if (m_Trust) {
					authType = Header.AUTH_TYPE_NONE;
				} else {
					// authType = Header.AUTH_TYPE_AES; 尚未支持
					authType = Header.AUTH_TYPE_SHA2;
				}
				header.setAuthType(authType);
				header.setAccessId(service.getOwner());
				header.setContentType(Header.CONTENT_TYPE_JSON);
				header.setCharset(Header.CHARSET_UTF8);
				String channel = m_Tunnel.getHeader().getChannel();
				if (StringUtil.isEmpty(channel)) {
					channel = Header.CHANNEL_RPC;
				}
				header.setChannel(channel);
				if (null != service.getMeshNode()) {
					// 标识请求来源为网格
					header.setServiceNo(service.getNo());
					// XXX 应该把签名过程封装起来
					MessageDigest md = MessageDigest.getInstance("SHA-256");
					Access internalAccess = getInternalAccess();
					md.update((header.getService() + header.getNoise()).getBytes("utf-8"));
					md.update(internalAccess.getAccessKey());
					String meshSign = "Mesh-Forward "+internalAccess.getAccessId() + ":" + Base64.encode(md.digest());
					header.setMeshAuth(meshSign);
				}

				// 组织wf_req
				WfReq wfReq = createWfReq(m_Tunnel, m_TraceToken, m_SupportForward);

				AutherOutputStream auther = AutherOutputStream.getInstance(authType);
				auther.init(AutherOutputStream.MODE_ENCODE, this, m_Trust);
				auther.auth(header);
				m_RequestOutput = m_Context.openRequestWriter();
				auther.setTransferTo(this, m_RequestOutput);
				m_RequestOutputAuther = auther;
				// 输出wf_req
				outWfReq(wfReq);

				// } catch (AuthException e) {
				// // access失效了？
				// _Logger.error(e.toString(), e);
				// responseError(WeforwordException.CODE_UNDEFINED, "内部异常");
				// return;
			} catch (Throwable e) {
				responseError(e);
				return;
			}

			m_Tunnel.requestReady(this, m_RequestOutputAuther);
		}

		// 输出wf_req
		void outWfReq(WfReq wfReq) throws IOException {
			JsonObject obj = new JsonObject() {

				@Override
				public int size() {
					return 1;
				}

				@Override
				public JsonPair property(String name) {
					if (wfReq.getName().equals(name)) {
						return new JsonPair(name, wfReq);
					}
					return null;
				}

				@Override
				public Iterable<JsonPair> items() {
					return Collections.singletonList(new JsonPair(wfReq.getName(), wfReq));
				}

			};
			JsonUtil.format(obj, new WithoutLastJsonOutput(m_RequestOutputAuther));
		}

		/**
		 * 请求内容已传输完成（到微服务端）
		 */
		void requestCompleted0() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_REQUEST_COMPLETED) {
					return;
				}
				m_Schedule = SCHEDULE_REQUEST_COMPLETED;
			}

			m_Tunnel.requestCompleted(this);
		}

		/**
		 * 解析微服务响应头
		 */
		void parseRespHeader() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_PARES_RESP_HEADER) {
					return;
				}
				m_Schedule = SCHEDULE_PARES_RESP_HEADER;
			}
			// if (null != m_Header) {
			// return;
			// }
			try {
				Headers hs;
				hs = m_Context.getResponseHeaders();
				Header header = new Header(getService().getName());
				ServiceEndpointImpl.this.readHeader(hs, header);
				String authType = header.getAuthType();
				if (StringUtil.isEmpty(authType)) {
					responseError(WeforwardException.CODE_AUTH_TYPE_INVALID, "验证错误，响应缺少auth_type");
					return;
				}
				// if (!HttpServiceEndpoint.this.isTrust() &&
				// Header.AUTH_TYPE_NONE.equals(authType)) {
				// responseError(WeforwordException.CODE_AUTH_TYPE_INVALID,
				// "验证错误，响应的auth_type无效");
				// return;
				// }
				AutherOutputStream auther = AutherOutputStream.getInstance(authType);
				if (null == auther) {
					responseError(WeforwardException.CODE_AUTH_TYPE_INVALID, "验证错误，响应的auth_type无效");
					return;
				}
				auther.init(AutherOutputStream.MODE_DECODE, this, m_Trust);
				m_ResponseInputAuther = auther;
				m_ResponseInputAuther.auth(header);
				m_Header = header;
			} catch (Throwable e) {
				responseError(e);
				return;
			}
		}

		// @Override
		// public OutputStream getOutput() throws IOException {
		// if (null == m_OutputAuther) {
		// throw new IOException("未就绪");
		// }
		// return m_OutputAuther;
		// }

		@Override
		public void requestCanceled(Tunnel tunnel) {
			checkTunnel(tunnel);

			end(BalanceElement.STATE_OK);
			if (null != m_Context) {
				m_Context.disconnect();
			}
		}

		void checkTunnel(Tunnel tunnel) {
			if (m_Tunnel != tunnel) {
				// 对不上？
				throw new IllegalStateException(tunnel + "与当前[" + m_Tunnel + "]不匹配");
			}
		}

		@Override
		public void requestCompleted(Tunnel tunnel) {
			checkTunnel(tunnel);

			AutherOutputStream auther = m_RequestOutputAuther;
			if (null != auther) {
				m_RequestOutputAuther = null;
				try {
					auther.finish();
				} catch (Throwable e) {
					responseError(e);
					return;
				}
			}
			OutputStream out = m_RequestOutput;
			if (null != out) {
				m_RequestOutput = null;
				try {
					out.close();
				} catch (Throwable e) {
					responseError(e);
					return;
				}
			}
		}

		void parseWfResp() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_PARES_WF_RESP) {
					return;
				}
				m_Schedule = SCHEDULE_PARES_WF_RESP;
			}
			// if (null != m_WfResp) {
			// return;
			// }
			InputStream responseInput = null;
			JsonInput jsonInput = null;
			WfResp wfResp;
			try {
				responseInput = m_Context.duplicateResponseStream();
				jsonInput = new JsonInputStream(responseInput, m_Header.getCharset());
				wfResp = ServiceEndpointImpl.this.createWfResp();
				try {
					JsonUtil.parse(jsonInput, wfResp);
				} catch (JsonParseAbort e) {
					if (JsonParseAbort.MATCHED != e) {
						// _Logger.error(e.toString(), e);
						throw e;
					}
				}
				if (!wfResp.found) {
					dumpResponse();
					responseError(WeforwardException.CODE_ILLEGAL_CONTENT, "未找到'" + wfResp.getName() + "'节点，或该节点不在最前");
					return;
				}
				// 读取到“,”分隔符
				char ch = JsonUtil.skipBlank(jsonInput, 100);
				if (',' != ch && '}' != ch) {
					// 格式有问题
					dumpResponse();
					responseError(WeforwardException.CODE_ILLEGAL_CONTENT, "未找到'" + wfResp.getName() + "'节点后面的','符号");
					return;
				}
				// 记下输出流转接位置
				wfResp.nextPosition = jsonInput.position() - 1;
				m_WfResp = wfResp;
			} catch (Throwable e) {
				responseError(e);
				return;
			} finally {
				if (null != jsonInput) {
					CloseUtil.close(jsonInput, _Logger);
				} else {
					CloseUtil.close(responseInput, _Logger);
				}
			}

			if (0 != wfResp.code) {
				int code = WeforwardException.CODE_SERVICE_INVOKE_ERROR;
				String msg;
				int state = BalanceElement.STATE_EXCEPTION;
				if (WeforwardException.CODE_SERVICE_BUSY == wfResp.code) {
					msg = "忙";
					state = BalanceElement.STATE_BUSY;
				} else if (WeforwardException.CODE_SERVICE_UNAVAILABLE == wfResp.code) {
					msg = "不可用";
					state = BalanceElement.STATE_UNAVAILABLE;
				} else if (WeforwardException.CODE_SERVICE_TIMEOUT == wfResp.code) {
					msg = wfResp.msg;
					if (StringUtil.isEmpty(msg)) {
						msg = "响应超时";
					}
					state = BalanceElement.STATE_TIMEOUT;
				} else if (WeforwardException.CODE_SERVICE_FORWARD == wfResp.code) {
					code = WeforwardException.CODE_SERVICE_FORWARD;
					msg = "转发至[" + wfResp.forwardTo + "]";
					state = BalanceElement.STATE_OK;
				} else {
					msg = "运行异常:" + wfResp.code + "/" + wfResp.msg;
				}
				responseError(code, msg, state);
				return;
			}

			m_Tunnel.responseReady(this);
		}

		void dumpResponse() {
			if (!ServiceEndpointImpl._Logger.isTraceEnabled()) {
				return;
			}
			InputStream in = null;
			try {
				in = m_Context.duplicateResponseStream();
				String charset = m_Header.getCharset();
				BytesOutputStream bos = new BytesOutputStream(in);
				Bytes bytes = bos.getBytes();
				bos.close();
				String str = new String(bytes.getBytes(), bytes.getOffset(), bytes.getSize(), charset);
				_Logger.trace(str);
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
			} finally {
				CloseUtil.close(in, _Logger);
			}
		}

		@Override
		public void responseReady(Tunnel tunnel, OutputStream output) {
			checkTunnel(tunnel);

			// 将微服务的响应转发到tunnel
			// OutputStream output;
			InputStream preparedStream = null;
			try {
				// output = m_Tunnel.getOutput();
				// 先补充验证原先wf_resp部分
				preparedStream = m_Context.duplicateResponseStream();
				m_ResponseInputAuther.write(preparedStream, m_WfResp.nextPosition);
				m_ResponseInputAuther.setTransferTo(null, output);
				m_Context.responseTransferTo(m_ResponseInputAuther, m_WfResp.nextPosition);
			} catch (Throwable e) {
				responseError(e);
				return;
			} finally {
				CloseUtil.close(preparedStream, _Logger);
			}

			synchronized (this) {
				m_Schedule = SCHEDULE_RESPONSE_TRANSFERED;
			}
			if (m_Context.isResponseCompleted()) {
				responseCompleted0();
			}
		}

		void responseCompleted0() {
			synchronized (this) {
				if (m_Schedule != SCHEDULE_RESPONSE_TRANSFERED) {
					// 等开始向tunnel转发响应流后，再触发
					return;
				}
			}
			AutherOutputStream auther = m_ResponseInputAuther;
			if (null != auther) {
				m_ResponseInputAuther = null;
				try {
					auther.finish();
				} catch (Throwable e) {
					responseError(e);
					return;
				}
			}
			try {
				m_Tunnel.responseCompleted(this);
			} finally {
				end(BalanceElement.STATE_OK);
				m_Context.close();
			}
		}

		void responseTimeout0() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_PARES_RESP_HEADER) {
					// 已经开始响应了，或者调用端未指定超时，继续吧
					if (m_Schedule >= SCHEDULE_RESPONSE_TRANSFERED || 0 == m_Tunnel.getWaitTimeout()) {
						return;
					}
				}
			}

			responseError(WeforwardException.CODE_SERVICE_TIMEOUT, "响应超时", BalanceElement.STATE_TIMEOUT);
		}

		void responseError(Throwable e) {
			ServiceEndpointImpl._Logger.error(e.toString(), e);
			int code;
			String msg;
			int state = BalanceElement.STATE_EXCEPTION;
			if (e instanceof IOException) {
				if (e instanceof AuthExceptionWrap) {
					AuthException cause = ((AuthExceptionWrap) e).getCause();
					code = WeforwardException.CODE_AUTH_FAIL;
					msg = "验证异常：" + cause.getMessage();
				} else {
					code = WeforwardException.CODE_SERVICE_INVOKE_ERROR;
					msg = "IO异常：" + e.getMessage();
					state = BalanceElement.STATE_FAIL;
				}
			} else if (e instanceof AuthException) {
				code = WeforwardException.CODE_AUTH_FAIL;
				msg = "验证异常：" + e.getMessage();
			} else if (e instanceof InvalidFormatException) {
				code = WeforwardException.CODE_SERIAL_ERROR;
				msg = "响应内容格式有误";
			} else if (e instanceof WeforwardException) {
				code = ((WeforwardException) e).getCode();
				msg = "内部错误：" + e.getMessage();
			} else {
				code = WeforwardException.CODE_UNDEFINED;
				msg = "内部错误";
			}
			responseError(code, msg, state);
		}

		void responseError(int code, String msg) {
			responseError(code, msg, BalanceElement.STATE_EXCEPTION);
		}

		void responseError(int code, String msg, int state) {
			StringBuilder sb = StringBuilderPool._8k.poll();
			try {
				sb.append("微服务[").append(getService().toStringNameNo()).append("]");
				if (!StringUtil.isEmpty(msg)) {
					sb.append(msg);
				}
				m_Tunnel.responseError(this, code, sb.toString());
			} finally {
				end(state);
				StringBuilderPool._8k.offer(sb);
				m_Context.disconnect();
			}
		}

		private void end(int state) {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_END) {
					return;
				}
				m_Schedule = SCHEDULE_END;
			}
			ServiceEndpointImpl.this.end(this, state, m_TraceToken);
		}

		// ------------ 以下是ClientHandler的实现

		@Override
		public void connectFail() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " connect fail.");
			}
			m_Url.fail();
			_Logger.warn("连接失败：" + m_Url.url);

			responseError(WeforwardException.CODE_SERVICE_CONNECT_FAIL, "连接失败", BalanceElement.STATE_FAIL);
		}

		@Override
		public void established(ClientContext context) {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " established.");
			}
			if (null == m_Context) {
				// websocket会在ClientChannel.request中，回调此接口
				m_Context = context;
			} else if (m_Context != context) {
				// 不会吧，什么情况！？
				_Logger.error("context不一致：" + m_Context + " != " + context);
			}
			m_Url.success();

			try {
				ServiceEndpointImpl.this.getRpcExecutor().execute(this);
			} catch (RejectedExecutionException e) {
				responseError(WeforwardException.CODE_GATEWAY_BUSY, "网关忙", BalanceElement.STATE_OK);
			}
		}

		@Override
		public void requestCompleted() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " request completed.");
			}

			requestCompleted0();
		}

		@Override
		public void requestAbort() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " request abort.");
			}

			// responseError(REQUEST_ABORT);
			responseError(WeforwardException.CODE_SERVICE_INVOKE_ERROR, "调用中止", BalanceElement.STATE_FAIL);
		}

		@Override
		public void responseHeader() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " response header.");
			}

			// 检查状态码
			int code;
			try {
				code = m_Context.getResponseCode();
			} catch (Throwable e) {
				responseError(e);
				return;
			}
			if (HttpConstants.OK != code) {
				int state = BalanceElement.STATE_FAIL;
				if (HttpConstants.ACCEPTED == code) {
					state = BalanceElement.STATE_TIMEOUT;
				} else if (HttpConstants.TOO_MANY_REQUESTS == code) {
					state = BalanceElement.STATE_BUSY;
				} else if (HttpConstants.SERVICE_UNAVAILABLE == code) {
					state = BalanceElement.STATE_UNAVAILABLE;
				}
				responseError(WeforwardException.CODE_SERVICE_INVOKE_ERROR, "http状态码异常:" + code, state);
				return;
			}

			parseRespHeader();
		}

		@Override
		public void prepared(int available) {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " prepared.");
			}

			if (available < m_PreparedSize) {
				return;
			}

			parseWfResp();
		}

		@Override
		public void responseCompleted() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " response completed.");
			}

			parseWfResp();
			responseCompleted0();
		}

		@Override
		public void responseTimeout() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " response timeout.");
			}

			responseTimeout0();
		}

		@Override
		public void errorResponseTransferTo(IOException e, Object msg, OutputStream writer) {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " error response transfer to.");
			}

			responseError(e);
		}

		// ------------ 以下是HeaderOutput的实现

		@Override
		public void writeHeader(Header header) throws IOException {
			ServiceEndpointImpl.this.writeHeader(this, header);
		}

		// ------------ 以下是HttpHeaderOutput的实现

		@Override
		public void put(String name, String value) throws IOException {
			m_Context.setRequestHeader(name, value);
		}

		// ------------ 以下是AccessLoader的实现

		@Override
		public Access getValidAccess(String accessId) {
			return m_Access;
		}

		// ------------ 以下是Runnable的实现

		@Override
		public void run() {
			connected();
		}
	}

	/**
	 * 处理网格转发的微服务管道
	 * 
	 * @author zhangpengji
	 *
	 */
	class MeshForwardEndpointPipe extends EndpointPipe {

		MeshForwardEndpointPipe(Tunnel tunnel, EndpointUrl url, ServiceInstance service) {
			super(tunnel, url, service, null, false);
		}

		@Override
		void connected() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_OUT_REQ_HEADER) {
					return;
				}
				m_Schedule = SCHEDULE_OUT_REQ_HEADER;
			}
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " connected.");
			}

			try {
				init();

				Header header = m_Tunnel.getHeader();
				writeHeader(header);
				m_RequestOutput = m_Context.openRequestWriter();
			} catch (Throwable e) {
				responseError(e);
				return;
			}

			m_Tunnel.requestReady(this, m_RequestOutput);
		}

		void parseRespHeader() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_PARES_RESP_HEADER) {
					return;
				}
				m_Schedule = SCHEDULE_PARES_RESP_HEADER;
			}
			try {
				Headers hs;
				hs = m_Context.getResponseHeaders();
				Header header = new Header(getService().getName());
				ServiceEndpointImpl.this.readHeader(hs, header);
				m_Header = header;
			} catch (Throwable e) {
				responseError(e);
				return;
			}
		}
		
		@Override
		void parseWfResp() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_PARES_WF_RESP) {
					return;
				}
				m_Schedule = SCHEDULE_PARES_WF_RESP;
			}

			m_Tunnel.responseReady(this);
		}
		
		@Override
		public void responseReady(Tunnel tunnel, OutputStream output) {
			checkTunnel(tunnel);

			try {
				m_Context.responseTransferTo(output, 0);
			} catch (Throwable e) {
				responseError(e);
				return;
			}

			synchronized (this) {
				m_Schedule = SCHEDULE_RESPONSE_TRANSFERED;
			}
			if (m_Context.isResponseCompleted()) {
				responseCompleted0();
			}
		}
	}

	class EndpointStreamPipe implements StreamPipe, ClientHandler, HttpHeaderOutput {

		/** 进度 - 已开始输出 */
		static final int SCHEDULE_OUT_REQUEST = 1;
		/** 进度 - 响应内容已就绪 */
		static final int SCHEDULE_RESPONSE_READY = 10;
		/** 进度 - 已开始转发响应内容 */
		static final int SCHEDULE_RESPONSE_TRANSFERED = 30;
		/** 进度 - 已结束 */
		static final int SCHEDULE_END = 40;

		StreamTunnel m_Tunnel;
		EndpointUrl m_Url;
		ServiceInstance m_Service;

		ClientContext m_Context;
		// 当前进度
		volatile int m_Schedule;
		// 请求输出流
		volatile OutputStream m_Output;
		String m_ContentType;
		String m_ContentDisposition;
		long m_Length;

		EndpointStreamPipe(StreamTunnel tunnel, EndpointUrl url, ServiceInstance service) {
			m_Tunnel = tunnel;
			m_Url = url;
			m_Service = service;
		}

		void open(ClientChannel channel, int timeout) {
			try {
				String id = URLEncoder.encode(m_Tunnel.getResourceId(), Header.CHARSET_DEFAULT);
				String url = m_Url.url + "?id=" + id;
				m_Context = channel.request(this, url, HttpConstants.METHOD_POST);
				m_Context.setTimeout(timeout * 1000);
			} catch (IOException e) {
				m_Url.fail();
				responseError(e);
			}
		}

		@Override
		public String getContentType() {
			return m_ContentType;
		}

		@Override
		public String getContentDisposition() {
			return m_ContentDisposition;
		}

		@Override
		public long getLength() {
			return m_Length;
		}

		/**
		 * 成功建立连接
		 */
		void connected() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_OUT_REQUEST) {
					return;
				}
				m_Schedule = SCHEDULE_OUT_REQUEST;
			}
			try {
				/* 输出请求头 */
				writeHeader(this, HttpConstants.WF_CHANNEL, Header.CHANNEL_STREAM);
				String contentType = m_Tunnel.getContentType();
				if (!StringUtil.isEmpty(contentType)) {
					writeHeader(this, HttpConstants.CONTENT_TYPE, contentType);
				}
				long length = m_Tunnel.getLength();
				if (0 != length) {
					writeHeader(this, HttpConstants.CONTENT_LENGTH, String.valueOf(length));
				}
				/* 转发请求内容 */
				m_Output = m_Context.openRequestWriter();
				m_Tunnel.requestReady(this, m_Output);
			} catch (Throwable e) {
				responseError(e);
			}
		}

		void checkTunnel(StreamTunnel tunnel) {
			if (m_Tunnel != tunnel) {
				// 对不上？
				throw new IllegalStateException(tunnel + "与当前[" + m_Tunnel + "]不匹配");
			}
		}

		@Override
		public void requestCanceled(StreamTunnel tunnel) {
			checkTunnel(tunnel);

			end(BalanceElement.STATE_OK);
			if (null != m_Context) {
				m_Context.disconnect();
			}
		}

		@Override
		public void requestCompleted(StreamTunnel tunnel) {
			checkTunnel(tunnel);

			OutputStream output = m_Output;
			if (null != output) {
				m_Output = null;
				try {
					output.close();
				} catch (IOException e) {
				}
			}
		}

		void parseRespHeader() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_RESPONSE_READY) {
					return;
				}
				m_Schedule = SCHEDULE_RESPONSE_READY;
			}
			int respCode;
			Headers headers;
			try {
				respCode = m_Context.getResponseCode();
				headers = m_Context.getResponseHeaders();
			} catch (Throwable e) {
				responseError(e);
				return;
			}
			m_ContentType = readHeader(headers, HttpConstants.CONTENT_TYPE);
			m_ContentDisposition = readHeader(headers, HttpConstants.CONTENT_DISPOSITION);
			String length = readHeader(headers, HttpConstants.CONTENT_LENGTH);
			if (!StringUtil.isEmpty(length)) {
				try {
					m_Length = Long.parseLong(length);
				} catch (NumberFormatException e) {
					// ignore
				}
			}

			m_Tunnel.responseReady(this, respCode, 0);
		}

		@Override
		public void responseReady(StreamTunnel tunnel, OutputStream output) {
			try {
				m_Context.responseTransferTo(output, 0);
			} catch (Throwable e) {
				responseError(e);
				return;
			}

			synchronized (this) {
				m_Schedule = SCHEDULE_RESPONSE_TRANSFERED;
			}
			if (m_Context.isResponseCompleted()) {
				responseCompleted0();
			}
		}

		void responseCompleted0() {
			synchronized (this) {
				if (m_Schedule != SCHEDULE_RESPONSE_TRANSFERED) {
					// 等开始向tunnel转发响应流后，再触发
					return;
				}
			}
			try {
				m_Tunnel.responseCompleted(this);
			} finally {
				end(BalanceElement.STATE_OK);
				m_Context.close();
			}

		}

		void responseTimeout0() {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_RESPONSE_READY) {
					// 已经开始响应了
					return;
				}
			}

			responseError(StreamTunnel.CODE_ACCEPTED, "响应超时", BalanceElement.STATE_TIMEOUT);
		}

		void responseError(Throwable e) {
			_Logger.error(e.toString(), e);

			int code = StreamTunnel.CODE_INTERNAL_ERROR;
			String msg;
			int state = BalanceElement.STATE_EXCEPTION;
			if (e instanceof IOException) {
				msg = "IO异常：" + e.getMessage();
				state = BalanceElement.STATE_FAIL;
			} else if (e instanceof WeforwardException) {
				code = ((WeforwardException) e).getCode();
				msg = "内部错误：" + e.getMessage();
			} else {
				msg = "内部错误";
			}
			responseError(code, msg.toString(), state);
		}

		void responseError(int code, String msg, int state) {
			try {
				StringBuilder sb = new StringBuilder();
				sb.append("微服务[").append(getService().toStringNameNo()).append("]");
				if (!StringUtil.isEmpty(msg)) {
					sb.append(msg);
				}
				m_Tunnel.responseError(this, code, sb.toString());
			} finally {
				end(state);
				m_Context.disconnect();
			}

		}

		void end(int state) {
			synchronized (this) {
				if (m_Schedule >= SCHEDULE_END) {
					return;
				}
				m_Schedule = SCHEDULE_END;
			}

			OutputStream output = m_Output;
			if (null != output) {
				m_Output = null;
				try {
					output.close();
				} catch (IOException e) {
				}
			}

			ServiceEndpointImpl.this.end(this, state);
		}

		// ------------ 以下是ClientHandler的实现

		@Override
		public void connectFail() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " connect fail.");
			}
			m_Url.fail();

			responseError(StreamTunnel.CODE_INTERNAL_ERROR, "连接失败", BalanceElement.STATE_FAIL);
		}

		@Override
		public void established(ClientContext context) {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " established.");
			}
			if (m_Context != context) {
				// 不会吧，什么情况！？
				_Logger.error("context不一致：" + m_Context + " != " + context);
			}
			m_Url.success();

			connected();
		}

		@Override
		public void requestCompleted() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " request completed.");
			}
		}

		@Override
		public void requestAbort() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " request abort.");
			}

			responseError(StreamTunnel.CODE_INTERNAL_ERROR, "调用中止", BalanceElement.STATE_FAIL);
		}

		@Override
		public void responseHeader() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " response header.");
			}

			parseRespHeader();
		}

		@Override
		public void prepared(int available) {
			// nothing to do
		}

		@Override
		public void responseCompleted() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " response completed.");
			}

			responseCompleted0();
		}

		@Override
		public void errorResponseTransferTo(IOException e, Object msg, OutputStream writer) {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " error response transfer to.");
			}

			responseError(e);
		}

		@Override
		public void responseTimeout() {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace(getService().getName() + " response timeout.");
			}

			responseTimeout0();
		}

		// ------------ 以下是HttpHeaderOutput的实现

		@Override
		public void put(String name, String value) throws IOException {
			m_Context.setRequestHeader(name, value);
		}

	}
}
