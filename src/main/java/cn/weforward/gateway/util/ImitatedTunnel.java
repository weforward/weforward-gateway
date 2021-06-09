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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cn.weforward.common.io.BytesInputStream;
import cn.weforward.common.io.BytesOutputStream;
import cn.weforward.common.json.JsonOutputStream;
import cn.weforward.common.util.Bytes;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Pipe;
import cn.weforward.gateway.Tunnel;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.RequestConstants;
import cn.weforward.protocol.ResponseConstants;
import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.exception.SerialException;
import cn.weforward.protocol.exception.WeforwardException;
import cn.weforward.protocol.serial.JsonSerialEngine;

/**
 * 模拟管道。
 * <p>
 * 用于支撑网关直接调用微服务
 * 
 * @author zhangpengji
 *
 */
public abstract class ImitatedTunnel implements Tunnel {

	Header m_Header;
	DtObject m_InvokeObject;
	BytesOutputStream m_Response;

	Access m_Access;
	int m_WaitTimeout;
	String m_Version;
	String m_ResId;
	boolean m_FromGatewayInternal;

	public ImitatedTunnel(String serviceName, DtObject invokeObject) {
		m_Header = Header.valueOf(serviceName);
		m_InvokeObject = invokeObject;
		m_FromGatewayInternal = true;
	}

	@Override
	public Header getHeader() {
		return m_Header;
	}

	// @Override
	// public Access getAccess() {
	// return m_Access;
	// }

	/**
	 * 请求凭证
	 * 
	 * @param accessId
	 */
	public void setAccess(Access access) {
		m_Access = access;
		if (null != access) {
			m_Header.setAccessId(access.getAccessId());
		} else {
			m_Header.setAccessId(null);
		}
	}

	@Override
	public int getWaitTimeout() {
		return m_WaitTimeout;
	}

	public void setWaitTimeout(int second) {
		m_WaitTimeout = second;
	}

	@Override
	public String getVersion() {
		return m_Version;
	}

	public void setVersion(String ver) {
		m_Version = ver;
	}

	@Override
	public String getTraceToken() {
		return null;
	}

	@Override
	public String getResId() {
		return m_ResId;
	}

	public void setResId(String resId) {
		m_ResId = resId;
	}

	@Override
	public String getAddr() {
		return null;
	}

	@Override
	public int getMarks() {
		return 0;
	}

	@Override
	public boolean isFromGatewayInternal() {
		return m_FromGatewayInternal;
	}

	public void setFromGatewayInternal(boolean bool) {
		m_FromGatewayInternal = bool;
	}

	@Override
	public void responseError(Pipe pipe, int code, String msg) {
		onError(code, msg);
	}

	/**
	 * 当响应错误码时
	 * 
	 * @param code
	 *            Weforward错误码
	 * @param msg
	 *            错误描述
	 */
	protected abstract void onError(int code, String msg);

	/**
	 * 当发生异常时
	 * <p>
	 * 大多数情况是以下异常：{@linkplain WeforwardException}、{@linkplain IOException}
	 * 
	 * @param e
	 */
	protected abstract void onError(Throwable e);

	@Override
	public InputStream mirrorTransferStream() throws IOException {
		BytesOutputStream bos = new BytesOutputStream();
		writeInvokeObject(bos);
		Bytes bytes = bos.getBytes();
		return new BytesInputStream(bytes.getBytes(), bytes.getOffset(), bytes.getSize());
	}

	@Override
	public void requestInit(Pipe pipe, int requestMaxSize) {

	}

	@Override
	public void requestReady(Pipe pipe, OutputStream output) {
		IOException error = null;
		try {
			writeInvokeObject(output);
		} catch (IOException e) {
			error = e;
		}
		if (null == error) {
			pipe.requestCompleted(this);
		} else {
			pipe.requestCanceled(this);
			onError(error);
		}
	}

	private void writeInvokeObject(OutputStream output) throws IOException {
		JsonOutputStream jout = new JsonOutputStream(output);
		if (null != m_InvokeObject) {
			jout.append(',');
			jout.append('"').append(RequestConstants.INVOKE).append('"').append(':');
			JsonSerialEngine.formatObject(m_InvokeObject, jout);
		}
		jout.append('}');
	}

	@Override
	public void requestCompleted(Pipe pipe) {
		onArrived();
	}

	protected abstract void onArrived();

	@Override
	public void responseReady(Pipe pipe) {
		if (null == m_Response) {
			try {
				m_Response = new BytesOutputStream();
				JsonOutputStream out = new JsonOutputStream(m_Response);
				// 输出一个空节点
				out.append("{\"n\":null");
			} catch (IOException e) {
				pipe.requestCanceled(this);
				onError(e);
				return;
			}
		}
		pipe.responseReady(this, m_Response);
	}

	@Override
	public void responseCompleted(Pipe pipe) {
		DtObject result = null;
		if (null != m_Response) {
			ByteArrayInputStream bis = null;
			try {
				Bytes bytes = m_Response.getBytes();
				bis = new ByteArrayInputStream(bytes.getBytes(), bytes.getOffset(), bytes.getSize());
				DtObject obj = JsonSerialEngine.parseObject(bis, Header.CHARSET_UTF8);
				result = obj.getObject(ResponseConstants.RESULT);
			} catch (IOException | SerialException e) {
				onError(e);
			} finally {
				bis = null;
				m_Response = null;
			}
		}
		onResult(result);
	}

	protected abstract void onResult(DtObject result);

	// @Override
	// public OutputStream getOutput() throws IOException {
	// if (null == m_Response) {
	// throw new IOException("尚未调用responseReady");
	// }
	// return m_Response;
	// }

	@Override
	public String toString() {
		String name = getClass().getSimpleName();
		if (StringUtil.isEmpty(name)) {
			name = getClass().getName();
		}
		return name + ":" + m_Header.getService();
	}
}
