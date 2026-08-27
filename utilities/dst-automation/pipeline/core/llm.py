"""Narrative text generation via Groq's OpenAI-compatible chat API.

Config: GROQ_API_KEY (required), GROQ_MODEL (default openai/gpt-oss-120b),
GROQ_BASE_URL.
Failures are non-fatal — a placeholder string is returned so reports still build.
"""
import logging
import os
import time

import requests

log = logging.getLogger(__name__)

# llama-3.3-70b-versatile was decommissioned by Groq on 2026-08-16
DEFAULT_MODEL = "openai/gpt-oss-120b"
DEFAULT_BASE_URL = "https://api.groq.com/openai/v1"


def generate_narrative(prompt, max_tokens=300):
    api_key = os.getenv("GROQ_API_KEY")
    if not api_key:
        log.warning("GROQ_API_KEY not set — returning placeholder text")
        return "[Narrative not generated — GROQ_API_KEY missing]"
    base = os.getenv("GROQ_BASE_URL", DEFAULT_BASE_URL).rstrip("/")
    model = os.getenv("GROQ_MODEL", DEFAULT_MODEL)
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": max_tokens,
        "temperature": 0.4,
    }
    if "gpt-oss" in model:
        # reasoning model: hidden reasoning tokens count against max_tokens, so
        # keep reasoning short and pad the cap — callers' max_tokens stays the
        # visible-output budget it was under llama
        payload["reasoning_effort"] = "low"
        payload["max_tokens"] = max_tokens + 500
    # Retry: a transient 404/429/5xx from Groq used to leave the placeholder
    # string as the report body posted to the partner channel. Seen live on
    # 2026-08-20 - three calls in one run all hit the same few-second blip.
    last = None
    for attempt in range(3):
        try:
            r = requests.post(
                f"{base}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json=payload,
                timeout=120,
            )
            if r.status_code >= 400:
                # the body carries Groq's reason; the bare status hides it
                log.warning(f"Groq {r.status_code} (attempt {attempt + 1}/3) "
                            f"model={model}: {r.text[:200]}")
                last = requests.HTTPError(f"{r.status_code}: {r.text[:200]}")
                if attempt < 2:
                    time.sleep(2 * (attempt + 1))
                    continue
                log.error(f"Groq gave up after 3 attempts: {last}")
                return "[Narrative not generated — LLM error]"
            break
        except requests.RequestException as e:
            last = e
            if attempt == 2:
                log.error(f"Groq unreachable after 3 attempts: {e}", exc_info=True)
                return "[Narrative not generated — LLM error]"
            log.warning(f"Groq request failed (attempt {attempt + 1}/3): {e}")
            time.sleep(2 * (attempt + 1))
    try:
        choice = (r.json().get("choices") or [{}])[0]
        if choice.get("finish_reason") == "length":
            log.warning(f"Narrative response hit max_tokens={payload['max_tokens']} "
                        f"— output likely truncated")
        txt = (choice.get("message", {}).get("content") or "").strip()
        # gpt-oss emits narrow no-break spaces (U+202F) e.g. in "88 %" — normalise
        # to plain ASCII so Word/Slack output stays clean
        txt = txt.replace(" ", " ").replace(" ", " ").replace(" %", "%")
        return txt or "[Narrative not generated — empty response]"
    except Exception as e:
        log.warning(f"Narrative generation failed (non-fatal): {e}")
        return "[Narrative not generated — LLM error]"
