You are a senior backend architect working on the DIGIT HCM platform.

**Goal:** Refactor the existing AI integration so the system supports BOTH Groq-hosted models and Anthropic models, selectable via configuration — with no dependency on Ollama or OpenAI.

The current system:

* Uses a single provider (legacy)
* Written in Java + Spring Boot
* Converts natural language → Elasticsearch DSL
* Needs fast free-tier option (Groq)
* Needs high-accuracy option (Anthropic)
* Must be production-grade and extensible

---

## 🎯 Target Architecture

Implement a provider-agnostic design:

Controller
→ AiQueryService
→ LlmClient (interface)
→ GroqLlmClient OR AnthropicLlmClient (selected via config)

**IMPORTANT:** No vendor lock-in. Easy to add more providers later.

---

## ✅ Functional Requirements

### 1. Introduce abstraction

Create interface:

```java
public interface LlmClient {
    String generate(String prompt);
}
```

Provide implementations:

* GroqLlmClient (PRIMARY / fast free-tier)
* AnthropicLlmClient (SECONDARY / high accuracy)

---

### 2. Configuration-driven provider switching

Add configuration:

```yaml
llm:
  provider: groq   # groq | anthropic
  temperature: 0
  max-tokens: 1024

  groq:
    api-key: ${GROQ_API_KEY:}
    model: llama3-70b-8192
    base-url: https://api.groq.com
    timeout-ms: 20000

  anthropic:
    api-key: ${ANTHROPIC_API_KEY:}
    model: claude-3-haiku-20240307
    base-url: https://api.anthropic.com
    timeout-ms: 30000
```

Behavior:

* Default provider = groq
* If provider=anthropic → automatically switch
* Fail fast if selected provider key missing
* No code changes required to switch

---

### 3. Groq client requirements (PRIMARY)

Endpoint (OpenAI-compatible):

POST `{base-url}/openai/v1/chat/completions`

Request body:

```json
{
  "model": "<model>",
  "messages": [
    {"role": "user", "content": "<prompt>"}
  ],
  "temperature": 0,
  "max_tokens": 1024
}
```

Requirements:

* Bearer auth header using GROQ_API_KEY
* Use Spring WebClient with connection pooling
* Proper timeout handling
* Structured latency logging
* Robust JSON parsing
* Thread-safe

---

### 4. Anthropic client requirements (SECONDARY)

Endpoint:

POST `{base-url}/v1/messages`

Request body format:

```json
{
  "model": "<model>",
  "max_tokens": 1024,
  "temperature": 0,
  "messages": [
    {"role": "user", "content": "<prompt>"}
  ]
}
```

Headers required:

* x-api-key
* anthropic-version: 2023-06-01
* content-type: application/json

Requirements:

* Retry with basic exponential backoff
* Timeout handling
* Clean DTOs
* Safe extraction of assistant text
* Production-grade error handling

---

### 5. Optional smart fallback (RECOMMENDED)

Add config:

```yaml
llm:
  fallback-enabled: true
  primary: groq
  secondary: anthropic
```

Behavior:

* Try primary first
* On timeout/rate-limit/error → fallback
* Log clearly when fallback happens
* Preserve response contract

---

### 6. Performance & safety guards (IMPORTANT)

Implement:

* Max prompt length guard
* Provider-specific timeout
* Structured logging (provider + latency)
* WebClient connection pooling
* Thread safety
* Kubernetes-friendly config
* Deterministic output (temperature=0)
* Graceful handling of Groq rate limits

---

### 7. Maintain backward compatibility

DO NOT change:

* Prompt construction logic
* Elasticsearch DSL output format
* AiQueryService public contract
* Downstream consumers

---

## ✅ Deliverables

Produce production-ready Java code including:

1. LlmClient interface
2. GroqLlmClient implementation
3. AnthropicLlmClient implementation
4. Spring configuration / provider factory
5. Updated AiQueryService
6. application.yml
7. Example logs
8. Example curl tests for both providers
9. Proper DTO classes
10. Clean, thread-safe, enterprise-grade code

---

## ⚠️ Important Constraints

* Optimize for deterministic ES query generation
* Assume high concurrency (DIGIT scale)
* Avoid blocking calls
* Prefer WebClient over RestTemplate
* No hardcoded providers
* Easy to extend for future LLMs
* Handle large prompts safely
* Minimize token usage
* Handle 429 (rate limit) from Groq gracefully

Produce clean, production-grade Java code suitable for long-term enterprise maintenance.
