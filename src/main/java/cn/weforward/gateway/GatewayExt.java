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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.ResultPage;
import cn.weforward.gateway.exception.DebugServiceException;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.doc.ServiceDocument;
import cn.weforward.protocol.exception.WeforwardException;
import cn.weforward.protocol.gateway.ServiceSummary;

/**
 * 网关扩展接口
 * 
 * @author zhangpengji
 *
 */
public interface GatewayExt extends Gateway {
	static final Logger _Logger = LoggerFactory.getLogger(GatewayExt.class);

	/**
	 * 同步来自其他节点的微服务
	 * 
	 * @param reg
	 *            注册的
	 * @param unreg
	 *            注销的
	 * @param complete
	 *            此次同步的微服务是否完整
	 */
	void syncServices(List<ServiceInstance> reg, List<ServiceInstance> unreg, boolean complete);

	/**
	 * 同步来自其他网格的微服务
	 * 
	 * @param reg
	 *            注册的
	 * @param unreg
	 *            注销的
	 */
	void syncServices(MeshNode meshNode, List<ServiceInstance> reg, List<ServiceInstance> unreg);

	/**
	 * 添加一个监听器
	 * 
	 * @param listener
	 */
	void addListener(ServiceListener listener);

	/**
	 * 移除一个监听器
	 * 
	 * @param listener
	 */
	void removeListener(ServiceListener listener);

	/**
	 * 获取服务的文档
	 * 
	 * @param serviceName
	 * @return 多个版本的文档。为null时表示服务不存在
	 */
	List<ServiceDocument> getDocuments(String serviceName);

	/**
	 * 网关是否已就绪
	 * 
	 * @return
	 */
	boolean isReady();

	/**
	 * 服务调试（执行脚本代码）
	 * 
	 * @param serviceName
	 *            微服务名
	 * @param serviceNo
	 *            微服务编号
	 * @param scriptSource
	 *            脚本源代码
	 * @param scriptName
	 *            脚本类名
	 * @param scriptArgs
	 *            脚本参数，表单格式（application/x-www-form-urlencoded）
	 * @return
	 * @throws WeforwardException
	 */
	DtObject debugService(String serviceName, String serviceNo, String scriptSource, String scriptName,
			String scriptArgs) throws DebugServiceException;

	/**
	 * 列举已注册的微服务
	 * 
	 * @param keyword
	 *            名称关键字，支持通配符'*'，如：*_order,*.pay,us*er
	 * @param accessGroup 微服务所属的access group
	 * @return 微服务概要信息
	 */
	ResultPage<ServiceSummary> listServiceSummary(String keyword, String accessGroup);
	
	/**
	 * 列举有效的微服务实例
	 * 
	 * @param name
	 *            微服务名称
	 * @return
	 */
	List<ServiceInstance> listValidService(String name);
	
	/**
	 * 是否存在此名称的微服务
	 * 
	 * @param name 微服务名
	 * @param accessGroup 若指定access group，则微服务必须归属于其中
	 * @return
	 */
	boolean isExistService(String name, String accessGroup);
}
