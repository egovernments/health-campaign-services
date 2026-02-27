package digit.config;

public class ErrorConstants {

    private ErrorConstants() {}

    public static final String EMPTY_QUERY_CODE = "CDL_EMPTY_QUERY";
    public static final String EMPTY_QUERY_MSG = "Query text cannot be empty";

    public static final String PII_IN_RESPONSE_CODE = "CDL_PII_IN_RESPONSE";
    public static final String PII_IN_RESPONSE_MSG = "LLM response contains PII-like patterns. Query rejected for safety.";

    public static final String INVALID_QUERY_CODE = "CDL_INVALID_QUERY";
    public static final String INVALID_QUERY_MSG = "Generated query failed validation: ";

    public static final String LLM_ERROR_CODE = "CDL_LLM_ERROR";
    public static final String LLM_ERROR_MSG = "Failed to get response from LLM: ";

    public static final String ES_ERROR_CODE = "CDL_ES_ERROR";
    public static final String ES_ERROR_MSG = "Elasticsearch query execution failed: ";

    public static final String UNKNOWN_INDEX_CODE = "CDL_UNKNOWN_INDEX";
    public static final String UNKNOWN_INDEX_MSG = "Unknown index name: ";

    public static final String PARSE_ERROR_CODE = "CDL_PARSE_ERROR";
    public static final String PARSE_ERROR_MSG = "Failed to parse LLM response as valid JSON";

    public static final String OPENAI_KEY_MISSING_CODE = "CDL_OPENAI_KEY_MISSING";
    public static final String OPENAI_KEY_MISSING_MSG = "OpenAI API key is required when provider is set to openai";

    public static final String GROQ_KEY_MISSING_CODE = "CDL_GROQ_KEY_MISSING";
    public static final String GROQ_KEY_MISSING_MSG = "Groq API key is required when provider is set to groq";

    public static final String ANTHROPIC_KEY_MISSING_CODE = "CDL_ANTHROPIC_KEY_MISSING";
    public static final String ANTHROPIC_KEY_MISSING_MSG = "Anthropic API key is required when provider is set to anthropic";

    public static final String PROMPT_TOO_LONG_CODE = "CDL_PROMPT_TOO_LONG";
    public static final String PROMPT_TOO_LONG_MSG = "Combined prompt length exceeds maximum allowed length";

    public static final String LLM_PROVIDER_UNKNOWN_CODE = "CDL_UNKNOWN_PROVIDER";
    public static final String LLM_PROVIDER_UNKNOWN_MSG = "Unknown LLM provider: ";
}
