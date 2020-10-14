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

import cn.weforward.gateway.Tunnel;
import cn.weforward.protocol.Header;

/**
 * Tunnel封装类
 * 
 * @author zhangpengji
 *
 */
public abstract class TunnelWrap implements Tunnel {

	protected volatile Tunnel m_Tunnel;

	protected TunnelWrap() {

	}

	protected TunnelWrap(Tunnel tunnel) {
		m_Tunnel = tunnel;
	}

	@Override
	public Header getHeader() {
		return m_Tunnel.getHeader();
	}

	// @Override
	// public Access getAccess() {
	// return m_Tunnel.getAccess();
	// }

	@Override
	public int getWaitTimeout() {
		return m_Tunnel.getWaitTimeout();
	}

	@Override
	public String getVersion() {
		return m_Tunnel.getVersion();
	}

	@Override
	public String getTraceToken() {
		return m_Tunnel.getTraceToken();
	}

	@Override
	public String getResId() {
		return m_Tunnel.getResId();
	}

	@Override
	public String getAddr() {
		return m_Tunnel.getAddr();
	}

	@Override
	public int getMarks() {
		return m_Tunnel.getMarks();
	}

}
