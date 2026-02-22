package digit.config;

import java.util.Set;
import java.util.regex.Pattern;

public class ServiceConstants {

    private ServiceConstants() {}

    // Response constants
    public static final String RES_MSG_ID = "uief87324";
    public static final String SUCCESSFUL = "successful";
    public static final String FAILED = "failed";

    // PII patterns
    public static final Pattern AADHAAR_PATTERN = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    public static final Pattern INDIAN_PHONE_PATTERN = Pattern.compile("\\b(?:\\+91[\\s-]?)?[6-9]\\d{9}\\b");
    public static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    public static final Pattern PAN_PATTERN = Pattern.compile("\\b[A-Z]{5}\\d{4}[A-Z]\\b");

    public static final String PII_REPLACEMENT_AADHAAR = "[AADHAAR_REDACTED]";
    public static final String PII_REPLACEMENT_PHONE = "[PHONE_REDACTED]";
    public static final String PII_REPLACEMENT_EMAIL = "[EMAIL_REDACTED]";
    public static final String PII_REPLACEMENT_PAN = "[PAN_REDACTED]";

    // Query type allowlist — only these ES query types are allowed
    public static final Set<String> ALLOWED_QUERY_TYPES = Set.of(
            "match", "match_phrase", "multi_match", "match_all",
            "term", "terms", "range", "bool",
            "must", "must_not", "should", "filter",
            "exists", "nested", "prefix"
    );

    // Blocked query types — explicitly dangerous
    public static final Set<String> BLOCKED_QUERY_TYPES = Set.of(
            "script", "script_score", "regexp", "wildcard", "fuzzy",
            "more_like_this", "percolate", "wrapper",
            "_update", "_delete", "_bulk", "_index"
    );

    // Allowed top-level DSL keys
    public static final Set<String> ALLOWED_TOP_LEVEL_KEYS = Set.of(
            "query", "size", "from", "sort", "timeout",
            "aggs", "aggregations", "_source", "highlight",
            "track_total_hits"
    );
}
