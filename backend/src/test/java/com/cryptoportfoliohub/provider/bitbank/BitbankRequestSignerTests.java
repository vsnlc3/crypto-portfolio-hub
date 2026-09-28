package com.cryptoportfoliohub.provider.bitbank;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BitbankRequestSignerTests {

    @Test
    void matchesOfficialAccessTimeWindowHmacSha256Example() {
        String signature = BitbankRequestSigner.sign(
                "hoge".getBytes(StandardCharsets.UTF_8),
                1_721_121_776_490L,
                1_000,
                "/v1/user/assets");

        assertThat(signature).isEqualTo("9ec5745960d05573c8fb047cdd9191bd0c6ede26f07700bb40ecf1a3920abae8");
    }
}
