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
import java.io.OutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.StreamPipe;
import cn.weforward.gateway.StreamTunnel;
import cn.weforward.gateway.util.CloseUtil;
import cn.weforward.gateway.util.OverloadLimit;
import cn.weforward.gateway.util.OverloadLimit.OverloadLimitToken;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.aio.ServerHandler;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpContext;
import cn.weforward.protocol.aio.http.HttpHeaders;
import cn.weforward.protocol.exception.AuthException;

/**
 * 基于Http的<code>StreamTunnel</code>实现
 * 
 * @author zhangpengji
 *
 */
public class HttpStreamTunnel implements StreamTunnel, ServerHandler {
	static final Logger _Logger = LoggerFactory.getLogger(HttpStreamTunnel.class);

	/** 进度 - 已开始业务处理 */
	static final int SCHEDULE_BEGIN = 1;
	/** 进度 - 已开始转发请求内容 */
	static final int SCHEDULE_REQUEST_TRANSFERED = 50;
	/** 进度 - 处理已结束 */
	static final int SCHEDULE_END = 100;

	HttpContext m_Context;
	ServerHandlerSupporter m_Supporter;
	volatile OverloadLimitToken m_LimitToken;

	volatile int m_Schedule;
	// 对接的微服务流管道
	volatile StreamPipe m_Pipe;
	// 请求已中断
	volatile boolean m_RequestAbort;
	// 响应输出流
	volatile OutputStream m_Output;
	String m_ServiceName;
	String m_ServiceNo;
	String m_ResourceId;
	String m_ContentType;
	long m_Length;

	public HttpStreamTunnel(HttpContext ctx, ServerHandlerSupporter supporter) {
		m_Context = ctx;
		m_Context.setMaxHttpSize(Configure.getInstance().getStreamChannelMaxSize());

		m_Supporter = supporter;
	}

	@Override
	public String getServiceName() {
		return m_ServiceName;
	}

	@Override
	public String getResourceId() {
		return m_ResourceId;
	}

	@Override
	public String getContentType() {
		return m_ContentType;
	}

	@Override
	public long getLength() {
		return m_Length;
	}
	
	@Override
	public String getServiceNo() {
		return m_ServiceNo;
	}
	
