package digit.service.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static digit.config.ServiceConstants.OLLAMA_GENERATE_PATH;
import static digit.config.ServiceConstants.OLLAMA_OPTION_TEMPERATURE;

@Slf4j
public class OllamaLlmClient implements LlmClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String model;
    private final double temperature;

    public OllamaLlmClient(RestTemplate restTemplate, String baseUrl, String model, double temperature) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.model = model;
        this.temperature = temperature;
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        log.info("Sending query to Ollama model: {}", model);

        OllamaRequest request = OllamaRequest.builder()
                .model(model)
                .prompt(userMessage)
                .system(systemPrompt)
                .stream(false)
                .options(Map.of(OLLAMA_OPTION_TEMPERATURE, temperature))
                .build();

        String url = baseUrl + OLLAMA_GENERATE_PATH;
        OllamaResponse response = restTemplate.postForObject(url, request, OllamaResponse.class);

        if (response == null || response.getResponse() == null) {
            throw new IllegalStateException("Ollama returned a null response");
        }

        String result = response.getResponse().trim();
        log.debug("LLM raw response: {}", result);
        return result;
    }
}
