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
package cn.weforward.gateway.api;

import java.util.HashMap;
import java.util.Map;

import cn.weforward.gateway.ops.OperationsException;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.Request;
import cn.weforward.protocol.RequestConstants;
import cn.weforward.protocol.Response;
import cn.weforward.protocol.client.ext.ResponseResultMapper;
import cn.weforward.protocol.client.ext.ResponseResultObject;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.support.CommonServiceCodes;
import cn.weforward.protocol.support.SimpleObjectMapperSet;
import cn.weforward.protocol.support.SimpleResponse;
import cn.weforward.protocol.support.datatype.FriendlyObject;

/**
 * 抽象Api实现
 * 
 * @author zhangpengji
 *
 */
abstract class AbstractGatewayApi implements GatewayApi {

	Map<String, ApiMethod> m_Methods;
	ResponseResultMapper m_ResultMapper;

	AbstractGatewayApi() {
		m_Methods = new HashMap<String, ApiMethod>();
	}

	void register(ApiMethod method) {
		m_Methods.put(method.getName(), method);
	}

	abstract SimpleObjectMapperSet getMappers();

	ApiMethod getMethod(String name) {
		return m_Methods.get(name);
	}

	ResponseResultMapper getResultMapper() {
		if (null == m_ResultMapper) {
			m_ResultMapper = new ResponseResultMapper(getMappers());
		}
		return m_ResultMapper;
	}

	@Override
	public Response invoke(Request req) {
		Header header = req.getHeader();
		FriendlyObject invokeObj = new FriendlyObject(req.getServiceInvoke());
		String methodName = invokeObj.getString(RequestConstants.METHOD);
		ApiMethod method = getMethod(methodName);
		ResponseResultObject result;
		if (null == method) {
			result = ResponseResultObject.error(CommonServiceCodes.METHOD_NOT_FOUND, "方法不存在:" + methodName);
		} else {
			try {
				Object content = method.executeMethod(header, invokeObj.getFriendlyObject(RequestConstants.PARAMS));
				result = ResponseResultObject.success(content);
			} catch (ApiException e) {
				result = ResponseResultObject.error(e.code, e.msg);
			} catch (OperationsException e) {
				_Logger.error(e.toString(), e);
				result = ResponseResultObject.error(CommonServiceCodes.INTERNAL_ERROR, e.getMessage());
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
				result = ResponseResultObject.error(CommonServiceCodes.INTERNAL_ERROR);
			}
		}
		Response resp = new SimpleResponse();
		DtObject resultObj = getResultMapper().toDtObject(result);
		resp.setServiceResult(resultObj);
		return resp;
	}
}
