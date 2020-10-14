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
package cn.weforward.gateway.api;

import cn.weforward.gateway.GatewayExt;
import cn.weforward.gateway.distribute.DistributeManage;
import cn.weforward.gateway.ops.access.AccessManage;
import cn.weforward.gateway.ops.right.RightManage;
import cn.weforward.gateway.ops.traffic.TrafficManage;

/**
 * 网关Api集合
 * 
 * @author zhangpengji
 *
 */
public class GatewayApis {

	ServiceRegisterApi m_ServiceRegisterApi;
	DistributeApi m_DistributeApi;
	KeeperApi m_KeeperApi;

	public GatewayApis() {
		m_ServiceRegisterApi = new ServiceRegisterApi();
		m_DistributeApi = new DistributeApi();
		m_KeeperApi = new KeeperApi();
	}

	public void setAccessManage(AccessManage am) {
		m_KeeperApi.setAccessManage(am);
	}

	public void setGateway(GatewayExt gw) {
		m_KeeperApi.setGateway(gw);
		m_ServiceRegisterApi.setGateway(gw);
	}

	public void setRightManage(RightManage rm) {
		m_KeeperApi.setRightManage(rm);
	}

	public void setTrafficManage(TrafficManage tm) {
		m_KeeperApi.setTrafficManage(tm);
	}

	public void setDistributeManage(DistributeManage dm) {
		m_DistributeApi.setDistributeManage(dm);
	}

	/**
	 * 按名称获取网关Api
	 * 
	 * @param name
	 * @return
	 */
	public GatewayApi getApi(String name) {
		if (m_ServiceRegisterApi.getName().equals(name)) {
			return m_ServiceRegisterApi;
		}
		if (m_DistributeApi.getName().equals(name)) {
			return m_DistributeApi;
		}
		if (m_KeeperApi.getName().equals(name)) {
			return m_KeeperApi;
		}
		return null;
	}
}
