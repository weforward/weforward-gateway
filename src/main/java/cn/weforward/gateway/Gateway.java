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

import cn.weforward.common.ResultPage;
import cn.weforward.protocol.RequestConstants;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.exception.UnReadyException;
import cn.weforward.protocol.ext.ServiceRuntime;
import cn.weforward.protocol.ops.ServiceExt;

/**
 * 网关
 * 
 * @author zhangpengji
 *
 */
public interface Gateway {

	/**
	 * 注册一个微服务
	 * 
	 * @param ownerAccessId
	 *            服务所有者的Access Id
	 * @param info
	 *            服务信息
	 * @param runtime
	 *            服务运行时信息
	 */
	void registerService(String ownerAccessId, Service service, ServiceRuntime runtime);

	/**
	 * 注销一个微服务
	 * 
	 * @param ownerAccessId
	 *            服务所有者的Access Id
	 * @param info
	 *            服务信息
	 */
	void unregisterService(String ownerAccessId, Service service);

	/**
	 * 对接微服务端
	 * <p>
	 * 需确保tunnel已读取完{@linkplain RequestConstants#WF_REQ}节点数据
	 * 
	 * @param tunnel
	 * @return
	 * @throws UnReadyException
	 */
	void joint(Tunnel tunnel);

	/**
	 * 对接微服务端
	 * 
	 * @param tunnel
	 */
	void joint(StreamTunnel tunnel);

	/**
	 * 列举微服务名称
	 * 
	 * @return
	 */
	ResultPage<String> listServiceName(String keyword);

	/**
	 * 列举微服务（实例）。
	 * <p>
	 * 支持按名称模糊搜索
	 * 
	 * @param name
	 *            服务名称，可空
	 * @return
	 */
	ResultPage<ServiceExt> listService(String name);

	/**
	 * 查询微服务（实例）
	 * 
	 * @param keyword
	 * @param runningId
	 * @return
	 */
	ResultPage<ServiceExt> searchService(String keyword, String runningId);
}
