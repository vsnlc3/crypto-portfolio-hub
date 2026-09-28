package com.cryptoportfoliohub.provider.bitbank;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class BitbankRequestSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private BitbankRequestSigner() {
    }

    static String sign(byte[] apiSecret, long requestTimeMillis, int timeWindowMillis, String pathAndQuery) {
        byte[] message = ("%d%d%s".formatted(requestTimeMillis, timeWindowMillis, pathAndQuery))
                .getBytes(StandardCharsets.UTF_8);
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(apiSecret, HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign provider request.");
        }
    }
}
