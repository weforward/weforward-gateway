package cn.weforward.gateway.plugin.misc;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.AccessLoaderExt;
import cn.weforward.gateway.api.ServiceChecker;
import cn.weforward.gateway.plugin.AccessLoaderAware;
import cn.weforward.gateway.plugin.PropertiesLoader;
import cn.weforward.gateway.plugin.PropertiesLoaderAware;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.ops.AccessExt;

/**
 * 检查微服务的分组名
 * 
 * @author smily
 *
 */
public class GroupingServiceChecker implements ServiceChecker, AccessLoaderAware, PropertiesLoaderAware {

	ServiceChecker m_PreChecker;
	AccessLoaderExt m_AccessLoader;
	List<String> m_Privileges;

	public GroupingServiceChecker() {

	}

	@Override
	public void setAccessLoader(AccessLoaderExt loader) {
		m_AccessLoader = loader;
	}

	@Override
	public void setPropertiesLoader(PropertiesLoader loader) {
		Properties properties = loader.loadProperties("servicechecker");
		if (null == properties) {
			return;
		}
		setPrivilegeString(properties.getProperty("privileges"));
	}

	public void setPrivilegeString(String str) {
		if (StringUtil.isEmpty(str)) {
			m_Privileges = null;
			return;
		}
		m_Privileges = Arrays.asList(str.split(";"));
	}

	@Override
	public String check(Service service, String owner) {
		if (null != m_PreChecker) {
			String result = m_PreChecker.check(service, owner);
			if (!StringUtil.isEmpty(result)) {
				return result;
			}
		}
		Access access = m_AccessLoader.getValidAccess(owner);
		if (!(access instanceof AccessExt)) {
			return null;
		}
		String group = ((AccessExt) access).getGroupId();
		if (StringUtil.isEmpty(group) || (!ListUtil.isEmpty(m_Privileges) && m_Privileges.contains(group))) {
			return null;
		}
		String prefix = group + ServiceName.GROUP_SPEARATOR;
		if (!service.getName().startsWith(prefix)) {
			return "服务名应该以[" + prefix + "]开头";
		}
		return null;
	}

	@Override
	public ServiceChecker getPreChecker() {
		return m_PreChecker;
	}

	@Override
	public void setPreCheck(ServiceChecker checker) {
		m_PreChecker = checker;
	}

}
