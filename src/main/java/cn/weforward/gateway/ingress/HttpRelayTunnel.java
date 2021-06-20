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

import java.io.OutputStream;
import java.util.concurrent.RejectedExecutionException;

import cn.weforward.gateway.Pipe;
import cn.weforward.gateway.auth.GatewayAuther;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpContext;
import cn.weforward.protocol.aio.http.HttpHeaderHelper;
import cn.weforward.protocol.aio.http.HttpHeaders;
import cn.weforward.protocol.exception.AuthException;
import cn.weforward.protocol.exception.WeforwardException;

/**
 * （来自其他网关的）中继的管道
 * 
 * @author zhangpengji
 *
 */
public class HttpRelayTunnel extends HttpTunnel {

	public HttpRelayTunnel(HttpContext ctx, ServerHandlerSupporter supporter, String addr, boolean trust) {
		super(ctx, supporter, addr, trust);
	}

	@Override
	public boolean isRelay() {
		return true;
	}

	@Override
	public int getWaitTimeout() {
		return 0;
	}

	@Override
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
			HttpHeaderHelper.fromHttpHeaders(hs, header);
		}
		HeaderChecker.CheckResult checkResult = HeaderChecker.check(header);
		if (0 != checkResult.code) {
			responseError(checkResult.code, checkResult.msg);
			return;
		}

		GatewayAuther auther = new GatewayAuther(m_Supporter.getAccessManage());
		try {
			auther.verify(header);
		} catch (AuthException e) {
			responseError(e);
			return;
		}

		m_Header = header;
	}

	@Override
	void parseWfReq() {
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_PARES_WF_REQ) {
				return;
			}
			m_Schedule = SCHEDULE_PARES_WF_REQ;
		}

		try {
			m_Supporter.getRpcExecutor().execute(this);
		} catch (RejectedExecutionException e) {
			// 线程池忙？
			HttpTunnel._Logger.warn(e.toString(), e);
			responseError(WeforwardException.CODE_GATEWAY_BUSY, "网关忙");
		}
	}

	@Override
	public void run() {
		try {
			// 对接微服务端
			m_Supporter.getGateway().joint(HttpRelayTunnel.this);
		} catch (Throwable e) {
			responseError(e);
			return;
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

		try {
			m_Context.requestTransferTo(pipeOutput, 0);
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

	@Override
	public void responseReady(Pipe pipe) {
		checkPipe(pipe);
		synchronized (this) {
			if (m_Schedule >= SCHEDULE_RESPONSE_TRANSFERED) {
				return;
			}
			m_Schedule = SCHEDULE_RESPONSE_TRANSFERED;
		}

		try {
			Header header = pipe.getHeader();
			writeHeader(header);
			m_Output = m_Context.openResponseWriter(HttpConstants.OK, null);
		} catch (Throwable e) {
			responseError(e);
			return;
		}

		m_Pipe.responseReady(this, m_Output);
	}
}
