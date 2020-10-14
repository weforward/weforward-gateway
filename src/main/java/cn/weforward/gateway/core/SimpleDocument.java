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
package cn.weforward.gateway.core;

import java.util.AbstractList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import cn.weforward.common.util.ListUtil;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.StatusCode;
import cn.weforward.protocol.doc.DocMethod;
import cn.weforward.protocol.doc.DocModify;
import cn.weforward.protocol.doc.DocObject;
import cn.weforward.protocol.doc.DocSpecialWord;
import cn.weforward.protocol.doc.DocStatusCode;
import cn.weforward.protocol.doc.ServiceDocument;
import cn.weforward.protocol.support.CommonServiceCodes;
import cn.weforward.protocol.support.doc.DocObjectVo;
import cn.weforward.protocol.support.doc.ServiceDocumentVo;

/**
 * 简单的微服务文档实现
 * 
 * @author zhangpengji
 *
 */
public class SimpleDocument implements ServiceDocument {

	protected ServiceDocumentVo m_Vo;
	protected Date m_CreateTime;
	protected boolean m_LoadFail;

	public SimpleDocument(ServiceDocumentVo vo) {
		m_Vo = vo;
		m_CreateTime = new Date();
	}

	public static SimpleDocument loadFail(Service service, String errorMsg) {
		return loadFail(service.getName(), service.getVersion(), errorMsg);
	}

	public static SimpleDocument loadFail(String name, String version, String errorMsg) {
		ServiceDocumentVo vo = new ServiceDocumentVo();
		vo.name = name;
		vo.version = version;
		vo.description = "加载失败：" + errorMsg;
		SimpleDocument doc = new SimpleDocument(vo);
		doc.m_LoadFail = true;
		return doc;
	}

	protected ServiceDocumentVo getVo() {
		return m_Vo;
	}

	public String getId() {
		return m_Vo.name + "-" + m_Vo.version;
	}

	public static String getId(Service service) {
		return service.getName() + "-" + service.getVersion();
	}

	@Override
	public String getName() {
		return getVo().getName();
	}

	@Override
	public String getVersion() {
		return getVo().getVersion();
	}

	@Override
	public String getDescription() {
		return getVo().getDescription();
	}

	@Override
	public List<DocModify> getModifies() {
		return getVo().getModifies();
	}

	@Override
	public List<DocMethod> getMethods() {
		return getVo().getMethods();
	}

	@Override
	public List<DocObject> getObjects() {
		final List<DocObject> objs = getVo().getObjects();
		// 追加固定的通用对象
		if (ListUtil.isEmpty(objs)) {
			return Collections.<DocObject>singletonList(DocObjectVo.RESULT_PAGE);
		}
		return new AbstractList<DocObject>() {

			@Override
			public DocObject get(int index) {
				if (0 == index) {
					return DocObjectVo.RESULT_PAGE;
				}
				return objs.get(index - 1);
			}

			@Override
			public int size() {
				return objs.size() + 1;
			}
		};
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<DocStatusCode> getStatusCodes() {
		final List<StatusCode> commonCodes = CommonServiceCodes.getCodes();
		List<DocStatusCode> commonDocCodes = new AbstractList<DocStatusCode>() {

			@Override
			public DocStatusCode get(int index) {
				StatusCode c = commonCodes.get(index);
				return new DocStatusCode() {

					@Override
					public String getMessage() {
						return c.msg;
					}

					@Override
					public int getCode() {
						return c.code;
					}
				};
			}

			@Override
			public int size() {
				return commonCodes.size();
			}
		};
		List<DocStatusCode> codes = getVo().getStatusCodes();
		if (ListUtil.isEmpty(codes)) {
			return commonDocCodes;
		}
		return ListUtil.union(commonDocCodes, codes);
	}

	@Override
	public int getMarks() {
		return getVo().getMarks();
	}

	@Override
	public boolean isMark(int mark) {
		return getVo().isMark(mark);
	}

	public boolean isTimeout() {
		if (isLoadFail()) {
			return (m_CreateTime.getTime() + 10 * 1000) < System.currentTimeMillis();
		}
		return (m_CreateTime.getTime() + 60 * 1000) < System.currentTimeMillis();
	}

	public DocModify getLastestModify() {
		List<DocModify> modifies = getModifies();
		if (null == modifies || modifies.isEmpty()) {
			return null;
		}
		return modifies.get(modifies.size() - 1);
	}

	public boolean isLoadFail() {
		return m_LoadFail;
	}

	public boolean isLatestThan(SimpleDocument other) {
		if (isLoadFail()) {
			return false;
		}
		if (other.isLoadFail()) {
			return true;
		}
		DocModify m1 = getLastestModify();
		if (null == m1 || null == m1.getDate()) {
			return false;
		}
		DocModify m2 = other.getLastestModify();
		if (null == m2 || null == m2.getDate()) {
			return true;
		}
		return m1.getDate().after(m2.getDate());
	}

	@Override
	public List<DocSpecialWord> getSpecialWords() {
		return getVo().getSpecialWords();
	}
}
