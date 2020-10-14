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
package cn.weforward.gateway;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import cn.weforward.common.util.Bytes;
import cn.weforward.common.util.NumberUtil;
import cn.weforward.common.util.StringUtil;

/**
 * 全局配置
 * 
 * @author zhangpengji
 *
 */
public class Configure {

	private static final Configure INSTANCE = new Configure();

	private int m_ServiceRequestDefaultSize = 4 * 1024 * 1024; // 4M
	private int m_ServiceRequestMaxSize = 100 * 1024 * 1024;// 100M
	private int m_ServiceForwardCount = 2;
	private int m_WfReqPreparedSize = 4 * 1024;// 4kB
	private int m_WfRespPreparedSize = 4 * 1024; // 4kB
	// private int m_StreamChannelTimeout = 120 * 1000; // 2 minute
	private int m_ServiceMaxReadTimeout = 10 * 60; // 10 minute
	private int m_GatewayApiMaxSize = 1024 * 1024; // 1M
	private int m_StreamChannelMaxSize = 100 * 1024 * 1024; // 100M
	private int m_ServiceInvokeMaxDepth = 20;
	private int m_RpcChannelMaxConcurrent; // auto
	private int m_StreamChannelMaxConcurrent; // auto
	private int m_SingleServiceConcurrentPercent = 65;
	private int m_UserAccessCacheMaxCapacity; // auto
	private boolean m_NettyDebug = false;

	// --------- 兼容性选项
	private boolean m_CompatMode = false;
	private boolean m_ShieldKepper = false;
	private boolean m_NotVerifyAccessId = false;

	private Configure() {

	}

	public static Configure getInstance() {
		return INSTANCE;
	}

	/**
	 * 是否兼容模式
	 * 
	 * @return
	 */
	public boolean isCompatMode() {
		return m_CompatMode;
	}

	/**
	 * 是否屏蔽keeper api
	 * 
	 * @return
	 */
	public boolean isShieldKepper() {
		return m_ShieldKepper;
	}

	/**
	 * 微服务最大转发次数
	 * 
	 * @return
	 */
	public int getServiceForwardCount() {
		return m_ServiceForwardCount;
	}

	/**
	 * wf_req节点预处理大小（字节数）
	 * 
	 * @return
	 */
	public int getWfReqPreparedSize() {
		return m_WfReqPreparedSize;
	}

	/**
	 * wf_resp节点预处理大小（字节数）
	 * 
	 * @return
	 */
	public int getWfRespPreparedSize() {
		return m_WfRespPreparedSize;
	}

	// /**
	// * stream信道的读取超时值（毫秒）
	// *
	// * @return
	// */
	// public int getStreamChannelTimeout() {
	// return m_StreamChannelTimeout;
	// }

	/**
	 * 微服务的最大读取超时值（秒）
	 * 
	 * @return
	 */
	public int getServiceMaxReadTimeout() {
		return m_ServiceMaxReadTimeout;
	}

	/**
	 * 网关api请求数据的最大字节数
	 * 
	 * @return
	 */
	public int getGatewayApiMaxSize() {
		return m_GatewayApiMaxSize;
	}

	/**
	 * 微服务请求数据的默认字节数
	 * 
	 * @return
	 */
	public int getServiceRequestDefaultSize() {
		return m_ServiceRequestDefaultSize;
	}

	/**
	 * stream信道请求数据的最大字节数
	 * 
	 * @return
	 */
	public int getStreamChannelMaxSize() {
		return m_StreamChannelMaxSize;
	}

	/**
	 * 微服务请求数据的最大字节数
	 * 
	 * @return
	 */
	public int getServiceRequestMaxSize() {
		return m_ServiceRequestMaxSize;
	}

	/**
	 * 微服务调用最大深度
	 * 
	 * @return
	 */
	public int getServiceInvokeMaxDepth() {
		return m_ServiceInvokeMaxDepth;
	}

	/**
	 * 是否开启netty调试
	 * 
	 * @return
	 */
	public boolean isNettyDebug() {
		return m_NettyDebug;
	}

	public int getRpcChannelMaxConcurrent() {
		if (0 == m_RpcChannelMaxConcurrent) {
			long memory = Runtime.getRuntime().maxMemory();
			// 假设每个请求消耗512KB内存
			long limit = memory / 1024 / 512;
			if (limit < 50) {
				limit = 50;
			} else if (limit > Integer.MAX_VALUE) {
				limit = Integer.MAX_VALUE;
			}
			m_RpcChannelMaxConcurrent = (int) limit;
		}
		return m_RpcChannelMaxConcurrent;
	}

	public int getStreamChannelMaxConcurrent() {
		if (0 == m_StreamChannelMaxConcurrent) {
			m_StreamChannelMaxConcurrent = getRpcChannelMaxConcurrent() / 50;
		}
		return m_StreamChannelMaxConcurrent;
	}

	public int getSingleServiceConcurrentPercent() {
		return m_SingleServiceConcurrentPercent;
	}

