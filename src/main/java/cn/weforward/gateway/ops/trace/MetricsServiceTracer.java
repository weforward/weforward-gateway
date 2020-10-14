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

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.crypto.Hex;
import cn.weforward.common.sys.Timestamp;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.metrics.WeforwadMetrics;
import cn.weforward.protocol.ops.trace.ServiceTraceToken;
import cn.weforward.protocol.ops.trace.SimpleServiceTraceToken;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;

/**
 * 基于Metrics的跟踪器实现
 * 
 * @author zhangpengji
 *
 */
public class MetricsServiceTracer implements ServiceTracer {
	static final Logger _Logger = LoggerFactory.getLogger(MetricsServiceTracer.class);

	Timestamp m_Timestamp;
	String m_ServiceId;

	MeterRegistry m_MeterRegistry;

	public MetricsServiceTracer(String serviceId) {
		m_ServiceId = serviceId;
		m_Timestamp = Timestamp.getInstance(Timestamp.POLICY_DEFAULT);
	}

	public void setMeterRegistry(MeterRegistry registry) {
		m_MeterRegistry = registry;
	}

	@Override
	public ServiceTraceToken onBegin(String preTokenStr, ServiceInstance service) {
		String spanId;
		try {
			spanId = genSpanId();
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
			return ERROR;
		}

		ServiceTraceToken preToken = SimpleServiceTraceToken.valueOf(preTokenStr);
		Token token;
		if (null != preToken && !StringUtil.isEmpty(preToken.getSpanId())) {
			token = new Token(spanId, preToken);
		} else {
			token = new Token(spanId);
		}
		token.setService(service);
		gaugeBegin(token);
		return token;
	}

	protected void gaugeBegin(Token token) {
		MeterRegistry registry = m_MeterRegistry;
		if (null == registry) {
			return;
		}
		if (StringUtil.isEmpty(token.getToken())) {
			return;
		}
		try {
			TimeGauge.builder(WeforwadMetrics.TRACE_START_TIME, System.currentTimeMillis(), TimeUnit.MILLISECONDS,
					Long::longValue).tags(token.getTags()).register(registry);
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
		}
	}

	protected void gaugeFinish(Token token) {
		if(null == token || StringUtil.isEmpty(token.getToken())) {
			return;
		}
		MeterRegistry registry = m_MeterRegistry;
		if (null == registry) {
			return;
		}
		try {
			TimeGauge.builder(WeforwadMetrics.TRACE_END_TIME, System.currentTimeMillis(), TimeUnit.MILLISECONDS,
					Long::longValue).tags(token.getTags()).register(registry);
		} catch (Throwable e) {
			_Logger.error(e.toString(), e);
		}
	}

	String genSpanId() {
		long timestamp = m_Timestamp.next(0);
		// 去掉最后的8位服务器标识
		timestamp >>= 8;
		StringBuilder sb = new StringBuilder(14 + (null == m_ServiceId ? 0 : m_ServiceId.length()));
		Hex.toHex(timestamp, sb);
		if (!StringUtil.isEmpty(m_ServiceId)) {
			sb.append(m_ServiceId);
		}
		return sb.toString();
	}

	@Override
	public void onFinish(ServiceTraceToken token) {
		if (token instanceof Token) {
			gaugeFinish((Token) token);
		}
	}

	static final ServiceTraceToken ERROR = new ServiceTraceToken() {

		@Override
		public String getToken() {
			return null;
		}

		@Override
		public String getTraceId() {
			return null;
		}

		@Override
		public String getSpanId() {
			return null;
		}

		@Override
		public String getParentId() {
			return null;
		}

		@Override
		public int getDepth() {
			return 0;
		}
	};

	static class Token implements ServiceTraceToken {

		String m_TraceId;
		String m_SpanId;
		int m_Depth;
		String m_ParentId;
		ServiceInstance m_Service;

		Token(String spanId, ServiceTraceToken pre) {
			m_TraceId = pre.getTraceId();
			m_SpanId = spanId;
			m_ParentId = pre.getSpanId();
			m_Depth = pre.getDepth() + 1;
		}

		Token(String spanId) {
			m_TraceId = spanId;
			m_SpanId = spanId;
			m_ParentId = null;
			m_Depth = 0;
		}

		// Token(String traceId, String spanId, int depth) {
		// m_TraceId = traceId;
		// m_SpanId = spanId;
		// m_Depth = depth;
		// }

		void setService(ServiceInstance service) {
			m_Service = service;
		}

		@Override
		public String getTraceId() {
			return m_TraceId;
		}

		@Override
		public String getSpanId() {
			return m_SpanId;
		}

		@Override
		public String getParentId() {
			return m_ParentId;
		}

		@Override
		public int getDepth() {
			return m_Depth;
		}

		// static Token parse(String str) {
		// if (StringUtil.isEmpty(str)) {
		// return null;
		// }
		// String traceId;
		// String spanId;
		// byte depth;
		// int bi = 0;
		// int ei = str.indexOf(SPEARATOR);
		// if (-1 != ei) {
		// traceId = str.substring(bi, ei);
		// bi = ei + 1;
		// ei = str.indexOf(SPEARATOR, bi);
		// if (-1 != ei) {
		// spanId = str.substring(bi, ei);
		// bi = ei + 1;
		// if (bi < str.length()) {
		// try {
		// depth = Byte.parseByte(str.substring(bi), 16);
		// return new Token(traceId, spanId, depth);
		// } catch (NumberFormatException e) {
		// }
		// }
		// }
		// }
		// return null;
		// }

		@Override
		public String getToken() {
			return Helper.toTokenId(this);
		}

		Tags getTags() {
			return WeforwadMetrics.TagHelper.of(WeforwadMetrics.TagHelper.traceId(m_TraceId),
					WeforwadMetrics.TagHelper.traceSpanId(m_SpanId),
					WeforwadMetrics.TagHelper.traceParentId(m_ParentId),
					WeforwadMetrics.TagHelper.serviceName(m_Service.getName()),
					WeforwadMetrics.TagHelper.serviceNo(m_Service.getNo()));
		}
	}
}
