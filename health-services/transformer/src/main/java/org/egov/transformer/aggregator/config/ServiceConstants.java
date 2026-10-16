package org.egov.transformer.aggregator.config;


import lombok.experimental.UtilityClass;

@UtilityClass
public final class ServiceConstants {

    public static final String EXTERNAL_SERVICE_EXCEPTION = "External Service threw an Exception: ";
    public static final String SEARCHER_SERVICE_EXCEPTION = "Exception while fetching from searcher: ";

    //Elastic Query Template
    public static final String ES_TERM_QUERY = "{\"query\":{\"term\":{\"%s\":\"%s\"}},\"size\":10000}";

    public static final String HOUSEHOLD_BASE_PATH = "";

    public static final String HOUSEHOLD_ID = "_id";
    public static final String HOUSEHOLD_MEMBER_HOUSEHOLD_ID = "householdId.keyword";
    public static final String HOUSEHOLD_MEMBER_INDIVIDUAL_ID = "individualId.keyword";
    public static final String INDIVIDUAL_ID = "_id";
    public static final String AGG_HOUSEHOLD_ID = "_id";

    public static final String CREATED_TIME = "createdTime";
    public static final String CREATED_TIME_NESTED = "auditDetails.createdTime";

    public static final String ORDER_ASC = "ASC";
    public static final String ORDER_DESC = "DESC";

    //Response JSON Path
    public static final String ES_HITS_PATH = "$.hits.hits.*._source";
}
