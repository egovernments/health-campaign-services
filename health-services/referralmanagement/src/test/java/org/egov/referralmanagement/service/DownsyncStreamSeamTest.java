package org.egov.referralmanagement.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression cover for the "one glued line per HH_MEMBERS file" bug fixed by
 * emitting a trailing {@code '\n'} at the end of every {@code streamQuery} /
 * {@code streamIndividualQuery} invocation.
 *
 * <p>Prior to the fix, chaining two {@code streamQuery} calls on the same
 * gzip stream produced output where the last object emitted by the first
 * call and the first object emitted by the second call were concatenated on
 * a single physical line — invalid NDJSON, since a strict line-by-line
 * parser would drop the joined line.
 *
 * <p>The scenarios below run the same 4-step pattern the writer uses:
 * <ol>
 *   <li>Open a {@code JsonGenerator} on a shared {@code GZIPOutputStream}</li>
 *   <li>Emit N NDJSON root objects using the writer's rootValueSeparator</li>
 *   <li>Close the generator, emit an explicit {@code '\n'} at the seam</li>
 *   <li>Repeat for the next entity type</li>
 * </ol>
 * The tests then round-trip the gzip bytes and assert every non-blank line
 * parses independently.
 */
class DownsyncStreamSeamTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Emits N `{"_t":<tag>,"i":<index>}` roots using the exact writer pattern. */
    private void emitStream(GZIPOutputStream gzip, String tag, int n) throws Exception {
        JsonGenerator gen = mapper.getFactory().createGenerator(gzip);
        gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        gen.setRootValueSeparator(new SerializedString("\n"));
        try {
            for (int i = 0; i < n; i++) {
                gen.writeStartObject();
                gen.writeStringField("_t", tag);
                gen.writeNumberField("i", i);
                gen.writeEndObject();
            }
        } finally {
            gen.flush();
            gen.close();
            gzip.write('\n');   // the fix — trailing seam newline
        }
    }

    private String[] roundTripLines(byte[] gzipped) throws Exception {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString().split("\n", -1);
        }
    }

    // ── HH_MEMBERS shape: 2 chained streamQuery calls ────────────────────────

    @Test
    @DisplayName("Two chained streamQuery calls produce clean NDJSON — no glued seam line")
    void chainedStreamQuery_producesCleanNdjson() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buf)) {
            emitStream(gzip, "HOUSEHOLD",        50);
            emitStream(gzip, "HOUSEHOLD_MEMBER", 80);
        }
        String[] lines = roundTripLines(buf.toByteArray());
        int nonBlank = 0, parsed = 0;
        for (String l : lines) {
            if (l.trim().isEmpty()) continue;
            nonBlank++;
            mapper.readTree(l);   // throws if the line isn't a valid JSON root
            parsed++;
        }
        assertEquals(130, nonBlank, "expected 50 + 80 non-blank NDJSON lines");
        assertEquals(130, parsed,   "every non-blank line must parse as a JSON root — no glued seam");
    }

    // ── Four chained streams (BENE_AE_REF shape) ─────────────────────────────

    @Test
    @DisplayName("Four chained streamQuery calls produce clean NDJSON — no seam glued anywhere")
    void fourChainedStreams_producesCleanNdjson() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buf)) {
            emitStream(gzip, "PROJECT_BENEFICIARY", 25);
            emitStream(gzip, "SIDE_EFFECT",         10);
            emitStream(gzip, "REFERRAL",             7);
            emitStream(gzip, "HF_REFERRAL",          3);
        }
        String[] lines = roundTripLines(buf.toByteArray());
        int parsed = 0;
        for (String l : lines) {
            if (l.trim().isEmpty()) continue;
            mapper.readTree(l);
            parsed++;
        }
        assertEquals(45, parsed, "25+10+7+3=45 parseable NDJSON lines");
    }

    // ── Edge case: one of the streams is empty (0 rows) ──────────────────────

    @Test
    @DisplayName("Empty stream at the seam does not corrupt the following stream's first row")
    void emptyMiddleStream_seamStillClean() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buf)) {
            emitStream(gzip, "A", 3);
            emitStream(gzip, "B", 0);   // empty
            emitStream(gzip, "C", 3);
        }
        String[] lines = roundTripLines(buf.toByteArray());
        int aCount = 0, cCount = 0;
        for (String l : lines) {
            if (l.trim().isEmpty()) continue;
            var node = mapper.readTree(l);
            String t = node.get("_t").asText();
            if ("A".equals(t)) aCount++;
            else if ("C".equals(t)) cCount++;
        }
        assertEquals(3, aCount);
        assertEquals(3, cCount);
    }

    // ── Only one stream — trailing newline shouldn't confuse consumers ───────

    @Test
    @DisplayName("Single-stream output ends with newline — trailing empty line is harmless")
    void singleStream_trailingNewlineIsFine() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buf)) {
            emitStream(gzip, "INDIVIDUAL", 5);
        }
        String[] lines = roundTripLines(buf.toByteArray());
        int parsed = 0;
        for (String l : lines) {
            if (l.trim().isEmpty()) continue;
            mapper.readTree(l);
            parsed++;
        }
        assertEquals(5, parsed);
    }
}
