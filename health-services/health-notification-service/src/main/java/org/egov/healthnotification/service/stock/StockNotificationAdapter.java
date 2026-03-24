package org.egov.healthnotification.service.stock;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.core.AdditionalFields;
import org.egov.common.models.stock.Stock;
import org.egov.healthnotification.Constants;
import org.egov.healthnotification.service.FacilityUserService;
import org.egov.healthnotification.service.MdmsService;
import org.egov.healthnotification.web.models.MdmsV2Data;
import org.egov.healthnotification.web.models.NotificationEvent;
import org.egov.healthnotification.web.models.enums.NotificationChannel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translates Stock events into generic NotificationEvent(s).
 *
 * Key design:
 *   - Event type is determined by additionalFields.stockEntryType
 *   - Notification recipient is the secondaryRole party:
 *     if primaryRole=SENDER → notify receiver; if primaryRole=RECEIVER → notify sender
 *   - Placeholders use sku (commodity name), mrnNumber (transaction ref) from additionalFields
 *   - MDMS campaignType is "PUSH-NOTIFICATION", template code is inside scheduledNotifications[0]
 */
@Service
@Slf4j
public class StockNotificationAdapter {

    private final MdmsService mdmsService;
    private final FacilityUserService facilityUserService;

    @Autowired
    public StockNotificationAdapter(MdmsService mdmsService,
                                     FacilityUserService facilityUserService) {
        this.mdmsService = mdmsService;
        this.facilityUserService = facilityUserService;
    }

    /**
     * Converts a Stock event into NotificationEvent(s).
     */
    public List<NotificationEvent> buildNotificationEvents(Stock stock, String topic) {
        List<NotificationEvent> events = new ArrayList<>();

        String tenantId = stock.getTenantId();

        // Extract stockEntryType from additionalFields — this drives the event type
        String stockEntryType = getAdditionalFieldValue(stock, Constants.ADDITIONAL_FIELD_STOCK_ENTRY_TYPE);
        if (stockEntryType == null || stockEntryType.isBlank()) {
            log.info("No stockEntryType in additionalFields for stock id={}. Skipping.", stock.getId());
            return events;
        }

        String eventType = mapStockEntryTypeToEventType(stockEntryType);
        if (eventType == null) {
            log.info("No mapping for stockEntryType={} for stock id={}. Skipping.", stockEntryType, stock.getId());
            return events;
        }

        log.info("Stock id={}: stockEntryType={} → eventType={}", stock.getId(), stockEntryType, eventType);

        // Fetch MDMS config using campaignType "PUSH-NOTIFICATION"
        MdmsV2Data notificationConfig = fetchNotificationConfig(tenantId);
        if (notificationConfig == null) {
            log.info("No MDMS notification config found for push notifications. tenantId={}", tenantId);
            return events;
        }

        // Find the matching event config from MDMS
        JsonNode eventConfig = findEventConfig(notificationConfig, eventType);
        if (eventConfig == null) {
            log.info("No enabled event config found for eventType={}", eventType);
            return events;
        }

        // Extract templateCode from scheduledNotifications[0]
        String templateCode = extractTemplateCode(eventConfig);
        if (templateCode == null || templateCode.isBlank()) {
            log.warn("No templateCode found for eventType={}. Skipping.", eventType);
            return events;
        }

        // Extract locale
        List<String> locales = extractLocales(notificationConfig);
        String locale = (locales != null && !locales.isEmpty()) ? locales.get(0) : "en_NG";

        // Build placeholders from stock + additionalFields
        Map<String, Object> placeholders = buildPlaceholders(stock, tenantId);

        // Build navigation data for screen redirect
        Map<String, String> navigationData = buildNavigationData(eventType, stock);

        // Map eventType to a human-readable title for the push notification
        String title = mapEventTypeToTitle(eventType);

        // Determine notification recipient facilityId: the secondaryRole party
        String recipientFacilityId = resolveRecipientFacilityId(stock);
        if (recipientFacilityId != null && !recipientFacilityId.isBlank()) {
            events.add(buildEvent(stock, eventType, tenantId, templateCode,
                    locale, recipientFacilityId, placeholders, navigationData, title));
        }

        log.info("Built {} notification event(s) for stock id={}, eventType={}",
                events.size(), stock.getId(), eventType);
        return events;
    }

    // ═══════════════════════════════════════════════════════
    //  Event Type Mapping
    // ═══════════════════════════════════════════════════════

    /**
     * Maps eventType to a human-readable push notification title.
     */
    String mapEventTypeToTitle(String eventType) {
        switch (eventType) {
            case Constants.EVENT_TYPE_STOCK_ISSUE:
                return Constants.TITLE_STOCK_ISSUE;
            case Constants.EVENT_TYPE_STOCK_RECEIPT:
                return Constants.TITLE_STOCK_RECEIPT;
            case Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE:
                return Constants.TITLE_STOCK_REVERSE_ISSUE;
            case Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT:
                return Constants.TITLE_STOCK_REVERSE_ACCEPT;
            case Constants.EVENT_TYPE_STOCK_REVERSE_REJECT:
                return Constants.TITLE_STOCK_REVERSE_REJECT;
            default:
                return eventType;
        }
    }

