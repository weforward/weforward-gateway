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

import cn.weforward.common.util.NumberUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.exception.ObjectMappingException;
import cn.weforward.protocol.ext.ObjectMapper;
import cn.weforward.protocol.support.BeanObjectMapper;
import cn.weforward.protocol.support.datatype.SimpleDtObject;

public class GatewayNodeMapper implements ObjectMapper<GatewayNode> {

	public static final GatewayNodeMapper INSTANCE = new GatewayNodeMapper();

	private BeanObjectMapper<GatewayNodeVo> m_Mapper;

	private GatewayNodeMapper() {
		m_Mapper = BeanObjectMapper.getInstance(GatewayNodeVo.class);
	}

	@Override
	public String getName() {
		return GatewayNode.class.getName();
	}

	@Override
	public DtObject toDtObject(GatewayNode node) throws ObjectMappingException {
		GatewayNodeVo vo = GatewayNodeVo.valueOf(node);
		DtObject result = m_Mapper.toDtObject(vo);
		if (Configure.getInstance().isCompatMode()) {
			SimpleDtObject dtObj = (SimpleDtObject) result;
			dtObj.put("host", node.getHostName() + ":" + node.getPort());
		}
		return result;
	}

	@Override
	public GatewayNode fromDtObject(DtObject obj) throws ObjectMappingException {
		GatewayNodeVo vo = m_Mapper.fromDtObject(obj);
		if (StringUtil.isEmpty(vo.hostName)) {
			String host = obj.getString("host").value();
			int idx = host.indexOf(':');
			vo.hostName = host.substring(0, idx);
			vo.port = NumberUtil.toInt(host.substring(idx + 1));
		}
		return new GatewayNodeWrap(vo);
	}
}
