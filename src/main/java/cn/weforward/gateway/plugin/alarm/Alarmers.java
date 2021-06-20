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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import cn.weforward.common.util.NumberUtil;
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
import cn.weforward.gateway.plugin.PropertiesLoader;
import cn.weforward.gateway.plugin.PropertiesLoaderAware;

/**
 * 报警器
 * 
 * @author daibo
 *
 */
public class Alarmers implements PropertiesLoaderAware, AccessLoaderAware, ServiceListener, GatewayAware,
		GatewayNodeListener, GatewayNodesAware {

	protected List<ServiceListener> m_ServiceListeners;
	protected List<GatewayNodeListener> m_GatewayNodeListeners;

	protected Gateway m_Gateway;
	protected AccessLoaderExt m_AccessLoader;
	protected GatewayNodes m_GatewayNodes;

	public Alarmers() {
		m_ServiceListeners = new ArrayList<>();
		m_GatewayNodeListeners = new ArrayList<>();
	}

	@Override
	public void setPropertiesLoader(PropertiesLoader loader) {
		Properties prop = loader.loadProperties("alarmers");
		if (null == prop) {
			return;
		}
		String servicename = prop.getProperty("service.name");
		String methodgroup = prop.getProperty("service.methodgroup");
		if (!StringUtil.isEmpty(servicename)) {
			ServiceAlarmer sa = new ServiceAlarmer(servicename, methodgroup);
			add((ServiceListener) sa);
			add((GatewayNodeListener) sa);
		}
		String smtpHost = prop.getProperty("smtp.host");
		String smtpUsername = prop.getProperty("smtp.username");
		String smtpPassword = prop.getProperty("smtp.password");
		String receivers = prop.getProperty("smtp.receivers");
		if (!StringUtil.isEmpty(smtpHost) && !StringUtil.isEmpty(smtpUsername) && !StringUtil.isEmpty(smtpPassword)
				&& !StringUtil.isEmpty(receivers)) {
			EmailAlarmer ea = new EmailAlarmer(smtpHost, smtpUsername, smtpPassword);
			int timeout = NumberUtil.toInt(prop.getProperty("alarmer.smtp.timeout"), 0);
			if (timeout > 0) {
				ea.setSmtpTimeout(timeout);
			}
			ea.setReceiver(receivers);
			add((ServiceListener) ea);
			add((GatewayNodeListener) ea);
		}

	}

	private synchronized void add(ServiceListener l) {
		setAware(l);
		m_ServiceListeners.add(l);
	}

	private void setAware(Object l) {
		if (null != m_Gateway && l instanceof GatewayAware) {
			((GatewayAware) l).setGateway(m_Gateway);
		}
		if (null != m_AccessLoader && l instanceof AccessLoaderAware) {
			((AccessLoaderAware) l).setAccessLoader(m_AccessLoader);
		}
		if (null != m_GatewayNodes && l instanceof GatewayNodesAware) {
			((GatewayNodesAware) l).setGatewayNodes(m_GatewayNodes);
		}
	}

	private synchronized void add(GatewayNodeListener l) {
		setAware(l);
		m_GatewayNodeListeners.add(l);
	}

	@Override
	public void setGateway(Gateway gateway) {
		m_Gateway = gateway;
		for (ServiceListener l : m_ServiceListeners) {
			if (l instanceof GatewayAware) {
				((GatewayAware) l).setGateway(m_Gateway);
			}
		}
		for (GatewayNodeListener l : m_GatewayNodeListeners) {
			if (l instanceof GatewayAware) {
				((GatewayAware) l).setGateway(m_Gateway);
			}
		}
	}

	@Override
	public void setAccessLoader(AccessLoaderExt loader) {
		m_AccessLoader = loader;
		for (ServiceListener l : m_ServiceListeners) {
			if (l instanceof AccessLoaderAware) {
				((AccessLoaderAware) l).setAccessLoader(loader);
			}
		}
		for (GatewayNodeListener l : m_GatewayNodeListeners) {
			if (l instanceof AccessLoaderAware) {
				((AccessLoaderAware) l).setAccessLoader(loader);
			}
		}
	}

	@Override
	public void setGatewayNodes(GatewayNodes nodes) {
		m_GatewayNodes = nodes;
		for (ServiceListener l : m_ServiceListeners) {
			if (l instanceof GatewayNodesAware) {
				((GatewayNodesAware) l).setGatewayNodes(nodes);
			}
		}
		for (GatewayNodeListener l : m_GatewayNodeListeners) {
			if (l instanceof GatewayNodesAware) {
				((GatewayNodesAware) l).setGatewayNodes(nodes);
			}
		}
	}

	@Override
	public void onServiceRegister(ServiceInstance service, boolean foreign) {
		for (ServiceListener l : m_ServiceListeners) {
			l.onServiceRegister(service, foreign);
		}
	}

	@Override
	public void onServiceUnregister(ServiceInstance service, boolean foreign) {
		for (ServiceListener l : m_ServiceListeners) {
			l.onServiceUnregister(service, foreign);
		}
	}

	@Override
	public void onServiceTimeout(ServiceInstance service) {
		for (ServiceListener l : m_ServiceListeners) {
			l.onServiceTimeout(service);
		}
	}

	@Override
	public void onServiceUnavailable(ServiceInstance service) {
		for (ServiceListener l : m_ServiceListeners) {
			l.onServiceUnavailable(service);
		}
	}

	@Override
	public void onServiceOverload(ServiceInstance service) {
		for (ServiceListener l : m_ServiceListeners) {
			l.onServiceOverload(service);
		}
	}

	@Override
	public void onGatewayNodeLost(GatewayNode node) {
		for (GatewayNodeListener l : m_GatewayNodeListeners) {
			l.onGatewayNodeLost(node);
		}
	}
}
