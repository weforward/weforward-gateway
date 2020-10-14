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
package cn.weforward.gateway.plugin.mongodb;

import javax.annotation.Resource;

import cn.weforward.data.UniteId;
import cn.weforward.data.persister.support.AbstractPersistent;
import cn.weforward.gateway.ops.access.system.MasterKeyVo;
import cn.weforward.gateway.plugin.mongodb.di.MasterKeyDi;

/**
 * MasterKeyVo存储类
 * 
 * @author zhangpengji
 *
 */
public class MasterKey extends AbstractPersistent<MasterKeyDi> {// implements Reloadable<MasterKey>

	@Resource
	MasterKeyVo m_Vo;

	protected MasterKey(MasterKeyDi di) {
		super(di);
	}

	public MasterKey(MasterKeyDi di, MasterKeyVo vo) {
		super(di);
		m_Id = UniteId.valueOf(vo.id, MasterKey.class);
		m_Vo = vo;
		markPersistenceUpdate();
	}

	void updateVo(MasterKeyVo vo) {
		m_Vo = vo;
		markPersistenceUpdate();
	}

	MasterKeyVo getVo() {
		return m_Vo;
	}

	// @Override
	// public boolean onReloadAccepted(Persister<MasterKey> persister, MasterKey
	// other) {
	// if (null == other) {
	// return false;
	// }
	// synchronized (this) {
	// m_Vo = other.m_Vo;
	// }
	// return true;
	// }

	boolean reload(MasterKey other) {
		if (null == other) {
			return false;
		}
		synchronized (this) {
			m_Vo = other.m_Vo;
		}
		return true;
	}
}
