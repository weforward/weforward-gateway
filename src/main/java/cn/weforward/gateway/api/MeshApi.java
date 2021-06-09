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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import cn.weforward.common.ResultPage;
import cn.weforward.common.util.TransList;
import cn.weforward.gateway.MeshNode;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.distribute.ServiceInstanceMapper;
import cn.weforward.gateway.mesh.MeshManage;
import cn.weforward.gateway.mesh.MeshNodeVo;
import cn.weforward.gateway.mesh.MeshNodeWrap;
import cn.weforward.gateway.ops.access.system.MasterKeyVo;
import cn.weforward.gateway.ops.access.system.ServiceAccessVo;
import cn.weforward.gateway.ops.right.RightTableVo;
import cn.weforward.gateway.ops.traffic.TrafficTableVo;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.datatype.DtList;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.datatype.DtString;
import cn.weforward.protocol.ext.ObjectMapper;
import cn.weforward.protocol.support.BeanObjectMapper;
import cn.weforward.protocol.support.PageData;
import cn.weforward.protocol.support.PageDataMapper;
import cn.weforward.protocol.support.SimpleObjectMapperSet;
import cn.weforward.protocol.support.datatype.FriendlyObject;

/**
 * Mesh Api
 * 
 * @author zhangpengji
 *
 */
public class MeshApi extends AbstractGatewayApi {

	MeshManage m_MeshManage;
	SimpleObjectMapperSet m_Mappers;

	MeshApi() {
		super();

		m_Mappers = new SimpleObjectMapperSet();
		m_Mappers.register(ServiceInstanceMapper.INSTANCE);
		m_Mappers.register(BeanObjectMapper.getInstance(MeshNodeVo.class));
		m_Mappers.register(BeanObjectMapper.getInstance(MasterKeyVo.class));
		m_Mappers.register(BeanObjectMapper.getInstance(ServiceAccessVo.class));
		m_Mappers.register(BeanObjectMapper.getInstance(RightTableVo.class));
		m_Mappers.register(BeanObjectMapper.getInstance(TrafficTableVo.class));
		PageDataMapper pageDataMapper = new PageDataMapper(m_Mappers);
		m_Mappers.register(pageDataMapper);

		register(sync);
		register(searchMasterKey);
		register(searchServiceAccess);
		register(searchRightTable);
		register(searchTrafficTable);
	}

	public void setMeshManage(MeshManage mm) {
		m_MeshManage = mm;
	}

	@Override
	SimpleObjectMapperSet getMappers() {
		return m_Mappers;
	}

	@Override
	public String getName() {
		return ServiceName.MESH.name;
	}

	private ApiMethod sync = new ApiMethod("sync") {

		@Override
		Void execute(Header reqHeader, FriendlyObject params) throws ApiException {
			List<MeshNodeVo> nodeVos = params.getList("nodes", MeshNodeVo.class, getMappers());
			List<ServiceInstance> regs = params.getList("reg_services", ServiceInstance.class, getMappers());
			List<ServiceInstance> unregs = params.getList("unreg_services", ServiceInstance.class, getMappers());
			List<Object> updateds = toList(params.getList("updated_objects"));

			List<MeshNode> nodes = new TransList<MeshNode, MeshNodeVo>(nodeVos) {

				@Override
				protected MeshNode trans(MeshNodeVo src) {
					return new MeshNodeWrap(src);
				}
			};
			m_MeshManage.syncFromBrother(nodes, regs, unregs, updateds);
			return null;
		}
	};

	List<Object> toList(DtList list) {
		if (null == list || 0 == list.size()) {
			return Collections.emptyList();
		}
		List<Object> objects = new ArrayList<Object>(list.size());
		for (int i = 0; i < list.size(); i++) {
			DtObject dtObj = (DtObject) list.getItem(i);
			DtString mapperName = dtObj.getString("__mapper");
			if (null == mapperName) {
				continue;
			}
			ObjectMapper<?> mapper = getMappers().getObjectMapper(mapperName.value());
			if (null == mapper) {
				continue;
			}
			Object obj = mapper.fromDtObject(dtObj);
			objects.add(obj);
		}
		return objects;
	}

	private ApiMethod searchMasterKey = new ApiMethod("search_master_key", true) {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			Date begin = params.getDate("begin");
			Date end = params.getDate("end");
			ResultPage<MasterKeyVo> rp = m_MeshManage.searchMasterKeyVo(begin, end);
			return toPageData(rp, params);
		}
	};

	PageData toPageData(ResultPage<? extends Object> rp, FriendlyObject params) {
		if (rp.getCount() > 0) {
			int page = params.getInt("page", 1);
			int pageSize = params.getInt("page_size", 100);
			rp.setPageSize(pageSize);
			rp.gotoPage(page);
		}
		return PageData.valueOf(rp);
	}

	private ApiMethod searchServiceAccess = new ApiMethod("search_service_access", true) {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			Date begin = params.getDate("begin");
			Date end = params.getDate("end");
			ResultPage<ServiceAccessVo> rp = m_MeshManage.searchServiceAccessVo(begin, end);
			return toPageData(rp, params);
		}
	};

	private ApiMethod searchRightTable = new ApiMethod("search_right_table", true) {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			Date begin = params.getDate("begin");
			Date end = params.getDate("end");
			ResultPage<RightTableVo> rp = m_MeshManage.searchRightTableVo(begin, end);
			return toPageData(rp, params);
		}
	};

	private ApiMethod searchTrafficTable = new ApiMethod("search_traffic_table", true) {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			Date begin = params.getDate("begin");
			Date end = params.getDate("end");
			ResultPage<TrafficTableVo> rp = m_MeshManage.searchTrafficTableVo(begin, end);
			return toPageData(rp, params);
		}
	};

}
