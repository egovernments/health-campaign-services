package digit.config;

import digit.service.llm.AnthropicLlmClient;
import digit.service.llm.FallbackLlmClient;
import digit.service.llm.GroqLlmClient;
import digit.service.llm.LlmClient;
import digit.service.llm.OllamaLlmClient;
import digit.service.llm.OpenAiLlmClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.message.BasicHeader;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import static digit.config.ServiceConstants.*;

@Configuration
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Slf4j
public class CdlConfiguration {

    // LLM provider selection
    @Value("${cdl.llm.provider}")
    private String llmProvider;

    // Fallback
    @Value("${cdl.llm.fallback-enabled}")
    private boolean fallbackEnabled;

    @Value("${cdl.llm.primary}")
    private String primaryProvider;

    @Value("${cdl.llm.secondary}")
    private String secondaryProvider;

    // Prompt guard
    @Value("${cdl.llm.max-prompt-length}")
    private int maxPromptLength;

    // Ollama
    @Value("${cdl.llm.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${cdl.llm.ollama.model}")
    private String ollamaModel;

    @Value("${cdl.llm.ollama.temperature}")
    private double ollamaTemperature;

    @Value("${cdl.llm.ollama.timeout-ms}")
    private long ollamaTimeoutMs;

    // OpenAI
    @Value("${cdl.llm.openai.api-key}")
    private String openaiApiKey;

    @Value("${cdl.llm.openai.base-url}")
    private String openaiBaseUrl;

    @Value("${cdl.llm.openai.model}")
    private String openaiModel;

    @Value("${cdl.llm.openai.temperature}")
    private double openaiTemperature;

    @Value("${cdl.llm.openai.timeout-ms}")
    private long openaiTimeoutMs;

    @Value("${cdl.llm.openai.max-tokens}")
    private int openaiMaxTokens;

    @Value("${cdl.llm.openai.max-retries}")
    private int openaiMaxRetries;

    // Groq
    @Value("${cdl.llm.groq.api-key}")
    private String groqApiKey;

    @Value("${cdl.llm.groq.base-url}")
    private String groqBaseUrl;

    @Value("${cdl.llm.groq.model}")
    private String groqModel;

    @Value("${cdl.llm.groq.temperature}")
    private double groqTemperature;

    @Value("${cdl.llm.groq.timeout-ms}")
    private long groqTimeoutMs;

    @Value("${cdl.llm.groq.max-tokens}")
    private int groqMaxTokens;

    // Anthropic
    @Value("${cdl.llm.anthropic.api-key}")
    private String anthropicApiKey;

    @Value("${cdl.llm.anthropic.base-url}")
    private String anthropicBaseUrl;

    @Value("${cdl.llm.anthropic.model}")
    private String anthropicModel;

    @Value("${cdl.llm.anthropic.temperature}")
    private double anthropicTemperature;

    @Value("${cdl.llm.anthropic.timeout-ms}")
    private long anthropicTimeoutMs;

    @Value("${cdl.llm.anthropic.max-tokens}")
    private int anthropicMaxTokens;

    @Value("${cdl.llm.anthropic.max-retries}")
    private int anthropicMaxRetries;

    // Elasticsearch
    @Value("${cdl.elasticsearch.host}")
    private String elasticsearchHost;

    @Value("${cdl.elasticsearch.port}")
    private int elasticsearchPort;

    @Value("${cdl.elasticsearch.scheme}")
    private String elasticsearchScheme;

    // Elasticsearch auth
    @Value("${cdl.elasticsearch.auth.type}")
    private String elasticsearchAuthType;

    @Value("${cdl.elasticsearch.auth.username}")
    private String elasticsearchUsername;

    @Value("${cdl.elasticsearch.auth.password}")
    private String elasticsearchPassword;

