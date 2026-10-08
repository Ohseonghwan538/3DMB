package com.map.egis.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class JwtService {
    public static final String COOKIE_NAME = "ACCESS_TOKEN";
    private static final long TOKEN_TTL_SECONDS = 8 * 60 * 60;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Pattern PAYLOAD_PATTERN = Pattern.compile(
            "\\{\"sub\":\"((?:\\\\.|[^\"\\\\])*)\",\"role\":\"((?:\\\\.|[^\"\\\\])*)\",\"csrf\":\"([A-Za-z0-9-]+)\",\"iat\":([0-9]+),\"exp\":([0-9]+)\\}");
    private final byte[] signingKey;

    public JwtService(@Value("${app.jwt.secret:}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            signingKey = new byte[32];
            new SecureRandom().nextBytes(signingKey);
            log.warn("JWT_SECRET is not set; tokens will be invalidated whenever this application restarts.");
        } else {
            byte[] configured = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (configured.length < 32) throw new IllegalArgumentException("JWT_SECRET must be at least 32 UTF-8 bytes.");
            signingKey = configured;
        }
    }

    public IssuedToken issue(String userId, String role) {
        try {
            String csrf = UUID.randomUUID().toString();
            long issuedAt = Instant.now().getEpochSecond();
            String header = ENCODER.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String payloadJson = "{\"sub\":\"" + escapeJson(userId) + "\",\"role\":\"" + escapeJson(role)
                    + "\",\"csrf\":\"" + csrf + "\",\"iat\":" + issuedAt + ",\"exp\":" + (issuedAt + TOKEN_TTL_SECONDS) + "}";
            String payload = ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = header + "." + payload;
            return new IssuedToken(signingInput + "." + ENCODER.encodeToString(sign(signingInput)), csrf, TOKEN_TTL_SECONDS);
        } catch (Exception error) {
            throw new IllegalStateException("인증 토큰을 생성하지 못했습니다.", error);
        }
    }

    public Optional<Claims> verify(String token) {
        if (token == null || token.length() > 8192) return Optional.empty();
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) return Optional.empty();
        try {
            String header = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            if (!HEADER_JSON.equals(header)) return Optional.empty();
            byte[] expected = sign(parts[0] + "." + parts[1]);
            byte[] submitted = DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expected, submitted)) return Optional.empty();
            String payload = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            Matcher matcher = PAYLOAD_PATTERN.matcher(payload);
            if (!matcher.matches()) return Optional.empty();
            String userId = unescapeJson(matcher.group(1));
            String role = unescapeJson(matcher.group(2));
            String csrf = matcher.group(3);
            long expiresAt = Long.parseLong(matcher.group(5));
            if (userId.isBlank() || role.isBlank() || csrf.isBlank() || expiresAt <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(new Claims(userId, role, csrf, expiresAt));
        } catch (Exception invalidToken) {
            return Optional.empty();
        }
    }

    private byte[] sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.US_ASCII));
    }

    private String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }

    private String unescapeJson(String value) {
        StringBuilder decoded = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '\\') {
                decoded.append(character);
                continue;
            }
            if (++index >= value.length()) throw new IllegalArgumentException("Invalid JSON escape.");
            char escaped = value.charAt(index);
            switch (escaped) {
                case '"', '\\', '/' -> decoded.append(escaped);
                case 'b' -> decoded.append('\b');
                case 'f' -> decoded.append('\f');
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 't' -> decoded.append('\t');
                case 'u' -> {
                    if (index + 4 >= value.length()) throw new IllegalArgumentException("Invalid JSON unicode escape.");
                    decoded.append((char) Integer.parseInt(value.substring(index + 1, index + 5), 16));
                    index += 4;
                }
                default -> throw new IllegalArgumentException("Invalid JSON escape.");
            }
        }
        return decoded.toString();
    }

    public record IssuedToken(String value, String csrfToken, long ttlSeconds) { }
    public record Claims(String userId, String role, String csrfToken, long expiresAt) { }
}
