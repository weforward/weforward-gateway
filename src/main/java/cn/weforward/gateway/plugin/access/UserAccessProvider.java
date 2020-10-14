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
package cn.weforward.gateway.plugin.access;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.GcCleanable;
import cn.weforward.common.ResultPage;
import cn.weforward.common.crypto.Base64;
import cn.weforward.common.crypto.Hex;
import cn.weforward.common.sys.GcCleaner;
import cn.weforward.common.util.LruCache;
import cn.weforward.common.util.ResultPageHelper;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.LruCache.CacheNode;
import cn.weforward.gateway.AccessLoaderExt;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.Gateway;
import cn.weforward.gateway.ops.access.AccessProvider;
import cn.weforward.gateway.plugin.AccessLoaderAware;
import cn.weforward.gateway.plugin.GatewayAware;
import cn.weforward.gateway.plugin.PropertiesLoader;
import cn.weforward.gateway.plugin.PropertiesLoaderAware;
import cn.weforward.gateway.util.SyncTunnel;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.RequestConstants;
import cn.weforward.protocol.ops.AccessExt;
import cn.weforward.protocol.support.datatype.FriendlyObject;
import cn.weforward.protocol.support.datatype.SimpleDtObject;

/**
 * 用户访问凭证实现
 * 
 * @author daibo
 *
 */
