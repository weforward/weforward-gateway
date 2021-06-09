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
package cn.weforward.gateway.distribute;

import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.mesh.MeshNodeVo;
import cn.weforward.protocol.gateway.vo.ServiceExtVo;
import cn.weforward.protocol.ops.ServiceExt;

public class ServiceInstanceVo extends ServiceExtVo {

	public MeshNodeVo meshNode;

	public ServiceInstanceVo() {

	}

	public ServiceInstanceVo(ServiceInstance service) {
		super((ServiceExt) service);
		this.meshNode = MeshNodeVo.valueOf(service.getMeshNode());
	}

	public static ServiceInstanceVo valueOf(ServiceInstance service) {
		if (null == service) {
			return null;
		}
		return new ServiceInstanceVo(service);
	}

	public MeshNodeVo getMeshNode() {
		return meshNode;
	}

	public void setMeshNode(MeshNodeVo meshNode) {
		this.meshNode = meshNode;
	}
}
