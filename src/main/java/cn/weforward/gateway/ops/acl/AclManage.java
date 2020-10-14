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
package cn.weforward.gateway.ops.acl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.protocol.Access;
import cn.weforward.protocol.ops.secure.AclTable;

/**
 * Acl管理
 * 
 * @author zhangpengji
 *
 */
public interface AclManage {
	static final Logger _Logger = LoggerFactory.getLogger(AclManage.class);

	/**
	 * 打开服务的ACL表
	 * 
	 * @param serviceName
	 * @return
	 */
	AclTable openAclTable(String serviceName);

	/**
	 * 获取服务对应的ACL表
	 * 
	 * @param serviceName
	 * @return
	 */
	AclTable getAclTableByName(String serviceName);

	/**
	 * 查找access对此资源的权限。未匹配到权限时，返回0
	 * 
	 * @param access
	 * @param resId
	 * @return
	 */
	int findResourceRight(String serviceName, Access access, String resId);
}
