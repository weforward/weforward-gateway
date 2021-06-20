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
package cn.weforward.gateway.plugin.alarm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.AccessLoaderExt;
import cn.weforward.gateway.Gateway;
import cn.weforward.gateway.GatewayNode;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.ServiceListener;
import cn.weforward.gateway.distribute.GatewayNodeListener;
import cn.weforward.gateway.distribute.GatewayNodes;
import cn.weforward.gateway.plugin.AccessLoaderAware;
import cn.weforward.gateway.plugin.GatewayAware;
import cn.weforward.gateway.plugin.GatewayNodesAware;
import cn.weforward.gateway.util.ImitatedTunnel;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.RequestConstants;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.support.NamingConverter;
import cn.weforward.protocol.support.datatype.FriendlyObject;
import cn.weforward.protocol.support.datatype.SimpleDtObject;

/**
 * 服务报警器
 * 
 * @author daibo
 *
 */
public class ServiceAlarmer implements ServiceListener, GatewayNodeListener, GatewayAware, AccessLoaderAware, GatewayNodesAware {

	static final Logger _Logger = LoggerFactory.getLogger(ServiceAlarmer.class);

	protected Gateway m_Gateway;
	protected AccessLoaderExt m_AccessLoader;
	/** 当前网关节点 */
	protected GatewayNode m_GatewayNode;
	/** 服务名 */
	protected String m_ServiceName;
	/** 方法组 */
	protected String m_MethodGroup;

	public ServiceAlarmer(String serviceName, String methodGroup) {
		m_ServiceName = serviceName;
		m_MethodGroup = StringUtil.toString(methodGroup);
	}

	@Override
	public void setGateway(Gateway gateway) {
		m_Gateway = gateway;
	}

	@Override
	public void setAccessLoader(AccessLoaderExt loader) {
		m_AccessLoader = loader;
	}
	
	@Override
	public void setGatewayNodes(GatewayNodes nodes) {
		m_GatewayNode = nodes.getSelfNode();
	}

	@Override
	public void onServiceRegister(ServiceInstance service, boolean foreign) {
	}

	@Override
	public void onServiceUnregister(ServiceInstance service, boolean foreign) {
	}

	@Override
	public void onServiceTimeout(ServiceInstance service) {
		on("onServiceTimeout", service);
	}

	@Override
	public void onServiceUnavailable(ServiceInstance service) {
		on("onServiceUnavailable", service);
	}

	@Override
	public void onServiceOverload(ServiceInstance service) {
		on("onServiceOverload", service);
	}

	private void on(String method, ServiceInstance service) {
		SimpleDtObject invokeObject = new SimpleDtObject();
		invokeObject.put(RequestConstants.METHOD, m_MethodGroup + NamingConverter.camelToWf(method));
		SimpleDtObject params = new SimpleDtObject();
		params.put("no", service.getNo());
		params.put("name", service.getName());
		params.put("version", service.getVersion());
		params.put("buildVersion", service.getBuildVersion());
		params.put("note", service.getNote());
		params.put("runningId", service.getRunningId());
		params.put("gateway", m_GatewayNode.getId());
		invokeObject.put(RequestConstants.PARAMS, params);
		on(method, invokeObject);
	}

	private void on(String method, SimpleDtObject invokeObject) {
		String serviceName = m_ServiceName;
		ImitatedTunnel tunnel = new ImitatedTunnel(serviceName, invokeObject) {

			@Override
			protected void onResult(DtObject result) {
				int code = FriendlyObject.getInt(result, "code", -1);
				if (0 == code) {
					// 成功
					if (_Logger.isTraceEnabled()) {
						_Logger.trace("通知" + serviceName + "." + m_MethodGroup + method + "成功");
					}
				} else {
					String errMsg = code + "/" + FriendlyObject.getString(result, "msg");
					_Logger.warn("通知" + serviceName + "." + m_MethodGroup + method + "出错，异常:  " + errMsg);
				}
			}

			@Override
			protected void onError(Throwable e) {
				_Logger.warn("通知" + serviceName + "." + m_MethodGroup + method + "出错", e);
			}

			@Override
			protected void onError(int code, String msg) {
				String errMsg = code + "/" + msg;
				_Logger.warn("通知" + serviceName + "." + m_MethodGroup + method + "出错，异常:  " + errMsg);
			}

			@Override
			protected void onArrived() {
			}
		};
		// 使用网关凭证
		Access access = m_AccessLoader.getInternalAccess();
		tunnel.setAccess(access);
		m_Gateway.joint(tunnel);
	}

	@Override
	public void onGatewayNodeLost(GatewayNode node) {
		String method = "onGatewayTimeout";
		SimpleDtObject invokeObject = new SimpleDtObject();
		invokeObject.put(RequestConstants.METHOD, m_MethodGroup + NamingConverter.camelToWf(method));
		SimpleDtObject params = new SimpleDtObject();
		params.put("id", node.getId());
		params.put("host", node.getHostName());
		params.put("port", node.getPort());
		params.put("gateway", m_GatewayNode.getId());
		invokeObject.put(RequestConstants.PARAMS, params);
		on(method, invokeObject);
	}

}
