package digit.config;

import digit.service.llm.LlmClient;
import digit.service.llm.OllamaLlmClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CdlConfiguration {

    // LLM (Ollama)
    @Value("${cdl.llm.provider}")
    private String llmProvider;

    @Value("${cdl.llm.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${cdl.llm.ollama.model}")
    private String ollamaModel;

    @Value("${cdl.llm.ollama.temperature}")
    private double ollamaTemperature;

    @Value("${cdl.llm.ollama.timeout-ms}")
    private long ollamaTimeoutMs;

    // Elasticsearch
    @Value("${cdl.elasticsearch.host}")
    private String elasticsearchHost;

    @Value("${cdl.elasticsearch.port}")
    private int elasticsearchPort;

    @Value("${cdl.elasticsearch.scheme}")
    private String elasticsearchScheme;

    // Query limits
    @Value("${cdl.query.max.size}")
    private int queryMaxSize;

    @Value("${cdl.query.default.size}")
    private int queryDefaultSize;

    @Value("${cdl.query.timeout}")
    private String queryTimeout;

    @Value("${cdl.query.max.agg.depth}")
    private int queryMaxAggDepth;

    // Default index
    @Value("${cdl.index.name}")
    private String defaultIndexName;

    @Bean
    public RestTemplate llmRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) ollamaTimeoutMs);
        factory.setReadTimeout((int) ollamaTimeoutMs);
        return new RestTemplate(factory);
    }

    @Bean
    public LlmClient llmClient(RestTemplate llmRestTemplate) {
        return new OllamaLlmClient(llmRestTemplate, ollamaBaseUrl, ollamaModel, ollamaTemperature);
    }

    @Bean
    public RestClient elasticsearchRestClient() {
        return RestClient.builder(
                new HttpHost(elasticsearchHost, elasticsearchPort, elasticsearchScheme)
        ).build();
    }
}