public class UserAccessProvider
		implements AccessProvider, GatewayAware, PropertiesLoaderAware, AccessLoaderAware, GcCleanable {

	static final Logger _Logger = LoggerFactory.getLogger(UserAccessProvider.class);

	static final String ACCESS_PREFIX = Access.KIND_USER + Access.SPEARATOR_STR;

	static final String OAUTH_KEY = ACCESS_PREFIX + 'O' + Access.SPEARATOR;

	static final int ID_MAX_LENGTH = 100;

	protected Gateway m_Gateway;

	protected byte[] m_Secret;

	protected AccessLoaderExt m_AccessLoader;

	protected LruCache<String, UserAccess> m_AccessCache;
	protected LruCache.Loader<String, UserAccess> m_AccessCacheLoader;

	public UserAccessProvider() {
		int maxCapacity = Configure.getInstance().getUserAccessCacheMaxCapacity();
		m_AccessCache = new LruCache<String, UserAccessProvider.UserAccess>(maxCapacity, "user_access");
		m_AccessCache.setNullTimeout(3 * 1000);
		m_AccessCacheLoader = new LruCache.Loader<String, UserAccess>() {

			@Override
			public UserAccess load(String key, CacheNode<String, UserAccess> node) {
				if (key.startsWith(OAUTH_KEY)) {
					int s = OAUTH_KEY.length();
					String serviceName = key.substring(s, key.indexOf(Access.SPEARATOR, s));
					return getOpenAccess(serviceName, key);
				}
				return openAccess(key);
			}
		};
		GcCleaner.register(this);
	}

	@Override
	public AccessExt createAccess(String groupId) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void onGcCleanup(int policy) {
		if (null != m_AccessCache) {
			m_AccessCache.onGcCleanup(policy);
		}
	}

	@Override
	public AccessExt getAccess(String id) {
		if (null == id || id.length() > ID_MAX_LENGTH) {
			return null;
		}
		return m_AccessCache.getHintLoad(id, m_AccessCacheLoader);
	}

	private UserAccess getOpenAccess(String serviceName, String id) {
		SimpleDtObject invokeObject = new SimpleDtObject();
		String method = "get_access";
		invokeObject.put(RequestConstants.METHOD, "get_access");
		SimpleDtObject params = new SimpleDtObject();
		params.put("id", id);
		invokeObject.put(RequestConstants.PARAMS, params);
		SyncTunnel tunnel = new SyncTunnel(serviceName, invokeObject);
		// 使用网关凭证
		Access access = m_AccessLoader.getInternalAccess();
		tunnel.setAccess(access);
		m_Gateway.joint(tunnel);
		try {
			int code = tunnel.getCode();
			if (0 != code) {
				String errMsg = code + "/" + tunnel.getMsg();
				_Logger.warn("调用" + serviceName + "." + method + "出错，异常:  " + errMsg);
				return null;
			}
			FriendlyObject result = FriendlyObject.valueOf(tunnel.getResult());
			code = result.getInt("code", -1);
			if (0 != code) {
				String errMsg = code + "/" + tunnel.getMsg();
				_Logger.warn("调用" + serviceName + "." + method + "出错，异常:  " + errMsg);
				return null;
			}
			result = FriendlyObject.valueOf(result.getObject("content"));
			String accessId = result.getString("accessId");
			if (!id.equals(accessId)) {
				_Logger.warn("access id不一致：" + id + " != " + accessId);
				return null;
			}
			accessId = id;
			byte[] accessKey = Base64.decode(result.getString("accessKey"));
			long expire = result.getLong("expire");
			UserAccess useraccess = new UserAccess(accessId, accessKey, expire);
			useraccess.group = result.getString("group");
			useraccess.summary = result.getString("summary");
			useraccess.tenant = result.getString("tenant");
			useraccess.openid = result.getString("openid");
			return useraccess;
		} catch (Throwable e) {
			_Logger.warn("调用" + serviceName + "." + method + "出错", e);
		}
		return null;
	}

	@Override
	public ResultPage<AccessExt> listAccess(String groupId, String keyword) {
		return ResultPageHelper.empty();
	}

	@Override
	public List<String> listAccessGroup() {
		return Collections.emptyList();
	}

	@Override
	public String getKind() {
		return Access.KIND_USER;
	}

	@Override
	public void setGateway(Gateway gateway) {
		m_Gateway = gateway;
	}

	@Override
	public void setAccessLoader(AccessLoaderExt loader) {
		m_AccessLoader = loader;
	}

	@Override
	public void setPropertiesLoader(PropertiesLoader loader) {
		Properties prop = loader.loadProperties("useraccess");
		if (null == prop) {
			return;
		}
		String secret = prop.getProperty("accessKey.secret");
		if (!StringUtil.isEmpty(secret)) {
			m_Secret = Hex.decode(secret);
		}
	}

	UserAccess openAccess(String id) {
		byte[] mk = m_Secret;
		if (null == mk) {
			return null;
		}
		if (null == id || id.length() < 4 || !id.startsWith(ACCESS_PREFIX)) {
			return null;
		}
		int idx = id.lastIndexOf(Access.SPEARATOR);
		if (-1 == idx || idx <= 4) {
			return null;
		}
		long expire;
		try {
			expire = Long.parseLong(id.substring(idx + 1), 16);
		} catch (NumberFormatException e) {
			// 格式不正确，是非法access？
			return null;
		}
		return new UserAccess(id, genAccessKey(id, mk), expire);
	}

	/**
	 * 生成accessId对应的密钥
	 * 
	 * @param accessId
	 * @param masterKey
	 *            128/192/256位的主密钥
	 * @return
	 */
	public static byte[] genAccessKey(String accessId, byte[] secretKey) {
		try {
			java.security.MessageDigest md;
			md = java.security.MessageDigest.getInstance("SHA-256");
			md.update(accessId.getBytes("utf-8"));
			md.update(secretKey);
			return md.digest();
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			// 应该不会发生
			throw new RuntimeException("生成AccessKey失败", e);
		}
	}

	static class UserAccess implements AccessExt {

		final String accessId;
		final byte[] accessKey;
		final long expire;

		String group;

		String summary;

		String tenant;

		String openid;

		String accessKeyHex;
		String accessKeyBase64;

		UserAccess(String accessId, byte[] accessKey, long expire) {
			this.accessId = accessId;
			this.accessKey = accessKey;
			this.expire = expire;
		}

		@Override
		public String getAccessId() {
			return this.accessId;
		}

		@Override
		public byte[] getAccessKey() {
			return this.accessKey;
		}

		@Override
		public String getKind() {
			return KIND_USER;
		}

		@Override
		public boolean isValid() {
			return System.currentTimeMillis() < expire;
		}

		@Override
		public String getGroupId() {
			return group;
		}

		@Override
		public String getSummary() {
			return summary;
		}

		@Override
		public void setSummary(String summary) {
			this.summary = summary;
		}

		@Override
		public String getAccessKeyHex() {
			if (null == this.accessKeyHex) {
				this.accessKeyHex = Hex.encode(accessKey);
			}
			return this.accessKeyHex;
		}

		@Override
		public String getAccessKeyBase64() {
			if (null == this.accessKeyBase64) {
				this.accessKeyBase64 = Base64.encode(accessKey);
			}
			return this.accessKeyBase64;
		}

		long getExpire() {
			return expire;
		}

		@Override
		public void setValid(boolean valid) {
			throw new UnsupportedOperationException();
		}

		@Override
		public String getTenant() {
			return tenant;
		}

		@Override
		public String getOpenid() {
			return openid;
		}
	}

}