	public boolean isNotVerifyAccessId() {
		return m_NotVerifyAccessId;
	}

	public int getUserAccessCacheMaxCapacity() {
		if (0 == m_UserAccessCacheMaxCapacity) {
			long memory = Runtime.getRuntime().maxMemory();
			// 分配25%的内存，假设cache请求消耗500B内存
			memory = memory / 4;
			long capacity = memory / 500;
			if (capacity < 10000) {
				capacity = 10000;
			} else if (capacity > Integer.MAX_VALUE) {
				capacity = Integer.MAX_VALUE;
			}
			m_UserAccessCacheMaxCapacity = (int) capacity;
		}
		return m_UserAccessCacheMaxCapacity;
	}

	@SuppressWarnings("serial")
	public static class ConfigureInitException extends RuntimeException {

		ConfigureInitException(String msg, Throwable cause) {
			super(msg, cause);
		}
	}

	public static class ConfigureBuilder {

		public static void init(Properties props) {
			String fieldPrefix = "m_";
			String configPrefix = "configure.";
			String setMethodPrefix = "set";
			Field[] fs = Configure.class.getDeclaredFields();
			for (Field f : fs) {
				if (!f.getName().startsWith(fieldPrefix)) {
					continue;
				}
				try {
					String fieldName = f.getName();
					String configName = configPrefix + Character.toLowerCase(fieldName.charAt(2))
							+ fieldName.substring(3);
					String setMethodName = setMethodPrefix + fieldName.substring(2);
					Method method = ConfigureBuilder.class.getMethod(setMethodName, String.class);
					String value = props.getProperty(configName);
					method.invoke(null, value);
				} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException
						| InvocationTargetException e) {
					throw new ConfigureInitException("初始化配置异常", e);
				}
			}

			// setServiceRequestDefaultSize(props.getProperty("configure.serviceRequestDefaultSize"));
			// setServiceRequestMaxSize(props.getProperty("configure.serviceRequestMaxSize"));
			// setServiceForwardCount(props.getProperty("configure.serviceForwardCount"));
			// setWfReqPreparedSize(props.getProperty("configure.wfReqPreparedSize"));
			// setWfRespPreparedSize(props.getProperty("configure.wfRespPreparedSize"));
			// setServiceMaxReadTimeout(props.getProperty("configure.serviceMaxReadTimeout"));
			// setGatewayApiMaxSize(props.getProperty("configure.gatewayApiMaxSize"));
			// setStreamChannelMaxSize(props.getProperty("configure.streamChannelMaxSize"));
			// setServiceInvokeMaxDepth(props.getProperty("configure.serviceInvokeMaxDepth"));
			// setNettyDebug(props.getProperty("configure.nettyDebug"));
			// setShieldKepper(props.getProperty("configure.shieldKepper"));
			// setCompatMode(props.getProperty("configure.compatMode"));
			// setRpcChannelMaxConcurrent(props.getProperty("configure.rpcChannelMaxConcurrent"));
			// setStreamChannelMaxConcurrent(props.getProperty("configure.streamChannelMaxConcurrent"));
			// setSingleServiceConcurrentPercent(props.getProperty("configure.singleServiceConcurrentPercent"));
		}

		public static void setServiceForwardCount(String countStr) {
			if (StringUtil.isEmpty(countStr)) {
				return;
			}
			int count = NumberUtil.toInt(countStr);
			if (count < 0 || count > 10) {
				throw new IllegalArgumentException("无效的ServiceForwardCount：" + count);
			}
			INSTANCE.m_ServiceForwardCount = count;
		}

		public static void setWfReqPreparedSize(String sizeStr) {
			if (StringUtil.isEmpty(sizeStr)) {
				return;
			}
			long size = Bytes.parseHumanReadable(sizeStr);
			if (size < 0 || size > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("无效的WfReqPreparedSize：" + sizeStr);
			}
			INSTANCE.m_WfReqPreparedSize = (int) size;
		}

		public static void setWfRespPreparedSize(String sizeStr) {
			if (StringUtil.isEmpty(sizeStr)) {
				return;
			}
			long size = Bytes.parseHumanReadable(sizeStr);
			if (size < 0 || size > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("无效的WfRespPreparedSize：" + sizeStr);
			}
			INSTANCE.m_WfRespPreparedSize = (int) size;
		}

		// public void setStreamChannelTimeout(String secondStr) {
		// if (StringUtil.isEmpty(secondStr)) {
		// return;
		// }
		// int second = NumberUtil.toInt(secondStr);
		// if (second < 0 || second > 1800) {
		// throw new IllegalArgumentException("无效的StreamChannelTimeout：" + second);
		// }
		// INSTANCE.m_StreamChannelTimeout = second * 1000;
		// }

		public static void setServiceMaxReadTimeout(String secondStr) {
			if (StringUtil.isEmpty(secondStr)) {
				return;
			}
			int second = NumberUtil.toInt(secondStr);
			if (second < 0 || second > 1800) {
				throw new IllegalArgumentException("无效的ServiceMaxReadTimeout：" + second);
			}
			INSTANCE.m_ServiceMaxReadTimeout = second;
		}

