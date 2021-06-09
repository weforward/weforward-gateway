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

import java.util.ArrayList;
import java.util.List;

/**
 * 变化的对象
 * 
 * @author zhangpengji
 *
 */
class ChangedObjectQueue {

	ArrayList<Object> m_Updateds;
	ArrayList<Object> m_Deleteds;

	ChangedObjectQueue() {
		m_Updateds = new ArrayList<>();
		m_Deleteds = new ArrayList<>();
	}

	void putUpdated(Object vo) {
		synchronized (this) {
			m_Updateds.add(vo);
			notify();
		}
	}

	void putDeleted(Object vo) {
		synchronized (this) {
			m_Deleteds.add(vo);
			notify();
		}
	}

	Changeds dump() {
		synchronized (this) {
			Changeds changeds = new Changeds();
			if (!m_Updateds.isEmpty()) {
				@SuppressWarnings("unchecked")
				List<Object> vos = (List<Object>) m_Updateds.clone();
				changeds.updates = vos;
				m_Updateds.clear();
			}
			if (!m_Deleteds.isEmpty()) {
				@SuppressWarnings("unchecked")
				List<Object> vos = (List<Object>) m_Deleteds.clone();
				changeds.deletes = vos;
				m_Deleteds.clear();
			}

			return changeds;
		}
	}

	boolean isEmpty() {
		return m_Updateds.isEmpty() && m_Deleteds.isEmpty();
	}

	Changeds poll(int timeout) throws InterruptedException {
		synchronized (this) {
			if (isEmpty()) {
				wait(timeout);
			}
			Changeds changeds = dump();
			return changeds;
		}
	}

	static class Changeds {
		List<Object> updates;
		List<Object> deletes;

		@Override
		public String toString() {
			return "{up:" + (null == updates ? 0 : updates.size()) + ",del:" + (null == deletes ? 0 : deletes.size())
					+ "}";
		}
	}
}
