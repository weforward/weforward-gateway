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
import cn.weforward.protocol.aio.ServerHandler;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpContext;
import cn.weforward.protocol.aio.http.HttpHeaders;

/**
 * 浏览器跨域控制
 * 
 * @author zhangpengji
 *
 */
public class HttpAccessControl implements ServerHandler {
	static final Logger _Logger = LoggerFactory.getLogger(HttpAccessControl.class);

	HttpContext m_Context;

	public HttpAccessControl(HttpContext ctx) {
		m_Context = ctx;
	}

	@Override
	public void requestHeader() {
		HttpHeaders headers = m_Context.getRequestHeaders();
		if (!StringUtil.isEmpty(headers.get("Access-Control-Request-Method"))) {
			try {
				m_Context.setResponseHeader("Access-Control-Allow-Origin", "*");
				m_Context.setResponseHeader("Access-Control-Allow-Methods", "POST");
				m_Context.setResponseHeader("Access-Control-Allow-Headers",
						"Authorization,Content-Type,Content-Encoding,User-Agent,X-Requested-With,Accept,Accept-Encoding,"
								+ HttpConstants.WF_TAG + "," + HttpConstants.WF_NOISE + ","
								+ HttpConstants.WF_CONTENT_SIGN);
				// response.setHeader("Access-Control-Allow-Headers", "*");
				// 减少预检请求的次数
				m_Context.setResponseHeader("Access-Control-Max-Age", "3600");
				m_Context.response(HttpConstants.OK, null);
			} catch (IOException e) {
				_Logger.error(e.toString(), e);
			}
		} else {
			try {
				m_Context.response(HttpConstants.BAD_REQUEST, HttpContext.RESPONSE_AND_CLOSE);
			} catch (IOException e) {
				_Logger.error(e.toString(), e);
			}
		}
		// m_Context.close();
	}

	/**
	 * 在任意请求的结束后，补充跨域访问的头
	 * 
	 * @param ctx
	 * @throws IOException
	 */
	public static void outHeaders(HttpContext ctx) throws IOException {
		// 不限制跨域访问
		ctx.setResponseHeader("Access-Control-Allow-Origin", "*");
		// 允许获取headers
		ctx.setResponseHeader("Access-Control-Expose-Headers",
				"*," + HttpConstants.WF_TAG + "," + HttpConstants.WF_NOISE + "," + HttpConstants.WF_CONTENT_SIGN);
	}

	@Override
	public void prepared(int available) {

	}

	@Override
	public void requestAbort() {

	}

	@Override
	public void requestCompleted() {

	}

	@Override
	public void responseTimeout() {

	}

	@Override
	public void responseCompleted() {

	}

	@Override
	public void errorRequestTransferTo(IOException e, Object msg, OutputStream writer) {

	}

}
