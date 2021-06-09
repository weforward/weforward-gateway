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

import cn.weforward.common.NameItem;

public class BalanceException extends Exception {
	private static final long serialVersionUID = 1L;

	public static final NameItem CODE_UNKNOWN = NameItem.valueOf("未知", 0x0);
	public static final NameItem CODE_OVERLOAD = NameItem.valueOf("过载", 0x1);
	public static final NameItem CODE_FAIL_DURING = NameItem.valueOf("失败过多", 0x2);
	public static final NameItem CODE_VERSION_NOT_MATCH = NameItem.valueOf("版本不匹配", 0x4);
	public static final NameItem CODE_EXCLUDE = NameItem.valueOf("被排除", 0x8);
	public static final NameItem CODE_NO_NOT_MATCH = NameItem.valueOf("编号不匹配", 0x10);
	public static final NameItem CODE_ALL_OVERLOAD = NameItem.valueOf("全过载", 0x20);
	public static final NameItem CODE_ALL_FAIL = NameItem.valueOf("全失败", 0x40);
	public static final NameItem CODE_ALL_BUSY = NameItem.valueOf("全忙", 0x80);
	public static final NameItem CODE_NO_SELF_MESH = NameItem.valueOf("非本网格实例", 0x100);

	public static final NameItem CODE_OVER_QUOTAS = NameItem.valueOf("超额", 0x10000);
	public static final NameItem CODE_FULL_QUOTAS = NameItem.valueOf("满额", 0x20000);

	int m_Code;
	String m_Keyword;

	public BalanceException(String serviceName, String errMsg, String keyword) {
		super("微服务[" + serviceName + "]" + errMsg, null, false, false);
		m_Keyword = keyword;
	}

	protected BalanceException(String serviceName, String errMsg, NameItem keyword) {
		super("微服务[" + serviceName + "]" + errMsg, null, false, false);
		m_Code = keyword.id;
		m_Keyword = keyword.name;
	}
	
	public int getCode() {
		return m_Code;
	}

	public String getKeyword() {
		return m_Keyword;
	}

	public static BalanceException overload(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, CODE_OVERLOAD);
	}

	public static BalanceException failDuring(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, CODE_FAIL_DURING);
	}

	public static BalanceException versionNotMatch(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, CODE_VERSION_NOT_MATCH);
	}

	public static BalanceException exclude(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, CODE_EXCLUDE);
	}

	public static BalanceException noNotMatch(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, CODE_NO_NOT_MATCH);
	}

	public static BalanceException allOverload(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, CODE_ALL_OVERLOAD);
	}

	public static BalanceException allFail(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, CODE_ALL_FAIL);
	}

	public static BalanceException allBusy(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, CODE_ALL_BUSY);
	}

	public static BalanceException noSelfMesh(String serviceName, String errMsg) {
		return new BalanceException(serviceName, errMsg, CODE_NO_SELF_MESH);
	}
}
