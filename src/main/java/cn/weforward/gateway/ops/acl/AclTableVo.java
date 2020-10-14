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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import cn.weforward.common.Nameable;
import cn.weforward.common.util.AntPathPattern;
import cn.weforward.protocol.ops.secure.AclTableItem;
import cn.weforward.protocol.ops.secure.AclTableResource;

/**
 * <code>AclTable</code>'s Vo
 * 
 * @author zhangpengji
 *
 */
public class AclTableVo {

	/** 唯一标识 */
	@Resource
	public String id;
	/** 服务名 */
	@Resource
	public String name;
	/** 项列表 */
	@Resource
	public List<AclTableItemVo> items;

	public AclTableVo() {

	}

	public static class AclTableItemVo implements Nameable {

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
		@Resource
		public List<AclTableResourceVo> resources;

		public AclTableItemVo() {

		}

		public AclTableItemVo(AclTableItem item) {
			this.accessId = item.getAccessId();
			this.accessKind = item.getAccessKind();
			this.accessGroup = item.getAccessGroup();
			this.name = item.getName();
			this.description = item.getDescription();
			List<AclTableResource> reses = item.getResources();
			if (reses.size() > 0) {
				List<AclTableResourceVo> vos = new ArrayList<AclTableResourceVo>(reses.size());
				for (AclTableResource res : reses) {
					AclTableResourceVo vo = AclTableResourceVo.valueOf(res);
					vos.add(vo);
				}
				this.resources = vos;
			}
		}

		public static AclTableItemVo valueOf(AclTableItem item) {
			if (null == item) {
				return null;
			}
			return new AclTableItemVo(item);
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

		public List<AclTableResourceVo> getResources() {
			return resources;
		}

		public void setResources(List<AclTableResourceVo> resources) {
			this.resources = resources;
		}

	}

	public static class AclTableResourceVo {

		/** 资源id */
		@Resource
		public String pattern;
		/** 权限 */
		@Resource
		public int right;
		/** 匹配模式 */
		@Resource
		public int matchType;

		private AntPathPattern m_AntPattern;
		private Pattern m_RegexPattern;

		public AclTableResourceVo() {

		}

		public AclTableResourceVo(AclTableResource res) {
			this.pattern = res.getPattern();
			this.right = res.getRight();
			this.matchType = res.getMatchType();
		}

		public static AclTableResourceVo valueOf(AclTableResource res) {
			if (null == res) {
				return null;
			}
			return new AclTableResourceVo(res);
		}

		public String getPattern() {
			return pattern;
		}

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}

		public int getRight() {
			return right;
		}

		public void setRight(int right) {
			this.right = right;
		}

		public int getMatchType() {
			return matchType;
		}

		public void setMatchType(int matchType) {
			this.matchType = matchType;
		}

		Pattern getRegexPattern() {
			if (null == m_RegexPattern) {
				m_RegexPattern = Pattern.compile(this.pattern);
			}
			return m_RegexPattern;
		}

		AntPathPattern getAntPattern() {
			if (null == m_AntPattern) {
				m_AntPattern = AntPathPattern.valueOf(this.pattern);
			}
			return m_AntPattern;
		}
	}
}
