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

import cn.weforward.common.json.JsonPair;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpContext;
import cn.weforward.protocol.aio.http.HttpHeaderOutput;
import cn.weforward.protocol.aio.http.HttpHeaders;

/**
 * 兼容hy的Tunnel
 * 
 * @author zhangpengji
 *
 */
public class HttpHyTunnel extends HttpTunnel {

	Boolean m_HoninyunMode;

	public HttpHyTunnel(HttpContext ctx, ServerHandlerSupporter supporter, String addr, boolean trust) {
		super(ctx, supporter, addr, trust);
	}

	@Override
	protected void readHeader(HttpHeaders hs, Header header) {
		if (null == m_HoninyunMode) {
			// 检查是否hy模式
			String auth = hs.get(HttpConstants.AUTHORIZATION);
			if (null != auth && auth.contains("HY-")) {
				m_HoninyunMode = true;
			} else {
				m_HoninyunMode = false;
			}
		}
		if (!isHoninyunMode()) {
			super.readHeader(hs, header);
		} else {
			HttpHyHeaderHelper.fromHttpHeaders(hs, header);
		}
	}

	boolean isHoninyunMode() {
		return Boolean.TRUE.equals(m_HoninyunMode);
	}

	@Override
	protected void writeHeader(HttpHeaderOutput output, Header header) throws IOException {
		if (!isHoninyunMode()) {
			super.writeHeader(output, header);
		} else {
			HttpHyHeaderHelper.outHeaders(header, output);
		}
	}

	@Override
	protected WfReq createWfReq() {
		if (!isHoninyunMode()) {
			return super.createWfReq();
		}

		return new HyReq();
	}

	static class HyReq extends WfReq {
		@Override
		String getName() {
			return "hy_req";
		}
	}

	@Override
	protected WfResp createWfResp(int code, String msg) {
		if (!isHoninyunMode()) {
			return super.createWfResp(code, msg);
		}

		HyResp resp = new HyResp();
		resp.setCode(code);
		resp.setMsg(msg);
		return resp;
	}

	static class HyResp extends WfResp {

		@Override
		String getName() {
			return "hy_resp";
		}

		@Override
		void setCode(int code) {
			items.put("hy_code", new JsonPair("hy_code", code));
		}

		@Override
		void setMsg(String msg) {
			items.put("hy_msg", new JsonPair("hy_msg", msg));
		}
	}
}
