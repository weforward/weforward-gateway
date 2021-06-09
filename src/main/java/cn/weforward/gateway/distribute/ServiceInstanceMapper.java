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
package cn.weforward.gateway.distribute;

import java.util.Date;

import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.mesh.MeshNodeVo;
import cn.weforward.gateway.mesh.MeshNodeWrap;
import cn.weforward.protocol.datatype.DataType;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.exception.ObjectMappingException;
import cn.weforward.protocol.ext.ObjectMapper;
import cn.weforward.protocol.gateway.vo.ServiceExtVo;
import cn.weforward.protocol.gateway.vo.ServiceExtWrap;
import cn.weforward.protocol.gateway.vo.ServiceVo;
import cn.weforward.protocol.gateway.vo.ServiceWrap;
import cn.weforward.protocol.support.BeanObjectMapper;
import cn.weforward.protocol.support.datatype.SimpleDtObject;

public class ServiceInstanceMapper implements ObjectMapper<ServiceInstance> {

	public static final ServiceInstanceMapper INSTANCE = new ServiceInstanceMapper();

	private ServiceInstanceMapper() {

	}

	@Override
	public DtObject toDtObject(ServiceInstance service) throws ObjectMappingException {
		ServiceInstanceVo vo = ServiceInstanceVo.valueOf(service);
		if (Configure.getInstance().isCompatMode()) {
			BeanObjectMapper<ServiceVo> mapper = BeanObjectMapper.getInstance(ServiceVo.class);
			SimpleDtObject dtObj = (SimpleDtObject) mapper.toDtObject(vo);
			if (StringUtil.isEmpty(vo.domain)) {
				// 从url中获取ip，端口
				String url = vo.urls.get(0);
				int bi = url.indexOf("://");
				int ei = url.lastIndexOf("/");
				int si = url.indexOf(":", bi + 3);
				dtObj.put("domain", url.substring(bi + 3, si));
				dtObj.put("port", Integer.parseInt(url.substring(si + 1, ei)));
			}
			dtObj.put("owner", vo.owner);
			dtObj.put("state", vo.state);
			dtObj.put("heartbeat", (null == vo.heartbeat ? 0 : vo.heartbeat.getTime()));
			MeshNodeVo meshNode = vo.getMeshNode();
			if(null != meshNode) {
				BeanObjectMapper<MeshNodeVo> meshNodeMapper = BeanObjectMapper.getInstance(MeshNodeVo.class);
				SimpleDtObject meshNodeDtObj = (SimpleDtObject) meshNodeMapper.toDtObject(meshNode);
				dtObj.put("mesh_node", meshNodeDtObj);
			}
			return dtObj;
		} else {
			BeanObjectMapper<ServiceInstanceVo> mapper = BeanObjectMapper.getInstance(ServiceInstanceVo.class);
			return mapper.toDtObject(vo);
		}
	}

	@Override
	public String getName() {
		return ServiceInstance.class.getName();
	}

	@Override
	public ServiceInstance fromDtObject(DtObject obj) throws ObjectMappingException {
		if (DataType.NUMBER == obj.getAttribute("heartbeat").type()) {
			// FIXME 暂时通过判断heartbeat来识别旧网关和兼容启用兼容模式的新网关
			BeanObjectMapper<ServiceVo> mapper = BeanObjectMapper.getInstance(ServiceVo.class);
			ServiceVo vo = mapper.fromDtObject(obj);
			ServiceExtVo extVo = new ServiceExtVo(new ServiceWrap(vo));
			extVo.owner = obj.getString("owner").value();
			extVo.heartbeat = new Date(obj.getNumber("heartbeat").valueLong());
			ServiceExtWrap wrap = new ServiceExtWrap(extVo);
			ServiceInstance serviceInstance = new ServiceInstance(wrap);
			DtObject meshNodeDtObj = obj.getObject("mesh_node");
			if(null != meshNodeDtObj) {
				BeanObjectMapper<MeshNodeVo> meshNodeMapper = BeanObjectMapper.getInstance(MeshNodeVo.class);
				MeshNodeVo meshNodeVo = meshNodeMapper.fromDtObject(meshNodeDtObj);
				serviceInstance.setMeshNode(new MeshNodeWrap(meshNodeVo));
			}
			return serviceInstance;
		} else {
			BeanObjectMapper<ServiceInstanceVo> mapper = BeanObjectMapper.getInstance(ServiceInstanceVo.class);
			ServiceInstanceVo vo = mapper.fromDtObject(obj);
			ServiceInstance serviceInstance = new ServiceInstance(new ServiceExtWrap(vo));
			if(null != vo.meshNode) {
				serviceInstance.setMeshNode(new MeshNodeWrap(vo.meshNode));
			}
			return serviceInstance;
		}
	}
}
