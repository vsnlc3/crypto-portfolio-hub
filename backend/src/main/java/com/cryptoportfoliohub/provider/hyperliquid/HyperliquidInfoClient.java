package com.cryptoportfoliohub.provider.hyperliquid;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.marketdata.MarketDataHttpErrors;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class HyperliquidInfoClient {

    private static final String INFO_URL = "https://api.hyperliquid.xyz/info";
    private final RestClient restClient;

    public HyperliquidInfoClient(@Qualifier("hyperliquidInfoRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public JsonNode query(Map<String, Object> request) {
        final JsonNode body;
        try {
            body = restClient.post().uri(INFO_URL).body(request).retrieve().body(JsonNode.class);
        } catch (HttpStatusCodeException exception) {
            throw new ProviderException(MarketDataHttpErrors.forStatus(exception.getStatusCode()));
        } catch (ResourceAccessException exception) {
            throw new ProviderException(MarketDataHttpErrors.forTransport(exception));
        } catch (RestClientException exception) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }
        if (body == null) {
            throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
        }
        return body;
    }
}
