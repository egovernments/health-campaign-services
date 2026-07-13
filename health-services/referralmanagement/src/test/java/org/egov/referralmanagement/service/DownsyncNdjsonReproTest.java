package org.egov.referralmanagement.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Reproduce the "Trying to output second root, &lt;ArrayNode&gt;" failure
 * seen in prod on Azure Blob. Each test variant mirrors more of the prod
 * pipeline until we get a green→red flip; when it fails, the JUnit stack
 * trace pinpoints the exact Jackson line so the fix can be surgical.
 */
class DownsyncNdjsonReproTest {

    private final ObjectMapper objectMapper = buildProdObjectMapper();

    private static ObjectMapper buildProdObjectMapper() {
        // Same shape as MainConfiguration.objectMapper() bean
        ObjectMapper m = new ObjectMapper();
        m.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        m.registerModule(new JavaTimeModule());
        return m;
    }

    /** Build one individual row shaped exactly like the prod SQL result. */
    private Map<String, Object> makeRow(int i, boolean decryptedString, boolean withAddress) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id",                "id-" + i);
        row.put("clientReferenceId", "cri-" + i);
        row.put("tenantId",          "ba");

        ArrayNode identifiers = objectMapper.createArrayNode();
        ObjectNode idNode = objectMapper.createObjectNode();
        idNode.put("identifierId",   "id-value-" + i);
        idNode.put("identifierType", "AADHAAR");
        identifiers.add(idNode);

        if (decryptedString) {
            // Post-decrypt: identifiers_json is a plain String containing the ArrayNode text.
            row.put("identifiers_json", identifiers.toString());
        } else {
            // Un-decrypted: JDBC delivers a PGobject with type=jsonb.
            row.put("identifiers_json", pgJsonb(identifiers.toString()));
        }

        if (withAddress) {
            ArrayNode addr = objectMapper.createArrayNode();
            ObjectNode a = objectMapper.createObjectNode();
            a.put("addressLine1", "42 Main St");
            addr.add(a);
            row.put("addresses_json", pgJsonb(addr.toString()));
        } else {
            row.put("addresses_json", null);
        }

        ArrayNode skills = objectMapper.createArrayNode();
        row.put("skills_json", pgJsonb(skills.toString()));

        // additionaldetails — commonly an ObjectNode-shaped jsonb
        ObjectNode addl = objectMapper.createObjectNode();
        addl.put("extra", "value-" + i);
        row.put("additionalDetails", pgJsonb(addl.toString()));

