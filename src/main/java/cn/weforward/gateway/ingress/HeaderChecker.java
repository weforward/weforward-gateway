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

import cn.weforward.common.util.StringUtil;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.exception.WeforwardException;

/**
 * 
 * @author zhangpengji
 *
 */
class HeaderChecker {

	static CheckResult check(Header header) {
		String authType = header.getAuthType();
		if (StringUtil.isEmpty(authType)) {
			return new CheckResult(WeforwardException.CODE_AUTH_TYPE_INVALID, "'auth_type' is require");
		}
		String contentType = header.getContentType();
		if (!Header.CONTENT_TYPE_JSON.equalsIgnoreCase(contentType)) {
			// 暂时仅支持json
			return new CheckResult(WeforwardException.CODE_SERIAL_ERROR, "content type unsupport:" + contentType);
		}
		String charset = header.getCharset();
		if (!Header.CHARSET_UTF8.equalsIgnoreCase(charset)) {
			// 暂时仅支持utf-8
			return new CheckResult(WeforwardException.CODE_SERIAL_ERROR, "charset unsupport:" + charset);
		}
		String channel = header.getChannel();
		if (!StringUtil.isEmpty(channel) && !Header.CHANNEL_ALL.contains(channel)) {
			return new CheckResult(WeforwardException.CODE_SERIAL_ERROR, "channel invalid:" + channel);
		}
		return SUCCESS;
	}

	static class CheckResult {
		int code;
		String msg;

		CheckResult(int code, String msg) {
			this.code = code;
			this.msg = msg;
		}
	}

	static final CheckResult SUCCESS = new CheckResult(0, null);
}
