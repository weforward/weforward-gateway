package cn.weforward.gateway;

import org.junit.Test;

import cn.weforward.common.util.IpRanges;

public class IpRangesTest {

	@Test
	public  void main() {
		IpRanges ipr = new IpRanges("172.16.1.1-172.16.255.255;10.0.0.1-10.255.255.255;100.64.0.0-100.127.255.255");
		System.out.println(ipr.find("100.121.82.213"));
	}
}
