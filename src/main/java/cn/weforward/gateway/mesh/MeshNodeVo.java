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
package cn.weforward.gateway.mesh;

import java.util.List;

import cn.weforward.common.util.FreezedList;
import cn.weforward.gateway.MeshNode;
import cn.weforward.gateway.distribute.GatewayNodeWrap;

public class MeshNodeVo {

	public String id;
	public List<String> urls;
	public int port;

	public MeshNodeVo() {

	}

	public MeshNodeVo(MeshNode node) {
		this.id = node.getId();
		this.urls = node.getUrls();
	}

	public static MeshNodeVo valueOf(MeshNode node) {
		if (null == node) {
			return null;
		}
		if (node instanceof GatewayNodeWrap) {
			return ((MeshNodeWrap) node).getVo();
		}
		return new MeshNodeVo(node);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public List<String> getUrls() {
		return urls;
	}

	public void setUrls(List<String> urls) {
		this.urls = FreezedList.freezed(urls);
	}
}
