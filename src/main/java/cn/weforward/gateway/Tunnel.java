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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import cn.weforward.protocol.Header;

/**
 * 客户端与网关之间的管道
 * 
 * @author zhangpengji
 *
 */
public interface Tunnel {

	// /**
	// * ip地址
	// *
	// * @return
	// */
	// String getIp();

	/**
	 * 请求头
	 * 
	 * @return
	 */
	Header getHeader();

	// /**
	// * 请求凭证
	// *
	// * @return
	// */
	// Access getAccess();

	/**
	 * 获取请求等待时间，单位：秒
	 * 
	 * @return
	 */
	int getWaitTimeout();

	/**
	 * 获取微服务版本号
	 * 
	 * @return
	 */
	String getVersion();

	/**
	 * 获取微服务调用跟踪令牌
	 * 
	 * @return
	 */
	String getTraceToken();

	/**
	 * 资源标识
	 * 
	 * @return
	 */
	String getResId();

	/**
	 * ip地址
	 * 
	 * @return
	 */
	String getAddr();

	/**
	 * 请求标识
	 * 
	 * @return
	 */
	int getMarks();

	/**
	 * 是否来自网关内部
	 * 
	 * @return
	 */
	default boolean isFromGatewayInternal() {
		return false;
	}

	// /**
	// * 输出错误
	// *
	// * @param code
	// * @param msg
	// */
	// void responseError(int code, String msg);

	/**
	 * 输出错误
	 * 
	 * @param pipe
	 *            已对接的微服务管道。未与微服务建立连接时传null
	 * @param code
	 * @param msg
	 */
	void responseError(Pipe pipe, int code, String msg);

	/**
	 * 创建转发内容的镜像流。必须得在{@linkplain #requestReady(Pipe, OutputStream)}前调用
	 * 
	 * @return
	 */
	InputStream mirrorTransferStream() throws IOException;

	/**
	 * 微服务管道已初始化
	 * 
	 * @param pipe
	 * @param requestMaxSize
	 *            请求数据的最大大小，0为不指定
	 */
	void requestInit(Pipe pipe, int requestMaxSize);

	/**
	 * 微服务管道的请求输出流已就绪
	 * 
	 * @param pipe
	 * @param output
	 */
	void requestReady(Pipe pipe, OutputStream output);

	/**
	 * 微服务管道的请求输出已完成
	 * 
	 * @param pipe
	 */
	void requestCompleted(Pipe pipe);

	/**
	 * 微服务管道的响应输入流已就绪
	 * 
	 * @param pipe
	 */
	void responseReady(Pipe pipe);

	/**
	 * 微服务管道的响应输入流已完成
	 * 
	 * @param pipe
	 */
	void responseCompleted(Pipe pipe);
}
