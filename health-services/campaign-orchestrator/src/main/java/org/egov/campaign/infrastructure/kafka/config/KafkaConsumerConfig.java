package org.egov.campaign.infrastructure.kafka.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

import java.util.concurrent.Executors;

@Configuration
public class KafkaConsumerConfig {

    @Value("${cms.kafka.consumer.concurrency.user.provisioner:30}")
    private int userProvisionerConcurrency;

    @Value("${cms.kafka.consumer.concurrency.project.provisioner:20}")
    private int projectProvisionerConcurrency;

    @Value("${cms.kafka.consumer.concurrency.facility.provisioner:20}")
    private int facilityProvisionerConcurrency;

    @Value("${cms.kafka.consumer.concurrency.mapping.reconciler:15}")
    private int mappingReconcilerConcurrency;

    @Value("${cms.kafka.consumer.concurrency.saga.coordinator:5}")
    private int sagaCoordinatorConcurrency;

    @Value("${cms.kafka.consumer.concurrency.excel.processor:10}")
    private int excelProcessorConcurrency;

    @Value("${cms.kafka.consumer.concurrency.excel.generator:5}")
    private int excelGeneratorConcurrency;

    @Bean("userProvisionerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> userProvisionerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        return buildFactory(consumerFactory, userProvisionerConcurrency);
    }

    @Bean("projectProvisionerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> projectProvisionerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        return buildFactory(consumerFactory, projectProvisionerConcurrency);
    }

    @Bean("facilityProvisionerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> facilityProvisionerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        return buildFactory(consumerFactory, facilityProvisionerConcurrency);
    }

    @Bean("mappingReconcilerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> mappingReconcilerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        return buildFactory(consumerFactory, mappingReconcilerConcurrency);
    }

    @Bean("sagaCoordinatorContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> sagaCoordinatorContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        return buildFactory(consumerFactory, sagaCoordinatorConcurrency);
    }

    @Bean("excelProcessorContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> excelProcessorContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        return buildFactory(consumerFactory, excelProcessorConcurrency);
    }

    @Bean("excelGeneratorContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> excelGeneratorContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        return buildFactory(consumerFactory, excelGeneratorConcurrency);
    }

    private ConcurrentKafkaListenerContainerFactory<String, Object> buildFactory(
            ConsumerFactory<String, Object> consumerFactory, int concurrency) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(concurrency);
        factory.getContainerProperties()
               .setListenerTaskExecutor(new TaskExecutorAdapter(Executors.newVirtualThreadPerTaskExecutor()));
        return factory;
    }
}
