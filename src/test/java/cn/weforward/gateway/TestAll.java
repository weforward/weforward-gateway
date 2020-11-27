package cn.weforward.gateway;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import cn.weforward.gateway.util.ServiceNameMatcher;

public class TestAll {

	@Test
	public void serviceNameMatch() {
		ServiceNameMatcher m1 = ServiceNameMatcher.getInstance("*.order");
		assertTrue(m1.match("my.order"));
		assertFalse(m1.match("my.order.your"));
		assertFalse(m1.match("order.your"));
		
		ServiceNameMatcher m2 = ServiceNameMatcher.getInstance("*_order_*");
		assertTrue(m2.match("my_order_"));
		assertTrue(m2.match("_order_your"));
		assertTrue(m2.match("my_order_your"));
		assertFalse(m2.match("order"));
		
		ServiceNameMatcher m3 = ServiceNameMatcher.getInstance("one*two");
		assertTrue(m3.match("onetwo"));
		assertTrue(m3.match("one_two"));
		assertFalse(m3.match("onetwothree"));
		
		ServiceNameMatcher m4 = ServiceNameMatcher.getInstance("one*two*");
		assertTrue(m4.match("onetwo"));
		assertTrue(m4.match("one_two"));
		assertTrue(m4.match("onetwothree"));
		assertFalse(m4.match("zeroonetwothree"));
		
		ServiceNameMatcher m5 = ServiceNameMatcher.getInstance("*one*two*");
		assertTrue(m5.match("zeroonetwothree"));
		assertTrue(m5.match("zeroone_twothree"));
		assertFalse(m5.match("zerotwothree"));
	}
}
