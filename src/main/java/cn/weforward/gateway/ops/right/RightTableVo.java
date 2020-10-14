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

import java.util.List;

import javax.annotation.Resource;

import cn.weforward.common.Nameable;
import cn.weforward.protocol.ops.secure.RightTableItem;

/**
 * <code>RightTable</code>'s Vo
 * 
 * @author zhangpengji
 *
 */
public class RightTableVo {

	/** 唯一标识 */
	@Resource
	public String id;
	/** 服务名 */
	@Resource
	public String name;
	/** 项列表 */
	@Resource
	public List<RightTableItemVo> items;

	public RightTableVo() {

	}

	public RightTableVo(String id, String name) {
		this.id = id;
		this.name = name;
	}

	public static class RightTableItemVo implements Nameable {

		/** access id */
		@Resource
		public String accessId;
		/** kind of access id */
		@Resource
		public String accessKind;
		/** group of access id */
		@Resource
		public String accessGroup;
		/** 项的名称 */
		@Resource
		public String name;
		/** 项的描述 */
		@Resource
		public String description;
		/** 标识 */
		@Resource
		public int marks;

		public RightTableItemVo() {

		}

		public RightTableItemVo(RightTableItem item) {
			this.name = item.getName();
			this.description = item.getDescription();
			this.accessId = item.getAccessId();
			this.accessKind = item.getAccessKind();
			this.accessGroup = item.getAccessGroup();
			if (item.isAllow()) {
				this.marks = RightTable.MARK_ALLOW;
			}
		}

		public static RightTableItemVo valueOf(RightTableItem item) {
			if (null == item) {
				return null;
			}
			return new RightTableItemVo(item);
		}

		public String getAccessId() {
			return accessId;
		}

		public void setAccessId(String accessId) {
			this.accessId = accessId;
		}

		public String getAccessKind() {
			return accessKind;
		}

		public void setAccessKind(String accessKind) {
			this.accessKind = accessKind;
		}

		public String getAccessGroup() {
			return accessGroup;
		}

		public void setAccessGroup(String accessGroup) {
			this.accessGroup = accessGroup;
		}

		@Override
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public int getMarks() {
			return marks;
		}

		public void setMarks(int marks) {
			this.marks = marks;
		}
	}
}
