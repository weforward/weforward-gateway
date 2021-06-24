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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.crypto.Base64;
import cn.weforward.common.crypto.Hex;
import cn.weforward.common.json.JsonObject;
import cn.weforward.common.json.JsonPair;
import cn.weforward.common.json.JsonUtil;
import cn.weforward.common.json.SimpleJsonObject;
import cn.weforward.common.json.StringInput;
import cn.weforward.common.json.StringOutput;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.GatewayExt;
import cn.weforward.gateway.api.GatewayApis;
import cn.weforward.gateway.core.MetricsCollecter;
import cn.weforward.gateway.ops.access.AccessManage;
import cn.weforward.gateway.ops.right.RightManage;
import cn.weforward.gateway.util.OverloadLimit;
import cn.weforward.protocol.AccessLoader;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.ServiceName;
import cn.weforward.protocol.ext.Producer;

/**
 * 为业务处理器提供支撑
 * 
 * @author zhangpengji
 *
 */
public class ServerHandlerSupporter {
	static final Logger _Logger = LoggerFactory.getLogger(ServerHandlerSupporter.class);

	AccessManage m_AccessManage;
	RightManage m_RightManage;
	GatewayExt m_Gateway;
	Producer m_Producer;
	GatewayApis m_GatewayApis;
	Executor m_ApiExecutor;
	Executor m_RpcExecutor;
	Executor m_DocExecutor;
	MetricsCollecter m_MetricsCollecter;

	String m_ResourcePreUrl;
	SecretKey m_ResourceTokenKey;
	IvParameterSpec m_ResourceTokenIv;

	OverloadLimit m_StreamLimit;

	public ServerHandlerSupporter() {
		m_StreamLimit = new OverloadLimit(Configure.getInstance().getStreamChannelMaxConcurrent());
	}

	public void setAccessManage(AccessManage am) {
		m_AccessManage = am;
	}

	public void setRightManage(RightManage rm) {
		m_RightManage = rm;
	}

	public void setGateway(GatewayExt gw) {
		m_Gateway = gw;
	}

	public void setProducer(Producer producer) {
		m_Producer = producer;
	}

	public void setGatewayApis(GatewayApis apis) {
		m_GatewayApis = apis;
	}

	public void setApiExecutor(Executor executor) {
		m_ApiExecutor = executor;
	}

	public void setRpcExecutor(Executor executor) {
		m_RpcExecutor = executor;
	}

	public void setDocExecutor(Executor executor) {
		m_DocExecutor = executor;
	}

	public void setResourcePreUrl(String url) {
		if (StringUtil.isEmpty(url)) {
			m_ResourcePreUrl = url;
			return;
		}
		m_ResourcePreUrl = genResourcePreUrl(url, false);
	}

	private String genResourcePreUrl(String url, boolean https) {
		StringBuilder sb = new StringBuilder(100);
		if (https && url.startsWith("http://")) {
			sb.append("https://").append(url.substring(7));
		} else {
			sb.append(url);
		}
		if (!url.endsWith("/")) {
			sb.append("/");
		}
		return sb.append(ServiceName.STREAM.name).append("/").toString();
	}

	public void setResourceSecret(String secret) throws NoSuchAlgorithmException, UnsupportedEncodingException {
		byte[] data;
		if (64 == secret.length()) {
			data = Hex.decode(secret);
		} else {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(secret.getBytes("utf-8"));
			data = md.digest();
		}
		// 256位的AES需要jdk8支持
		m_ResourceTokenKey = new SecretKeySpec(data, "AES");
		m_ResourceTokenIv = new IvParameterSpec(data, 0, 16);
	}

	public void setMetricsCollecter(MetricsCollecter collecter) {
		m_MetricsCollecter = collecter;
	}

	public AccessLoader getAccessLoader() {
		return m_AccessManage;
	}

	public AccessManage getAccessManage() {
		return m_AccessManage;
	}

	// /**
	// * 检查访问源是否可信
	// *
	// * @param address
	// * 访问者地址，如“127.0.0.1:12345”
	// * @return true为可信、false为不可信、null为未知
	// */
	// public Boolean checkFrom(String address) {
	// // TODO
	// return null;
	// }

	public RightManage getRightManage() {
		return m_RightManage;
	}

	/**
	 * 获取默认的数据制作器
	 * 
	 * @return
	 */
	public Producer getProducer() {
		return m_Producer;
	}

	/**
	 * 获取网关Api集合
	 * 
	 * @return
	 */
	public GatewayApis getGateWayApis() {
		return m_GatewayApis;
	}

	/**
	 * 获取网关
	 * 
	 * @return
	 */
	public GatewayExt getGateway() {
		return m_Gateway;
	}

	/**
	 * 获取api的执行器
	 * 
	 * @return
	 */
	public Executor getApiExecutor() {
		return m_ApiExecutor;
	}

