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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.io.BytesOutputStream;
import cn.weforward.common.io.InputStreamNio;
import cn.weforward.common.io.OutputStreamNio;
import cn.weforward.gateway.Pipe;
import cn.weforward.gateway.Tunnel;
import cn.weforward.gateway.exception.BalanceException;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.exception.WeforwardException;

/**
 * 支持微服务转发（单<code>Tunnel</code>对接多<code>Pipe</code>）
 * 
 * @author zhangpengji
 *
 */
class ForwardBridger {
	// static final Logger _Logger = ServiceEndpointBalance._Logger;
	static final Logger _Logger = LoggerFactory.getLogger(ForwardBridger.class);

	ServiceInstanceBalance m_Balance;
	TunnelWrap m_TunnelWrap;
	PipeWrap m_PipeWrap;

	final int m_MaxForward;
	// 已连接过的endpoint
	final List<String> m_Connected;
	volatile InputStream m_TransferBuffer;
	volatile boolean m_End;

	ForwardBridger(ServiceInstanceBalance balance, Tunnel tunnel, int maxForward) throws IOException {
		m_TransferBuffer = tunnel.mirrorTransferStream();

		m_Balance = balance;
		m_TunnelWrap = new TunnelWrap(tunnel);
		m_PipeWrap = new PipeWrap();
		m_MaxForward = maxForward;
		m_Connected = new ArrayList<String>(m_MaxForward + 1);
	}

	void connect(ServiceEndpoint endpoint) {
		m_Connected.add(endpoint.getService().getNo());
		m_PipeWrap.reset();
		boolean supportForward = (checkCount() && null != m_TransferBuffer);
		endpoint.connect(m_TunnelWrap, supportForward);
	}

	int getConnectedCount() {
		return m_Connected.size();
	}

	boolean checkCount() {
		return getConnectedCount() <= m_MaxForward;
	}

	void end() {
		synchronized (getLock()) {
			m_End = true;
		}

		InputStream buffer = m_TransferBuffer;
		if (null != buffer) {
			m_TransferBuffer = null;
			try {
				buffer.close();
			} catch (IOException e) {
				_Logger.error(e.toString(), e);
			}
		}
	}

	private Object getLock() {
		return this;
	}

	InputStream duplicateTransferBuffer() {
		InputStream buffer = m_TransferBuffer;
		if (checkCount()) {
			// 还能继续转发，复制一份
			InputStream copy = null;
			if (buffer instanceof InputStreamNio) {
				try {
					InputStreamNio nio = ((InputStreamNio) buffer).duplicate();
					if (nio instanceof InputStream) {
						copy = (InputStream) nio;
					}
				} catch (IOException e) {
					_Logger.error(e.toString(), e);
				}
			}
			m_TransferBuffer = copy;
		} else {
			m_TransferBuffer = null;
		}
		return buffer;
	}

	void checkPipe(Pipe pipe) {
		// 可能是上一个pipe？
		if (null != m_PipeWrap.m_Pipe && m_PipeWrap.m_Pipe != pipe) {
			throw new IllegalStateException(pipe + "与当前[" + m_PipeWrap.m_Pipe + "]不匹配");
		}
	}

	private class TunnelWrap extends cn.weforward.gateway.util.TunnelWrap {

		TunnelWrap(Tunnel tunnel) {
			super(tunnel);
		}

		// @Override
		// public int getWaitTimeout() {
		// // XXX 应随转发次数减少
		// return m_Tunnel.getWaitTimeout();
		// }

		@Override
		public void responseError(Pipe pipe, int code, String msg) {
			if (!checkPipe(pipe)) {
				return;
			}
			if (m_End) {
				return;
			}

			if (WeforwardException.CODE_SERVICE_FORWARD != code) {
				responseError0(null, WeforwardException.CODE_SERVICE_INVOKE_ERROR, msg);
				return;
			}

			// 尝试转发
			if (_Logger.isDebugEnabled()) {
				_Logger.debug(msg + "，已到达：" + m_Connected);
			}
			if (!checkCount() || null == m_TransferBuffer) {
				msg = msg + "，但被拒绝";
				responseError0(null, WeforwardException.CODE_SERVICE_INVOKE_ERROR, msg);
				return;
			}

			try {
				ServiceEndpoint endpoint = m_Balance.get(pipe.getForwardTo(), getVersion(), m_Connected);
				connect(endpoint);
			} catch (BalanceException e) {
				_Logger.warn(e.toString());
				msg = msg + "，但" + e.getKeyword();
				responseError0(null, WeforwardException.CODE_SERVICE_INVOKE_ERROR, msg);
			} catch (Throwable e) {
				_Logger.error(e.toString(), e);
				responseError0(null, WeforwardException.CODE_INTERNAL_ERROR, "内部错误");
			}
		}

