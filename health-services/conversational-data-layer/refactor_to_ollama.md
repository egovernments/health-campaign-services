You are a senior backend architect working on the DIGIT HCM platform.

**Goal:** Refactor an existing AI service that currently uses the Anthropic API so that it works with a local Ollama-based model for a long-term, cost-free, and offline-capable solution.

### Current state

* The service calls Anthropic using ANTHROPIC_API_KEY.
* It sends prompts and receives completions.
* It is used to convert natural language into Elasticsearch queries.
* The codebase is Java + Spring Boot.

### Target state

Migrate the implementation to use a locally running Ollama model while keeping the service clean, configurable, and provider-agnostic.

---

### Requirements

**1. Remove vendor lock-in**

* Introduce an abstraction layer (e.g., `LlmClient` interface).
* Anthropic implementation should be removable.
* Add an Ollama implementation.

**2. Ollama integration**

* Use Ollama HTTP API: `http://localhost:11434/api/generate`
* Default model: `mistral` (configurable)
* Support streaming = false
* Temperature configurable (default 0)

**3. Configuration**

* Remove dependency on `ANTHROPIC_API_KEY`
* Add config:

```yaml
llm:
  provider: ollama
  ollama:
    base-url: http://localhost:11434
    model: mistral
    temperature: 0
```

**4. Code quality**

* Follow Spring Boot best practices
* Use RestTemplate or WebClient
* Proper timeout handling
* Proper error handling
* Clean DTOs for request/response
* Make it production-ready

**5. Backward compatibility**

* Keep prompt-building logic unchanged
* Keep response contract unchanged
* Ensure Elasticsearch query generation still works

**6. Provide**

* Interface design
* Ollama client implementation
* Updated service class
* application.yml changes
* Sample curl to test Ollama
* Steps to run locally

---

### Important constraints

* Optimize for deterministic output (temperature 0)
* Keep token usage minimal
* Handle large prompts safely
* Do NOT assume GPU availability
* Make solution Kubernetes-friendly for future

Produce clean, production-grade Java code.
