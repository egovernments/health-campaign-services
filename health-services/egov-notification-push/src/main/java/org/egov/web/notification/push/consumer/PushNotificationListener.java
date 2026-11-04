package org.egov.web.notification.push.consumer;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.egov.web.notification.push.consumer.contract.PushNotificationRequest;
import org.egov.web.notification.push.service.DeviceTokenService;
import org.egov.web.notification.push.service.PushMetrics;
import org.egov.web.notification.push.service.PushNotificationService;
import org.egov.web.notification.push.web.contract.DeviceToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PushNotificationListener {

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PushMetrics metrics;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @KafkaListener(topicPattern = "(${kafka.tenant.id.pattern}){0,1}${kafka.topics.notification.push}")
    public void processPushNotification(HashMap<String, Object> consumerRecord,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        PushNotificationRequest request = objectMapper.convertValue(consumerRecord, PushNotificationRequest.class);
        if (request.getEventId() == null || request.getEventId().isBlank()) {
            request.setEventId(topic + "-" + partition + "-" + offset);
        }
        log.info("PUSH_TRACE received eventId={} title={} tenantId={} facilityId={} roles={} data={}",
                request.getEventId(), request.getTitle(), request.getTenantId(), request.getFacilityId(),
                request.getRecipientRoles(), request.getData());

        boolean noTokens = request.getDeviceTokens() == null || request.getDeviceTokens().isEmpty();
        if (noTokens && request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            List<DeviceToken> tokens = deviceTokenService.getActiveTokensForUsers(request.getUserIds(), request.getTenantId());
            if (tokens.isEmpty()) {
                metrics.noRecipients(request.getTenantId());
                log.warn("PUSH_SKIP reason=NO_TOKENS eventId={} userIds={} tenantId={} title={} data={}",
                        request.getEventId(), request.getUserIds(), request.getTenantId(), request.getTitle(), request.getData());
                return;
            }
            request.setDeviceTokens(tokens.stream().map(DeviceToken::getDeviceToken).distinct().collect(Collectors.toList()));
            log.info("PUSH_TRACE resolved count={} eventId={} userIds={} data={}",
                    request.getDeviceTokens().size(), request.getEventId(), request.getUserIds(), request.getData());
        } else if (noTokens && request.getFacilityId() != null && !request.getFacilityId().isEmpty()) {

            String facilityId = request.getFacilityId();
            String tenantId = request.getTenantId();
            List<String> roles = request.getRecipientRoles();

            List<DeviceToken> tokens;
            if (roles != null && !roles.isEmpty()) {
                log.info("Resolving device tokens for facilityId: {} with roles: {}", facilityId, roles);
                tokens = deviceTokenService.getTokensByFacilityIdAndRoles(facilityId, roles, tenantId);
            } else {
                log.info("No recipientRoles in request. Resolving all tokens for facilityId: {}", facilityId);
                tokens = deviceTokenService.getTokensByFacilityId(facilityId, tenantId);
            }

            if (tokens == null || tokens.isEmpty()) {
                metrics.noRecipients(tenantId);
                log.warn("PUSH_SKIP reason=NO_TOKENS facilityId={} roles={} tenantId={} title={} data={}",
                        facilityId, roles, tenantId, request.getTitle(), request.getData());
                return;
            }

            List<String> resolvedTokens = tokens.stream()
                    .map(DeviceToken::getDeviceToken)
                    .collect(Collectors.toList());
            request.setDeviceTokens(resolvedTokens);
            log.info("PUSH_TRACE resolved count={} facilityId={} roles={} users={} data={}",
                    resolvedTokens.size(), facilityId, roles,
                    tokens.stream().map(t -> t.getUserId() + ":" + t.getUserRoles()).collect(Collectors.toList()),
                    request.getData());
        }

        pushNotificationService.sendPushNotification(request);
    }

}
