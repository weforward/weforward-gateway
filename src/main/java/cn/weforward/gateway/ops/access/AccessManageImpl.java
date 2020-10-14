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
package cn.weforward.gateway.ops.access;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.weforward.common.ResultPage;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.PluginContainer;
import cn.weforward.gateway.PluginListener;
import cn.weforward.gateway.Pluginable;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.ops.AccessExt;

/**
 * <code>AccessManage</code>实现
 * 
 * @author zhangpengji
 *
 */
public class AccessManageImpl implements AccessManage, PluginListener {

	Map<String, AccessProvider> m_AccessProviders;

	public AccessManageImpl() {
		m_AccessProviders = new HashMap<String, AccessProvider>();
	}

	public void setPluginContainer(PluginContainer container) {
		container.register(this);
	}

	@Override
	public Access getInternalAccess() {
		AccessProvider provider = getProvider(Access.KIND_GATEWAY);
		if (null == provider) {
			return null;
		}
		return provider.getAccess(AccessExt.GATEWAY_INTERNAL_ACCESS_ID);
	}

	@Override
	public Access getValidAccess(String accessId) {
		AccessExt acc = getAccess(accessId);
		if (null == acc || !acc.isValid()) {
			return null;
		}
		return acc;
	}

	@Override
	public AccessProvider registerProvider(AccessProvider provider) {
		if (null == provider) {
			throw new IllegalArgumentException("'provider'不能为空");
		}
		String kind = provider.getKind();
		if (!Access.KIND_ALL.contains(kind)) {
			throw new IllegalArgumentException("不支持的类型：" + kind);
		}
		return m_AccessProviders.put(kind, provider);
	}

	@Override
	public boolean unregisterProvider(AccessProvider provider) {
		if (null == provider) {
			return false;
		}
		String kind = provider.getKind();
		AccessProvider exist = m_AccessProviders.get(kind);
		if (exist == provider) {
			m_AccessProviders.remove(kind);
			return true;
		}
		return false;
	}

	public AccessProvider getProvider(String kind) {
		return m_AccessProviders.get(kind);
	}

	@Override
	public AccessExt createAccess(String kind, String groupId) {
		AccessProvider provider = getProvider(kind);
		if (null == provider) {
			throw new IllegalArgumentException("不支持的类型：" + kind);
		}
		return provider.createAccess(groupId);
	}

	@Override
	public AccessExt getAccess(String id) {
		String kind = Access.Helper.getKind(id);
		if (StringUtil.isEmpty(kind)) {
			return null;
		}
		AccessProvider provider = getProvider(kind);
		if (null == provider) {
			return null;
		}
		return provider.getAccess(id);
	}

	@Override
	public ResultPage<AccessExt> listAccess(String kind, String groupId, String keyword) {
		AccessProvider provider = getProvider(kind);
		if (null == provider) {
			return ResultPageHelper.empty();
		}
		return provider.listAccess(groupId, keyword);
	}

	@Override
	public List<String> listAccessGroup(String kind) {
		AccessProvider provider = getProvider(kind);
		if (null == provider) {
			return Collections.emptyList();
		}
		return provider.listAccessGroup();
	}

	@Override
	public void onPluginLoad(Pluginable plugin) {
		if (plugin instanceof AccessProvider) {
			registerProvider((AccessProvider) plugin);
		}
	}

	@Override
	public void onPluginUnload(Pluginable plugin) {
		if (plugin instanceof AccessProvider) {
			unregisterProvider((AccessProvider) plugin);
		}
	}

}
