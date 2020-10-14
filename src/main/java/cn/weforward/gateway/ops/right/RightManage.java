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
package cn.weforward.gateway.ops.right;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.gateway.Tunnel;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.exception.AuthException;
import cn.weforward.protocol.ops.secure.RightTable;

/**
 * 微服务权限管理
 * 
 * @author zhangpengji
 *
 */
public interface RightManage {
	static final Logger _Logger = LoggerFactory.getLogger(RightManage.class);

	/**
	 * 验证访问者身份是否被允许
	 * 
	 * @param header
	 *            请求头
	 * @throws AuthException
	 */
	void verifyAccess(Header header) throws AuthException;

	/**
	 * 验证访问者身份是否被允许
	 * 
	 * @param tunnel
	 *            请求管道
	 * @throws AuthException
	 */
	void verifyAccess(Tunnel tunnel) throws AuthException;

	/**
	 * 打开服务的调用规则表
	 * 
	 * @param serviceName
	 * @return
	 */
	RightTable openRightTable(String serviceName);

	/**
	 * 获取服务对应的规则表
	 * 
	 * @param serviceName
	 * @return
	 */
	RightTable getRightTable(String serviceName);
}
