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

import cn.weforward.protocol.Header;
import cn.weforward.protocol.aio.ServerContext;
import cn.weforward.protocol.client.ext.ResponseResultObject;
import cn.weforward.protocol.exception.WeforwardException;
import cn.weforward.protocol.support.CommonServiceCodes;
import cn.weforward.protocol.support.datatype.FriendlyObject;

/**
 * Api的方法
 * 
 * @author zhangpengji
 *
 */
abstract class ApiMethod {

	String m_Name;
	boolean m_WithoutParams;

	ApiMethod(String name) {
		this(name, false);
	}

	ApiMethod(String name, boolean withoutParams) {
		m_Name = name;
		m_WithoutParams = withoutParams;
	}

	String getName() {
		return m_Name;
	}

	Object executeMethod(Header header, FriendlyObject params, ServerContext context)
			throws ApiException, WeforwardException {
		if (!m_WithoutParams && null == params) {
			return ResponseResultObject.error(CommonServiceCodes.ILLEGAL_ARGUMENT, "params不能为空");
		}
		return execute(header, params, context);
	}

	abstract Object execute(Header header, FriendlyObject params) throws ApiException, WeforwardException;

	Object execute(Header header, FriendlyObject params, ServerContext context)
			throws ApiException, WeforwardException {
		// 子类重载
		return execute(header, params);
	}
}
