package org.egov.transformer.config;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.egov.common.models.project.Project;
import org.egov.common.models.project.ProjectStaff;
import org.egov.common.models.project.Task;
import org.egov.common.models.stock.Stock;
import org.egov.tracer.config.TracerConfiguration;
import org.egov.transformer.enums.Operation;
import org.egov.transformer.models.upstream.Service;
import org.egov.transformer.service.TransformationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

@Import({TracerConfiguration.class})
@Configuration
@ComponentScan(basePackages = {"org.egov"})
@Slf4j
public class MainConfiguration {

    @Value("${app.timezone}")
    private String timeZone;

    @Value("${spring.redis.host}")
    private String redisHost;

    @PostConstruct
    public void initialize() {
        TimeZone.setDefault(TimeZone.getTimeZone(timeZone));
    }

    @Bean
    public ObjectMapper objectMapper(){
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).setTimeZone(TimeZone.getTimeZone(timeZone));
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    @Bean
    @Qualifier("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.WRAPPER_ARRAY);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    @Bean
    @Autowired
    public MappingJackson2HttpMessageConverter jacksonConverter(ObjectMapper objectMapper) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        return converter;
    }

    @Bean
    @Autowired
    @Qualifier("taskTransformationServiceMap")
    public Map<Operation, List<TransformationService<Task>>> getOperationTransformationServiceMapForTask(
            List<TransformationService<Task>> transformationServices) {
        Map<Operation, List<TransformationService<Task>>> map =  transformationServices
                .stream()
                .collect(Collectors.groupingBy(TransformationService::getOperation));
        log.info(map.toString());
        return map;
    }

    @Bean
    @Autowired
    @Qualifier("projectStaffTransformationServiceMap")
    public Map<Operation, List<TransformationService<ProjectStaff>>> getOperationTransformationServiceMapForProjectStaff(
            List<TransformationService<ProjectStaff>> transformationServices) {
        Map<Operation, List<TransformationService<ProjectStaff>>> map =  transformationServices
                .stream()
                .collect(Collectors.groupingBy(TransformationService::getOperation));
        log.info(map.toString());
        return map;
    }

    @Bean
    @Autowired
    @Qualifier("projectTransformationServiceMap")
    public Map<Operation, List<TransformationService<Project>>> getOperationTransformationServiceMapForProject(
            List<TransformationService<Project>> transformationServices) {
        Map<Operation, List<TransformationService<Project>>> map =  transformationServices
                .stream()
                .collect(Collectors.groupingBy(TransformationService::getOperation));
        log.info(map.toString());
        return map;
    }

    @Bean
    @Autowired
    @Qualifier("stockTransformationServiceMap")
    public Map<Operation, List<TransformationService<Stock>>> getOperationTransformationServiceMapForStock(
            List<TransformationService<Stock>> transformationServices) {
        Map<Operation, List<TransformationService<Stock>>> map =  transformationServices
                .stream()
                .collect(Collectors.groupingBy(TransformationService::getOperation));
        log.info(map.toString());
        return map;
    }
    @Bean
    @Autowired
    @Qualifier("serviceTaskTransformationServiceMap")
    public Map<Operation, List<TransformationService<Service>>> getOperationTransformationServiceMapForServiceTask(
            List<TransformationService<Service>> transformationServices) {
        Map<Operation, List<TransformationService<Service>>> map =  transformationServices
                .stream()
                .collect(Collectors.groupingBy(TransformationService::getOperation));
        log.info(map.toString());
        return map;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setHostName(redisHost);
        return new JedisConnectionFactory(redisStandaloneConfiguration);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(@Qualifier("redisObjectMapper") ObjectMapper redisObjectMapper,
                                                       RedisConnectionFactory redisConnectionFactory) {
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(redisObjectMapper, Object.class);
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(serializer);
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(serializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

}