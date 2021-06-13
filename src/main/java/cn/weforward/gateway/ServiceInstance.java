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

import java.util.Collections;
import java.util.Date;
import java.util.List;

import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.aio.ClientChannel;
import cn.weforward.protocol.ops.ServiceExt;

/**
 * 网关中的微服务实例
 * 
 * @author zhangpengji
 *
 */
public class ServiceInstance implements ServiceExt {

	protected String m_Id;
	protected String m_NameNo;

	/** @see #getName() */
	protected String m_Name;
	/** @see #getDomain() */
	protected String m_Domain;
	/** @see #getPort() */
	protected int m_Port;
	/** @see #getUrls() */
	protected List<String> m_Urls;
	/** @see #getNo() */
	protected String m_No;
	/** @see #getVersion() */
	protected String m_Version;
	/** @see #getCompatibleVersion() */
	protected String m_CompatibleVersion;
	/** @see #getHeartbeatPeriod() */
	protected int m_HeartbeatPeriod;
	/** @see #getBuildVersion() */
	protected String m_BuildVersion;
	/** @see #getNote() */
	protected String m_Note;
	/** @see #getDocumentMethod() */
	protected String m_DocumentMethod;
	/** @see #getDebugMethod() */
	protected String m_DebugMethod;
	/** @see #getRunningId() */
	protected String m_RunningId;
	/** @see #getRequestMaxSize() */
	protected int m_RequestMaxSize;
	/** @see #getMarks() */
	protected int m_Marks;

	/** @see #getOwner() */
	protected String m_Owner;
	/** @see #getHeartbeat() */
	protected Date m_Heartbeat;
	/** @see #getState() */
	protected int m_State;

	/** 所在的网格 */
	protected MeshNode m_MeshNode;

	/**
	 * 与网关通信的channel。<br/>
	 * 当不为空时，表示实例只能由当前网关通过此Channel通信
	 */
	protected ClientChannel m_ClientChannel;

	public ServiceInstance(Service service, String owner, Date heartbeat) {
		init(service);
		setOwner(owner);
		setHeartbeat(heartbeat);
	}

	/**
	 * 由其他网关的微服务信息构造
	 * 
	 * @param foreign
	 */
	public ServiceInstance(ServiceExt foreign) {
		init(foreign);
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
		init((ServiceExt) foreign);
		m_MeshNode = foreign.getMeshNode();
	}

	protected void init(Service service) {
		m_Name = service.getName();
		m_Domain = service.getDomain();
		m_Port = service.getPort();
		m_Urls = service.getUrls();
		if (ListUtil.isEmpty(m_Urls)) {
			if (!StringUtil.isEmpty(m_Domain) && m_Port > 0) {
				String url = "http://" + m_Domain + ":" + m_Port + "/" + m_Name;
				m_Urls = Collections.singletonList(url);
			}
		}
		m_No = service.getNo();
		m_Version = service.getVersion();
		m_CompatibleVersion = service.getCompatibleVersion();
		m_BuildVersion = service.getBuildVersion();
		m_HeartbeatPeriod = service.getHeartbeatPeriod();
		m_Note = service.getNote();
		m_DocumentMethod = service.getDocumentMethod();
		m_RunningId = service.getRunningId();
		m_RequestMaxSize = service.getRequestMaxSize();
		m_Marks = service.getMarks();
		m_DebugMethod = service.getDebugMethod();
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

	@Override
	public String getName() {
		return m_Name;
	}

	@Override
	public List<String> getUrls() {
		return m_Urls;
	}

	@Override
	public String getNo() {
		return m_No;
	}

	@Override
	public String getVersion() {
		return m_Version;
	}

	@Override
	public String getCompatibleVersion() {
		return m_CompatibleVersion;
	}

	@Override
	public String getBuildVersion() {
		return m_BuildVersion;
	}

	@Override
	public String getNote() {
		return m_Note;
	}

	@Override
	public String getDocumentMethod() {
		return m_DocumentMethod;
	}

	@Override
	public String getRunningId() {
		return m_RunningId;
	}

	public void setName(String name) {
		m_Name = name;
	}

	public void setUrls(List<String> urls) {
		m_Urls = urls;
	}

	public void setNo(String no) {
		m_No = no;
	}

	public void setVersion(String version) {
		m_Version = version;
	}

	public void setCompatibleVersion(String compatibleVersion) {
		m_CompatibleVersion = compatibleVersion;
	}

	public void setHeartbeatPeriod(int heartbeatPeriod) {
		m_HeartbeatPeriod = heartbeatPeriod;
	}

	public void setBuildVersion(String buildVersion) {
		m_BuildVersion = buildVersion;
	}

	public void setNote(String note) {
		m_Note = note;
	}

	public void setDocumentMethod(String documentMethod) {
		m_DocumentMethod = documentMethod;
	}

	public void setRunningId(String runningId) {
		m_RunningId = runningId;
	}

	@Override
	public String getDomain() {
		return m_Domain;
	}

	@Override
	public int getPort() {
		return m_Port;
	}

	@Override
	public int getRequestMaxSize() {
		return m_RequestMaxSize;
	}

	@Override
	public int getMarks() {
		return m_Marks;
	}

	@Override
	public String getDebugMethod() {
		return m_DebugMethod;
	}

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
		int period = m_HeartbeatPeriod;
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

	public ClientChannel getClientChannel() {
		return m_ClientChannel;
	}

	public void setClientChannel(ClientChannel clientChannel) {
		m_ClientChannel = clientChannel;
	}
}
