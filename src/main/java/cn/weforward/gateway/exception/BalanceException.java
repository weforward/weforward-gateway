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
package cn.weforward.gateway.exception;

public class BalanceException extends Exception {
	private static final long serialVersionUID = 1L;

	String m_Keyword;

	public BalanceException(String serviceName, String errMsg, String keyword) {
		super("微服务[" + serviceName + "]" + errMsg, null, false, false);
		m_Keyword = keyword;
	}

	public String getKeyword() {
		return m_Keyword;
	}

	public static BalanceException overload(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, "过载");
	}

	public static BalanceException failDuring(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, "失败过多");
	}

	public static BalanceException versionNotMatch(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, "版本不匹配");
	}

	public static BalanceException exclude(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, "被排除");
	}

	static BalanceException noNotMatch(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, "编号不匹配");
	}

	public static BalanceException allOverload(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, "全过载");
	}

	public static BalanceException allFail(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, "全失败");
	}

	public static BalanceException notFound(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, "全忙");
	}
}
