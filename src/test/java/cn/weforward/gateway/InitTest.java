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
package cn.weforward.gateway;

import org.junit.Test;

import cn.weforward.common.util.StringUtil;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.gateway.http.HttpKeeper;
import cn.weforward.protocol.ops.AccessExt;

public class InitTest {

	public InitTest() {
		System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");

	}

	@Test
	public void main() throws Exception {
		String url = "http://127.0.0.1:5661/";
		String id = "";
		String key = "";
		HttpKeeper keeper = new HttpKeeper(url, id, key);
		if (StringUtil.isEmpty(id) && StringUtil.isEmpty(key)) {
			keeper.getInvoker().setAuthType(Header.AUTH_TYPE_NONE);
		}
		createAccess(keeper, "H", "default", "devops");
	}

	public void createAccess(HttpKeeper keeper, String kind, String group, String summary) {
		AccessExt acc = keeper.createAccess(kind, group, summary);
		System.out.println(acc.getAccessId() + "," + acc.getAccessKeyHex() + "," + acc.getSummary());
	}

}
