package com.tarento.analytics.dto;

import java.util.List;

/**
 * Decides which facilities appear as rows in the stock summary.
 *
 * <p>The list is derived the way the dashboard derives it today: begin at the manager's own
 * facilities and follow "issued to" links outward. Sourcing the list from the campaign's facility
 * hierarchy instead is an open decision; the seam for that alternative is the resolver, which is
 * the only reader of this object.
 */
public class FacilityScope {

	/**
	 * The manager's own facilities, from which the walk starts. The service cannot know these, so
	 * the caller supplies them; without them the walk has no starting point and a different rule
	 * applies (see the resolver).
	 */
	private List<String> rootFacilityIds;

	/**
	 * Whether the starting facilities appear as rows themselves. Defaults to false, which is what
	 * the dashboard does today — a manager's own warehouse is absent from its own summary. Set true
	 * to include them; that is a deliberate change to what is on screen, not a bug fix.
	 */
	private Boolean includeRoots;

	public List<String> getRootFacilityIds() {
		return rootFacilityIds;
	}

	public void setRootFacilityIds(List<String> rootFacilityIds) {
		this.rootFacilityIds = rootFacilityIds;
	}

	public Boolean getIncludeRoots() {
		return includeRoots;
	}

	public void setIncludeRoots(Boolean includeRoots) {
		this.includeRoots = includeRoots;
	}

	public boolean hasRoots() {
		return rootFacilityIds != null && !rootFacilityIds.isEmpty();
	}

	public boolean rootsIncluded() {
		return Boolean.TRUE.equals(includeRoots);
	}
}
