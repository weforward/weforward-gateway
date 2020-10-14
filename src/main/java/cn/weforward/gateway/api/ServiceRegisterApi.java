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

import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.GatewayExt;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.gateway.vo.ServiceVo;
import cn.weforward.protocol.support.BeanObjectMapper;
import cn.weforward.protocol.support.CommonServiceCodes;
import cn.weforward.protocol.support.SimpleObjectMapperSet;
import cn.weforward.protocol.support.SpecificationChecker;
import cn.weforward.protocol.support.datatype.FriendlyObject;

/**
 * Service Register Api
 * 
 * @author zhangpengji
 *
 */
class ServiceRegisterApi extends AbstractGatewayApi {

	SimpleObjectMapperSet m_Mappers;
	GatewayExt m_Gateway;

	ServiceRegisterApi() {
		super();

		SimpleObjectMapperSet mappers = new SimpleObjectMapperSet();
		mappers.register(BeanObjectMapper.getInstance(ServiceVo.class));
		m_Mappers = mappers;

		register(register);
		register(unregister);
	}

	@Override
	SimpleObjectMapperSet getMappers() {
		return m_Mappers;
	}

	public void setGateway(GatewayExt gw) {
		m_Gateway = gw;
	}

	@Override
	public String getName() {
		return ServiceName.SERVICE_REGISTER.name;
	}

	private ApiMethod register = new ApiMethod("register") {

		@Override
		Void execute(Header reqHeader, FriendlyObject params) throws ApiException {
			ServiceVo info = params.toObject(ServiceVo.class, m_Mappers);
			String errorMsg = checkService(info);
			if (!StringUtil.isEmpty(errorMsg)) {
				throw new ApiException(CommonServiceCodes.ILLEGAL_ARGUMENT.code, errorMsg);
			}
			m_Gateway.registerService(reqHeader.getAccessId(), info, info.serviceRuntime);
			return null;
		}
	};

	private ApiMethod unregister = new ApiMethod("unregister") {

		@Override
		Void execute(Header reqHeader, FriendlyObject params) throws ApiException {
			ServiceVo info = params.toObject(ServiceVo.class, m_Mappers);
			String errorMsg = checkService(info);
			if (!StringUtil.isEmpty(errorMsg)) {
				throw new ApiException(CommonServiceCodes.ILLEGAL_ARGUMENT.code, errorMsg);
			}
			m_Gateway.unregisterService(reqHeader.getAccessId(), info);
			return null;
		}
	};

	String checkService(ServiceVo info) {
		String nameErr = SpecificationChecker.checkServiceName(info.name);
		if (!StringUtil.isEmpty(nameErr)) {
			return nameErr;
		}
		if ((StringUtil.isEmpty(info.domain) || 0 == info.port) && ListUtil.isEmpty(info.urls)) {
			return "微服务信息不完整";
		}
		String noErr = SpecificationChecker.checkServiceNo(info.no);
		if (!StringUtil.isEmpty(noErr)) {
			return noErr;
		}
		int maxSize = info.requestMaxSize;
		if (maxSize < 0 || maxSize > Configure.getInstance().getServiceRequestMaxSize()) {
			return "微服务请求数据的最大字节数不合法：" + maxSize;
		}
		return null;
	}
}
