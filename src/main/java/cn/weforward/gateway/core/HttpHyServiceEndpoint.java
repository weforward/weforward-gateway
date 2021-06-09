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

import cn.weforward.common.json.JsonObject;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.ingress.HttpHyHeaderHelper;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpHeaderOutput;
import cn.weforward.protocol.aio.http.HttpHeaders;
import cn.weforward.protocol.ops.traffic.TrafficTableItem;

/**
 * 兼容hy的Endpoint
 * 
 * @author zhangpengji
 *
 */
public class HttpHyServiceEndpoint extends HttpServiceEndpoint {

	public HttpHyServiceEndpoint(ServiceInstanceBalance group, ServiceInstance service, TrafficTableItem rule) {
		super(group, service, rule);
	}

	@Override
	protected void readHeader(HttpHeaders hs, Header header) {
		HttpHyHeaderHelper.fromHttpHeaders(hs, header);
	}

	@Override
	protected void writeHeader(HttpHeaderOutput output, Header header) throws IOException {
		HttpHyHeaderHelper.outHeaders(header, output);
	}

	protected void writeHeader(HttpHeaderOutput output, String name, String value) throws IOException {
		if (HttpConstants.WF_CHANNEL.equals(name)) {
			name = "HY-Channel";
		}
		super.writeHeader(output, name, value);
	}

	@Override
	protected WfReq createWfReq() {
		return new HyReq();
	}

	static class HyReq extends WfReq {
		@Override
		protected String getName() {
			return "hy_req";
		}
	}

	@Override
	protected WfResp createWfResp() {
		return new HyResp();
	}

	static class HyResp extends WfResp {

		@Override
		protected String getName() {
			return "hy_resp";
		}

		@Override
		protected void foundWfResp(JsonObject obj) {
			super.foundWfResp(obj);

			Number code = (Number) obj.property("hy_code").getValue();
			this.code = code.intValue();
			String msg = (String) obj.property("hy_msg").getValue();
			this.msg = msg;
		}
	}
}
