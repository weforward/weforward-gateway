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
package cn.weforward.gateway.ops.access.system;

import java.security.NoSuchAlgorithmException;

import javax.crypto.KeyGenerator;

import cn.weforward.common.util.StringUtil;

/**
 * 主密钥
 * 
 * @author zhangpengji
 *
 */
public class MasterKey {

	/** 标识 - 已失效 */
	public static final int MARK_INVALID = 1;

	MasterKeyVoFactory m_VoFactory;
	MasterKeyVo m_Vo;

	MasterKey(MasterKeyVoFactory factory, String id, String groupId) throws NoSuchAlgorithmException {
		if (StringUtil.isEmpty(id) || StringUtil.isEmpty(groupId)) {
			throw new IllegalArgumentException("id,group不能为空");
		}
		m_VoFactory = factory;
		m_Vo = new MasterKeyVo();
		m_Vo.id = id;
		m_Vo.groupId = groupId;
		KeyGenerator kgen = KeyGenerator.getInstance("AES");
		kgen.init(256);
		m_Vo.key = kgen.generateKey().getEncoded();
		markUpdate();
	}

	MasterKey(MasterKeyVoFactory factory, MasterKeyVo vo) {
		m_VoFactory = factory;
		m_Vo = vo;
	}

	String getId() {
		return m_Vo.id;
	}

	String getGroupId() {
		return m_Vo.groupId;
	}
	
	byte[] getKey() {
		return m_Vo.key;
	}

	void update(MasterKeyVo vo) {
		m_Vo = vo;
	}

	void setValid(boolean valid) {
		if (valid) {
			m_Vo.marks &= (-MARK_INVALID);
		} else {
			m_Vo.marks |= MARK_INVALID;
		}
	}

	boolean isValid() {
		return !isMark(MARK_INVALID);
	}

	boolean isMark(int mark) {
		return mark == (mark & m_Vo.marks);
	}

	protected void markUpdate() {
		m_VoFactory.put(m_Vo);
	}
}
