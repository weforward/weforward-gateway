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

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.sys.ClockTick;
import cn.weforward.common.sys.Memory;
import cn.weforward.common.sys.VmStat;
import cn.weforward.metrics.WeforwardMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;

/**
 * 指标收集器
 * 
 * @author zhangpengji
 *
 */
public class MetricsCollecter {
	static final Logger _Logger = LoggerFactory.getLogger(MetricsCollecter.class);

	/** 计时器 */
	static final ClockTick _Tick = ClockTick.getInstance(1);

	String m_ServerId;
	long m_StartTime;
	RpcCounter m_RpcCounter;
	// 刷新间隔，单位：秒
	int m_RefreshInterval;

	volatile long m_LastRefresh;

	static class RpcCounter {
		volatile int m_RpcCount;
		volatile int m_RpcConcurrent;
		volatile int m_RpcMaxConcurrent;
		volatile int m_RpcCountLast;
		volatile int m_RpcMaxConcurrentLast;

		volatile int m_StreamCount;
		volatile int m_StreamConcurrent;
		volatile int m_StreamMaxConcurrent;
		volatile int m_StreamCountLast;
		volatile int m_StreamMaxConcurrentLast;

		public synchronized void onRpcBegin() {
			++m_RpcCount;
			int curr = ++m_RpcConcurrent;
			if (curr > m_RpcMaxConcurrent) {
				m_RpcMaxConcurrent = curr;
			}
		}

		public synchronized void onRpcEnd() {
			--m_RpcConcurrent;
		}

		public synchronized void onStreamBegin() {
			++m_StreamCount;
			int curr = ++m_StreamConcurrent;
			if (curr > m_StreamMaxConcurrent) {
				m_StreamMaxConcurrent = curr;
			}
		}

		public synchronized void onStreamEnd() {
			--m_StreamConcurrent;
		}

		public synchronized void refresh() {
			m_RpcCountLast = m_RpcCount;
			m_RpcCount = 0;
			m_RpcMaxConcurrentLast = m_RpcMaxConcurrent;
			m_RpcMaxConcurrent = 0;

			m_StreamCountLast = m_StreamCount;
			m_StreamCount = 0;
			m_StreamMaxConcurrentLast = m_StreamMaxConcurrent;
			m_StreamMaxConcurrent = 0;
		}
	}

	public MetricsCollecter(String serverId, MeterRegistry register) {
		m_ServerId = serverId;
		m_StartTime = System.currentTimeMillis();
		m_LastRefresh = -1;
		m_RefreshInterval = 30;

		m_RpcCounter = new RpcCounter();

		Tags tags = WeforwardMetrics.TagHelper.of(WeforwardMetrics.TagHelper.gatewayId(m_ServerId));
		TimeGauge.builder(WeforwardMetrics.GATEWAY_START_TIME, this, TimeUnit.MILLISECONDS,
				MetricsCollecter::getStartTime).tags(tags).register(register);
		TimeGauge.builder(WeforwardMetrics.GATEWAY_UP_TIME, this, TimeUnit.MILLISECONDS, MetricsCollecter::getUpTime)
				.tags(tags).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_MEMORY_MAX, this, MetricsCollecter::getMemoryMax).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_MEMORY_ALLOC, this, MetricsCollecter::getMemoryAlloc).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_MEMORY_USED, this, MetricsCollecter::getMemoryUsed).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_GC_FULL_COUNT, this, MetricsCollecter::getGcCount).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_GC_FULL_TIME, this, MetricsCollecter::getGcTime).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_THREAD_COUNT, this, MetricsCollecter::getThreadCount).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_CPU_USAGE_RATE, this, MetricsCollecter::getProcessCpuLoad).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_RPC_COUNT, this, MetricsCollecter::getRpcCount).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_RPC_CONCURRENT, this, MetricsCollecter::getRpcMaxConcurrent).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_STREAM_COUNT, this, MetricsCollecter::getStreamCount).tags(tags)
				.strongReference(true).register(register);
		Gauge.builder(WeforwardMetrics.GATEWAY_STREAM_CONCURRENT, this, MetricsCollecter::getStreamMaxConcurrent)
				.tags(tags).strongReference(true).register(register);
	}

	public void setInterval(int second) {
		m_RefreshInterval = second;
	}

	public long getStartTime() {
		return m_StartTime;
	}

	public long getUpTime() {
		return System.currentTimeMillis() - m_StartTime;
	}

	private synchronized void refresh() {
		long t = _Tick.getTickerLong();
		if (Math.abs(t - m_LastRefresh) > m_RefreshInterval) {
			VmStat.refresh();
			m_RpcCounter.refresh();
			m_LastRefresh = t;
		}
	}

	public long getMemoryMax() {
		refresh();
		return VmStat.getMemory().getMax();
	}

	public long getMemoryAlloc() {
		refresh();
		return VmStat.getMemory().getAlloc();
	}

	public long getMemoryUsable() {
		refresh();
		return VmStat.getMemory().getUsable();
	}

	public long getMemoryUsed() {
		refresh();
		Memory mem = VmStat.getMemory();
		return mem.getMax() - mem.getUsable();
	}

	public int getGcCount() {
		refresh();
		return VmStat.getMemory().getGcCount();
	}

	public int getGcTime() {
		refresh();
		return VmStat.getMemory().getGcTime();
	}

	public int getThreadCount() {
		refresh();
		return VmStat.getThreadCount();
	}

	public int getProcessCpuLoad() {
		refresh();
		return VmStat.getProcessCpuLoad();
	}

	public int getRpcCount() {
		refresh();
		return m_RpcCounter.m_RpcCountLast;
	}

	public int getRpcMaxConcurrent() {
		refresh();
		return m_RpcCounter.m_RpcMaxConcurrentLast;
	}

	public int getStreamCount() {
		refresh();
		return m_RpcCounter.m_StreamCountLast;
	}

	public int getStreamMaxConcurrent() {
		refresh();
		return m_RpcCounter.m_StreamMaxConcurrentLast;
	}

	public void onRpcBegin() {
		m_RpcCounter.onRpcBegin();
	}

	public void onRpcEnd() {
		m_RpcCounter.onRpcEnd();
	}

	public void onStreamBegin() {
		m_RpcCounter.onStreamBegin();
	}

	public void onStreamEnd() {
		m_RpcCounter.onStreamEnd();
	}
}