		void responseError0(Pipe pipe, int code, String msg) {
			end();

			if (null != pipe) {
				pipe.requestCanceled(m_TunnelWrap);
			}
			m_Tunnel.responseError(m_PipeWrap, code, msg);
		}

		boolean checkPipe(Pipe pipe) {
			// 可能是上一个pipe？
			Pipe curr = m_PipeWrap.m_Pipe;
			if (null != curr && curr != pipe) {
				_Logger.error("Pipe不匹配：" + curr + " != " + pipe);
				return false;
			}
			return true;
		}

		@Override
		public InputStream mirrorTransferStream() throws IOException {
			throw new IOException("不支持");
		}

		@Override
		public void requestInit(Pipe pipe, int requestMaxSize) {
			Pipe curr = m_PipeWrap.m_Pipe;
			if (null != curr) {
				// 有bug :(
				_Logger.error("Pipe已存在");
				return;
			}
			m_PipeWrap.change(pipe);

			if (1 == getConnectedCount()) {
				// 首次请求
				m_Tunnel.requestInit(m_PipeWrap, requestMaxSize);
			}
		}

		@Override
		public void requestReady(Pipe pipe, OutputStream output) {
			if (!checkPipe(pipe)) {
				return;
			}

			if (1 == getConnectedCount()) {
				firstReady(pipe, output);
			} else {
				secondReady(pipe, output);
			}
		}

		void firstReady(Pipe pipe, OutputStream output) {
			if (m_End) {
				pipe.requestCanceled(m_TunnelWrap);
				return;
			}
			// 首次请求，直接传输原生内容
			m_Tunnel.requestReady(m_PipeWrap, output);
		}

		void secondReady(Pipe pipe, OutputStream output) {
			boolean end;
			InputStream buffer;
			synchronized (getLock()) {
				if (m_End) {
					end = true;
					buffer = null;
				} else {
					end = false;
					buffer = duplicateTransferBuffer();
				}
			}
			if (end) {
				pipe.requestCanceled(m_TunnelWrap);
				return;
			}

			if (null == buffer) {
				// 什么情况？？？
				_Logger.error("TransferBuffer is <null>.");
				responseError0(pipe, WeforwardException.CODE_INTERNAL_ERROR, "内部错误");
				return;
			}

			try {
				if (output instanceof OutputStreamNio) {
					((OutputStreamNio) output).write(buffer);
				} else {
					BytesOutputStream.transfer(buffer, output, -1);
				}
			} catch (IOException e) {
				// 应该不会抛异常
				_Logger.error(e.toString(), e);
				responseError0(pipe, WeforwardException.CODE_SERVICE_INVOKE_ERROR, "IO异常:" + e.getMessage());
				return;
			} finally {
				try {
					buffer.close();
				} catch (IOException e) {
				}
			}
			pipe.requestCompleted(m_TunnelWrap);
		}

		@Override
		public void requestCompleted(Pipe pipe) {
		}

		@Override
		public void responseReady(Pipe pipe) {
			if (!checkPipe(pipe)) {
				return;
			}
			m_Tunnel.responseReady(m_PipeWrap);
		}

		@Override
		public void responseCompleted(Pipe pipe) {
			if (!checkPipe(pipe)) {
				return;
			}
			end();

			m_Tunnel.responseCompleted(m_PipeWrap);
		}

	}

	private class PipeWrap implements Pipe {

		// volatile ServiceEndpoint m_Endpoint;
		volatile Pipe m_Pipe;

		synchronized void reset() {
			m_Pipe = null;
		}

		synchronized void change(Pipe pipe) {
			m_Pipe = pipe;
		}

		@Override
		public Service getService() {
			return m_Pipe.getService();
		}
		
		@Override
		public Header getHeader() {
			return m_Pipe.getHeader();
		}

		@Override
		public String getTag() {
			return m_Pipe.getTag();
		}

		@Override
		public String getResourceId() {
			return m_Pipe.getResourceId();
		}

		@Override
		public long getResourceExpire() {
			return m_Pipe.getResourceExpire();
		}

		@Override
		public String getResourceService() {
			return m_Pipe.getResourceService();
		}
		
		@Override
		public String getResourceServiceNo() {
			return m_Pipe.getResourceServiceNo();
		}

		@Override
		public String getResourceUrl() {
			return m_Pipe.getResourceUrl();
		}
		
		@Override
		public String getForwardTo() {
			return m_Pipe.getForwardTo();
		}

		@Override
		public List<String> getNotifyReceives() {
			return m_Pipe.getNotifyReceives();
		}

		@Override
		public void requestCanceled(Tunnel tunnel) {
			end();

			m_Pipe.requestCanceled(m_TunnelWrap);
		}

		@Override
		public void requestCompleted(Tunnel tunnel) {
			m_Pipe.requestCompleted(m_TunnelWrap);
		}

		@Override
		public void responseReady(Tunnel tunnel, OutputStream output) {
			m_Pipe.responseReady(m_TunnelWrap, output);
		}

	}

}
