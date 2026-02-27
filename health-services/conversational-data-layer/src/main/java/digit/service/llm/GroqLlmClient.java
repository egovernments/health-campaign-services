package digit.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static digit.config.ServiceConstants.*;

@Slf4j
public class GroqLlmClient implements LlmClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final String apiKey;

    public GroqLlmClient(RestTemplate restTemplate, String baseUrl, String model,
                          double temperature, int maxTokens, String apiKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.apiKey = apiKey;
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        log.info("Sending query to Groq model: {}", model);

        OpenAiRequest request = OpenAiRequest.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .messages(List.of(
                        OpenAiRequest.Message.builder().role(GROQ_ROLE_SYSTEM).content(systemPrompt).build(),
                        OpenAiRequest.Message.builder().role(GROQ_ROLE_USER).content(userMessage).build()
                ))
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(GROQ_AUTH_HEADER, GROQ_BEARER_PREFIX + apiKey);

        HttpEntity<OpenAiRequest> entity = new HttpEntity<>(request, headers);
        String url = baseUrl + GROQ_CHAT_COMPLETIONS_PATH;

        long start = System.currentTimeMillis();
        OpenAiResponse response = restTemplate.postForObject(url, entity, OpenAiResponse.class);
        long latencyMs = System.currentTimeMillis() - start;
        log.info("Groq response received in {}ms", latencyMs);

        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            throw new IllegalStateException("Groq returned an empty response");
        }

        String result = response.getChoices().get(0).getMessage().getContent().trim();
        log.debug("LLM raw response: {}", result);
        return result;
    }
}
