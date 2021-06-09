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

import cn.weforward.data.persister.Persistent;
import cn.weforward.data.persister.Persister;
import cn.weforward.data.persister.PersisterFactory;
import cn.weforward.gateway.ops.access.system.MasterKeyVo;
import cn.weforward.gateway.ops.access.system.MasterKeyVoFactory;
import cn.weforward.gateway.plugin.mongodb.di.MasterKeyDi;

/**
 * <code>MasterKeyVoFactory</code>实现
 * 
 * @author zhangpengji
 *
 */
public class MasterKeyVoFactoryImpl extends AbstractVoFactoryImpl<MasterKey, MasterKeyVo> implements MasterKeyVoFactory, MasterKeyDi {

	MasterKeyVoFactoryImpl(PersisterFactory factory) {
		super(factory);
	}

	@SuppressWarnings("unchecked")
	@Override
	public <E extends Persistent> Persister<E> getPersister(Class<E> clazz) {
		if (clazz == MasterKey.class) {
			return (Persister<E>) m_Persister;
		}
		return null;
	}

	@Override
	protected Persister<MasterKey> createPersister(PersisterFactory factory) {
		return factory.createPersister(MasterKey.class, this);
	}

	@Override
	protected String getVoId(MasterKeyVo vo) {
		return vo.id;
	}

	@Override
	protected MasterKey createVoPersistent(MasterKeyVo vo) {
		return new MasterKey(this, vo);
	}
}
