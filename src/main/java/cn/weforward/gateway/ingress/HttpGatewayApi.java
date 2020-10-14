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
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.gateway.Configure;
import cn.weforward.gateway.api.GatewayApi;
import cn.weforward.gateway.util.CloseUtil;
import cn.weforward.gateway.util.OverloadLimit.OverloadLimitToken;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.Request;
import cn.weforward.protocol.Response;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.aio.ServerHandler;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpContext;
import cn.weforward.protocol.aio.http.HttpHeaderHelper;
import cn.weforward.protocol.aio.http.HttpHeaderOutput;
import cn.weforward.protocol.aio.http.HttpHeaders;
import cn.weforward.protocol.exception.AuthException;
import cn.weforward.protocol.exception.WeforwardException;
import cn.weforward.protocol.ext.Producer;
import cn.weforward.protocol.support.SimpleResponse;

/**
 * 网关api请求处理
 * 
 * @author zhangpengji
 *
 */
public class HttpGatewayApi implements ServerHandler, Runnable, Producer.Output {
	static final Logger _Logger = LoggerFactory.getLogger(HttpGatewayApi.class);

	/** 进度 - 占位标识 */
	static final int SCHEDULE_MASK = 0xFFFF;
	/** 进度 - 处理已开始 */
	static final int SCHEDULE_EXECUTE = 50;
	/** 进度 - 处理已结束 */
	static final int SCHEDULE_END = 100;
	/** 进度 - 已取消 */
	static final int SCHEDULE_MARK_ABORT = 0x10000;

	HttpContext m_Context;
	ServerHandlerSupporter m_Supporter;
	volatile OverloadLimitToken m_LimitToken;

	volatile Header m_Header;
	volatile InputStream m_RequestInput;
	volatile OutputStream m_ResponseOutput;
	// 当前进度
	volatile int m_Schedule;

	HttpGatewayApi(HttpContext ctx, ServerHandlerSupporter supporter) {
		this(ctx, supporter, null);
	}

	HttpGatewayApi(HttpContext ctx, ServerHandlerSupporter supporter, OverloadLimitToken limitToken) {
		m_Context = ctx;
		m_Context.setMaxHttpSize(Configure.getInstance().getGatewayApiMaxSize());

		m_Supporter = supporter;

		m_LimitToken = limitToken;
	}

