package org.egov.product.summaryreport.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.contract.request.RequestInfo;
import org.egov.common.contract.request.User;
import org.egov.product.summaryreport.repository.SummaryReportRepository;
import org.egov.product.summaryreport.web.models.DailyReportSummary;
import org.egov.product.summaryreport.web.models.SummaryReportSearchCriteria;
import org.egov.product.summaryreport.web.models.SummaryReportSearchRequest;
import org.egov.tracer.model.CustomException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Builds the per-day, per-employee summary array by running the five aggregate queries
 * on the read replica and merging them by day.
 */
@Service
@Slf4j
public class SummaryReportService {

    private final SummaryReportRepository repository;

    @Autowired
    public SummaryReportService(SummaryReportRepository repository) {
        this.repository = repository;
    }

    public List<DailyReportSummary> getDailySummary(SummaryReportSearchRequest request) {
        String createdBy = resolveCreatedBy(request.getRequestInfo());
        SummaryReportSearchCriteria criteria = request.getSummaryReportSearchCriteria();
        validate(criteria);

        String tenantId = criteria.getTenantId();
        Long startDate = criteria.getStartDate();
        Long endDate = criteria.getEndDate();

        // day -> summary; TreeMap keeps the array ordered by date ascending.
        Map<String, DailyReportSummary> byDay = new TreeMap<>();

        mergeCounts(byDay, createdBy,
                repository.householdsRegisteredByDay(createdBy, tenantId, startDate, endDate),
                DailyReportSummary::setHouseholdsRegistered);
        mergeCounts(byDay, createdBy,
                repository.childrenRegisteredByDay(createdBy, tenantId, startDate, endDate),
                DailyReportSummary::setChildrenRegistered);
        mergeCounts(byDay, createdBy,
                repository.beneficiariesRegisteredByDay(createdBy, tenantId, startDate, endDate),
                DailyReportSummary::setBeneficiariesRegistered);
        mergeCounts(byDay, createdBy,
                repository.childrenTreatedByDay(createdBy, tenantId, startDate, endDate),
                DailyReportSummary::setChildrenTreated);

        Map<String, Map<String, Long>> stockByDay =
                repository.stockConsumedByDay(createdBy, tenantId, startDate, endDate);
        stockByDay.forEach((day, stockMap) ->
                getOrCreate(byDay, day, createdBy).setStockConsumedMap(stockMap));

        return new ArrayList<>(byDay.values());
    }

    private void mergeCounts(Map<String, DailyReportSummary> byDay, String createdBy,
                             Map<String, Long> counts,
                             BiConsumer<DailyReportSummary, Long> setter) {
        counts.forEach((day, count) -> setter.accept(getOrCreate(byDay, day, createdBy), count));
    }

    private DailyReportSummary getOrCreate(Map<String, DailyReportSummary> byDay, String day,
                                           String createdBy) {
        return byDay.computeIfAbsent(day, d -> DailyReportSummary.builder()
                .date(d)
                .createdBy(createdBy)
                .build());
    }

    private String resolveCreatedBy(RequestInfo requestInfo) {
        User user = requestInfo != null ? requestInfo.getUserInfo() : null;
        String uuid = user != null ? user.getUuid() : null;
        if (!StringUtils.hasText(uuid)) {
            throw new CustomException("INVALID_USER",
                    "RequestInfo.userInfo.uuid is required to resolve the employee for the summary report");
        }
        return uuid;
    }

    private void validate(SummaryReportSearchCriteria criteria) {
        if (criteria == null) {
            throw new CustomException("INVALID_CRITERIA", "SummaryReportSearchCriteria is required");
        }
        if (criteria.getStartDate() == null || criteria.getEndDate() == null) {
            throw new CustomException("INVALID_DATE_RANGE", "startDate and endDate are required (epoch millis)");
        }
        if (criteria.getStartDate() > criteria.getEndDate()) {
            throw new CustomException("INVALID_DATE_RANGE", "startDate must be less than or equal to endDate");
        }
        if (!StringUtils.hasText(criteria.getTenantId())) {
            throw new CustomException("INVALID_TENANT", "tenantId is required");
        }
    }
}
