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
 * 网关节点
 * 
 * @author zhangpengji
 *
 */
public interface GatewayNode {

	/**
	 * 节点标识（服务器id）
	 * 
	 * @return
	 */
	String getId();

	/**
	 * 节点的域名（ip地址）
	 * 
	 * @return
	 */
	String getHostName();

	/**
	 * 节点的端口
	 * 
	 * @return
	 */
	int getPort();

	/**
	 * 节点入口链接
	 * 
	 * @return
	 */
	List<String> getUrls();

	/**
	 * 节点是否为当前网关
	 * 
	 * @return
	 */
	boolean isSelf();
}
