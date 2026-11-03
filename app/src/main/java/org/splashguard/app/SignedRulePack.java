package org.splashguard.app;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Restricted offline JWS verifier. Authentication never authorizes a selector or an action. */
public final class SignedRulePack {
    public static final int MAX_ENVELOPE_BYTES = 65536;
    public static final int MAX_HEADER_BYTES = 256;
    public static final int MAX_PAYLOAD_BYTES = 48000;
    public static final int MAX_TRUSTED_KEYS = 16;
    public static final int MAX_ALLOWED_PACK_IDS = 128;
    public static final String TYPE = "splashguard-rule-limits+jws";
    private static final Set<String> HEADER_FIELDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("alg", "kid", "typ")));
    private static final BigInteger F4 = BigInteger.valueOf(65537);
    private SignedRulePack() {}

    /** Fixed categories contain no untrusted file contents, paths, or provider exceptions. */
    public enum Reason {
        INPUT_FORMAT, HEADER, UNTRUSTED_KEY, SIGNATURE, PAYLOAD, PACK_SCOPE,
        TRUST_CONFIGURATION, CRYPTO_UNAVAILABLE
    }
    public static final class Failure extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public final Reason reason;
        private Failure(Reason reason) {
            super("Rule authentication failed: " + reason.name());
            this.reason = reason;
        }
    }

    /** Only reviewed application code supplies keys and their publisher/channel scope. */
    public static final class TrustedKey {
        public final String kid, publisher, channel;
        public final List<String> allowedPackIds;
        private final RSAPublicKey key;
        private final int signatureBytes;

        public TrustedKey(String kid, String publisher, String channel, List<String> allowedPackIds,
                          PublicKey publicKey) {
            this.kid = identifier(kid);
            this.publisher = identifier(publisher);
            this.channel = identifier(channel);
            if (allowedPackIds == null || allowedPackIds.isEmpty()
                    || allowedPackIds.size() > MAX_ALLOWED_PACK_IDS) throw fail(Reason.TRUST_CONFIGURATION);
            List<String> copy = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String id : allowedPackIds) {
                String checked = identifier(id);
                if (!seen.add(checked)) throw fail(Reason.TRUST_CONFIGURATION);
                copy.add(checked);
            }
            this.allowedPackIds = Collections.unmodifiableList(copy);
            if (!(publicKey instanceof RSAPublicKey)) throw fail(Reason.TRUST_CONFIGURATION);
            RSAPublicKey rsa = (RSAPublicKey) publicKey;
            BigInteger modulus = rsa.getModulus(), exponent = rsa.getPublicExponent();
            if (modulus == null || exponent == null || modulus.signum() <= 0 || !modulus.testBit(0)
                    || modulus.bitLength() < 2048 || modulus.bitLength() > 4096
                    || !F4.equals(exponent)) throw fail(Reason.TRUST_CONFIGURATION);
            // Rebuild a provider key to avoid retaining a caller-supplied mutable key object.
            try {
                key = (RSAPublicKey) KeyFactory.getInstance("RSA")
                        .generatePublic(new RSAPublicKeySpec(modulus, exponent));
            } catch (GeneralSecurityException invalid) {
                throw fail(Reason.TRUST_CONFIGURATION);
            }
            signatureBytes = (modulus.bitLength() + 7) / 8;
        }
    }

    /** The verifier alone constructs authentication results; canonical merged states are different. */
    public static final class VerifiedPack {
        public final OfflineRuleStore.Pack pack;
        public final String kid, publisher, channel, payloadSha256, originalEnvelope;
        private VerifiedPack(OfflineRuleStore.Pack pack, TrustedKey trusted,
                             String payloadSha256, String originalEnvelope) {
            this.pack = pack;
            kid = trusted.kid;
            publisher = trusted.publisher;
            channel = trusted.channel;
            this.payloadSha256 = payloadSha256;
            this.originalEnvelope = originalEnvelope;
        }
    }

    /** No IO, network, persistence, or current-state mutation occurs in this method. */
    public static VerifiedPack verify(String compact, List<TrustedKey> trustedKeys) {
        if (compact == null || compact.isEmpty() || compact.length() > MAX_ENVELOPE_BYTES)
            throw fail(Reason.INPUT_FORMAT);
        int first = -1, second = -1;
        for (int i = 0; i < compact.length(); i++) {
            char c = compact.charAt(i);
            if (c == '.') {
                if (first < 0) first = i;
                else if (second < 0) second = i;
                else throw fail(Reason.INPUT_FORMAT);
            } else if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_'))
                throw fail(Reason.INPUT_FORMAT);
        }
        if (first <= 0 || second <= first + 1 || second >= compact.length() - 1)
            throw fail(Reason.INPUT_FORMAT);
        Map<String, TrustedKey> trust = trustIndex(trustedKeys);
        byte[] headerBytes = decodeSegment(compact.substring(0, first), MAX_HEADER_BYTES);
        Map<String, Object> header;
        try {
            header = RuleJsonCodec.readObject(utf8(headerBytes, Reason.HEADER));
            if (!HEADER_FIELDS.equals(header.keySet()) || !"RS256".equals(header.get("alg"))
                    || !TYPE.equals(header.get("typ")) || !(header.get("kid") instanceof String))
                throw fail(Reason.HEADER);
        } catch (IllegalArgumentException invalid) {
            throw fail(Reason.HEADER);
        }
        Object keyId = header.get("kid");
        TrustedKey trusted = trust.get(keyId);
        if (trusted == null) throw fail(Reason.UNTRUSTED_KEY);
        byte[] payload = decodeSegment(compact.substring(first + 1, second), MAX_PAYLOAD_BYTES);
        byte[] signature = decodeSegment(compact.substring(second + 1), trusted.signatureBytes);
        if (signature.length != trusted.signatureBytes) throw fail(Reason.SIGNATURE);
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(trusted.key);
            verifier.update(compact.substring(0, second).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(signature)) throw fail(Reason.SIGNATURE);
        } catch (SignatureException invalid) {
            throw fail(Reason.SIGNATURE);
        } catch (GeneralSecurityException unavailable) {
            throw fail(Reason.CRYPTO_UNAVAILABLE);
        }
        OfflineRuleStore.Pack pack;
        try {
            pack = RuleJsonCodec.decodePack(utf8(payload, Reason.PAYLOAD));
        } catch (IllegalArgumentException invalid) {
            throw fail(Reason.PAYLOAD);
        }
        if (!trusted.allowedPackIds.contains(pack.id)) throw fail(Reason.PACK_SCOPE);
        return new VerifiedPack(pack, trusted, digest(payload), compact);
    }

    private static Map<String, TrustedKey> trustIndex(List<TrustedKey> keys) {
        if (keys == null || keys.size() > MAX_TRUSTED_KEYS) throw fail(Reason.TRUST_CONFIGURATION);
        Map<String, TrustedKey> result = new LinkedHashMap<>();
        for (TrustedKey key : keys) {
            if (key == null || result.put(key.kid, key) != null) throw fail(Reason.TRUST_CONFIGURATION);
        }
        return result;
    }
    private static byte[] decodeSegment(String text, int maxBytes) {
        if (text.isEmpty() || text.length() > (maxBytes * 4 + 2) / 3)
            throw fail(Reason.INPUT_FORMAT);
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(text);
            if (bytes.length == 0 || bytes.length > maxBytes
                    || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(text))
                throw fail(Reason.INPUT_FORMAT);
            return bytes;
        } catch (IllegalArgumentException invalid) {
            throw fail(Reason.INPUT_FORMAT);
        }
    }
    private static String utf8(byte[] bytes, Reason reason) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException invalid) {
            throw fail(reason);
        }
    }
    private static String identifier(String value) {
        if (value == null || value.length() > 80
                || !value.matches("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*"))
            throw fail(Reason.TRUST_CONFIGURATION);
        return value;
    }
    private static String digest(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(64);
            for (byte value : hash) {
                int unsigned = value & 255;
                result.append(Character.forDigit(unsigned >>> 4, 16));
                result.append(Character.forDigit(unsigned & 15, 16));
            }
            return result.toString();
        } catch (GeneralSecurityException unavailable) {
            throw fail(Reason.CRYPTO_UNAVAILABLE);
        }
    }
    private static Failure fail(Reason reason) { return new Failure(reason); }
}
