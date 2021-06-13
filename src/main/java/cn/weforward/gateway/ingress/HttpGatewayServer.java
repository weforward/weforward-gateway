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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.util.AntPathPattern;
import cn.weforward.common.util.IpRanges;
import cn.weforward.common.util.StringUtil;
import cn.weforward.common.util.VersionUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.protocol.aio.Headers;
import cn.weforward.protocol.aio.ServerContext;
import cn.weforward.protocol.aio.ServerHandler;
import cn.weforward.protocol.aio.ServerHandlerFactory;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpContext;
import cn.weforward.protocol.aio.http.HttpHeaders;
import cn.weforward.protocol.aio.netty.NettyHttpServer;

/**
 * 基于Http协议的网关入口
 * 
 * @author zhangpengji
 *
 */
public class HttpGatewayServer implements ServerHandlerFactory {
	static final Logger _Logger = LoggerFactory.getLogger(HttpGatewayServer.class);

	static final int METHOD_GET = 0x01;
	static final int METHOD_POST = 0x02;
	static final int METHOD_OPTIONS = 0x04;

	NettyHttpServer m_Server;

	ServerHandlerSupporter m_Supporter;
	AntPathPattern m_ApiUriPattern;
	AntPathPattern m_RpcUriPattern;
	AntPathPattern m_DocUriPattern;
	AntPathPattern m_StreamUriPattern;

	/** 只允许指定访问的IP段 */
	protected IpRanges m_AllowIps;
	/** 可信的前端代理IP段 */
	protected IpRanges m_ProxyIps;
	/** 输出网关版本号信息 */
	String m_VersionInfo;

	public HttpGatewayServer(int port) {
		m_Server = new NettyHttpServer(port);
		m_Server.setName("gw");
		m_Server.setHandlerFactory(this);
		Configure cfg = Configure.getInstance();
		if (cfg.isNettyDebug()) {
			m_Server.setDebugEnabled(true);
		}

		int size = Math.max(cfg.getServiceRequestMaxSize(), cfg.getStreamChannelMaxSize());
		if (size > m_Server.getMaxHttpSize()) {
			m_Server.setMaxHttpSize(size);
		}
	}

	public void setSupporter(ServerHandlerSupporter supporter) {
		m_Supporter = supporter;
	}

	public void setIdle(int secs) {
		m_Server.setIdle(secs);
	}

	public void setRpcUri(String uri) {
		m_RpcUriPattern = AntPathPattern.valueOf(uri);
		m_Server.start();
	}

	public void setApiUri(String uri) {
		m_ApiUriPattern = AntPathPattern.valueOf(uri);
	}

	public void setDocUri(String uri) {
		m_DocUriPattern = AntPathPattern.valueOf(uri);
	}

	public void setStreamUri(String uri) {
		m_StreamUriPattern = AntPathPattern.valueOf(uri);
	}
	
	public void setWebSocketUri(String uri) {
		m_Server.setWebSocket(uri);
	}

	public void setVersion(boolean enabled) {
		if (enabled) {
			m_VersionInfo = VersionUtil
					.getImplementationVersionByJar(cn.weforward.gateway.ingress.HttpGatewayServer.class);
			_Logger.info("Gateway version: " + m_VersionInfo);
		} else {
			m_VersionInfo = null;
		}
	}

	/**
	 * 设置允许匿名访问的IP（段）列表
	 * 
	 * @param ipList
	 *            IP（段）列表，每IP（段）项以分号分隔，如：“127.0.0.1;192.168.0.0-192.168.0.100”
	 */
	public void setAllowIps(String ipList) {
		if (StringUtil.isEmpty(ipList)) {
			m_AllowIps = null;
			return;
		}
		IpRanges iprs = new IpRanges(ipList);
		setAllowIpRanges(iprs);
	}

	public void setAllowIpRanges(IpRanges iprs) {
		m_AllowIps = iprs;
	}

	/**
	 * 设置可信的前端代理IP段
	 * 
	 * @param ipList
	 *            IP（段）列表，每IP（段）项以分号分隔，如：“127.0.0.1;192.168.0.0-192.168.0.100”
	 */
	public void setProxyIps(String ipList) {
		if (StringUtil.isEmpty(ipList)) {
			m_ProxyIps = null;
			return;
		}
		IpRanges iprs = new IpRanges(ipList);
		setProxyIpsRanges(iprs);
	}

	public void setProxyIpsRanges(IpRanges iprs) {
		m_ProxyIps = iprs;
	}

	static CheckFromResult CHECKFROMRESULT_NULL = new CheckFromResult(null, null);
	static CheckFromResult CHECKFROMRESULT_FALSE = new CheckFromResult(null, false);

	static class CheckFromResult {
		String realIp;
		Boolean trust;

		CheckFromResult(String realIp, Boolean trust) {
			this.realIp = realIp;
			this.trust = trust;
		}
	}