    /**
     * Maps stockEntryType (from additionalFields) to MDMS eventType.
     *
     * stockEntryType → MDMS eventType:
     *   ISSUED          → STOCK_ISSUE_PUSH_NOTIFICATION
     *   RECEIPT          → STOCK_RECEIVE_PUSH_NOTIFICATION
     *   RETURNED         → STOCK_REVERSE_ISSUE_PUSH_NOTIFICATION
     *   RETURN_ACCEPTED  → STOCK_REVERSE_ACCEPT_PUSH_NOTIFICATION
     *   RETURN_REJECTED  → STOCK_REVERSE_REJECT_PUSH_NOTIFICATION
     */
    String mapStockEntryTypeToEventType(String stockEntryType) {
        switch (stockEntryType) {
            case Constants.STOCK_ENTRY_TYPE_ISSUED:
                return Constants.EVENT_TYPE_STOCK_ISSUE;
            case Constants.STOCK_ENTRY_TYPE_RECEIPT:
                return Constants.EVENT_TYPE_STOCK_RECEIPT;
            case Constants.STOCK_ENTRY_TYPE_RETURNED:
                return Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE;
            case Constants.STOCK_ENTRY_TYPE_RETURN_ACCEPTED:
                return Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT;
            case Constants.STOCK_ENTRY_TYPE_RETURN_REJECTED:
                return Constants.EVENT_TYPE_STOCK_REVERSE_REJECT;
            default:
                return null;
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Recipient Resolution
    // ═══════════════════════════════════════════════════════

    /**
     * Resolves the recipient facilityId — the secondaryRole party.
     *
     *   primaryRole=SENDER   → notify receiverId
     *   primaryRole=RECEIVER → notify senderId
     */
    private String resolveRecipientFacilityId(Stock stock) {
        String primaryRole = getAdditionalFieldValue(stock, Constants.ADDITIONAL_FIELD_PRIMARY_ROLE);

        if (Constants.ROLE_SENDER.equals(primaryRole)) {
            log.debug("primaryRole=SENDER → recipient facilityId={}", stock.getReceiverId());
            return stock.getReceiverId();
        } else if (Constants.ROLE_RECEIVER.equals(primaryRole)) {
            log.debug("primaryRole=RECEIVER → recipient facilityId={}", stock.getSenderId());
            return stock.getSenderId();
        } else {
            log.warn("Unknown primaryRole={}. Defaulting to receiverId={}", primaryRole, stock.getReceiverId());
            return stock.getReceiverId();
        }
    }

    // ═══════════════════════════════════════════════════════
    //  Placeholder Building
    // ═══════════════════════════════════════════════════════

    /**
     * Builds placeholder map matching MDMS placeholders:
     *   {Sending_Facility_Name}, {Receiving_Facility_Name},
     *   {Transaction_ID}, {quantity_of_sku}
     */
    private Map<String, Object> buildPlaceholders(Stock stock, String tenantId) {
        Map<String, Object> placeholders = new HashMap<>();

        // Facility names
        String senderName = facilityUserService.resolveFacilityName(
                stock.getSenderId(), stock.getSenderType(), tenantId);
        String receiverName = facilityUserService.resolveFacilityName(
                stock.getReceiverId(), stock.getReceiverType(), tenantId);

        placeholders.put(Constants.PLACEHOLDER_SENDING_FACILITY, senderName);
        placeholders.put(Constants.PLACEHOLDER_RECEIVING_FACILITY, receiverName);

        // Transaction reference from mrnNumber (additionalFields), fallback to clientReferenceId
        String mrnNumber = getAdditionalFieldValue(stock, Constants.ADDITIONAL_FIELD_MRN_NUMBER);
        placeholders.put(Constants.PLACEHOLDER_TRANSACTION_ID,
                mrnNumber != null ? mrnNumber : (stock.getClientReferenceId() != null ? stock.getClientReferenceId() : stock.getId()));

        // Quantity of SKU — sku name + quantity combined, or just quantity
        String sku = getAdditionalFieldValue(stock, Constants.ADDITIONAL_FIELD_SKU);
        Integer quantity = stock.getQuantity();
        String qtyStr = quantity != null ? quantity.toString() : "0";

        // "quantity_of_sku" — e.g., "50 ITN Nets" or just "50" if no sku
        String quantityOfSku = sku != null ? (qtyStr + " " + sku) : qtyStr;
        placeholders.put(Constants.PLACEHOLDER_QUANTITY_OF_SKU, quantityOfSku);

        return placeholders;
    }

    // ═══════════════════════════════════════════════════════
    //  Navigation Data (screen redirect)
    // ═══════════════════════════════════════════════════════

    private Map<String, String> buildNavigationData(String eventType, Stock stock) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationType", Constants.NOTIFICATION_TYPE_STOCK);
        data.put("eventType", eventType);

        String mrnNumber = getAdditionalFieldValue(stock, Constants.ADDITIONAL_FIELD_MRN_NUMBER);
        data.put("transactionRef", mrnNumber != null ? mrnNumber :
                (stock.getClientReferenceId() != null ? stock.getClientReferenceId() : stock.getId()));

        switch (eventType) {
            case Constants.EVENT_TYPE_STOCK_ISSUE:
                data.put("screen", Constants.SCREEN_PENDING_RECEIPT);
                break;
            case Constants.EVENT_TYPE_STOCK_RECEIPT:
                data.put("screen", Constants.SCREEN_TRANSACTION_DETAILS);
                break;
            case Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE:
                data.put("screen", Constants.SCREEN_PENDING_RECEIPT);
                break;
            case Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT:
            case Constants.EVENT_TYPE_STOCK_REVERSE_REJECT:
                data.put("screen", Constants.SCREEN_RECORD_RECEIPT);
                break;
            default:
                break;
        }

        return data;
    }

    // ═══════════════════════════════════════════════════════
    //  MDMS Helpers
    // ═══════════════════════════════════════════════════════

    private MdmsV2Data fetchNotificationConfig(String tenantId) {
        try {
            return mdmsService.fetchNotificationConfigByProjectType(
                    Constants.CAMPAIGN_TYPE_PUSH_NOTIFICATION, tenantId);
        } catch (Exception e) {
            log.error("Failed to fetch push notification config from MDMS: {}", e.getMessage());
            return null;
        }
    }

    private JsonNode findEventConfig(MdmsV2Data config, String eventType) {
        JsonNode eventNotifications = config.getData().get(Constants.FIELD_EVENT_NOTIFICATIONS);
        if (eventNotifications == null || !eventNotifications.isArray()) {
            return null;
        }

        for (JsonNode eventNode : eventNotifications) {
            String type = eventNode.path(Constants.FIELD_EVENT_TYPE).asText();
            boolean enabled = eventNode.path(Constants.FIELD_ENABLED).asBoolean(false);

            if (eventType.equals(type) && enabled) {
                return eventNode;
            }
        }
        return null;
    }

    /**
     * Extracts templateCode from scheduledNotifications[0].templateCode
     * as per MDMS structure.
     */
    private String extractTemplateCode(JsonNode eventConfig) {
        JsonNode scheduledNotifications = eventConfig.path(Constants.FIELD_SCHEDULED_NOTIFICATIONS);
        if (scheduledNotifications.isArray() && scheduledNotifications.size() > 0) {
            JsonNode first = scheduledNotifications.get(0);
            if (first.path(Constants.FIELD_ENABLED).asBoolean(false)) {
                return first.path(Constants.FIELD_TEMPLATE_CODE).asText(null);
            }
        }
        return null;
    }

    private List<String> extractLocales(MdmsV2Data config) {
        List<String> locales = new ArrayList<>();
        try {
            JsonNode localeNode = config.getData().path(Constants.FIELD_LOCALE);
            if (localeNode.isArray()) {
                for (JsonNode node : localeNode) {
                    String locale = node.asText();
                    if (locale != null && !locale.isBlank()) {
                        locales.add(locale);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error extracting locales from MDMS config: {}", e.getMessage());
        }
        return locales;
    }

    // ═══════════════════════════════════════════════════════
    //  AdditionalFields Helper
    // ═══════════════════════════════════════════════════════

    private String getAdditionalFieldValue(Stock stock, String key) {
        AdditionalFields additionalFields = stock.getAdditionalFields();
        if (additionalFields == null || additionalFields.getFields() == null) {
            return null;
        }
        return additionalFields.getFields().stream()
                .filter(f -> key.equals(f.getKey()))
                .map(f -> String.valueOf(f.getValue()))
                .findFirst()
                .orElse(null);
    }

    // ═══════════════════════════════════════════════════════
    //  Event Builder
    // ═══════════════════════════════════════════════════════

    private NotificationEvent buildEvent(Stock stock, String eventType, String tenantId,
                                          String templateCode, String locale,
                                          String recipientFacilityId,
                                          Map<String, Object> placeholders, Map<String, String> data,
                                          String title) {
        return NotificationEvent.builder()
                .tenantId(tenantId)
                .eventType(eventType)
                .entityType(Constants.ENTITY_TYPE_STOCK)
                .entityId(stock.getId())
                .templateCode(templateCode)
                .title(title)
                .locale(locale)
                .recipientFacilityId(recipientFacilityId)
                .placeholders(placeholders)
                .data(data)
                .channel(NotificationChannel.PUSH)
                .build();
    }
}
