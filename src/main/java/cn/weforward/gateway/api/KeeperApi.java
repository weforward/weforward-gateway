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

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

import cn.weforward.common.ResultPage;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.TransList;
import cn.weforward.common.util.TransResultPage;
import cn.weforward.gateway.GatewayExt;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.exception.DebugServiceException;
import cn.weforward.gateway.ops.access.AccessManage;
import cn.weforward.gateway.ops.right.RightManage;
import cn.weforward.gateway.ops.traffic.TrafficManage;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.client.ext.ResponseResultObject;
import cn.weforward.protocol.doc.ServiceDocument;
import cn.weforward.protocol.gateway.ServiceSummary;
import cn.weforward.protocol.gateway.vo.AccessExtVo;
import cn.weforward.protocol.gateway.vo.RightTableItemVo;
import cn.weforward.protocol.gateway.vo.RightTableItemWrap;
import cn.weforward.protocol.gateway.vo.RightTableVo;
import cn.weforward.protocol.gateway.vo.ServiceExtVo;
import cn.weforward.protocol.gateway.vo.TrafficTableItemVo;
import cn.weforward.protocol.gateway.vo.TrafficTableItemWrap;
import cn.weforward.protocol.gateway.vo.TrafficTableVo;
import cn.weforward.protocol.ops.AccessExt;
import cn.weforward.protocol.ops.secure.RightTable;
import cn.weforward.protocol.ops.secure.RightTableItem;
import cn.weforward.protocol.ops.traffic.TrafficTable;
import cn.weforward.protocol.ops.traffic.TrafficTableItem;
import cn.weforward.protocol.support.BeanObjectMapper;
import cn.weforward.protocol.support.CommonServiceCodes;
import cn.weforward.protocol.support.PageData;
import cn.weforward.protocol.support.PageDataMapper;
import cn.weforward.protocol.support.SimpleObjectMapperSet;
import cn.weforward.protocol.support.datatype.FriendlyObject;
import cn.weforward.protocol.support.doc.ServiceDocumentVo;

/**
 * Keeper Api
 * 
 * @author zhangpengji
 *
 */
class KeeperApi extends AbstractGatewayApi {

	SimpleObjectMapperSet m_Mappers;
	AccessManage m_AccessManage;
	RightManage m_RightManage;
	TrafficManage m_TrafficManage;
	GatewayExt m_Gateway;

	KeeperApi() {
		super();

		SimpleObjectMapperSet mappers = new SimpleObjectMapperSet();
		mappers.register(BeanObjectMapper.getInstance(AccessExtVo.class));
		mappers.register(BeanObjectMapper.getInstance(ServiceExtVo.class));
		mappers.register(BeanObjectMapper.getInstance(RightTableVo.class));
		mappers.register(BeanObjectMapper.getInstance(RightTableItemVo.class));
		mappers.register(BeanObjectMapper.getInstance(TrafficTableVo.class));
		mappers.register(BeanObjectMapper.getInstance(TrafficTableItemVo.class));
		mappers.register(BeanObjectMapper.getInstance(ServiceSummary.class));
		PageDataMapper pageDataMapper = new PageDataMapper(mappers);
		mappers.register(pageDataMapper);
		m_Mappers = mappers;

		register(listAccess);
		register(listAccessGroup);
		register(createAccess);
		register(updateAccess);
		register(getAccess);

		register(listService);
		register(listServiceName);
		register(listServiceSummary);
		register(searchService);
		register(isExistService);

		register(getRightTable);
		register(appendRightRule);
		register(insertRightRule);
		register(moveRightRule);
		register(replaceRightRule);
		register(removeRightRule);
		register(setRightRules);

		register(getTrafficTable);
		register(appendeTrafficRule);
		register(insertTrafficRule);
		register(moveTrafficRule);
		register(replaceTrafficRule);
		register(removeTrafficRule);
		register(setTrafficRules);

		register(getDocuments);
		register(debugService);
	}

	@Override
	SimpleObjectMapperSet getMappers() {
		return m_Mappers;
	}

