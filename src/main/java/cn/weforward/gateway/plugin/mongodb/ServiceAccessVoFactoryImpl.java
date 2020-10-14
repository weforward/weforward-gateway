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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import cn.weforward.common.NameItem;
import cn.weforward.common.ResultPage;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.common.util.TransResultPage;
import cn.weforward.data.persister.ChangeListener;
import cn.weforward.data.persister.Persistent;
import cn.weforward.data.persister.Persister;
import cn.weforward.data.persister.PersisterFactory;
import cn.weforward.gateway.ops.access.system.ServiceAccessVo;
import cn.weforward.gateway.ops.access.system.ServiceAccessVoFactory;
import cn.weforward.gateway.plugin.mongodb.di.ServiceAccessDi;

/**
 * <code>SystemAccessVoFactory</code>实现
 * 
 * @author zhangpengji
 *
 */
public class ServiceAccessVoFactoryImpl implements ServiceAccessVoFactory, ServiceAccessDi {

	Persister<ServiceAccess> m_PsSystemAccess;
	List<ReloadListener> m_Listeners;

	ServiceAccessVoFactoryImpl(PersisterFactory factory) {
		m_PsSystemAccess = factory.createPersister(ServiceAccess.class, this);
		// m_PsSystemAccess.setReloadEnabled(true);
		m_PsSystemAccess.addListener(new ChangeListener<ServiceAccess>() {

			@Override
			public void onChange(NameItem type, String id, Supplier<ServiceAccess> supplierdata) {
				if (ChangeListener.TYPE_NEW.id == type.id || ChangeListener.TYPE_UPDATE.id == type.id) {
					ServiceAccess acc = m_PsSystemAccess.get(id);
					acc.reload(supplierdata.get());
					ServiceAccessVoFactoryImpl.this.onReload(acc);
				}
			}
		});
		m_Listeners = new CopyOnWriteArrayList<ReloadListener>();
	}

	// @Override
	// public String genPersistenceId(String prefix) {
	// String id = m_PsSystemAccess.getNewId(prefix).getOrdinal();
	// if (-1 != id.indexOf(Access.SPEARATOR)) {
	// id = id.replace(Access.SPEARATOR_STR, "");
	// }
	// return id;
	// }

	@Override
	public ServiceAccessVo get(String id) {
		ServiceAccess obj = m_PsSystemAccess.get(id);
		if (null == obj) {
			return null;
		}
		return obj.getVo();
	}

	@Override
	public void put(ServiceAccessVo accessVo) {
		String id = accessVo.id;
		ServiceAccess access = m_PsSystemAccess.get(id);
		if (null == access) {
			synchronized (this) {
				access = m_PsSystemAccess.get(id);
				if (null == access) {
					access = new ServiceAccess(this, accessVo);
					return;
				}
			}
		}
		access.updateVo(accessVo);
	}

	@Override
	public ResultPage<ServiceAccessVo> startsWith(String prefix) {
		ResultPage<ServiceAccess> rp = m_PsSystemAccess.startsWith(prefix);
		if (0 == rp.getCount()) {
			return ResultPageHelper.empty();
		}
		return new TransResultPage<ServiceAccessVo, ServiceAccess>(rp) {

			@Override
			protected ServiceAccessVo trans(ServiceAccess src) {
				return src.getVo();
			}
		};
	}

	@Override
	public ResultPage<String> startsWithOfId(String prefix) {
		return m_PsSystemAccess.startsWithOfId(prefix);
	}

	@Override
	public void registerReloadListener(ReloadListener listener) {
		m_Listeners.add(listener);
	}

	@Override
	public boolean unregisterReloadListener(ReloadListener listener) {
		return m_Listeners.remove(listener);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <E extends Persistent> Persister<E> getPersister(Class<E> clazz) {
		if (clazz == ServiceAccess.class) {
			return (Persister<E>) m_PsSystemAccess;
		}
		return null;
	}

	@Override
	public void onReload(ServiceAccess obj) {
		for (ReloadListener listener : m_Listeners) {
			listener.onReload(obj.getVo());
		}
	}

}
