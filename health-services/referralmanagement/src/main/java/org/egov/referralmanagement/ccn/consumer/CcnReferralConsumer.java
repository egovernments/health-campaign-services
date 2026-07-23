package org.egov.referralmanagement.ccn.consumer;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.referralmanagement.ccn.CcnReferralService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Isolated CCN consumer: forwards created HFReferrals to SPICE via ONIX.
 *
 * <p>Listens on the SAME create-HFReferral topic the persister uses, but under a DIFFERENT consumer
 * group ({@code referralmanagement.ccn.consumer-group}) so it gets its own full copy of every
 * referral (pub-sub fan-out) — never stealing messages from the persister.</p>
 *
 * <p>The topic value is a JSON ARRAY ({@code List<HFReferral>} from GenericRepository.save), so this
 * consumer uses a dedicated {@code ccnKafkaListenerContainerFactory} (StringDeserializer) and parses
 * the JSON itself — the service-wide HashMapDeserializer cannot handle arrays.</p>
 *
 * <p>Fully separate from {@code HFReferralConsumer}; no shared code path. Dormant unless
 * {@code referralmanagement.ccn.enabled=true}.</p>
 */
@Slf4j
@Component
public class CcnReferralConsumer {

    private final CcnReferralService ccnReferralService;
    private final ObjectMapper objectMapper;

    public CcnReferralConsumer(CcnReferralService ccnReferralService,
                               @Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.ccnReferralService = ccnReferralService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${referralmanagement.hfreferral.kafka.create.topic}",
            groupId = "${referralmanagement.ccn.consumer-group}",
            containerFactory = "ccnKafkaListenerContainerFactory")
    public void onHFReferralCreate(String message,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<HFReferral> referrals = parse(message);
            if (referrals.isEmpty()) {
                return;
            }
            log.info("CCN consumer received {} referral(s) from topic {}", referrals.size(), topic);
            for (HFReferral referral : referrals) {
                ccnReferralService.forward(referral);
            }
        } catch (Exception exception) {
            // Isolated: log only. Never rethrow — must not disrupt the persist/create flow.
            log.error("CCN consumer error on topic {}: {}", topic, exception.getMessage());
            log.error("Trace: {}", ExceptionUtils.getStackTrace(exception));
        }
    }

    /** save() publishes a JSON array; be tolerant of a single object too. */
    public List<HFReferral> parse(String message) throws Exception {
        List<HFReferral> out = new ArrayList<>();
        JsonNode node = objectMapper.readTree(message);
        if (node.isArray()) {
            for (JsonNode n : node) {
                out.add(objectMapper.convertValue(n, HFReferral.class));
            }
        } else if (node.isObject()) {
            out.add(objectMapper.convertValue(node, HFReferral.class));
        }
        return out;
    }
}
