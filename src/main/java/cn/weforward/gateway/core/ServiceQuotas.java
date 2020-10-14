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
package cn.weforward.gateway.core;

import java.util.concurrent.atomic.AtomicInteger;

import cn.weforward.gateway.exception.QuotasException;

/**
 * 微服务配额
 * 
 * @author zhangpengji
 *
 */
public class ServiceQuotas {

	/** 最大限额 */
	protected int m_Max;
	/** 保留配额 */
	protected int m_Reserve;
	/** 已用配额 */
	protected AtomicInteger m_Count;

	public ServiceQuotas(int max, int reserve) {
		m_Max = max;
		m_Reserve = reserve;
		m_Count = new AtomicInteger(0);
	}

	public int getMax() {
		return m_Max;
	}

	public int getCount() {
		return m_Count.get();
	}

	protected int calcQuota(int quota, int concurrent) {
		if (quota <= 0) {
			// 用尽了:(
			return 0;
		}

		if (quota > m_Reserve) {
			// 还有很多
			return quota + concurrent;
		}

		if (quota > 3) {
			// 剩余不多了，按2/3衰减
			return quota * 2 / 3;
		}

		// 一个可用额度
		return 1;
	}

	public int getQuota(String service, int concurrent) {
		return calcQuota(m_Max - m_Count.get(), concurrent);
	}

	public int use(String service, int concurrent) throws QuotasException {
		int count = m_Count.incrementAndGet();
		if (count > m_Max) {
			// 超额了，减一对冲前面的加一，然后抛出超额异常
			m_Count.decrementAndGet();
			throw QuotasException.overQuotas(service, "超额{count:" + count + ",concurrent:" + concurrent + ",reserve:"
					+ m_Reserve + ",max:" + m_Max + "}");
		}
		if (concurrent > 0) {
			int q = calcQuota(m_Max - count, concurrent);
			if (concurrent >= q) {
				// 满额了，减一对冲前面的加一，然后抛出满额异常
				m_Count.decrementAndGet();
				throw QuotasException.fullQuotas(service, "满额{count:" + (count - 1) + ",concurrent:" + concurrent
						+ ",quota:" + q + ",reserve:" + m_Reserve + ",max:" + m_Max + "}");
			}
		}
		return count;
	}

	public int free(String service) {
		return m_Count.decrementAndGet();
	}

	/**
	 * 重置（置零）已用配额，特殊情况下使用
	 */
	public void reset() {
		m_Count.set(0);
	}

	@Override
	public String toString() {
		return "{max:" + m_Max + ",count:" + m_Count.get() + "}";
	}

}
