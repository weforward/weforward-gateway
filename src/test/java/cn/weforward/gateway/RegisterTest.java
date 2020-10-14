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

import cn.weforward.protocol.ext.ServiceRuntime;
import cn.weforward.protocol.gateway.http.HttpServiceRegister;
import cn.weforward.protocol.gateway.vo.ServiceVo;

public class RegisterTest {

	public RegisterTest() {
		System.setProperty("https.protocols", "TLSv1,TLSv1.1,TLSv1.2");

	}

	@Test
	public void main() throws Exception {
		String url = "http://127.0.0.1:5661/";
		String id = "H-0947f4f50120-09488c7c0120";
		String key = "fbc9214442dd93625b62ec02098ba91b583d2fece2239bd47e7fee2611fe6192";

		HttpServiceRegister register = new HttpServiceRegister(url, id, key);
		ServiceRuntime runtime = new ServiceRuntime();
		runtime.startTime = System.currentTimeMillis();
		ServiceVo service = new ServiceVo();
		service.name = "test";
		service.no = "x0ff";
		service.domain = "127.0.0.1";
		service.port = 15000;
		register.registerService(service, runtime);
	}

}
