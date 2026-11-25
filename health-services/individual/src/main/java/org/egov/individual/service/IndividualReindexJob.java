package org.egov.individual.service;

import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.individual.Individual;
import org.egov.common.models.individual.UserDetails;
import org.egov.common.producer.Producer;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time re-index: after the backfill, sends every stored individual (cipher text and search
 * hashes, deleted rows too) once to a topic that only the indexer reads, so the plain documents in the individual ES
 * index are replaced (same document id). The indexer's legacy re-index must not be used: it reads through the search
 * API, which returns plain values.
 *
 * <p>Off unless {@code individual.reindex.enabled=true}. Run it as a separate one-off pod or job with the new image:
 * it stops this instance's Kafka listeners first (it must not process bulk messages), sends the pages, and then ends
 * the process (exit code 0 when every page was sent).</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "individual.reindex.enabled", havingValue = "true")
public class IndividualReindexJob implements ApplicationRunner {

    private final IndividualRepository repository;
    private final Producer producer;
    private final IndividualProperties properties;
    private final ObjectProvider<KafkaListenerEndpointRegistry> listeners;
    private final ConfigurableApplicationContext context;
    private final String topic;
    private final int batch;
    private final boolean exitWhenDone;

    public IndividualReindexJob(IndividualRepository repository,
                                @Qualifier("individualProducer") Producer producer,
                                IndividualProperties properties,
                                ObjectProvider<KafkaListenerEndpointRegistry> listeners,
                                ConfigurableApplicationContext context,
                                @Value("${individual.reindex.topic:}") String topic,
                                @Value("${individual.reindex.batch:100}") int batch,
                                @Value("${individual.reindex.exit:true}") boolean exitWhenDone) {
        this.repository = repository;
        this.producer = producer;
        this.properties = properties;
        this.listeners = listeners;
        this.context = context;
        this.topic = topic;
        this.batch = batch;
        this.exitWhenDone = exitWhenDone;
    }

    /**
     * The ES documents are written from the service's save/update messages, so a re-indexed document must look the
     * same. Three fields come out of a database read differently from those messages: an identifier's
     * individualClientReferenceId is the person's clientReferenceId in the messages (the column can hold something
     * else), no skills is null (the read gives an empty list), and no user is null (the read gives an empty user).
     */
    static void asSaveMessage(Individual individual) {
        if (individual.getIdentifiers() != null && individual.getClientReferenceId() != null) {
            individual.getIdentifiers().forEach(i -> i.setIndividualClientReferenceId(individual.getClientReferenceId()));
        }
        if (individual.getSkills() != null && individual.getSkills().isEmpty()) {
            individual.setSkills(null);
        }
        UserDetails user = individual.getUserDetails();
        if (user != null && user.getUsername() == null && user.getPassword() == null && user.getRoles() == null
                && user.getUserType() == null) {
            individual.setUserDetails(null);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode = 1;
        try {
            KafkaListenerEndpointRegistry registry = listeners.getIfAvailable();
            if (registry != null) {
                registry.stop();  // this process only re-indexes; it must not consume bulk or DLQ messages
            }
            if (topic == null || topic.isBlank()) {
                log.error("re-index: individual.reindex.topic is not set; nothing sent");
                return;
            }
            long sent = 0;
            long pages = 0;
            String last = "";
            long started = System.currentTimeMillis();
            while (true) {
                List<Individual> page = repository.findStoredPage(properties.getStateLevelTenantId(), last, batch);
                if (page.isEmpty()) {
                    break;
                }
                page.forEach(IndividualReindexJob::asSaveMessage);
                producer.push(topic, page);
                sent += page.size();
                last = page.get(page.size() - 1).getId();
                if (++pages % 100 == 0) {
                    log.info("re-index: {} individuals sent to {}", sent, topic);
                }
            }
            log.info("re-index done: {} individuals sent to {} in {} s", sent, topic, (System.currentTimeMillis() - started) / 1000);
            exitCode = 0;
        } catch (Exception e) {
            log.error("re-index stopped; run it again (pages already sent are sent again, which is harmless)", e);
        } finally {
            if (exitWhenDone) {
                int code = exitCode;
                System.exit(SpringApplication.exit(context, () -> code));
            }
        }
    }
}
