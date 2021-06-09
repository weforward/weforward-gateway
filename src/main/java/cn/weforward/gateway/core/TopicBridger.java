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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.io.BytesOutputStream;
import cn.weforward.common.io.InputStreamNio;
import cn.weforward.common.util.ListUtil;
import cn.weforward.gateway.Pipe;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.Tunnel;
import cn.weforward.gateway.util.CloseUtil;
import cn.weforward.gateway.util.DiscardOutputStream;
import cn.weforward.gateway.util.TunnelWrap;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.ResponseConstants;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.exception.AuthException;
import cn.weforward.protocol.exception.WeforwardException;

/**
 * 支持'topic'信道
 * 
 * @author zhangpengji
 *
 */
class TopicBridger {
	static final Logger _Logger = LoggerFactory.getLogger(TopicBridger.class);

	Map<String, TopicDetail> m_Details;

	List<ServiceInstanceBalance> m_Balances;
	TopicTunnel m_TunnelWrap;
	PipeWrap m_PipeWrap;
	volatile InputStream m_RequestBuffer;
	volatile boolean m_RequestBufferReady;

	TopicBridger() {
		m_Details = new HashMap<String, TopicDetail>();
	}

	void jonit(GatewayImpl gw, Tunnel tunnel) throws IOException {
		Header header = tunnel.getHeader();
		String serviceName = header.getService();
		String[] names;
		if (-1 != serviceName.indexOf(';')) {
			names = serviceName.split(";");
		} else {
			names = new String[] { serviceName };
		}
		
		List<ServiceInstanceBalance> balances = new ArrayList<>(names.length);
		for (String name : names) {
			TopicDetail detail = new TopicDetail(name);
			m_Details.put(name, detail);

			ServiceInstanceBalance balance = gw.getServiceInstanceBalance(name);
			if (null == balance) {
				detail.appendError("服务不存在");
				_Logger.error("服务不存在：" + name);
				continue;
			}
			Header copyHeader = Header.copy(header);
			copyHeader.setService(name);
			try {
				gw.m_RightManage.verifyAccess(copyHeader);
			} catch (AuthException e) {
				detail.appendError(e.getMessage());
				_Logger.error(e.toString());
				continue;
			}
			balances.add(balance);
		}

		m_RequestBuffer = tunnel.mirrorTransferStream();

		m_Balances = balances;
		m_TunnelWrap = new TopicTunnel(tunnel);

		m_PipeWrap = new PipeWrap();
	}

	void connect() {
		m_TunnelWrap.getTunnel().requestInit(m_PipeWrap, 0);
		m_TunnelWrap.getTunnel().requestReady(m_PipeWrap, new DiscardOutputStream());

		m_TunnelWrap.doNotify();
	}

	synchronized InputStream duplicateTransferBuffer() throws IOException {
		InputStream buffer = m_RequestBuffer;
		if (null == buffer) {
			throw new IOException("已结束");
		}
		// 先复制一份
		InputStream copy = null;
		InputStreamNio nio = ((InputStreamNio) buffer).duplicate();
		copy = (InputStream) nio;
		m_RequestBuffer = copy;

		return buffer;
	}

	void end() {
		InputStream buffer;
		synchronized (this) {
			buffer = m_RequestBuffer;
			m_RequestBuffer = null;
		}
		if (null != buffer) {
			CloseUtil.close(buffer, _Logger);
		}
	}

	private class TopicTunnel extends TunnelWrap {

		List<Pipe> m_Pipes;
		List<PipeReady> m_PipeReadys;
		List<String> m_Successes;
		AtomicInteger m_Count;

		TopicTunnel(Tunnel tunnel) {
			super(tunnel);

			m_Pipes = new ArrayList<>();
			m_PipeReadys = new ArrayList<>();
			m_Successes = new ArrayList<>();
			m_Count = new AtomicInteger();
		}

