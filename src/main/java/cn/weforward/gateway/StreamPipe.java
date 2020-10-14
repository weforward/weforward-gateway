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

/**
 * 网关与微服务端之间的流管道
 * 
 * @author zhangpengji
 *
 */
public interface StreamPipe {

	/**
	 * 内容类型，如：image/png
	 * 
	 * @return
	 */
	String getContentType();

	/**
	 * 内容展示形式
	 * 
	 * @return
	 */
	String getContentDisposition();

	/**
	 * 内容长度。0表示未知
	 * 
	 * @return
	 */
	long getLength();

	/**
	 * 客户端管道的请求已取消
	 */
	void requestCanceled(StreamTunnel tunnel);

	/**
	 * 客户端管道的请求已完成
	 */
	void requestCompleted(StreamTunnel tunnel);

	/**
	 * 客户端管道的响应输出流已就绪
	 * 
	 * @param tunnel
	 */
	void responseReady(StreamTunnel tunnel, OutputStream output);
}
