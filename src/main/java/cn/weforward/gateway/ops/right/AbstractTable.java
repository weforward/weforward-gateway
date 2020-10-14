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

import java.util.Collections;
import java.util.List;

import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.TransList;
import cn.weforward.gateway.ops.right.RightTableVo.RightTableItemVo;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.ops.AccessExt;
import cn.weforward.protocol.ops.secure.RightTableItem;

/**
 * 抽象RightTable
 * 
 * @author zhangpengji
 *
 */
public abstract class AbstractTable implements RightTableExt {
	/** 标识 - 允许调用 */
	public static final int MARK_ALLOW = 1;

	protected abstract List<RightTableItemVo> getItemVos();

	@Override
	public boolean isAllow(Access access) {
		List<RightTableItemVo> items = getItemVos();
		if (!ListUtil.isEmpty(items)) {
			for (RightTableItemVo item : items) {
				if (match(item, access)) {
					return isAllow(item);
				}
			}
		}
		return false;
	}

	static boolean isAllow(RightTableItemVo item) {
		return MARK_ALLOW == (MARK_ALLOW & item.marks);
	}

	static boolean match(RightTableItemVo item, Access acc) {
		if (StringUtil.isEmpty(item.accessId) && StringUtil.isEmpty(item.accessKind)
				&& StringUtil.isEmpty(item.accessGroup)) {
			// 空项（id、kind、group都未指定）的特殊匹配规则
			return null == acc;
		}
		if (!StringUtil.isEmpty(item.accessId) && (null == acc || !item.accessId.equals(acc.getAccessId()))) {
			return false;
		}
		if (!StringUtil.isEmpty(item.accessKind) && (null == acc || !item.accessKind.equals(acc.getKind()))) {
			return false;
		}
		if (!StringUtil.isEmpty(item.accessGroup)) {
			if (null == acc || !(acc instanceof AccessExt)) {
				return false;
			}
			AccessExt ext = (AccessExt) acc;
			if (!item.accessGroup.equals(ext.getGroupId())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public List<RightTableItem> getItems() {
		List<RightTableItemVo> vos = getItemVos();
		if (ListUtil.isEmpty(vos)) {
			return Collections.emptyList();
		}
		return new TransList<RightTableItem, RightTableItemVo>(vos) {

			@Override
			protected RightTableItem trans(RightTableItemVo src) {
				return new RightTableItemWrap(src);
			}
		};
	}
}