		void doNotify() {
			List<ServiceEndpoint> eps = new ArrayList<>();
			for (ServiceInstanceBalance balance : m_Balances) {
				List<ServiceEndpoint> ls = balance.list();
				if (!ListUtil.isEmpty(ls)) {
					eps.addAll(ls);
				} else {
					m_Details.get(balance.getName()).appendError("无可用实例");
				}
			}
			if (ListUtil.isEmpty(eps)) {
				// responseError(WeforwardException.CODE_SERVICE_INVOKE_ERROR, "通知失败，无可用实例");
				checkFinish();
				return;
			}

			m_Count.set(eps.size());
			for (ServiceEndpoint ep : eps) {
				Pipe p = ep.connect(m_TunnelWrap, false);
				m_Pipes.add(p);
			}
		}

		void requestCanceled() {
			end();
			cancelPipes();
		}

		void cancelPipes() {
			List<Pipe> ps = m_Pipes;
			if (ListUtil.isEmpty(ps)) {
				return;
			}
			for (Pipe p : ps) {
				p.requestCanceled(this);
			}
		}

		void requestCompleted() {
			writeRequestStream();
		}

		void pushPipeOutput(Pipe pipe, OutputStream output) {
			synchronized (m_PipeReadys) {
				PipeReady pr = new PipeReady(pipe, output);
				m_PipeReadys.add(pr);
			}
		}

		PipeReady popPipeReady() {
			synchronized (m_PipeReadys) {
				int size = m_PipeReadys.size();
				if (0 == size) {
					return null;
				}
				return m_PipeReadys.remove(size - 1);
			}
		}

		void addSuccess(Pipe pipe) {
			synchronized (m_Successes) {
				m_Successes.add(pipe.getService().getNo());
			}
			m_Count.decrementAndGet();
			m_Details.get(pipe.getService().getName()).incReceives();

			checkFinish();
		}

		void addFail(Pipe pipe) {
			synchronized (m_Successes) {
				if (m_Successes.contains(pipe.getService().getNo())) {
					// 已经成功发送请求，忽略后续的失败
					return;
				}
			}
			m_Count.decrementAndGet();

			checkFinish();
		}

		void checkFinish() {
			if (0 != m_Count.get()) {
				return;
			}
			// if (0 == m_Successes.size()) {
			// // 全失败
			// responseError(WeforwardException.CODE_SERVICE_INVOKE_ERROR, "通知失败，全失败");
			// return;
			// }

			if (_Logger.isTraceEnabled()) {
				_Logger.trace("微服务[" + m_Tunnel.getHeader().getService() + "]通知完成，成功：" + m_Successes);
			}
			end();
			// m_PipeWrap.setNotifyReceives(m_Successes);
			m_Tunnel.responseReady(m_PipeWrap);
		}

		void writeRequestStream() {
			if (!m_RequestBufferReady) {
				// 等请求数据接收完再继续
				return;
			}

			PipeReady pr = popPipeReady();
			while (null != pr) {
				InputStream in = null;
				try {
					in = duplicateTransferBuffer();
					BytesOutputStream.transfer(in, pr.output, 0);
					pr.pipe.requestCompleted(this);
				} catch (Throwable e) {
					_Logger.error("微服务[" + ServiceInstance.getNameNo(pr.pipe.getService()) + "]通知失败", e);
					pr.pipe.requestCanceled(this);
				} finally {
					CloseUtil.close(in, _Logger);
				}

				pr = popPipeReady();
			}
		}

		void responseReady(OutputStream output) {
			// XXX 目前只支持json格式
			StringBuilder result = new StringBuilder();
			result.append(",\"result\":{\"" + ResponseConstants.CODE + "\":0,\"" + ResponseConstants.MSG + "\":\"\",\""
					+ ResponseConstants.CONTENT + "\":[");
			boolean first = true;
			for (TopicDetail detail : m_Details.values()) {
				if (first) {
					first = false;
				} else {
					result.append(',');
				}
				result.append("{\"name\":\"" + detail.name + "\",\"err\":\"" + detail.err + "\",\"receives\":"
						+ detail.receives + "}");
			}
			result.append("]}}");
			try {
				output.write(result.toString().getBytes(Header.CHARSET_UTF8));
			} catch (Throwable e) {
				responseError(e);
				return;
			}

			m_Tunnel.responseCompleted(m_PipeWrap);
		}

