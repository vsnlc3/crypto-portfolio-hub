package com.cryptoportfoliohub.api;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import com.cryptoportfoliohub.assets.api.AssetDataStatus;
import com.cryptoportfoliohub.assets.api.AssetsResponse;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.assertThat;

class DecimalStringSerializerTests {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void serializesBigDecimalApiFieldsAsPlainBaseTenStrings() throws Exception {
        AssetsResponse.Price price = new AssetsResponse.Price(
                new BigDecimal("1E-8"), "USD", "COINGECKO", null, AssetDataStatus.COMPLETE, null);
        String json = objectMapper.writeValueAsString(price);

        assertThat(json).contains("\"amount\":\"0.00000001\"");
        assertThat(json).doesNotContain("\"amount\":1.0E-8");
    }
}
