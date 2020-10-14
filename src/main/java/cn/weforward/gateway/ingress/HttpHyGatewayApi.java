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

import cn.weforward.gateway.api.GatewayApi;
import cn.weforward.protocol.AccessLoader;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.Request;
import cn.weforward.protocol.RequestConstants;
import cn.weforward.protocol.Response;
import cn.weforward.protocol.ResponseConstants;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.aio.http.HttpContext;
import cn.weforward.protocol.aio.http.HttpHeaderOutput;
import cn.weforward.protocol.aio.http.HttpHeaders;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.exception.AuthException;
import cn.weforward.protocol.exception.SerialException;
import cn.weforward.protocol.exception.WeforwardException;
import cn.weforward.protocol.ext.Producer;
import cn.weforward.protocol.support.SimpleProducer;
import cn.weforward.protocol.support.SimpleRequest;
import cn.weforward.protocol.support.datatype.SimpleDtNumber;
import cn.weforward.protocol.support.datatype.SimpleDtObject;
import cn.weforward.protocol.support.datatype.SimpleDtString;

/**
 * 兼容hy的Api
 * 
 * @author zhangpengji
 *
 */
public class HttpHyGatewayApi extends HttpGatewayApi {

	HttpHyGatewayApi(HttpContext ctx, ServerHandlerSupporter supporter) {
		super(ctx, supporter);
	}

	@Override
	protected void readHeader(HttpHeaders hs, Header header) {
		HttpHyHeaderHelper.fromHttpHeaders(hs, header);
	}

	@Override
	protected void writeHeader(HttpHeaderOutput output, Header header) throws IOException {
		HttpHyHeaderHelper.outHeaders(header, output);
	}

	@Override
	protected void verifyAccess(Header header) throws AuthException {
		// 改服务名
		if ("hy_service_recorder".equals(header.getService())) {
			header = Header.copy(header);
			header.setService(ServiceName.SERVICE_REGISTER.name);
		} else if ("hy_distributed".equals(header.getService())) {
			header = Header.copy(header);
			header.setService(ServiceName.DISTRIBUTED.name);
		}
		super.verifyAccess(header);
	}

	@Override
	protected GatewayApi getApi(String apiName) {
		if ("hy_service_recorder".equals(apiName)) {
			apiName = ServiceName.SERVICE_REGISTER.name;
		} else if ("hy_distributed".equals(apiName)) {
			apiName = ServiceName.DISTRIBUTED.name;
		}
		return super.getApi(apiName);
	}

	@Override
	protected Producer getProducer() {
		return new HyProducer(m_Supporter.getAccessLoader());
	}

	static class HyProducer extends SimpleProducer {

		public HyProducer(AccessLoader loader) {
			super(loader);
		}

		@Override
		protected Request toRequest(Header header, DtObject contentObj) throws SerialException {
			try {
				Request request = new SimpleRequest(header);
				DtObject serviceInvoke = contentObj.getObject(RequestConstants.INVOKE);
				request.setServiceInvoke(serviceInvoke);
				return request;
			} catch (Exception e) {
				throw new SerialException(WeforwardException.CODE_SERIAL_ERROR, "不符合Request标准格式", e);
			}
		}

		@Override
		protected DtObject toDtObject(Response response) {
			SimpleDtObject contentObj = new SimpleDtObject(false);
			// 排序属性名，保证先输出wf_resp节点
			contentObj.setAttributeComparator(COMP_RESP);
			SimpleDtObject respObj = new SimpleDtObject(false);
			respObj.put("hy_code", SimpleDtNumber.valueOf(response.getResponseCode()));
			respObj.put("hy_msg", SimpleDtString.valueOf(response.getResponseMsg()));
			contentObj.put("hy_resp", respObj);
			contentObj.put(ResponseConstants.RESULT, response.getServiceResult());
			return contentObj;
		}
	}
}
