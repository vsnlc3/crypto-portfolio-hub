# Provider仕様確認

最終確認日: 2026-09-28

本書は公式に公開されたProvider仕様と、その仕様から確定できるAdapter上の制約を記録する。APIドキュメントに明記されない挙動は推測で確定せず、「実装前の確認事項」に残す。Providerの生レスポンス、Credential、個人情報をログへ出さない。

## Market Data (Step 4-4)

### 採用方針

- Spot assetのCrypto priceと24h market quoteにはCoinGecko Demo APIの`GET /coins/markets`を使い、1リクエストにMVPの必要Coin IDをまとめる。要求通貨はUSD、`price_change_percentage=24h`、`precision=full`とする。
- responseの`current_price`はUSD建て単価、`price_change_percentage_24h`は過去24時間の変化率、`last_updated`はそのMarket dataの時刻として扱う。Provider識別値は`COINGECKO`。Price Currencyは`USD`。
- CoinGecko Demo API keyはBackend Secret `COINGECKO_DEMO_API_KEY`として注入する。Demo APIの`x-cg-demo-api-key`認証headerを使い、query string、Browser、Frontend bundle、ログには出さない。公式PricingではDemo planは100 calls/min、月10,000 calls、Data Freshnessは60 secondsから。実装ではAssetごとに呼ばず一括取得し、ユーザー共有Cacheを初期10分TTLで使う。連続稼働時でも31日で最大約4,464回に抑え、Retryと手動更新用のQuota余裕を残す。
- USD/JPYにはExchangeRate-API Free plan `GET /v6/{API_KEY}/latest/USD`を使う。`conversion_rates.JPY`はUSD 1単位あたりのJPY、`time_last_update_unix`はRateのProvider update時刻、`base_code`はUSDであることを確認して使う。Provider識別値は`EXCHANGERATE_API`。
- FX API keyはBackend Secret `EXCHANGERATE_API_KEY`として注入する。公式資料上Free planは1,500 requests/month、更新は24時間ごと。共有Cacheで更新時刻を見て最大1日1回取得する。KeyがPathに含まれるため、HTTP client / proxy / error logでRequest URIを記録しないか、Key部分を必ずredactする。
- API keyは開発時も`.env`等のGit管理外設定で注入し、DB / Git / Docker imageへ保存しない。未設定ならMarket data capabilityをUnavailableにし、平文値や固定のDummy価格へfallbackしない。

### Coin ID Mapping

CoinGeckoのSymbolは一意でないためsymbol検索ではなくAPI IDを指定する。現行UIのCanonical Assetに対するIDは以下のとおり。

| Application Asset | CoinGecko ID | Notes |
| --- | --- | --- |
| BTC | `bitcoin` | Canonical Bitcoin |
| ETH | `ethereum` | Canonical Ether |
| SOL | `solana` | Canonical Solana |
| XRP | `ripple` | Canonical XRP |
| HYPE | `hyperliquid` | Hyperliquid native HYPE。`hype-3`の別Tokenと混同しない |
| USDC | `usd-coin` | Canonical USDC。Network別Mint / Contractを検証し、別の同名・bridged tokenをSymbolだけで同一視しない |

Provider `asset_key`からCanonical AssetへのMappingが不明なAsset、CoinGeckoにないAsset、複数候補が残るAssetは推測せず価格をUnavailableとする。将来Assetを追加する場合はProvider識別子・network・token address等でIdentityを確定してからMarket IDを追加する。

### Timestamp、鮮度、失敗

- `price_evaluated_at`はCoinGecko `last_updated`、`fx_evaluated_at`はExchangeRate-API `time_last_update_unix`に対応する。HTTP取得時刻とProviderデータ時刻は混同しない。取得時刻はObservability / cache control上で別途保持してよい。
- Application鮮度方針として、暗号資産価格はProvider時刻から15分、日次FXは72時間を超えた値をSTALEとする。これはProviderが保証する更新間隔ではなく、Demo planの更新頻度と共有Cacheを踏まえたUI / Valuation向けのMVP判定値。運用データに応じて設定化する。
- FX取得元通貨と対象通貨が同一の場合はRate 1、Source `IDENTITY`とする。MVP Portfolio換算ではJPY→JPYをidentityとし、USD→JPYは上記FX Rateを使う。
- CoinGeckoまたはFX API障害時に第二Providerへ自動fallbackしない。成功済みresponseは共有Cacheとして利用し、Provider observationからの経過時間が鮮度上限を超えたらSTALEとする。必要なPrice / FXがUnavailableまたは鮮度上限を超え、全体のNet Worthを正しく算出できないときはそのデータを0扱いせず、新しいSnapshotを作らない。前回成功済みの全体Current Stateを再利用する場合は既存方針に従いSTALEとして示す。
- `price_change_percentage_24h`がnull / 欠落 / staleの場合は24h quoteだけをUnavailableにする。現在価格とFXが有効ならPortfolio valuationは継続できる。Comparison periodは`24h`、Quote sourceは`COINGECKO`、quote evaluatedAtはPriceと同じ`last_updated`とする。
- Hyperliquid Perpetual PositionのPosition Value / Unrealized PnLでは、Hyperliquidから取得した当該PositionのMark Price / Provider PnLを使う。Spot向けCoinGecko価格へ置換・fallbackせず、価格通貨ごとのFX換算だけを適用する。
- 429、timeout、5xx、認証・quota errorは別Providerへの切替やゼロ値にせず、Market Data capabilityの失敗状態として記録する。Bounded backoffはRate Limit / Retry共通方針に従い、同じ共有Cache keyへの同時Fetchを抑制する。
- CoinGeckoのDemo planは公式Pricingで「testing and exploration」向けと説明され、Attribution requiredとなっている。Screen Designに従い`Powered by CoinGecko` attributionを読みやすく表示しAPI pageへリンクする。アプリを運営者の組織外のユーザーへ提供する前に、利用プラン・API Terms、User Agreement、Privacy Policy、データの制限事項 / 免責表示を確認し、Demo planが本番提供を許可すると推定しない。

### 参照した公式資料

