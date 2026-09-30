package org.egov.referralmanagement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Forces every {@link RestTemplate} bean to serialize request bodies as JSON.
 *
 * <p>{@code com.azure:azure-storage-blob} (pulled in for the downsync file-upload feature) drags
 * {@code jackson-dataformat-xml} onto the classpath. Spring Boot then auto-registers
 * {@code MappingJackson2XmlHttpMessageConverter} on every RestTemplate, and because it appears
 * before the JSON converter it wins {@code canWrite(...)} for POST bodies — so the internal
 * service-to-service calls made by {@code ServiceRequestClient} (e.g. the project-beneficiary
 * search during referral validation) go out with {@code Content-Type: application/xml} and are
 * rejected with "unsupported media type", breaking referral create.</p>
 *
 * <p>We cannot exclude the jar (the Azure blob SDK needs it, but uses its own serializer — not the
 * Spring RestTemplate converters). So instead we post-process every RestTemplate bean: drop the XML
 * converter(s) and make a JSON converter the first entry.</p>
 */
@Component
public class RestTemplateJsonConverterConfig implements BeanPostProcessor {

    private final ObjectMapper objectMapper;

    public RestTemplateJsonConverterConfig(@Qualifier("objectMapper") ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof RestTemplate restTemplate) {
            List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
            // Remove any XML-based converter so it can no longer win POST-body negotiation.
            converters.removeIf(c -> c.getClass().getName().toLowerCase().contains("xml"));
            // Ensure a JSON converter exists and is first.
            MappingJackson2HttpMessageConverter json = converters.stream()
                    .filter(c -> c instanceof MappingJackson2HttpMessageConverter)
                    .map(c -> (MappingJackson2HttpMessageConverter) c)
                    .findFirst()
                    .orElse(null);
            if (json == null) {
                converters.add(0, new MappingJackson2HttpMessageConverter(objectMapper));
            } else if (converters.indexOf(json) != 0) {
                converters.remove(json);
                converters.add(0, json);
            }
        }
        return bean;
    }
}
