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

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import cn.weforward.common.util.StringUtil;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.Request;
import cn.weforward.protocol.Response;
import cn.weforward.protocol.client.ServiceInvoker;
import cn.weforward.protocol.client.ServiceInvokerFactory;
import cn.weforward.protocol.client.ext.RequestInvokeObject;

public class TestClient {

	String accessId = "H-093dc2130128-093dc2130128";
	String accessKey = "513c55c44ada322a9f8e86f580fbad20480034d8b5f32577bfb307cc842bced1";
	String url = "http://127.0.0.1:5661";

	// @Before
	// public void login() {
	// List<String> urls = new ArrayList<>();
	// urls.add(hyServiceUrl + "/zuoche_user");
	// ServiceInvoker invoker = ServiceInvokerFactory.create(urls, null, null);
	// invoker.setAuthType(HyHeader.AUTH_TYPE_NONE);
	// RequestInvokeObject invoke = new RequestInvokeObject("login");
	// invoke.putParam("user_name", "manager");
	// invoke.putParam("password", "manager");
	// HyResponse resp = invoker.invoke(invoke.toHyDtObject());
	// HyDtObject result = resp.getServiceResult();
	// HyDtObject resultContent = result.getObject("content");
	// info.id = resultContent.getString("access_id").value();
	// info.key = resultContent.getString("access_key").value();
	// }

	// @After
	// public void logout() {
	// List<String> urls = new ArrayList<>();
	// urls.add(hyServiceUrl + "/zuoche_user");
	// ServiceInvoker invoker = ServiceInvokerFactory.create(urls, info.id,
	// info.key);
	// invoker.setAuthType(HyHeader.AUTH_TYPE_NONE);
	// RequestInvokeObject invoke = new RequestInvokeObject("logout");
	// invoker.invoke(invoke.toHyDtObject());
	// }

	@Test
	public void main() throws Exception {
		testCall();
		// testTag();
		// testBalance();
		// testTrace();

	}

	// @Before
	// public void openAccess() throws AccessInjectionException {
	// String accessId = "H-0947f4f50120-0947f4f50220";
	// String accessKey =
	// "95644a4823d9022671bbbd4151d6b20426e71b3eaeb4a2827f9a258e02e8582c";
	// List<String> urls = Arrays.asList(hyApiUrl + "/hy_access_injection");
	// accessInjection = new RemoteAccessInject(urls, accessId, accessKey);
	// // info = ai.openAccess(HyAccess.KIND_USER, "007", Misc.md5Hash("007"));
	// info = accessInjection.openAccess(HyAccess.KIND_USER_SESSION,
	// Misc.toHex64(System.currentTimeMillis()));
	// }

	// @After
	// public void closeAccess() throws AccessInjectionException {
	// accessInjection.closeAccess(info.id);
	// }

	void testCall() {
		List<String> urls = new ArrayList<>();
		urls.add(url + "/test");
		ServiceInvoker invoker = ServiceInvokerFactory.create(urls, accessId, accessKey);
		if (StringUtil.isEmpty(accessId)) {
			invoker.setAuthType(Header.AUTH_TYPE_NONE);
		} else {
			invoker.setAuthType(Header.AUTH_TYPE_SHA2);
		}
		RequestInvokeObject invoke = new RequestInvokeObject("say_hello");
		invoke.putParam("name", "浩宁云");
		/*
		 * File file = new File("1.png"); FileInputStream in = new
		 * FileInputStream(file); BytesOutputStream bos = new BytesOutputStream(in);
		 * byte[] data = bos.getBytes().fit(); bos.close(); String fileStr = new
		 * String(data, "iso-8859-1"); params.put("file", fileStr);
		 */
		long start = System.currentTimeMillis();
		Request req = invoker.createRequest(invoke.toDtObject());
		// req.setResourceId("Config$123-ab");
		// req.setTenant("tianhe");
		req.setWaitTimeout(3600);
		Response resp = invoker.invoke(req);
		System.out.println("use:" + (System.currentTimeMillis() - start));
		if (0 != resp.getResponseCode()) {
			System.out.println(resp.getResponseCode() + "/" + resp.getResponseMsg());
		}
		System.out.println(resp.getServiceResult());
		System.out.println(resp.getResourceUrl());
	}

	// void testTag() throws Exception {
	// List<String> urls = new ArrayList<>();
	// urls.add(hyServiceUrl + "/test");
	// ServiceInvoker invoker = ServiceInvokerFactory.create(urls, info.id,
	// info.key);
	// ((DefaultServiceInvoker) invoker).setAuthType(HyHeader.AUTH_TYPE_NONE);
	// RequestInvokeObject invoke = new RequestInvokeObject("say_hello");
	// invoke.putParam("name", "honin");
	//
	// String tag = null;
	// while (true) {
	// HyRequest req = invoker.createRequest(invoke.toHyDtObject());
	// req.getHeader().setTag(tag);
	// HyResponse resp = invoker.invoke(req);
	// tag = resp.getHeader().getTag();
	// System.out.println("tag: " + tag);
	// Thread.sleep(1000);
	// }
	// }

	// void testBalance() throws Exception {
	// List<String> urls = new ArrayList<>();
	// urls.add(hyServiceUrl + "/test");
	// ServiceInvoker invoker = ServiceInvokerFactory.create(urls, info.id,
	// info.key);
	// ((DefaultServiceInvoker) invoker).setAuthType(HyHeader.AUTH_TYPE_NONE);
	// RequestInvokeObject invoke = new RequestInvokeObject("say_hello");
	// invoke.putParam("name", "honin");
	//
	// while (true) {
	// HyRequest req = invoker.createRequest(invoke.toHyDtObject());
	// HyResponse resp = invoker.invoke(req);
	// System.out.println("tag: " + resp.getHeader().getTag());
	// Thread.sleep(1000);
	// }
	// }

	// void testTrace() {
	// List<String> urls = new ArrayList<>();
	// urls.add("http://127.0.0.1:5661/test");
	// ServiceInvoker invoker = ServiceInvokerFactory.create(urls, info.id,
	// info.key);
	// invoker.setAuthType(HyHeader.AUTH_TYPE_SHA2);
	// RequestInvokeObject invoke = new RequestInvokeObject("trace");
	// HyRequest req = invoker.createRequest(invoke.toHyDtObject());
	// HyResponse resp = invoker.invoke(req);
	// if (0 != resp.getResponseCode()) {
	// System.out.println(resp.getResponseCode() + "/" + resp.getResponseMsg());
	// }
	// System.out.println(resp.getServiceResult());
	// }
}
