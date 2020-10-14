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
package cn.weforward.gateway.util;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对资源使用防止过载的限制
 * 
 * @author zhangpengji
 *
 */
public class OverloadLimit {
	static final Logger _Logger = LoggerFactory.getLogger(OverloadLimit.class);

	/**
	 * 资源使用令牌
	 * 
	 * @author zhangpengji
	 *
	 */
	public static abstract class OverloadLimitToken {

		/**
		 * 释放资源
		 */
		public abstract void free();
	}

	int m_UseMax;
	AtomicInteger m_InUse;
	OverloadLimitToken m_Token;

	/**
	 * 构造
	 * 
	 * @param max
	 *            对资源的最大同时使用数
	 */
	public OverloadLimit(int max) {
		m_UseMax = max;
		m_InUse = new AtomicInteger();
		m_Token = new OverloadLimitToken() {

			@Override
			public void free() {
				int inUse = m_InUse.get();
				if (inUse <= 0) {
					// 重复调用？
					if (_Logger.isTraceEnabled()) {
						_Logger.trace("in use:" + inUse);
					}
					return;
				}
				m_InUse.decrementAndGet();
			}
		};
	}

	/**
	 * 申请资源的使用
	 * 
	 * @return 有空闲资源则返回token，否则返回null
	 */
	public OverloadLimitToken use() {
		int count = m_InUse.incrementAndGet();
		if (count > m_UseMax) {
			m_InUse.decrementAndGet();
			return null;
		}
		return m_Token;
	}

	/**
	 * 正在使用的资源数
	 * 
	 * @return
	 */
	public int getInUse() {
		return m_InUse.get();
	}
}
