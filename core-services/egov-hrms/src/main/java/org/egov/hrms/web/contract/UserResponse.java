/*
 * eGov suite of products aim to improve the internal efficiency,transparency,
 * accountability and the service delivery of the government  organizations.
 *
 *  Copyright (C) 2016  eGovernments Foundation
 *
 *  The updated version of eGov suite of products as by eGovernments Foundation
 *  is available at http://www.egovernments.org
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see http://www.gnu.org/licenses/ or
 *  http://www.gnu.org/licenses/gpl.html .
 *
 *  In addition to the terms of the GPL license to be adhered to in using this
 *  program, the following additional terms are to be complied with:
 *
 *      1) All versions of this program, verbatim or modified must carry this
 *         Legal Notice.
 *
 *      2) Any misrepresentation of the origin of the material is prohibited. It
 *         is required that all modified versions of this material be marked in
 *         reasonable ways as different from the original version.
 *
 *      3) This license does not grant any rights to any user of the program
 *         with regards to rights under trademark law for use of the trade names
 *         or trademarks of eGovernments Foundation.
 *
 *  In case of any queries, you can reach eGovernments Foundation at contact@egovernments.org.
 */

package org.egov.hrms.web.contract;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import org.egov.common.contract.response.ResponseInfo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Setter
@ToString
@Builder
public class UserResponse {

	private ResponseInfo responseInfo;

	private List<User> user = new ArrayList<User>();

	@JsonIgnore
	@Builder.Default
	private Long totalCount = 0L;

	/**
	 * Per-record failure info surfaced by the underlying user backend
	 * (individual or egov-user). Each entry carries at least
	 * <code>username</code>, <code>errorCode</code>, and
	 * <code>errorMessage</code>. Callers correlate by username. Marked
	 * {@code @JsonIgnore} because callers of the search/create HTTP APIs
	 * don't need to see this on the wire — it's an in-memory channel to
	 * propagate failure detail through {@link org.egov.hrms.service.UserService}
	 * implementations up to {@link org.egov.hrms.service.EmployeeService}.
	 */
	@JsonIgnore
	@Builder.Default
	private List<Map<String, Object>> errors = new ArrayList<>();

	/**
	 * Per-username source attribution when this response comes from
	 * {@link org.egov.hrms.service.UserService#searchByUsernames}: maps
	 * matching username -> the backend that detected it
	 * ({@code "individual"} for a direct hit in the individual table,
	 * {@code "egov-user"} for a hit via the eg_user cross-check when no
	 * individual row existed). Empty when nothing matched.
	 */
	@JsonIgnore
	@Builder.Default
	private Map<String, String> sourceByUsername = new java.util.HashMap<>();

}