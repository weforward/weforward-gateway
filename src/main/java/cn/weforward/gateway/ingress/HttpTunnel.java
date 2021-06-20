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
package cn.weforward.gateway.ingress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.execption.InvalidFormatException;
import cn.weforward.common.json.JsonArray;
import cn.weforward.common.json.JsonInput;
import cn.weforward.common.json.JsonInputStream;
import cn.weforward.common.json.JsonNode;
import cn.weforward.common.json.JsonObject;
import cn.weforward.common.json.JsonOutput;
import cn.weforward.common.json.JsonOutputStream;
import cn.weforward.common.json.JsonPair;
import cn.weforward.common.json.JsonParseAbort;
import cn.weforward.common.json.JsonUtil;
import cn.weforward.common.json.SimpleJsonArray;
import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.Pipe;
import cn.weforward.gateway.Tunnel;
import cn.weforward.gateway.util.CloseUtil;
import cn.weforward.gateway.util.WithoutLastJsonOutput;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.Header.HeaderOutput;
import cn.weforward.protocol.RequestConstants;
import cn.weforward.protocol.ResponseConstants;
import cn.weforward.protocol.aio.ServerHandler;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpContext;
import cn.weforward.protocol.aio.http.HttpHeaderHelper;
import cn.weforward.protocol.aio.http.HttpHeaderOutput;
import cn.weforward.protocol.aio.http.HttpHeaders;
import cn.weforward.protocol.auth.AuthExceptionWrap;
import cn.weforward.protocol.auth.AutherOutputStream;
import cn.weforward.protocol.exception.AuthException;
import cn.weforward.protocol.exception.InvokeDeniedException;
import cn.weforward.protocol.exception.WeforwardException;

/**
 * 基于Http协议的<code>Tunnel</code>实现。<br/>
 * 处理流程：<br/>
 * 1、读取请求头，验证请求头，验证权限；<br/>
 * 2、读取wf_req节点； <br/>
 * 3、与微服务端点对接； <br/>
 * 4、转发请求内容； <br/>
 * 
 * @author zhangpengji
 *
 */
public class HttpTunnel implements Tunnel, HeaderOutput, ServerHandler, Runnable {
	static final Logger _Logger = LoggerFactory.getLogger(HttpTunnel.class);

	/** 进度 - 已开始业务处理 */
	static final int SCHEDULE_BEGIN = 1;
	/** 进度 - 已开始解析wf_req节点 */
	static final int SCHEDULE_PARES_WF_REQ = 20;
	/** 进度 - 预备转发请求内容 */
	static final int SCHEDULE_REQUEST_TRANSFERER_READY = 49;
	/** 进度 - 已开始转发请求内容 */
	static final int SCHEDULE_REQUEST_TRANSFERED = 50;
	/** 进度 - 已开始转发响应内容 */
	static final int SCHEDULE_RESPONSE_TRANSFERED = 70;
	/** 进度 - 处理已结束 */
	static final int SCHEDULE_END = 100;

	HttpContext m_Context;
	ServerHandlerSupporter m_Supporter;
	String m_Addr;
	boolean m_Trust;

	// 当前进度
	volatile int m_Schedule;
	volatile Header m_Header;
	volatile WfReq m_WfReq;
	// 对请求内容的验证器
	volatile AutherOutputStream m_InputAuther;
	// 对接的微服务管道
	volatile Pipe m_Pipe;
	// 响应输出流
	volatile OutputStream m_Output;
	// 对响应内容的验证器
	volatile AutherOutputStream m_OutputAuther;
	// // 请求已中断
	// volatile boolean m_RequestAbort;

	public HttpTunnel(HttpContext ctx, ServerHandlerSupporter supporter, String addr, boolean trust) {
		m_Context = ctx;
		m_Supporter = supporter;
		m_Addr = addr;
		m_Trust = trust;
	}

	@Override
	public Header getHeader() {
		return m_Header;
	}

