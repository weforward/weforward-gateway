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
import java.util.List;

import cn.weforward.common.util.ListUtil;
import cn.weforward.gateway.ops.OperationsException;
import cn.weforward.gateway.ops.right.RightTableVo.RightTableItemVo;
import cn.weforward.protocol.ops.secure.RightTableItem;

public class InternalRightTable extends AbstractTable {

	RightManageImpl m_RightManage;
	String m_Name;
	List<RightTableItemVo> m_ImmutableItems;
	RightTable m_VariableTable;

	public InternalRightTable(RightManageImpl rightManage, String name) {
		m_RightManage = rightManage;
		m_Name = name;
		m_ImmutableItems = new ArrayList<RightTableItemVo>();
	}

	@Override
	public String getName() {
		return m_Name;
	}

	public void addInternalItem(String accessId) {
		addInternalItem("内置", accessId, null, null);
	}

	public void addInternalItem(String name, String accessId, String accessKind, String accessGroup) {
		RightTableItemVo vo = new RightTableItemVo();
		vo.name = name;
		vo.description = "不可更改";
		vo.accessId = accessId;
		vo.accessKind = accessKind;
		vo.accessGroup = accessGroup;
		vo.marks = MARK_ALLOW;
		m_ImmutableItems.add(vo);
	}

	RightTable openVariableTable() {
		if (null == m_VariableTable) {
			synchronized (this) {
				if (null == m_VariableTable) {
					m_VariableTable = m_RightManage.openRightTableInner(m_Name);
				}
			}
		}
		return m_VariableTable;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected List<RightTableItemVo> getItemVos() {
		RightTable variable = openVariableTable();
		if (null == variable) {
			return m_ImmutableItems;
		}
		return ListUtil.union(m_ImmutableItems, variable.getItemVos());
	}

	@Override
	public void appendItem(RightTableItem item) {
		openVariableTable().appendItem(item);
	}

	@Override
	public void insertItem(RightTableItem item, int index) {
		if (index < m_ImmutableItems.size()) {
			throw new OperationsException("内置项不可更改。index:" + index);
		}
		index -= m_ImmutableItems.size();
		openVariableTable().insertItem(item, index);
	}

	@Override
	public void replaceItem(RightTableItem item, int index, String name) {
		if (index < m_ImmutableItems.size()) {
			throw new OperationsException("内置项不可更改。index:" + index);
		}
		index -= m_ImmutableItems.size();
		openVariableTable().replaceItem(item, index, name);
	}

	@Override
	public void moveItem(int from, int to) {
		if (from < m_ImmutableItems.size() || to < m_ImmutableItems.size()) {
			throw new OperationsException("内置项不可更改。from:" + from + ",to:" + to);
		}
		from -= m_ImmutableItems.size();
		to -= m_ImmutableItems.size();
		openVariableTable().moveItem(from, to);
	}

	@Override
	public void removeItem(int index, String name) {
		if (index < m_ImmutableItems.size()) {
			throw new OperationsException("内置项不可更改。index:" + index);
		}
		index -= m_ImmutableItems.size();
		openVariableTable().removeItem(index, name);
	}
	
	@Override
	public void setItems(List<RightTableItem> items) {
		openVariableTable().setItems(items);
	}

}
