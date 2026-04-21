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
 *   - Event type is determined by the combination of additionalFields.stockEntryType + additionalFields.status
 *   - For ISSUED / RETURNED entries, status (IN_TRANSIT, ACCEPTED, REJECTED) determines the notification flow:
 *
 *     ISSUED  + IN_TRANSIT → STOCK_ISSUE (notify receiver B: stock is on its way)
 *     ISSUED  + ACCEPTED  → STOCK_RECEIVE (notify sender A: B accepted)
 *     ISSUED  + REJECTED  → STOCK_ISSUE_REJECT (notify sender A: B rejected)
 *     RETURNED + IN_TRANSIT → STOCK_REVERSE_ISSUE (notify receiver A: return is on its way)
 *     RETURNED + ACCEPTED  → STOCK_REVERSE_ACCEPT (notify sender B: A accepted the return)
 *     RETURNED + REJECTED  → STOCK_REVERSE_REJECT (notify sender B: A rejected the return)
 *
 *   - DAMAGED entries retain legacy behavior (stockEntryType-only mapping)
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

        // Extract status from additionalFields — used in combination with stockEntryType
        String stockStatus = getAdditionalFieldValue(stock, Constants.ADDITIONAL_FIELD_STOCK_STATUS);

        String eventType = mapToEventType(stockEntryType, stockStatus);
        if (eventType == null) {
            log.info("No mapping for stockEntryType={}, status={} for stock id={}. Skipping.",
                    stockEntryType, stockStatus, stock.getId());
            return events;
        }

        log.info("Stock id={}: stockEntryType={}, status={} → eventType={}",
                stock.getId(), stockEntryType, stockStatus, eventType);

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
        String locale = (locales != null && !locales.isEmpty()) ? locales.get(0) : Constants.DEFAULT_LOCALE;

        // Build placeholders from stock + additionalFields
        Map<String, Object> placeholders = buildPlaceholders(stock, tenantId);

        // Build navigation data for screen redirect
        Map<String, String> navigationData = buildNavigationData(eventType, stock);

        // Map eventType to a human-readable title for the push notification
        String title = mapEventTypeToTitle(eventType);

        // Extract recipientRoles from MDMS event config (all roles used for filtering)
        List<String> recipientRoles = extractRecipientRoles(eventConfig);

        // Determine notification recipient facilityId based on stockEntryType + status
        String recipientFacilityId = resolveRecipientFacilityId(stock, stockEntryType, stockStatus);
        if (recipientFacilityId != null && !recipientFacilityId.isBlank()) {
            events.add(buildEvent(stock, eventType, tenantId, templateCode,
                    locale, recipientFacilityId, recipientRoles, placeholders, navigationData, title));
        }

        log.info("Built {} notification event(s) for stock id={}, eventType={}",
                events.size(), stock.getId(), eventType);
        return events;
    }

    // ═══════════════════════════════════════════════════════
    //  Event Type Mapping (stockEntryType + status)
    // ═══════════════════════════════════════════════════════

    /**
     * Maps the combination of stockEntryType + status to the MDMS eventType.
     *
     * For ISSUED and RETURNED, status is required to determine the flow.
     * For DAMAGED, legacy mapping is retained (status ignored).
     *
     * @param stockEntryType e.g. ISSUED, RETURNED, DAMAGED
     * @param status         e.g. IN_TRANSIT, ACCEPTED, REJECTED (may be null for DAMAGED)
     * @return the MDMS eventType string, or null if no mapping exists
     */
    String mapToEventType(String stockEntryType, String status) {
        switch (stockEntryType) {
            case Constants.STOCK_ENTRY_TYPE_ISSUED:
                return mapIssuedStatusToEventType(status);

            case Constants.STOCK_ENTRY_TYPE_RETURNED:
                return mapReturnedStatusToEventType(status);

            case Constants.STOCK_ENTRY_TYPE_DAMAGED:
                // DAMAGED retains legacy behavior — no status check
                return null; // No push notification event type for DAMAGED currently

            // Legacy stockEntryTypes that may still arrive — keep backward compatibility
            case Constants.STOCK_ENTRY_TYPE_RECEIPT:
                return Constants.EVENT_TYPE_STOCK_RECEIPT;
            case Constants.STOCK_ENTRY_TYPE_RETURN_ACCEPTED:
                return Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT;
            case Constants.STOCK_ENTRY_TYPE_RETURN_REJECTED:
                return Constants.EVENT_TYPE_STOCK_REVERSE_REJECT;

            default:
                return null;
        }
    }

    /**
     * ISSUED + status → eventType:
     *   IN_TRANSIT → STOCK_ISSUE_PUSH_NOTIFICATION  (receiver gets notified)
     *   ACCEPTED   → STOCK_RECEIVE_PUSH_NOTIFICATION (sender gets confirmation)
     *   REJECTED   → STOCK_ISSUE_REJECT_PUSH_NOTIFICATION (sender gets rejection)
     */
    private String mapIssuedStatusToEventType(String status) {
        if (status == null || status.isBlank()) {
            log.info("ISSUED entry with no status. Skipping.");
            return null;
        }
        switch (status) {
            case Constants.STOCK_STATUS_IN_TRANSIT:
                return Constants.EVENT_TYPE_STOCK_ISSUE;
            case Constants.STOCK_STATUS_ACCEPTED:
                return Constants.EVENT_TYPE_STOCK_RECEIPT;
            case Constants.STOCK_STATUS_REJECTED:
                return Constants.EVENT_TYPE_STOCK_ISSUE_REJECT;
            default:
                log.info("ISSUED entry with unknown status={}. Skipping.", status);
                return null;
        }
    }

    /**
     * RETURNED + status → eventType:
     *   IN_TRANSIT → STOCK_REVERSE_ISSUE_PUSH_NOTIFICATION  (receiver gets notified)
     *   ACCEPTED   → STOCK_REVERSE_ACCEPT_PUSH_NOTIFICATION (receiver gets notified)
     *   REJECTED   → STOCK_REVERSE_REJECT_PUSH_NOTIFICATION (receiver gets notified)
     */
    private String mapReturnedStatusToEventType(String status) {
        if (status == null || status.isBlank()) {
            log.info("RETURNED entry with no status. Skipping.");
            return null;
        }
        switch (status) {
            case Constants.STOCK_STATUS_IN_TRANSIT:
                return Constants.EVENT_TYPE_STOCK_REVERSE_ISSUE;
            case Constants.STOCK_STATUS_ACCEPTED:
                return Constants.EVENT_TYPE_STOCK_REVERSE_ACCEPT;
            case Constants.STOCK_STATUS_REJECTED:
                return Constants.EVENT_TYPE_STOCK_REVERSE_REJECT;
            default:
                log.info("RETURNED entry with unknown status={}. Skipping.", status);
                return null;
        }
    }

    /**
     * Maps eventType to a human-readable push notification title.
     */
    String mapEventTypeToTitle(String eventType) {
        switch (eventType) {
            case Constants.EVENT_TYPE_STOCK_ISSUE:
                return Constants.TITLE_STOCK_ISSUE;
            case Constants.EVENT_TYPE_STOCK_RECEIPT:
                return Constants.TITLE_STOCK_RECEIPT;
            case Constants.EVENT_TYPE_STOCK_ISSUE_REJECT:
                return Constants.TITLE_STOCK_ISSUE_REJECT;
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

    // ═══════════════════════════════════════════════════════
    //  Recipient Resolution (now based on stockEntryType + status)
    // ═══════════════════════════════════════════════════════

    /**
     * Resolves the recipient facilityId based on stockEntryType + status combination.
     *
     * ISSUED  + IN_TRANSIT → receiver (B) gets the notification: stock is on its way
     * ISSUED  + ACCEPTED   → sender (A) gets the notification: B confirmed receipt
     * ISSUED  + REJECTED   → sender (A) gets the notification: B rejected
     * RETURNED + IN_TRANSIT → receiver (A) gets the notification: return is on its way
     * RETURNED + ACCEPTED  → sender (B) gets the notification: A accepted the return
     * RETURNED + REJECTED  → sender (B) gets the notification: A rejected the return
     *
     * In a RETURNED stock record the roles are flipped:
     *   senderId = B (the facility that is returning the stock)
     *   receiverId = A (the facility that receives the returned stock / decides to accept or reject)
     * So when A acts on the return (ACCEPTED/REJECTED), B must be notified → use senderId.
     *
     * Fallback: uses primaryRole-based logic for backward compatibility.
     */
    private String resolveRecipientFacilityId(Stock stock, String stockEntryType, String status) {
        if (status != null && !status.isBlank()) {
            switch (stockEntryType) {
                case Constants.STOCK_ENTRY_TYPE_ISSUED:
                    return resolveIssuedRecipient(stock, status);

                case Constants.STOCK_ENTRY_TYPE_RETURNED:
                    return resolveReturnedRecipient(stock, status);

                default:
                    break;
            }
        }

        // Fallback: legacy primaryRole-based resolution
        return resolveRecipientByPrimaryRole(stock);
    }

    /**
     * ISSUED recipient:
     *   IN_TRANSIT → receiver (stock is on its way to them)
     *   ACCEPTED   → sender (confirmation that receiver accepted)
     *   REJECTED   → sender (notification that receiver rejected)
     */
    private String resolveIssuedRecipient(Stock stock, String status) {
        switch (status) {
            case Constants.STOCK_STATUS_IN_TRANSIT:
                log.debug("ISSUED+IN_TRANSIT → recipient=receiverId={}", stock.getReceiverId());
                return stock.getReceiverId();
            case Constants.STOCK_STATUS_ACCEPTED:
                log.debug("ISSUED+ACCEPTED → recipient=senderId={}", stock.getSenderId());
                return stock.getSenderId();
            case Constants.STOCK_STATUS_REJECTED:
                log.debug("ISSUED+REJECTED → recipient=senderId={}", stock.getSenderId());
                return stock.getSenderId();
            default:
                return resolveRecipientByPrimaryRole(stock);
        }
    }

    /**
     * RETURNED recipient resolution.
     *
     * <p>In a RETURNED stock record the sender/receiver roles are the <em>opposite</em>
     * of the original ISSUED record:
     * <ul>
     *   <li>{@code senderId} = B — the facility that initiated the return</li>
     *   <li>{@code receiverId} = A — the facility receiving the returned goods (original sender)</li>
     * </ul>
     *
     * <ul>
     *   <li>IN_TRANSIT → notify A ({@code receiverId}): the return is on its way to you</li>
     *   <li>ACCEPTED   → notify B ({@code senderId}): A accepted your return ← <b>bug was here</b></li>
     *   <li>REJECTED   → notify B ({@code senderId}): A rejected your return ← <b>bug was here</b></li>
     * </ul>
     */
    private String resolveReturnedRecipient(Stock stock, String status) {
        switch (status) {
            case Constants.STOCK_STATUS_IN_TRANSIT:
                log.debug("RETURNED+IN_TRANSIT → recipient=receiverId={}", stock.getReceiverId());
                return stock.getReceiverId();
            case Constants.STOCK_STATUS_ACCEPTED:
                log.debug("RETURNED+ACCEPTED → recipient=receiverId={}", stock.getSenderId());
                return stock.getSenderId();
            case Constants.STOCK_STATUS_REJECTED:
                log.debug("RETURNED+REJECTED → recipient=receiverId={}", stock.getSenderId());
                return stock.getSenderId();
            default:
                return resolveRecipientByPrimaryRole(stock);
        }
    }

    /**
     * Legacy fallback: resolves recipient based on primaryRole from additionalFields.
     *   primaryRole=SENDER   → notify receiverId
     *   primaryRole=RECEIVER → notify senderId
     */
    private String resolveRecipientByPrimaryRole(Stock stock) {
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
            case Constants.EVENT_TYPE_STOCK_ISSUE_REJECT:
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
     * Extracts all recipientRoles from the MDMS eventConfig's recipientRoles array.
     * e.g. "recipientRoles": ["WAREHOUSE_MANAGER", "DISTRIBUTOR"] → returns ["WAREHOUSE_MANAGER", "DISTRIBUTOR"]
     * Returns null if not configured.
     */
    private List<String> extractRecipientRoles(JsonNode eventConfig) {
        JsonNode recipientRolesNode = eventConfig.path(Constants.FIELD_RECIPIENT_ROLES);
        if (recipientRolesNode.isArray() && recipientRolesNode.size() > 0) {
            List<String> roles = new ArrayList<>();
            for (JsonNode roleNode : recipientRolesNode) {
                String role = roleNode.asText(null);
                if (role != null && !role.isBlank()) {
                    roles.add(role);
                }
            }
            if (!roles.isEmpty()) {
                log.debug("Extracted recipientRoles from MDMS: {}", roles);
                return roles;
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
                                          String recipientFacilityId, List<String> recipientRoles,
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
                .recipientRoles(recipientRoles)
                .placeholders(placeholders)
                .data(data)
                .channel(NotificationChannel.PUSH)
                .build();
    }
}
