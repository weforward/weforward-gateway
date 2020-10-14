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
package cn.weforward.gateway.ops.acl;

import java.util.Collections;
import java.util.List;

import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.TransList;
import cn.weforward.gateway.ops.TableItemHelper;
import cn.weforward.gateway.ops.TableItemHelper.NameChecker;
import cn.weforward.gateway.ops.acl.AclTableVo.AclTableItemVo;
import cn.weforward.gateway.ops.acl.AclTableVo.AclTableResourceVo;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.ops.AccessExt;
import cn.weforward.protocol.ops.secure.AclTableItem;
import cn.weforward.protocol.ops.secure.AclTableResource;

/**
 * <code>AclTable</code>实现
 * 
 * @author zhangpengji
 *
 */
public class AclTable implements cn.weforward.protocol.ops.secure.AclTable {

	AclTableVo m_Vo;
	AclTableVoFactory m_Factory;

	AclTable(AclTableVoFactory factory, AclTableVo vo) {
		m_Factory = factory;
		m_Vo = vo;
	}

	public static String genId(String serviceName) {
		return serviceName;
	}

	AclTableVo getVo() {
		return m_Vo;
	}

	void updateVo(AclTableVo vo) {
		m_Vo = vo;
	}

	void markUpdate() {
		m_Factory.put(m_Vo);
	}

	@Override
	public String getName() {
		return getVo().name;
	}

	public int findResourceRight(Access access, String resId) {
		List<AclTableItemVo> items = getVo().items;
		if (ListUtil.isEmpty(items)) {
			return 0;
		}
		for (AclTableItemVo i : items) {
			if (match(i, access)) {
				return findRight(i, resId);
			}
		}
		return 0;
	}

	static boolean match(AclTableItemVo item, Access acc) {
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
			return false;
		}
		return true;
	}

	static int findRight(AclTableItemVo item, String resId) {
		List<AclTableResourceVo> resources = item.resources;
		if (null == resources || resources.isEmpty()) {
			return 0;
		}
		for (AclTableResourceVo r : resources) {
			if (match(r, resId)) {
				return r.right;
			}
		}
		return 0;
	}

	static boolean match(AclTableResourceVo res, String resId) {
		if (StringUtil.isEmpty(resId)) {
			return false;
		}
		if (AclTableResource.MATCH_TYPE_REGULAR == res.matchType) {
			return res.getRegexPattern().matcher(resId).matches();
		} else {
			return res.getAntPattern().match(resId);
		}
	}

	@Override
	public List<AclTableItem> getItems() {
		List<AclTableItemVo> vos = getVo().items;
		if (ListUtil.isEmpty(vos)) {
			return Collections.emptyList();
		}
		return new TransList<AclTableItem, AclTableItemVo>(vos) {

			@Override
			protected AclTableItem trans(AclTableItemVo src) {
				return new AclTableItemWrap(src);
			}
		};
	}

	@Override
	public void appendItem(AclTableItem item) {
		if (null == item) {
			throw new IllegalArgumentException("Item不能为空");
		}
		getVo().items = TableItemHelper.append(getVo().items, AclTableItemVo.valueOf(item));
		markUpdate();
	}

	@Override
	public void insertItem(AclTableItem item, int index) {
		if (null == item) {
			throw new IllegalArgumentException("Item不能为空");
		}
		getVo().items = TableItemHelper.insert(getVo().items, index, AclTableItemVo.valueOf(item));
		markUpdate();
	}

	@Override
	public void replaceItem(AclTableItem item, int index, String name) {
		getVo().items = TableItemHelper.replace(getVo().items, index, AclTableItemVo.valueOf(item),
				NameChecker.valueOf(name));
		markUpdate();
	}

	@Override
	public void moveItem(int from, int to) {
		getVo().items = TableItemHelper.move(getVo().items, from, to);
		markUpdate();
	}

	@Override
	public void removeItem(int index, String name) {
		getVo().items = TableItemHelper.remove(getVo().items, index, NameChecker.valueOf(name));
		markUpdate();
	}

}
