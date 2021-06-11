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

import java.util.Date;

import javax.annotation.Resource;

import cn.weforward.common.util.StringUtil;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.ops.ServiceExt;
import cn.weforward.protocol.support.SimpleService;

/**
 * 网关中的微服务实例
 * 
 * @author zhangpengji
 *
 */
public class ServiceInstance extends SimpleService implements ServiceExt {

	protected String m_Id;
	protected String m_NameNo;

	/** @see #getOwner() */
	@Resource
	protected String m_Owner;
	/** @see #getHeartbeat() */
	@Resource
	protected Date m_Heartbeat;
	/** @see #getState() */
	@Resource
	protected int m_State;
	/** 所在的网格 */
	@Resource
	protected MeshNode m_MeshNode;

	public ServiceInstance(Service service, String owner, Date heartbeat) {
		super(service);
		setOwner(owner);
		setHeartbeat(heartbeat);
	}

	/**
	 * 由其他网关的微服务信息构造
	 * 
	 * @param foreign
	 */
	public ServiceInstance(ServiceExt foreign) {
		super(foreign);
		m_Owner = foreign.getOwner();
		m_Heartbeat = foreign.getHeartbeat();
		// m_State = service.getState(); 略过状态
	}

	/**
	 * 由其他网关的微服务信息构造
	 * 
	 * @param foreign
	 */
	public ServiceInstance(ServiceInstance foreign) {
		this((ServiceExt) foreign);
		m_MeshNode = foreign.getMeshNode();
	}

	// public static ServiceInstance valueOf(ServiceExt service) {
	// if (null == service) {
	// return null;
	// }
	// if (service instanceof ServiceInstance) {
	// return (ServiceInstance) service;
	// }
	// return new ServiceInstance(service);
	// }

	// @Override
	public String getId() {
		if (null == m_Id) {
			m_Id = getId(this);
		}
		return m_Id;
	}

	public static String getId(Service service) {
		if (StringUtil.isEmpty(service.getNo())) {
			return service.getName();
		}
		return service.getName() + '-' + service.getNo();
	}

	@Override
	public int getHeartbeatPeriod() {
		int period = super.getHeartbeatPeriod();
		if (period < 0) {
			period = -1;
		}
		if (0 == period) {
			period = 60;
		}
		return period;
	}

	/**
	 * 是否心跳超时
	 * 
	 * @return
	 */
	public boolean isHeartbeatTimeout(int maxMissing) {
		long period = getHeartbeatPeriod(); // 转为long，避免计算溢出
		if (-1 == period) {
			// 永不过期
			return false;
		}
		return (System.currentTimeMillis() - getHeartbeatMills()) > (period * maxMissing * 1000);
	}

	@Override
	public String getOwner() {
		return m_Owner;
	}

	@Override
	public Date getHeartbeat() {
		return m_Heartbeat;
	}

	public long getHeartbeatMills() {
		return (null == m_Heartbeat) ? 0 : m_Heartbeat.getTime();
	}

	@Override
	public boolean isTimeout() {
		return isState0(STATE_TIMEOUT);
	}

	public void setTimeout(boolean bool) {
		setState0(bool ? STATE_TIMEOUT : -STATE_TIMEOUT);
	}

	@Override
	public boolean isUnavailable() {
		return isState0(STATE_UNAVAILABLE);
	}

	public void setUnavailable(boolean bool) {
		setState0(bool ? STATE_UNAVAILABLE : -STATE_UNAVAILABLE);
	}

	@Override
	public boolean isInaccessible() {
		return isState0(STATE_INACCESSIBLE);
	}

	public void setInaccessible(boolean bool) {
		setState0(bool ? STATE_INACCESSIBLE : -STATE_INACCESSIBLE);
	}

	public void setOwner(String owner) {
		m_Owner = owner;
	}

	public void setHeartbeat(Date heartbeat) {
		m_Heartbeat = heartbeat;
	}

	public int getState() {
		return m_State;
	}

	public void setState(int state) {
		m_State = state;
	}

	protected void setState0(int state) {
		if (state < 0) {
			state = -state;
			m_State &= (~state);
		} else {
			m_State |= state;
		}
	}

	protected boolean isState0(int state) {
		return state == (state & m_State);
	}

	/**
	 * 返回微服务名字+编号的字串
	 * 
	 * @return
	 */
	public String toStringNameNo() {
		if (null == m_NameNo) {
			m_NameNo = getNameNo(this);
		}
		return m_NameNo;
	}

	public static String getNameNo(Service service) {
		if (null == service) {
			return "";
		}
		return service.getName() + "(" + StringUtil.toString(service.getNo()) + ")";
	}

	@Override
	public boolean isOverload() {
		return isState0(STATE_OVERLOAD);
	}

	public void setOverload(boolean bool) {
		setState0(bool ? STATE_OVERLOAD : -STATE_OVERLOAD);
	}

	public boolean isForwardEnable() {
		return isMark(MARK_FORWARD_ENABLE);
	}

	public boolean isMark(int mark) {
		return mark == (mark & m_Marks);
	}

	public MeshNode getMeshNode() {
		return m_MeshNode;
	}

	public void setMeshNode(MeshNode meshNode) {
		m_MeshNode = meshNode;
	}

	public String getMeshNodeId() {
		return (null == m_MeshNode) ? null : m_MeshNode.getId();
	}

	/**
	 * 此服务实例在本网格中
	 * 
	 * @return
	 */
	public boolean isSelfMesh() {
		return null == m_MeshNode || m_MeshNode.isSelf();
	}
}
