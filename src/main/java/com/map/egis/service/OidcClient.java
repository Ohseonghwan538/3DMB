package com.map.egis.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class OidcClient {
    private final String issuer;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile JsonObject discovery;

    public OidcClient(@Value("${app.oidc.issuer:}") String issuer,
                      @Value("${app.oidc.client-id:}") String clientId,
                      @Value("${app.oidc.client-secret:}") String clientSecret,
                      @Value("${app.oidc.redirect-uri:}") String redirectUri) {
        this.issuer = issuer == null ? "" : issuer.trim();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public AuthorizationRequest authorizationRequest() {
        requireConfigured();
        String state = randomToken();
        String nonce = randomToken();
        String verifier = randomToken() + randomToken();
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
        JsonObject metadata = metadata();
        String location = metadata.get("authorization_endpoint").getAsString()
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode("openid profile email")
                + "&state=" + encode(state)
                + "&nonce=" + encode(nonce)
                + "&code_challenge=" + encode(challenge)
                + "&code_challenge_method=S256";
        return new AuthorizationRequest(location, state, nonce, verifier);
    }

    public Identity exchangeAndVerify(String code, String verifier, String expectedNonce) {
        requireConfigured();
        if (code == null || code.isBlank() || code.length() > 4096) throw new IllegalArgumentException("SSO 인증 코드를 확인할 수 없습니다.");
        JsonObject metadata = metadata();
        try {
            String form = "grant_type=authorization_code"
                    + "&code=" + encode(code)
                    + "&redirect_uri=" + encode(redirectUri)
                    + "&client_id=" + encode(clientId)
                    + "&code_verifier=" + encode(verifier);
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(metadata.get("token_endpoint").getAsString()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form));
            if (clientSecret != null && !clientSecret.isBlank()) {
                String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
                request.header("Authorization", "Basic " + basic);
            }
            JsonObject tokenResponse = sendJson(request.build());
            if (!tokenResponse.has("id_token")) throw new IllegalArgumentException("SSO 응답에 ID 토큰이 없습니다.");
            return verifyIdToken(tokenResponse.get("id_token").getAsString(), expectedNonce, metadata);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SSO 공급자와의 통신이 중단되었습니다.", error);
        } catch (Exception error) {
            if (error instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalStateException("SSO 인증 응답을 검증하지 못했습니다.", error);
        }
    }

    private Identity verifyIdToken(String token, String expectedNonce, JsonObject metadata) throws Exception {
        if (token.length() > 16_384) throw new IllegalArgumentException("SSO ID 토큰이 허용 크기를 초과했습니다.");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw new IllegalArgumentException("SSO ID 토큰 형식이 올바르지 않습니다.");
        JsonObject header = JsonParser.parseString(decode(parts[0])).getAsJsonObject();
        if (!"RS256".equals(string(header, "alg"))) throw new IllegalArgumentException("SSO ID 토큰 알고리즘이 허용되지 않습니다.");
        String kid = string(header, "kid");
        JsonObject key = findKey(metadata.get("jwks_uri").getAsString(), kid);
        BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(string(key, "n")));
        BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(string(key, "e")));
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, exponent));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) {
            throw new IllegalArgumentException("SSO ID 토큰 서명이 유효하지 않습니다.");
        }
        JsonObject claims = JsonParser.parseString(decode(parts[1])).getAsJsonObject();
        String tokenIssuer = string(claims, "iss");
        String subject = string(claims, "sub");
        String nonce = string(claims, "nonce");
        if (!issuer.equals(tokenIssuer) || subject.isBlank() || !constantEquals(expectedNonce, nonce)) {
            throw new IllegalArgumentException("SSO 발급자, 사용자 또는 nonce가 일치하지 않습니다.");
        }
        if (claims.get("exp").getAsLong() <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("SSO ID 토큰이 만료되었습니다.");
        }
        if (!claims.has("iat") || claims.get("iat").getAsLong() > Instant.now().plusSeconds(60).getEpochSecond()
                || claims.get("exp").getAsLong() <= claims.get("iat").getAsLong()) {
            throw new IllegalArgumentException("SSO ID 토큰의 발급 시각이 유효하지 않습니다.");
        }
        if (!audienceContains(claims.get("aud"), clientId)) {
            throw new IllegalArgumentException("SSO ID 토큰의 audience가 일치하지 않습니다.");
        }
        if (claims.get("aud").isJsonArray() && claims.getAsJsonArray("aud").size() > 1
                && !clientId.equals(string(claims, "azp"))) {
            throw new IllegalArgumentException("SSO ID 토큰의 authorized party가 일치하지 않습니다.");
        }
        String email = optionalString(claims, "email");
        boolean emailVerified = claims.has("email_verified") && claims.get("email_verified").getAsBoolean();
        String name = optionalString(claims, "name");
        if (!emailVerified || email == null || email.isBlank()) {
            throw new IllegalArgumentException("SSO 계정은 검증된 이메일 주소를 제공해야 합니다.");
        }
        return new Identity(issuer, subject, email, name == null || name.isBlank() ? email : name, true);
    }

    private JsonObject findKey(String jwksUri, String kid) throws InterruptedException {
        JsonObject keys = sendJson(HttpRequest.newBuilder(URI.create(jwksUri)).timeout(Duration.ofSeconds(10)).GET().build());
        JsonArray array = keys.getAsJsonArray("keys");
        for (JsonElement element : array) {
            JsonObject key = element.getAsJsonObject();
            if (kid.equals(string(key, "kid")) && "RSA".equals(string(key, "kty"))
                    && (!key.has("use") || "sig".equals(string(key, "use")))) return key;
        }
        throw new IllegalArgumentException("SSO 서명 키를 찾을 수 없습니다.");
    }

    private JsonObject metadata() {
        JsonObject cached = discovery;
        if (cached != null) return cached;
        synchronized (this) {
            if (discovery == null) {
                try {
                    JsonObject metadata = sendJson(HttpRequest.newBuilder(URI.create(stripSlash(issuer) + "/.well-known/openid-configuration"))
                            .timeout(Duration.ofSeconds(10)).GET().build());
                    if (!issuer.equals(string(metadata, "issuer"))) {
                        throw new IllegalArgumentException("OIDC discovery issuer가 설정과 다릅니다.");
                    }
                    for (String field : new String[]{"authorization_endpoint", "token_endpoint", "jwks_uri"}) {
                        URI endpoint = URI.create(string(metadata, field));
                        if (!"https".equalsIgnoreCase(endpoint.getScheme())) {
                            throw new IllegalArgumentException("OIDC endpoint는 HTTPS여야 합니다: " + field);
                        }
                    }
                    discovery = metadata;
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("OIDC 설정을 조회하던 중 통신이 중단되었습니다.", error);
                }
            }
            return discovery;
        }
    }

    private JsonObject sendJson(HttpRequest request) throws InterruptedException {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OIDC provider request failed with HTTP status {}", response.statusCode());
                throw new IllegalStateException("OIDC 공급자가 요청을 거부했습니다 (HTTP " + response.statusCode() + ").");
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (java.io.IOException error) {
            throw new IllegalStateException("OIDC 공급자에 연결하지 못했습니다.", error);
        }
    }

    private void requireConfigured() {
        if (issuer.isBlank() || clientId == null || clientId.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalStateException("OIDC_ISSUER, OIDC_CLIENT_ID, OIDC_REDIRECT_URI 설정이 필요합니다.");
        }
        if (!"https".equalsIgnoreCase(URI.create(issuer).getScheme())) {
            throw new IllegalStateException("OIDC issuer는 HTTPS URL이어야 합니다.");
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String optionalString(JsonObject object, String key) {
        String value = string(object, key);
        return value.isBlank() ? null : value;
    }

    private static boolean audienceContains(JsonElement aud, String clientId) {
        if (aud == null || aud.isJsonNull()) return false;
        if (aud.isJsonPrimitive()) return clientId.equals(aud.getAsString());
        if (aud.isJsonArray()) {
            for (JsonElement item : aud.getAsJsonArray()) if (clientId.equals(item.getAsString())) return true;
        }
        return false;
    }

    private boolean constantEquals(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception unavailable) {
            throw new IllegalStateException("OIDC PKCE challenge를 만들 수 없습니다.", unavailable);
        }
    }

    private String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripSlash(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public record AuthorizationRequest(String location, String state, String nonce, String verifier) { }
    public record Identity(String issuer, String subject, String email, String name, boolean emailVerified) { }
}
