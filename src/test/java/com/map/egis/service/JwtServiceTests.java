package com.map.egis.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTests {
    private static final String SECRET = "test-signing-key-that-is-long-enough-123";

    @Test
    void issuesAndVerifiesSignedClaims() {
        JwtService service = new JwtService(SECRET);
        JwtService.IssuedToken issued = service.issue("user-01", "ADMIN");

        JwtService.Claims claims = service.verify(issued.value()).orElseThrow();
        assertEquals("user-01", claims.userId());
        assertEquals("ADMIN", claims.role());
        assertEquals(issued.csrfToken(), claims.csrfToken());
        assertTrue(claims.expiresAt() > System.currentTimeMillis() / 1000);
    }

    @Test
    void rejectsModifiedAndMalformedTokens() {
        JwtService service = new JwtService(SECRET);
        String[] parts = service.issue("member", "USER").value().split("\\.");
        parts[2] = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);

        assertTrue(service.verify(String.join(".", parts)).isEmpty());
        assertTrue(service.verify("not.a.jwt").isEmpty());
    }

    @Test
    void requiresStrongConfiguredSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("too-short"));
    }
}
