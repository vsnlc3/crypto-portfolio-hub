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
