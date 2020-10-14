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
package cn.weforward.gateway.ops.access.system;

import cn.weforward.common.ResultPage;
import cn.weforward.gateway.Pluginable;

/**
 * <code>ServiceAccessVo</code>工厂
 * 
 * @author zhangpengji
 *
 */
public interface ServiceAccessVoFactory extends Pluginable {

	// /**
	// * 生成新ID并加上指定前缀
	// * <p>
	// * 新ID不能包含Access的保留字符，如{@linkplain Access#SPEARATOR}
	// *
	// * @param prefix
	// * @return
	// */
	// String genPersistenceId(String prefix);

	/**
	 * 按id获取SystemAccessVo
	 * 
	 * @param id
	 * @return
	 */
	ServiceAccessVo get(String id);

	/**
	 * 置入一个SystemAccessVo
	 * 
	 * @param accessVo
	 */
	void put(ServiceAccessVo accessVo);

	/**
	 * 按id前缀搜索
	 * 
	 * @param prefix
	 * @return
	 */
	ResultPage<ServiceAccessVo> startsWith(String prefix);

	/**
	 * 查找ID为指定前缀的对象
	 * 
	 * @param prefix
	 *            ID前缀
	 * @return 对象结果页
	 */
	ResultPage<String> startsWithOfId(String prefix);

	/**
	 * 注册重载监听器
	 *
	 * @param listener
	 */
	void registerReloadListener(ReloadListener listener);

	/**
	 * 注销重载监听器
	 *
	 * @param listener
	 * @return 注销成功返回true
	 */
	boolean unregisterReloadListener(ReloadListener listener);

	interface ReloadListener {

		void onReload(ServiceAccessVo accessVo);
	}
}