- [CoinGecko Demo API - Coins List with Market Data](https://docs.coingecko.com/demo/reference/coins-markets)
- [CoinGecko Errors & Rate Limits](https://docs.coingecko.com/docs/errors-and-rate-limits)
- [CoinGecko Demo API Authentication](https://docs.coingecko.com/demo/reference/authentication)
- [CoinGecko API Pricing / Demo Limits and Attribution](https://www.coingecko.com/en/api/pricing)
- [CoinGecko API Terms of Service](https://www.coingecko.com/en/api_terms)
- [CoinGecko API IDの説明](https://www.coingecko.com/learn/coingecko-api-troubleshooting-guide-and-solutions)
- [Bitcoin](https://www.coingecko.com/en/coins/bitcoin), [Ethereum](https://www.coingecko.com/en/coins/ethereum), [Solana](https://www.coingecko.com/en/coins/solana), [XRP](https://www.coingecko.com/en/coins/xrp), [HYPE](https://www.coingecko.com/en/coins/hyperliquid), [USDC](https://www.coingecko.com/en/coins/usd-coin) CoinGecko API ID pages
- [ExchangeRate-API Free plan](https://www.exchangerate-api.com/), [Free API Request Format](https://www.exchangerate-api.com/docs/standard-requests), [Supported Currency Codes](https://www.exchangerate-api.com/docs/supported-currencies)

Step 4-4では公式仕様およびIDを確認し、実価格値を検証Fixtureへ固定しない。Step 6-1ではCredential注入後に両Providerへの実requestを確認した。

### Step 6-1 実装状況

- 2026-09-28にCoinGecko Demo markets / authentication / error limit資料とExchangeRate-API Standard Request資料を再確認し、下記の実装契約と一致することを確認した。
- Backendはこの節のCanonical Asset IDだけをCoinGeckoへ一括送信する。未知・曖昧な`asset_key`は価格Unavailableとし、SymbolだけからCoin IDを推測しない。
- CoinGeckoでは`x-cg-demo-api-key` Header、USD建て`/coins/markets`、`last_updated`を使用する。価格がNULL、評価時刻が不正、またはresponse自体が解釈できない場合はその値を採用せず0にしない。
- USD / JPYはExchangeRate-APIのUSD base responseから`conversion_rates.JPY`と`time_last_update_unix`を検証する。JPY→JPYはProvider呼び出しなしのRate `1` / `IDENTITY`。
- PriceはBackend単一Instance内でユーザー間共有する10分のin-memory Cache、Provider評価時刻から15分をfreshness上限として判定する。FXはresponse更新時刻を保持し、Free planの1日更新を前提に最低24時間ごとの同Cache、72時間をfreshness上限とする。各上限は設定値。複数Backend Instance間のCache共有は将来のDeployment設計事項。
- 429 / timeout等の失敗は分類し、bounded backoffの間は再Fetchしない。前回成功値は評価時刻からSTALE判定し、存在しなければUnavailableとする。別Providerやゼロ値へfallbackしない。
- Provider Key未設定でもBackendは起動できる。CoinGecko Demo keyはHeaderからのみ送信し、ExchangeRate-API keyは要求Pathに含まれるためrequest URIをアプリケーションログ・例外メッセージへ記録しない。
- 2026-09-28、`.env`から注入したCredentialで`MarketDataLiveSmokeTests`を実行し、CoinGecko DemoのCurrent PriceとExchangeRate-APIのUSD/JPY responseを取得してresponse shapeおよびProvider評価時刻の読み取りを確認した（1 test成功）。値とCredentialはログ・文書へ記録しない。

### Step 6-2 実装状況

- CoinGecko `price_change_percentage_24h`をCurrent Priceと同じresponseから読み取り、percentage unit / `H24` periodとして保持する。Sourceは`COINGECKO`、evaluatedAtは同一rowの`last_updated`とする。
- 24h changeがNULL / 欠落の場合もCurrent Priceは利用可能なまま、changeだけUnavailableとする。PriceがSTALEの場合もchangeはUnavailableとし、SnapshotやPrice Historyから変化率を推測しない。
- Fixture Testでquote metadata、NULL change、STALE changeを確認した。Price / Ticker History DBは作成しない。

## bitbank (Step 4-1)

### 参照した公式資料

- [bitbank Private REST API (English)](https://github.com/bitbankinc/bitbank-api-docs/blob/master/rest-api.md)
- [bitbank Private REST API (日本語)](https://github.com/bitbankinc/bitbank-api-docs/blob/master/rest-api_JP.md)
- [bitbank Public REST API / Pair metadata](https://github.com/bitbankinc/bitbank-api-docs/blob/master/public-api.md)
- [bitbank API Keyの発行とAPI仕様](https://support.bitbank.cc/hc/ja/articles/360036234574-API%E3%82%AD%E3%83%BC%E3%81%AE%E7%99%BA%E8%A1%8C%E3%81%A8API%E4%BB%95%E6%A7%98%E3%81%AE%E7%A2%BA%E8%AA%8D%E6%96%B9%E6%B3%95)
- [bitbank公式Lab CLI](https://github.com/bitbankinc/bitbank-lab-cli) — 約定量の単位に関する補助根拠
- [bitbank公式Node SDK](https://github.com/bitbankinc/node-bitbankcc) — Private REST request parametersの補助確認

2026-09-28に公式REST仕様、エラー一覧、公式CLI / SDKを再確認し、Step 7-4 Adapterの契約とfixtureを実装した。

### 接続と認証

- Private REST base URLは `https://api.bitbank.cc/v1`。
- Connection作成で必要な秘密値はAPI KeyとAPI Secret。UIでローカルのConnection名を付けられるが、Provider account identifierの代わりにはしない。
- bitbankの公式Supportは、外部資産管理サービス用API Keyに「参照」のみを選ぶよう案内している。Crypto Portfolio Hubは発注、注文取消、出金、入金確認等の更新系APIを呼び出さない。
- Private APIは `ACCESS-KEY`、`ACCESS-SIGNATURE` と、`ACCESS-REQUEST-TIME` / `ACCESS-TIME-WINDOW` または `ACCESS-NONCE` を使う。署名はAPI SecretによるHMAC-SHA256。GETは `/v1` を含むpathとquery、POSTはJSON bodyを署名対象に含める。両方式を送る場合はTIME-WINDOW方式が優先される。
- TIME-WINDOWのデフォルトは5000ms、最大60000ms。公式資料は5000ms以下を推奨する。AdapterはUTC epoch milliseconds、`requestTime + timeWindow + rawPathAndQuery` のHMAC-SHA256を使い、公式資料の既知署名値と照合するfixture testを持つ。
- HTTP 429 / bitbank error code `10009` はRATE_LIMIT、`10005`はTIMEOUT、API認証コードはAUTHENTICATIONへ分類する。生レスポンス、API Key、API Secretは例外Messageへ含めない。429への自動再送は行わず、Sync側へ安全な失敗として返す。
- 読み取り用の接続確認は署名付き `GET /user/assets` で行う。Providerの生レスポンスはログへ出さない。
- 確認したPrivate REST資料には、API Keyに紐づく安定したbitbank口座IDを返す本人情報Endpointが見当たらない。`/user/assets` はassetごとの行を返す。Withdrawal historyにある `account_uuid` は出金先口座の識別子であり、bitbank口座IDとして使わない。したがって `connections.external_account_ref` はNULLとし、API Key/Secretを識別子として保存・表示しない。

### 残高

`GET /user/assets` はassetごとに次を返す。

| Field | 公式説明 | Adapterでの扱い |
| --- | --- | --- |
| `free_amount` | 利用可能量 | Availableとして保存 |
| `locked_amount` | ロック量 | Lockedとして保存 |
| `onhand_amount` | 保有量 | Providerが返す保有総量として利用し、free / lockedを再加算しない |
| `withdrawing_amount` | ロック量のうち出金中の量 | locked / onhandの内数として扱い、available / locked / totalへ重ねて加算しない |
| `amount_precision` | 数量精度 | 表示・検証情報 |

APIは数量を返すが、JPY評価額や市場価格はこのEndpointからは得られない。評価には後続Stepで選定するMarket Dataを使う。数値は文字列Decimalとして保持し、JavaScriptの浮動小数点へ変換しない。

### Activity / 履歴

| Event | Read API | Provider Event ID / 時刻 | 取得制約 |
| --- | --- | --- | --- |
| Spot trade | `GET /user/spot/trade_history` | `trade_id` / `executed_at` (ms) | `count`最大1000、`since` / `end`、`pair`、`order_id`、`order=asc|desc` |
| Deposit | `GET /user/deposit_history` | `uuid` / `found_at`、任意の`confirmed_at` (ms) | `count`最大100、`since` / `end`、`asset`。asset省略は暗号資産全件、JPYは `asset=jpy` を指定 |
| Withdrawal | `GET /user/withdrawal_history` | `uuid` / `requested_at` (ms) | `count`最大100、`since` / `end`、`asset`。JPYは `asset=jpy` を指定 |

Trade responseにはpair、side、amount、price、maker/taker、base / quote fee、quote accrued fee、任意のrealized PnL / interestがある。Depositは `FOUND` / `CONFIRMED` / `DONE` 状態と `NORMAL` / `MANUAL` / `STAKING` categoryを返す。Withdrawalはamount、fee、network、txid、statusを返す。各時刻はミリ秒で扱う。公式日本語REST資料はtrade / deposit / withdrawalの `since` と `end` をミリ秒と明記している。

Deposit historyはdestination tag、memo、bank accountを返さない。Withdrawal historyは送金先address / tagとJPY出金先の銀行・口座名義情報を含み得るため、Activityに不要なこれらの値を永続化、表示、ログ出力しない。

### Activity Legsと重複排除

- `GET /spot/pairs` の `base_asset` / `quote_asset` を使ってpairを分解する。
- `GET /spot/pairs` の `base_asset` / `quote_asset` でpairを解決する。Trade historyのREST表は `amount` を「注文量」と記載し、単位自体は明記しない。公式CLIの `btc_jpy` 注文例はamountをBTC数量として扱い、買い側のquote必要額を `price × amount` としているため、Adapterではamountをbase数量としてBUY `OUT quote + IN base`、SELL `OUT base + IN quote`へ写像する。この写像はREST表の単独記載ではなく公式CLIの注文例を補助根拠とする推論である。fixtureでbase / quote計算とDecimal精度を固定した。実アカウントのRead-only Smoke Testで取引履歴を照合するまでは、Providerの生Amount単位が独立仕様として保証されたとは扱わない。
- `fee_amount_base` / `fee_amount_quote` は対応する資産の独立した `FEE` Legにする。Spotでは `fee_occurred_amount_quote` が `fee_amount_quote` と同値と公式資料にあるため、これを追加のFeeとして二重計上しない。
- Depositはasset / amountの `IN` Leg、Withdrawalはasset / amountの `OUT` Legとして扱う。Withdrawal responseにはfee値があるが、その通貨単位を現行REST資料で確認できないため、推測した `FEE` Legは作らずunavailableのままにする。Tradeの`fee_amount_base` / `fee_amount_quote` は通貨が明示されるためFee Legsへ写像する。status変更は同じProvider eventの更新として扱う。
- Headerの `provider_event_id` にはtradeの `trade_id`、deposit / withdrawalの `uuid` を文字列化して保存する。`dedup_key` はConnectionとevent種別を含め、例として `trade:<trade_id>`、`deposit:<uuid>`、`withdrawal:<uuid>` を使う。公式資料はIDの全口座横断一意性を保証していないため、異なるConnection間でIDを共有しない。
- Trade APIは `position_side`、`profit_loss`、`interest` など信用取引向けの値も返す。MVPではbitbankの信用建玉をPerpetualとして扱わず、`position_side`のある履歴を通常のspot BUY / SELLへ誤変換しない。専用要件が決まるまでは元種別を保った未対応イベントとして扱う。

### Pagination、Rate Limit、履歴範囲

- Trade / deposit / withdrawal APIはcount上限とミリ秒 `since` / `end` 時間条件を公開する。REST資料にはoffset / cursorはなく、deposit / withdrawalの並び順指定も記載されていない。公式Node SDKにはdeposit / withdrawalの`order` request例があるがREST契約にないためAdapterは依存しない。
- count上限へ達したwindowはミリ秒範囲を再帰分割する。境界のinclusive / exclusiveは公式REST資料で確認できないため、各queryを境界の前後1ms広げ、受信後に各windowの範囲へfilterし、dedup keyで重複を防ぐ。1つのミリ秒に上限件数が集中する場合、またはendpointあたり64 requestの上限内に完全取得できない場合は成功として扱わず、Activity Capabilityを失敗させる。欠けた履歴を完全なものとして表示しない。
- REST資料は取得可能な最古の日付や保存期間を定義していない。MVPでAPIが提供する履歴の範囲を超えて存在すると約束しない。
- 公式Rate Limitはユーザー単位・秒単位で、通常QUERYは10回/秒、UPDATEは6回/秒。超過時はHTTP 429またはerror code `10009`。Adapterはread-only QUERYだけを使い、HTTP/APIエラーはRATE_LIMITとして失敗状態へ渡す。Adapter自体は自動再試行しない。
- Deposit responseのamountはParameter表でnumberと記載される一方、response例ではstring。ParserはDecimal精度を失わず、数値JSON fixtureで確認する。Withdrawal responseはamount / feeをstringとして受ける。

### 設計影響

- `database-design.md` の任意 `external_account_ref` はbitbankでNULLを許容するためSchema変更不要。
- `activities` + `activity_legs` はtrade、deposit、withdrawalとFeeを表現できるためSchema変更不要。
- Connector入力はAPI Key / API SecretだけをProvider資格情報とし、識別情報が必要ならローカル表示名にする。read-only権限を画面説明と接続手順に明記する。

### Step 7-5 Sync実装

- Syncは既存のConnection単位のManual Sync APIを使い、`BALANCE`と`ACTIVITY`を別Capabilityとして実行・記録する。Provider呼出しとDB保存の間に長時間Transactionを保持しない。各Capabilityの`lastAttemptAt` / `lastSuccessAt`、Sync Run / Result、失敗状態は共通Sync lifecycleで管理する。
- bitbank RESTは最古の取得可能日を定義していない。Application policyとして初回Activity queryをSync受付時刻から遡る90日に限定し、2回目以降はActivity Capabilityの前回成功時刻から1時間前をinclusiveに再取得する。queryの終了時刻はSync受付時刻とする。この期間をProviderが保証する履歴保存期間とは扱わない。
- Activity APIにcursorはなく、取得可能なwindowはAdapterが件数上限に応じて時間分割する。全windowを上限内に取得できた場合だけCapabilityを成功にする。続きのProvider cursorは保存しないため、正常完了時の`continuationAvailable`は`false`。1ミリ秒windowでも上限を解消できない場合やendpoint当たりのrequest budgetを超えた場合はActivityを失敗にし、前回履歴を保持する。
- BalanceはAdapterの全responseを検証してから単一DB TransactionでConnectionのCurrent Balance集合を置換する。成功した空集合は現在残高なしを示す。Provider取得または保存に失敗した場合は削除・置換をRollbackし、前回成功したBalanceとその`last_success_sync_run_id`を保持する。`onhand_amount`をtotal、`free_amount` / `locked_amount`を別列へ保存し、`withdrawing_amount`は重複加算しない。
- Activity Headerとその全Legは同一DB Transactionで保存する。`dedup_key`をConnection / User内で再利用し、重複SyncではHeaderやLegを増やさない。同じProvider Eventのstatusが進んだ場合はHeaderを更新し、既存Legは維持する。保存途中のLeg制約違反等では同じTransactionをRollbackし、部分Activityを残さない。
- `original_amount` / `original_currency`にはAdapterが取得した資産額と通貨を保存する。価格・FXはこのStepで適用しないため、`jpy_value`とvaluation metadataはNULL / `UNAVAILABLE`のままとする。取得不能値を0として補わない。
- Credential、Balance、Activity、Sync情報へのQueryと保存は`authenticated user id`を含める。所有者が異なるConnectionへのSyncは404とし、Providerへ渡さない。Activity Legの所有権は親Activity経由で検証する。

## Solana (Step 4-2)

公式資料のAdapter契約は2026-09-28に再確認した。Step 7-2ではFixture / Mock HTTPで契約を実装検証し、HeliusへのLive requestは行っていない。

### 参照した公式資料

- [Solana Account / Address](https://solana.com/docs/core/accounts) — Address形式、Account、PDA
- [Solana `getBalance`](https://solana.com/docs/rpc/http/getbalance)
- [Solana `getTokenAccountsByOwner`](https://solana.com/docs/rpc/http/gettokenaccountsbyowner)
- [Solana `getSignaturesForAddress`](https://solana.com/docs/rpc/http/getsignaturesforaddress)
- [Solana `getTransaction`](https://solana.com/docs/rpc/http/gettransaction)
- [Solana RPC JSON Structures](https://solana.com/docs/rpc/json-structures) — Token Balance、Transaction Metadata
- [Solana SPL Token Basics](https://solana.com/docs/tokens/basics) — Token Program / Token-2022
- [Metaplex Token Metadata](https://www.metaplex.com/docs/smart-contracts/token-metadata)
- [Solana Public RPC Endpoints](https://solana.com/docs/references/clusters)
- [Helius `getTransactionsForAddress` API Reference](https://www.helius.dev/docs/api-reference/rpc/http/gettransactionsforaddress)
- [Helius token-account history guide](https://www.helius.dev/blog/solana-token-accounts-history)
- [Helius Parsed Events Quickstart / REST Reference](https://www.helius.dev/docs/parsed-events/quickstart)
- [Helius `getTransactionsForAddress` launch / credit details](https://www.helius.dev/blog/introducing-gettransactionsforaddress)
- [Helius pricing](https://www.helius.dev/pricing)

### AddressとConnection入力

- Solana Account Addressは32-byte値をbase58文字列で表す。Public KeyだけでなくPDAもAddressとして有効であり、PDAには対応する秘密鍵がない。入力検証はbase58 decode後の32-byte形式を確認し、curve上にあることやWalletアプリによる署名を要求しない。
- Connection作成に必要なProvider入力はSolana Wallet Address。アプリ内の表示名は任意のローカル名としてよく、UI上のサービス名はPhantomとして表示できる。Solana RPC clusterはアプリ側でMainnetに固定し、ユーザーに秘密鍵、Seed Phrase、署名、Wallet接続を求めない。
- Addressの形式が正しいことはアドレスの利用権限・所有を証明しない。読み取り専用のPortfolio接続なので秘密鍵によるownership challengeは導入しない。

### Current BalanceとAsset識別

| データ | Provider仕様 | Adapter上の扱い |
| --- | --- | --- |
| Native SOL | `getBalance(address)` はlamportsを返す。1 SOL = 1,000,000,000 lamports | 整数のlamportsからDecimalでSOLへ換算する。`uiAmount`等の浮動小数値を金額計算に使わない |
| SPL Token | `getTokenAccountsByOwner(owner, {programId}, {encoding: "jsonParsed"})` でOwner配下のToken Account一覧を取得する。各Accountにmint、owner、state、`tokenAmount.amount`（整数文字列）、`decimals`がある | Classic Token ProgramとToken-2022 Programを対象とし、Programごとに取得して同一MintのToken Accountを合算する。`amount / 10^decimals`を任意精度Decimalで計算する |
| Asset identity | Mint AddressがTokenを識別する。Mintごとに別Assetとする | `asset_key`は少なくともSolana networkとMintを特定できる形にする。symbol/nameはidentityやdedup keyに使わない |
| Metadata | Token基本情報だけで統一的なsymbol/nameが保証されるわけではない。Metaplex Token MetadataはMintに紐づくPDA上にあり、URI先のJSONを使う場合もある | Metadataは表示補助に限る。欠落・未検証・取得失敗時もMintと残高を保持し、symbol/nameを推測しない。Metadata不在を残高0と扱わない |

- 0 lamportsや空のToken Account応答はProvider取得が成功した場合の実測0として扱い、RPC失敗・Token Programの一部取得失敗とは区別する。取得不能なToken Programがある場合はBalance Capabilityをpartial/errorとし、前回成功Stateをstaleとして保持する。
- Token Account自体が持つlamportsはToken Accountのrent reserve等であり、Token数量ではない。MVPのNative SOL BalanceはWallet Addressへの`getBalance`を使い、Token Accountの`lamports`をSOL残高へ単純加算しない。回収可能なrent reserveをNet Worthへ含めるかは、必要になった段階で別途定義する。
- Wrapped SOLはMintを持つToken Accountとして返る。SOLとWrapped SOLの表示・価格Assetへの統合規則はこのProvider調査だけでは確定しない。各残高はまずMint単位で保存し、価格Asset mappingはMarket Data設計で扱う。

### Activity、分類、Legs

- Solana標準RPCの`getSignaturesForAddress`は指定Addressをaccount keysに含むTransaction Signatureを新しい順に返し、`before` / `until` / `limit`を受け取る。ただしWallet Addressだけを指定した標準RPC queryでは、Wallet配下のToken Accountだけに関係するTransactionを漏らす可能性がある。
- MVPのSolana履歴ProviderはHeliusを採用する。履歴取得はHelius `getTransactionsForAddress`を使い、`filters.tokenAccounts: "balanceChanged"` を基本候補とする（`all`も選択可能）。HeliusはWalletが所有するToken Accountに関係する履歴を含められると説明している。Mainnetの共有Public RPCはrate limitとSLAがないため、本番用履歴基盤には使わない。
- HeliusのgTFAからSignatureをcursorで収集し、署名をHelius Parsed Eventsの`POST /v1/parsed-events/transactions`へ最大100件ずつ渡して分類・Transfer情報を取得する。gTFAはstatusを`any`、`tokenAccounts`を`balanceChanged`とし、1ページ最大1,000件の範囲でcursorを返す。Parsed Events APIはOpen Betaであり、対応プログラム外の命令はraw fallbackとなる。`summary.type`等のProvider分類は根拠として使える場合に限り利用し、未対応・解析失敗は元のイベント種別を保持してunknown/unavailableとして扱う。全DeFi命令の分類を保証しない。
- 1 Transaction Signatureにつき1 Activity Headerを基本とし、Transaction内の複数のWallet移動をLegsへまとめる。Token TransferのMint、Wallet方向、amountとNative SOL TransferをActivity Legsへ写像する。双方の`fromUserAccount` / `toUserAccount`が接続Walletなら内部移動として外部IN / OUTにしない。SWAPとして明示的に解析できた場合、Wallet視点の入力Mintを`OUT`、出力Mintを`IN`として保存する。ルート途中の`inner_swaps`を個々のLegとして足し合わせると、実際のWallet入出金と二重計上するため使用しない。複数Swapや別イベントが同一Transactionに含まれる場合のHeader / Legs集約は、公式fixtureで正規化ルールを確認する。
- ProviderのToken amountはraw integer amountとdecimalsを基に任意精度で換算し、JavaScript floating pointへ変換しない。Parsed Eventsのamount表現も含めて、実装時に大きな整数（2^53超）を使ったfixtureを追加する。正確な数量を復元できないイベントを丸めて保存しない。
- 現行DBのBalance / Activity quantityは`NUMERIC(38,18)`である。Mintの小数桁に従ってDecimal変換し、末尾ゼロを除いても小数桁が18を超える値は丸めて保存せずCapabilityを失敗させる。DB精度を広げる必要性は実データと運用量を確認して別途判断する。
- Solana TransactionのFeeはlamports建て。Providerが返す`fee` / `feePayer`を確認し、接続WalletがFee payerである場合だけ、SOL数量に換算した`FEE` Legを作る。別WalletがFeeを払ったTransactionのFeeを接続Walletへ帰属させない。
- Failed TransactionもActivity状態として識別する。失敗Transactionは状態・Signatureを保持できるが、revertされたIN / OUTを成功移動としてLeg化しない。接続WalletがFee payerで実際にFeeを負担した場合は`FEE`のみ記録する。
- HeaderのProvider Event IDはSolana Transaction Signature、dedup keyはConnection IDとSignatureを含める（例: `transaction:<signature>`）。異なるWallet Connection間で同一Signatureが観測されても、Connectionごとに履歴を分離する。
- Solana `blockTime`はNULLになり得る。Slotから推測時刻を作らず、時刻を確定できないイベントはtimestamp再取得を試み、確定できない間はActivity Capabilityをpartial/unavailableとして扱う。DBの`activities.occurred_at`が必須である現行Schemaへ架空時刻を保存しない。
- Helius Parsed EventsのサンプルではSwapのinput/output Mint・amount、token/native transfer、fee、status、signatureが返る。Parserの分類や金額がWallet視点の実入出金と一致することを代表的なTransfer / Swap / failed transactionのfixtureで検証するまで、parsed description文字列を台帳データとして扱わない。

### Pagination、履歴範囲、Rate Limit、費用

- Helius gTFAは`transactionDetails: "signatures" | "full"`、昇順/降順、時間/slot/status filters、`paginationToken`を持つ。現在のAPI Referenceでは1 requestあたり最大1,000件。`tokenAccounts` filterは`none` / `balanceChanged` / `all`。履歴ページ取得はcursorがなくなるまで継続し、ページ境界と再開位置を保存する。
- HeliusはgTFAのArchival DataでGenesis以降の履歴を取得できると案内する。ただしサービス・プラン・API機能の提供条件に依存する。標準Solana RPCのTransaction dataはNodeごとの保持状況に依存し、Solana公式はPublic RPCを本番用に推奨していない。MVPの初回Backfill範囲は直近90日とし、1回のmanual syncでは100件まで取得する。cursorが続く場合は同じ90日範囲を後続manual syncで再開し、範囲を完了するまでは全履歴を取得済みと表示しない。取得途中の失敗・上限到達をActivity completeとして報告しない。
- Helius現在のPricingページはFree $0 / 月・1M credits・10 RPC requests/s、Developer $49 / 月・10M credits・50 RPC requests/sを掲載する。HeliusのgTFA公開記事は1 callあたり100 credits、利用可能プランはpaidと説明する。これらは単一API Key / ProjectのProvider制限であり、Userごとに増えるQuotaではない。Rate limit / credits超過はbounded backoff後に失敗・partial状態とし、API Keyをログへ出さない。
- Parsed Events Quickstartは現在、全プランで1 requestあたり10 creditsと記載する。一方Helius Pricingページには「paid planで2026-09-21まで無料、その後の最終credit costは変更され得る」と残っており、2026-09-27時点で公式資料間に料金表記の不一致がある。Provider統合直前にDashboard / 最新公式PricingでgTFA利用可能プラン、Parsed Events費用、共通RPS制限を再確認する。MVP導入はAWS等の有料Infrastructureを増やさず、初期はHeliusの利用量を監視し、必要な場合だけ有料Planを判断する。
- API KeyはユーザーごとのWallet Credentialではなく、Backendが保護して使うApplication Provider Secret。ローカルはGit管理外の環境変数、本番はDeploymentで確定する保護されたSecret設定から注入する。ConnectionにはWallet Addressとローカル表示名のみ保存する。

### 実装前確認事項

- 定期同期間隔は未確定。手動Syncの現行動作へ定期実行を追加する場合は別途決める。
- Helius Parserが認識しないProgram、Token-2022 extension、複合Transactionの分類・Legの境界をfixtureで検証する。未解析のイベントをSWAPや送金として推測分類しない。
- Helius Parsed Events料金の公式表示が複数ページで不一致のため、Step 7実装で実APIキーを使う前に最新Plan / credits / rate limitを再確認する。外部Keyが未設定でもMock / fixtureによるAdapter実装・テストを先に進める。
- Token Metadata URIの安全な取得・更新頻度、未知Mintの価格Provider mappingはこの仕様確認の範囲外とし、MetadataをPortfolio valuationの必須条件にしない。

### Step 7-2 Adapter実装

- Native SOLはSolana Mainnet `getBalance` のlamportsを整数として取得し、`BigDecimal`でSOLへ換算する。共通`asset_key`は価格Mapping済みCanonical IDの`SOL`、networkは`SOLANA`、asset referenceは`NATIVE`とする。SPL / Token-2022はProgramごとに`getTokenAccountsByOwner`を呼び、同じMintをraw整数・decimalsで加算する。Token Accountのlamportsは加算しない。
- 標準RPCの`jsonParsed` responseにはtoken amountのraw amountとdecimalsはあるが、一般的なsymbol / nameは保証されない。共通Balance modelではsymbol / nameをNULLに保ち、Mintを識別子とする。DBの`asset_balances.symbol`はNOT NULLのため、Step 7-3で保存する際にProvider symbolがないAssetには汎用表示値`TOKEN`を使い、asset identityや価格Mappingには使わない。
- Helius履歴は`getTransactionsForAddress`のSignature page単位で取得し、同じpageのSignatureを最大100件ずつParsed Eventsへ送る。1ページ内のActivityを正規化して返し、next cursorを保持する。Adapter自身は初回Backfill範囲を決めず、全履歴を自動走査しない。
- 取引transferはWallet視点でtoken / native transferをIN / OUTへ変換する。Providerが`summary.type=swap`と示し、実TransferにWalletからのOUTとWalletへのINの両方がある場合だけ`SWAP`にする。route内swap詳細は加えない。transferのどちらか一方向だけ、または他の明示分類のない移動は`TRANSFER`とする。Connection Wallet間の内部transferはActivity Legを作らない。
- Failed Transactionには成功扱いの移動Legを作らず、Walletが実際のFee payerなら正のFeeだけSOL `FEE` Legとして加える。Parser失敗時も正確なblockTimeがSignature pageまたはParsed Eventにある場合は分類不能の`OTHER` Headerを返せる。両方に時間がなければ架空の時刻を作らずActivity pageを失敗させる。
- Helius API keyは`HELIUS_API_KEY`からBackendだけへ注入する。キー未設定時はActivity取得を`UNAVAILABLE`にし、BalanceのSolana RPC取得やBackend起動は妨げない。API keyはHelius URLのquery parameterなので、request URL、例外cause、生responseをログへ出さない。3秒接続 / 5秒読取timeoutを使い、HTTP 429は`RATE_LIMIT`、timeoutは`TIMEOUT`として即時retryせずSync層へ返す。Mainnet RPC URLは`SOLANA_RPC_URL`で運用設定できるが、別clusterへ切り替えない。
- Addressのbase58 decode後32 bytes検証は既存Connection入力とAdapterで共通化した。curve判定や署名は要求しない。
- Fixture TestではMainnet RPCとHelius形式のMock responseを使い、u64 lamports、2^53超raw token amount、Program横断Mint合算、Swap、failed Transaction、Fee payer、pagination cursor、Helius rate limitを確認した。実Helius API CredentialによるLive Smoke Testは行っていない。

### Step 7-3 Sync実装

- 初回Activity query windowはmanual sync受付時刻から90日前。Helius Signature pageとParsed Eventsは1回のmanual syncで最大100件処理する。ページにcontinuation cursorがあれば、そのopaque cursorと元のquery-window startを`connection_sync_states`へ保存し、次のmanual syncで同じ範囲を再開する。
- 初回90日windowを完了した後は、Activity Capabilityの前回成功時刻から1時間前をinclusiveに取得開始時刻とする。再取得分はConnectionごとのprovider event / dedup keyで除外する。前回Activity取得が失敗した場合はcursor・query-window start・last-success timeを進めない。
- Balance CapabilityはSOL、Classic Token、Token-2022の全応答とparseが成功してから単一Transaction内でCurrent Balance集合を置換する。部分応答・失敗時は既存Balanceを保持する。
- BalanceとActivityはCapability別に実行・保存する。Activity Header / Legsは同一Transactionで保存し、Activity失敗で成功済みBalanceをrollbackしない。historical Activity valuationは取得できたoriginal amount / currencyだけを保持し、価格・FXがないLegはNULL / UNAVAILABLEのままにする。
- `continuationAvailable`だけをSync APIへ返し、Helius cursorやquery-window timestampは公開しない。各取得・保存Queryはauthenticated user idでscopedし、HistoryはConnection所有者にだけ返す。

## Hyperliquid (Step 4-3)

### 参照した公式資料

- [Hyperliquid Info Endpoint](https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint)
- [Hyperliquid Exchange Endpoint](https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/exchange-endpoint) — `userSetAbstraction`で定義されるMode値
- [Spot Info Endpoints](https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint/spot)
- [Perpetuals Info Endpoints](https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/info-endpoint/perpetuals)
- [Account abstraction modes](https://hyperliquid.gitbook.io/hyperliquid-docs/trading/account-abstraction-modes)
- [Contract specifications](https://hyperliquid.gitbook.io/hyperliquid-docs/trading/contract-specifications)
- [Margining](https://hyperliquid.gitbook.io/hyperliquid-docs/trading/margining)
- [Funding](https://hyperliquid.gitbook.io/hyperliquid-docs/trading/funding)
- [Robust price indices](https://hyperliquid.gitbook.io/hyperliquid-docs/trading/robust-price-indices)
- [Portfolio graphs](https://hyperliquid.gitbook.io/hyperliquid-docs/trading/portfolio-graphs)
- [Rate limits and user limits](https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/rate-limits-and-user-limits)
- [WebSocket response types](https://hyperliquid.gitbook.io/hyperliquid-docs/for-developers/api/websocket/subscriptions) — `tid` uniqueness note

Hyperliquid公式Info Endpointは `userAbstraction` Queryと、`unifiedAccount` / `portfolioMargin` / `disabled` / `default` / `dexAbstraction` のResponse値を現在掲載している。Info EndpointのMode列挙は確認済み。開発環境からMainnet Info APIへ送ったread-only確認RequestはDNS解決に失敗したため、実APIレスポンスは確認できていない。実データ接続時も列挙外・NULL・取得失敗を許容し、未知値を追加Mappingしない。

### ConnectionとRead API

- Hyperliquid Mainnet Read APIは`POST https://api.hyperliquid.xyz/info`の公開JSON API。確認したbalance、position、history endpointsはConnectionごとのAPI KeyやSecretを要求せず、入力は実際のAccount Address。
- Addressは`0x`付き42文字のhexadecimal address。Master AccountまたはSub-accountを接続する場合は、照会したいAccountそのもののAddressを指定する。Agent Wallet Addressでは空結果となり得る。Private Key / Agent Secretは要求・保存しない。
- Endpointの`user` Address形式だけでは、入力がアプリ上でユーザー本人のAccountだと証明されない。読み取り専用接続ではSecretによる署名challengeを追加しない。

### Spot BalanceとAsset識別

- `spotClearinghouseState`は`balances[]`として`coin`、`token`、`hold`、`total`、`entryNtl`を返す。`spotMeta`の`tokens[]`にある`index` / `tokenId` / `name`を参照し、`token` indexとAsset identityを解決する。Spot pairの`@{index}`はSpot MarketのUniverse indexであり、Token IDとして使わない。
- Asset identityは表示用`coin`文字列単独ではなく、Hyperliquid networkとSpot Tokenの`tokenId`を基本にする。Symbol remappingや同名tokenがあっても別Tokenを誤結合しない。
- 公式Spot Info Endpointは `spotClearinghouseState` を「token balances」と定義し、Balanceごとに`total`と`hold`を返す。Net Worthの数量にはProviderがBalanceとして返す`total`を使い、`hold`は利用可能額等の状態情報として保持する。`total + hold`を残高として加算しない。公式ページは両値の包含関係を文章で詳述していないため、非ゼロholdを含むProvider fixtureでtotalを二重加算しないことを検証する。
- 通貨ごとの数量は文字列Decimalとして扱う。`spotMetaAndAssetCtxs`等の価格データやMarket Data IDとの対応はStep 4-4で決める。

### Perpetual Position、Price、Margin、PnL

- `clearinghouseState(user, dex)`は`assetPositions`と`marginSummary` / `crossMarginSummary`を返す。Position fieldには`coin`、signed `szi`、`entryPx`、`leverage.type/value`、`liquidationPx`、`marginUsed`、`positionValue`、`unrealizedPnl`がある。`metaAndAssetCtxs(dex)`は各Perp Marketの`markPx`等を返すため、正しいDEXとMarket metadataで結合する。欠損`liquidationPx`やmark priceを0で補わない。
- Stable Position Keyは少なくとも`connection_id + perp_dex + coin`とする。HIP-3のMarket名は`dex:coin`となり得るため、`coin`だけで異なるDEXのPositionをまとめない。`szi`符号は方向、絶対値は数量として扱う。Position ValueはRequirementsどおり`abs(szi × Mark Price)`をPrice Currencyで計算し、JPY換算した値もNet Worthには加えない。
- Hyperliquidの最初のPerp DEXのContract Specificationsでは、Perpetualは主にUSDC-margined、USDT建てのlinear contractsであり、USDC/USDTの換算はPnLへ適用されない。Price単位とCollateral / PnL単位は同じと仮定しない。現在記載される例外のPURR-USD / HYPE-USDは価格をUSDC建てとする。HIP-3はDEXごとのCollateral token / contract仕様を見て単位を解決し、symbol名だけから推定しない。
- Contract Specificationsから推定される最初のPerp DEXのEntry / Mark / Liquidation PriceはContractのQuote Currency（主にUSDT、記載例外はUSDC）、`marginUsed`とPerpetual account values、`unrealizedPnl`はUSDC建てである。ただしInfo Response field自体にはCurrency fieldがないため、この対応はContract仕様に基づく推定としてMarketごとに照合する。JPY換算では対応するCurrencyごとのFXを記録する。契約単位を確定できないHIP-3 PositionはJPY評価をunavailableとする。
- APIの`unrealizedPnl`をPositionごとに取得できる。HyperliquidのPortfolio graph仕様ではAccount ValueがCross / Isolated PositionのUnrealized PnLを含むと説明される。`marginUsed`はPositionに割り当てられたMarginであり、Account EquityまたはSpot Balanceに加算する別の資産として扱わない。Position ValueもNet Worthへ加算しない。

### Account ModeとNet WorthのMapping

Hyperliquid公式資料はSpot BalanceとPerp Equityの関係をAccount abstraction mode別に定義している。

| Account Mode | 公式説明 | 取得上の影響 |
| --- | --- | --- |
| Standard / Manual | SpotとPerp Balance、各DEXのBalanceが分離する | Spot残高と各DEX Perp Account Equityを別々に数える必要がある |
| Unified Account | 各Asset BalanceをSpotとPerp collateralで共有する | `spotClearinghouseState`が全Balance / Holdのsource of truth。Per-DEX `clearinghouseState`を別資産残高として足せない |
| Portfolio Margin | 対象Assetを含む一つのPortfolioにSpotとPerpsを統合する | `spotClearinghouseState`が全Balance / Holdのsource of truth。Per-DEX account valueの単純加算はできない |

- Info Endpointの `userAbstraction` を接続先Account Addressごとに取得し、次のとおり正規化する。

| `userAbstraction` Response | 内部Mode | Net Worth / Capability上の扱い |
| --- | --- | --- |
| `disabled` | `STANDARD` | Exchange Endpointでは抽象化を無効にする設定値。Account abstraction modes資料のStandard / ManualがSpotと各DEXのPerp Balanceを分離すると説明するため、そのStandard相当として扱う（資料を組み合わせたMapping上の推論）。 |
| `unifiedAccount` | `UNIFIED_ACCOUNT` | Unified / Portfolio Margin用のBalance Sourceとして`spotClearinghouseState`を使う。Perp account balance/equityを加算しない。 |
| `portfolioMargin` | `PORTFOLIO_MARGIN` | Unified / Portfolio Margin用のBalance Sourceとして`spotClearinghouseState`を使う。Perp account balance/equityを加算しない。 |
| `default` | `UNSUPPORTED` | Info Endpointは値を列挙するが、現行Account abstraction modes資料でBalance semanticsを特定できない。Unified等と推測しない。 |
| `dexAbstraction` | `UNSUPPORTED` | 旧HIP-3 DEX abstraction。Account abstraction modes資料が廃止済みとするためMVPでは評価対象外。 |
| 欠落、API失敗、上記以外 | `UNKNOWN` | 新しいNet Worth / Snapshotを算出しない。既存成功Stateがあれば既存stale policyに従う。 |

`disabled`からStandard / Manualへの対応は、Exchange Endpointが `disabled` を設定可能な抽象化値として示し、Account abstraction modes資料がStandard / Manualの別残高を説明することに基づく推論である。Info Endpointの列挙にない`default`を、アプリの画面既定値や過去の既定値から推測してはいけない。

Modeごとの集計:

- `STANDARD`: Spotは`spotClearinghouseState.balances[].total`を使う。Perp DEXごとに`clearinghouseState.marginSummary.accountValue`をAccount Equityとして使い、Account ValueはCross / Isolated PositionのUnrealized PnLを含むためPosition PnLを重ねて加算しない。`marginUsed`、`totalMarginUsed`、Position Valueを資産へ加えない。Spot BalanceとPerp DEX Account Equityは別口座として各通貨のJPY評価を合算する。
- `UNIFIED_ACCOUNT` / `PORTFOLIO_MARGIN`: すべてのHyperliquid Balanceの基準は`spotClearinghouseState.balances[].total`とする。`hold`は表示・利用可能額用の属性として保持し、`total`へ加えない。Perp側の`marginSummary.accountValue`、`crossMarginSummary.accountValue`、collateral/equityをNet Worthへ加えない。Perp Positionは`assetPositions`から表示・Exposureへ使える値だけを扱う。`unrealizedPnl`をNet Worthへ加える場合は、Position単位で重複がなく、当該通貨が解決済みの場合に限り各PnLを一度だけ加える。必要なPosition/PnLが取得不能・曖昧ならNet Worthはunavailableとし、新しいSnapshotを作らない。
- どのModeでも`marginUsed`はPositionに割当済みのMarginであり別資産ではない。Position ValueはNet Worthへ加算しない。
- `meta`のPerp DEX `collateralToken`とSpot Token metadata、および該当Marketの公式Contract Specificationを使ってPerp Account Equity / Margin / PnLの通貨を解決する。通貨やJPY FXが一意に解決できないHIP-3 DEXは評価対象として推測せず、Portfolio全体を算出できない場合は新規Snapshotを作らない。異なるDEXのAccount Equityを単一通貨として足さない。

`provider_account_states`はHyperliquidのAccount ModeとDEX単位のAccount Equityを保持できるよう、`account_scope`を含む複数行Current Stateとして保存する（DB設計 §15）。`account_scope`はAccount全体または個別Perp DEXを区別し、Account ModeとProviderのResponse値も追跡する。

### Activity、Event ID、取得範囲

| Activity | API / Fields | 取得上の扱い |
| --- | --- | --- |
| Spot Fill | `userFills`, `userFillsByTime`; `coin`, `side`, `px`, `sz`, `time`, `hash`, `oid`, `tid`, `fee`, `feeToken`, `builderFee`, `closedPnl` | Spot Marketは`@index`を`spotMeta`へ解決し、従来どおりIN / OUT / FEE legsへ正規化する。`builderFee`は`fee`に含まれるため二重計上しない |
| Perp Fill | `userFills`, `userFillsByTime`; `coin`, `side`, `dir`, `sz`, `px`, `startPosition`, `closedPnl`, `time`, `hash`, `tid`, `fee`, `feeToken`, `builderFee` | Perp約定は`activity_perpetual_fill_details`へ保存し、Spot資産のIN / OUT legsを作らない。実際に口座から差し引かれた正のFeeだけ`FEE` legへ記録する。`builderFee`は`fee`に含まれる |
| Funding | `userFunding`; `delta.coin`, `usdc`, `szi`, `fundingRate`, `hash`, `time` | `usdc`は符号付きの実支払額。正数は残高増加の`IN`、負数は残高減少の`OUT`とする。ゼロなら資産移動legを作らない。費用符号とRate/Positionの関係を公式Funding仕様の例とfixtureで検証する |
| Other Ledger Update | `userNonFundingLedgerUpdates`; `delta`, `hash`, `time` | 公式資料はDeposit、Transfer、Withdrawal等を含むと説明。delta typeとasset / amountを確認してからLeg化し、未対応typeは推測変換しない |

- Fillの`tid`は単独で全時点・全coinに一意とは限らず、公式WebSocket docsはglobally uniqueなtrade idに`(block_time, coin, tid)`を使うよう説明する。Header dedup key候補はConnectionと`time + coin + tid`を含める。集約Fillでは個々のTrade IDとの対応を壊さないようfixture検証する。
- `userFunding` / Ledger Updateは`hash` / `time`とdeltaを返す。複数Market・複数deltaのCollisionを避けるため、event typeとcoin/delta identityもdedup keyへ含め、API再取得fixtureで一意性を検証する。Fundingは公式Response例のsigned `usdc`を保持し、正数を受取、負数を支払としてLeg方向へ変換する。
- Perp FillはContract Exposureの変化であり、Spot TokenのWallet移動ではない。`activities` Headerと`activity_perpetual_fill_details`を1:1で保存し、`activity_legs`にはPerp約定数量のIN / OUTを作らない。`side`はProvider値`B` / `A`をBUY / SELLへ正規化し、Providerの`dir`はポジション効果として方向へmappingする。`startPosition`はfill前のsigned position、`sz`は正のquantity、`px`はprice、`closedPnl`は取得できるsigned実現PnLとして保存する。通貨が確定できない場合は通貨を推測しない。
- Perp Fillの正の取引Feeは`feeToken` / `fee`をAsset leg `FEE`として保存する。負のfee rebateは資産増加の`IN` leg、ゼロfeeはLegなしとし、負数をFEE quantityへ入れない。`builderFee`は合計feeに含まれるため追加Legにしない。
- `userFills`は最大2,000件、`userFillsByTime`は1 Response最大2,000件かつ直近10,000 fillsのみと明記される。したがってSpot/Perp Fillの古い履歴が常に取得できるとは約束しない。
- `userFunding` / `userNonFundingLedgerUpdates`はinclusiveな`startTime` / `endTime`で取得し、公式Info docsの時刻範囲Paginationは最大500要素またはdistinct blocksとして次の`startTime`を使う。全履歴の最古時刻は資料に明記されていない。同一時刻の境界は重複dedupと欠落検証をfixtureで行う。

### Rate Limit

- Hyperliquid REST APIはIP単位で合計1,200 weight / minute。`clearinghouseState`と`spotClearinghouseState`は2 weight、その他多くのInfo queryは20 weight。`userFills`、`userFillsByTime`、`userFunding`等はResponse 20 itemsごとに追加weightがある。
- Weight上限内であっても履歴APIの個別件数上限・直近10,000 fills保持範囲を超えて完全性を約束しない。Rate limit応答・timeout時はbounded backoff後に失敗/partialとし、前回Current State / Activityをstale保持する。

### 実装時の安全条件

- `userAbstraction`の各既知値を上記Mappingへ厳密に分岐する。`default`、`dexAbstraction`、欠落、未知Response、取得失敗をStandardへfallbackしない。
- Unknown / Unsupported Modeでは新しいPortfolio評価を成功扱いにせず、完全なNet Worthを算出できないときはSnapshotを作成しない。過去のModeとStateを使う場合は既存STALE方針に従う。
- `userFills`の集約で一つのResponse fillが複数Trade IDを表す場合、個々のIDとの対応が保証できないものを個別取引Activityに偽装しない。dedupとdetail mappingはfixtureで確認する。
- `userAbstraction`とPerp Fill DetailのRead API仕様は確定した。ネットワーク制限によりLive API responseは未確認のため、API fixture testをAdapter実装Stepで追加する。Live OAuth Smoke Testのような外部確認は通常回帰テストの前提にしない。
