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

import cn.weforward.common.crypto.Hex;
import cn.weforward.common.execption.OverflowException;
import cn.weforward.common.sys.ClockTick;
import cn.weforward.common.sys.IdGenerator;

/**
 * 最简易的ID生成器，以时间戳（当前时间-MIN_TIME，单位是1秒）生成ID
 * 
 * <pre>
 * 格式：{时间戳}+{以1每步递增的计数器}+{服务器标识}
 * 当ID（平均）产生速度小于255/1每秒比较安全，碰撞几率不高
 * </pre>
 * 
 * @author liangyi,zhangpengji
 *
 */
public class SimpleIdGenerator extends IdGenerator {
	/** 计时器 */
	static final ClockTick _Tick = ClockTick.getInstance(1);

	/** 时间点单位（1秒） */
	public static final int MAX_TIME_UNIT = 1000;
	/** 2020-1-1的时间点（用于加上当前时间生成ID） */
	public static final long MIN_TIME = 1577808000000L;

	/** 时间戳 */
	protected volatile long m_Timestamp;
	/** 当前ID序列计数 */
	protected volatile int m_Ordinal;
	/** 最后计数 */
	protected volatile int m_LastTick;

	public SimpleIdGenerator(String serverId) {
		super(serverId);

		m_Timestamp = (System.currentTimeMillis() - MIN_TIME) / MAX_TIME_UNIT;
		m_Ordinal = 0;
		m_LastTick = _Tick.getTicker();
	}

	/**
	 * 生成id。<br/>
	 * 注意：id存在重复的可能，比如系统时间调整
	 */
	@Override
	public synchronized String genId(String prefix) {
		int tick = _Tick.getTicker();
		if (m_LastTick != tick) {
			long timestamp = (System.currentTimeMillis() - MIN_TIME) / MAX_TIME_UNIT;
			if (m_Timestamp >= timestamp) {
				_Logger.error("系统时间倒退：" + m_Timestamp + " >= " + timestamp);
			} else {
				m_Timestamp = timestamp;
				m_Ordinal = 0;
				m_LastTick = tick;
			}
		}
		int ordinal = m_Ordinal++;
		if (ordinal > 0xFF) {
			throw new OverflowException("在短时（" + MAX_TIME_UNIT + "秒）内生成太多时间戳：" + ordinal);
		}
		StringBuilder sb;
		if (null != prefix && prefix.length() > 0) {
			sb = new StringBuilder(prefix.length() + 10 + m_ServerId.length());
			sb.append(prefix);
		} else {
			sb = new StringBuilder(10 + m_ServerId.length());
		}
		Hex.toHexFixed((int) m_Timestamp, sb);
		Hex.toHexFixed((byte) ordinal, sb);
		sb.append(m_ServerId);
		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(32);
		sb.append("{server:");
		sb.append(m_ServerId);
		sb.append(",ts:");
		Hex.toHexFixed(m_Timestamp, sb);
		sb.append(",ordinal:");
		Hex.toHexFixed((short) m_Ordinal, sb);
		sb.append("}");
		return sb.toString();
	}
}