		void responseError(Throwable e) {
			_Logger.error(e.toString(), e);
			if (e instanceof IOException) {
				responseError(WeforwardException.CODE_SERVICE_INVOKE_ERROR, "IO异常:" + e.getMessage());
			} else {
				responseError(WeforwardException.CODE_INTERNAL_ERROR, "内部错误");
			}
		}

		void responseError(int code, String msg) {
			end();

			m_TunnelWrap.getTunnel().responseError(m_PipeWrap, code, msg);

			cancelPipes();
		}

		@Override
		public void responseError(Pipe pipe, int code, String msg) {
			_Logger.warn("微服务[" + ServiceInstance.getNameNo(pipe.getService()) + "]通知失败：" + code + "/" + msg);

			addFail(pipe);
		}

		@Override
		public InputStream mirrorTransferStream() throws IOException {
			throw new IOException("不支持");
		}

		@Override
		public void requestInit(Pipe pipe, int requestMaxSize) {

		}

		@Override
		public void requestReady(Pipe pipe, OutputStream output) {
			pushPipeOutput(pipe, output);
			writeRequestStream();
		}

		@Override
		public void requestCompleted(Pipe pipe) {
			// 通知成功 :)
			if (_Logger.isTraceEnabled()) {
				_Logger.trace("微服务[" + ServiceInstance.getNameNo(pipe.getService()) + "]通知成功");
			}

			addSuccess(pipe);
		}

		@Override
		public void responseReady(Pipe pipe) {
			pipe.responseReady(m_TunnelWrap, new DiscardOutputStream());
		}

		@Override
		public void responseCompleted(Pipe pipe) {
			if (_Logger.isTraceEnabled()) {
				_Logger.trace("微服务[" + ServiceInstance.getNameNo(pipe.getService()) + "]通知收到响应");
			}
		}

	}

	private class PipeReady {
		final Pipe pipe;
		final OutputStream output;

		PipeReady(Pipe pipe, OutputStream output) {
			this.pipe = pipe;
			this.output = output;
		}
	}

	private class PipeWrap implements Pipe {

		// List<String> m_NotifyReceives;

		@Override
		public Header getHeader() {
			return null;
		}

		@Override
		public String getTag() {
			return null;
		}

		@Override
		public String getResourceId() {
			return null;
		}

		@Override
		public long getResourceExpire() {
			return 0;
		}

		@Override
		public String getResourceService() {
			return null;
		}

		@Override
		public String getForwardTo() {
			return null;
		}

		// void setNotifyReceives(List<String> receives) {
		// m_NotifyReceives = receives;
		// }

		@Override
		public List<String> getNotifyReceives() {
			// List<String> receives = m_NotifyReceives;
			// if (null == receives) {
			// return Collections.emptyList();
			// }
			// return receives;
			return Collections.emptyList();
		}

		@Override
		public Service getService() {
			return null;
		}

		@Override
		public void requestCanceled(Tunnel tunnel) {
			m_TunnelWrap.requestCanceled();
		}

		@Override
		public void requestCompleted(Tunnel tunnel) {
			m_RequestBufferReady = true;
			m_TunnelWrap.requestCompleted();
		}

		@Override
		public void responseReady(Tunnel tunnel, OutputStream output) {
			m_TunnelWrap.responseReady(output);
		}

	}

	static class TopicDetail {
		String name;
		String err;
		volatile int receives;

		TopicDetail(String name) {
			this.name = name;
			this.err = "";
		}
		
		void appendError(String msg) {
			this.err = msg;
		}

		void incReceives() {
			this.receives++;
		}
	}
}
