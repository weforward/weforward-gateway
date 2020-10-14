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
import cn.weforward.gateway.Pluginable;
import cn.weforward.protocol.ops.AccessExt;

/**
 * Access的提供者
 * 
 * @author zhangpengji
 *
 */
public interface AccessProvider extends Pluginable {

	/**
	 * 支持Access的类型
	 * 
	 * @return
	 */
	String getKind();

	/**
	 * 创建一个Access
	 * 
	 * @param groupId
	 *            Access所属组
	 * @return
	 */
	AccessExt createAccess(String groupId);

	/**
	 * 按AccessId获取Access
	 * 
	 * @param id
	 * @return
	 */
	AccessExt getAccess(String id);

	/**
	 * 列举Access
	 * 
	 * @param groupId
	 *            Access所属组，可空
	 * @param keyword
	 *            关键字，可空
	 * @return
	 */
	ResultPage<AccessExt> listAccess(String groupId, String keyword);

	/**
	 * 列举Access组
	 * 
	 * @return
	 */
	List<String> listAccessGroup();
}
