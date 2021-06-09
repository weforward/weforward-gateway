package cn.weforward.gateway;

import java.io.UnsupportedEncodingException;

import org.junit.Test;

import cn.weforward.gateway.ingress.ServerHandlerSupporter;
import cn.weforward.gateway.ingress.StreamResourceToken;

public class ResourceTokenTest {

	@Test
	public  void parse() throws Exception, UnsupportedEncodingException {
		ServerHandlerSupporter supporter = new ServerHandlerSupporter();
		supporter.setResourceSecret("");
		StreamResourceToken token = supporter.parseResourceToken("token=NtOC4AewU0Ln/joGKyFITYOFE51gQ6ed2PtmNaQvRTB0OCtCat/rTUsn/QVKjU/NO55jKmMX7a6hk/44tbR9xbtRojADlxgTyeEqZYPs");
		System.out.println(token.getResourceId());
	}
	
	@Test
	public  void gen() throws Exception, UnsupportedEncodingException {
		ServerHandlerSupporter supporter = new ServerHandlerSupporter();
		supporter.setResourcePreUrl("https://g1.honinsys.cn/");
		supporter.setResourceSecret("");
		StreamResourceToken token = new StreamResourceToken();
		token.setResourceId("SimpleRunning$097d125c-016d.MemoryMap$20210104103505.tar");
		token.setServiceName("devops");
		token.setExpire(System.currentTimeMillis()+10*60*1000);
		System.out.println(supporter.genResourceUrl(token));
	}
}
