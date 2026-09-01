package com.tarento.analytics.constant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ElasticSearchConstants {
	 public static final String LTE = "<=";
	 public static final String LT = "<";
	 public static final String GTE = ">=";
	 public static final String GT = ">";
	 public static final String ASC_ORDER = "ASC";
	 public static final String STARTS_WITH = "startsWith";
	 public static final String ENDS_WITH = "endsWith";
	 public static final List<String> upsertResults =
		      new ArrayList<>(Arrays.asList("CREATED", "UPDATED", "NOOP"));
	 public static final String SOFT_MODE = "soft";
     public static final String RAW_APPEND = ".raw";
     public static final String DAY_OF_WEEK = "dayOfWeek";
     public static final String DAY = "day";
     public static final String HOUR = "hour";

     /**
      * Indices whose documents carry a soft-delete marker that current-state charts must exclude.
      * A retired campaign-staff assignment is tombstoned in place (same document id) rather than
      * removed, so without this exclusion a worker whose area was corrected is counted once for the
      * assignment they left and once for the new one.
      */
     public static final String PROJECT_STAFF_INDEX_FRAGMENT = "project-staff-index";
     public static final String IS_DELETED_FIELD = "Data.isDeleted";
}
