package digit.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import digit.config.CdlConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LlmTranslationService {

    private final AnthropicClient anthropicClient;
    private final CdlConfiguration config;

    @Autowired
    public LlmTranslationService(AnthropicClient anthropicClient, CdlConfiguration config) {
        this.anthropicClient = anthropicClient;
        this.config = config;
    }

    /**
     * Sends the system prompt and user query to Claude and returns the raw response text.
     */
    public String translate(String systemPrompt, String userMessage) {
        log.info("Sending query to LLM model: {}", config.getAnthropicModel());

        MessageCreateParams params = MessageCreateParams.builder()
                .model(config.getAnthropicModel())
                .maxTokens(config.getAnthropicMaxTokens())
                .temperature(config.getAnthropicTemperature())
                .system(systemPrompt)
                .addUserMessage(userMessage)
                .build();

        Message message = anthropicClient.messages().create(params);

        StringBuilder responseText = new StringBuilder();
        for (ContentBlock block : message.content()) {
            if (block.isText()) {
                responseText.append(block.asText().text());
            }
        }

        String rawResponse = responseText.toString().trim();
        log.debug("LLM raw response: {}", rawResponse);
        return rawResponse;
    }

    /**
     * Extracts JSON from LLM response, stripping markdown code fences if present.
     */
    public String extractJson(String llmResponse) {
        if (llmResponse == null || llmResponse.isBlank()) {
            return llmResponse;
        }

        String trimmed = llmResponse.trim();

        // Strip markdown code fences: ```json ... ``` or ``` ... ```
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
            trimmed = trimmed.trim();
        }

        return trimmed;
    }
}
