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
package cn.weforward.gateway.ops.traffic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.TransList;
import cn.weforward.gateway.ops.TableItemHelper;
import cn.weforward.gateway.ops.TableItemHelper.NameChecker;
import cn.weforward.gateway.ops.traffic.TrafficTableVo.TrafficTableItemVo;
import cn.weforward.protocol.ops.traffic.TrafficTableItem;

/**
 * <code>TrafficTable</code>实现
 * 
 * @author zhangpengji
 *
 */
public class TrafficTable implements cn.weforward.protocol.ops.traffic.TrafficTable {

	TrafficTableVo m_Vo;
	TrafficTableVoFactory m_Factory;
	TrafficManageImpl m_Manage;

	TrafficTable(TrafficManageImpl manage, TrafficTableVoFactory factory, TrafficTableVo vo) {
		m_Manage = manage;
		m_Factory = factory;
		m_Vo = vo;
	}

	public static String genId(String serviceName) {
		return serviceName;
	}

	TrafficTableVo getVo() {
		return m_Vo;
	}

	void updateVo(TrafficTableVo vo) {
		m_Vo = vo;
		m_Manage.onTrafficRuleChange(this);
	}

	void markUpdate() {
		m_Factory.put(m_Vo);
	}

	@Override
	public String getName() {
		return getVo().name;
	}

	public TrafficTableItem findRule(String serviceNo, String serviceVersion) {
		List<TrafficTableItemVo> items = getVo().items;
		if (ListUtil.isEmpty(items)) {
			// 默认使用轮询方式
			return TrafficTableItem.DEFAULT;
		}
		for (TrafficTableItemVo item : items) {
			if (match(item, serviceNo, serviceVersion)) {
				return new TrafficTableItemWrap(item);
			}
		}
		return null;
	}

	public static boolean match(TrafficTableItemVo item, String no, String version) {
		if ((StringUtil.isEmpty(item.serviceNo) || item.serviceNo.equals(no))
				&& (StringUtil.isEmpty(item.serviceVersion) || item.serviceVersion.equals(version))) {
			return true;
		}
		return false;
	}

	@Override
	public List<TrafficTableItem> getItems() {
		List<TrafficTableItemVo> items = getVo().items;
		if (ListUtil.isEmpty(items)) {
			return Collections.emptyList();
		}
		return new TransList<TrafficTableItem, TrafficTableItemVo>(items) {

			@Override
			protected TrafficTableItem trans(TrafficTableItemVo src) {
				return new TrafficTableItemWrap(src);
			}
		};
	}

	@Override
	public void appendItem(TrafficTableItem item) {
		if (null == item) {
			throw new IllegalArgumentException("Item不能为空");
		}
		getVo().items = TableItemHelper.append(getVo().items, TrafficTableItemVo.valueOf(item));
		markUpdate();
		m_Manage.onTrafficRuleChange(this);
	}

	@Override
	public void insertItem(TrafficTableItem item, int index) {
		if (null == item) {
			throw new IllegalArgumentException("Item不能为空");
		}
		getVo().items = TableItemHelper.insert(getVo().items, index, TrafficTableItemVo.valueOf(item));
		markUpdate();
		m_Manage.onTrafficRuleChange(this);
	}

	@Override
	public void replaceItem(TrafficTableItem item, int index, String name) {
		getVo().items = TableItemHelper.replace(getVo().items, index, TrafficTableItemVo.valueOf(item),
				NameChecker.valueOf(name));
		markUpdate();
		m_Manage.onTrafficRuleChange(this);
	}

	@Override
	public void moveItem(int from, int to) {
		getVo().items = TableItemHelper.move(getVo().items, from, to);
		markUpdate();
		m_Manage.onTrafficRuleChange(this);
	}

	@Override
	public void removeItem(int index, String name) {
		getVo().items = TableItemHelper.remove(getVo().items, index, NameChecker.valueOf(name));
		markUpdate();
		m_Manage.onTrafficRuleChange(this);
	}

	@Override
	public void setItems(List<TrafficTableItem> items) {
		List<TrafficTableItemVo> vos = Collections.emptyList();
		if (!ListUtil.isEmpty(items)) {
			vos = new ArrayList<>(items.size());
			for (TrafficTableItem item : items) {
				TrafficTableItemVo vo = TrafficTableItemVo.valueOf(item);
				vos.add(vo);
			}
		}
		getVo().items = vos;
		markUpdate();
	}
}