	/**
	 * 获取处理rpc,event的执行器
	 * 
	 * @return
	 */
	public Executor getRpcExecutor() {
		return m_RpcExecutor;
	}

	/**
	 * 获取文档请求的执行器
	 * 
	 * @return
	 */
	public Executor getDocExecutor() {
		return m_DocExecutor;
	}

	/**
	 * 根据uri，解析出token
	 * 
	 * @param queryString
	 * @return
	 * @throws IllegalArgumentException
	 *             解析失败时抛出
	 */
	public StreamResourceToken parseResourceToken(String queryString) throws IllegalArgumentException {
		if (StringUtil.isEmpty(queryString)) {
			throw new IllegalArgumentException("'uri' is require.");
		}
		String tokenParam = "token=";
		int idx = queryString.indexOf(tokenParam);
		if (0 != idx || tokenParam.length() == queryString.length()) {
			throw new IllegalArgumentException("缺少token参数");
		}
		String tokenValue = queryString.substring(idx + tokenParam.length());
		try {
			tokenValue = URLDecoder.decode(tokenValue, Header.CHARSET_DEFAULT);
			byte[] content = Base64.decode(tokenValue);
			// 解密
			Cipher cipher = Cipher.getInstance("AES/OFB/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, m_ResourceTokenKey, m_ResourceTokenIv);
			byte[] plain = cipher.doFinal(content, 0, content.length - 4);
			// 校验crc
			int crc32Verify = ((content[content.length - 4] << 24) & 0xFF000000);
			crc32Verify |= ((content[content.length - 3] << 16) & 0xFF0000);
			crc32Verify |= ((content[content.length - 2] << 8) & 0xFF00);
			crc32Verify |= ((content[content.length - 1]) & 0xFF);
			CRC32 crc = new CRC32();
			crc.update(plain);
			int crc32 = (int) crc.getValue();
			if (crc32Verify != crc32) {
				throw new IllegalArgumentException("token校验crc失败：" + crc32Verify + " != " + crc32);
			}
			String json = new String(plain, 0, plain.length, "utf-8");
			JsonObject jo = (JsonObject) JsonUtil.parse(new StringInput(json), null);
			String service = getValue(jo, "s");
			String serviceNo = getValue(jo, "no");
			Number expire = getValue(jo, "exp");
			String resId = getValue(jo, "id");
			StreamResourceToken token = new StreamResourceToken();
			token.setServiceName(service);
			token.setServiceNo(serviceNo);
			token.setResourceId(resId);
			token.setExpire(expire.longValue());
			return token;
		} catch (Throwable e) {
			throw new IllegalArgumentException("解析token失败:" + tokenValue, e);
		}
	}

	@SuppressWarnings("unchecked")
	private static <E> E getValue(JsonObject obj, String key) {
		JsonPair pair = obj.property(key);
		if (null == pair) {
			return null;
		}
		return (E) pair.getValue();
	}

	/**
	 * 根据token，生成资源的访问链接。生成失败返回null
	 * 
	 * @param token
	 * @return
	 */
	public String genResourceUrl(StreamResourceToken token) {
		String service = token.getServiceName();
		long resExpire = token.getExpire();
		String resId = token.getResourceId();
		if (StringUtil.isEmpty(service) || 0 >= resExpire || StringUtil.isEmpty(resId)) {
			return null;
		}
		try {
			SimpleJsonObject jo = new SimpleJsonObject();
			jo.add("s", service);
			jo.add("no", token.getServiceNo());
			jo.add("exp", resExpire);
			jo.add("id", resId);
			StringOutput out = new StringOutput();
			JsonUtil.format(jo, out);
			byte[] plain = out.toString().getBytes("utf-8");
			// 加密
			Cipher cipher = Cipher.getInstance("AES/OFB/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, m_ResourceTokenKey, m_ResourceTokenIv);
			byte[] encrypt = cipher.doFinal(plain);
			byte[] content = Arrays.copyOf(encrypt, encrypt.length + 4);
			// 追加crc校验
			CRC32 crc = new CRC32();
			crc.update(plain);
			int value = (int) crc.getValue();
			content[content.length - 4] = (byte) ((value >> 24) & 0xFF);
			content[content.length - 3] = (byte) ((value >> 16) & 0xFF);
			content[content.length - 2] = (byte) ((value >> 8) & 0xFF);
			content[content.length - 1] = (byte) (value & 0xFF);
			return getResourcePreUrl(service) + "?token="
					+ URLEncoder.encode(Base64.encode(content), Header.CHARSET_DEFAULT);
		} catch (Throwable e) {
			_Logger.error("生成resource token失败：" + resId, e);
			return null;
		}
	}

	private String getResourcePreUrl(String serviceName) {
		return m_ResourcePreUrl;
	}

	public MetricsCollecter getMetricsCollecter() {
		return m_MetricsCollecter;
	}

	public OverloadLimit getStreamOverloadLimit() {
		return m_StreamLimit;
	}
}
