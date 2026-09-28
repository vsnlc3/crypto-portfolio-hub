package com.cryptoportfoliohub.provider;

import java.util.List;

public interface BalanceProvider {
    List<NormalizedAssetBalance> fetchBalances(String accountAddress);
}
