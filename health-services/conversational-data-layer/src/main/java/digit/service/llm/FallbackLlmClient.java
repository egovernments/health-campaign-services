package digit.service.llm;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FallbackLlmClient implements LlmClient {

    private final LlmClient primary;
    private final LlmClient secondary;
    private final String primaryName;
    private final String secondaryName;

    public FallbackLlmClient(LlmClient primary, LlmClient secondary,
                              String primaryName, String secondaryName) {
        this.primary = primary;
        this.secondary = secondary;
        this.primaryName = primaryName;
        this.secondaryName = secondaryName;
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        long start = System.currentTimeMillis();
        try {
            String result = primary.generate(systemPrompt, userMessage);
            log.info("Primary LLM [{}] succeeded in {}ms", primaryName, System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            long primaryLatencyMs = System.currentTimeMillis() - start;
            log.warn("Primary LLM [{}] failed after {}ms: {}. Falling back to [{}]",
                    primaryName, primaryLatencyMs, e.getMessage(), secondaryName);

            long fallbackStart = System.currentTimeMillis();
            String result = secondary.generate(systemPrompt, userMessage);
            log.info("Fallback LLM [{}] succeeded in {}ms", secondaryName, System.currentTimeMillis() - fallbackStart);
            return result;
        }
    }
}