        return row;
    }

    private static PGobject pgJsonb(String value) {
        PGobject p = new PGobject();
        p.setType("jsonb");
        try { p.setValue(value); } catch (Exception e) { throw new RuntimeException(e); }
        return p;
    }

    /** Copy of production wjsonm helper — writes a jsonb/string as raw JSON value. */
    private void wjsonm(JsonGenerator gen, String field, Map<String, Object> row, String col) throws Exception {
        Object val = row.get(col);
        if (val == null) {
            gen.writeNullField(field);
        } else if (val instanceof PGobject pg) {
            gen.writeFieldName(field);
            String v = pg.getValue();
            if (v != null) gen.writeRawValue(v); else gen.writeNull();
        } else if (val instanceof String s) {
            gen.writeFieldName(field);
            gen.writeRawValue(s);
        } else {
            gen.writeNullField(field);
        }
    }

    /** Copy of production writeIndividualBuffer — writes NDJSON of individuals. */
    private void writeIndividualBuffer(JsonGenerator gen, List<Map<String, Object>> buffer) throws Exception {
        for (Map<String, Object> row : buffer) {
            gen.writeStartObject();
            gen.writeStringField("_t", "INDIVIDUAL");
            gen.writeStringField("id",                (String) row.get("id"));
            gen.writeStringField("clientReferenceId", (String) row.get("clientReferenceId"));
            gen.writeStringField("tenantId",          (String) row.get("tenantId"));
            wjsonm(gen, "additionalFields", row, "additionalDetails");
            wjsonm(gen, "address",          row, "addresses_json");
            wjsonm(gen, "identifiers",      row, "identifiers_json");
            wjsonm(gen, "skills",           row, "skills_json");
            gen.writeEndObject();
            gen.writeRaw('\n');
        }
    }

    // ── Variant 1: plain OutputStream, all PGobject ──────────────────────────

    @Test
    @DisplayName("1000 rows, all PGobject-typed jsonb, plain OutputStream")
    void variant1_plainStream_allPGobject() throws Exception {
        List<Map<String, Object>> buf = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) buf.add(makeRow(i, false, true));
        drive(buf, /*gzip=*/false);
    }

    // ── Variant 2: plain OutputStream, mix of PGobject and decrypted String ──

    @Test
    @DisplayName("1000 rows, mixed PGobject + decrypted String, plain OutputStream")
    void variant2_plainStream_mixed() throws Exception {
        List<Map<String, Object>> buf = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) buf.add(makeRow(i, i % 3 == 0, true));
        drive(buf, /*gzip=*/false);
    }

    // ── Variant 3: GZIPOutputStream, mixed PGobject and String ───────────────

    @Test
    @DisplayName("1000 rows, mixed, wrapped in GZIPOutputStream")
    void variant3_gzip_mixed() throws Exception {
        List<Map<String, Object>> buf = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) buf.add(makeRow(i, i % 3 == 0, true));
        drive(buf, /*gzip=*/true);
    }

    // ── Variant 4: LARGE batch (5000 rows) via GZIP ──────────────────────────

    @Test
    @DisplayName("5000 rows, mixed, GZIP — pressures write context state")
    void variant4_gzip_5000() throws Exception {
        List<Map<String, Object>> buf = new java.util.ArrayList<>();
        for (int i = 0; i < 5000; i++) buf.add(makeRow(i, i % 3 == 0, true));
        drive(buf, /*gzip=*/true);
    }

    // ── Variant 5: batch-flushed like prod — call writeIndividualBuffer 5 times on the SAME gen
    @Test
    @DisplayName("5 batches of 1000 rows each, same generator (like the mid-cursor flush path)")
    void variant5_multiBatchSameGen() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = new GZIPOutputStream(out);
        JsonGenerator gen = objectMapper.getFactory().createGenerator(gzip);
        gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        try {
            for (int batch = 0; batch < 5; batch++) {
                List<Map<String, Object>> buf = new java.util.ArrayList<>();
                int base = batch * 1000;
                for (int i = 0; i < 1000; i++) buf.add(makeRow(base + i, i % 3 == 0, true));
                writeIndividualBuffer(gen, buf);
            }
        } finally {
            gen.flush();
            gen.close();
            gzip.close();
        }
        assertRoundTrip(out.toByteArray(), 5000);
    }

    // ── Variant 6: identifiers_json content contains a top-level ARRAY only ──

    @Test
    @DisplayName("Every row's identifiers_json is a bare ArrayNode as decrypted String — worst case")
    void variant6_allArrayNodeString() throws Exception {
        List<Map<String, Object>> buf = new java.util.ArrayList<>();
        for (int i = 0; i < 1000; i++) buf.add(makeRow(i, /*decrypted=*/true, true));
        drive(buf, true);
    }

    private void drive(List<Map<String, Object>> buffer, boolean useGzip) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = useGzip ? new GZIPOutputStream(out) : null;
        java.io.OutputStream target = useGzip ? gzip : out;
        JsonGenerator gen = objectMapper.getFactory().createGenerator(target);
        gen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        try {
            writeIndividualBuffer(gen, buffer);
        } finally {
            gen.flush();
            gen.close();
            if (useGzip) gzip.close();
        }
        byte[] bytes = out.toByteArray();
        assertRoundTrip(bytes, buffer.size(), useGzip);
    }

    private void assertRoundTrip(byte[] bytes, int expectedLines) {
        assertRoundTrip(bytes, expectedLines, true);
    }

    private void assertRoundTrip(byte[] bytes, int expectedLines, boolean gzip) {
        try {
            java.io.InputStream in = gzip
                    ? new GZIPInputStream(new java.io.ByteArrayInputStream(bytes))
                    : new java.io.ByteArrayInputStream(bytes);
            String content = new String(in.readAllBytes());
            // Count NDJSON lines
            long lines = content.lines().filter(l -> !l.isBlank()).count();
            if (lines != expectedLines) {
                throw new AssertionError("Expected " + expectedLines + " lines, got " + lines);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
