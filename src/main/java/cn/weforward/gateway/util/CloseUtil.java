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

import java.io.InputStream;
import java.io.OutputStream;

import org.slf4j.Logger;

import cn.weforward.common.io.OutputStreamNio;
import cn.weforward.common.json.JsonInput;

/**
 * Stream工具集
 * 
 * @author zhangpengji
 *
 */
public class CloseUtil {

	public static void close(InputStream in, Logger logger) {
		if (null != in) {
			try {
				in.close();
			} catch (Throwable e) {
				if (logger.isTraceEnabled()) {
					logger.trace(e.toString(), e);
				}
			}
		}
	}

	public static void close(JsonInput in, Logger logger) {
		if (null != in) {
			try {
				in.close();
			} catch (Throwable e) {
				if (logger.isTraceEnabled()) {
					logger.trace(e.toString(), e);
				}
			}
		}
	}

	public static void close(OutputStream out, Logger logger) {
		if (null != out) {
			try {
				out.close();
			} catch (Throwable e) {
				if (logger.isTraceEnabled()) {
					logger.trace(e.toString(), e);
				}
			}
		}
	}

	public static void cancel(OutputStream out, Logger logger) {
		try {
			if (out instanceof OutputStreamNio) {
				((OutputStreamNio) out).cancel();
				return;
			}
			out.close();
		} catch (Throwable e) {
			if (logger.isTraceEnabled()) {
				logger.trace(e.toString(), e);
			}
		}
	}
}
