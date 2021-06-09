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

import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import cn.weforward.common.NameItem;
import cn.weforward.common.ResultPage;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.common.util.TransResultPage;
import cn.weforward.data.persister.Persister;
import cn.weforward.data.persister.PersisterFactory;
import cn.weforward.gateway.ops.VoFactory;

/**
 * <code>MasterKeyVoFactory</code>实现
 * 
 * @author zhangpengji
 *
 */
public abstract class AbstractVoFactoryImpl<E extends VoPersistent<V>, V> implements VoFactory<V> {

	Persister<E> m_Persister;
	List<ReloadListener<V>> m_ReloadListeners;
	List<ChangeListener<V>> m_ChangeListeners;

	AbstractVoFactoryImpl(PersisterFactory factory) {
		m_Persister = createPersister(factory);
		// m_PsMasterKey.setReloadEnabled(true);
		m_Persister.addListener(new cn.weforward.data.persister.ChangeListener<E>() {

			@Override
			public void onChange(NameItem type, String id, Supplier<E> supplierdata) {
				if (cn.weforward.data.persister.ChangeListener.TYPE_NEW.id == type.id
						|| cn.weforward.data.persister.ChangeListener.TYPE_UPDATE.id == type.id) {
					E obj = m_Persister.get(id);
					obj.reload(supplierdata.get());
					AbstractVoFactoryImpl.this.onReload(obj);
					
					AbstractVoFactoryImpl.this.onChanged(obj, CHANGE_TYPE_UPDATE);
				}
			}
		});
		m_ReloadListeners = new CopyOnWriteArrayList<ReloadListener<V>>();
		m_ChangeListeners = new CopyOnWriteArrayList<ChangeListener<V>>();
	}
	
	protected abstract Persister<E> createPersister(PersisterFactory factory);

	// @Override
	// public String genPersistenceId(String prefix) {
	// String id = m_PsMasterKey.getNewId(prefix).getOrdinal();
	// if (-1 != id.indexOf(Access.SPEARATOR)) {
	// id = id.replace(Access.SPEARATOR_STR, "");
	// }
	// return id;
	// }

	@Override
	public V get(String id) {
		E obj = m_Persister.get(id);
		if (null == obj) {
			return null;
		}
		return obj.getVo();
	}

	@Override
	public void put(V vo) {
		E obj = putInner(vo);
		
		onChanged(obj, CHANGE_TYPE_UPDATE);
	}
	
	protected E putInner(V vo) {
		String id = getVoId(vo);
		E obj = m_Persister.get(id);
		if (null == obj) {
			synchronized (this) {
				obj = m_Persister.get(id);
				if (null == obj) {
					obj = createVoPersistent(vo);
					return obj;
				}
			}
		}
		obj.updateVo(vo);
		return obj;
	}
	
	protected abstract String getVoId(V vo);
	
	protected abstract E createVoPersistent(V vo);

	@Override
	public ResultPage<V> startsWith(String prefix) {
		ResultPage<E> rp = m_Persister.startsWith(prefix);
		if (0 == rp.getCount()) {
			return ResultPageHelper.empty();
		}
		return new TransResultPage<V, E>(rp) {

			@Override
			protected V trans(E src) {
				return src.getVo();
			}
		};
	}

	@Override
	public ResultPage<String> startsWithOfId(String prefix) {
		return m_Persister.startsWithOfId(prefix);
	}

	@Override
	public ResultPage<V> search(Date begin, Date end) {
		ResultPage<E> rp = m_Persister.search(begin, end);
		if (0 == rp.getCount()) {
			return ResultPageHelper.empty();
		}
		return new TransResultPage<V, E>(rp) {

			@Override
			protected V trans(E src) {
				return src.getVo();
			}
		};
	}

	@Override
	public void registerReloadListener(ReloadListener<V> listener) {
		m_ReloadListeners.add(listener);
	}

	@Override
	public boolean unregisterReloadListener(ReloadListener<V> listener) {
		return m_ReloadListeners.remove(listener);
	}
	
	@Override
	public void registerChangeListener(ChangeListener<V> listener) {
		m_ChangeListeners.add(listener);
	}
	
	@Override
	public boolean unregisterChangeListener(ChangeListener<V> listener) {
		return m_ChangeListeners.remove(listener);
	}

	public void onReload(E obj) {
		for (ReloadListener<V> listener : m_ReloadListeners) {
			listener.onReload(obj.getVo());
		}
	}

	public void onChanged(E obj, int type) {
		for (ChangeListener<V> listener : m_ChangeListeners) {
			listener.onChanged(obj.getVo(), type);
		}
	}
	
	@Override
	public void updateByMesh(V vo) {
		E obj = putInner(vo);
		onReload(obj);
		onChanged(obj, CHANGE_TYPE_UPDATE);
	}
}