	// @Override
	// public Access getAccess() {
	// // 验证器已校验过accessId
	// String id = m_Header.getAccessId();
	// if (StringUtil.isEmpty(id)) {
	// return null;
	// }
	// return m_Supporter.getAccessLoader().getValidAccess(id);
	// }

	@Override
	public int getWaitTimeout() {
		return m_WfReq.waitTimeout;
	}

	@Override
	public String getVersion() {
		return m_WfReq.version;
	}

	@Override
	public String getTraceToken() {
		return m_WfReq.traceToken;
	}

	@Override
	public String getResId() {
		return m_WfReq.resId;
	}

	@Override
	public String getAddr() {
		return m_Addr;
	}

	@Override
	public int getMarks() {
		return m_WfReq.marks;
	}
	
	@Override
	public String getGatewayAuthType() {
		return m_Header.getGatewayAuthType();
	}

	@Override
	public InputStream mirrorTransferStream() throws IOException {
		return m_Context.mirrorRequestStream(m_WfReq.nextPosition);
	}

	@Override
	public void requestInit(Pipe pipe, int requestMaxSize) {
		checkPipe(pipe);
		boolean abort = false;
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_END) {
				abort = true;
			} else {
				m_Pipe = pipe;
			}
		}
		if (abort) {
			// 刚创建就被告知已取消 :(
			pipe.requestCanceled(this);
			return;
		}

		if (requestMaxSize > 0) {
			m_Context.setMaxHttpSize(requestMaxSize);
		}
	}

	@Override
	public void requestReady(Pipe pipe, OutputStream pipeOutput) {
		checkPipe(pipe);
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_REQUEST_TRANSFERER_READY) {
				// 已经执行了？
				return;
			}
			m_Schedule = SCHEDULE_REQUEST_TRANSFERER_READY;
		}

		InputStream preparedStream = null;
		try {
			// pipeOutput = pipe.getOutput();
			// 先补充验证原先wf_req部分
			preparedStream = m_Context.duplicateRequestStream();
			m_InputAuther.write(preparedStream, m_WfReq.nextPosition);
			// 再接驳pipe的Output，输出wf_req后面的内容
			m_InputAuther.setTransferTo(null, pipeOutput);
			m_Context.requestTransferTo(m_InputAuther, m_WfReq.nextPosition);
		} catch (Throwable e) {
			responseError(e);
			return;
		} finally {
			CloseUtil.close(preparedStream, _Logger);
		}

		synchronized (this) {
			m_Schedule = SCHEDULE_REQUEST_TRANSFERED;
		}
		if (m_Context.isRequestCompleted()) {
			requestCompleted0();
		}
	}

	/**
	 * 请求转发时发生错误
	 * 
	 * @param error
	 */
	void requestTransferError(Throwable error) {
		responseError(error);
	}

	/**
	 * 请求已结束
	 */
	void requestCompleted0() {
		synchronized (this) {
			if (m_Schedule != SCHEDULE_REQUEST_TRANSFERED) {
				// 等微服务端的管道就绪后再触发
				return;
			}
		}

		AutherOutputStream auther = m_InputAuther;
		if (null != auther) {
			m_InputAuther = null;
			try {
				auther.finish();
			} catch (Throwable e) {
				responseError(e);
				return;
			}
		}
		if (null != m_Pipe) {
			m_Pipe.requestCompleted(this);
		}
	}

	/**
	 * 请求被中断
	 */
	void requestAbort0() {
		// synchronized (this) {
		// m_RequestAbort = true;
		// }
		responseError(WeforwardException.CODE_NETWORK_ERROR, "调用中止", true);
	}

	protected static class WfResp implements JsonObject {

		Map<String, JsonPair> items;

		WfResp() {
			this.items = new HashMap<String, JsonPair>();
		}

		String getName() {
			return ResponseConstants.WF_RESP;
		}

		void setCode(int code) {
			items.put(ResponseConstants.WF_CODE, new JsonPair(ResponseConstants.WF_CODE, code));
		}

		void setMsg(String msg) {
			items.put(ResponseConstants.WF_MSG, new JsonPair(ResponseConstants.WF_MSG, msg));
		}

		void setResourceUrl(String url) {
			String key = ResponseConstants.RESOURCE_URL;
			items.put(key, new JsonPair(key, url));
		}

		void setEventReceives(List<String> receives) {
			String key = ResponseConstants.NOTIFY_RECEIVES;
			JsonArray arr = new SimpleJsonArray(receives);
			items.put(key, new JsonPair(key, arr));
		}

		@Override
		public int size() {
			return items.size();
		}

		@Override
		public JsonPair property(String name) {
			return items.get(name);
		}

		@Override
		public Iterable<JsonPair> items() {
			return items.values();
		}

	}

	@Override
	public void requestCompleted(Pipe pipe) {

	}

	@Override
	public void responseReady(Pipe pipe) {
		checkPipe(pipe);
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_RESPONSE_TRANSFERED) {
				return;
			}
			m_Schedule = SCHEDULE_RESPONSE_TRANSFERED;
		}

		// 组织响应头
		Header respHeader = new Header(m_Header.getService());
		String authType = Header.AUTH_TYPE_NONE;
		// TODO
		// if (!m_Trust && !StringUtil.isEmpty(m_Header.getAccessId())) {
		// authType = Header.AUTH_TYPE_AES;
		// }
		respHeader.setAuthType(authType);
		respHeader.setContentType(Header.CONTENT_TYPE_JSON);
		respHeader.setCharset(Header.CHARSET_UTF8);
		respHeader.setTag(m_Pipe.getTag());

		// 组织wf_resp
		WfResp wfResp = createWfResp(0, "");
		String resId = m_Pipe.getResourceId();
		if (!StringUtil.isEmpty(resId)) {
			String resService = m_Pipe.getResourceService();
			long resExpire = m_Pipe.getResourceExpire();
			StreamResourceToken token = new StreamResourceToken();
			token.setServiceName(resService);
			token.setResourceId(resId);
			token.setExpire(resExpire);
			String resUrl = m_Supporter.genResourceUrl(token);
			if (!StringUtil.isEmpty(resUrl)) {
				wfResp.setResourceUrl(resUrl);
			}
		}
		List<String> eventReceives = m_Pipe.getNotifyReceives();
		if (!ListUtil.isEmpty(eventReceives)) {
			wfResp.setEventReceives(eventReceives);
		}

		AutherOutputStream auther = AutherOutputStream.getInstance(authType);
		auther.init(AutherOutputStream.MODE_ENCODE, m_Supporter.getAccessLoader(), m_Trust);
		try {
			auther.auth(respHeader);
			m_Output = m_Context.openResponseWriter(HttpConstants.OK, null);
			auther.setTransferTo(this, m_Output);
			m_OutputAuther = auther;
			// 输出wf_resp
			outWfResp(wfResp, m_OutputAuther, true);
		} catch (Throwable e) {
			responseError(e);
			return;
		}

		m_Pipe.responseReady(this, m_OutputAuther);
	}

	void checkPipe(Pipe pipe) {
		if (null != m_Pipe && m_Pipe != pipe) {
			// 对不上？
			throw new IllegalStateException(pipe + "与当前[" + m_Pipe + "]不匹配");
		}
	}

	protected WfResp createWfResp(int code, String msg) {
		WfResp resp = new WfResp();
		resp.setCode(code);
		resp.setMsg(msg);
		return resp;
	}

	// 输出wf_resp
	void outWfResp(WfResp resp, OutputStream output, boolean withoutLast) throws IOException {
		JsonObject obj = new JsonObject() {

			@Override
			public int size() {
				return 1;
			}

			@Override
			public JsonPair property(String name) {
				if (resp.getName().equals(name)) {
					return new JsonPair(name, resp);
				}
				return null;
			}

			@Override
			public Iterable<JsonPair> items() {
				return Collections.singletonList(new JsonPair(resp.getName(), resp));
			}

		};

		JsonOutput joutput;
		if (withoutLast) {
			joutput = new WithoutLastJsonOutput(output);
		} else {
			joutput = new JsonOutputStream(output);
		}
		JsonUtil.format(obj, joutput);
	}

	// @Override
	// public OutputStream getOutput() throws IOException {
	// if (null == m_OutputAuther) {
	// throw new IOException("尚未调用responseReady");
	// }
	// return m_OutputAuther;
	// }

	@Override
	public void responseCompleted(Pipe pipe) {
		checkPipe(pipe);
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_END) {
				return;
			}
			m_Schedule = SCHEDULE_END;
		}

		AutherOutputStream outputAuther = m_OutputAuther;
		if (null != outputAuther) {
			m_OutputAuther = null;
			try {
				outputAuther.finish();
			} catch (Throwable e) {
				// responseError(e);
				// return;
				_Logger.error(e.toString(), e);
			}
		}

		// 关闭输出流。但不置为null，标识已经已无法再输出错误信息
		if (null != m_Output) {
			try {
				m_Output.close();
			} catch (Throwable e) {
				// responseError(e);
				// return;
				_Logger.error(e.toString(), e);
			}
		}
		
		m_Supporter.getMetricsCollecter().onRpcEnd();
		// m_Context.close(); 不用关
	}

	protected void parseHeader() {
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_BEGIN) {
				return;
			}
			m_Schedule = SCHEDULE_BEGIN;
		}

		m_Supporter.getMetricsCollecter().onRpcBegin();
		if (!m_Supporter.getGateway().isReady()) {
			responseError(WeforwardException.CODE_UNREADY, "网关未就绪");
			return;
		}
		String serviceName = HttpHeaderHelper.getServiceName(m_Context.getUri());
		Header header = new Header(serviceName);
		HttpHeaders hs = m_Context.getRequestHeaders();
		if (null != hs && hs.size() > 0) {
			readHeader(hs, header);
		}
		HeaderChecker.CheckResult checkResult = HeaderChecker.check(header);
		if (0 != checkResult.code) {
			responseError(checkResult.code, checkResult.msg);
			return;
		}

		AutherOutputStream auther = AutherOutputStream.getInstance(header.getAuthType());
		if (null == auther) {
			responseError(WeforwardException.CODE_AUTH_TYPE_INVALID, "'auth type' invalid:" + header.getAuthType());
			return;
		}
		auther.init(AutherOutputStream.MODE_DECODE, m_Supporter.getAccessLoader(), m_Trust);
		m_InputAuther = auther;
		m_Header = header;
	}

	protected void readHeader(HttpHeaders hs, Header header) {
		HttpHeaderHelper.fromHttpHeaders(hs, header);
	}

	void parseWfReq() {
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_PARES_WF_REQ) {
				return;
			}
			m_Schedule = SCHEDULE_PARES_WF_REQ;
		}

		InputStream requestInput = null;
		JsonInput jsonInput = null;
		WfReq wfReq;
		try {
			requestInput = m_Context.duplicateRequestStream();
			jsonInput = new JsonInputStream(requestInput, m_Header.getCharset());
			wfReq = createWfReq();
			try {
				JsonUtil.parse(jsonInput, wfReq);
			} catch (JsonParseAbort e) {
				if (JsonParseAbort.MATCHED != e) {
					// _Logger.error(e.toString(), e);
					throw e;
				}
			}
			if (!wfReq.found) {
				responseError(WeforwardException.CODE_ILLEGAL_CONTENT, "未找到'" + wfReq.getName() + "'节点，或该节点不在最前");
				return;
			}
			// 读取到“,”分隔符
			char ch = JsonUtil.skipBlank(jsonInput, 100);
			if (',' != ch) {
				// 格式有问题
				responseError(WeforwardException.CODE_ILLEGAL_CONTENT, "未找到'" + wfReq.getName() + "'节点后面的','符号");
				return;
			}
			// 记下输入流转接位置
			wfReq.nextPosition = jsonInput.position() - 1;
		} catch (Throwable e) {
			responseError(e);
			return;
		} finally {
			if (null != jsonInput) {
				CloseUtil.close(jsonInput, _Logger);
			} else {
				CloseUtil.close(requestInput, _Logger);
			}
		}
		m_WfReq = wfReq;

		try {
			m_Supporter.getRpcExecutor().execute(this);
		} catch (RejectedExecutionException e) {
			// 线程池忙？
			HttpTunnel._Logger.warn(e.toString(), e);
			responseError(WeforwardException.CODE_GATEWAY_BUSY, "网关忙");
		}
	}

	protected WfReq createWfReq() {
		return new WfReq();
	}

	// WfReq节点
	protected static class WfReq implements JsonUtil.Listener {
		int waitTimeout;
		String version;
		String resId;
		String traceToken;
		// String tenantId;
		int marks;
		/** 是否找到wf_req节点 */
		boolean found;
		/** 下个节点的位置 */
		int nextPosition;

		String getName() {
			return RequestConstants.WF_REQ;
		}

		@Override
		public void foundNode(JsonNode value, String name, int depth) throws JsonParseAbort {
			if (getName().equals(name) && (value instanceof JsonObject)) {
				try {
					JsonObject obj = (JsonObject) value;
					for (JsonPair p : obj.items()) {
						Object v = p.getValue();
						if (null == v) {
							continue;
						}
						String k = p.getKey();
						if (RequestConstants.WAIT_TIMEOUT.equals(k)) {
							this.waitTimeout = ((Number) v).intValue();
						} else if (RequestConstants.VERSION.equals(k)) {
							this.version = (String) v;
						} else if (RequestConstants.RESOURCE_ID.equals(k)) {
							this.resId = (String) v;
						} else if (RequestConstants.TRACE_TOKEN.equals(k)) {
							this.traceToken = (String) p.getValue();
							// } else if (RequestConstants.TENANT.equals(k)) {
							// this.tenantId = (String) v;
						} else if (RequestConstants.MARKS.equals(k)) {
							this.marks = ((Number) v).intValue();
						}
					}
					found = true;
				} catch (Exception e) {
					throw new JsonParseAbort(getName() + "节点解析异常", e);
				}
			}
			throw JsonParseAbort.MATCHED;
		}
	}

	void responseError(Throwable e) {
		if (e instanceof AuthExceptionWrap) {
			responseError(e.getCause());
			return;
		}
		if (e instanceof InvokeDeniedException) {
			_Logger.error(e.getMessage());
		} else {
			_Logger.error(e.toString(), e);
		}

		int code;
		String msg;
		if (e instanceof IOException) {
			code = WeforwardException.CODE_NETWORK_ERROR;
			msg = "IO异常";
		} else if (e instanceof InvalidFormatException) {
			code = WeforwardException.CODE_SERIAL_ERROR;
			msg = "请求内容格式有误";
		} else if (e instanceof WeforwardException) {
			WeforwardException wf = (WeforwardException) e;
			code = wf.getCode();
			msg = wf.getMessage();
		} else {
			code = WeforwardException.CODE_UNDEFINED;
			msg = "网关内部错误";
		}
		responseError(code, msg, false);
	}

	@Override
	public void responseError(Pipe pipe, int code, String msg) {
		checkPipe(pipe);

		m_Pipe = null;
		responseError(code, msg, false);
	}

	void responseError(int code, String msg) {
		responseError(code, msg, false);
	}

	// @Override
	void responseError(int code, String msg, boolean abort) {
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_END) {
				return;
			}
			m_Schedule = SCHEDULE_END;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("请求失败:").append(code).append('/').append(msg).append(' ');
		toString(sb);
		_Logger.warn(sb.toString());

		if (abort || null != m_Output) {
			// 已经无法输出错误了
			if (null != m_Output) {
				CloseUtil.cancel(m_Output, _Logger);
			}
		} else {
			OutputStream output = null;
			try {
				// 组织头信息
				Header respHeader = new Header(null);
				respHeader.setAuthType(Header.AUTH_TYPE_NONE);
				respHeader.setContentType(Header.CONTENT_TYPE_JSON);
				respHeader.setCharset(Header.CHARSET_UTF8);

				// 组织WfResp
				WfResp wfResp = createWfResp(code, msg);
				
				writeHeader(respHeader);
				int httpCode = HttpConstants.OK;
				if (WeforwardException.CODE_UNREADY == code || WeforwardException.CODE_GATEWAY_BUSY == code) {
					// 响应503，让前端代理转到其他网关
					httpCode = HttpConstants.SERVICE_UNAVAILABLE;
				}
				output = m_Context.openResponseWriter(httpCode, null);
				outWfResp(wfResp, output, false);
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
			} finally {
				CloseUtil.close(output, _Logger);
			}
		}

		m_Supporter.getMetricsCollecter().onRpcEnd();
		// m_Context.close(); 不用关

		Pipe pipe = m_Pipe;
		if (null != pipe) {
			// m_Pipe = null;
			pipe.requestCanceled(this);
		}
	}

	protected void writeHeader(HttpHeaderOutput output, Header header) throws IOException {
		HttpHeaderHelper.outHeaders(header, output);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		toString(sb);
		return sb.toString();
	}

	public void toString(StringBuilder sb) {
		sb.append('[');
		if (null != m_Header) {
			sb.append("service:").append(m_Header.getService()).append(",access:").append(m_Header.getAccessId())
					.append(",addr:").append(getAddr()).append(",ua:").append(m_Header.getUserAgent());
		} else {
			sb.append("uri:" + m_Context.getUri());
		}
		HttpHeaders rh = m_Context.getRequestHeaders();
		if(null != rh) {
			sb.append(",ref:").append(rh.get("Referer"));
		}
		sb.append(']');
	}

	// ------------ 以下是HeaderOutput的实现

	@Override
	public void writeHeader(Header header) throws IOException {
		HttpHeaderOutput output = new HttpHeaderOutput.HttpContextOutput(m_Context);
		writeHeader(output, header);
		HttpAccessControl.outHeaders(m_Context);
	}

	// ------------ 以下是ServerHandler的实现

	@Override
	public void requestHeader() {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " request header.");
		}

		parseHeader();
	}

	@Override
	public void prepared(int available) {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " prepared.");
		}

		if (available >= Configure.getInstance().getWfReqPreparedSize()) {
			parseWfReq();
		}
	}

	@Override
	public void requestAbort() {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " request abort.");
		}

		requestAbort0();
	}

	@Override
	public void requestCompleted() {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " request completed.");
		}

		parseWfReq();
		requestCompleted0();
	}

	@Override
	public void responseTimeout() {
		// 没有设置超时值，不会触发此方法
	}

	@Override
	public void responseCompleted() {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " response completed.");
		}

		// 不需要做什么
	}

	@Override
	public void errorRequestTransferTo(IOException e, Object msg, OutputStream writer) {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " error request transfer to.");
		}

		requestTransferError(e);
	}

	// ------------ 以下是Runnable的实现

	@Override
	public void run() {
		try {
			// 验证头
			m_InputAuther.auth(m_Header);
			// 检查权限
			// m_Supporter.getRightManage().verifyAccess(m_Header); 先检查微服务是否存在
			// 对接微服务端
			m_Supporter.getGateway().joint(HttpTunnel.this);
		} catch (Throwable e) {
			if (_Logger.isDebugEnabled() && e instanceof AuthException) {
				_Logger.debug("request header, s:" + m_Header.getService() + ",acc:" + m_Header.getAccessId() + ",n:"
						+ m_Header.getNoise() + ",tag:" + m_Header.getTag() + ",ch:" + m_Header.getChannel() + ",cs:"
						+ m_Header.getContentSign() + ",sign:" + m_Header.getSign());
			}
			responseError(e);
			return;
		}
	}
}
