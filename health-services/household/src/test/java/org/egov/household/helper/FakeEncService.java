package org.egov.household.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.egov.common.pii.EncryptionDecryptionUtil;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A stand-in for enc-service in unit tests: cipher-shaped text that can be turned back into the plain value. */
public final class FakeEncService {

    private static final String KEY_ID = "228075";

    private FakeEncService() {
    }

    /** Passes the library's cipher check (keyId|base64, at least 16 bytes) and hides the plain value. */
    public static String fakeCipher(String plain) {
        return KEY_ID + "|" + Base64.getEncoder().encodeToString(("################" + plain).getBytes(StandardCharsets.UTF_8));
    }

    public static String fakePlain(String cipher) {
        String text = new String(Base64.getDecoder().decode(cipher.substring(cipher.indexOf('|') + 1)), StandardCharsets.UTF_8);
        return text.substring(16);
    }

    /** An {@link EncryptionDecryptionUtil} mock that encrypts and decrypts with {@link #fakeCipher}. */
    @SuppressWarnings("unchecked")
    public static EncryptionDecryptionUtil encUtil(ObjectMapper mapper) {
        EncryptionDecryptionUtil util = mock(EncryptionDecryptionUtil.class);
        when(util.encryptValues(anyList(), anyString())).thenAnswer(call -> ((List<String>) call.getArgument(0)).stream()
                .map(FakeEncService::fakeCipher).collect(Collectors.toList()));
        when(util.decryptValueNodes(any(), anyString(), any(), anyString())).thenAnswer(call -> {
            ArrayNode in = call.getArgument(0);
            ArrayNode out = mapper.createArrayNode();
            in.forEach(n -> out.addObject().put("value", fakePlain(n.get("value").asText())));
            return out;
        });
        return util;
    }
}
