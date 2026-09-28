package com.cryptoportfoliohub.provider.solana;

import com.cryptoportfoliohub.error.ProviderErrorCategory;
import com.cryptoportfoliohub.error.ProviderException;
import com.cryptoportfoliohub.domain.solana.SolanaAddress;
import com.cryptoportfoliohub.provider.ActivityPage;
import com.cryptoportfoliohub.provider.ActivityProvider;
import com.cryptoportfoliohub.provider.NormalizedActivity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class SolanaActivityProvider implements ActivityProvider {

    private final HeliusClient heliusClient;
    private final SolanaActivityNormalizer normalizer;

    public SolanaActivityProvider(HeliusClient heliusClient, SolanaActivityNormalizer normalizer) {
        this.heliusClient = heliusClient;
        this.normalizer = normalizer;
    }

    @Override
    public ActivityPage fetchActivities(String accountAddress, String cursor, int limit, Instant fromInclusive) {
        SolanaAddress address = new SolanaAddress(accountAddress);
        HeliusClient.SignaturePage page = heliusClient.fetchSignaturePage(address, cursor, limit, fromInclusive);
        if (page.signatures().isEmpty()) {
            return new ActivityPage(List.of(), page.nextCursor());
        }

        List<String> signatures = page.signatures().stream()
                .map(row -> text(row.path("signature")))
                .toList();
        Map<String, JsonNode> eventsBySignature = new HashMap<>();
        for (int start = 0; start < signatures.size(); start += 100) {
            List<String> batch = signatures.subList(start, Math.min(start + 100, signatures.size()));
            for (JsonNode event : heliusClient.fetchParsedEvents(batch)) {
                String signature = text(event.path("signature"));
                if (eventsBySignature.putIfAbsent(signature, event) != null) {
                    throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
                }
            }
        }

        List<NormalizedActivity> activities = new ArrayList<>();
        for (JsonNode signatureRow : page.signatures()) {
            String signature = text(signatureRow.path("signature"));
            JsonNode event = eventsBySignature.get(signature);
            if (event == null) {
                throw new ProviderException(ProviderErrorCategory.INVALID_RESPONSE);
            }
            activities.add(normalizer.normalize(address.value(), signatureRow, event));
        }
        return new ActivityPage(activities, page.nextCursor());
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText();
    }
}
