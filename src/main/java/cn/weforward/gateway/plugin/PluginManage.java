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
package cn.weforward.gateway.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.AccessLoaderExt;
import cn.weforward.gateway.Gateway;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.gateway.distribute.GatewayNodes;

/**
 * 插件管理器
 * 
 * @author zhangpengji
 *
 */
public class PluginManage implements PluginContainer, PropertiesLoader {
	static final Logger _Logger = LoggerFactory.getLogger(PluginManage.class);

	CopyOnWriteArrayList<Pluginable> m_Plugins;
	CopyOnWriteArrayList<PluginListener> m_Listeners;

	Gateway m_Gateway;
	AccessLoaderExt m_AccessLoader;
	GatewayNodes m_GatewayNodes;
	String m_PluginNames;

	public PluginManage() {
		m_Plugins = new CopyOnWriteArrayList<>();
		m_Listeners = new CopyOnWriteArrayList<>();
	}

	public void setGateway(Gateway gateway) {
		m_Gateway = gateway;
		loadByNames();
	}

	public void setAccessLoader(AccessLoaderExt accessLoader) {
		m_AccessLoader = accessLoader;
		loadByNames();
	}

	public void setGatewayNodes(GatewayNodes nodes) {
		m_GatewayNodes = nodes;
		loadByNames();
	}

	/**
	 * 设置需要加载的插件名称
	 * 
	 * @param names
	 *            类名，使用“;”分隔
	 */
	public void setPluginNames(String names) {
		m_PluginNames = names;
		loadByNames();
	}

	private void loadByNames() {
		if (StringUtil.isEmpty(m_PluginNames) || null == m_AccessLoader || null == m_GatewayNodes
				|| null == m_Gateway) {
			return;
		}
		for (String name : m_PluginNames.split(";")) {
			load(name);
		}
		m_PluginNames = null;
	}

	/**
	 * 根据类名加载插件
	 * 
	 * @param classNames
	 */
	public synchronized void load(String className) {
		Pluginable plugin;
		try {
			Class<?> clazz = Class.forName(className);
			plugin = (Pluginable) clazz.newInstance();
			if (plugin instanceof PropertiesLoaderAware) {
				((PropertiesLoaderAware) plugin).setPropertiesLoader(this);
			}
			if (plugin instanceof AccessLoaderAware) {
				((AccessLoaderAware) plugin).setAccessLoader(m_AccessLoader);
			}
			if (plugin instanceof GatewayNodesAware) {
				((GatewayNodesAware) plugin).setGatewayNodes(m_GatewayNodes);
			}
			if (plugin instanceof GatewayAware) {
				((GatewayAware) plugin).setGateway(m_Gateway);
			}
			if (plugin instanceof PluginContainerAware) {
				((PluginContainerAware) plugin).setPluginContainer(this);
			}
		} catch (Throwable e) {
			_Logger.error("插件加载失败：" + className, e);
			return;
		}
		loaded(plugin);
	}

	private synchronized void loaded(Pluginable plugin) {
		m_Plugins.add(plugin);
		_Logger.info("已加载插件：" + plugin.getClass());

		for (PluginListener l : m_Listeners) {
			try {
				l.onPluginLoad(plugin);
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
			}
		}
	}

	/**
	 * 根据类名卸载插件
	 * 
	 * @param classNames
	 */
	public synchronized void unload(String className) {
		for (int i = 0; i < m_Plugins.size(); i++) {
			Pluginable p = m_Plugins.get(i);
			if (p.getClass().getName().equals(className)) {
				unloaded(i);
			}
		}
	}

	private synchronized void unloaded(int idx) {
		Pluginable p = m_Plugins.remove(idx);
		_Logger.info("已卸载插件：" + p.getClass());

		for (PluginListener l : m_Listeners) {
			try {
				l.onPluginUnload(p);
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
			}
		}
	}

	private synchronized void unloaded(Pluginable plugin) {
		if (!m_Plugins.remove(plugin)) {
			return;
		}
		_Logger.info("已卸载插件：" + plugin.getClass());

		for (PluginListener l : m_Listeners) {
			try {
				l.onPluginUnload(plugin);
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
			}
		}
	}

	@Override
	public void register(PluginListener listener) {
		// 当前可能正在加载plugin，先添加listener，可能会重复回调onPluginLoad，若后添加，则会漏掉
		m_Listeners.add(listener);

		if (m_Plugins.isEmpty()) {
			return;
		}
		for (Pluginable p : m_Plugins) {
			try {
				listener.onPluginLoad(p);
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
			}
		}
	}

	@Override
	public synchronized boolean unregister(PluginListener listener) {
		return m_Listeners.remove(listener);
	}

	@Override
	public Properties loadProperties(String name) {
		File file;
		String dir = System.getProperty("user.dir", "");
		if (StringUtil.isEmpty(dir)) {
			file = new File("conf/plugin/" + name + ".properties");
		} else {
			file = new File(dir + "/conf/plugin/" + name + ".properties");
		}
		if (!file.exists()) {
			_Logger.error("插件配置文件不存在：" + file);
			return null;
		}
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(file));
			return properties;
		} catch (Exception e) {
			_Logger.error("插件配置文件加载出错：" + file, e);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <E extends Pluginable> List<E> getPlugin(Class<E> clazz) {
		List<E> result = Collections.emptyList();
		for (Pluginable p : m_Plugins) {
			if (clazz.isAssignableFrom(p.getClass())) {
				if (result.isEmpty()) {
					result = new ArrayList<>();
				}
				result.add((E) p);
			}
		}
		return result;
	}

	@Override
	public void add(Pluginable plugin) {
		loaded(plugin);
	}

	@Override
	public void remove(Pluginable plugin) {
		unloaded(plugin);
	}
}
