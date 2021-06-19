package cn.weforward.gateway.api;

import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.Configure;
import cn.weforward.gateway.Pluginable;
import cn.weforward.protocol.Service;
import cn.weforward.protocol.support.SpecificationChecker;

/**
 * 微服务检查器
 * 
 * @author smily
 *
 */
public interface ServiceChecker extends Pluginable {

	/**
	 * 检查微服务信息，并返回错误
	 */
	String check(Service service, String owner);

	ServiceChecker getPreChecker();

	void setPreCheck(ServiceChecker checker);

	ServiceChecker DEFALUT = new ServiceChecker() {

		@Override
		public String check(Service service, String owner) {
			String nameErr = SpecificationChecker.checkServiceName(service.getName());
			if (!StringUtil.isEmpty(nameErr)) {
				return nameErr;
			}
			if ((StringUtil.isEmpty(service.getDomain()) || 0 == service.getPort())
					&& ListUtil.isEmpty(service.getUrls())) {
				return "微服务信息不完整";
			}
			String noErr = SpecificationChecker.checkServiceNo(service.getNo());
			if (!StringUtil.isEmpty(noErr)) {
				return noErr;
			}
			int maxSize = service.getRequestMaxSize();
			if (maxSize < 0 || maxSize > Configure.getInstance().getServiceRequestMaxSize()) {
				return "微服务请求数据的最大字节数不合法：" + maxSize;
			}
			return null;
		}

		@Override
		public ServiceChecker getPreChecker() {
			return null;
		}

		@Override
		public void setPreCheck(ServiceChecker checker) {
			throw new UnsupportedOperationException();
		}
	};
}
