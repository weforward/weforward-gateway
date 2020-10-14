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
package cn.weforward.gateway.ingress;

/**
 * 流资源的令牌
 * 
 * @author zhangpengji
 *
 */
public class StreamResourceToken {

	String m_ServiceName;
	String m_ServiceNo;
	String m_ResourceId;
	long m_Expire;

	public StreamResourceToken() {

	}

	public String getServiceName() {
		return m_ServiceName;
	}

	public void setServiceName(String serviceName) {
		m_ServiceName = serviceName;
	}

	public String getServiceNo() {
		return m_ServiceNo;
	}

	public void setServiceNo(String serviceNo) {
		m_ServiceNo = serviceNo;
	}

	public String getResourceId() {
		return m_ResourceId;
	}

	public void setResourceId(String resourceId) {
		m_ResourceId = resourceId;
	}

	public long getExpire() {
		return m_Expire;
	}

	public void setExpire(long expire) {
		m_Expire = expire;
	}

	public boolean isExpire() {
		return m_Expire * 1000 < System.currentTimeMillis();
	}
}
