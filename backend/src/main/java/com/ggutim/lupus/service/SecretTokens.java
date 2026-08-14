package com.ggutim.lupus.service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates opaque, cryptographically random secrets used as master and
 * player tokens (URL-safe base64, 32 bytes of entropy).
 */
final class SecretTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretTokens() {
    }

    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
