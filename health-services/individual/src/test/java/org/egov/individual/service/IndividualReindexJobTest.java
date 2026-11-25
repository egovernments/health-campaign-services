package org.egov.individual.service;

import org.egov.common.models.individual.Individual;
import org.egov.common.producer.Producer;
import org.egov.individual.config.IndividualProperties;
import org.egov.individual.repository.IndividualRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndividualReindexJobTest {

    private static Individual ind(String id) {
        return Individual.builder().id(id).build();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<KafkaListenerEndpointRegistry> provider(KafkaListenerEndpointRegistry registry) {
        ObjectProvider<KafkaListenerEndpointRegistry> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(registry);
        return p;
    }

    @Test
    @DisplayName("sends every page of stored rows to the re-index topic, in id order, after stopping its Kafka listeners")
    void sendsAllPages() throws Exception {
        IndividualRepository repo = mock(IndividualRepository.class);
        Producer producer = mock(Producer.class);
        KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
        IndividualProperties props = new IndividualProperties();
        props.setStateLevelTenantId("dev");
        List<Individual> page1 = List.of(ind("a"), ind("b"));
        List<Individual> page2 = List.of(ind("c"));
        when(repo.findStoredPage("dev", "", 2)).thenReturn(page1);
        when(repo.findStoredPage("dev", "b", 2)).thenReturn(page2);
        when(repo.findStoredPage("dev", "c", 2)).thenReturn(Collections.emptyList());

        new IndividualReindexJob(repo, producer, props, provider(registry), mock(ConfigurableApplicationContext.class),
                "individual-reindex-topic", 2, false).run(null);

        var order = inOrder(registry, producer);
        order.verify(registry).stop();
        order.verify(producer).push("individual-reindex-topic", page1);
        order.verify(producer).push("individual-reindex-topic", page2);
    }

    @Test
    @DisplayName("a re-indexed record looks like the service's save message: person's reference on identifiers, no empty skills or user")
    void looksLikeSaveMessage() {
        Individual i = Individual.builder().id("a").clientReferenceId("person-ref")
                .identifiers(new java.util.ArrayList<>(List.of(org.egov.common.models.individual.Identifier.builder()
                        .clientReferenceId("identifier-ref").individualClientReferenceId("identifier-ref").build())))
                .skills(new java.util.ArrayList<>())
                .userDetails(org.egov.common.models.individual.UserDetails.builder().tenantId("dev").build()).build();

        IndividualReindexJob.asSaveMessage(i);

        org.junit.jupiter.api.Assertions.assertEquals("person-ref", i.getIdentifiers().get(0).getIndividualClientReferenceId());
        org.junit.jupiter.api.Assertions.assertNull(i.getSkills());
        org.junit.jupiter.api.Assertions.assertNull(i.getUserDetails());
    }

    @Test
    @DisplayName("without a topic nothing is read or sent")
    void noTopicSendsNothing() throws Exception {
        IndividualRepository repo = mock(IndividualRepository.class);
        Producer producer = mock(Producer.class);
        new IndividualReindexJob(repo, producer, new IndividualProperties(), provider(null),
                mock(ConfigurableApplicationContext.class), "", 100, false).run(null);
        verify(repo, never()).findStoredPage(anyString(), anyString(), anyInt());
        verify(producer, never()).push(anyString(), eq(Collections.emptyList()));
    }
}
