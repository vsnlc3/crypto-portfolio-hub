package com.cryptoportfoliohub.provider.solana;

import com.cryptoportfoliohub.domain.solana.SolanaAddress;
import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.marketdata.MarketDataHttpErrors;
import com.cryptoportfoliohub.provider.solana.config.SolanaProviderProperties;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@Component
public class HeliusClient {

    private static final String HELIUS_BASE_URL = "https://mainnet.helius-rpc.com";
    private final RestClient restClient;
    private final SolanaProviderProperties properties;

    public HeliusClient(@Qualifier("solanaRestClient") RestClient solanaRestClient,
            SolanaProviderProperties properties) {
        this.restClient = solanaRestClient;
        this.properties = properties;
    }

    public SignaturePage fetchSignaturePage(SolanaAddress address, String cursor, int limit) {
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("Helius history page limit must be between 1 and 1000.");
        }
        Map<String, Object> options = new java.util.HashMap<>();
        options.put("transactionDetails", "signatures");
        options.put("sortOrder", "desc");
        options.put("commitment", "finalized");
        options.put("limit", limit);
        options.put("filters", Map.of("status", "any", "tokenAccounts", "balanceChanged"));
        if (cursor != null && !cursor.isBlank()) {
            options.put("paginationToken", cursor);
        }
        JsonNode root = post("/", Map.of(
                "jsonrpc", "2.0",
                "id", "1",
                "method", "getTransactionsForAddress",
                "params", List.of(address.value(), options)));
        JsonNode result = root.path("result");
        JsonNode rows = result.path("data");
        if (!rows.isArray()) {
            throw invalidResponse();
        }
        List<JsonNode> signatures = new ArrayList<>();
        for (JsonNode row : rows) {
            String signature = text(row.path("signature"));
            if (signature.isBlank()) {
                throw invalidResponse();
            }
            signatures.add(row);
        }
        String nextCursor = text(result.path("paginationToken"));
        return new SignaturePage(signatures, nextCursor.isBlank() ? null : nextCursor);
    }

    public List<JsonNode> fetchParsedEvents(List<String> signatures) {
        if (signatures == null || signatures.isEmpty()) {
            return List.of();
        }
        if (signatures.size() > 100 || signatures.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Helius Parsed Events accepts 1 to 100 signatures per request.");
        }
        JsonNode root = post("/v1/parsed-events/transactions", Map.of(
                "transactions", List.copyOf(signatures),
                "commitment", "finalized"));
        if (!root.isArray() || root.size() != signatures.size()) {
            throw invalidResponse();
        }
        List<JsonNode> events = new ArrayList<>();
        for (JsonNode event : root) {
            if (text(event.path("signature")).isBlank()) {
                throw invalidResponse();
            }
            events.add(event);
        }
        return List.copyOf(events);
    }

    private JsonNode post(String path, Object requestBody) {
        String apiKey = properties.getHeliusApiKey();
        if (apiKey.isBlank()) {
            throw new ProviderException(ProviderErrorCategory.UNAVAILABLE);
        }
        final URI uri;
        try {
            uri = UriComponentsBuilder.fromUriString(HELIUS_BASE_URL)
                    .path(path)
                    .queryParam("api-key", apiKey)
                    .build()
                    .encode()
                    .toUri();
        } catch (RuntimeException exception) {
            throw invalidResponse();
        }

        final JsonNode response;
        try {
            response = restClient.post().uri(uri).body(requestBody).retrieve().body(JsonNode.class);
        } catch (HttpStatusCodeException exception) {
            throw new ProviderException(MarketDataHttpErrors.forStatus(exception.getStatusCode()));
        } catch (ResourceAccessException exception) {
            throw new ProviderException(MarketDataHttpErrors.forTransport(exception));
        } catch (RestClientException exception) {
            throw invalidResponse();
        }
        if (response == null) {
            throw invalidResponse();
        }
        if (response.isObject() && response.hasNonNull("error")) {
            throw invalidResponse();
        }
        return response;
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }

    private static ProviderException invalidResponse() {
        return new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
    }

    public record SignaturePage(List<JsonNode> signatures, String nextCursor) {
        public SignaturePage {
            signatures = List.copyOf(signatures);
        }
    }
}
