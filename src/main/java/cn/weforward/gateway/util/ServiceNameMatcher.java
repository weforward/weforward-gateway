package cn.weforward.gateway.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.weforward.common.util.ListUtil;
import cn.weforward.common.util.StringUtil;

/**
 * 微服务名的通配符匹配器
 * 
 * @author zhangpengji
 *
 */
public class ServiceNameMatcher {

	static final char WILDCARD = '*';

	static final String WILDCARD_STR = String.valueOf('*');

	static final ServiceNameMatcher EMPTY = new ServiceNameMatcher(null);

	final String m_Keyword;
	List<Integer> m_WildcardIdxes;

	public static ServiceNameMatcher getInstance(String keyword) {
		if (StringUtil.isEmpty(keyword) || WILDCARD_STR.equals(keyword)) {
			return EMPTY;
		}
		return new ServiceNameMatcher(keyword);
	}

	private ServiceNameMatcher(String keyword) {
		m_Keyword = keyword;
	}

	public static boolean hasWildcard(String keyword) {
		if (StringUtil.isEmpty(keyword)) {
			return false;
		}
		return -1 != keyword.indexOf(WILDCARD);
	}

	protected void init() {
		if (null != m_WildcardIdxes) {
			return;
		}
		if (StringUtil.isEmpty(m_Keyword)) {
			m_WildcardIdxes = Collections.emptyList();
			return;
		}
		int idx = m_Keyword.indexOf(WILDCARD);
		if (-1 == idx) {
			m_WildcardIdxes = Collections.emptyList();
			return;
		}
		List<Integer> wildcards = new ArrayList<>();
		wildcards.add(idx);
		while (idx + 1 < m_Keyword.length() && -1 != (idx = m_Keyword.indexOf(WILDCARD, idx + 1))) {
			wildcards.add(idx);
		}
		m_WildcardIdxes = wildcards;
	}

	public boolean match(String name) {
		init();
		if (StringUtil.isEmpty(m_Keyword)) {
			return true;
		}
		if (ListUtil.isEmpty(m_WildcardIdxes)) {
			return name.equals(m_Keyword);
		}
		if (1 == m_WildcardIdxes.size()) {
			if (0 == m_WildcardIdxes.get(0)) {
				return name.endsWith(m_Keyword.substring(1));
			}
			if (m_Keyword.length() - 1 == m_WildcardIdxes.get(0)) {
				return name.startsWith(m_Keyword.substring(0, m_Keyword.length() - 1));
			}
		}
		if (2 == m_WildcardIdxes.size()) {
			if (0 == m_WildcardIdxes.get(0) && m_Keyword.length() - 1 == m_WildcardIdxes.get(1)) {
				return name.contains(m_Keyword.substring(1, m_Keyword.length() - 1));
			}
		}
		int wildcardPos = 0;
		int bi = 0;
		int ei = m_WildcardIdxes.get(wildcardPos);
		int matchPos = 0;
		while (bi <= ei && bi < m_Keyword.length()) {
			if (bi < ei) {
				if (0 == bi) {
					if (!name.startsWith(m_Keyword.substring(0, ei))) {
						return false;
					}
				} else if (m_Keyword.length() == ei) {
					if (!name.endsWith(m_Keyword.substring(bi))) {
						return false;
					}
				} else {
					int i = name.indexOf(m_Keyword.substring(bi, ei), matchPos);
					if (-1 == i) {
						return false;
					}
					matchPos = i + ei - bi;
				}
			}
			bi = ei + 1;
			if (++wildcardPos < m_WildcardIdxes.size()) {
				ei = m_WildcardIdxes.get(wildcardPos);
			} else {
				ei = m_Keyword.length();
			}
		}
		return true;
	}
}
