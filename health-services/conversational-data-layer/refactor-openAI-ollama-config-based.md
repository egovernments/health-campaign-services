You are a senior backend architect working on the DIGIT HCM platform.

**Goal:** Refactor the existing AI integration so the system supports BOTH OpenAI GPT models and a local Ollama model, selectable via configuration — without any code changes.

The current system:

* Uses Ollama locally (default today)
* Written in Java + Spring Boot
* Converts natural language → Elasticsearch DSL
* Needs higher accuracy option via OpenAI
* Must remain cost-efficient with Ollama fallback

---

## 🎯 Target Architecture

Implement a provider-agnostic design:

Controller
→ AiQueryService
→ LlmClient (interface)
→ OllamaLlmClient OR OpenAiLlmClient (selected via config)

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

* OllamaLlmClient (existing, default)
* OpenAiLlmClient (new high-accuracy option)

---

### 2. Configuration-driven provider switching

Add configuration:

```yaml
llm:
  provider: ollama   # ollama | openai
  temperature: 0
  max-tokens: 1024

  ollama:
    base-url: http://localhost:11434
    model: mistral
    timeout-ms: 60000

  openai:
    api-key: ${OPENAI_API_KEY:}
    model: gpt-4o-mini
    base-url: https://api.openai.com
    timeout-ms: 30000
```

Behavior:

* Default provider = ollama
* If provider=openai → automatically switch
* No code changes required
* Fail fast if OpenAI selected but key missing

---

### 3. Ollama client requirements (PRIMARY / low-cost mode)

Endpoint:

POST `{base-url}/api/generate`

Request body:

```json
{
  "model": "<model>",
  "prompt": "<prompt>",
  "stream": false,
  "options": {
    "temperature": 0
  }
}
```

Requirements:

* Use Spring WebClient with connection pooling
* Proper timeout handling
* Structured latency logging
* Robust JSON parsing
* Thread-safe

---

### 4. OpenAI client requirements (HIGH-ACCURACY mode)

Endpoint:

POST `{base-url}/v1/chat/completions`

Request body format:

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

* Bearer auth header
* Retry (basic exponential backoff)
* Timeout handling
* Clean DTOs
* Extract assistant message safely
* Production-grade error handling

---

### 5. Optional smart fallback (RECOMMENDED)

Add config:

```yaml
llm:
  fallback-enabled: true
  primary: ollama
  secondary: openai
```

Behavior:

* Try primary first
* On timeout/error → fallback
* Log clearly when fallback happens
* Preserve response contract

---

### 6. Performance & safety guards (IMPORTANT)

Implement:

* Max prompt length guard
* Provider-specific timeout
* Structured logging (latency + provider)
* WebClient connection pooling
* Thread safety
* Kubernetes-friendly config
* Deterministic output (temperature=0)

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
2. OllamaLlmClient implementation
3. OpenAiLlmClient implementation
4. Spring configuration / factory
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

Produce clean, production-grade Java code suitable for long-term enterprise maintenance.
