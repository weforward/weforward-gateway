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
package cn.weforward.gateway.ops.access;

import java.util.List;

import cn.weforward.common.ResultPage;
import cn.weforward.gateway.AccessLoaderExt;
import cn.weforward.protocol.ops.AccessExt;

/**
 * 访问许可管理
 * 
 * @author zhangpengji
 *
 */
public interface AccessManage extends AccessLoaderExt {

	/**
	 * 注册AccessProvider
	 * 
	 * @param provider
	 * @return 已注册，且相同kind的Provider
	 */
	AccessProvider registerProvider(AccessProvider provider);

	/**
	 * 注销AccessProvider
	 * 
	 * @param provider
	 * @return
	 */
	boolean unregisterProvider(AccessProvider provider);

	/**
	 * 创建一个访问许可
	 * 
	 * @param kind
	 *            分类
	 * @param groupId
	 *            组（如：商家）的id
	 * @return
	 */
	AccessExt createAccess(String kind, String groupId);

	/**
	 * 获取Access
	 * 
	 * @param id
	 *            对象id或accessId
	 * @return
	 */
	AccessExt getAccess(String id);

	/**
	 * 按类型、组、关键字搜索Access
	 * 
	 * @param kind
	 * @param groupId
	 * @param keyword
	 * @return
	 */
	ResultPage<AccessExt> listAccess(String kind, String groupId, String keyword);

	/**
	 * 按类型列举Access组
	 * 
	 * @param kind
	 * @return
	 */
	List<String> listAccessGroup(String kind);
}
