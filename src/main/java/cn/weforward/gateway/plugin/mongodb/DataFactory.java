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
package cn.weforward.gateway.plugin.mongodb;

import java.util.Properties;

import cn.weforward.data.mongodb.persister.MongodbPersisterFactory;
import cn.weforward.data.persister.PersisterFactory;
import cn.weforward.data.util.DelayFlusher;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.Pluginable;
import cn.weforward.gateway.distribute.GatewayNodes;
import cn.weforward.gateway.plugin.GatewayNodesAware;
import cn.weforward.gateway.plugin.PluginContainerAware;
import cn.weforward.gateway.plugin.PropertiesLoader;
import cn.weforward.gateway.plugin.PropertiesLoaderAware;

/**
 * 数据工厂
 * <p>
 * 负责统一加载各个VoFactory，并通过PluginContainer置入
 * 
 * @author zhangpengji
 *
 */
public class DataFactory implements Pluginable, PropertiesLoaderAware, PluginContainerAware, GatewayNodesAware {

	PersisterFactory m_Factory;
	ServiceAccessVoFactoryImpl m_SystemAccessVoFactory;
	MasterKeyVoFactoryImpl m_MasterKeyVoFactory;
	RightTableVoFactoryImpl m_RightTableVoFactory;
	TrafficTableVoFactoryImpl m_TafficTableVoFactory;
	AclTableVoFactoryImpl m_AclTableVoFactory;

	PluginContainer m_PluginContainer;
	PropertiesLoader m_PropertiesLoader;
	GatewayNodes m_GatewayNodes;
	boolean m_Inited;

	public DataFactory() {

	}

	@Override
	public void setPropertiesLoader(PropertiesLoader loader) {
		m_PropertiesLoader = loader;
		init();
	}

	@Override
	public void setGatewayNodes(GatewayNodes nodes) {
		m_GatewayNodes = nodes;
		init();
	}

	void init() {
		if (null == m_PropertiesLoader || null == m_GatewayNodes) {
			return;
		}
		Properties prop = m_PropertiesLoader.loadProperties("mongodb");

		String connection = prop.getProperty("db.connection");
		String dbName = prop.getProperty("db.name");
		String serverId = m_GatewayNodes.getSelfNode().getId();

		MongodbPersisterFactory factory = new MongodbPersisterFactory(connection, dbName);
		factory.setServerId(serverId);

		DelayFlusher flusher = new DelayFlusher(1);
		factory.setFlusher(flusher);

		m_Factory = factory;

		m_SystemAccessVoFactory = new ServiceAccessVoFactoryImpl(factory);
		m_MasterKeyVoFactory = new MasterKeyVoFactoryImpl(factory);
		m_RightTableVoFactory = new RightTableVoFactoryImpl(factory);
		m_TafficTableVoFactory = new TrafficTableVoFactoryImpl(factory);
		m_AclTableVoFactory = new AclTableVoFactoryImpl(factory);

		m_Inited = true;
		addPlugins();
	}

	void addPlugins() {
		if (!m_Inited || null == m_PluginContainer) {
			return;
		}
		m_PluginContainer.add(m_SystemAccessVoFactory);
		m_PluginContainer.add(m_MasterKeyVoFactory);
		m_PluginContainer.add(m_RightTableVoFactory);
		m_PluginContainer.add(m_TafficTableVoFactory);
		m_PluginContainer.add(m_AclTableVoFactory);
	}

	@Override
	public void setPluginContainer(PluginContainer plugins) {
		m_PluginContainer = plugins;
		addPlugins();
	}
}
