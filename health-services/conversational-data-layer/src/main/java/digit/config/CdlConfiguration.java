package digit.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CdlConfiguration {

    // Anthropic
    @Value("${cdl.anthropic.api.key}")
    private String anthropicApiKey;

    @Value("${cdl.anthropic.model}")
    private String anthropicModel;

    @Value("${cdl.anthropic.max.tokens}")
    private long anthropicMaxTokens;

    @Value("${cdl.anthropic.temperature}")
    private double anthropicTemperature;

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
    public AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder()
                .apiKey(anthropicApiKey)
                .build();
    }

    @Bean
    public RestClient elasticsearchRestClient() {
        return RestClient.builder(
                new HttpHost(elasticsearchHost, elasticsearchPort, elasticsearchScheme)
        ).build();
    }
}
