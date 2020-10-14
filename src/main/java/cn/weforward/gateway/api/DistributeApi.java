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

import java.util.List;

import cn.weforward.common.ResultPage;
import cn.weforward.gateway.distribute.DistributeManage;
import cn.weforward.gateway.distribute.GatewayNode;
import cn.weforward.gateway.distribute.GatewayNodeMapper;
import cn.weforward.gateway.distribute.ServiceExtMapper;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.ext.ObjectMapper;
import cn.weforward.protocol.ops.ServiceExt;
import cn.weforward.protocol.support.PageData;
import cn.weforward.protocol.support.PageDataMapper;
import cn.weforward.protocol.support.SimpleObjectMapperSet;
import cn.weforward.protocol.support.datatype.FriendlyObject;

/**
 * Distribute Api
 * 
 * @author zhangpengji
 *
 */
public class DistributeApi extends AbstractGatewayApi {

	DistributeManage m_DistributeManage;
	SimpleObjectMapperSet m_Mappers;

	DistributeApi() {
		super();

		m_Mappers = new SimpleObjectMapperSet() {
			@SuppressWarnings("unchecked")
			@Override
			public <E> ObjectMapper<E> getObjectMapper(Class<E> clazz) {
				if (ServiceExt.class.isAssignableFrom(clazz)) {
					return (ObjectMapper<E>) ServiceExtMapper.INSTANCE;
				}
				return super.getObjectMapper(clazz);
			}
		};
		m_Mappers.register(ServiceExtMapper.INSTANCE);
		m_Mappers.register(GatewayNodeMapper.INSTANCE);
		PageDataMapper pageDataMapper = new PageDataMapper(m_Mappers);
		m_Mappers.register(pageDataMapper);

		register(sync);
		register(getServices);
	}

	public void setDistributeManage(DistributeManage dm) {
		m_DistributeManage = dm;
	}

	@Override
	SimpleObjectMapperSet getMappers() {
		return m_Mappers;
	}

	@Override
	public String getName() {
		return ServiceName.DISTRIBUTED.name;
	}

	private ApiMethod sync = new ApiMethod("sync") {

		@Override
		Void execute(Header reqHeader, FriendlyObject params) throws ApiException {
			List<GatewayNode> nodes = params.getList("nodes", GatewayNode.class, getMappers());
			List<ServiceExt> regs = params.getList("reg_services", ServiceExt.class, getMappers());
			List<ServiceExt> unregs = params.getList("unreg_services", ServiceExt.class, getMappers());

			m_DistributeManage.syncFromBrother(nodes, regs, unregs);
			return null;
		}
	};

	private ApiMethod getServices = new ApiMethod("get_services", true) {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			ResultPage<ServiceExt> services = m_DistributeManage.getServices();
			if (services.getCount() > 0) {
				int page = params.getInt("page", 1);
				int pageSize = params.getInt("page_size", 1000);
				services.setPageSize(pageSize);
				services.gotoPage(page);
			}
			return PageData.valueOf(services);
		}
	};
}
