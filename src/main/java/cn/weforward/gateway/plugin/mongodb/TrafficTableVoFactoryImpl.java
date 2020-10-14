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
import cn.weforward.gateway.ops.traffic.TrafficTableVo;
import cn.weforward.gateway.ops.traffic.TrafficTableVoFactory;
import cn.weforward.gateway.plugin.mongodb.di.TrafficTableDi;

/**
 * <code>TrafficTableVoFactory</code>实现
 * 
 * @author zhangpengji
 *
 */
public class TrafficTableVoFactoryImpl implements TrafficTableVoFactory, TrafficTableDi {

	Persister<TrafficTable> m_PsTrafficTable;
	List<ReloadListener> m_Listeners;

	TrafficTableVoFactoryImpl(PersisterFactory factory) {
		m_PsTrafficTable = factory.createPersister(TrafficTable.class, this);
		//m_PsTrafficTable.setReloadEnabled(true);
		m_PsTrafficTable.addListener(new ChangeListener<TrafficTable>() {
			
			@Override
			public void onChange(NameItem type, String id, Supplier<TrafficTable> supplierdata) {
				if (ChangeListener.TYPE_NEW.id == type.id || ChangeListener.TYPE_UPDATE.id == type.id) {
					TrafficTable table = m_PsTrafficTable.get(id);
					table.reload(supplierdata.get());
					TrafficTableVoFactoryImpl.this.onReload(table);
				}
			}
		});
		m_Listeners = new CopyOnWriteArrayList<ReloadListener>();
	}

	@Override
	public TrafficTableVo get(String id) {
		TrafficTable obj = m_PsTrafficTable.get(id);
		if (null == obj) {
			return null;
		}
		return obj.getVo();
	}

	@Override
	public void put(TrafficTableVo vo) {
		String id = vo.id;
		TrafficTable obj = m_PsTrafficTable.get(id);
		if (null == obj) {
			synchronized (this) {
				obj = m_PsTrafficTable.get(id);
				if (null == obj) {
					obj = new TrafficTable(this, vo);
					return;
				}
			}
		}
		obj.updateVo(vo);
	}

	@Override
	public ResultPage<TrafficTableVo> startsWith(String prefix) {
		ResultPage<TrafficTable> rp = m_PsTrafficTable.startsWith(prefix);
		if (0 == rp.getCount()) {
			return ResultPageHelper.empty();
		}
		return new TransResultPage<TrafficTableVo, TrafficTable>(rp) {

			@Override
			protected TrafficTableVo trans(TrafficTable src) {
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
		if (clazz == TrafficTable.class) {
			return (Persister<E>) m_PsTrafficTable;
		}
		return null;
	}

	@Override
	public void onReload(TrafficTable obj) {
		for (ReloadListener listener : m_Listeners) {
			listener.onReload(obj.getVo());
		}
	}

}
