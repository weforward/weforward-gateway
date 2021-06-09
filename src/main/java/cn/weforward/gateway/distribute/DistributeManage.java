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

import java.util.List;

import cn.weforward.common.ResultPage;
import cn.weforward.gateway.ServiceInstance;

/**
 * 网关分布管理
 * 
 * @author zhangpengji
 *
 */
public interface DistributeManage {

	/**
	 * 同步兄弟节点的资源
	 * 
	 * @param nodes
	 *            兄弟节点所知的所有节点
	 * @param regServices
	 *            注册到兄弟节点的微服务
	 * @param unregServices
	 *            从兄弟节点注销的微服务
	 * @return
	 */
	void syncFromBrother(List<GatewayNode> nodes, List<ServiceInstance> regServices, List<ServiceInstance> unregServices);

	/**
	 * 获取本节点的全部微服务
	 * 
	 * @return
	 */
	ResultPage<ServiceInstance> getServices();
}