	void requestHeader0() {
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_BEGIN) {
				return;
			}
			m_Schedule = SCHEDULE_BEGIN;
		}
		
		m_Supporter.getMetricsCollecter().onStreamBegin();

		if (!m_Supporter.getGateway().isReady()) {
			responseError(HttpConstants.SERVICE_UNAVAILABLE, "网关未就绪");
			return;
		}
		
		OverloadLimit limit = m_Supporter.getStreamOverloadLimit();
		if (null != limit) {
			OverloadLimitToken token = limit.use();
			if (null == token) {
				_Logger.error("请求过多：" + limit.getInUse());
				responseError(HttpConstants.SERVICE_UNAVAILABLE, "网关忙");
				return;
			}
			m_LimitToken = token;
		}
		
		if(!parseRequest()) {
			return;
		}

		// joint过程不会依赖外部系统，直接在netty工作线程处理
		m_Supporter.getGateway().joint(this);
	}
	
	protected boolean parseRequest() {
		String query = m_Context.getQueryString();
		StreamResourceToken token;
		try {
			token = m_Supporter.parseResourceToken(query);
		} catch (IllegalArgumentException e) {
			_Logger.error("uri异常:" + StringUtil.limit(query, 200), e);
			responseError(HttpConstants.BAD_REQUEST, "参数异常");
			return false;
		}
		if (token.isExpire()) {
			responseError(HttpConstants.BAD_REQUEST, "链接已过期");
			return false;
		}
		m_ServiceName = token.getServiceName();
		m_ServiceNo = token.getServiceNo();
		m_ResourceId = token.getResourceId();

		HttpHeaders headers = m_Context.getRequestHeaders();
		if (null != headers) {
			m_ContentType = headers.getHeaderRaw(HttpConstants.CONTENT_TYPE);
			String length = headers.getHeaderRaw(HttpConstants.CONTENT_LENGTH);
			if (!StringUtil.isEmpty(length)) {
				try {
					m_Length = Long.parseLong(length);
				} catch (NumberFormatException e) {
					// ignore
				}
			}
		}
		return true;
	}

	@Override
	public void requestReady(StreamPipe pipe, OutputStream output) {
		boolean abort = false;
		synchronized (this) {
			if (m_RequestAbort) {
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

		try {
			m_Context.requestTransferTo(output, 0);
		} catch (Throwable e) {
			responseError(e);
			return;
		}
		synchronized (this) {
			m_Schedule = SCHEDULE_REQUEST_TRANSFERED;
		}
		if (m_Context.isRequestCompleted()) {
			requestCompleted0();
		}
	}

	void requestCompleted0() {
		synchronized (this) {
			if (m_Schedule != SCHEDULE_REQUEST_TRANSFERED) {
				// 等微服务端的管道就绪后再触发
				return;
			}
		}

		if (null != m_Pipe) {
			m_Pipe.requestCompleted(this);
		}
	}

	void checkPipe(StreamPipe pipe) {
		if (null != m_Pipe && m_Pipe != pipe) {
			// 对不上？
			throw new IllegalStateException(pipe + "与当前[" + m_Pipe + "]不匹配");
		}
	}

	@Override
	public void responseReady(StreamPipe pipe, int httpCode, int errCode) {
		checkPipe(pipe);

		try {
			String contentType = pipe.getContentType();
			if (!StringUtil.isEmpty(contentType)) {
				m_Context.setResponseHeader(HttpConstants.CONTENT_TYPE, contentType);
			}
			String contentDisposition = pipe.getContentDisposition();
			if (!StringUtil.isEmpty(contentDisposition)) {
				m_Context.setResponseHeader(HttpConstants.CONTENT_DISPOSITION, contentDisposition);
			}
			long length = pipe.getLength();
			if (0 != length) {
				m_Context.setResponseHeader(HttpConstants.CONTENT_LENGTH, String.valueOf(length));
			}
			HttpAccessControl.outHeaders(m_Context);
			if (0 == httpCode) {
				httpCode = toHttpCode(errCode);
			}
			m_Output = m_Context.openResponseWriter(httpCode, null);
		} catch (Throwable e) {
			responseError(e);
			return;
		}

		m_Pipe.responseReady(this, m_Output);
	}

	@Override
	public void responseCompleted(StreamPipe pipe) {
		checkPipe(pipe);

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
		
		m_Supporter.getMetricsCollecter().onStreamEnd();

		OverloadLimitToken token = m_LimitToken;
		m_LimitToken = null;
		if (null != token) {
			token.free();
		}

		// m_Context.close();
	}

	void requestAbort0() {
		synchronized (this) {
			m_RequestAbort = true;
		}

		responseError(HttpConstants.INTERNAL_SERVER_ERROR, "网络中断");
	}

	@Override
	public void responseError(StreamPipe pipe, int code, String msg) {
		checkPipe(pipe);

		m_Pipe = null;

		responseError(toHttpCode(code), msg);
	}

	static int toHttpCode(int code) {
		switch (code) {
		case CODE_OK:
			return HttpConstants.OK;
		case CODE_ACCEPTED:
			return HttpConstants.ACCEPTED;
		case CODE_ILLEGAL_ARGUMENT:
			return HttpConstants.BAD_REQUEST;
		case CODE_FORBIDDEN:
			return HttpConstants.FORBIDDEN;
		case CODE_NOT_FOUND:
			return HttpConstants.NOT_FOUND;
		case CODE_TOO_LARGE:
			return HttpConstants.REQUEST_ENTITY_TOO_LARGE;
		case CODE_UNAVAILABLE:
			return HttpConstants.SERVICE_UNAVAILABLE;
		default:
			return HttpConstants.INTERNAL_SERVER_ERROR;
		}
	}

	void responseError(Throwable e) {
		_Logger.error(e.toString(), e);

		String msg;
		if (e instanceof IOException) {
			msg = "网络错误";
		} else if (e instanceof AuthException) {
			msg = e.getMessage();
		} else {
			msg = "网关内部错误";
		}
		responseError(HttpConstants.INTERNAL_SERVER_ERROR, msg);
	}

	void responseError(int httpCode, String msg) {
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_END) {
				return;
			}
			m_Schedule = SCHEDULE_END;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("请求失败:").append(httpCode).append('/').append(msg);
		sb.append(" ,uri:" + m_Context.getUri());
		_Logger.error(sb.toString());

		if (m_RequestAbort || null != m_Output) {
			// 已经无法输出错误了
			// Abort时，不用关Output，context会处理
			if (!m_RequestAbort && null != m_Output) {
				CloseUtil.close(m_Output, _Logger);
			}
		} else {
			try {
				m_Context.setResponseHeader(HttpConstants.CONTENT_TYPE, "text/plain;charset=" + Header.CHARSET_DEFAULT);
				HttpAccessControl.outHeaders(m_Context);
				m_Context.response(httpCode, (null == msg ? null : msg.getBytes(Header.CHARSET_DEFAULT)));
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
			}
		}
		
		m_Supporter.getMetricsCollecter().onStreamEnd();

		OverloadLimitToken token = m_LimitToken;
		m_LimitToken = null;
		if (null != token) {
			token.free();
		}

		m_Context.disconnect();

		StreamPipe pipe = m_Pipe;
		if (null != pipe) {
			// m_Pipe = null;
			pipe.requestCanceled(this);
		}
	}

	// ------------ 以下是ServerHandler的实现

	@Override
	public void requestHeader() {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " request header.");
		}

		requestHeader0();
	}

	@Override
	public void prepared(int available) {
		// ignore
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

		requestCompleted0();
	}

	@Override
	public void responseTimeout() {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " response timeout.");
		}

		// ignore
	}

	@Override
	public void responseCompleted() {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " response completed.");
		}

		// ignore
	}

	@Override
	public void errorRequestTransferTo(IOException e, Object msg, OutputStream writer) {
		if (HttpTunnel._Logger.isTraceEnabled()) {
			HttpTunnel._Logger.trace(m_Context.getUri() + " error request transfer to.");
		}

		responseError(e);
	}
}
