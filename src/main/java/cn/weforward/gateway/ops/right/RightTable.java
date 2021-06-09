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
package cn.weforward.gateway.ops.right;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.weforward.common.util.ListUtil;
import cn.weforward.gateway.ops.TableItemHelper;
import cn.weforward.gateway.ops.TableItemHelper.NameChecker;
import cn.weforward.gateway.ops.right.RightTableVo.RightTableItemVo;
import cn.weforward.protocol.ops.secure.RightTableItem;

/**
 * <code>RightTable</code>实现
 * 
 * @author zhangpengji
 *
 */
public class RightTable extends AbstractTable {

	RightTableVo m_Vo;
	RightTableVoFactory m_Factory;

	RightTable(RightTableVoFactory factory, RightTableVo vo) {
		m_Factory = factory;
		m_Vo = vo;
	}

	public static String genId(String serviceName) {
		return serviceName;
	}

	RightTableVo getVo() {
		return m_Vo;
	}

	void updateVo(RightTableVo vo) {
		m_Vo = vo;
	}

	void markUpdate() {
		m_Factory.put(m_Vo);
	}

	@Override
	public String getName() {
		return getVo().name;
	}

	protected List<RightTableItemVo> getItemVos() {
		List<RightTableItemVo> items = getVo().items;
		if (null == items) {
			return Collections.emptyList();
		}
		return items;
	}

	@Override
	public synchronized void appendItem(RightTableItem item) {
		if (null == item) {
			throw new IllegalArgumentException("Item不能为空");
		}
		getVo().items = TableItemHelper.append(getVo().items, RightTableItemVo.valueOf(item));
		markUpdate();
	}

	@Override
	public synchronized void insertItem(RightTableItem item, int index) {
		if (null == item) {
			throw new IllegalArgumentException("Item不能为空");
		}
		getVo().items = TableItemHelper.insert(getVo().items, index, RightTableItemVo.valueOf(item));
		markUpdate();
	}

	@Override
	public synchronized void replaceItem(RightTableItem item, int index, String name) {
		getVo().items = TableItemHelper.replace(getVo().items, index, RightTableItemVo.valueOf(item),
				NameChecker.valueOf(name));
		markUpdate();
	}

	@Override
	public synchronized void moveItem(int from, int to) {
		getVo().items = TableItemHelper.move(getVo().items, from, to);
		markUpdate();
	}

	@Override
	public synchronized void removeItem(int index, String name) {
		getVo().items = TableItemHelper.remove(getVo().items, index, NameChecker.valueOf(name));
		markUpdate();
	}

	@Override
	public void setItems(List<RightTableItem> items) {
		List<RightTableItemVo> vos = Collections.emptyList();
		if (!ListUtil.isEmpty(items)) {
			vos = new ArrayList<>(items.size());
			for (RightTableItem item : items) {
				RightTableItemVo vo = RightTableItemVo.valueOf(item);
				vos.add(vo);
			}
		}
		getVo().items = vos;
		markUpdate();
	}
}
