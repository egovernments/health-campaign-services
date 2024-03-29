package com.tarento.analytics.helper;

import java.util.List;

import com.tarento.analytics.dto.AggregateRequestDto;
import com.tarento.analytics.dto.Data;

/**
 * Compute Helper Inferface which receives the Request and the List of Data 
 * Implementations will derive as to what has to be the computation based on the Business Logic Specifications
 * @author darshan
 *
 */
public interface ComputeHelper {
	public List<Data> compute(AggregateRequestDto request, List<Data> data);
	public List<Data> compute(AggregateRequestDto request, List<Data> data, List<Data> capValues);
	public Double compute(AggregateRequestDto request, double value);
	public Double compute(AggregateRequestDto request, double value, double capTotal);

}
