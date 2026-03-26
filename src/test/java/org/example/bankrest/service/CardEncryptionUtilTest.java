package org.example.bankrest.service;

import org.example.bankrest.util.CardEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class CardEncryptionUtilTest {

    private static final String TEST_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private CardEncryptionUtil util;

    @BeforeEach
    void setUp() {
        util = new CardEncryptionUtil(TEST_SECRET);
    }

    @Test
    @DisplayName("encrypt then decrypt should return original number")
    void encryptThenDecrypt() {
        String cardNumber = "4532015112830366";
        String encrypted = util.encrypt(cardNumber);
        String decrypted = util.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(cardNumber);
    }

    @Test
    @DisplayName("two encryptions of the same number should differ (random IV)")
    void randomIv() {
        String cardNumber = "4532015112830366";
        String enc1 = util.encrypt(cardNumber);
        String enc2 = util.encrypt(cardNumber);
        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test
    @DisplayName("mask should show only last 4 digits")
    void mask() {
        String masked = util.mask("4532015112830366");
        assertThat(masked).isEqualTo("**** **** **** 0366");
    }

    @Test
    @DisplayName("mask should throw on non-16-digit input")
    void maskInvalidLength() {
        assertThatThrownBy(() -> util.mask("123456789"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16 digits");
    }

    @Test
    @DisplayName("decrypt should throw on tampered ciphertext")
    void decryptTampered() {
        String encrypted = util.encrypt("4532015112830366");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "XXXX";
        assertThatThrownBy(() -> util.decrypt(tampered))
                .isInstanceOf(RuntimeException.class);
    }
}
