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
import cn.weforward.gateway.ops.right.RightTableVo;
import cn.weforward.gateway.ops.right.RightTableVoFactory;
import cn.weforward.gateway.plugin.mongodb.di.RightTableDi;

/**
 * <code>RightTableVoFactory</code>实现
 * 
 * @author zhangpengji
 *
 */
public class RightTableVoFactoryImpl implements RightTableVoFactory, RightTableDi {

	Persister<RightTable> m_PsRightTable;
	List<ReloadListener> m_Listeners;

	RightTableVoFactoryImpl(PersisterFactory factory) {
		m_PsRightTable = factory.createPersister(RightTable.class, this);
		//m_PsRightTable.setReloadEnabled(true);
		m_PsRightTable.addListener(new ChangeListener<RightTable>() {
			
			@Override
			public void onChange(NameItem type, String id, Supplier<RightTable> supplierdata) {
				if (ChangeListener.TYPE_NEW.id == type.id || ChangeListener.TYPE_UPDATE.id == type.id) {
					RightTable table = m_PsRightTable.get(id);
					table.reload(supplierdata.get());
					RightTableVoFactoryImpl.this.onReload(table);
				}
			}
		});
		m_Listeners = new CopyOnWriteArrayList<ReloadListener>();
	}

	@Override
	public RightTableVo get(String id) {
		RightTable obj = m_PsRightTable.get(id);
		if (null == obj) {
			return null;
		}
		return obj.getVo();
	}

	@Override
	public void put(RightTableVo vo) {
		String id = vo.id;
		RightTable obj = m_PsRightTable.get(id);
		if (null == obj) {
			synchronized (this) {
				obj = m_PsRightTable.get(id);
				if (null == obj) {
					obj = new RightTable(this, vo);
					return;
				}
			}
		}
		obj.updateVo(vo);
	}

	@Override
	public ResultPage<RightTableVo> startsWith(String prefix) {
		ResultPage<RightTable> rp = m_PsRightTable.startsWith(prefix);
		if (0 == rp.getCount()) {
			return ResultPageHelper.empty();
		}
		return new TransResultPage<RightTableVo, RightTable>(rp) {

			@Override
			protected RightTableVo trans(RightTable src) {
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
		if (clazz == RightTable.class) {
			return (Persister<E>) m_PsRightTable;
		}
		return null;
	}

	@Override
	public void onReload(RightTable obj) {
		for (ReloadListener listener : m_Listeners) {
			listener.onReload(obj.getVo());
		}
	}

}
