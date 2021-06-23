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
 * 客户端与网关之间的流管道
 * 
 * @author zhangpengji
 *
 */
public interface StreamTunnel {

	/** 错误码 - 成功 */
	int CODE_OK = 200;
	/** 错误码 - 微服务已收到请求，但响应超时 */
	int CODE_ACCEPTED = 202;
	/** 错误码 - 参数异常 */
	int CODE_ILLEGAL_ARGUMENT = 400;
	/** 错误码 - 禁止访问 */
	int CODE_FORBIDDEN = 403;
	/** 错误码 - 资源或微服务不存在 */
	int CODE_NOT_FOUND = 404;
	/** 错误码 - 资源过大 */
	int CODE_TOO_LARGE = 413;
	/** 错误码 - 内部错误 */
	int CODE_INTERNAL_ERROR = 500;
	/** 错误码 - 不可用 */
	int CODE_UNAVAILABLE = 503;

	/**
	 * 服务名
	 * 
	 * @return
	 */
	String getServiceName();

	/**
	 * 服务实例编号
	 * 
	 * @return
	 */
	String getServiceNo();

	/**
	 * 资源标识
	 * 
	 * @return
	 */
	String getResourceId();

	/**
	 * 内容类型，如：image/png
	 * 
	 * @return
	 */
	String getContentType();

	/**
	 * 内容长度。0表示未知
	 * 
	 * @return
	 */
	long getLength();

	/**
	 * 网关认证类型
	 * 
	 * @return
	 */
	default String getGatewayAuthType() {
		return null;
	}

	/**
	 * 是否中继
	 * 
	 * @return
	 */
	default boolean isRelay() {
		return false;
	}

	/**
	 * 微服务管道的请求输出流已就绪
	 * 
	 * @param pipe
	 */
	void requestReady(StreamPipe pipe, OutputStream output);

	/**
	 * 微服务管道的响应输入流已就绪
	 * 
	 * @param pipe
	 * @param httpCode 当pipe使用http协议时，传入微服务端返回的http状态码。当不为0时，将忽略code参数
	 * @param errCode  错误码，如:{@linkplain #CODE_NOT_FOUND}
	 */
	void responseReady(StreamPipe pipe, int httpCode, int errCode);

	/**
	 * 微服务管道的响应输入流已完成
	 * 
	 * @param pipe
	 */
	void responseCompleted(StreamPipe pipe);

	/**
	 * 输出错误
	 * 
	 * @param pipe 已对接的<code>StreamPipe</code>。未与建立连接时传null
	 * @param code 错误码，如:{@linkplain #CODE_NOT_FOUND}
	 * @param msg
	 */
	void responseError(StreamPipe pipe, int code, String msg);
}
