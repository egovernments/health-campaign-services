package org.egov.healthnotification.service.hfreferral;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.egov.healthnotification.Constants;
import org.egov.healthnotification.config.HealthNotificationProperties;
import org.egov.healthnotification.service.FacilityUserService;
import org.egov.healthnotification.service.MdmsService;
import org.egov.healthnotification.web.models.MdmsV2Data;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.egov.healthnotification.web.models.enums.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HFReferralNotificationAdapterTest {

    @InjectMocks
    private HFReferralNotificationAdapter adapter;

    @Mock
    private MdmsService mdmsService;

    @Mock
    private FacilityUserService facilityUserService;

    @Mock
    private HealthNotificationProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ObjectNode buildHFReferralRecord() {
        ObjectNode record = objectMapper.createObjectNode();
        record.put("id", "ref-1");
        record.put("clientReferenceId", "cref-1");
        record.put("tenantId", "ba");
        record.put("name", "rachana");
        record.put("projectId", "project-1");
        record.put("projectFacilityId", "PF-001");
        record.put("beneficiaryId", "ben-1");
        record.put("referralCode", "RCODE-001");
        record.put("symptom", "FEVER");

        ObjectNode additionalFields = objectMapper.createObjectNode();
        additionalFields.put("schema", "HFReferral");
        additionalFields.put("version", 1);

        ArrayNode fields = objectMapper.createArrayNode();
        fields.add(createField("referredBy", "user-uuid-1"));
        fields.add(createField("nameOfReferral", "rachana"));
        fields.add(createField("referralCycle", "3"));
        fields.add(createField("gender", "FEMALE"));
        fields.add(createField("ageInMonths", "9"));
        fields.add(createField("dateOfEvaluation", "1774433865432"));
        fields.add(createField("administrativeArea", "JAMICA"));

        additionalFields.set("fields", fields);
        record.set("additionalFields", additionalFields);

        return record;
    }

    private ObjectNode createField(String key, String value) {
        ObjectNode field = objectMapper.createObjectNode();
        field.put("key", key);
        field.put("value", value);
        return field;
    }

    private MdmsV2Data buildMdmsConfig(String eventType) {
        ObjectNode dataNode = objectMapper.createObjectNode();
        dataNode.put("campaignType", "PUSH-NOTIFICATION");

        ArrayNode localeArray = objectMapper.createArrayNode();
        localeArray.add("en_NG");
        dataNode.set("locale", localeArray);

        ArrayNode eventNotifications = objectMapper.createArrayNode();
        ObjectNode eventNode = objectMapper.createObjectNode();
        eventNode.put("eventType", eventType);
        eventNode.put("enabled", true);

        ArrayNode scheduledNotifications = objectMapper.createArrayNode();
        ObjectNode schedNode = objectMapper.createObjectNode();
        schedNode.put("templateCode", "HCM_REFERRAL_CREATED_SMS");
        schedNode.put("enabled", true);
        scheduledNotifications.add(schedNode);
        eventNode.set("scheduledNotifications", scheduledNotifications);

        eventNotifications.add(eventNode);
        dataNode.set("eventNotifications", eventNotifications);

        return MdmsV2Data.builder().data(dataNode).build();
    }

    @Test
    void buildNotificationEvents_createTopic_buildsSingleEvent() {
        ObjectNode record = buildHFReferralRecord();
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_REFERRAL_CREATED);

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(
                eq(Constants.CAMPAIGN_TYPE_PUSH_NOTIFICATION), eq("ba"))).thenReturn(config);
        when(facilityUserService.resolveProjectFacilityId(eq("PF-001"), eq("ba")))
                .thenReturn("FAC-001");

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        assertEquals(1, events.size());
        NotificationEvent event = events.get(0);
        assertEquals(Constants.EVENT_TYPE_REFERRAL_CREATED, event.getEventType());
        assertEquals(Constants.ENTITY_TYPE_HF_REFERRAL, event.getEntityType());
        assertEquals("ref-1", event.getEntityId());
        assertEquals("HCM_REFERRAL_CREATED_SMS", event.getTemplateCode());
        assertEquals("en_NG", event.getLocale());
        assertEquals("ba", event.getTenantId());
        assertEquals(Constants.TITLE_REFERRAL_CREATED, event.getTitle());
        assertEquals(NotificationChannel.PUSH, event.getChannel());
        // facilityId resolved from projectFacilityId
        assertEquals("FAC-001", event.getRecipientFacilityId());
    }

    @Test
    void buildNotificationEvents_multiTenantCreateTopic_buildsSingleEvent() {
        ObjectNode record = buildHFReferralRecord();
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_REFERRAL_CREATED);

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveProjectFacilityId(any(), any())).thenReturn("FAC-001");

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "ba-save-hfreferral-topic");

        assertEquals(1, events.size());
    }

    @Test
    void buildNotificationEvents_updateTopic_returnsEmpty() {
        ObjectNode record = buildHFReferralRecord();

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "update-hfreferral-topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_navigationDataPopulatedCorrectly() {
        ObjectNode record = buildHFReferralRecord();
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_REFERRAL_CREATED);

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveProjectFacilityId(any(), any())).thenReturn("FAC-001");

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        Map<String, String> data = events.get(0).getData();
        assertEquals(Constants.NOTIFICATION_TYPE_REFERRAL, data.get("notificationType"));
        assertEquals(Constants.EVENT_TYPE_REFERRAL_CREATED, data.get("eventType"));
        assertEquals("RCODE-001", data.get("referralCode"));
        assertEquals("ben-1", data.get("beneficiaryId"));
        assertEquals("project-1", data.get("projectId"));
        assertEquals("PF-001", data.get("projectFacilityId"));
        assertEquals(Constants.SCREEN_REFERRAL_DETAILS, data.get("screen"));
    }

    @Test
    void buildNotificationEvents_placeholdersBuiltCorrectly() {
        ObjectNode record = buildHFReferralRecord();
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_REFERRAL_CREATED);

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveProjectFacilityId(any(), any())).thenReturn("FAC-001");

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        Map<String, Object> placeholders = events.get(0).getPlaceholders();
        assertEquals("rachana", placeholders.get(Constants.PLACEHOLDER_REFERRAL_NAME));
        assertEquals("RCODE-001", placeholders.get(Constants.PLACEHOLDER_REFERRAL_CODE));
        assertEquals("FEVER", placeholders.get(Constants.PLACEHOLDER_SYMPTOM));
        assertEquals("FEMALE", placeholders.get(Constants.PLACEHOLDER_GENDER));
        assertEquals("9", placeholders.get(Constants.PLACEHOLDER_AGE_IN_MONTHS));
        assertEquals("3", placeholders.get(Constants.PLACEHOLDER_REFERRAL_CYCLE));
        assertEquals("user-uuid-1", placeholders.get(Constants.PLACEHOLDER_REFERRED_BY));
        assertEquals("1774433865432", placeholders.get(Constants.PLACEHOLDER_DATE_OF_EVALUATION));
        assertEquals("JAMICA", placeholders.get(Constants.PLACEHOLDER_ADMINISTRATIVE_AREA));
    }

    @Test
    void buildNotificationEvents_noTenantId_returnsEmpty() {
        ObjectNode record = buildHFReferralRecord();
        record.put("tenantId", "");

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_noProjectFacilityId_returnsEmpty() {
        ObjectNode record = buildHFReferralRecord();
        record.put("projectFacilityId", "");

        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_REFERRAL_CREATED);

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_facilityIdResolutionFails_returnsEmpty() {
        ObjectNode record = buildHFReferralRecord();
        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_REFERRAL_CREATED);

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveProjectFacilityId(any(), any())).thenReturn(null);

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_noMdmsConfig_returnsEmpty() {
        ObjectNode record = buildHFReferralRecord();

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any()))
                .thenThrow(new RuntimeException("MDMS down"));

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_eventTypeDisabledInMdms_returnsEmpty() {
        ObjectNode record = buildHFReferralRecord();

        ObjectNode dataNode = objectMapper.createObjectNode();
        ArrayNode eventNotifications = objectMapper.createArrayNode();
        ObjectNode eventNode = objectMapper.createObjectNode();
        eventNode.put("eventType", Constants.EVENT_TYPE_REFERRAL_CREATED);
        eventNode.put("enabled", false);
        eventNotifications.add(eventNode);
        dataNode.set("eventNotifications", eventNotifications);

        MdmsV2Data config = MdmsV2Data.builder().data(dataNode).build();

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        assertTrue(events.isEmpty());
    }

    @Test
    void buildNotificationEvents_usesClientReferenceIdWhenIdIsUnknown() {
        ObjectNode record = buildHFReferralRecord();
        record.remove("id");

        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_REFERRAL_CREATED);

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveProjectFacilityId(any(), any())).thenReturn("FAC-001");

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        assertEquals(1, events.size());
        assertEquals("cref-1", events.get(0).getEntityId());
    }

    @Test
    void buildNotificationEvents_missingAdditionalFields_usesDefaults() {
        ObjectNode record = objectMapper.createObjectNode();
        record.put("id", "ref-2");
        record.put("clientReferenceId", "cref-2");
        record.put("tenantId", "ba");
        record.put("projectId", "project-1");
        record.put("projectFacilityId", "PF-002");
        record.put("referralCode", "RCODE-002");
        record.put("symptom", "COUGH");

        MdmsV2Data config = buildMdmsConfig(Constants.EVENT_TYPE_REFERRAL_CREATED);

        when(properties.getHfReferralCreateTopic()).thenReturn("save-hfreferral-topic");
        when(mdmsService.fetchNotificationConfigByProjectType(any(), any())).thenReturn(config);
        when(facilityUserService.resolveProjectFacilityId(any(), any())).thenReturn("FAC-002");

        List<NotificationEvent> events = adapter.buildNotificationEvents(record, "save-hfreferral-topic");

        assertEquals(1, events.size());
        Map<String, Object> placeholders = events.get(0).getPlaceholders();
        assertEquals("", placeholders.get(Constants.PLACEHOLDER_REFERRAL_NAME));
        assertEquals("", placeholders.get(Constants.PLACEHOLDER_GENDER));
        assertEquals("", placeholders.get(Constants.PLACEHOLDER_AGE_IN_MONTHS));
    }
}
