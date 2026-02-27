package digit.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static digit.config.ServiceConstants.*;

@Slf4j
public class OpenAiLlmClient implements LlmClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final String apiKey;
    private final int maxRetries;

    public OpenAiLlmClient(RestTemplate restTemplate, String baseUrl, String model,
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
        log.info("Sending query to OpenAI model: {}", model);

        OpenAiRequest request = OpenAiRequest.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .messages(List.of(
                        OpenAiRequest.Message.builder().role(OPENAI_ROLE_SYSTEM).content(systemPrompt).build(),
                        OpenAiRequest.Message.builder().role(OPENAI_ROLE_USER).content(userMessage).build()
                ))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(OPENAI_AUTH_HEADER, OPENAI_BEARER_PREFIX + apiKey);

        HttpEntity<OpenAiRequest> entity = new HttpEntity<>(request, headers);
        String url = baseUrl + OPENAI_CHAT_COMPLETIONS_PATH;

        Exception lastException = null;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long start = System.currentTimeMillis();
            try {
                OpenAiResponse response = restTemplate.postForObject(url, entity, OpenAiResponse.class);
                long latencyMs = System.currentTimeMillis() - start;
                log.info("OpenAI response received in {}ms (attempt {})", latencyMs, attempt);

                if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                    throw new IllegalStateException("OpenAI returned an empty response");
                }

                String result = response.getChoices().get(0).getMessage().getContent().trim();
                log.debug("LLM raw response: {}", result);
                return result;

            } catch (Exception e) {
                long latencyMs = System.currentTimeMillis() - start;
                lastException = e;
                log.warn("OpenAI attempt {}/{} failed after {}ms: {}", attempt, maxRetries, latencyMs, e.getMessage());

                if (attempt < maxRetries) {
                    long backoffMs = 1000L * (1L << (attempt - 1));
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Interrupted during OpenAI retry backoff", ie);
                    }
                }
            }
        }
        throw new IllegalStateException("OpenAI failed after " + maxRetries + " attempts", lastException);
    }
}
