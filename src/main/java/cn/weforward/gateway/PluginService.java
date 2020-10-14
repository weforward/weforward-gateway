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

import cn.weforward.common.Nameable;
import cn.weforward.common.restful.RestfulService;
import cn.weforward.protocol.ServiceName;

/**
 * 为插件提供接入网关远端入口的支持。
 * <p>
 * <code>getName()</code>返回的名称仅允许包含小写字母、数字、下划线。
 * <p>
 * 在网关Http入口，<code>getName()</code>返回的名称与{@link ServiceName#PLUGIN}组成完整的uri，例如：__pl_plugin_name；<br/>
 * 当uri匹配时，将由PluginService接管此请求
 * 
 * @author zhangpengji
 *
 */
public interface PluginService extends RestfulService, Nameable, Pluginable {

}
