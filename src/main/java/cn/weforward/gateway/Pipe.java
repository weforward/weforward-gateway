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
package cn.weforward.gateway;

import java.io.OutputStream;
import java.util.List;

import cn.weforward.protocol.Header;
import cn.weforward.protocol.Response;
import cn.weforward.protocol.Service;

/**
 * 网关与微服务端之间的管道
 * 
 * @author zhangpengji
 *
 */
public interface Pipe {

	/** @see Header#getTag() */
	String getTag();

	/** @see Response#getResourceId() */
	String getResourceId();

	/** @see Response#getResourceExpire() */
	long getResourceExpire();
	
	/** @see Response#getResourceService() */
	String getResourceService();

	/** @see Response#getForwardTo() */
	String getForwardTo();

	/** @see Response#getNotifyReceives() */
	List<String> getNotifyReceives();

	/**
	 * 管道所属的微服务实例，可空
	 * 
	 * @return
	 */
	Service getService();

	/**
	 * 客户端管道的请求已取消
	 */
	void requestCanceled(Tunnel tunnel);

	/**
	 * 客户端管道的请求已完成
	 */
	void requestCompleted(Tunnel tunnel);

	/**
	 * 客户端管道的响应输出流已就绪
	 * 
	 * @param tunnel
	 */
	void responseReady(Tunnel tunnel, OutputStream output);
}
