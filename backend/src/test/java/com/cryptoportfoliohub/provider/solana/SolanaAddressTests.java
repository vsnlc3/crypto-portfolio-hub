package com.cryptoportfoliohub.provider.solana;

import com.cryptoportfoliohub.domain.solana.SolanaAddress;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SolanaAddressTests {

    @Test
    void acceptsBase58EncodingOfA32ByteAddressIncludingPdaStyleZeroBytes() {
        assertThat(SolanaAddress.isValid("11111111111111111111111111111111")).isTrue();
        assertThat(new SolanaAddress(" 11111111111111111111111111111111 ").value())
                .isEqualTo("11111111111111111111111111111111");
    }

    @Test
    void rejectsInvalidAlphabetAndNon32ByteValues() {
        assertThat(SolanaAddress.isValid("1111111111111111111111111111111")).isFalse();
        assertThat(SolanaAddress.isValid("0OIl1111111111111111111111111111")).isFalse();
        assertThatThrownBy(() -> new SolanaAddress("not-an-address"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