	public void setAccessManage(AccessManage am) {
		m_AccessManage = am;
	}

	public void setGateway(GatewayExt gw) {
		m_Gateway = gw;
	}

	public void setRightManage(RightManage rm) {
		m_RightManage = rm;
	}

	public void setTrafficManage(TrafficManage tm) {
		m_TrafficManage = tm;
	}

	@Override
	public String getName() {
		return ServiceName.KEEPER.name;
	}

	private ApiMethod listAccess = new ApiMethod("list_access") {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String kind = params.getString("kind");
			String groupId = params.getString("group");
			String keyword = params.getString("keyword");
			int page = params.getInt("page", 1);
			int pageSize = params.getInt("page_size", 50);
			ResultPage<AccessExtVo> vos = ResultPageHelper.empty();
			ResultPage<AccessExt> rp = m_AccessManage.listAccess(kind, groupId, keyword);
			if (rp.getCount() > 0) {
				vos = new TransResultPage<AccessExtVo, AccessExt>(rp) {

					@Override
					protected AccessExtVo trans(AccessExt src) {
						return new AccessExtVo(src);
					}
				};
				vos.setPageSize(pageSize);
				vos.gotoPage(page);
			}
			return new PageData(vos);
		}
	};

	private ApiMethod listAccessGroup = new ApiMethod("list_access_group") {

		@Override
		List<String> execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String kind = params.getString("kind");
			return m_AccessManage.listAccessGroup(kind);
		}
	};

	private ApiMethod createAccess = new ApiMethod("create_access") {

		@Override
		AccessExtVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String kind = params.getString("kind");
			String group = params.getString("group");
			String summary = params.getString("summary");
			AccessExt access = m_AccessManage.createAccess(kind, group);
			access.setSummary(summary);
			return new AccessExtVo(access);
		}
	};

	private ApiMethod getAccess = new ApiMethod("get_access") {

		@Override
		AccessExtVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String id = params.getString("id");
			AccessExt access = m_AccessManage.getAccess(id);
			return AccessExtVo.valueOf(access);
		}
	};

	private ApiMethod updateAccess = new ApiMethod("update_access") {

		@Override
		AccessExtVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String id = params.getString("id");
			AccessExt access = m_AccessManage.getAccess(id);
			if (null == access) {
				throw new ApiException(CommonServiceCodes.ILLEGAL_ARGUMENT.code, "找不到Access:" + id);
			}
			String summary = params.getString("summary");
			if (!StringUtil.isEmpty(summary)) {
				access.setSummary(summary);
			}
			String valid = params.getString("valid");
			if (null != valid) {
				access.setValid(Boolean.valueOf(valid));
			}
			return new AccessExtVo(access);
		}
	};

	private ApiMethod listServiceName = new ApiMethod("list_service_name", true) {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String keyword = params.getString("keyword");
			if (!StringUtil.isEmpty(keyword)) {
				if ('*' != keyword.charAt(0)) {
					keyword = '*' + keyword;
				}
				if ('*' != keyword.charAt(keyword.length() - 1)) {
					keyword = keyword + '*';
				}
			}
			String accessGroup = params.getString("access_group");
			int page = params.getInt("page", 1);
			int pageSize = params.getInt("page_size", 50);
			ResultPage<String> names = m_Gateway.listServiceName(keyword, accessGroup);
			if (names.getCount() > 0) {
				names.setPageSize(pageSize);
				names.gotoPage(page);
			}
			return new PageData(names);
		}
	};

	private ApiMethod listServiceSummary = new ApiMethod("list_service_summary", true) {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String keyword = params.getString("keyword");
			if (!StringUtil.isEmpty(keyword)) {
				if ('*' != keyword.charAt(0)) {
					keyword = '*' + keyword;
				}
				if ('*' != keyword.charAt(keyword.length() - 1)) {
					keyword = keyword + '*';
				}
			}
			String accessGroup = params.getString("access_group");
			int page = params.getInt("page", 1);
			int pageSize = params.getInt("page_size", 50);
			ResultPage<ServiceSummary> summarys = m_Gateway.listServiceSummary(keyword, accessGroup);
			if (summarys.getCount() > 0) {
				summarys.setPageSize(pageSize);
				summarys.gotoPage(page);
			}
			return new PageData(summarys);
		}
	};

	private ApiMethod listService = new ApiMethod("list_service") {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			int page = params.getInt("page", 1);
			int pageSize = params.getInt("page_size", 50);
			String accessGroup = params.getString("access_group");
			ResultPage<ServiceExtVo> vos = ResultPageHelper.empty();
			ResultPage<ServiceInstance> rp = m_Gateway.listService(name, accessGroup);
			if (rp.getCount() > 0) {
				vos = new TransResultPage<ServiceExtVo, ServiceInstance>(rp) {

					@Override
					protected ServiceExtVo trans(ServiceInstance src) {
						return new ServiceExtVo(src);
					}
				};
				vos.setPageSize(pageSize);
				vos.gotoPage(page);
			}
			return new PageData(vos);
		}
	};

	private ApiMethod searchService = new ApiMethod("search_service") {

		@Override
		PageData execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String keyword = params.getString("keyword");
			String runningId = params.getString("running_id");
			int page = params.getInt("page", 1);
			int pageSize = params.getInt("page_size", 50);
			ResultPage<ServiceExtVo> vos = ResultPageHelper.empty();
			ResultPage<ServiceInstance> rp = m_Gateway.searchService(keyword, runningId);
			if (rp.getCount() > 0) {
				vos = new TransResultPage<ServiceExtVo, ServiceInstance>(rp) {

					@Override
					protected ServiceExtVo trans(ServiceInstance src) {
						return new ServiceExtVo(src);
					}
				};
				vos.setPageSize(pageSize);
				vos.gotoPage(page);
			}
			return new PageData(vos);
		}
	};

	private ApiMethod getRightTable = new ApiMethod("get_right_table") {

		@Override
		RightTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			RightTable table = m_RightManage.getRightTable(name);
			if (null == table) {
				return null;
			}
			return new RightTableVo(table);
		}
	};

	private ApiMethod appendRightRule = new ApiMethod("append_right_rule") {

		@Override
		RightTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			RightTableItemVo item = params.getObject("item", RightTableItemVo.class, m_Mappers);
			RightTable table = m_RightManage.openRightTable(name);
			table.appendItem(new RightTableItemWrap(item));
			return new RightTableVo(table);
		}
	};

	private ApiMethod insertRightRule = new ApiMethod("insert_right_rule") {

		@Override
		RightTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			RightTableItemVo vo = params.getObject("item", RightTableItemVo.class, m_Mappers);
			int idx = params.getInt("index", -1);
			RightTable table = m_RightManage.openRightTable(name);
			RightTableItem item = new RightTableItemWrap(vo);
			if (idx >= 0) {
				table.insertItem(item, idx);
			} else {
				table.appendItem(item);
			}
			return new RightTableVo(table);
		}
	};

	private ApiMethod replaceRightRule = new ApiMethod("replace_right_rule") {

		@Override
		RightTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			RightTableItemVo item = params.getObject("item", RightTableItemVo.class, m_Mappers);
			int index = params.getInt("index", -1);
			String replaceName = params.getString("replace_name");
			RightTable table = m_RightManage.openRightTable(name);
			table.replaceItem(new RightTableItemWrap(item), index, replaceName);
			return new RightTableVo(table);
		}
	};

	private ApiMethod removeRightRule = new ApiMethod("remove_right_rule") {

		@Override
		RightTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			int index = params.getInt("index", -1);
			String removeName = params.getString("remove_name");
			RightTable table = m_RightManage.openRightTable(name);
			table.removeItem(index, removeName);
			return new RightTableVo(table);
		}
	};

	private ApiMethod moveRightRule = new ApiMethod("move_right_rule") {

		@Override
		RightTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			int from = params.getInt("from", -1);
			int to = params.getInt("to", -1);
			RightTable table = m_RightManage.openRightTable(name);
			table.moveItem(from, to);
			return new RightTableVo(table);
		}
	};

	private ApiMethod setRightRules = new ApiMethod("set_right_rules") {

		@Override
		RightTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			List<RightTableItemVo> vos = params.getList("items", RightTableItemVo.class, m_Mappers);
			RightTable table = m_RightManage.openRightTable(name);
			List<RightTableItem> items = new TransList<RightTableItem, RightTableItemVo>(vos) {

				@Override
				protected RightTableItem trans(RightTableItemVo src) {
					return new RightTableItemWrap(src);
				}
			};
			table.setItems(items);
			return new RightTableVo(table);
		}
	};

	private ApiMethod getTrafficTable = new ApiMethod("get_traffic_table") {

		@Override
		TrafficTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			TrafficTable table = m_TrafficManage.getTrafficTable(name);
			if (null == table) {
				return null;
			}
			return new TrafficTableVo(table);
		}
	};

	private ApiMethod appendeTrafficRule = new ApiMethod("append_traffic_rule") {

		@Override
		TrafficTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			TrafficTableItemVo item = params.getObject("item", TrafficTableItemVo.class, m_Mappers);
			TrafficTable table = m_TrafficManage.openTrafficTable(name);
			table.appendItem(new TrafficTableItemWrap(item));
			return new TrafficTableVo(table);
		}
	};

	private ApiMethod insertTrafficRule = new ApiMethod("insert_traffic_rule") {

		@Override
		TrafficTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			TrafficTableItemVo vo = params.getObject("item", TrafficTableItemVo.class, m_Mappers);
			int idx = params.getInt("index", -1);
			TrafficTable table = m_TrafficManage.openTrafficTable(name);
			TrafficTableItem item = new TrafficTableItemWrap(vo);
			if (idx >= 0) {
				table.insertItem(item, idx);
			} else {
				table.appendItem(item);
			}
			return new TrafficTableVo(table);
		}
	};

	private ApiMethod replaceTrafficRule = new ApiMethod("replace_traffic_rule") {

		@Override
		TrafficTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			TrafficTableItemVo item = params.getObject("item", TrafficTableItemVo.class, m_Mappers);
			int index = params.getInt("index", -1);
			String replaceName = params.getString("replace_name");
			TrafficTable table = m_TrafficManage.openTrafficTable(name);
			table.replaceItem(new TrafficTableItemWrap(item), index, replaceName);
			return new TrafficTableVo(table);
		}
	};

	private ApiMethod removeTrafficRule = new ApiMethod("remove_traffic_rule") {

		@Override
		TrafficTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			int index = params.getInt("index", -1);
			String removeName = params.getString("remove_name");
			TrafficTable table = m_TrafficManage.openTrafficTable(name);
			table.removeItem(index, removeName);
			return new TrafficTableVo(table);
		}
	};

	private ApiMethod moveTrafficRule = new ApiMethod("move_traffic_rule") {

		@Override
		TrafficTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			int from = params.getInt("from", -1);
			int to = params.getInt("to", -1);
			TrafficTable table = m_TrafficManage.openTrafficTable(name);
			table.moveItem(from, to);
			return new TrafficTableVo(table);
		}
	};

	private ApiMethod setTrafficRules = new ApiMethod("set_traffic_rules") {

		@Override
		TrafficTableVo execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			List<TrafficTableItemVo> vos = params.getList("items", TrafficTableItemVo.class, m_Mappers);
			TrafficTable table = m_TrafficManage.openTrafficTable(name);
			List<TrafficTableItem> items = new TransList<TrafficTableItem, TrafficTableItemVo>(vos) {

				@Override
				protected TrafficTableItem trans(TrafficTableItemVo src) {
					return new TrafficTableItemWrap(src);
				}
			};
			table.setItems(items);
			return new TrafficTableVo(table);
		}
	};

	private ApiMethod getDocuments = new ApiMethod("get_documents") {

		@Override
		ResponseResultObject execute(Header reqHeader, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			final List<ServiceDocument> docs = m_Gateway.getDocuments(name);
			List<ServiceDocumentVo> vos = Collections.emptyList();
			if (docs.size() > 0) {
				vos = new AbstractList<ServiceDocumentVo>() {

					@Override
					public ServiceDocumentVo get(int index) {
						return ServiceDocumentVo.valueOf(docs.get(index));
					}

					@Override
					public int size() {
						return docs.size();
					}
				};
			}
			return ResponseResultObject.success(vos);
		}
	};

	private ApiMethod debugService = new ApiMethod("debug_service") {

		@Override
		Object execute(Header header, FriendlyObject params) throws ApiException {
			String serviceName = params.getString("service_name");
			String serviceNo = params.getString("service_no");
			String source = params.getString("script-source");
			String name = params.getString("script-name");
			String args = params.getString("script-args");
			try {
				return m_Gateway.debugService(serviceName, serviceNo, source, name, args);
			} catch (DebugServiceException e) {
				_Logger.warn(e.toString(), e);
				throw new ApiException(CommonServiceCodes.INTERNAL_ERROR.code, e.getMessage());
			}
		}
	};
	
	private ApiMethod isExistService = new ApiMethod("is_exist_service") {

		@Override
		Boolean execute(Header header, FriendlyObject params) throws ApiException {
			String name = params.getString("name");
			String accessGroup = params.getString("access_group");
			return m_Gateway.isExistService(name, accessGroup);
		}
	};

	// ApiMethod getAclTable = new ApiMethod("get_acl_table") {
	//
	// @Override
	// ResponseResultObject execute(Header reqHeader, FriendlyObject params) throws
	// ApiException {
	// String name = params.getString("name");
	// AclTable table = m_RightManage.getAclTableByName(name);
	// if (null == table) {
	// return ResponseResultObject.success(null);
	// }
	// return ResponseResultObject.success(new AclTableInfo(table));
	// }
	// };
	//
	// ApiMethod appendeAclRule = new ApiMethod("append_acl_rule") {
	//
	// @Override
	// ResponseResultObject execute(Header reqHeader, FriendlyObject params) throws
	// ApiException {
	// String name = params.getString("name");
	// AclTable.AccessRuleItem item = params.getObject("item",
	// AclTable.AccessRuleItem.class, m_Mappers);
	// AclTable table = m_RightManage.openAclTable(name);
	// table.appendItem(item);
	// return ResponseResultObject.success(new AclTableInfo(table));
	// }
	// };
	//
	// ApiMethod replaceAclRule = new ApiMethod("replace_acl_rule") {
	//
	// @Override
	// ResponseResultObject execute(Header reqHeader, FriendlyObject params) throws
	// ApiException {
	// String name = params.getString("name");
	// AclTable.AccessRuleItem item = params.getObject("item",
	// AclTable.AccessRuleItem.class, m_Mappers);
	// int index = params.getInt("index", -1);
	// String replaceName = params.getString("replace_name");
	// AclTable table = m_RightManage.openAclTable(name);
	// table.replaceItem(item, index, replaceName);
	// return ResponseResultObject.success(new AclTableInfo(table));
	// }
	// };
	//
	// ApiMethod removeAclRule = new ApiMethod("remove_acl_rule") {
	//
	// @Override
	// ResponseResultObject execute(Header reqHeader, FriendlyObject params) throws
	// ApiException {
	// String name = params.getString("name");
	// int index = params.getInt("index", -1);
	// String removeName = params.getString("remove_name");
	// AclTable table = m_RightManage.openAclTable(name);
	// table.removeItem(index, removeName);
	// return ResponseResultObject.success(new AclTableInfo(table));
	// }
	// };
	//
	// ApiMethod moveAclRule = new ApiMethod("move_acl_rule") {
	//
	// @Override
	// ResponseResultObject execute(Header reqHeader, FriendlyObject params) throws
	// ApiException {
	// String name = params.getString("name");
	// int from = params.getInt("from", -1);
	// int to = params.getInt("to", -1);
	// AclTable table = m_RightManage.openAclTable(name);
	// table.moveItem(from, to);
	// return ResponseResultObject.success(new AclTableInfo(table));
	// }
	// };
}
