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

import java.io.IOException;

import cn.weforward.protocol.datatype.DtObject;
import cn.weforward.protocol.exception.WeforwardException;

/**
 * 同步等待调用结果的管道
 * 
 * @author zhangpengji
 *
 */
public class SyncTunnel extends ImitatedTunnel {

	protected int m_Code;
	protected String m_Msg;
	protected Throwable m_Exception;
	protected DtObject m_Result;

	private volatile boolean m_Done;

	public SyncTunnel(String serviceName, DtObject invokeObject) {
		super(serviceName, invokeObject);
		setWaitTimeout(30 * 1000);
	}

	/**
	 * 获取Weforward响应码
	 * 
	 * @return
	 * @throws Throwable
	 */
	public int getCode() throws InterruptedException, WeforwardException, IOException, Throwable {
		check();
		return m_Code;
	}

	/**
	 * 获取Weforward响应码描述
	 * 
	 * @return
	 * @throws Throwable
	 */
	public String getMsg() throws InterruptedException, WeforwardException, IOException, Throwable {
		check();
		return m_Msg;
	}

	/**
	 * 获取微服务的返回值
	 * 
	 * @return
	 * @throws Throwable
	 */
	public DtObject getResult() throws InterruptedException, WeforwardException, IOException, Throwable {
		check();
		return m_Result;
	}

	private void check() throws InterruptedException, Throwable {
		if (!m_Done) {
			synchronized (this) {
				if (!m_Done) {
					wait(getWaitTimeout() * 1000);
				}
				if (!m_Done) {
					m_Exception = new IOException("等待超时");
					m_Done = true;
				}
			}
		}
		if (null != m_Exception) {
			throw m_Exception;
		}
	}

	@Override
	protected void onError(int code, String msg) {
		m_Code = code;
		m_Msg = msg;
		m_Done = true;
		synchronized (this) {
			notifyAll();
		}
	}

	@Override
	protected void onError(Throwable e) {
		m_Exception = e;
		m_Done = true;
		synchronized (this) {
			notifyAll();
		}
	}

	@Override
	protected void onResult(DtObject result) {
		m_Result = result;
		m_Done = true;
		synchronized (this) {
			notifyAll();
		}
	}

	@Override
	protected void onArrived() {

	}

}
