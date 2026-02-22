package digit.service.llm;

public interface LlmClient {

    String generate(String systemPrompt, String userMessage);
}
