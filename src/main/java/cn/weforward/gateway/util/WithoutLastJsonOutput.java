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
import java.io.OutputStream;

import cn.weforward.common.json.JsonOutputStream;

/**
 * 忽略最后一个'}'的json输出
 * 
 * @author zhangpengji
 *
 */
public class WithoutLastJsonOutput extends JsonOutputStream {
	int m_Mark;
	boolean m_Escape;
	boolean m_Quot;

	public WithoutLastJsonOutput(OutputStream stream) {
		super(stream);
	}

	@Override
	public Appendable append(char ch) throws IOException {
		if ('\\' == ch) {
			// 转义符
			m_Escape = true;
			return super.append(ch);
		}
		if (m_Escape) {
			// 上一个字符是转义符
			m_Escape = false;
			return super.append(ch);
		}
		if ('"' == ch) {
			if (!m_Quot) {
				// 引号开始
				m_Quot = true;
			} else {
				// 引号结束
				m_Quot = false;
			}
			return super.append(ch);
		}
		if (m_Quot) {
			// 在引号中
			return super.append(ch);
		}
		if ('{' == ch) {
			m_Mark++;
		} else if ('}' == ch) {
			if (1 == m_Mark) {
				// 忽略最后一个
				return this;
			} else {
				m_Mark--;
			}
		}
		return super.append(ch);
	}
}