	/**
	 * 检查访问源
	 * 
	 * @return TRUE为可信、FALSE为拒绝访问、null为可访问
	 */
	protected CheckFromResult checkFrom(ServerContext ctx) throws IOException {
		String ip = ctx.getRemoteAddr();
		if (null == ip || ip.length() < 7) {
			// IP这么怪？
			return CHECKFROMRESULT_NULL;
		}
		int idx = ip.lastIndexOf(':');
		if (idx > 0) {
			// 去掉最后面的“:端口”
			ip = ip.substring(0, idx);
		}
		Headers headers = null;
		if (null != m_ProxyIps && null != m_ProxyIps.find(ip)) {
			// 经过了代理服务器的地址
			headers = ctx.getRequestHeaders();
			if (null != headers) {
				// 由X-Forwarded-For取得实际IP
				String fip = headers.get("X-Forwarded-For");
				if (null != fip && fip.length() > 0) {
					// 取第一个IP，如：client, proxy1, proxy2, ...
					idx = fip.indexOf(',') - 1;
					// 且去除空格
					while (idx > 7 && fip.charAt(idx) == ' ') {
						--idx;
					}
					ip = (idx > 0) ? fip.substring(0, idx + 1) : fip;
				} else if (_Logger.isTraceEnabled()) {
					_Logger.trace("'X-Forwarded-For' is empry, from:" + ip);
				}
			}
		}

		if (null != m_AllowIps && null == m_AllowIps.find(ip)) {
			// 若有且不在允许IP列表，要拒绝掉
			return CHECKFROMRESULT_FALSE;
		}

		// 看看通道是否标识为安全的（如通过HTTPS代理后）
		if (null != headers && "on".equals(headers.get(HttpConstants.WF_SECURE))) {
			return new CheckFromResult(ip, true);
		}
		return new CheckFromResult(ip, null);
	}

	protected boolean checkMeshForward(HttpContext ctx) {
		HttpHeaders headers = ctx.getRequestHeaders();
		return null != headers && !StringUtil.isEmpty(headers.get(HttpConstants.WF_MESH_AUTH));
	}

	private int getMethod(String method) {
		method = method.toUpperCase();
		if (HttpConstants.METHOD_GET.equalsIgnoreCase(method)) {
			return METHOD_GET;
		}
		if (HttpConstants.METHOD_POST.equalsIgnoreCase(method)) {
			return METHOD_POST;
		}
		if (HttpConstants.METHOD_OPTIONS.equalsIgnoreCase(method)) {
			return METHOD_OPTIONS;
		}
		return 0;
	}

	@Override
	public ServerHandler handle(ServerContext ctx) throws IOException {
		int method = getMethod(ctx.getVerb());
		if (0 == (method & (METHOD_GET | METHOD_POST | METHOD_OPTIONS))) {
			// 只支持GET或POST
			ctx.response(HttpConstants.METHOD_NOT_ALLOWED, HttpContext.RESPONSE_AND_CLOSE);
			ctx.disconnect();
			return null;
		}

		CheckFromResult cfr = checkFrom(ctx);
		if (Boolean.FALSE.equals(cfr.trust)) {
			// 拒绝此IP访问
			ctx.response(HttpConstants.FORBIDDEN, HttpContext.RESPONSE_AND_CLOSE);
			ctx.disconnect();
			return null;
		}

		if (METHOD_OPTIONS == (method)) {
			return new HttpAccessControl(ctx);
		}

		if (null != m_VersionInfo) {
			// HTTP头输出网关版本号
			ctx.setResponseHeader(HttpConstants.WF_GW_VERSION, m_VersionInfo);
		}

		String uri = ctx.getUri();
		if (METHOD_POST == method && m_ApiUriPattern.match(uri)) {
			return openGatewayApi(ctx);
		}
		if (METHOD_GET == method && m_DocUriPattern.match(uri)) {
			return openServiceDoc(ctx);
		}

		if (!(ctx instanceof HttpContext)) {
			ctx.response(HttpConstants.METHOD_NOT_ALLOWED, HttpContext.RESPONSE_AND_CLOSE);
			ctx.disconnect();
			return null;
		}
		if (m_StreamUriPattern.match(uri)) {
			return openStreamTunnel((HttpContext) ctx);
		}
		if (METHOD_POST == method && m_RpcUriPattern.match(uri)) {
			return openTunnel((HttpContext) ctx, cfr);
		}

		ctx.response(HttpConstants.NOT_FOUND, HttpContext.RESPONSE_AND_CLOSE);
		ctx.disconnect();
		return null;
	}

	private ServerHandler openGatewayApi(ServerContext ctx) throws IOException {
		return new GatewayApiHandler(ctx, m_Supporter);
	}

	private ServerHandler openTunnel(HttpContext ctx, CheckFromResult cfr) throws IOException {
		if (checkMeshForward(ctx)) {
			return new HttpMeshTunnel(ctx, m_Supporter, cfr.realIp, Boolean.TRUE.equals(cfr.trust));
		}
		return new HttpTunnel(ctx, m_Supporter, cfr.realIp, Boolean.TRUE.equals(cfr.trust));
	}

	private ServerHandler openServiceDoc(ServerContext ctx) throws IOException {
		return new ServiceDocHandler(ctx, m_Supporter);
	}

	ServerHandler openStreamTunnel(HttpContext ctx) throws IOException {
		return new HttpStreamTunnel(ctx, m_Supporter);
	}
}