    @Value("${cdl.elasticsearch.auth.api-key}")
    private String elasticsearchApiKey;

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
    public LlmClient llmClient() {
        if (fallbackEnabled) {
            log.info("LLM fallback enabled: primary={}, secondary={}", primaryProvider, secondaryProvider);
            LlmClient primary = createClientForProvider(primaryProvider);
            LlmClient secondary = createClientForProvider(secondaryProvider);
            return new FallbackLlmClient(primary, secondary, primaryProvider, secondaryProvider);
        }
        log.info("LLM provider: {}", llmProvider);
        return createClientForProvider(llmProvider);
    }

    @Bean
    public RestClient elasticsearchRestClient() {
        RestClientBuilder builder = RestClient.builder(
                new HttpHost(elasticsearchHost, elasticsearchPort, elasticsearchScheme)
        );

        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, (certificate, authType) -> true)
                    .build();
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder
                            .setSSLContext(sslContext)
                            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        switch (elasticsearchAuthType) {
            case ES_AUTH_BASIC:
                log.info("Elasticsearch auth: basic (user={})", elasticsearchUsername);
                BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                credentialsProvider.setCredentials(AuthScope.ANY,
                        new UsernamePasswordCredentials(elasticsearchUsername, elasticsearchPassword));
                builder.setHttpClientConfigCallback(
                        httpClientBuilder -> httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
                );
                break;

            case ES_AUTH_API_KEY:
                log.info("Elasticsearch auth: api-key");
                builder.setDefaultHeaders(new Header[]{
                        new BasicHeader(ES_AUTH_HEADER, ES_API_KEY_PREFIX + elasticsearchApiKey)
                });
                break;

            case ES_AUTH_NONE:
            default:
                log.info("Elasticsearch auth: none");
                break;
        }

        return builder.build();
    }

    private LlmClient createClientForProvider(String provider) {
        switch (provider) {
            case LLM_PROVIDER_OLLAMA:
                return new OllamaLlmClient(
                        createRestTemplate(ollamaTimeoutMs),
                        ollamaBaseUrl, ollamaModel, ollamaTemperature
                );

            case LLM_PROVIDER_OPENAI:
                if (openaiApiKey == null || openaiApiKey.isBlank()) {
                    throw new IllegalStateException(
                            "OpenAI API key is required when provider is set to openai. "
                                    + "Set cdl.llm.openai.api-key or OPENAI_API_KEY env var."
                    );
                }
                return new OpenAiLlmClient(
                        createRestTemplate(openaiTimeoutMs),
                        openaiBaseUrl, openaiModel, openaiTemperature,
                        openaiMaxTokens, openaiApiKey, openaiMaxRetries
                );

            case LLM_PROVIDER_GROQ:
                if (groqApiKey == null || groqApiKey.isBlank()) {
                    throw new IllegalStateException(
                            "Groq API key is required when provider is set to groq. "
                                    + "Set cdl.llm.groq.api-key or GROQ_API_KEY env var."
                    );
                }
                return new GroqLlmClient(
                        createRestTemplate(groqTimeoutMs),
                        groqBaseUrl, groqModel, groqTemperature,
                        groqMaxTokens, groqApiKey
                );

            case LLM_PROVIDER_ANTHROPIC:
                if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
                    throw new IllegalStateException(
                            "Anthropic API key is required when provider is set to anthropic. "
                                    + "Set cdl.llm.anthropic.api-key or ANTHROPIC_API_KEY env var."
                    );
                }
                return new AnthropicLlmClient(
                        createRestTemplate(anthropicTimeoutMs),
                        anthropicBaseUrl, anthropicModel, anthropicTemperature,
                        anthropicMaxTokens, anthropicApiKey, anthropicMaxRetries
                );

            default:
                throw new IllegalStateException("Unknown LLM provider: " + provider);
        }
    }

    private RestTemplate createRestTemplate(long timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeoutMs);
        factory.setReadTimeout((int) timeoutMs);
        return new RestTemplate(factory);
    }
}
