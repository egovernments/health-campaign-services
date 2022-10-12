package org.digit.health.sync.orchestrator.client;

import org.digit.health.sync.context.metric.SyncStepMetric;
import org.digit.health.sync.context.step.DeliverySyncStep;
import org.digit.health.sync.context.step.RegistrationSyncStep;
import org.digit.health.sync.context.step.SyncStep;
import org.digit.health.sync.helper.SyncStepMetricTestBuilder;
import org.digit.health.sync.helper.SyncUpDataListTestBuilder;
import org.digit.health.sync.orchestrator.SyncOrchestrator;
import org.digit.health.sync.orchestrator.client.enums.SyncLogStatus;
import org.digit.health.sync.orchestrator.client.metric.SyncLogMetric;
import org.digit.health.sync.web.models.SyncUpDataList;
import org.digit.health.sync.web.models.request.DeliveryMapper;
import org.digit.health.sync.web.models.request.HouseholdRegistrationMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCampaignSyncOrchestratorClientTest {

    @Mock
    private SyncOrchestrator syncOrchestrator;

    @Test
    @DisplayName("health camp sync orchestrator client should call health camp sync orchestrator to orchestrate")
    void testThatHealthCampSyncOrchestratorClientCallsHealthCampSyncOrchestratorToOrchestrate() {
        HealthCampaignSyncOrchestratorClient syncOrchestratorClient =
                new HealthCampaignSyncOrchestratorClient(syncOrchestrator);
        SyncUpDataList syncUpDataList = SyncUpDataListTestBuilder.builder()
                .withOneHouseholdRegistrationAndDelivery()
                .build();
        Map<Object, Object> payloadMap = new HashMap<>();
        payloadMap.put("syncUpDataList", syncUpDataList);
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = getStepToPayloadMap();
        when(syncOrchestrator.orchestrate(stepToPayloadMap)).thenReturn(Collections.emptyList());

        syncOrchestratorClient.orchestrate(payloadMap);

        verify(syncOrchestrator, times(1)).orchestrate(stepToPayloadMap);
    }

    @Test
    @DisplayName("health camp sync orchestrator client should orchestrate and return aggregate metrics")
    void testThatHealthCampSyncOrchestratorClientOrchestratesAndReturnsAggregateMetrics() {
        HealthCampaignSyncOrchestratorClient syncOrchestratorClient =
                new HealthCampaignSyncOrchestratorClient(syncOrchestrator);
        SyncUpDataList syncUpDataList = SyncUpDataListTestBuilder.builder()
                .withOneHouseholdRegistrationAndDelivery()
                .build();
        Map<Object, Object> payloadMap = new HashMap<>();
        payloadMap.put("syncUpDataList", syncUpDataList);
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = getStepToPayloadMap();
        List<SyncStepMetric> syncStepMetrics = new ArrayList<>();
        syncStepMetrics.add(SyncStepMetricTestBuilder.builder()
                .withCompletedRegistrationStep().build());
        syncStepMetrics.add(SyncStepMetricTestBuilder.builder()
                .withCompletedDeliveryStep().build());
        SyncLogMetric syncLogMetricExpected = SyncLogMetric.builder()
                .syncLogStatus(SyncLogStatus.COMPLETE)
                .errorCount(0)
                .successCount(2)
                .totalCount(2)
                .build();
        when(syncOrchestrator.orchestrate(stepToPayloadMap)).thenReturn(syncStepMetrics);

        SyncLogMetric syncLogMetric = (SyncLogMetric) syncOrchestratorClient
                .orchestrate(payloadMap);

        assertEquals(syncLogMetricExpected, syncLogMetric);
    }

    @Test
    @DisplayName("health camp sync orchestrator client should orchestrate for multiple payloads and return aggregate metrics")
    void testThatHealthCampSyncOrchestratorClientOrchestratesForMultiplePayloadsAndReturnsAggregateMetrics() {
        HealthCampaignSyncOrchestratorClient syncOrchestratorClient =
                new HealthCampaignSyncOrchestratorClient(syncOrchestrator);
        SyncUpDataList syncUpDataList = SyncUpDataListTestBuilder.builder()
                .withTwoHouseholdRegistrationAndDelivery()
                .build();
        Map<Object, Object> payloadMap = new HashMap<>();
        payloadMap.put("syncUpDataList", syncUpDataList);
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = getStepToPayloadMap();
        Map<Class<? extends SyncStep>, Object> secondStepToPayloadMap = getSecondStepToPayloadMap();
        List<SyncStepMetric> firstSyncStepMetrics = new ArrayList<>();
        firstSyncStepMetrics.add(SyncStepMetricTestBuilder.builder()
                .withCompletedRegistrationStep().build());
        firstSyncStepMetrics.add(SyncStepMetricTestBuilder.builder()
                .withCompletedDeliveryStep().build());
        List<SyncStepMetric> secondSyncStepMetrics = new ArrayList<>();
        secondSyncStepMetrics.add(SyncStepMetricTestBuilder.builder()
                .withCompletedRegistrationStep().build());
        secondSyncStepMetrics.add(SyncStepMetricTestBuilder.builder()
                .withFailedDeliveryStep().build());
        SyncLogMetric syncLogMetricExpected = SyncLogMetric.builder()
                .syncLogStatus(SyncLogStatus.PARTIALLY_COMPLETE)
                .errorCount(1)
                .successCount(3)
                .totalCount(4)
                .build();
        when(syncOrchestrator.orchestrate(stepToPayloadMap)).thenReturn(firstSyncStepMetrics);
        when(syncOrchestrator.orchestrate(secondStepToPayloadMap)).thenReturn(secondSyncStepMetrics);

        SyncLogMetric syncLogMetric = (SyncLogMetric) syncOrchestratorClient
                .orchestrate(payloadMap);

        assertEquals(syncLogMetricExpected, syncLogMetric);
    }

    private static Map<Class<? extends SyncStep>, Object> getStepToPayloadMap() {
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
        stepToPayloadMap.put(RegistrationSyncStep.class,
                HouseholdRegistrationMapper.INSTANCE.toRequest(SyncUpDataListTestBuilder
                        .getHouseholdRegistration()));
        stepToPayloadMap.put(DeliverySyncStep.class,
                DeliveryMapper.INSTANCE.toRequest(SyncUpDataListTestBuilder.getDelivery()));
        return stepToPayloadMap;
    }

    private static Map<Class<? extends SyncStep>, Object> getSecondStepToPayloadMap() {
        Map<Class<? extends SyncStep>, Object> stepToPayloadMap = new HashMap<>();
        stepToPayloadMap.put(RegistrationSyncStep.class,
                HouseholdRegistrationMapper.INSTANCE.toRequest(SyncUpDataListTestBuilder
                        .getHouseholdRegistration("some-different-id")));
        stepToPayloadMap.put(DeliverySyncStep.class,
                DeliveryMapper.INSTANCE.toRequest(SyncUpDataListTestBuilder
                        .getDelivery("some-different-id")));
        return stepToPayloadMap;
    }
}
