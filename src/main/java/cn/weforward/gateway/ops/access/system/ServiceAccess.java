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
package cn.weforward.gateway.ops.access.system;

import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import cn.weforward.common.crypto.Base64;
import cn.weforward.common.crypto.Hex;
import cn.weforward.protocol.ops.AccessExt;

/**
 * 微服务凭证
 * 
 * @author zhangpengji
 *
 */
public class ServiceAccess implements AccessExt {

	/** 状态 - 有效 */
	static final int STATE_VALID = 0x0;
	/** 状态 - 已失效 */
	static final int STATE_INVALID = 0x1;

	ServiceAccessProvider m_Provider;
	ServiceAccessVo m_Vo;

	String m_Kind;
	String m_Group;
	byte[] m_AccessKey;
	String m_AccessKeyHex;
	String m_AccessKeyBase64;

	/**
	 * 由已存在的vo构造
	 * 
	 * @param vo
	 * @param voFactory
	 */
	ServiceAccess(ServiceAccessProvider provider, ServiceAccessVo vo) {
		m_Provider = provider;
		m_Vo = vo;
	}

	// /**
	// * 由主密钥构造
	// *
	// * @param di
	// * @param masterKey
	// * @param kind
	// */
	// public SystemAccess(SystemAccessVoFactory voFactory, MasterKeyVo masterKey,
	// String kind) {
	// m_VoFactory = voFactory;
	// // 使用voFactory创建新id，并替换Access的保留符
	// String ordinal = voFactory.genPersistenceId(null);
	// String id = genId(kind, masterKey, ordinal);
	// m_Vo = new SystemAccessVo();
	// m_Vo.id = id;
	// markUpdate();
	// }

	protected void markUpdate() {
		m_Provider.m_AccessVoFactory.put(m_Vo);
	}

	ServiceAccessVo getVo() {
		// try {
		// SystemAccessVo vo = m_VoFactory.get(m_Vo.id);
		// m_Vo = vo;
		// } catch (Exception e) {
		// _Logger.error("加载[" + m_Vo.id + "]出错，继续使用旧数据", e);
		// }
		return m_Vo;
	}

	void updateVo(ServiceAccessVo vo) {
		m_Vo = vo;
	}

	@Override
	public String getAccessId() {
		return m_Vo.id;
	}

	@Override
	public byte[] getAccessKey() {
		if (null == m_AccessKey) {
			synchronized (this) {
				if (null == m_AccessKey) {
					m_AccessKey = getAccessKey0();
				}
			}
		}
		return m_AccessKey;
	}

	private byte[] getAccessKey0() {
		String accessId = getAccessId();
		MasterKey mk = getMasterKey();
		if (null == mk) {
			throw new IllegalStateException("无法加载[" + accessId + "]的主密钥");
		}
		try {
			// 计算Access Id的HASH值
			java.security.MessageDigest md;
			md = java.security.MessageDigest.getInstance("SHA-256");
			md.update(getAccessId().getBytes("utf-8"));
			// String sign = getOriginalSign();
			// if (!Misc.isEmpty(sign)) {
			// md.update(sign.getBytes("utf-8"));
			// }
			byte[] content = md.digest();
			// 对HASH值与所属的Merchant key进行加密，得到Access Key
			byte[] masterKey = mk.getKey();
			SecretKey sk = new SecretKeySpec(masterKey, "AES"); // 256位的AES需要jdk8支持
			IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(masterKey, 0, 16));
			Cipher cipher = Cipher.getInstance("AES/OFB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, sk, iv);
			return cipher.doFinal(content);
		} catch (Exception e) {
			// 应该不会发生
			throw new IllegalStateException("生成密钥失败", e);
		}
	}

	public MasterKey getMasterKey() {
		return m_Provider.getMasterKeyByAccessId(getAccessId());
	}

	public static String[] parseId(String id) {
		String[] arr = new String[3];
		int bi = 0;
		int ei = id.indexOf(SPEARATOR);
		if (-1 != ei) {
			arr[0] = id.substring(bi, ei);
			bi = ei + 1;
			ei = id.indexOf(SPEARATOR, bi);
			if (-1 != ei) {
				arr[1] = id.substring(bi, ei);
				bi = ei + 1;
				if (bi < id.length()) {
					arr[2] = id.substring(bi);
					return arr;
				}
			}
		}
		return null;
	}

	@Override
	public String getKind() {
		if (null == m_Kind) {
			String id = getAccessId();
			int idx = id.indexOf(SPEARATOR);
			if (-1 == idx) {
				m_Kind = "";
			} else {
				m_Kind = id.substring(0, idx);
			}
		}
		return m_Kind;
	}

	@Override
	public boolean isValid() {
		return STATE_VALID == getState();
	}

	public int getState() {
		return m_Vo.state;
	}

	@Override
	public String getGroupId() {
		if (null == m_Group) {
			m_Group = getMasterKey().getGroupId();
		}
		return m_Group;
	}

	@Override
	public String getSummary() {
		return getVo().summary;
	}

	@Override
	public void setSummary(String summary) {
		getVo().summary = summary;
		markUpdate();
	}

	@Override
	public String getAccessKeyHex() {
		if(null == m_AccessKeyHex) {
			m_AccessKeyHex = Hex.encode(getAccessKey());
		}
		return m_AccessKeyHex;
	}
	
	@Override
	public String getAccessKeyBase64() {
		if(null == m_AccessKeyBase64) {
			m_AccessKeyBase64 = Base64.encode(getAccessKey());
		}
		return m_AccessKeyBase64;
	}

	@Override
	public void setValid(boolean valid) {
		getVo().state = valid ? STATE_VALID : STATE_INVALID;
		markUpdate();
	}

	@Override
	public String getTenant() {
		return null;
	}

	@Override
	public String getOpenid() {
		return null;
	}

}
