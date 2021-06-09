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

import java.util.List;

import javax.annotation.Resource;

import cn.weforward.common.Nameable;
import cn.weforward.protocol.ops.traffic.TrafficTableItem;

/**
 * <code>TrafficTable</code>'s Vo
 * 
 * @author zhangpengji
 *
 */
public class TrafficTableVo {

	/** 唯一标识 */
	@Resource
	public String id;
	/** 服务名 */
	@Resource
	public String name;
	/** 项列表 */
	@Resource
	public List<TrafficTableItemVo> items;

	public TrafficTableVo() {

	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<TrafficTableItemVo> getItems() {
		return items;
	}

	public void setItems(List<TrafficTableItemVo> items) {
		this.items = items;
	}

	public static class TrafficTableItemVo implements Nameable {
		@Resource
		public String name;
		@Resource
		public String serviceNo;
		@Resource
		public String serviceVersion;
		@Resource
		public int weight;
		@Resource
		public int maxFails;
		@Resource
		public int failTimeout;
		@Resource
		public int maxConcurrent;
		@Resource
		public int connectTimeout;
		@Resource
		public int readTimeout;

		public TrafficTableItemVo() {

		}

		public TrafficTableItemVo(TrafficTableItem item) {
			this.serviceNo = item.getServiceNo();
			this.serviceVersion = item.getServiceVersion();
			this.name = item.getName();
			this.weight = item.getWeight();
			this.maxFails = item.getMaxFails();
			this.failTimeout = item.getFailTimeout();
			this.maxConcurrent = item.getMaxConcurrent();
			this.connectTimeout = item.getConnectTimeout();
			this.readTimeout = item.getReadTimeout();
		}

		public static TrafficTableItemVo valueOf(TrafficTableItem item) {
			if (null == item) {
				return null;
			}
			return new TrafficTableItemVo(item);
		}

		@Override
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getServiceNo() {
			return serviceNo;
		}

		public void setServiceNo(String serviceNo) {
			this.serviceNo = serviceNo;
		}

		public String getServiceVersion() {
			return serviceVersion;
		}

		public void setServiceVersion(String serviceVersion) {
			this.serviceVersion = serviceVersion;
		}

		public int getWeight() {
			return weight;
		}

		public void setWeight(int weight) {
			this.weight = weight;
		}

		public int getMaxFails() {
			return maxFails;
		}

		public void setMaxFails(int maxFails) {
			this.maxFails = maxFails;
		}

		public int getFailTimeout() {
			return failTimeout;
		}

		public void setFailTimeout(int failTimeout) {
			this.failTimeout = failTimeout;
		}

		public int getMaxConcurrent() {
			return maxConcurrent;
		}

		public void setMaxConcurrent(int maxConcurrent) {
			this.maxConcurrent = maxConcurrent;
		}

		public int getConnectTimeout() {
			return connectTimeout;
		}

		public void setConnectTimeout(int connectTimeout) {
			this.connectTimeout = connectTimeout;
		}

		public int getReadTimeout() {
			return readTimeout;
		}

		public void setReadTimeout(int readTimeout) {
			this.readTimeout = readTimeout;
		}
	}
}
