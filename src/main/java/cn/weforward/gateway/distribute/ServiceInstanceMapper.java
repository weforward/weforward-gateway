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

import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.mesh.MeshNodeWrap;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.exception.ObjectMappingException;
import cn.weforward.protocol.ext.ObjectMapper;
import cn.weforward.protocol.gateway.vo.ServiceExtWrap;
import cn.weforward.protocol.support.BeanObjectMapper;

public class ServiceInstanceMapper implements ObjectMapper<ServiceInstance> {

	public static final ServiceInstanceMapper INSTANCE = new ServiceInstanceMapper();

	private ServiceInstanceMapper() {

	}

	@Override
	public DtObject toDtObject(ServiceInstance service) throws ObjectMappingException {
		ServiceInstanceVo vo = ServiceInstanceVo.valueOf(service);
		BeanObjectMapper<ServiceInstanceVo> mapper = BeanObjectMapper.getInstance(ServiceInstanceVo.class);
		return mapper.toDtObject(vo);
	}

	@Override
	public String getName() {
		return ServiceInstance.class.getName();
	}

	@Override
	public ServiceInstance fromDtObject(DtObject obj) throws ObjectMappingException {
		BeanObjectMapper<ServiceInstanceVo> mapper = BeanObjectMapper.getInstance(ServiceInstanceVo.class);
		ServiceInstanceVo vo = mapper.fromDtObject(obj);
		ServiceInstance serviceInstance = new ServiceInstance(new ServiceExtWrap(vo));
		if (null != vo.meshNode) {
			serviceInstance.setMeshNode(new MeshNodeWrap(vo.meshNode));
		}
		return serviceInstance;
	}
}
