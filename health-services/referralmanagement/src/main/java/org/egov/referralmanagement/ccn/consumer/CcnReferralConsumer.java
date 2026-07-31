package org.egov.referralmanagement.ccn.consumer;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.common.models.referralmanagement.Referral;
import org.egov.common.models.referralmanagement.hfreferral.HFReferral;
import org.egov.referralmanagement.ccn.CcnReferralService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Isolated CCN consumer: forwards created {@link Referral}s (normal referral flow) to SPICE via ONIX.
 *
 * <p>Listens on the SAME create-Referral topic ({@code save-referral-topic}) the persister uses, but
 * under a DIFFERENT consumer group so it gets its own full copy (pub-sub fan-out) — never stealing
 * from the persister. The topic value is a JSON ARRAY (List&lt;Referral&gt; from GenericRepository.save),
 * so a dedicated StringDeserializer factory is used and the JSON is parsed here.</p>
 *
 * <p>Dormant unless {@code referralmanagement.ccn.enabled=true}.</p>
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

    // topicPattern (not topics): in central-instance mode the persister publishes to tenant-prefixed
    // topics (e.g. sierraleone-save-referral-topic), so match any *save-referral-topic.
    @KafkaListener(
            topicPattern = "${referralmanagement.ccn.create-topic-pattern:.*save-referral-topic}",
            groupId = "${referralmanagement.ccn.consumer-group}",
            containerFactory = "ccnKafkaListenerContainerFactory")
    public void onReferralCreate(String message,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<Referral> referrals = parse(message);
            if (referrals.isEmpty()) {
                return;
            }
            log.info("CCN consumer received {} referral(s) from topic {}", referrals.size(), topic);
            for (Referral referral : referrals) {
                ccnReferralService.forward(referral);
            }
        } catch (Exception exception) {
            // Isolated: log only. Never rethrow — must not disrupt the persist/create flow.
            log.error("CCN consumer error on topic {}: {}", topic, exception.getMessage());
            log.error("Trace: {}", ExceptionUtils.getStackTrace(exception));
        }
    }

    /** save() publishes a JSON array; tolerate a single object too. */
    public List<Referral> parse(String message) throws Exception {
        List<Referral> out = new ArrayList<>();
        JsonNode node = objectMapper.readTree(message);
        if (node.isArray()) {
            for (JsonNode n : node) out.add(objectMapper.convertValue(n, Referral.class));
        } else if (node.isObject()) {
            out.add(objectMapper.convertValue(node, Referral.class));
        }
        return out;
    }

    // ── HFReferral fan-out ────────────────────────────────────────────────────
    // The mobile "Refer" screen creates HFReferrals (published to *save-hfreferral-topic) — a DIFFERENT
    // entity/topic than Referral. Listen for those too and forward them to SPICE via the same ONIX path.
    @KafkaListener(
            topicPattern = "${referralmanagement.ccn.hf-create-topic-pattern:.*save-hfreferral-topic}",
            groupId = "${referralmanagement.ccn.consumer-group}",
            containerFactory = "ccnKafkaListenerContainerFactory")
    public void onHfReferralCreate(String message,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            List<HFReferral> hfReferrals = parseHf(message);
            if (hfReferrals.isEmpty()) {
                return;
            }
            log.info("CCN consumer received {} HFReferral(s) from topic {}", hfReferrals.size(), topic);
            for (HFReferral hf : hfReferrals) {
                ccnReferralService.forwardHfReferral(hf);
            }
        } catch (Exception exception) {
            // Isolated: log only. Never rethrow — must not disrupt the persist/create flow.
            log.error("CCN consumer error on hfreferral topic {}: {}", topic, exception.getMessage());
            log.error("Trace: {}", ExceptionUtils.getStackTrace(exception));
        }
    }

    /** save() publishes a JSON array; tolerate a single object too. */
    public List<HFReferral> parseHf(String message) throws Exception {
        List<HFReferral> out = new ArrayList<>();
        JsonNode node = objectMapper.readTree(message);
        if (node.isArray()) {
            for (JsonNode n : node) out.add(objectMapper.convertValue(n, HFReferral.class));
        } else if (node.isObject()) {
            out.add(objectMapper.convertValue(node, HFReferral.class));
        }
        return out;
    }
}
