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
package cn.weforward.gateway.ops;

import java.util.ArrayList;
import java.util.List;

import cn.weforward.common.Nameable;
import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;

public class TableItemHelper {

	/**
	 * 追加
	 * 
	 * @param <E>
	 * @param list
	 * @param index
	 * @param ele
	 */
	public static <E> List<E> append(List<E> list, E ele) {
		List<E> copy = new ArrayList<E>();
		if (!ListUtil.isEmpty(list)) {
			copy = new ArrayList<E>(list.size() + 1);
			copy.addAll(list);
		} else {
			copy = new ArrayList<E>();
		}
		copy.add(ele);
		return copy;
	}

	/**
	 * 在指定位置插入
	 * 
	 * @param <E>
	 * @param list
	 * @param index
	 * @param ele
	 */
	public static <E> List<E> insert(List<E> list, int index, E ele) {
		List<E> copy = new ArrayList<E>();
		if (!ListUtil.isEmpty(list)) {
			copy = new ArrayList<E>(list.size() + 1);
			copy.addAll(list);
		} else {
			copy = new ArrayList<E>();
		}
		if (index < 0) {
			index = 0;
		} else if (index > copy.size()) {
			index = copy.size();
		}
		copy.add(index, ele);
		return copy;
	}

	public static <E extends Nameable> List<E> replace(List<E> list, int index, E ele, NameChecker checker) {
		if (ListUtil.isEmpty(list) || index < 0 || index >= list.size()) {
			throw new OperationsException("无效index[" + index + "]");
		}
		E old = list.get(index);
		checker.check(old);
		List<E> copy = new ArrayList<E>(list);
		copy.set(index, ele);
		return copy;
	}

	public static <E> List<E> move(List<E> list, int from, int to) {
		if (ListUtil.isEmpty(list) || from < 0 || from >= list.size()) {
			throw new OperationsException("无效form[" + from + "]");
		}
		if (ListUtil.isEmpty(list) || to < 0 || to >= list.size()) {
			throw new OperationsException("无效to[" + to + "]");
		}
		if (from == to) {
			return list;
		}
		List<E> copy = new ArrayList<E>(list);
		int step = (from < to) ? 1 : -1;
		E item = copy.get(from);
		while (from != to) {
			int next = from + step;
			copy.set(from, copy.get(next));
			from = next;
		}
		copy.set(to, item);
		return copy;
	}

	public static <E extends Nameable> List<E> remove(List<E> list, int index, NameChecker checker) {
		if (ListUtil.isEmpty(list) || index < 0 || index >= list.size()) {
			throw new OperationsException("无效index[" + index + "]");
		}
		E old = list.get(index);
		checker.check(old);
		List<E> copy = new ArrayList<E>(list);
		copy.remove(index);
		return copy;
	}

	public static class NameChecker {

		final String name;

		public NameChecker(String name) {
			this.name = name;
		}

		public void check(Nameable ele) {
			if (!StringUtil.eq(ele.getName(), name)) {
				throw new OperationsException("Item名称不对应，请重新操作");
			}
		}

		public static NameChecker valueOf(String name) {
			if (StringUtil.isEmpty(name)) {
				return NAMELESS;
			}
			return new NameChecker(name);
		}
	}

	static final NameChecker NAMELESS = new NameChecker(null) {
		public void check(Nameable ele) {

		}
	};
}
