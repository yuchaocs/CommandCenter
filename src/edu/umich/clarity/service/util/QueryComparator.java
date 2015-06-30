package edu.umich.clarity.service.util;

import java.util.Comparator;

import edu.umich.clarity.thrift.QuerySpec;

public class QueryComparator<T> implements Comparator<T> {

	@Override
	public int compare(Object o1, Object o2) {
		QuerySpec query1 = (QuerySpec) o1;
		QuerySpec query2 = (QuerySpec) o2;
		double result = query1.getBudget() - query2.getBudget();
		if (result > 0) {
			return 1;
		} else if (result < 0) {
			return -1;
		}
		return 0;
	}

}
