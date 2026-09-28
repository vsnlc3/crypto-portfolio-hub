package com.cryptoportfoliohub.domain.solana;

import java.math.BigInteger;
import java.util.Objects;

public record SolanaAddress(String value) {

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    public SolanaAddress {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (!isValid(value)) {
            throw new IllegalArgumentException("Invalid Solana address.");
        }
    }

    public static boolean isValid(String value) {
        if (value == null || value.length() < 32 || value.length() > 44) {
            return false;
        }
        BigInteger decoded = BigInteger.ZERO;
        for (int index = 0; index < value.length(); index++) {
            int digit = BASE58_ALPHABET.indexOf(value.charAt(index));
            if (digit < 0) {
                return false;
            }
            decoded = decoded.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit));
        }
        int leadingZeroBytes = 0;
        while (leadingZeroBytes < value.length() && value.charAt(leadingZeroBytes) == '1') {
            leadingZeroBytes++;
        }
        int significantBytes = decoded.signum() == 0 ? 0 : (decoded.bitLength() + 7) / 8;
        return leadingZeroBytes + significantBytes == 32;
    }
}
