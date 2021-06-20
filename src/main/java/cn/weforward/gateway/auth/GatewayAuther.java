package cn.weforward.gateway.auth;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

import cn.weforward.common.crypto.Base64;
import cn.weforward.common.crypto.Hex;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.AccessLoaderExt;
import cn.weforward.protocol.Access;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.exception.AuthException;

/**
 * 网关认证器
 * 
 * @author smily
 *
 */
public class GatewayAuther {
	protected static final Random RANDOM = new Random();

	AccessLoaderExt m_AccessLoader;

	public GatewayAuther(AccessLoaderExt loader) {
		m_AccessLoader = loader;
	}

	/**
	 * 生成认证信息，并置入Header
	 * 
	 * @param type
	 * @param requestHeader
	 * @throws AuthException
	 */
	public void generate(String type, Header requestHeader) throws AuthException {
		// TODO Header.GATEWAY_AUTH_TYPE_MESH_FORWARD 网关转发不应该用内置密钥
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			Access internalAccess = m_AccessLoader.getInternalAccess();
			String service = requestHeader.getService();
			String noise = requestHeader.getNoise();
			if (StringUtil.isEmpty(noise)) {
				// XXX 不能保证不重复
				long n = System.currentTimeMillis() << 20;
				int i = RANDOM.nextInt();
				n |= (i & 0xfffff);
				noise = Hex.toHex64(n);
				requestHeader.setNoise(noise);
			}
			md.update((service + noise).getBytes("utf-8"));
			md.update(internalAccess.getAccessKey());
			String auth = type + " " + internalAccess.getAccessId() + ":" + Base64.encode(md.digest());
			requestHeader.setGatewayAuth(auth);
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			throw new AuthException(e);
		}
	}

	public void verify(Header requestHeader) throws AuthException {
		String auth = requestHeader.getGatewayAuth();
		if (StringUtil.isEmpty(auth)) {
			throw new AuthException("缺失认证信息");
		}
		int idx1 = auth.indexOf(' ');
		if (-1 == idx1 || idx1 >= auth.length() - 3) {
			throw new AuthException("认证信息格式有误：" + auth);
		}
		int idx2 = auth.indexOf(':', idx1 + 2);
		if (-1 == idx2 || idx2 >= auth.length() - 1) {
			throw new AuthException("认证信息格式有误：" + auth);
		}
		String accessId = auth.substring(idx1 + 1, idx2);
		String sign = auth.substring(idx2 + 1);
		Access access = m_AccessLoader.getValidAccess(accessId);
		if (null == access) {
			throw new AuthException("无效的access id：" + accessId);
		}
		String verifySign;
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			Access internalAccess = m_AccessLoader.getInternalAccess();
			String service = requestHeader.getService();
			String noise = requestHeader.getNoise();
			md.update((service + noise).getBytes("utf-8"));
			md.update(internalAccess.getAccessKey());
			verifySign = Base64.encode(md.digest());
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			throw new AuthException(e);
		}
		if (!StringUtil.eq(verifySign, sign)) {
			throw new AuthException(AuthException.CODE_AUTH_FAIL, "代理转发验证失败：" + verifySign + " != " + sign);
		}
	}
}
