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
package cn.weforward.gateway.plugin.alarm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.ServiceListener;

/**
 * 假的报警器
 * 
 * @author zhangpengji
 *
 */
public class FakeAlarmer implements ServiceListener {
	static final Logger _Logger = LoggerFactory.getLogger(FakeAlarmer.class);

	@Override
	public void onServiceRegister(ServiceInstance service, boolean foreign) {

	}

	@Override
	public void onServiceUnregister(ServiceInstance service, boolean foreign) {

	}

	@Override
	public void onServiceTimeout(ServiceInstance service) {
		_Logger.error("微服务[" + service.toStringNameNo() + "]心跳超时");
	}

	@Override
	public void onServiceUnavailable(ServiceInstance service) {
		_Logger.error("微服务[" + service.toStringNameNo() + "]不可用");
	}

	@Override
	public void onServiceOverload(ServiceInstance service) {
		_Logger.error("微服务[" + service.toStringNameNo() + "]过载");
	}

}
