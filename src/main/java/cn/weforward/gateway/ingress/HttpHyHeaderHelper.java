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

import java.io.IOException;

import cn.weforward.common.Dictionary;
import cn.weforward.common.util.StringUtil;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpHeaderOutput;

/**
 * 兼容Hy的Http头信息
 * 
 * @author zhangpengji
 *
 */
public class HttpHyHeaderHelper {

	/**
	 * 输出头信息
	 * 
	 * @param header
	 * @return
	 * @throws IOException
	 */
	public static void outHeaders(Header header, HttpHeaderOutput output) throws IOException {
		output.put(HttpConstants.CONTENT_TYPE,
				"application/" + header.getContentType() + ";charset=" + header.getCharset());
		String authType = header.getAuthType();
		if (!StringUtil.isEmpty(authType)) {
			StringBuilder authHeader = new StringBuilder();
			authHeader.append(authType.replace("WF-", "HY-"));
			if (null != header.getAccessId() && header.getAccessId().length() > 0) {
				authHeader.append(' ').append(header.getAccessId());
				if (null != header.getSign() && header.getSign().length() > 0) {
					authHeader.append(':').append(header.getSign());
				}
			}
			output.put(HttpConstants.AUTHORIZATION, authHeader.toString());
		}
		String noise = header.getNoise();
		if (!StringUtil.isEmpty(noise)) {
			output.put("HY-Noise", noise);
		}
		String tag = header.getTag();
		if (!StringUtil.isEmpty(tag)) {
			output.put("HY-Tag", tag);
		}
		String channel = header.getChannel();
		if (!StringUtil.isEmpty(channel)) {
			output.put("HY-Channel", channel);
		}
		String contentSign = header.getContentSign();
		if (!StringUtil.isEmpty(contentSign)) {
			output.put("HY-Content-Sign", contentSign);
		}
	}

	/**
	 * 由http头信息创建Header
	 * 
	 * @param hs
	 * @return
	 */
	public static void fromHttpHeaders(Dictionary<String, String> hs, Header header) {
		int idx;
		String contentType = hs.get(HttpConstants.CONTENT_TYPE);
		if (null != contentType && contentType.length() > 0) {
			contentType = contentType.toLowerCase();
			if (contentType.contains("json")) {
				header.setContentType(Header.CONTENT_TYPE_JSON);
			}
			idx = contentType.indexOf("charset=");
			if (-1 != idx) {
				header.setCharset(contentType.substring(idx + 8));
			}
		}
		String auth = hs.get(HttpConstants.AUTHORIZATION);
		if (null != auth && auth.length() > 0) {
			auth = auth.trim();
			idx = auth.indexOf(' ');
			if (-1 == idx) {
				header.setAuthType(auth.replace("HY-", "WF-"));
			} else {
				header.setAuthType(auth.substring(0, idx).replace("HY-", "WF-"));
				int idx2 = auth.indexOf(':');
				if (-1 == idx2) {
					header.setAccessId(auth.substring(idx + 1));
				} else {
					header.setAccessId(auth.substring(idx + 1, idx2));
					header.setSign(auth.substring(idx2 + 1));
				}
			}
		}
		String noise = hs.get("HY-Noise");
		header.setNoise(noise);
		String tag = hs.get("HY-Tag");
		header.setTag(tag);
		String channel = hs.get("HY-Channel");
		header.setChannel(channel);
		String userAgent = hs.get(HttpConstants.USER_AGENT);
		header.setUserAgent(userAgent);
		String contentSign = hs.get("HY-Content-Sign");
		header.setContentSign(contentSign);
	}
}