	@Override
	public void requestHeader() {
		if (null != m_Header) {
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
		m_Header = header;
	}

	@Override
	public void prepared(int available) {
		// 什么都不用做
	}

	@Override
	public void requestAbort() {
		synchronized (this) {
			m_Schedule |= SCHEDULE_MARK_ABORT;
		}

		if ((m_Schedule & SCHEDULE_MASK) >= SCHEDULE_EXECUTE) {
			// 已开始执行
			return;
		}
		responseError(WeforwardException.CODE_NETWORK_ERROR, "调用中止");
	}

	@Override
	public void requestCompleted() {
		try {
			// 请求中断后上下文会清理请求内容，先缓存下来
			m_RequestInput = m_Context.mirrorRequestStream(0);
		} catch (IOException e) {
			responseError(e);
			return;
		}

		Executor executor = m_Supporter.getApiExecutor();
		try {
			executor.execute(this);
		} catch (RejectedExecutionException e) {
			// 线程池忙？
			_Logger.warn(e.toString(), e);
			responseError(WeforwardException.CODE_GATEWAY_BUSY, "网关忙");
		}
	}

	@Override
	public void run() {
		executeApi();
	}

	protected GatewayApi getApi(String apiName) {
		return m_Supporter.getGateWayApis().getApi(apiName);
	}

	protected void readHeader(HttpHeaders hs, Header header) {
		HttpHeaderHelper.fromHttpHeaders(hs, header);
	}

	protected void verifyAccess(Header header) throws AuthException {
		m_Supporter.getRightManage().verifyAccess(header);
	}

	protected Producer getProducer() {
		return m_Supporter.getProducer();
	}

	protected void writeHeader(HttpHeaderOutput output, Header header) throws IOException {
		HttpHyHeaderHelper.outHeaders(header, output);
	}

	private void executeApi() {
		synchronized (this) {
			if ((m_Schedule & SCHEDULE_MASK) >= SCHEDULE_EXECUTE) {
				return;
			}
			m_Schedule = (m_Schedule & ~SCHEDULE_MASK) | SCHEDULE_EXECUTE;
		}

		try {
			String apiName = HttpHeaderHelper.getServiceName(m_Header.getService());
			GatewayApi api = getApi(apiName);
			if (null == api) {
				responseError(WeforwardException.CODE_API_NOT_FOUND, "Api不存在：" + apiName);
				return;
			}
			if (ServiceName.KEEPER.name.equals(api.getName()) && Configure.getInstance().isShieldKepper()) {
				responseError(WeforwardException.CODE_UNREADY, "已屏蔽keeper api");
				return;
			}
			verifyAccess(m_Header);

			Producer producer = getProducer();
			// 解析Request
			Request request;
			request = producer.fetchRequest(new Producer.Input() {

				@Override
				public Header readHeader() throws IOException {
					return m_Header;
				}

				@Override
				public InputStream getInputStream() throws IOException {
					return m_RequestInput;
				}
			});

			// 调用api
			Response response;
			response = api.invoke(request);
			// 检查在api.invoke之后，即便网络中断也执行请求
			if (SCHEDULE_MARK_ABORT == (SCHEDULE_MARK_ABORT & m_Schedule)) {
				if (_Logger.isTraceEnabled()) {
					_Logger.trace("调用已中断，不输出响应");
				}
				return;
			}

			// 返回结果
			if (null == response.getHeader()) {
				Header respHeader = new Header(apiName);
				respHeader.setAuthType(Header.AUTH_TYPE_NONE);
				respHeader.setContentType(m_Header.getContentType());
				respHeader.setCharset(m_Header.getCharset());
				response.setHeader(respHeader);
			}

			m_ResponseOutput = m_Context.openResponseWriter(HttpConstants.OK, null);
			producer.make(response, this);
		} catch (Throwable e) {
			responseError(e);
			return;
		} finally {
			end();
		}
	}

	private void responseError(Throwable e) {
		_Logger.error(e.toString(), e);
		if (e instanceof IOException) {
			responseError(WeforwardException.CODE_NETWORK_ERROR, e.getMessage());
		} else if (e instanceof WeforwardException) {
			WeforwardException wf = (WeforwardException) e;
			responseError(wf.getCode(), wf.getMessage());
		} else {
			responseError(WeforwardException.CODE_INTERNAL_ERROR, e.getMessage());
		}
	}

	private void responseError(int code, String msg) {
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("Api调用失败:").append(code).append('/').append(msg);
			sb.append(" ,api:" + (null == m_Header ? m_Context.getUri() : m_Header.getService()));
			_Logger.error(sb.toString());

			if (SCHEDULE_MARK_ABORT != (SCHEDULE_MARK_ABORT & m_Schedule) && null == m_ResponseOutput) {
				Header respHeader = new Header(null);
				respHeader.setAuthType(Header.AUTH_TYPE_NONE);
				respHeader.setContentType(Header.CONTENT_TYPE_JSON);
				respHeader.setCharset(Header.CHARSET_UTF8);

				SimpleResponse response = new SimpleResponse(respHeader);
				response.setResponseCode(code);
				response.setResponseMsg(msg);

				int httpCode = HttpConstants.OK;
				if (WeforwardException.CODE_UNREADY == code || WeforwardException.CODE_GATEWAY_BUSY == code) {
					httpCode = HttpConstants.SERVICE_UNAVAILABLE;
				}
				m_ResponseOutput = m_Context.openResponseWriter(httpCode, null);
				getProducer().make(response, this);
			}
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
		} finally {
			end();
		}

	}

	private void end() {
		synchronized (this) {
			if ((m_Schedule & SCHEDULE_MASK) >= SCHEDULE_END) {
				return;
			}
			m_Schedule = (m_Schedule & ~SCHEDULE_MASK) | SCHEDULE_END;
		}

		CloseUtil.close(m_RequestInput, _Logger);
		CloseUtil.close(m_ResponseOutput, _Logger);

		OverloadLimitToken token = m_LimitToken;
		m_LimitToken = null;
		if (null != token) {
			token.free();
		}
		// m_Context.close();
	}

	@Override
	public void responseTimeout() {
		// 未启用超时，不用处理
	}

	@Override
	public void responseCompleted() {
		// 什么都不用做
	}

	@Override
	public void errorRequestTransferTo(IOException e, Object msg, OutputStream writer) {
		// 未启用转发，不用处理
	}

	// --------------以下是Producer.Output的实现

	@Override
	public void writeHeader(Header header) throws IOException {
		HttpHeaderOutput output = new HttpHeaderOutput.HttpContextOutput(m_Context);
		writeHeader(output, header);
		HttpAccessControl.outHeaders(m_Context);
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return m_ResponseOutput;
	}

}
