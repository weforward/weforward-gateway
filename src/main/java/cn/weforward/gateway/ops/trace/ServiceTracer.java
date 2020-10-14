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
package cn.weforward.gateway.ops.trace;

import cn.weforward.gateway.Pluginable;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.protocol.ops.trace.ServiceTraceToken;

/**
 * 微服务调用跟踪器
 * 
 * @author zhangpengji
 *
 */
public interface ServiceTracer extends Pluginable {

	/**
	 * 调用开始
	 * 
	 * @param preToken
	 *            由调用端带来令牌
	 * @param service
	 *            调用的微服务
	 * @return token生成失败，则返回null
	 */
	ServiceTraceToken onBegin(String preToken, ServiceInstance service);

	/**
	 * 调用结束
	 * 
	 * @param token
	 *            在onBegin返回的标识
	 */
	void onFinish(ServiceTraceToken token);

}
