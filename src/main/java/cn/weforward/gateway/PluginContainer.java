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
package cn.weforward.gateway;

import java.util.List;

/**
 * 插件的容器
 * 
 * @author zhangpengji
 *
 */
public interface PluginContainer {

	/**
	 * 添加一个插件
	 * 
	 * @param plugin
	 */
	void add(Pluginable plugin);

	/**
	 * 移除一个插件
	 * 
	 * @param plugin
	 */
	void remove(Pluginable plugin);

	/**
	 * 注册一个监听器
	 * 
	 * @param listener
	 */
	void register(PluginListener listener);

	/**
	 * 注销一个监听器
	 * 
	 * @param listener
	 * @return
	 */
	boolean unregister(PluginListener listener);

	/**
	 * 获取与指定clazz有继承关系的插件
	 * 
	 * @param <E>
	 * @param clazz
	 * @return
	 */
	<E extends Pluginable> List<E> getPlugin(Class<E> clazz);
}
