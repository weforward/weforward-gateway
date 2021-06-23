package cn.weforward.gateway.ingress;

import cn.weforward.common.DictionaryExt;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.auth.GatewayAuther;
import cn.weforward.protocol.Header;
import cn.weforward.protocol.aio.http.HttpConstants;
import cn.weforward.protocol.aio.http.HttpContext;
import cn.weforward.protocol.aio.http.HttpHeaderHelper;
import cn.weforward.protocol.aio.http.HttpHeaders;
import cn.weforward.protocol.aio.http.QueryStringParser;
import cn.weforward.protocol.exception.AuthException;

/**
 * StreamTunnel中继
 * 
 * @author smily
 *
 */
public class HttpStreamRelayTunnel extends HttpStreamTunnel {

	public HttpStreamRelayTunnel(HttpContext ctx, ServerHandlerSupporter supporter) {
		super(ctx, supporter);
	}

	@Override
	public boolean isRelay() {
		return true;
	}

	@Override
	protected boolean parseRequest() {
		m_ServiceName = HttpHeaderHelper.getServiceName(m_Context.getUri());
		Header header = new Header(m_ServiceName);
		HttpHeaders httpHeaders = m_Context.getRequestHeaders();
		if (null == httpHeaders || 0 == httpHeaders.size()) {
			responseError(HttpConstants.BAD_REQUEST, "中继验证失败，缺少头信息");
			return false;
		}
		HttpHeaderHelper.fromHttpHeaders(httpHeaders, header);
		GatewayAuther auther = new GatewayAuther(m_Supporter.getAccessManage());
		try {
			auther.verify(header);
		} catch (AuthException e) {
			responseError(e);
			return false;
		}
		m_ServiceNo = header.getServiceNo();
		DictionaryExt<String, String> params = QueryStringParser.toParams(m_Context.getQueryString());
		String id = params.get("id");
		if (StringUtil.isEmpty(id)) {
			responseError(HttpConstants.BAD_REQUEST, "参数异常");
			return false;
		}
		m_ResourceId = id;
		m_ContentType = httpHeaders.getHeaderRaw(HttpConstants.CONTENT_TYPE);
		String length = httpHeaders.getHeaderRaw(HttpConstants.CONTENT_LENGTH);
		if (!StringUtil.isEmpty(length)) {
			try {
				m_Length = Long.parseLong(length);
			} catch (NumberFormatException e) {
				// ignore
			}
		}
		return true;
	}
}
