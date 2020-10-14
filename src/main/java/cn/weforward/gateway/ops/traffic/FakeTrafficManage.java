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
package cn.weforward.gateway.ops.traffic;

import cn.weforward.protocol.Service;
import cn.weforward.protocol.gateway.vo.TrafficTableItemVo;
import cn.weforward.protocol.gateway.vo.TrafficTableItemWrap;
import cn.weforward.protocol.ops.traffic.TrafficTable;
import cn.weforward.protocol.ops.traffic.TrafficTableItem;

public class FakeTrafficManage implements TrafficManage {

	@Override
	public TrafficTableItem findTrafficRule(Service service) {
		TrafficTableItemVo vo = new TrafficTableItemVo(service.getNo(), service.getVersion());
		return new TrafficTableItemWrap(vo);
	}

	@Override
	public TrafficTable openTrafficTable(String serviceName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public TrafficTable getTrafficTable(String serviceName) {
		return null;
	}

	@Override
	public void registerListener(TrafficListener listener) {

	}

	@Override
	public void unregisterListener(TrafficListener listener) {

	}

}
