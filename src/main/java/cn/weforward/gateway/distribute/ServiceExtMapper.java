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
import cn.weforward.protocol.datatype.DataType;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.exception.ObjectMappingException;
import cn.weforward.protocol.ext.ObjectMapper;
import cn.weforward.protocol.gateway.vo.ServiceExtVo;
import cn.weforward.protocol.gateway.vo.ServiceExtWrap;
import cn.weforward.protocol.gateway.vo.ServiceVo;
import cn.weforward.protocol.gateway.vo.ServiceWrap;
import cn.weforward.protocol.ops.ServiceExt;
import cn.weforward.protocol.support.BeanObjectMapper;
import cn.weforward.protocol.support.datatype.SimpleDtObject;

public class ServiceExtMapper implements ObjectMapper<ServiceExt> {

	public static final ServiceExtMapper INSTANCE = new ServiceExtMapper();

	private ServiceExtMapper() {

	}

	@Override
	public DtObject toDtObject(ServiceExt service) throws ObjectMappingException {
		ServiceExtVo vo = ServiceExtVo.valueOf(service);
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
			return dtObj;
		} else {
			BeanObjectMapper<ServiceExtVo> mapper = BeanObjectMapper.getInstance(ServiceExtVo.class);
			return mapper.toDtObject(vo);
		}
	}

	@Override
	public String getName() {
		return ServiceExt.class.getName();
	}

	@Override
	public ServiceExt fromDtObject(DtObject obj) throws ObjectMappingException {
		// FIXME
		if (DataType.NUMBER == obj.getAttribute("heartbeat").type()) {
			BeanObjectMapper<ServiceVo> mapper = BeanObjectMapper.getInstance(ServiceVo.class);
			ServiceVo vo = mapper.fromDtObject(obj);
			ServiceExtVo extVo = new ServiceExtVo(new ServiceWrap(vo));
			extVo.owner = obj.getString("owner").value();
			extVo.heartbeat = new Date(obj.getNumber("heartbeat").valueLong());
			return new ServiceExtWrap(extVo);
		} else {
			BeanObjectMapper<ServiceExtVo> mapper = BeanObjectMapper.getInstance(ServiceExtVo.class);
			ServiceExtVo vo = mapper.fromDtObject(obj);
			return new ServiceExtWrap(vo);
		}
	}
}
