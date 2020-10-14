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
import java.util.Collections;
import java.util.List;

import cn.weforward.common.ResultPage;
import cn.weforward.common.crypto.Base64;
import cn.weforward.common.crypto.Hex;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.gateway.ops.access.AccessManage;
import cn.weforward.gateway.ops.access.AccessProvider;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.ops.AccessExt;

/**
 * {@linkplain Access#KIND_GATEWAY}的Access提供者
 * 
 * @author zhangpengji
 *
 */
public class GatewayAccessProvider implements AccessProvider {

	AccessExt m_InternalAccess;

	public GatewayAccessProvider() {

	}

	public void setInternalAccessSecret(String secret) throws NoSuchAlgorithmException {
		m_InternalAccess = new InternalAccess(secret);
	}

	public void setAccessManage(AccessManage am) {
		am.registerProvider(this);
	}

	@Override
	public String getKind() {
		return Access.KIND_GATEWAY;
	}

	@Override
	public AccessExt createAccess(String groupId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public AccessExt getAccess(String id) {
		if (null != m_InternalAccess && m_InternalAccess.getAccessId().equals(id)) {
			return m_InternalAccess;
		}
		return null;
	}

	@Override
	public ResultPage<AccessExt> listAccess(String groupId, String keyword) {
		if (null != m_InternalAccess) {
			return ResultPageHelper.singleton(m_InternalAccess);
		}
		return ResultPageHelper.empty();
	}
	
	@Override
	public List<String> listAccessGroup() {
		return Collections.emptyList();
	}

	static class InternalAccess implements AccessExt {

		final byte[] m_AccessKey;
		String m_AccessKeyHex;
		String m_AccessKeyBase64;

		InternalAccess(String secret) throws NoSuchAlgorithmException {
			m_AccessKey = Access.Helper.secretToAccessKey(secret);
		}

		@Override
		public String getAccessId() {
			return AccessExt.GATEWAY_INTERNAL_ACCESS_ID;
		}

		@Override
		public byte[] getAccessKey() {
			return m_AccessKey;
		}

		@Override
		public String getKind() {
			return Access.KIND_GATEWAY;
		}

		@Override
		public boolean isValid() {
			return true;
		}

		@Override
		public String getGroupId() {
			return null;
		}

		@Override
		public String getSummary() {
			return "gateway internal access";
		}

		@Override
		public void setSummary(String summary) {

		}

		@Override
		public String getAccessKeyHex() {
			if(null == m_AccessKeyHex) {
				m_AccessKeyHex = Hex.encode(m_AccessKey);
			}
			return m_AccessKeyHex;
		}
		
		@Override
		public String getAccessKeyBase64() {
			if(null == m_AccessKeyBase64) {
				m_AccessKeyBase64 = Base64.encode(m_AccessKey);
			}
			return m_AccessKeyBase64;
		}

		@Override
		public void setValid(boolean valid) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getTenant() {
			return null;
		}

		@Override
		public String getOpenid() {
			return null;
		}

	}
}
