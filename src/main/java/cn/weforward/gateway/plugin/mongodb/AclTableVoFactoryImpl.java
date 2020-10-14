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
import cn.weforward.gateway.ops.acl.AclTableVo;
import cn.weforward.gateway.ops.acl.AclTableVoFactory;
import cn.weforward.gateway.plugin.mongodb.di.AclTableDi;

/**
 * <code>AclTableVoFactory</code>实现
 * 
 * @author zhangpengji
 *
 */
public class AclTableVoFactoryImpl implements AclTableVoFactory, AclTableDi {

	Persister<AclTable> m_PsAclTable;
	List<ReloadListener> m_Listeners;

	AclTableVoFactoryImpl(PersisterFactory factory) {
		m_PsAclTable = factory.createPersister(AclTable.class, this);
		// m_PsAclTable.setReloadEnabled(true);
		m_PsAclTable.addListener(new ChangeListener<AclTable>() {

			@Override
			public void onChange(NameItem type, String id, Supplier<AclTable> supplierdata) {
				if (ChangeListener.TYPE_NEW.id == type.id || ChangeListener.TYPE_UPDATE.id == type.id) {
					AclTable table = m_PsAclTable.get(id);
					table.reload(supplierdata.get());
					AclTableVoFactoryImpl.this.onReload(table);
				}
			}
		});
		m_Listeners = new CopyOnWriteArrayList<ReloadListener>();
	}

	@Override
	public AclTableVo get(String id) {
		AclTable obj = m_PsAclTable.get(id);
		if (null == obj) {
			return null;
		}
		return obj.getVo();
	}

	@Override
	public void put(AclTableVo vo) {
		String id = vo.id;
		AclTable obj = m_PsAclTable.get(id);
		if (null == obj) {
			synchronized (this) {
				obj = m_PsAclTable.get(id);
				if (null == obj) {
					obj = new AclTable(this, vo);
					return;
				}
			}
		}
		obj.updateVo(vo);
	}

	@Override
	public ResultPage<AclTableVo> startsWith(String prefix) {
		ResultPage<AclTable> rp = m_PsAclTable.startsWith(prefix);
		if (0 == rp.getCount()) {
			return ResultPageHelper.empty();
		}
		return new TransResultPage<AclTableVo, AclTable>(rp) {

			@Override
			protected AclTableVo trans(AclTable src) {
				return src.getVo();
			}
		};
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
		if (clazz == AclTable.class) {
			return (Persister<E>) m_PsAclTable;
		}
		return null;
	}

	@Override
	public void onReload(AclTable obj) {
		for (ReloadListener listener : m_Listeners) {
			listener.onReload(obj.getVo());
		}
	}

}
