package digit.service;

import digit.service.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LlmTranslationService {

    private final LlmClient llmClient;

    public LlmTranslationService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * Sends the system prompt and user query to the LLM and returns the raw response text.
     */
    public String translate(String systemPrompt, String userMessage) {
        return llmClient.generate(systemPrompt, userMessage);
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
