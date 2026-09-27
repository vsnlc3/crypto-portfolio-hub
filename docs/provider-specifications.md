# Provider仕様確認

最終確認日: 2026-09-27

本書は公式に公開されたProvider仕様と、その仕様から確定できるAdapter上の制約を記録する。APIドキュメントに明記されない挙動は推測で確定せず、「実装前の確認事項」に残す。Providerの生レスポンス、Credential、個人情報をログへ出さない。

## bitbank (Step 4-1)

### 参照した公式資料

- [bitbank Private REST API (English)](https://github.com/bitbankinc/bitbank-api-docs/blob/master/rest-api.md)
- [bitbank Private REST API (日本語)](https://github.com/bitbankinc/bitbank-api-docs/blob/master/rest-api_JP.md)
- [bitbank Public REST API / Pair metadata](https://github.com/bitbankinc/bitbank-api-docs/blob/master/public-api.md)
- [bitbank API Keyの発行とAPI仕様](https://support.bitbank.cc/hc/ja/articles/360036234574-API%E3%82%AD%E3%83%BC%E3%81%AE%E7%99%BA%E8%A1%8C%E3%81%A8API%E4%BB%95%E6%A7%98%E3%81%AE%E7%A2%BA%E8%AA%8D%E6%96%B9%E6%B3%95)
- [bitbank公式Lab CLI](https://github.com/bitbankinc/bitbank-lab-cli) — 約定量の単位に関する補助根拠

### 接続と認証

- Private REST base URLは `https://api.bitbank.cc/v1`。
- Connection作成で必要な秘密値はAPI KeyとAPI Secret。UIでローカルのConnection名を付けられるが、Provider account identifierの代わりにはしない。
- bitbankの公式Supportは、外部資産管理サービス用API Keyに「参照」のみを選ぶよう案内している。Crypto Portfolio Hubは発注、注文取消、出金、入金確認等の更新系APIを呼び出さない。
- Private APIは `ACCESS-KEY`、`ACCESS-SIGNATURE` と、`ACCESS-REQUEST-TIME` / `ACCESS-TIME-WINDOW` または `ACCESS-NONCE` を使う。署名はAPI SecretによるHMAC-SHA256。GETは `/v1` を含むpathとquery、POSTはJSON bodyを署名対象に含める。両方式を送る場合はTIME-WINDOW方式が優先される。
- TIME-WINDOWのデフォルトは5000ms、最大60000ms。公式資料は5000ms以下を推奨する。実装ではUTC epoch time、署名対象のquery/bodyバイト列、リクエスト時刻許容範囲を単体テストする。
- 読み取り用の接続確認は署名付き `GET /user/assets` で行う。Providerの生レスポンスはログへ出さない。
- 確認したPrivate REST資料には、API Keyに紐づく安定したbitbank口座IDを返す本人情報Endpointが見当たらない。`/user/assets` はassetごとの行を返す。Withdrawal historyにある `account_uuid` は出金先口座の識別子であり、bitbank口座IDとして使わない。したがって `connections.external_account_ref` はNULLとし、API Key/Secretを識別子として保存・表示しない。

### 残高

`GET /user/assets` はassetごとに次を返す。

| Field | 公式説明 | Adapterでの扱い |
| --- | --- | --- |
| `free_amount` | 利用可能量 | Availableとして保存 |
| `locked_amount` | ロック量 | Lockedとして保存 |
| `onhand_amount` | 保有量 | Providerが返す保有総量として利用し、free / lockedを再加算しない |
| `withdrawing_amount` | ロック量のうち出金中の量 | 別属性として保持できる。locked / onhandへ重ねて加算しない |
| `amount_precision` | 数量精度 | 表示・検証情報 |

APIは数量を返すが、JPY評価額や市場価格はこのEndpointからは得られない。評価には後続Stepで選定するMarket Dataを使う。数値は文字列Decimalとして保持し、JavaScriptの浮動小数点へ変換しない。

### Activity / 履歴

| Event | Read API | Provider Event ID / 時刻 | 取得制約 |
| --- | --- | --- | --- |
| Spot trade | `GET /user/spot/trade_history` | `trade_id` / `executed_at` (ms) | `count`最大1000、`since` / `end`、`pair`、`order_id`、`order=asc|desc` |
| Deposit | `GET /user/deposit_history` | `uuid` / `found_at`、任意の`confirmed_at` (ms) | `count`最大100、`since` / `end`、`asset`。asset省略は暗号資産全件、JPYは `asset=jpy` を指定 |
| Withdrawal | `GET /user/withdrawal_history` | `uuid` / `requested_at` (ms) | `count`最大100、`since` / `end`、`asset`。JPYは `asset=jpy` を指定 |

Trade responseにはpair、side、amount、price、maker/taker、base / quote fee、quote accrued fee、任意のrealized PnL / interestがある。Depositは `FOUND` / `CONFIRMED` / `DONE` 状態と `NORMAL` / `MANUAL` / `STAKING` categoryを返す。Withdrawalはamount、fee、network、txid、statusを返す。

Deposit historyはdestination tag、memo、bank accountを返さない。Withdrawal historyは送金先address / tagとJPY出金先の銀行・口座名義情報を含み得るため、Activityに不要なこれらの値を永続化、表示、ログ出力しない。

### Activity Legsと重複排除

- `GET /spot/pairs` の `base_asset` / `quote_asset` を使ってpairを分解する。
- Trade historyは `buy` / `sell`、`amount`、`price` を返す。公式API資料だけではtrade historyの `amount` 単位が明記されていない。公式Lab CLIの注文例は買い側を `price × amount` のquote資産、売り側をamountのbase資産として扱う。この根拠から、spot Adapterではamountをbase数量と解釈し、BUYを `OUT quote (amount × price) + IN base (amount)`、SELLを `OUT base (amount) + IN quote (amount × price)` とする。これは公開API資料単独で明示された定義ではないため、実装時に公式mock fixture等で検証し、そのテストを残す。
- `fee_amount_base` / `fee_amount_quote` は対応する資産の独立した `FEE` Legにする。Spotでは `fee_occurred_amount_quote` が `fee_amount_quote` と同値と公式資料にあるため、これを追加のFeeとして二重計上しない。
- Depositはasset / amountの `IN` Leg、Withdrawalはamountの `OUT` Legとfeeの `FEE` Legを候補にする。status変更は同じProvider eventの更新として扱う。
- Headerの `provider_event_id` にはtradeの `trade_id`、deposit / withdrawalの `uuid` を文字列化して保存する。`dedup_key` はConnectionとevent種別を含め、例として `trade:<trade_id>`、`deposit:<uuid>`、`withdrawal:<uuid>` を使う。公式資料はIDの全口座横断一意性を保証していないため、異なるConnection間でIDを共有しない。
- Trade APIは `position_side`、`profit_loss`、`interest` など信用取引向けの値も返す。MVPではbitbankの信用建玉をPerpetualとして扱わず、`position_side`のある履歴を通常のspot BUY / SELLへ誤変換しない。専用要件が決まるまでは元種別を保った未対応イベントとして扱う。

### Pagination、Rate Limit、履歴範囲

- Trade / deposit / withdrawal APIはcount上限と `since` / `end` 時間条件を公開する。REST資料にはoffset / cursorはなく、deposit / withdrawalの並び順指定も記載されていない。
- 公式Node SDKのdeposit / withdrawal request例には `order` がある一方、現行REST仕様のParameter表には記載がない。AdapterはこのParameterに依存しない。count上限に到達した時間窓の分割方法、境界時刻のinclusive/exclusive、同一timestamp時の完全性は実装前にfixtureまたは実レスポンスで検証する。確認不能ならActivity Capabilityをpartial/errorとして報告し、欠けた履歴を完全なものとして表示しない。
- REST資料は取得可能な最古の日付や保存期間を定義していない。MVPでAPIが提供する履歴の範囲を超えて存在すると約束しない。
- 公式Rate Limitはユーザー単位・秒単位で、通常QUERYは10回/秒、UPDATEは6回/秒。超過時はHTTP 429。アプリはRead-only QUERYだけを使い、並列同期を抑制し、429時はbounded backoff後に再試行または失敗状態として記録する。
- Deposit responseのamountはParameter表でnumberと記載される一方、response例ではstring。ParserはDecimal精度を失わず、数値JSONと文字列JSONの両方をfixtureで確認する。

### 設計影響

- `database-design.md` の任意 `external_account_ref` はbitbankでNULLを許容するためSchema変更不要。
- `activities` + `activity_legs` はtrade、deposit、withdrawalとFeeを表現できるためSchema変更不要。
- Connector入力はAPI Key / API SecretだけをProvider資格情報とし、識別情報が必要ならローカル表示名にする。read-only権限を画面説明と接続手順に明記する。

## Solana (Step 4-2)

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
- HeliusのgTFAからSignatureをcursorで収集し、署名をHelius Parsed Eventsの`POST /v1/parsed-events/transactions`へ最大100件ずつ渡して分類・Transfer情報を取得する方式を採用候補とする。Parsed Events APIはOpen Betaであり、対応プログラム外の命令はraw fallbackとなる。`summary.type`等のProvider分類は根拠として使える場合に限り利用し、未対応・解析失敗は元のイベント種別を保持してunknown/unavailableとして扱う。全DeFi命令の分類を保証しない。
- 1 Transaction Signatureにつき1 Activity Headerを基本とし、Transaction内の複数のWallet移動をLegsへまとめる。Token TransferのMint、Wallet方向、amountとNative SOL TransferをActivity Legsへ写像する。双方の`fromUserAccount` / `toUserAccount`が接続Walletなら内部移動として外部IN / OUTにしない。SWAPとして明示的に解析できた場合、Wallet視点の入力Mintを`OUT`、出力Mintを`IN`として保存する。ルート途中の`inner_swaps`を個々のLegとして足し合わせると、実際のWallet入出金と二重計上するため使用しない。複数Swapや別イベントが同一Transactionに含まれる場合のHeader / Legs集約は、公式fixtureで正規化ルールを確認する。
- ProviderのToken amountはraw integer amountとdecimalsを基に任意精度で換算し、JavaScript floating pointへ変換しない。Parsed Eventsのamount表現も含めて、実装時に大きな整数（2^53超）を使ったfixtureを追加する。正確な数量を復元できないイベントを丸めて保存しない。
- Solana TransactionのFeeはlamports建て。Providerが返す`fee` / `feePayer`を確認し、接続WalletがFee payerである場合だけ、SOL数量に換算した`FEE` Legを作る。別WalletがFeeを払ったTransactionのFeeを接続Walletへ帰属させない。
- Failed TransactionもActivity状態として識別する。失敗Transactionは状態・Signatureを保持できるが、revertされたIN / OUTを成功移動としてLeg化しない。接続WalletがFee payerで実際にFeeを負担した場合は`FEE`のみ記録する。
- HeaderのProvider Event IDはSolana Transaction Signature、dedup keyはConnection IDとSignatureを含める（例: `transaction:<signature>`）。異なるWallet Connection間で同一Signatureが観測されても、Connectionごとに履歴を分離する。
- Solana `blockTime`はNULLになり得る。Slotから推測時刻を作らず、時刻を確定できないイベントはtimestamp再取得を試み、確定できない間はActivity Capabilityをpartial/unavailableとして扱う。DBの`activities.occurred_at`が必須である現行Schemaへ架空時刻を保存しない。
- Helius Parsed EventsのサンプルではSwapのinput/output Mint・amount、token/native transfer、fee、status、signatureが返る。Parserの分類や金額がWallet視点の実入出金と一致することを代表的なTransfer / Swap / failed transactionのfixtureで検証するまで、parsed description文字列を台帳データとして扱わない。

### Pagination、履歴範囲、Rate Limit、費用

- Helius gTFAは`transactionDetails: "signatures" | "full"`、昇順/降順、時間/slot/status filters、`paginationToken`を持つ。現在のAPI Referenceでは1 requestあたり最大1,000件。`tokenAccounts` filterは`none` / `balanceChanged` / `all`。履歴ページ取得はcursorがなくなるまで継続し、ページ境界と再開位置を保存する。
- HeliusはgTFAのArchival DataでGenesis以降の履歴を取得できると案内する。ただしサービス・プラン・API機能の提供条件に依存する。標準Solana RPCのTransaction dataはNodeごとの保持状況に依存し、Solana公式はPublic RPCを本番用に推奨していない。MVPの初回Syncで過去どこまで取り込むかは未決定であり、無制限Backfillを暗黙に行わない。期間を決めた後も取得途中の失敗・上限到達をActivity completeとして報告しない。
- Helius現在のPricingページはFree $0 / 月・1M credits・10 RPC requests/s、Developer $49 / 月・10M credits・50 RPC requests/sを掲載する。HeliusのgTFA公開記事は1 callあたり100 credits、利用可能プランはpaidと説明する。これらは単一API Key / ProjectのProvider制限であり、Userごとに増えるQuotaではない。Rate limit / credits超過はbounded backoff後に失敗・partial状態とし、API Keyをログへ出さない。
- Parsed Events Quickstartは現在、全プランで1 requestあたり10 creditsと記載する。一方Helius Pricingページには「paid planで2026-09-21まで無料、その後の最終credit costは変更され得る」と残っており、2026-09-27時点で公式資料間に料金表記の不一致がある。Provider統合直前にDashboard / 最新公式PricingでgTFA利用可能プラン、Parsed Events費用、共通RPS制限を再確認する。MVP導入はAWS等の有料Infrastructureを増やさず、初期はHeliusの利用量を監視し、必要な場合だけ有料Planを判断する。
- API KeyはユーザーごとのWallet Credentialではなく、Backendが保護して使うApplication Provider Secret。ローカルはGit管理外の環境変数、本番はDeploymentで確定する保護されたSecret設定から注入する。ConnectionにはWallet Addressとローカル表示名のみ保存する。

### 実装前確認事項

- 初回Activity Backfillの期間・件数上限は requirements / screen designで指定されていない。Sync実装Stepまでに運用可能な対象期間を確定する。無制限に過去履歴を取り込む仕様として扱わない。
- Helius Parserが認識しないProgram、Token-2022 extension、複合Transactionの分類・Legの境界をfixtureで検証する。未解析のイベントをSWAPや送金として推測分類しない。
- Helius Parsed Events料金の公式表示が複数ページで不一致のため、Step 7実装で実APIキーを使う前に最新Plan / credits / rate limitを再確認する。外部Keyが未設定でもMock / fixtureによるAdapter実装・テストを先に進める。
- Token Metadata URIの安全な取得・更新頻度、未知Mintの価格Provider mappingはこの仕様確認の範囲外とし、MetadataをPortfolio valuationの必須条件にしない。
