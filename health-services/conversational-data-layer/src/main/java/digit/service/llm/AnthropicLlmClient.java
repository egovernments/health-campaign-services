package digit.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static digit.config.ServiceConstants.*;

@Slf4j
public class AnthropicLlmClient implements LlmClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final String apiKey;
    private final int maxRetries;

    public AnthropicLlmClient(RestTemplate restTemplate, String baseUrl, String model,
                               double temperature, int maxTokens, String apiKey, int maxRetries) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.apiKey = apiKey;
        this.maxRetries = maxRetries;
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        log.info("Sending query to Anthropic model: {}", model);

        AnthropicRequest request = AnthropicRequest.builder()
                .model(model)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .system(systemPrompt)
                .messages(List.of(
                        AnthropicRequest.Message.builder()
                                .role(ANTHROPIC_ROLE_USER)
                                .content(userMessage)
                                .build()
                ))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(ANTHROPIC_API_KEY_HEADER, apiKey);
        headers.set(ANTHROPIC_VERSION_HEADER, ANTHROPIC_VERSION_VALUE);

        HttpEntity<AnthropicRequest> entity = new HttpEntity<>(request, headers);
        String url = baseUrl + ANTHROPIC_MESSAGES_PATH;

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long start = System.currentTimeMillis();
            try {
                AnthropicResponse response = restTemplate.postForObject(url, entity, AnthropicResponse.class);
                long latencyMs = System.currentTimeMillis() - start;
                log.info("Anthropic response received in {}ms (attempt {})", latencyMs, attempt);

                if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
                    throw new IllegalStateException("Anthropic returned an empty response");
                }

                String result = response.getContent().get(0).getText().trim();
                log.debug("LLM raw response: {}", result);
                return result;

            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - start;
                lastException = e;
                log.warn("Anthropic attempt {}/{} failed after {}ms: {}", attempt, maxRetries, latencyMs, e.getMessage());

                if (attempt < maxRetries) {
                    long backoffMs = 1000L * (1L << (attempt - 1));
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted during Anthropic retry backoff", ie);
                    }
                }
            }
        }
        throw new IllegalStateException("Anthropic failed after " + maxRetries + " attempts", lastException);
    }
}
