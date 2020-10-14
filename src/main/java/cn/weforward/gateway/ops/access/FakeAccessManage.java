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
package cn.weforward.gateway.ops.access;

import java.util.Collections;
import java.util.List;

import cn.weforward.common.ResultPage;
import cn.weforward.common.crypto.Base64;
import cn.weforward.common.crypto.Hex;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.ops.AccessExt;

public class FakeAccessManage implements AccessManage {

	static final String ID = "H-093dc2130128-093dc2130128";
	static final String KEY = "513c55c44ada322a9f8e86f580fbad20480034d8b5f32577bfb307cc842bced1";

	static final AccessExt SINGLE = new AccessExt() {

		@Override
		public boolean isValid() {
			return true;
		}

		@Override
		public String getKind() {
			return Access.KIND_SERVICE;
		}

		@Override
		public byte[] getAccessKey() {
			return Hex.decode(getAccessKeyHex());
		}

		@Override
		public String getAccessId() {
			return ID;
		}

		@Override
		public String getGroupId() {
			return null;
		}

		@Override
		public String getSummary() {
			return null;
		}

		@Override
		public void setSummary(String summary) {

		}

		@Override
		public String getAccessKeyHex() {
			return KEY;
		}
		
		@Override
		public String getAccessKeyBase64() {
			return Base64.encode(getAccessKey());
		}

		@Override
		public void setValid(boolean valid) {
		}

		@Override
		public String getTenant() {
			return null;
		}

		@Override
		public String getOpenid() {
			return null;
		}
	};

	@Override
	public Access getValidAccess(String accessId) {
		return getAccess(accessId);
	}

	@Override
	public AccessProvider registerProvider(AccessProvider provider) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean unregisterProvider(AccessProvider provider) {
		throw new UnsupportedOperationException();
	}

	@Override
	public AccessExt createAccess(String kind, String groupId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public AccessExt getAccess(String id) {
		if (!SINGLE.getAccessId().equals(id)) {
			return null;
		}
		return SINGLE;
	}

	@Override
	public ResultPage<AccessExt> listAccess(String kind, String groupId, String keyword) {
		return ResultPageHelper.singleton(SINGLE);
	}

	@Override
	public Access getInternalAccess() {
		return SINGLE;
	}

	@Override
	public List<String> listAccessGroup(String kind) {
		// return Collections.singletonList(SINGLE.getGroupId());
		return Collections.emptyList();
	}

}