		public static void setGatewayApiMaxSize(String sizeStr) {
			if (StringUtil.isEmpty(sizeStr)) {
				return;
			}
			long size = Bytes.parseHumanReadable(sizeStr);
			if (size < 0 || size > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("无效的GatewayApiMaxSize：" + sizeStr);
			}
			INSTANCE.m_GatewayApiMaxSize = (int) size;
		}

		public static void setServiceRequestDefaultSize(String sizeStr) {
			if (StringUtil.isEmpty(sizeStr)) {
				return;
			}
			long size = Bytes.parseHumanReadable(sizeStr);
			if (size < 0 || size > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("无效的ServiceRequestSize：" + sizeStr);
			}
			INSTANCE.m_ServiceRequestDefaultSize = (int) size;
		}

		public static void setStreamChannelMaxSize(String sizeStr) {
			if (StringUtil.isEmpty(sizeStr)) {
				return;
			}
			long size = Bytes.parseHumanReadable(sizeStr);
			if (size <= 0 || size > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("无效的StreamChannelMaxSize：" + sizeStr);
			}
			INSTANCE.m_StreamChannelMaxSize = (int) size;
		}

		public static void setServiceRequestMaxSize(String sizeStr) {
			if (StringUtil.isEmpty(sizeStr)) {
				return;
			}
			long size = Bytes.parseHumanReadable(sizeStr);
			if (size < 0 || size > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("无效的ServiceRequestMaxSize：" + sizeStr);
			}
			INSTANCE.m_ServiceRequestMaxSize = (int) size;
		}

		public static void setShieldKepper(String boolStr) {
			if (StringUtil.isEmpty(boolStr)) {
				return;
			}
			INSTANCE.m_ShieldKepper = Boolean.valueOf(boolStr);
		}

		public static void setCompatMode(String boolStr) {
			if (StringUtil.isEmpty(boolStr)) {
				return;
			}
			INSTANCE.m_CompatMode = Boolean.valueOf(boolStr);
		}

		public static void setServiceInvokeMaxDepth(String depthStr) {
			if (StringUtil.isEmpty(depthStr)) {
				return;
			}
			int depth = NumberUtil.toInt(depthStr);
			if (depth < 1 || depth > 100) {
				throw new IllegalArgumentException("无效的ServiceInvokeMaxDepth：" + depth);
			}
			INSTANCE.m_ServiceInvokeMaxDepth = depth;
		}

		public static void setNettyDebug(String boolStr) {
			if (StringUtil.isEmpty(boolStr)) {
				return;
			}
			INSTANCE.m_NettyDebug = Boolean.valueOf(boolStr);
		}

		public static void setRpcChannelMaxConcurrent(String countStr) {
			if (StringUtil.isEmpty(countStr)) {
				return;
			}
			int count;
			try {
				count = NumberUtil.toInt(countStr);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("无效的RpcChannelMaxConcurrent：" + countStr);
			}
			if (count <= 0) {
				throw new IllegalArgumentException("无效的RpcChannelMaxConcurrent：" + countStr);
			}
			INSTANCE.m_RpcChannelMaxConcurrent = count;
		}

		public static void setStreamChannelMaxConcurrent(String countStr) {
			if (StringUtil.isEmpty(countStr)) {
				return;
			}
			int count;
			try {
				count = NumberUtil.toInt(countStr);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("无效的StreamChannelMaxConcurrent：" + countStr);
			}
			if (count <= 0) {
				throw new IllegalArgumentException("无效的StreamChannelMaxConcurrent：" + countStr);
			}
			INSTANCE.m_StreamChannelMaxConcurrent = count;
		}

		public static void setSingleServiceConcurrentPercent(String percentStr) {
			if (StringUtil.isEmpty(percentStr)) {
				return;
			}
			int percent;
			try {
				percent = NumberUtil.toInt(percentStr);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("无效的SingleServiceConcurrentPercent：" + percentStr);
			}
			if (percent <= 0 || percent > 100) {
				throw new IllegalArgumentException("无效的SingleServiceConcurrentPercent：" + percentStr);
			}
			INSTANCE.m_SingleServiceConcurrentPercent = percent;
		}

		public static void setNotVerifyAccessId(String boolStr) {
			if (StringUtil.isEmpty(boolStr)) {
				return;
			}
			INSTANCE.m_NotVerifyAccessId = Boolean.valueOf(boolStr);
		}

		public static void setUserAccessCacheMaxCapacity(String countStr) {
			if (StringUtil.isEmpty(countStr)) {
				return;
			}
			int count;
			try {
				count = NumberUtil.toInt(countStr);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("无效的UserAccessCacheMaxCapacity：" + countStr);
			}
			if (count <= 0) {
				throw new IllegalArgumentException("无效的UserAccessCacheMaxCapacity：" + countStr);
			}
			INSTANCE.m_UserAccessCacheMaxCapacity = count;
		}
	}
}
