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
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.json.JsonOutputStream;
import cn.weforward.common.restful.RestfulResponse;
import cn.weforward.common.util.TransList;
import cn.weforward.gateway.util.OverloadLimit.OverloadLimitToken;
import cn.weforward.protocol.aio.ServerContext;
import cn.weforward.protocol.aio.ServerHandler;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.doc.ServiceDocument;
import cn.weforward.protocol.serial.JsonSerialEngine;
import cn.weforward.protocol.support.datatype.SimpleDtList;
import cn.weforward.protocol.support.datatype.SimpleDtObject;
import cn.weforward.protocol.support.doc.ServiceDocumentVo;

/**
 * 微服务文档请求处理
 * 
 * @author zhangpengji
 *
 */
public class ServiceDocHandler implements ServerHandler, Runnable {
	static final Logger _Logger = LoggerFactory.getLogger(ServiceDocHandler.class);

	ServerContext m_Context;
	ServerHandlerSupporter m_Supporter;
	volatile OverloadLimitToken m_LimitToken;

	ServiceDocHandler(ServerContext ctx, ServerHandlerSupporter supporter) {
		this(ctx, supporter, null);
	}

	ServiceDocHandler(ServerContext ctx, ServerHandlerSupporter supporter, OverloadLimitToken token) {
		m_Context = ctx;
		m_Supporter = supporter;
		m_LimitToken = token;
	}

	@Override
	public void requestHeader() {
		// 不处理
	}

	@Override
	public void prepared(int available) {
		// 不处理
	}

	@Override
	public void requestAbort() {
		clear();
	}

	@Override
	public void requestCompleted() {
		Executor executor = m_Supporter.getDocExecutor();
		try {
			executor.execute(this);
		} catch (RejectedExecutionException e) {
			// 线程池忙？
			_Logger.warn(String.valueOf(e), e);
			// response(RestfulResponse.STATUS_SERVICE_UNAVAILABLE);
			response(RestfulResponse.STATUS_TOO_MANY_REQUESTS);
		}
	}

	void showDocument() throws IOException {
		SimpleDtObject result;
		try {
			String uri = m_Context.getUri();
			int idx = uri.lastIndexOf('/');
			if (-1 == idx || idx == uri.length() - 1) {
				response(HttpConstants.NOT_FOUND);
				return;
			}

			String serviceName = uri.substring(idx + 1);
			List<ServiceDocument> docs = m_Supporter.getGateway().getDocuments(serviceName);
			if (docs.isEmpty()) {
				response(HttpConstants.NOT_FOUND);
				return;
			}

			List<ServiceDocumentVo> vos = new TransList<ServiceDocumentVo, ServiceDocument>(docs) {

				@Override
				protected ServiceDocumentVo trans(ServiceDocument src) {
					return ServiceDocumentVo.valueOf(src);
				}
			};
			result = new SimpleDtObject();
			result.put("docs", SimpleDtList.toDtList(vos, ServiceDocumentVo.MAPPER));
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
			response(HttpConstants.INTERNAL_SERVER_ERROR);
			return;
		}

		m_Context.setResponseHeader("Content-Type", "application/json;charset=utf-8");
		HttpAccessControl.outHeaders(m_Context);

		OutputStream out;
		out = m_Context.openResponseWriter(HttpConstants.OK, null);
		JsonOutputStream jos = new JsonOutputStream(out);
		JsonSerialEngine.formatObject(result, jos);
		out.close();
	}

	void response(int code) {
		try {
			HttpAccessControl.outHeaders(m_Context);
			m_Context.response(code, null);
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
		} finally {
			clear();
		}
	}

	@Override
	public void responseTimeout() {
		// 不处理
	}

	@Override
	public void responseCompleted() {
		// 不处理
	}

	@Override
	public void errorRequestTransferTo(IOException e, Object msg, OutputStream writer) {
		// 不处理
	}

	@Override
	public void run() {
		try {
			showDocument();
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
			// m_Context.close();
		} finally {
			clear();
		}
	}

	void clear() {
		OverloadLimitToken token = m_LimitToken;
		m_LimitToken = null;
		if (null != token) {
			token.free();
		}
	}
}
