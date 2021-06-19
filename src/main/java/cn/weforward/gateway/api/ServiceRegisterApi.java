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

import cn.weforward.common.ResultPage;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.GatewayExt;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.aio.ClientChannel;
import cn.weforward.protocol.aio.ServerBackwardChannel;
import cn.weforward.protocol.aio.ServerContext;
import cn.weforward.protocol.exception.WeforwardException;
import cn.weforward.protocol.gateway.vo.ServiceVo;
import cn.weforward.protocol.support.BeanObjectMapper;
import cn.weforward.protocol.support.CommonServiceCodes;
import cn.weforward.protocol.support.PageData;
import cn.weforward.protocol.support.PageDataMapper;
import cn.weforward.protocol.support.SimpleObjectMapperSet;
import cn.weforward.protocol.support.datatype.FriendlyObject;

/**
 * Service Register Api
 * 
 * @author zhangpengji
 *
 */
class ServiceRegisterApi extends AbstractGatewayApi implements PluginListener {

	ServiceChecker m_ServiceChecker;
	SimpleObjectMapperSet m_Mappers;
	GatewayExt m_Gateway;

	ServiceRegisterApi() {
		super();
		m_ServiceChecker = ServiceChecker.DEFALUT;

		SimpleObjectMapperSet mappers = new SimpleObjectMapperSet();
		mappers.register(BeanObjectMapper.getInstance(ServiceVo.class));
		PageDataMapper pageDataMapper = new PageDataMapper(mappers);
		mappers.register(pageDataMapper);
		m_Mappers = mappers;

		register(register);
		register(unregister);
		register(listServiceName);
	}

	@Override
	SimpleObjectMapperSet getMappers() {
		return m_Mappers;
	}

	public void setGateway(GatewayExt gw) {
		m_Gateway = gw;
	}

	public void setPluginContainer(PluginContainer container) {
		container.register(this);
	}

	@Override
	public void onPluginLoad(Pluginable plugin) {
		if (plugin instanceof ServiceChecker) {
			((ServiceChecker) plugin).setPreCheck(m_ServiceChecker);
			m_ServiceChecker = (ServiceChecker) plugin;
		}
	}

	@Override
	public void onPluginUnload(Pluginable plugin) {
	}

	@Override
	public String getName() {
		return ServiceName.SERVICE_REGISTER.name;
	}

	private ApiMethod register = new ApiMethod("register") {

		@Override
		Object execute(Header header, FriendlyObject params) throws ApiException, WeforwardException {
			throw new ApiException(CommonServiceCodes.INTERNAL_ERROR.code, "未实现");
		}

		@Override
		Void execute(Header reqHeader, FriendlyObject params, ServerContext context) throws ApiException {
			ServiceVo info = params.toObject(ServiceVo.class, m_Mappers);
			String owner = reqHeader.getAccessId();
			String errorMsg = m_ServiceChecker.check(info, owner);
			if (!StringUtil.isEmpty(errorMsg)) {
				throw new ApiException(CommonServiceCodes.ILLEGAL_ARGUMENT.code, errorMsg);
			}
			ClientChannel channel = null;
			if (context instanceof ServerBackwardChannel) {
				channel = ((ServerBackwardChannel) context).getClientChannel();
			}
			m_Gateway.registerService(owner, info, info.serviceRuntime, channel);
			return null;
		}
	};

	private ApiMethod unregister = new ApiMethod("unregister") {

		@Override
		Void execute(Header reqHeader, FriendlyObject params) throws ApiException {
			ServiceVo info = params.toObject(ServiceVo.class, m_Mappers);
			String owner = reqHeader.getAccessId();
			String errorMsg = m_ServiceChecker.check(info, owner);
			if (!StringUtil.isEmpty(errorMsg)) {
				throw new ApiException(CommonServiceCodes.ILLEGAL_ARGUMENT.code, errorMsg);
			}
			m_Gateway.unregisterService(owner, info);
			return null;
		}
	};

	private ApiMethod listServiceName = new ApiMethod("list_service_name", true) {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String keyword = params.getString("keyword");
			int page = params.getInt("page", 1);
			int pageSize = params.getInt("page_size", 50);
			ResultPage<String> names = m_Gateway.listServiceName(keyword);
			if (names.getCount() > 0) {
				names.setPageSize(pageSize);
				names.gotoPage(page);
			}
			return new PageData(names);
		}
	};

}
