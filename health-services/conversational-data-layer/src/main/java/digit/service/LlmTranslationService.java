package digit.service;

import digit.config.CdlConfiguration;
import digit.service.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.egov.tracer.model.CustomException;
import org.springframework.stereotype.Service;

import static digit.config.ErrorConstants.PROMPT_TOO_LONG_CODE;
import static digit.config.ErrorConstants.PROMPT_TOO_LONG_MSG;

@Service
@Slf4j
public class LlmTranslationService {

    private final LlmClient llmClient;
    private final CdlConfiguration config;

    public LlmTranslationService(LlmClient llmClient, CdlConfiguration config) {
        this.llmClient = llmClient;
        this.config = config;
    }

    /**
     * Sends the system prompt and user query to the LLM and returns the raw response text.
     */
    public String translate(String systemPrompt, String userMessage) {
        int promptLength = systemPrompt.length() + userMessage.length();
        if (promptLength > config.getMaxPromptLength()) {
            throw new CustomException(PROMPT_TOO_LONG_CODE,
                    PROMPT_TOO_LONG_MSG + " (" + promptLength + " > " + config.getMaxPromptLength() + ")");
        }

        long start = System.currentTimeMillis();
        String result = llmClient.generate(systemPrompt, userMessage);
        log.info("LLM translation completed in {}ms", System.currentTimeMillis() - start);
        return result;
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
