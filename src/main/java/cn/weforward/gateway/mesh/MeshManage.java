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
package cn.weforward.gateway.mesh;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.ResultPage;
import cn.weforward.gateway.MeshNode;
import cn.weforward.gateway.ops.access.system.MasterKeyVo;
import cn.weforward.gateway.ops.access.system.ServiceAccessVo;
import cn.weforward.gateway.ops.right.RightTableVo;
import cn.weforward.gateway.ops.traffic.TrafficTableVo;

/**
 * 网关网格化管理
 * 
 * @author zhangpengji
 *
 */
public interface MeshManage {
	static final Logger _Logger = LoggerFactory.getLogger(MeshManage.class);

	/**
	 * 同步其他节点的资源
	 * 
	 * @param nodes
	 *            兄弟节点所知的所有节点
	 * @param regServices
	 *            注册到兄弟节点的微服务
	 * @param unregServices
	 *            从兄弟节点注销的微服务
	 * @return
	 */
	void syncFromBrother(List<MeshNode> nodes, List<MeshService> regServices, List<MeshService> unregServices,
			List<Object> updatedObjects);

	ResultPage<MasterKeyVo> searchMasterKeyVo(Date begin, Date end);

	ResultPage<ServiceAccessVo> searchServiceAccessVo(Date begin, Date end);

	ResultPage<RightTableVo> searchRightTableVo(Date begin, Date end);

	ResultPage<TrafficTableVo> searchTrafficTableVo(Date begin, Date end);
}
