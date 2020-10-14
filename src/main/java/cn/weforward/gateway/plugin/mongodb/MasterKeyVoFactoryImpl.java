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
import cn.weforward.gateway.ops.access.system.MasterKeyVo;
import cn.weforward.gateway.ops.access.system.MasterKeyVoFactory;
import cn.weforward.gateway.plugin.mongodb.di.MasterKeyDi;

/**
 * <code>MasterKeyVoFactory</code>实现
 * 
 * @author zhangpengji
 *
 */
public class MasterKeyVoFactoryImpl implements MasterKeyVoFactory, MasterKeyDi {

	Persister<MasterKey> m_PsMasterKey;
	List<ReloadListener> m_Listeners;

	MasterKeyVoFactoryImpl(PersisterFactory factory) {
		m_PsMasterKey = factory.createPersister(MasterKey.class, this);
		//m_PsMasterKey.setReloadEnabled(true);
		m_PsMasterKey.addListener(new ChangeListener<MasterKey>() {

			@Override
			public void onChange(NameItem type, String id, Supplier<MasterKey> supplierdata) {
				if (ChangeListener.TYPE_NEW.id == type.id || ChangeListener.TYPE_UPDATE.id == type.id) {
					MasterKey mk = m_PsMasterKey.get(id);
					mk.reload(supplierdata.get());
					MasterKeyVoFactoryImpl.this.onReload(mk);
				}
			}
		});
		m_Listeners = new CopyOnWriteArrayList<ReloadListener>();
	}

	// @Override
	// public String genPersistenceId(String prefix) {
	// String id = m_PsMasterKey.getNewId(prefix).getOrdinal();
	// if (-1 != id.indexOf(Access.SPEARATOR)) {
	// id = id.replace(Access.SPEARATOR_STR, "");
	// }
	// return id;
	// }

	@Override
	public MasterKeyVo get(String id) {
		MasterKey obj = m_PsMasterKey.get(id);
		if (null == obj) {
			return null;
		}
		return obj.getVo();
	}

	@Override
	public void put(MasterKeyVo masterKeyVo) {
		String id = masterKeyVo.id;
		MasterKey obj = m_PsMasterKey.get(id);
		if (null == obj) {
			synchronized (this) {
				obj = m_PsMasterKey.get(id);
				if (null == obj) {
					obj = new MasterKey(this, masterKeyVo);
					return;
				}
			}
		}
		obj.updateVo(masterKeyVo);
	}

	@Override
	public ResultPage<MasterKeyVo> startsWith(String prefix) {
		ResultPage<MasterKey> rp = m_PsMasterKey.startsWith(prefix);
		if (0 == rp.getCount()) {
			return ResultPageHelper.empty();
		}
		return new TransResultPage<MasterKeyVo, MasterKey>(rp) {

			@Override
			protected MasterKeyVo trans(MasterKey src) {
				return src.getVo();
			}
		};
	}

	@SuppressWarnings("unchecked")
	@Override
	public <E extends Persistent> Persister<E> getPersister(Class<E> clazz) {
		if (clazz == MasterKey.class) {
			return (Persister<E>) m_PsMasterKey;
		}
		return null;
	}

	@Override
	public void registerReloadListener(ReloadListener listener) {
		m_Listeners.add(listener);
	}

	@Override
	public boolean unregisterReloadListener(ReloadListener listener) {
		return m_Listeners.remove(listener);
	}
	
	public void onReload(MasterKey obj) {
		for (ReloadListener listener : m_Listeners) {
			listener.onReload(obj.getVo());
		}
	}

}
