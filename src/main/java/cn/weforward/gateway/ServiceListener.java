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

/**
 * 微服务监听器
 * 
 * @author zhangpengji
 *
 */
public interface ServiceListener extends Pluginable {

	/**
	 * 当注册时
	 * 
	 * @param service
	 * @param foreign
	 *            为true时，表示此微服务在其他网关注册
	 */
	void onServiceRegister(ServiceInstance service, boolean foreign);

	/**
	 * 当注销时
	 * 
	 * @param service
	 * @param foreign
	 *            为true时，表示此微服务在其他网关注销
	 */
	void onServiceUnregister(ServiceInstance service, boolean foreign);

	/**
	 * 当心跳超时
	 * 
	 * @param service
	 */
	void onServiceTimeout(ServiceInstance service);

	/**
	 * 当不可用（失败过多）时
	 * 
	 * @param service
	 */
	void onServiceUnavailable(ServiceInstance service);

	/**
	 * 当过载（并发过多）时
	 * 
	 * @param service
	 */
	void onServiceOverload(ServiceInstance service);
}
