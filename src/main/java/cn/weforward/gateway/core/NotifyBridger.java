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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.io.BytesOutputStream;
import cn.weforward.common.io.InputStreamNio;
import cn.weforward.common.util.ListUtil;
import cn.weforward.gateway.Pipe;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.Tunnel;
import cn.weforward.gateway.exception.BalanceException;
import cn.weforward.gateway.util.CloseUtil;
import cn.weforward.gateway.util.DiscardOutputStream;
import cn.weforward.protocol.Request;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.exception.WeforwardException;

/**
 * 支持<code>notify</code>信道（单<code>Tunnel</code>对接多<code>Pipe</code>）
 * 
 * @author zhangpengji
 *
 */
class NotifyBridger {
	static final Logger _Logger = LoggerFactory.getLogger(NotifyBridger.class);

	final ServiceEndpointBalance m_Balance;
	final NotifyTunnel m_TunnelWrap;
	final PipeWrap m_PipeWrap;
	volatile InputStream m_RequestBuffer;
	volatile boolean m_RequestBufferReady;

	NotifyBridger(ServiceEndpointBalance balance, Tunnel tunnel) throws IOException {
		m_RequestBuffer = tunnel.mirrorTransferStream();

		m_Balance = balance;
		if (Request.MARK_NOTIFY_BROADCAST == (Request.MARK_NOTIFY_BROADCAST & tunnel.getMarks())) {
			m_TunnelWrap = new BroadCastTunnel(tunnel);
		} else {
			m_TunnelWrap = new RoundRobinTunnel(tunnel);
		}

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

	private abstract class NotifyTunnel extends cn.weforward.gateway.util.TunnelWrap {

		NotifyTunnel(Tunnel tunnel) {
			super(tunnel);
		}

		Tunnel getTunnel() {
			return m_Tunnel;
		}

		// String getServiceName() {
		// return m_Tunnel.getHeader().getService();
		// }

		abstract void doNotify();

		abstract void requestCanceled();

		abstract void requestCompleted();

		void responseReady(OutputStream output) {
			// XXX 目前只支持json格式
			try {
				output.write('}');
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

		abstract void responseError(int code, String msg);
	}

	private class RoundRobinTunnel extends NotifyTunnel {

		volatile Pipe m_CurrPipe;
		volatile OutputStream m_CurrPipeOutput;
		// 已连接过的endpoint
		final List<String> m_Connected;

		RoundRobinTunnel(Tunnel tunnel) {
			super(tunnel);
			m_Connected = new ArrayList<String>();
		}

		@Override
		void doNotify() {
			roundRobin();
		}

		void roundRobin() {
			m_CurrPipe = null;

			ServiceEndpoint ep;
			try {
				ep = m_Balance.get(null, getVersion(), m_Connected);
			} catch (BalanceException e) {
				_Logger.warn(e.toString());
				responseError(WeforwardException.CODE_SERVICE_INVOKE_ERROR, "通知失败，已尝试：" + m_Connected);
				return;
			}
			m_Connected.add(ep.getService().getNo());
			ep.connect(this, false);
		}

		@Override
		void requestCanceled() {
			end();

			Pipe p = m_CurrPipe;
			if (null != p) {
				p.requestCanceled(this);
			}
		}

		@Override
		void requestCompleted() {
			writeRequestStream();
		}

		void writeRequestStream() {
			if (!m_RequestBufferReady) {
				// 等请求数据接收完再继续
				return;
			}

			OutputStream out;
			synchronized (this) {
				out = m_CurrPipeOutput;
				m_CurrPipeOutput = null;
			}
			if (null == out) {
				return;
			}

			InputStream in = null;
			try {
				in = duplicateTransferBuffer();
				BytesOutputStream.transfer(in, out, 0);
			} catch (Throwable e) {
				responseError(e);
				return;
			} finally {
				CloseUtil.close(in, _Logger);
			}
			m_CurrPipe.requestCompleted(this);
		}

		void responseError(int code, String msg) {
			end();

			m_TunnelWrap.getTunnel().responseError(m_PipeWrap, code, msg);

			Pipe p = m_CurrPipe;
			if (null != p) {
				p.requestCanceled(this);
			}
		}

		@Override
		public void responseError(Pipe pipe, int code, String msg) {
			_Logger.warn("微服务[" + ServiceInstance.getNameNo(pipe.getService()) + "]通知失败：" + code + "/" + msg);

			if (m_CurrPipe == pipe) {
				// 下一个
				roundRobin();
			}
		}

		@Override
		public InputStream mirrorTransferStream() throws IOException {
			throw new IOException("不支持");
		}

		@Override
		public void requestInit(Pipe pipe, int requestMaxSize) {
			Pipe curr = m_CurrPipe;
			if (null != curr) {
				// 有bug :(
				_Logger.error("Pipe已存在");
				return;
			}

			m_CurrPipe = pipe;
		}

		@Override
		public void requestReady(Pipe pipe, OutputStream output) {
			if (!checkPipe(pipe)) {
				return;
			}

			m_CurrPipeOutput = output;
			writeRequestStream();
		}

		boolean checkPipe(Pipe pipe) {
			Pipe curr = m_CurrPipe;
			if (curr != pipe) {
				// 有bug？！
				_Logger.error("Pipe不匹配：" + curr + " != " + pipe);
				return false;
			}
			return true;
		}

		@Override
		public void requestCompleted(Pipe pipe) {
			if (!checkPipe(pipe)) {
				return;
			}

			// 通知成功 :)
			if (_Logger.isTraceEnabled()) {
				_Logger.trace("微服务[" + ServiceInstance.getNameNo(pipe.getService()) + "]通知成功");
			}

			end();

			m_PipeWrap.setNotifyReceives(Collections.singletonList(pipe.getService().getNo()));
			m_Tunnel.responseReady(m_PipeWrap);
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

	private class BroadCastTunnel extends NotifyTunnel {

		List<Pipe> m_Pipes;
		List<PipeReady> m_PipeReadys;
		List<String> m_Successes;
		AtomicInteger m_Dones;

		BroadCastTunnel(Tunnel tunnel) {
			super(tunnel);

			m_Pipes = new ArrayList<>();
			m_PipeReadys = new ArrayList<>();
			m_Successes = new ArrayList<>();
			m_Dones = new AtomicInteger();
		}

		@Override
		void doNotify() {
			List<ServiceEndpoint> eps = m_Balance.list();
			if (ListUtil.isEmpty(eps)) {
				responseError(WeforwardException.CODE_SERVICE_INVOKE_ERROR, "通知失败，无可用实例");
				return;
			}

			for (ServiceEndpoint ep : eps) {
				Pipe p = ep.connect(m_TunnelWrap, false);
				m_Pipes.add(p);
			}
		}

		@Override
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

		@Override
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
			m_Dones.incrementAndGet();

			checkFinish();
		}

		void addFail(Pipe pipe) {
			synchronized (m_Successes) {
				if (m_Successes.contains(pipe.getService().getNo())) {
					// 已经成功发送请求，忽略后续的失败
					return;
				}
			}
			m_Dones.incrementAndGet();

			checkFinish();
		}

		void checkFinish() {
			if (m_Dones.get() != m_Pipes.size()) {
				return;
			}
			if (0 == m_Successes.size()) {
				// 全失败
				m_Pipes = null;
				responseError(WeforwardException.CODE_SERVICE_INVOKE_ERROR, "通知失败，全失败");
				return;
			}

			end();
			m_PipeWrap.setNotifyReceives(m_Successes);
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
			if (_Logger.isInfoEnabled()) {
				_Logger.info("微服务[" + ServiceInstance.getNameNo(pipe.getService()) + "]通知成功");
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

		List<String> m_NotifyReceives;

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

		void setNotifyReceives(List<String> receives) {
			m_NotifyReceives = receives;
		}

		@Override
		public List<String> getNotifyReceives() {
			List<String> receives = m_NotifyReceives;
			if (null == receives) {
				return Collections.emptyList();
			}
			return receives;
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
}
