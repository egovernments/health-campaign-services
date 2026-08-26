package org.egov.transformer.producer;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.egov.transformer.config.TransformerProperties;
import org.egov.transformer.models.ErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
@Slf4j
public class TransformerErrorProducer {

    private final Producer producer;

    private final TransformerProperties properties;

    // Holds the Kafka source topic of the record currently being processed on this thread.
    // Service-layer failures deep in the enrichment chain don't know the source topic and
    // pass null; this lets sendToErrorTopic fall back to the topic stamped by the consumer,
    // so every error record carries the correct source topic (and stays replayable).
    private static final ThreadLocal<String> CURRENT_SOURCE_TOPIC = new ThreadLocal<>();

    @Autowired
    public TransformerErrorProducer(Producer producer, TransformerProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    /**
     * Work to run with the source topic stamped on the current thread. Allowed to throw so
     * consumers can run their (checked-exception) deserialize/transform logic inside it.
     */
    @FunctionalInterface
    public interface SourceTopicScopedWork {
        void run() throws Exception;
    }

    /**
     * PREFERRED entry point for consumers. Stamps {@code topic} for the current thread, runs
     * {@code work}, and ALWAYS clears the stamp in a finally — so a consumer can never leak a
     * stale topic onto a pooled listener thread. Any exception from {@code work} propagates to
     * the caller (the consumer's catch), exactly as before.
     *
     * <pre>
     * errorQueueProducer.withSourceTopic(topic, () -&gt; {
     *     List&lt;T&gt; list = objectMapper.readValue(payload, T[].class);
     *     service.transform(list);
     * });
     * </pre>
     */
    public void withSourceTopic(String sourceTopic, SourceTopicScopedWork work) throws Exception {
        setSourceTopic(sourceTopic);
        try {
            work.run();
        } finally {
            clearSourceTopic();
        }
    }

    /**
     * Low-level stamp; prefer {@link #withSourceTopic}. If you call this directly you MUST call
     * {@link #clearSourceTopic()} in a finally, or a stale topic will leak onto the pooled
     * listener thread and stamp the next unrelated record's error with the wrong source topic.
     */
    public void setSourceTopic(String sourceTopic) {
        CURRENT_SOURCE_TOPIC.set(sourceTopic);
    }

    /**
     * Clear the stamped source topic. Uses remove() (not set(null)) to avoid retaining empty
     * entries on pooled threads. Prefer {@link #withSourceTopic} which does this for you.
     */
    public void clearSourceTopic() {
        CURRENT_SOURCE_TOPIC.remove();
    }

    public void sendToErrorTopic(Object payload, String sourceTopic, Exception exception) {
        try {
            String resolvedTopic = StringUtils.isNotBlank(sourceTopic) ? sourceTopic : CURRENT_SOURCE_TOPIC.get();
            String payloadString = payload != null ? payload.toString() : null;
            ErrorMessage errorMessage = ErrorMessage.builder()
                    .id(buildDeterministicId(resolvedTopic, payloadString))
                    .topic(resolvedTopic)
                    .payload(payloadString)
                    .errorMessage(exception.getMessage())
                    .stackTrace(ExceptionUtils.getStackTrace(exception))
                    .timestamp(System.currentTimeMillis())
                    .build();
            producer.push(properties.getTransformerErrorTopic(), errorMessage);
        } catch (Exception e) {
            log.error("TRANSFORMER failed to push error to error topic: {}", ExceptionUtils.getStackTrace(e));
        }
    }

    /**
     * Deterministic id derived from the source topic + payload. Reprocessing the same failed
     * message (Kafka at-least-once redelivery) then yields the SAME id, so Elasticsearch
     * overwrites the existing document instead of creating a duplicate. Distinct failures still
     * get distinct ids; identical failures collapse to one doc (matches QA TC-11). Kept in UUID
     * format for the error index's "id: $.id" mapping.
     */
    private String buildDeterministicId(String topic, String payload) {
        String basis = (topic == null ? "" : topic) + "|" + (payload == null ? "" : payload);
        return UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
