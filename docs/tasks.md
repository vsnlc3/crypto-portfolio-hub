# Crypto Portfolio Hub 実装タスク

文書ステータス: Draft

関連文書:

- [requirements.md](./requirements.md)
- [screen-design.md](./screen-design.md)
- [development-guidelines.md](./development-guidelines.md)
- [provider-specifications.md](./provider-specifications.md)
- [database-design.md](./database-design.md)
- [api-design.md](./api-design.md) ※ API実装と並行して作成・更新する

---

## 1. この文書の目的

本書は、Crypto Portfolio Hub のMVPを段階的に実装するためのタスクリストである。

各Stepは原則として上から順番に実施する。

1つのStepでは、そのStepに必要な範囲だけを変更し、後続Stepの機能を先行実装しない。

各Step完了時に以下を行う。

- 必要なテストを実行する
- 実装と設計文書の差分を確認する
- APIを追加・変更した場合は `api-design.md` を更新する
- DB Schema変更が必要な場合は `database-design.md` との整合性を確認する
- 変更内容をCommitする
- 次Stepへ進む前に未解決事項を明記する

APIは事前に全Endpointを固定せず、各Vertical Sliceの実装と同時にRequest / Response DTO、Error、認可、Pagination等を確定し、`api-design.md` へ反映する。

各機能の単体 / Controller / Repository / Frontend Testは該当Stepと同時に作成・実行する。後半のRegression Phaseは横断的な再確認を行い、機能Testを初めて追加するPhaseにしない。

実装依存関係は以下を守る。

```text
Provider仕様確認
→ Provider別Connections
→ Market Data取得
→ Providerデータ取得 / Sync
→ Price / FX Valuation
→ Portfolio集計 / Snapshot
→ Assets / Positions / Activity / Dashboard UI
```

---

# Phase 0: 設計確定

## Step 0-1: Database設計レビュー

- [x] `database-design.md` をレビューする
- [x] requirements / screen-design / development-guidelinesとの矛盾を確認する
- [x] Portfolio Snapshotで7D / 30D / 90D / 1Yの履歴表示が可能か確認する
- [x] Current StateとHistoryの境界を確認する
- [x] Credential保存方式を確認する
- [x] Numeric precision / scaleを確認する
- [x] User ownershipを確認する
- [x] Activity Header / Activity Legs構造を確認する
- [x] Perpetual PositionのPrice / Margin / PnLごとのFXを確認する
- [x] SnapshotのCOMPLETE / STALEと生成条件を確認する
- [x] Retention方針を確認する

### 完了条件

- [x] DB設計上の重大な未決事項がない
- [x] Provider固有仕様以外のSchema方針が確定している

Provider API、Event ID、履歴取得範囲、Rate Limit等はProvider仕様確認Phaseで確定する。

---

# Phase 1: Backend / DB 基盤

## Step 1-1: Spring Bootプロジェクト作成

- [x] `backend/` を作成する
- [x] Java 25 LTSを使用する
- [x] Spring Boot 4.1.x系を使用する
- [x] Maven構成を作成する
- [x] Maven Wrapperを追加する
- [x] Spring MVCを導入する
- [x] Spring Data JPAを導入する
- [x] PostgreSQL Driverを導入する
- [x] Flywayを導入する
- [x] Spring Securityを導入する
- [x] OAuth2 Clientを導入する
- [x] Validationを導入する
- [x] Spring Boot Actuatorを導入する
- [x] Test dependenciesを追加する
- [x] Testcontainers PostgreSQLを導入し、初期Migration / RepositoryのIntegration Testに使えるようにする
- [x] 基本Package構成をdevelopment-guidelinesに合わせる

### 完了条件

- [x] Backendが起動する
- [x] `/actuator/health` が成功する
- [x] `./mvnw test` が成功する
- [x] Secretや実CredentialをRepositoryへ追加していない

---

## Step 1-2: PostgreSQL / Docker Compose

- [x] Root Docker ComposeへBackendを追加する
- [x] PostgreSQLコンテナを追加する
- [x] BackendからPostgreSQLへ接続する
- [x] DB接続値を環境変数化する
- [x] PostgreSQLのNamed Volumeを設定する
- [x] Frontend / Backend / PostgreSQLを内部Networkで接続する
- [x] Backend / PostgreSQLをブラウザーから直接利用する構成にしない
- [x] 開発用SecretをGit管理しない

### 完了条件

以下の3サービスがDocker Composeで起動する。

```text
Frontend
Backend
PostgreSQL
```

- [x] Backend health checkが成功する
- [x] PostgreSQL接続が成功する
- [x] Container再作成でDB Volumeが失われない

---

## Step 1-3: Flyway初期Schema

`database-design.md` を基準にMigrationを作成する。

対象:

- [x] `users`
- [x] `connections`
- [x] `connection_credentials`
- [x] `connection_sync_states`
- [x] `sync_runs`
- [x] `sync_run_results`
- [x] `asset_balances`
- [x] `perpetual_positions`
- [x] `provider_account_states`
- [x] `activities`
- [x] `activity_legs`
- [x] `portfolio_snapshots`
- [x] CHECK Constraint
- [x] Foreign Key
- [x] Composite Foreign Key
- [x] Unique Constraint
- [x] Partial Unique Index
- [x] Query用Index

### User ownership

DB設計に従い、Connection配下の対象テーブルでは、

```text
(connection_id, user_id)
```

から、

```text
connections(id, user_id)
```

へのComposite Foreign Keyを作成する。

`connections` には以下を持たせる。

```text
UNIQUE (id, user_id)
```

`activity_legs` は `activities` 経由で所有関係を継承する。

`sync_run_results` は `sync_runs` 経由で所有関係を継承する。

### Retention

MVPでは以下を自動削除しない。

```text
Activity / Activity Legs / Perpetual Fill Details
Sync Run / Sync Run Results
Portfolio Snapshot
```

Retention用のTTL JobやPartitionは作成しない。

CredentialはConnection削除時に即時削除する方針を維持する。

### 完了条件

- [x] Testcontainersの空DBからMigrationが成功する
- [x] 複合FK・必須FK・CHECK制約の拒否ケースをIntegration Testで確認する
- [x] Backend再起動時にMigrationが安全に検証される
- [x] Hibernate Schema自動生成に依存しない
- [x] 想定外のcross-user組み合わせをComposite FKが拒否する
- [x] `sync_run_results` が存在しない `sync_runs` を参照できない
- [x] `activity_legs` が存在しないActivityを参照できない

---

# Phase 2: 共通Backend基盤

## Step 2-1: Error Handling

- [x] RFC 9457 Problem Detailsを基準としたError Responseを実装する
- [x] Validation Error
- [x] Authentication Error
- [x] Authorization Error
- [x] Resource Not Found
- [x] Provider Error
- [x] Persistence Error
- [x] Unexpected Error
- [x] Request IDを導入する

### Provider Error分類候補

```text
TIMEOUT
RATE_LIMIT
AUTHENTICATION
PERMISSION
UNAVAILABLE
INVALID_RESPONSE
```

Provider固有の生Error ResponseをFrontendへ直接公開しない。

### 完了条件

- [x] Stack TraceをFrontendへ返さない
- [x] SQLをFrontendへ返さない
- [x] CredentialをFrontendへ返さない
- [x] Providerの認証ResponseをFrontendへ返さない
- [x] 安定したApplication error codeを返せる
- [x] Error mapping / Request IDの単体またはController Testがある

---

## Step 2-2: Numeric / Money基盤

- [x] `BigDecimal` を使った数量・金額処理方針を実装する
- [x] Money / Price / Quantity / FX等、必要なDomain Valueを定義する
- [x] Currencyを明示して異通貨の暗黙加算を防ぐ
- [x] JPY換算を実装できる基盤を作る
- [x] 表示用丸めと内部計算を分離する
- [x] DB設計のprecision / scaleとJava型を一致させる
- [x] 取得不能値を0へ変換しない

### Perpetual FX

用途を分離する。

```text
Price FX  → Position Value
Margin FX → Margin
PnL FX    → Unrealized PnL
```

異なるCurrencyの場合でも暗黙に同じRateを流用しない。

### テスト対象

- [x] JPY換算
- [x] JPY → JPY identity conversion
- [x] PnL正負
- [x] Position Value
- [x] Margin換算
- [x] Unrealized PnL換算
- [x] Price / Margin / PnLの異なるCurrency
- [x] FX取得不能
- [x] 小数精度
- [x] Rounding

---

## Step 2-3: JPA Entity / Repository基盤

`database-design.md` に従いPersistence Mappingを実装する。

対象:

- [x] User
- [x] Connection
- [x] ConnectionCredential
- [x] ConnectionSyncState
- [x] SyncRun
- [x] SyncRunResult
- [x] AssetBalance
- [x] PerpetualPosition
- [x] ProviderAccountState
- [x] Activity
- [x] ActivityLeg
- [x] PortfolioSnapshot

### 方針

- [x] Many-to-Oneは原則LAZY
- [x] 巨大なObject Graphを作らない
- [x] One-to-Manyを常時EAGER取得しない
- [x] `CascadeType.ALL` を機械的に使わない
- [x] History EntityをConnection操作から誤削除しない
- [x] JPA EntityをREST DTOとして直接公開しない
- [x] User所有ResourceのRepository QueryはuserIdを条件に含める

### 禁止

User所有Resourceに対し、

```text
findById(id)
```

だけで取得し、そのままAPIへ返す実装を作らない。

### 完了条件

- [x] EntityとSchemaが一致する
- [x] Repository Testの土台と、User IDを条件にしたRepository Testがある
- [x] User ownershipを検索条件に含められる

---

# Phase 3: Authentication / User

## Step 3-1: Google OAuth2 Login

- [x] Spring Security OAuth2 Loginを設定する
- [x] Google OIDC `sub` を取得する
- [x] 初回ログイン時にUserを作成する
- [x] 再ログイン時に既存Userへ紐付ける
- [x] EmailではなくGoogle SubjectをIdentityの基準にする
- [x] Backend Sessionを利用する
- [x] HttpOnly Cookieを使用する
- [x] ProductionでSecure Cookieを使用できる構成にする
- [x] SameSite方針を設定する
- [x] Logoutを実装する
- [x] CSRF対策を有効にする

### API

以下のAPIとBrowser向けOAuth routeを実装し、`api-design.md` に契約を記録する。

```text
GET  /api/v1/auth/csrf
GET  /api/v1/auth/me
POST /api/v1/auth/logout

GET  /oauth2/authorization/google
GET  /login/oauth2/code/google
```

Google OAuth Client ID / Secretを設定したローカル環境で実Google OAuth E2Eを確認済み。公開環境では利用するHost / Schemeに対応したcallback URL登録が別途必要。Secret値はRepositoryへ保存しない。

### 完了条件

- [x] Googleログインできる
- [x] ログインUserをBackendで特定できる
- [x] 未認証状態では保護APIへアクセスできない
- [x] ClientからUser IDを指定して認証を回避できない
- [x] Logout後に保護APIへアクセスできない

### テスト

- [x] OAuth成功・失敗をMockで検証する
- [x] Google `sub` から同一Userへ紐付くことを検証する
- [x] Email変更・重複だけでUser identityが変わらないことを検証する
- [x] 未認証拒否、Logout後のSession無効化、CSRF保護APIを検証する
- [x] Client supplied userIdを認可根拠にできないことを検証する
- [x] 実Google OAuth E2EでLogin → `/api/v1/auth/me` (`200`) → Logout (`204`) → Logout後の `/api/v1/auth/me` (`401`) を確認する

---

## Step 3-2: Frontend Sign in

- [x] Sign in画面を実装する
- [x] Googleログインへの導線を追加する
- [x] 未認証時のRoute Guardを追加する
- [x] 固定プロフィール表示をGoogleログインUserへ置き換える
- [x] Logoutを接続する
- [x] 認証Loadingを表示する
- [x] 認証Errorを表示する
- [x] Vitest / React Testing LibraryのFrontend Test基盤を用意する

### テスト

- [x] Route Guardが未認証時にSign inへ遷移する
- [x] 認証中 / 認証Error / Logout中・失敗・成功後の画面状態を検証する
- [x] 実ブラウザーでSign in → Google Login → Dashboard → Logout → Sign inを確認する

実Google OAuthのブラウザー確認は外部Smoke Testとして扱い、通常の実装・回帰テストの完了条件にはしない。

### 完了条件

```text
Sign in
↓
Google Login
↓
Dashboard
↓
Logout
↓
Sign in
```

が動作する。

---

# Phase 4: Provider仕様確認

実Provider実装前に、公式仕様を確認する。

確認結果は必要に応じて、

- `database-design.md`
- `api-design.md`
- `development-guidelines.md`

へ反映する。

推測でProvider Adapterを実装しない。

---

## Step 4-1: bitbank

確認する。

- [x] Balance API
- [x] Activity / Transaction API
- [x] Deposit / Withdrawal API
- [x] API Key
- [x] API Secret
- [x] API Key署名方式
- [x] 必要なread-only権限
- [x] 注文・出金権限が不要であること
- [x] 接続確認方法
- [x] Account identifierの有無
- [x] Connection作成時に必要な入力項目
- [x] Balanceのavailable / locked / total semantics
- [x] Event ID
- [x] Pagination
- [x] Rate Limit
- [x] 履歴取得可能範囲
- [x] BUY / SELL時に取得できる資産情報
- [x] Base / Quote Assetの数量
- [x] Fee Asset / Fee Quantity
- [x] Activity Legsへの変換方法
- [x] Event dedup key

Confirmed behavior and unresolved API documentation details are recorded in [provider-specifications.md](./provider-specifications.md). The Adapter uses millisecond window splitting with conservative boundary overlap and fails closed when a provider cap cannot be cleared. Spot trade amount is interpreted as base quantity from the official CLI order examples and is identified in the Provider specification as an inference pending read-only account smoke validation.

---

## Step 4-2: Solana

確認する。

- [x] Wallet Address形式
- [x] Address validation
- [x] Connection作成時に必要な入力項目
- [x] Native SOL Balance
- [x] SPL Token Balance
- [x] Token Mintによる識別
- [x] Token metadata取得範囲
- [x] Transaction履歴
- [x] Transfer分類
- [x] Swap分類
- [x] SwapのOUT asset
- [x] SwapのIN asset
- [x] Fee asset / quantity
- [x] Transaction Signature
- [x] Activity Event ID / dedup key
- [x] RPC / API Provider
- [x] Historical Data取得範囲
- [x] Pagination
- [x] Rate Limit
- [x] RPC費用

秘密鍵・Seed Phraseは要求しない。

確認結果と公式資料は[provider-specifications.md](./provider-specifications.md)に記録した。HeliusをSolana履歴Providerとして採用し、Wallet配下Token Accountの履歴を含めてcursor取得する。MVPの初回Backfill範囲は直近90日、1回100件を上限として決定し、続きは同一query windowのcursorから手動再開する。Helius Parsed EventsはOpen Betaで公式料金記載に不一致があるため、実API接続前に料金・Plan・Rate Limitを再確認する。未対応ProgramやParser失敗を推測分類せず、FixtureでTransfer / Swap / failed Transactionと数量精度を検証する。

---

## Step 4-3: Hyperliquid

確認する。

- [x] Account Address
- [x] Address validation
- [x] Connection作成時に必要な入力項目
- [x] Spot Balance
- [x] Perpetual Position
- [x] Entry Price
- [x] Mark Price
- [x] Liquidation Price
- [x] Position Quantity
- [x] Leverage
- [x] Margin
- [x] Collateral
- [x] Account Equity
- [x] Unrealized PnL
- [x] Funding
- [x] Activity
- [x] Stable Position Key
- [x] Event ID
- [x] Historical Data取得範囲
- [x] Pagination
- [x] Rate Limit
- [x] `userAbstraction` によるAccount abstraction mode detectionとMode別Net Worth source mapping
- [x] Spot `total` / `hold`を別属性として保持し、`total`のみを残高評価に使う規則
- [x] Funding signed amountからIN / OUTへの変換規則
- [x] Perp Fill DetailとActivity Header / asset Legsのmapping

確認結果と公式資料は[provider-specifications.md](./provider-specifications.md)と[database-design.md](./database-design.md)へ反映した。`userAbstraction`の公式Response値をModeへMappingし、`default` / legacy `dexAbstraction` / 未知値はUNSUPPORTED / UNKNOWNとして推測集計しない。Unified Account / Portfolio MarginはSpot Clearinghouse Balanceを基準とし、Perp account balance/equityを重ねない。StandardはSpotとPerp DEXごとのAccount Equityを分ける。Perp Fillは1:1 `activity_perpetual_fill_details`へ保存し、`activity_legs`には現物のIN / OUTを作らない。公式例のZero Addressへread-only queryを行い、`default` responseを確認したが、実User AddressのModeは未確認である。Adapter Fixture TestはStep 7-6で完了した。

特に以下を確認する。

```text
Account EquityにUnrealized PnLが含まれるか

Account EquityとSpot / Collateral Balanceの包含関係

Position MarginとAccount Collateralの包含関係

Price Currency
Margin Currency
PnL Currency

FundingのAsset / Amount / Direction（signed `usdc`を正数IN / 負数OUTとする）
```

Net Worthへ二重計上しないため、Providerの数値定義を公式仕様で確認する。

---

## Step 4-4: Market Data

以下を決定する。

- [x] Current Crypto Price取得元
- [x] USD / JPY FX取得元
- [x] 24h price changeを含むmarket quote / tickerの取得可否・比較期間
- [x] 対応Assetとsymbol / asset_keyからMarket Data IDへの対応
- [x] Price Currency
- [x] Price Source / evaluatedAt
- [x] FX Source / evaluatedAt
- [x] Price timestamp
- [x] FX timestamp
- [x] Rate Limit
- [x] Failure時の扱い
- [x] stale判定に利用する鮮度基準
- [x] JPYの場合のidentity conversion
- [x] Provider障害時のfallbackを設けるか
- [x] 価格変化・FXが取得不能な場合にunavailableとして返せるか

MVPで独立したMarket Price History DBは作らない。Provider仕様・Market Data Providerの公式資料を確認し、取得できる項目・頻度・条件を推測で確定しない。

### 確定結果

- CoinGecko Demo API `/coins/markets`でSpot向けUSD建てcurrent price、24h percent change、`last_updated`を一括取得する。Demo planは公式資料時点で100 calls/min、10,000 calls/month、Data Freshnessは60秒から。Appは1回の複数ID requestを10分TTLの共有Cacheで利用する（31日連続でも約4,464 calls）。Hyperliquid PerpはProviderのMark Price / PnLを使い、Spot価格と混ぜない。
- USD/JPYはExchangeRate-API Free planのUSD base ratesから取得し、`time_last_update_unix`をFX evaluatedAtとして使う。日次更新、月1,500 requests。Backend SecretのAPI keyを使い、URL / logへ漏らさない。
- 現行UI Asset ID: BTC=`bitcoin`, ETH=`ethereum`, SOL=`solana`, XRP=`ripple`, HYPE=`hyperliquid`, USDC=`usd-coin`。Provider asset_keyは正規のAsset identityを確認してからmappingし、tickerだけで別Tokenを統合しない。
- Price Currency=`USD`; Source=`COINGECKO`; FX Source=`EXCHANGERATE_API`; Price timestamp=`last_updated`; FX timestamp=`time_last_update_unix`。JPY identityはRate `1`, Source `IDENTITY`。
- Application鮮度の初期値はCrypto price 15分、daily FX 72時間。二次Providerへの自動fallbackは行わない。必要な価格・FXを得られないときはNULL / unavailableとし、Portfolio全体が正しく評価できなければSnapshotを作成しない。鮮度上限を超えた前回成功Current Stateを再利用する場合はSTALEとして扱う。
- Price changeは24h percentage。Unavailable / staleな24h changeは`null`とし、Price評価が有効な場合のNet Worth計算を妨げない。
- 公式資料を[provider-specifications.md](./provider-specifications.md)へ記録した。実API requestとcredential確認はStep 6-1、Adapter / cache / failure testsもStep 6-1 / 6-2で実施する。
- CoinGecko Demo planではAttributionが必要なため、Dashboard / Assetsへ`Powered by CoinGecko`表記とAPI pageへのLinkを含める。組織外への提供・公開前にはPlanのlicense条件とTerms上必要なUser Agreement / Privacy Policy / data disclaimerを確認する。Demo planをそのまま商用・公開運用可能と見なさない。

### Connectionsへ引き継ぐ確定事項

- bitbank Create RequestのAPI Key / API Secret、read-only権限、接続確認、identifier、入力項目
- Solana Create RequestのWallet Address形式、validation、入力項目
- Hyperliquid Create RequestのAccount Address形式、validation、入力項目

これらの確認が完了するまで、次のConnections PhaseでProvider固有DTO / validation / Formを確定しない。

---

# Phase 5: Connections

## Step 5-1: Credential暗号化

- [x] AES-256-GCM暗号化を実装する
- [x] nonceをCredentialごとに生成する
- [x] key versionを保存する
- [x] Encryption Keyを環境変数等のBackend Secretから取得する
- [x] `CREDENTIAL_ENCRYPTION_KEY_BASE64`にBase64形式の32-byte AES key、`CREDENTIAL_ENCRYPTION_KEY_VERSION`に正のkey versionを設定する
- [x] Encryption KeyをDBへ保存しない
- [x] Encryption KeyをDocker imageへ埋め込まない
- [x] Encryption Key未設定時に平文保存へfallbackしない
- [x] CredentialをResponseへ返さない（現StepではCredentialを返すAPIを追加せず、暗号化値もsafe `toString` で秘匿する）
- [x] Credentialをログへ出さない
- [x] Credential ciphertextもログへ出さない
- [x] Connection削除Flow用のCredential即時削除Repository operationを用意する。Connection削除時の呼び出しはStep 5-2で実装する。

### テスト

- [x] encrypt → decrypt
- [x] 同一値でも異なるnonce
- [x] 不正ciphertext
- [x] 不正tag
- [x] key未設定
- [x] key version
- [x] Credential削除用のUser-scoped Repository operationを用意し、他UserのCredentialを削除できないことを検証する。Connection削除Flowからの呼び出しはStep 5-2で実装する。

---

## Step 5-2: Connection Backend

Phase 4で確認したProvider仕様に基づき、Provider別Create Request、validation、Connection一覧・削除を実装する。

- [x] Connection一覧
- [x] Connection追加
- [x] Connection削除
- [x] ProviderごとのCreate Request / Response DTO
- [x] Providerごとのvalidation
- [x] User ownership
- [x] display name
- [x] masked identifier（bitbankはProvider仕様上Account IDが得られないためNULLとし、Credentialを識別子として使わない）
- [x] ProviderごとのCapabilities
- [x] Connection status（作成直後の`CONNECTED`は接続設定の登録状態を表し、Provider APIの検証はSync時に行う）
- [x] last attempt / last success
- [x] 論理削除
- [x] Current State削除方針をDB設計どおり実装する（Balance / Position / Account State / Sync State）
- [x] CredentialはConnection削除時に即時削除する
- [x] Historyは保持し、削除済ConnectionをActive一覧に返さない

### User ownership

全操作で、

```text
authenticated user
+
connection id
```

を条件にする。

User AがUser BのConnection IDを指定しても取得・変更・削除できないこと。

### API

実装と同時に `api-design.md` を更新する。

候補:

```text
GET    /api/v1/connections
POST   /api/v1/connections
DELETE /api/v1/connections/{connectionId}
```

Request / Response DTOはPhase 4で確認したProvider別の入力項目と既存Connections UIを基に確定する。API契約を `api-design.md` へ反映する。

### Backend Test / ownership

- [x] Provider別Create Requestのvalidationを検証する
- [x] Repository / ControllerでUser AがUser BのConnectionを取得・変更・削除できないことを検証する
- [x] Client supplied userIdで所有権を変更できないことを検証する
- [x] 論理削除後のConnectionがActive一覧に含まれないことを検証する
- [x] User AがUser BのCredentialを取得できず、Connection操作からBのCredentialへアクセスできないことを検証する
- [x] Connection削除時にCredential / Current Stateを削除し、Activity / Sync Historyを保持することを検証する

---

## Step 5-3: Connections Frontend

既存Connections UIを実データへ接続する。

- [x] TanStack Query導入
- [x] Connection一覧
- [x] Loading
- [x] Empty
- [x] Error
- [x] Add Source
- [x] Delete / Disconnect
- [x] マスク済みIdentifier
- [x] Connection Status
- [x] Capabilities
- [x] last attempt / last successful sync
- [x] Toast
- [x] React Hook Form
- [x] Zod

### 完了条件

- [x] Provider仕様で確定したbitbank / Solana Wallet Address / HyperliquidのFormからConnectionを追加できる
- [x] Connectionを一覧表示できる
- [x] Connectionを削除できる
- [x] 他UserのConnectionが表示されない
- [x] 入力validationとAPI validationの結果を表示する
- [x] Credentialが画面/API Response/ログへ露出しないことをテストする

このPhaseではJPY評価額を表示しない。Portfolio計算完了後にConnections画面へ追加する。手動Sync操作もProvider Sync API完成後のDashboard Phaseで統合する。

### Frontend Test

- [x] Provider別Form、validation、Loading / Empty / Errorを検証する
- [x] Connection追加・削除後の一覧更新を検証する

---

# Phase 6: Market Data / Valuation基盤

Phase 4で取得元・仕様を確認した後、Market Data Providerを実装する。Market Price History DBは作成しない。Provider Adapter / SyncによるPortfolioデータ取得はPhase 7、そのデータに価格・FXを適用するValuationとPortfolio集計はPhase 8で行う。

## Step 6-1: Market Data Provider

- [x] 公式仕様に基づくCurrent Crypto Price取得
- [x] USD / JPY FX取得
- [x] Canonical `asset_key` とMarket Data IDの対応
- [x] Price Currencyを保持する
- [x] Price Source / `price_evaluated_at` を追跡する
- [x] FX Source / `fx_evaluated_at` を追跡する
- [x] JPYのidentity conversionはRate `1` / Source `IDENTITY` とする
- [x] Rate Limit / Timeoutを扱う
- [x] Provider Errorを分類する
- [x] Price / FXの鮮度基準を適用できる
- [x] fallbackは実装しない（Phase 4の採用方針どおり）
- [x] CoinGecko Demo key / ExchangeRate-API keyはBackend環境設定から注入し、未設定時はUnavailableとして起動・同期する（実SecretはSource / Image / DB / logへ入れない）
- [x] CoinGeckoは複数Coin IDを一括取得し、Market DataをUserごとに重複Fetchせず共有Cacheする
- [x] ExchangeRate-API responseの更新時刻に合わせて共有Cacheし、Keyを含むRequest URIをlogへ出さない
- [x] CacheしたPrice / FXにはProvider evaluatedAtと鮮度状態を付ける
- [x] CoinGecko AttributionをDashboard / Assetsに表示し、API pageへLinkする

### Live Provider Smoke Test

- [x] `.env`に設定した実キーでCoinGecko / ExchangeRate-APIへの実requestを行い、response shapeと評価時刻を確認した。`-Dmarket-data.live-smoke=true`で明示実行し、キーやrequest URIは表示しない。
- [x] 2026-09-28にJava 25でLive Smoke Testを再実行し、1 test成功（failures / errors / skipped すべて0）を確認した。Provider値・Credentialは記録しない。
- [x] 実キー未設定時は該当ProviderをUnavailableのままにし、Dummy価格・為替へfallbackしないことを確認する。

## Step 6-2: 24h Market Quote

Assetsで表示する銘柄ごとの価格変化はMarket Data Providerのcurrent quote / tickerとして扱う。Portfolio Snapshotから算出しない。

- [x] 固定比較期間 `24h` のprice changeを取得する
- [x] Providerの返すchangeの単位（価格差 / percentage等）を保持する
- [x] Comparison periodを `24h` として返す
- [x] Market Data Sourceを返す
- [x] quoteの `evaluatedAt` を返す
- [x] Providerが値を返さない場合は `null / unavailable` とする
- [x] 取得不能値を推測値や0にしない
- [x] Price History DBやTicker History DBを作らない

### Step 6-1 Tests

- [x] Current priceの取得とcanonical `asset_key` / CoinGecko ID mapping
- [x] Price Currency / Source / evaluatedAtの保持
- [x] USD / JPY FXの取得とSource / evaluatedAtの保持
- [x] Rate Limit / Timeout / Error分類
- [x] price unavailable / FX unavailable
- [x] stale price / stale FXの判定

## Step 6-2 Tests

- [x] 24h quoteのcomparison period / source / evaluatedAt
- [x] 24h change unavailable
- [x] 24h changeにPortfolio Snapshotを使用していないこと

---

# Phase 7: Provider Adapter / Sync

Providerは一度に全部作らず、1サービスずつ完成させる。

推奨順:

```text
Solana
↓
bitbank
↓
Hyperliquid
```

Solanaは公開Wallet Addressだけで開始できるため最初に実装する。Provider取得値は正規化して保存し、Market Dataによる価格・FX評価はPhase 8で行う。評価前や取得不能時のJPY値はNULL / `UNAVAILABLE` とし、0で埋めない。Activity Legの評価はProvider報告値または仕様確認済みのイベント時点 / 取込時点評価を使い、根拠がなければ `UNAVAILABLE` とする。後から現在価格で過去Activityを再評価しない。

---

## Step 7-1: Provider Port

実際に確認したProvider仕様を基準に必要なPortを実装する。

Provider連携用の候補:

```text
BalanceProvider
PositionProvider
ActivityProvider
AccountProvider
```

Providerが対応しないCapabilityの空実装を作らない。

Provider DTOをApplication / Domainへ漏らさない。

### 共通Sync制御

- [x] Sync APIの共通契約を定め、`api-design.md` へ記録する
- [x] 同一Connectionの同時Syncを防止し、`SYNCING` を表現する
- [x] 異なるConnectionは独立してSyncできる
- [x] Success / Error / Partial Failureいずれでも同期中状態を解放する
- [x] `lastAttemptAt` と `lastSuccessAt` の意味を分けて更新する
- [x] 認証済みUserとConnection IDの両方で所有権を検証する

### 共通Sync Test

- [x] 同一Connectionの重複Sync拒否 / 競合制御
- [x] 異なるConnectionの並行Sync
- [x] Error後のSync状態解放
- [x] User AがUser BのConnectionのSyncを実行できない

---

## Step 7-2: Solana Adapter

- [x] Wallet Address validation
- [x] Native SOL Balance取得
- [x] SPL Token Balance取得
- [x] asset_key正規化
- [x] 共通Balanceモデルへの変換
- [x] Transaction取得
- [x] Transfer正規化
- [x] Swap正規化
- [x] Activity Header生成
- [x] IN / OUT / FEE Activity Legs生成
- [x] Event dedup key
- [x] Error分類
- [x] Timeout
- [x] Rate Limit考慮
- [x] fixture / Mock HTTPテスト

取得できない情報を推測して埋めない。

2026-09-28、公式Solana / Helius資料で確認したrequest / response contractをProvider Adapterに実装し、Fixture / Mock HTTP Testを実行した。HeliusへのLive requestは行っていない。Token symbol / nameは推測せずNULLにし、Current DB schemaの小数scaleを超えて正確に保存できないquantityは丸めずProvider failureとして返す。

---

## Step 7-3: Solana Sync

- [x] Manual Sync
- [x] Sync Run作成
- [x] Sync Run Result
- [x] Capability Sync State
- [x] Current Balance更新
- [x] Activity Header / Legs保存
- [x] 同一Activityを重複保存しない
- [x] Activity Header / Legsを同一Transactionで保存する
- [x] Providerから取得できたoriginalAmount / originalCurrencyとvaluation metadataを保持する
- [x] Historical Legを現在価格で後から再評価しない。評価情報がなければNULL / unavailableにする
- [x] Sync失敗時に前回成功Current Stateを保持する
- [x] stale判定に必要な情報を保持する
- [x] Balance完全成功時のみCurrent Balance集合を更新する
- [x] Activity失敗がBalance成功を巻き戻さない
- [x] User AがUser BのConnection / Sync / Balance / Activityへアクセスできない
- [x] User AがUser BのActivity Legsを親Activity経由でも取得できない

### API

```text
POST /api/v1/connections/{connectionId}/sync
GET  /api/v1/connections/{connectionId}/sync-runs/{syncRunId}
```

共通Sync契約を利用する。Provider別のCapability、Response、Partial Failure表現は `api-design.md` に記録済み。POSTは202で受付結果を返し、GETはUser ownershipを検証してCapability別結果と`continuationAvailable`を返す。Heliusのopaque cursorとquery-window startはAPIへ公開しない。

初回Activity取得は直近90日、各manual syncは最大100件とする。続きがあるときはcursorと同じquery windowを保存し、次回manual syncで再開する。初回範囲の取得完了後のincremental syncは前回成功時刻の1時間前から取得し、重複eventはdedupする。

### 完了条件

- [x] 1つのCapability失敗が他Capabilityの成功を失敗扱いにしない
- [x] 前回成功値が残る
- [x] 未取得と0を区別できる
- [x] Current State・Activityを取得したUser所有Connectionだけへ保存できることを検証する
- [x] 90日初回window、1回100件上限、cursor再開、1時間のincremental overlapを検証する
- [x] TestcontainersによるSync API・Persistence・ownership・partial failureテストを通す

---

## Step 7-4: bitbank Adapter

- [x] Credential復号処理の呼出し（connection_id + authenticated user idのowner-scoped query）
- [x] API署名（TIME-WINDOW HMAC-SHA256と公式signature example）
- [x] Provider DTO
- [x] Balance取得・正規化（onhandをtotalとし、available / lockedを別保持）
- [x] Activity / Transaction取得・正規化
- [x] BUY / SELLのActivity Header / Legs
- [x] Deposit / WithdrawalのActivity Header / Legs
- [x] Fee Leg（通貨が明示されたspot trade Fee。Withdrawal Fee単位は未確定としてLegを作らない）
- [x] dedup key mapping
- [x] Error mapping
- [x] Timeout / Rate Limit
- [x] fixture / Adapter Test

Trade amountのbase quantity解釈は公式CLI注文例に基づく推論であり、実アカウントRead-only Smoke Testは外部確認事項として残す。Withdrawal Feeのcurrencyは公式REST資料に明記されないためunavailableとし、Net WorthやBalanceには加算・減算しない。

取得できない項目を推測せず、必要情報がないLegはNULL / unavailableにする。

---

## Step 7-5: bitbank Sync

- [x] Sync Run / Result / Capability State
- [x] Balance Current State更新
- [x] Activity Header / Legsを同一Transactionで保存
- [x] originalAmount / originalCurrencyと取得済みvaluation metadataを保持
- [x] 取得できない値を推測せず、NULL / unavailableにする
- [x] 重複排除・再実行時の冪等性
- [x] Partial Failure
- [x] Sync失敗時に前回成功値を保持
- [x] lastAttemptAt / lastSuccessAt
- [x] stale情報
- [x] User ownership
- [x] User AがUser BのCredential / Balance / Activity / Sync情報へアクセスできない
- [x] Sync Integration Test

Application policyとしてActivity初回同期は受付時刻から過去90日、以降は前回成功時刻から1時間の重複を含めて再取得する。Providerが最古の履歴日を保証しないことは`provider-specifications.md`に記録した。Balanceは完全成功時だけ置換し、Activityはdedup keyで重複排除してstatus変更をHeaderへ反映する。`BitbankSyncIntegrationTests`でBalance / Activity保存、original amount / currency、valuation unavailable、部分失敗時の前回Balance保持、Header / LegsのRollback、User ownershipをTestcontainersで検証した。

---

## Step 7-6: Hyperliquid Adapter

- [x] `V10__support_hyperliquid_account_modes_and_perp_fills.sql` MigrationとJPA / Repositoryを追加する
- [x] `provider_account_states.account_scope`でPerp DEXごとのAccount Stateを保持する
- [x] Account Stateへ正規化ModeとProvider `userAbstraction`値を保存する
- [x] Info API responseをdecodeし、共通形式へ正規化するDTOを追加する
- [x] `userAbstraction`を取得し、既知値をModeへ厳密にMappingする
- [x] `default` / `dexAbstraction` / 欠落 / 未知値 / API失敗をUNKNOWN / UNSUPPORTEDとし、誤集計しない
- [x] Spot Balance取得・正規化
- [x] `total`をBalanceとして評価し、`hold`を加算しない
- [x] Account State取得・正規化
- [x] Position取得・正規化
- [x] Funding signed amountをIN / OUTへ正規化
- [x] Spot Fill Activity Header / Legs
- [x] Perp Fill Activity Header / Perpetual Fill Detail
- [x] Perp Fill quantityを資産IN / OUT legsにしない
- [x] Perp FeeをFEE Leg、Fee rebateをIN Legへ変換する
- [x] Event ID / Position stable key mapping
- [x] Standard / Unified / Portfolio MarginのNet Worth mappingとEquity / PnL二重計上防止
- [x] Account Equityの意味を反映
- [x] Collateralの意味を反映
- [x] Unrealized PnLの意味を反映
- [x] Price Currencyを保持
- [x] Margin Currencyを保持
- [x] PnL Currencyを保持
- [x] Provider Error mapping
- [x] Timeout / Rate Limit
- [x] fixture / Adapter Test

### Hyperliquid Mapping Tests

- [x] `disabled` → Standard; `unifiedAccount` → Unified; `portfolioMargin` → Portfolio Margin
- [x] `default` / `dexAbstraction` / unknown / missing modeでNet Worth valuation unavailable
- [x] StandardでSpot totalと各Perp DEX accountValueを一度ずつ利用し、accountValue内のPnLを重ねない
- [x] Unified / Portfolio MarginでSpot totalを利用し、Perp accountValueを加算しない
- [x] Per-Position Unrealized PnLは意味・Currencyを確認できる場合のみ一度加算
- [x] Position margin / Position ValueをNet Worthへ加算しない
- [x] Spot holdをtotalへ追加しない
- [x] Fundingの正数・負数・ゼロをIN / OUT / no legへ対応
- [x] Perp Fill Detailのside / direction / quantity / price / startPosition / closedPnl mapping
- [x] Perp Fill quantityはIN / OUT legsにならず、Feeのみ資産legになる
- [x] DetailのUser ownershipは親Activityから継承され、cross-user参照を拒否する

Fixture Adapter TestでMode mapping、未知値時のfail closed、StandardとUnified / Portfolio Marginの残高元、Spot hold、spot / perp fill、fee rebate、funding、Position通貨、Perp Fill Detail、rate limit / timeoutを確認した。Testcontainers Integration TestでV10 Schema / Hibernate validateとPerp Fill Detailの親Activity経由User ownershipを確認した。Fill履歴が直近10,000件の上限へ達した場合、`ActivityPage.limitedByProviderHistory`をtrueにして完全な履歴と誤認しない。

---

## Step 7-7: Hyperliquid Sync

- [x] Sync Run / Result / Capability State
- [x] Balance / Account State / Position Current State更新
- [x] Activity Header / Legsを同一Transactionで保存
- [x] Perp Fill Activity Header / Detailを同一Transactionで冪等保存
- [x] Perp Fee / Funding / Spot Fill LegsとPerp Fill Detailを混同しない
- [x] originalAmount / originalCurrencyと取得済みvaluation metadataを保持
- [x] 取得できない値を推測せず、NULL / unavailableにする
- [x] 重複排除・再実行時の冪等性
- [x] Partial Failure
- [x] Sync失敗時に前回成功値を保持
- [x] lastAttemptAt / lastSuccessAt
- [x] stale情報
- [x] User ownership
- [x] User AがUser BのBalance / Position / Account State / Activity / Sync情報へアクセスできない
- [x] Sync Integration Test

`HyperliquidSyncIntegrationTests`でCurrent State置換、Spot / Perp / Fundingの保存形、Perp Fill Detail、同じイベント再取得時の重複排除、Provider履歴上限時にActivity Syncを完了扱いしないこと、Current State失敗時の前回成功値保持、lastAttempt / lastSuccess、User ownershipをTestcontainers PostgreSQLで検証した。初回Activity queryはSync開始時刻から90日、以後は前回Activity成功時刻から1時間の重複を含め、APIの10,000 fills上限に達した場合はDataを保存した上でActivity Capabilityをfailedにし、最終成功時刻を進めない。

---

# Phase 8: Portfolio Domain / Valuation

## Step 8-1: Current State Valuation / Portfolio計算

Phase 7で取得・正規化したBalance / Position / Account Stateに、Phase 6で実装したCurrent Price / FXを適用する。`requirements.md` の定義を正として、評価と集計を行う。

- [x] Holdings Value
- [x] Directional Value
- [x] Stablecoin Value
- [x] Net Worth
- [x] Market Exposure
- [x] Position Value
- [x] Margin JPY
- [x] Unrealized PnL JPY
- [x] Exposure Ratio

### Valuation persistence

- [x] Asset BalanceのPrice / FX / JPY ValueとSource / evaluatedAtを保存する
- [x] Perpetual PositionのPosition Value / Margin / Unrealized PnLをそれぞれの通貨でJPY評価する
- [x] Position Price FX / Margin FX / PnL FXのRate / Source / evaluatedAtを別々に保存する
- [x] Provider Account StateのJPY Value / Source / evaluatedAtを保存する
- [x] Market Data取得失敗時も既知のProvider数量・状態を0評価にせず保持する

### FX

以下を明確に分離する。

```text
Position Value
→ Price Currency
→ Price FX

Margin
→ Margin Currency
→ Margin FX

Unrealized PnL
→ PnL Currency
→ PnL FX
```

### 重要

- [x] Position ValueをNet Worthへ加算しない
- [x] Marginを資産として二重計上しない
- [x] Account EquityとBalanceを二重計上しない
- [x] Unrealized PnLを二重計上しない
- [x] Realized PnLを残高へ別途再加算しない
- [x] Long / ShortをMarket Exposureで相殺しない
- [x] Price取得不能値を0扱いしない
- [x] FX取得不能値を0扱いしない
- [x] 未取得と実際の0を区別する

### テスト

- [x] JPY Assetのidentity conversion
- [x] USD AssetのJPY換算
- [x] Crypto AssetのCurrent PriceによるJPY評価
- [x] Spotのみ
- [x] Stablecoinのみ
- [x] Long Position
- [x] Short Position
- [x] 複数Position
- [x] EquityがUnrealized PnLを含むケース
- [x] EquityがUnrealized PnLを含まないケース
- [x] Price Currency / Margin Currency / PnL Currencyが異なるケース
- [x] FX不足
- [x] Price不足
- [x] 0 Balance
- [x] Long + Short Exposure
- [x] stale price / stale FXを反映した評価状態
- [x] unavailable valuationが0へ変換されないこと
- [x] Price / Margin / PnLのCurrencyが異なるPerpetual Position
- [x] Provider Account Stateのvalue / source / evaluatedAtを保持する
- [x] Provider Account Stateの評価とEquity / Balance二重計上防止

### 実装結果

- User IDを必須とするPortfolio Valuation Serviceを追加し、Current StateのRepository Queryを認証済みUser IDで絞る。
- Holdings Valueは現物Balance全体、Directional ValueはCRYPTO、Stablecoin ValueはSTABLECOIN、FIATはHoldings / Net Worthに含める。Market ExposureはDirectional Valueと全Perpetual Position Valueの絶対額を合算する。
- Net WorthではHyperliquid StandardのPerp DEX Account Equityを各一度だけ加算し、Account Equityに含まれるPnLを重複加算しない。Unified / Portfolio MarginはSpot BalanceにPosition PnLを一度だけ反映し、Perp EquityとPosition Valueを加えない。
- `account_equity_jpy`をV11で追加し、Equity Currency FX Rate / Source / evaluatedAtとJPY額を保存する。Price / Margin / PnLは独立したJPY換算を行い、Perpetual Positionには意味別FXを保存する。
- Solanaの公式USDC / USDT MintおよびHyperliquidの公式Spot USDC / HYPE Token IDをProvider identityとして確認した。未知TokenはSymbolから推定しない。USDC / USDTをUSDへ固定せずCoinGecko評価価格からJPY換算する。
- Unit TestとPostgreSQL Testcontainers Integration TestでUser分離、Identity Mapping、Spot / Stablecoin / Perpetual計算、FX不足、既知0と未取得、JPY Identity、Stale、Equity / PnLの二重計上を確認する。

---

## Step 8-2: Portfolio Snapshot

- [x] Current PortfolioからSnapshot候補を計算する
- [x] Snapshot生成条件を判定する
- [x] `portfolio_snapshots` へ保存する
- [x] `COMPLETE` を保存する
- [x] `STALE` を保存する
- [x] `data_as_of_at` を計算する
- [x] 同一タイミングの過剰なSnapshot作成を防ぐ

### Snapshotを作成できる条件

対象UserのPortfolioについて、以下をすべて正しく計算できる場合のみ作成する。

- Net Worth
- Holdings Value
- Directional Value
- Stablecoin Value
- Market Exposure
- Unrealized PnL

### COMPLETE

Portfolio評価に必要なCurrent State、Price、FXがすべて鮮度基準を満たす。

### STALE

前回成功Current Stateや鮮度基準を超えた既知のPrice / FXを使用しているが、Portfolio全体の計算自体は可能。

### Snapshotを作成しないケース

- [x] Portfolio評価対象Connectionがなく、未接続を0円と誤認させる場合
- [x] 必要なPortfolio Capabilityが未同期
- [x] 前回成功Current Stateも存在しない
- [x] 必要なPriceが取得不能
- [x] 必要なFXが取得不能
- [x] 主要集計値の一部しか計算できない

不明値を0として補わない。

Activity等、Portfolio評価に影響しないCapabilityの失敗だけではSnapshot生成を妨げない。

### History Chart

- [ ] 7D
- [ ] 30D
- [ ] 90D
- [ ] 1Y

のSnapshotを取得できること。

存在しないSnapshotを0として生成・補間しない。

Snapshot生成頻度・最小間隔はProvider制限・運用方針を確認して決定する。Portfolio関連Current StateまたはValuationが更新された後にSnapshot判定を行い、Activity等の非Portfolio Capabilityだけの失敗では妨げない。

### Snapshot Test

- [x] COMPLETE作成
- [x] 前回成功状態によるSTALE作成
- [x] 未同期 / 前回成功状態なしでは作成しない
- [x] 必要なPrice / FX不足では作成しない
- [x] Activity失敗だけなら生成可能
- [x] 0 Balanceは取得済みの0として扱う
- [x] 未取得を0扱いしない
- [ ] 欠損履歴点を生成・0補間しない
- [x] User AがUser BのSnapshotを生成・取得できない

### 実装結果

- Portfolio Valuation結果へSnapshot専用の鮮度と`data_as_of_at`を追加し、保存対象6集計値だけで生成可否を判定する。画面用Valuationの追加指標やMargin FXの不足がSnapshotに必要な集計を妨げない場合は生成を妨げない。
- `data_as_of_at`は必要なPortfolio Capabilityの前回成功時刻、使用したCurrent Stateの取得時刻、必要なPrice / FX評価時刻の最古を使用する。
- Portfolio関連Capabilityを含むSync完了後にトランザクションコミット後のイベントでSnapshot生成を試みる。ACTIVITYだけのSyncでは新しいSnapshot判定を起動しない。
- 同一Userについて最新Snapshotと`data_as_of_at`、Status、保存する6値が一致する場合は作成を省略する。User行をロックして同時作成を直列化する。
- PostgreSQL TestcontainersでCOMPLETE / STALE、Activity失敗、未接続、Price不足、Margin FXのみ不足、重複抑制、時刻、User分離を検証した。

## Step 8-3: Connection別Portfolio評価 API

Portfolio計算後に、Connections画面用のConnection別JPY評価額と状態を集計する。

- [x] ConnectionごとにNet Worthへ反映される保有資産額を集計する
- [x] Position ValueやMarginを保有資産として二重計上しない
- [x] Connection内の必要な評価値が不足する場合は0にせずunavailable / partial statusを返す
- [x] Capabilitiesとlast successful sync metadataを返す
- [x] authenticated user idでConnectionとCurrent Stateを必ず絞る
- [x] API変更を `api-design.md` に反映する

### Test / ownership

- [x] 複数Connectionの合計がPortfolio定義と一致する
- [x] unavailable / staleを0として扱わない
- [x] User AがUser BのConnection別評価額を取得できない

### 実装結果

- 既存の `GET /api/v1/connections` と作成応答へ `portfolioValue` とCapabilityごとの `capabilitySync` を追加した。既存のCapability名配列とConnection単位の同期時刻は維持する。
- Connection評価額は、現物BalanceとNet Worthへ実際に反映されるHyperliquid Account Equity / Unrealized PnLから計算する。Perpetual Position ValueとMarginは加えない。
- 評価額は完全に計算できる場合のみ返し、既知の入力が一部でも未評価なら `amountJpy: null` / `PARTIAL` とする。最後の成功状態を再利用する場合は金額を保ち `STALE` とする。
- API応答、複数Connectionの合計、Hyperliquid Equity、部分未評価、STALE、Capability時刻、User所有範囲をPostgreSQL Testcontainersで検証した。

---

# Phase 9: Assets

## Step 9-1: Assets API

既存Assets画面を確認し、必要なQuery / DTOを実装する。

最低限:

- [x] 銘柄別合計数量
- [x] JPY評価額
- [x] Spot Holdings
- [x] Directional Assets
- [x] Stablecoins
- [x] 保有Connection内訳
- [x] Current Price
- [x] Price Currency
- [x] Price Source / evaluatedAt
- [x] 24h Price Change
- [x] Comparison period (`24h`)
- [x] Market Data Source / evaluatedAt
- [x] 24h change unavailableを `null / unavailable` で返す
- [x] Fresh / stale / unavailable情報

24h Price ChangeはPhase 6のMarket Data quote / ticker由来とし、Portfolio Snapshotから算出しない。

Connectionを跨いだAsset集約はApplication / Query Serviceで行う。

API仕様を `api-design.md` に反映する。

### API Test / ownership

- [x] 銘柄の数量 / JPY評価額 / quote項目を検証する
- [x] 24h changeの比較期間・単位・source・evaluatedAtを検証する
- [x] 24h change unavailableはnull/statusで表現し0にしない
- [x] User AがUser BのBalance / Asset summaryを取得できない

### 実装結果

- Authenticated User所有のBalanceをApplication Query Serviceで銘柄集約し、Spot Holdings / Directional / Stablecoins、JPY評価、Connection内訳を返す `GET /api/v1/assets` を追加した。
- Supported assetだけcanonical keyで統合し、未対応assetはnetworkとasset reference（referenceなしではasset key）で識別する。同じsymbolだけの銘柄統合は行わない。
- CoinGeckoのcurrent priceとH24 price changeにsource / evaluatedAt / freshnessを含める。未取得の24h changeやFXを0にしない。Balance未同期Connectionを含むcross-Connection aggregateは不完全として数量・金額をnullにする。
- API契約を `docs/api-design.md` に追加した。Testcontainersで複数Connection集約、Quote項目、H24 unavailable、stale quote、FX unavailable、未同期、未知token identity、User ownership、未認証を検証した（8 tests）。

---

## Step 9-2: Assets Frontend

静的mockをAPIへ置き換える。

- [x] TanStack Query
- [x] JPY表示
- [x] Source表示
- [x] Current Price / Price Currency表示
- [x] 24h Price Change / 24h比較期間表示
- [x] Market Data Source / evaluatedAtの状態表示
- [x] unavailable price change表示（0と区別）
- [x] Loading
- [x] Empty
- [x] Error
- [x] Partial Error
- [x] stale表示
- [x] unavailable表示

### Frontend Test

- [x] JPY金額と価格通貨の表示
- [x] 24h changeとcomparison periodの表示
- [x] unavailableを0として表示しない
- [x] Partial Error / stale表示

### 実装結果

- Assets画面の静的mockを `GET /api/v1/assets` のTanStack Queryへ置き換えた。Summary、銘柄行、AllocationをAPI値で表示し、JPYの集計・評価額と通貨単位付きCurrent Priceを区別する。
- Sourceと評価時刻を表示し、24h changeが未取得なら `24h unavailable`、staleなら `24h stale` として数値0を表示しない。既知の接続別残高はPartial時に表示し、完全な集計値が不明な場合はUnavailableとして残す。
- Loading / no-Connection / no-asset Empty / Error・Retry / Partial / stale / unavailableの画面状態を実装した。Testで表示状態とJPY・価格通貨・Quote metadataを確認した。
- Frontend検証: `pnpm test` 20 tests、`pnpm typecheck` 成功、`pnpm exec next build --webpack` 成功。既定のTurbopack buildは実行環境でCSS loaderのhelper processがportをbindできず失敗したため、Webpack buildで本番コンパイルを確認した。

---

# Phase 10: Positions

## Step 10-1: Positions API

- [x] Symbol
- [x] Long / Short
- [x] Leverage
- [x] Quantity
- [x] Entry Price
- [x] Mark Price
- [x] Liquidation Price
- [x] Price Currency
- [x] Position Value JPY
- [x] Margin Amount
- [x] Margin Currency
- [x] Margin JPY
- [x] Unrealized PnL
- [x] PnL Currency
- [x] Unrealized PnL JPY
- [x] Price FX metadata
- [x] Margin FX metadata
- [x] PnL FX metadata
- [x] Freshness

JPY換算不能値を0として返さない。

API仕様を `api-design.md` に反映する。

### API Test / ownership

- [x] Price / Margin / PnL FXが独立して適用されることを検証する
- [x] FX不足を0へ変換しない
- [x] User AがUser BのPositionを取得できない

### 実装結果

- Authenticated User所有の現行Perpetual Positionを返す `GET /api/v1/positions` を追加した。instrument / side / leverage / quantity / Entry / Mark / Liquidation、各raw currency valueとJPY評価、Connection、取得・同期時刻を含む。
- Position Value、Margin、Unrealized PnLはPrice / Margin / PnLの各通貨とFX metadataで別々に換算する。必要値・FXが未取得なら対応するJPY値はnullとし、既知のzeroだけをzeroで返す。Summaryもすべての対象Connectionを把握して全Positionを評価できる場合だけ合計を返す。
- API契約を `docs/api-design.md` に追加した。Testcontainersで異なる3 FX、PnL FX unavailable、Margin FX stale、User ownership、未認証、未接続状態を検証した。

---

## Step 10-2: Positions Frontend

Dashboard上のPerpetual Position表示を実APIへ置き換える。

- [x] Entry
- [x] Mark
- [x] Liquidation
- [x] Position Value
- [x] Margin
- [x] Unrealized PnL
- [x] JPY / USD等のCurrency表示
- [x] Loading
- [x] Empty
- [x] stale
- [x] unavailable

### Frontend Test

- [x] JPY / USD通貨単位を正しく表示する
- [x] unavailable / stale表示を検証する

### 実装結果

- DashboardのPerpetual Positions表を`GET /api/v1/positions`へ接続し、JPY合計、Position Value / Margin / Unrealized PnLの原通貨とJPY評価、Entry / Mark / Liquidation Priceの通貨、FX source / evaluation time、行と全体の状態を表示する。
- Loading / Error / Retry / Empty / STALE / PARTIAL / UNAVAILABLEを実装した。JPY換算不能値は`Unavailable`とし、0円として表示しない。
- Frontend Test 6件、全Frontend test、TypeScript typecheck、Webpack buildを確認した。

---

# Phase 11: Activity

## Step 11-1: Activity API

UserのConnectionを横断してActivity Header + Legsを取得する。論理削除済みConnectionのActivity Historyも所有Userには表示する。検索では `authenticated user id` を必須条件とし、Activity取得に `connection.deleted_at IS NULL` を必須条件として使用しない。

- [x] `occurredAt DESC, id DESC`
- [x] Cursor Pagination
- [x] Default limit
- [x] Max limit
- [x] Provider
- [x] Event Type
- [x] Original Event Type
- [x] Status
- [x] Activity Legs
- [x] direction
- [x] assetKey
- [x] symbol
- [x] quantity
- [x] originalAmount
- [x] originalCurrency
- [x] jpyValue
- [x] valuationStatus
- [x] valuationBasis
- [x] priceUsed
- [x] priceCurrency
- [x] priceSource
- [x] priceEvaluatedAt
- [x] fxRateToJpy
- [x] fxSource
- [x] fxEvaluatedAt
- [x] Fee Leg
- [x] valuation availability
- [x] 取得不能値のNULL表現

### Activity Leg

最低限以下を返せること。

```text
IN
OUT
FEE
```

Swap例:

```text
SWAP
├ OUT SOL
├ IN  USDC
└ FEE SOL
```

Headerだけ取得でき、Asset Legを特定できないEventでは、推測したLegを生成しない。

API仕様を `api-design.md` に記録する。取得済みの元情報・評価情報をAPI / Domainで失わない。Frontendが全metadataを常時表示する必要はない。

### API Test / ownership

- [x] Cursor順序とpagination境界を検証する
- [x] Activity dedup / Header + Legs / Swap / Feeを検証する
- [x] User AがUser BのActivity / Activity Legsを取得できない
- [x] 論理削除Connectionの履歴を所有Userが取得できる
- [x] 論理削除Connectionの履歴を別Userが取得できない
- [x] valuation unavailableをnullで返し、0にしない

### 実装結果

- `GET /api/v1/activities`を追加。認証User単位でActivity Header + Legs / Perpetual Fill Detailを取得し、`occurredAt DESC, id DESC` のopaque cursor paginationを実装した。Default limitは20、最大100。
- 論理削除ConnectionのActivity Historyを所有者へ返し、同期状態を失った削除済みConnectionの履歴はSTALEとする。active Connectionの未同期・partial・stale状態をResponse Summaryへ含める。
- Legの数量、original amount / currency、JPY評価、valuation basis、価格・FX metadataを保持し、評価不能値はnullを返す。Perpetual fillはFill Detailとして返し、資産移動Legへ変換しない。
- `ActivitiesApiIntegrationTests`でCursor境界、default / max limit、User分離、論理削除履歴、Swap IN / OUT / FEE、Perpetual Fill、unavailable null、partial / stale状態を検証した。重複Sync時のHeader dedupは`BitbankSyncIntegrationTests#repeatedHistoryIsIdempotentAndProviderStatusCanAdvance`で検証する。Backend `mvn verify` は163 tests、failure / error / skippedなしで成功し、最後のJSON null assertion追加後もActivity API 4 testsを再実行して成功した。

---

## Step 11-2: Activity Frontend

- [x] mock-data依存を削除
- [x] API取得
- [x] Activity Header表示
- [x] Activity Legs表示
- [x] Swap IN / OUT表示
- [x] Fee表示
- [x] 日付Grouping
- [x] Infinite Queryまたは追加読込
- [x] Loading
- [x] Empty
- [x] Error
- [x] Partial Error
- [x] stale表示
- [x] unavailable valuation表示
- [x] 元通貨とquantity / originalAmountの単位を区別する

### Frontend Test

- [x] Swap IN / OUT / FEEの表示
- [x] 元通貨 / JPY valuation表示
- [x] unavailable valuationを0表示しない
- [x] 日付Grouping / pagination追加読込

### 実装結果

- Activity画面の`mock-data.ts`を外し、`GET /api/v1/activities`をTanStack Query Infinite Queryで取得する。cursorで追加読込し、全ページをイベント発生日時の日付単位でまとめる。
- Header・provider stateとActivity LegsのIN / OUT / FEE、raw quantityとoriginal amount / currency、JPY評価を表示する。Perpetual Fill DetailはSpot資産移動と分けて表示する。
- Loading / Empty / Error / Partial Error / STALE / UNAVAILABLEを実装し、値がnullのJPY評価や数量を0と誤認させない。
- Frontend Test 8件でSwap / Perpetual表示、JPYと原通貨の区別、Unavailable、状態表示、既知emptyと未接続の区別、日付Grouping、cursor追加読込、追加ページ失敗後の再試行を検証する。

---

# Phase 12: Dashboard

Dashboardは他機能の集約になるため後半に実装する。

## Step 12-1: Portfolio Summary API

既存Dashboard UIに必要なQueryを実装する。

返すもの:

- [x] Net Worth
- [x] 24h Change response field（Step 12-2でSnapshot比較が実装されるまで値は `UNAVAILABLE`）
- [x] Holdings
- [x] Directional
- [x] Stablecoins
- [x] Market Exposure
- [x] Exposure Ratio
- [x] Unrealized PnL
- [x] Connection別評価額
- [x] Connection別Data Status
- [x] last successful sync metadata

巨大なDatabase Entity Graphを返さない。

### API Test / ownership

- [x] User AのSummaryにUser Bの保有情報が含まれない
- [x] Connection別Data Statusのpartial / stale / unavailableを検証する
- [x] 二重計上がないことを検証する

`GET /api/v1/portfolio/summary`を追加した。Sessionから解決したUser IDだけでCurrent State、Connections、Sync Stateを照会し、Entity Graphではなく集計DTOを返す。24h ChangeはSnapshot API（Step 12-2）完成までUnavailableとし、現在のMarket QuoteからPortfolio変化を推定しない。Testcontainers Integration TestでUser間分離、Net WorthへのPerpetual Position Value / Margin / PnLの二重計上防止、Connectionのpartial / stale / unavailable、Activity失敗がPortfolio状態へ影響しないことを検証した。

---

## Step 12-2: Portfolio History API

期間:

```text
7D
30D
90D
1Y
```

- [x] Snapshot取得
- [x] 時系列sort
- [x] `COMPLETE`
- [x] `STALE`
- [x] データなし状態
- [x] period validation
- [x] 欠損Snapshotを0として補間しない
- [x] 24h change計算に必要な履歴取得

API仕様を `api-design.md` に反映する。

### API Test / ownership

- [x] period validationとUser ownershipを検証する
- [x] COMPLETE / STALE / empty historyを検証する
- [x] 24h changeはPortfolio Snapshot間の比較で扱う
- [x] 24h changeに比較可能な履歴点がない場合はunavailableとし0にしない

`GET /api/v1/portfolio/history?period=7D|30D|90D|1Y`を追加した。期間内のUser所有Snapshotだけを古い順に返し、欠損点は補間しない。24h Changeは最新Snapshotと24時間前以前の直近Snapshotを直接比較し、比較点がなければUnavailableにする。PostgreSQL Testcontainers Integration Testでperiod filter / validation、User分離、COMPLETE / STALE / EMPTY、24h amount / percentage、zero / negative baseline、比較点なしを検証した。Backend `./mvnw -q verify` は172 tests実行、failures / errors 0、Live Smoke Test 1件を未指定system propertyによりskip。

---

## Step 12-3: Dashboard Frontend

既存DashboardをAPIへ接続する。

- [x] Net Worth
- [x] 24h Change
- [x] History Chart
- [x] Exposure
- [x] Service Cards
- [x] Allocation
- [x] Asset一覧
- [x] Positions
- [x] 7D / 30D / 90D / 1Y切替
- [x] COMPLETE / STALE表示
- [x] 欠損期間の適切な表示
- [x] Loading
- [x] Empty
- [x] Error
- [x] Partial Error
- [x] stale表示

存在しない履歴点を0円として描画しない。

巨大なDashboard API 1本に依存せず、画面上独立して扱う方が自然なQueryは分離する。

### Frontend Test

- [x] Net Worth / Exposure / Positionの表示とLoading / Empty / Errorを検証する
- [x] COMPLETE / STALEを区別して表示する
- [x] Snapshot欠損点を0円として描画しない
- [x] User切替 / Logoutで前Userのcacheを表示しない

**実装結果:** DashboardをPortfolio Summary / History、Assets、Positionsの独立したTanStack Queryへ接続した。Net Worth、Snapshot比較の24h変化、JPY Exposure、Connection cards、Allocation、Asset一覧、Position表をAPIデータで表示する。履歴点は補間・0埋めせず、COMPLETE / STALE、Loading / Empty / Error / Partial Errorを表示する。認証User ID変更とLogout時にユーザー所有Query cacheを消去する。Dashboard画面Testで表示・期間切替・状態と未取得値、AuthBoundary TestでUser切替 / Logout時のcache消去を確認した。Frontend `pnpm test` は40 tests、失敗0。`pnpm typecheck` 成功。`pnpm exec next build --webpack` 成功。通常の`pnpm build`はTurbopackのsandbox内port bind制限で失敗したためwebpack buildで確認した。

---

## Step 12-4: Connections評価額 / Manual Sync Frontend統合

Provider Sync APIはStep 7-3で実装済み。Portfolio / Valuation後にConnections画面で評価額とManual SyncをUIへ統合する。

- [x] ConnectionごとのJPY評価額を表示する
- [x] Capabilitiesを表示する
- [x] fresh / stale / unavailableを表示する
- [x] lastAttemptAt / last successful syncを表示する
- [x] Syncボタンから対象Connectionの `POST /api/v1/connections/{connectionId}/sync` を呼ぶ
- [x] Syncing中はボタンをdisableし、同一Connectionへの重複操作を防ぐ
- [x] Success / Partial Failure / Errorを表示する
- [x] Sync完了後にConnection状態を再取得する
- [x] `connections`、`assets`、`positions`、`activity`、Portfolio Summary / History等の関連TanStack Queryをinvalidateする
- [x] Credentialを表示・再送しない
- [x] JPY評価不能値を0にしない

### Test / ownership

- [x] Sync開始中 / Success / Partial Failure / Error表示を検証する
- [x] 同一Connectionのボタンが同期中disableされる
- [x] Sync成功後のquery invalidation / 状態再取得を検証する
- [x] User AがUser BのConnection IDでSyncできないことをAPI / Controller Testで検証する
- [x] unavailable valuationを0と区別する

**実装結果:** Connection一覧にJPY評価額、Complete / Stale / Partial / Unavailable、Capability別状態、最終試行 / 成功時刻を接続した。SyncはCSRF付きのbodyなしPOSTで開始し、Sync Runをpollして結果を表示する。同じConnectionのSync / Disconnectを実行中disableし、終了後Connections / Assets / Positions / Activity / Portfolio Summary / Historyをinvalidateする。Credentialを表示・送信せず、評価不能額はUnavailableのまま表示する。Frontend `pnpm test` は45 tests、失敗0。`pnpm typecheck` と`pnpm exec next build --webpack` 成功。Backend `BitbankSyncIntegrationTests.userCannotSyncOrReadAnotherUsersConnectionCredentialBalanceActivityOrSyncRun` は1 test、失敗 / error 0。

---

# Phase 13: Sync改善

## Step 13-1: Retry / Timeout

ProviderごとのHTTP clientで以下を実装する。

- [x] Connection Timeout 3秒 / Response Timeout 5秒を設定する。Solana / Market Dataは環境設定で変更可能。
- [x] read-only Provider clientで通信I/O failure、HTTP 408 / 429 / 500 / 502 / 503 / 504をRetry対象に分類する。TLS / 証明書、認証、権限、Validation、その他4xxはRetryしない。
- [x] 最大3回（初回を含む）に制限する。
- [x] 200ms開始の指数backoff（上限2秒）にfull jitterを適用する。
- [x] `Retry-After`のdelta-seconds / HTTP-dateを守る。指定待機が2秒を超える場合は早期Retryせず、そのresponseで失敗する。
- [x] Provider request / responseやCredentialをlogへ出さない。

以下を自動Retryしない。

```text
Authentication Error
Permission Error
Validation Error
```

Retryはbitbank、Solana RPC / Helius、Hyperliquid Info、Market Dataのread-only HTTP clientだけに適用する。書込APIを追加する場合は同じclientを流用しない。HTTP status以外のProvider response bodyに含まれるエラーはHTTP Retryへ通さず、Providerごとの明示的な仕様に従って分類する。JUnit fake-response TestでHTTP status分類、通信例外、3回上限、指数backoff / jitter、両方の`Retry-After`形式、指定待機上限、Interrupted時の処理を確認する。

2026-09-28、Java 25 / Docker Testcontainersで`./mvnw -q clean verify`を実行し、178 tests、failures 0 / errors 0 / skipped 1（system property未指定のLive Smoke Test）を確認した。Retry専用Testは6 testsすべて成功した。

---

# Phase 14: Frontend品質 / Regression

## Step 14-1: TypeScript

- [x] `typescript.ignoreBuildErrors` を解除する
- [ ] typecheck scriptを追加する
- [ ] Production buildが型エラーなしで成功する
- [ ] API Response DTOの型をFrontendで定義する
- [ ] Decimal stringを不用意にJavaScript numberへ変換しない
- [ ] lint / typecheck / production buildを実行し成功させる

---

## Step 14-2: Frontend横断Regression Test

Vitest / React Testing Libraryの基盤はPhase 3で導入し、各画面・操作の機能Testは対応するFrontend Stepで作成・実行する。本Stepでは全画面のRegressionを行い、初めて各機能のTestを作成する計画にしない。

- [ ] Sign in / Route Guard / Logout
- [ ] Connections Add / Delete / Manual Sync
- [ ] 金額・Currency・JPY / USD表示
- [ ] Loading / Empty / Error / Partial Error
- [ ] stale / unavailable
- [ ] Provider別Form validation
- [ ] Activity Header / Legs / Swap / Fee
- [ ] History欠損表示
- [ ] Auth User変更・Logout時に前UserのQuery cacheが残らない

---

# Phase 15: Backend Integration / Regression Test

本Phaseは、Phase 1〜13の各機能Stepで作成・実行済みの単体 / Controller / Repository / Adapter / Sync Testを、Testcontainers PostgreSQLを含む統合環境で横断Regressionする。Feature Testを初めて作成するPhaseとして扱わない。

## User ownership regression

User A / User Bを作成し、以下すべてのResourceについてUser AがUser BのResourceを取得・変更・削除・同期できないことを再確認する。

- [ ] Connection
- [ ] Credential
- [ ] Balance
- [ ] Position
- [ ] Provider Account State
- [ ] Activity
- [ ] Activity Legs
- [ ] Sync Run / Sync Result / Sync State
- [ ] Portfolio Snapshot
- [ ] Connection別評価額

Repository / Application / Controllerの各境界を確認する。DBのComposite Foreign Keyについても、Connection Userと子Entity Userが食い違う行を保存できないことを再確認する。

## Provider / Sync integration regression

- [ ] 各Provider fixture / Mock APIとの正規化・Error mapping
- [ ] Success / Failure / Partial Failure
- [ ] 前回成功Current State保持
- [ ] lastAttemptAt / lastSuccessAt / Capability状態
- [ ] 同一Connection重複Sync防止と状態解放
- [ ] Activity Header / Legsの同一Transaction保存
- [ ] 再同期時のdedup
- [ ] 論理削除ConnectionのActivity / Sync History保持・User本人への提供

## Portfolio / Snapshot regression

- [ ] Net Worth / Market Exposure / Position Value / Margin / Unrealized PnL
- [ ] Price / Margin / PnL FX分離と二重計上防止
- [ ] price / FX unavailable、stale、取得済み0の区別
- [ ] COMPLETE / STALE Snapshot
- [ ] 必須Current State / Price / FX不足時にSnapshotを作らない
- [ ] Activity失敗だけではSnapshot作成を妨げない
- [ ] 欠損履歴点を0補間しない

## Credential / Retention regression

- [ ] encryption / decryption / random nonce / key version
- [ ] invalid ciphertext / plaintext fallbackなし / Credential非公開・非記録
- [ ] Connection削除時CredentialとCurrent Stateを削除
- [ ] Activity / Sync Run / Portfolio Snapshotを削除しない
- [ ] TTL JobやRetention目的Partitionが追加されていない

## Integration実行条件

- [ ] Testcontainers PostgreSQLで空Schemaから全Flyway Migrationを適用する
- [ ] API認証 / User ownership / DB制約を組み合わせたController Integration Testを実行する
- [ ] 対象テストがすべて成功する

---

# Phase 16: Demo Data

実サービスを持っていない閲覧者でもPortfolioを確認できるようにする。

実データ版MVP完成後に対応する。

- [ ] Demo Modeの方式を設計する
- [ ] Credential不要のDemo User / fixtureを検討する
- [ ] Demo Connections
- [ ] Demo Portfolio
- [ ] Demo Activity Header / Legs
- [ ] Demo Positions
- [ ] Demo History
- [ ] COMPLETE / STALE例
- [ ] 実Userデータと完全に分離する

このPhaseまでは先行実装しない。

---

# Phase 17: CI

GitHub Actionsを追加する。

## Frontend

- [ ] lint
- [ ] typecheck
- [ ] test
- [ ] production build

## Backend

- [ ] compile
- [ ] unit test
- [ ] integration test
- [ ] Testcontainers test

## Container

- [ ] Docker build
- [ ] Docker Compose configuration validation

CIでは実Provider CredentialやGoogle Client Secretを使わない。

---

# Phase 18: Deployment

## Step 18-1: Production Compose

以下を本番用に構成する。

- [ ] Frontend
- [ ] Backend
- [ ] PostgreSQL
- [ ] Caddy

確認する。

- [ ] Production SecretをImageへ含めない
- [ ] PostgreSQLを外部公開しない
- [ ] Backend内部Portを不要に外部公開しない
- [ ] Named Volume
- [ ] restart policy
- [ ] health check
- [ ] log rotation

---

## Step 18-2: Lightsail

- [ ] Instance選定
- [ ] Region選定
- [ ] Static IP
- [ ] Domain
- [ ] DNS
- [ ] Caddy
- [ ] HTTPS
- [ ] Firewall
- [ ] Google OAuth Production Redirect URL
- [ ] Production secrets
- [ ] CoinGecko利用Plan / licenseと、外部ユーザー向けUser Agreement / Privacy Policy / data limitation disclaimerを公開前に確認する
- [ ] Encryption Key
- [ ] Named Volume
- [ ] DB Backup
- [ ] Restore確認
- [ ] 月額費用確認

AWS Secrets ManagerはMVP必須としない。

本番Secretの具体的な保管方式はDeployment時に確定する。

---

# Phase 19: MVP最終確認

## Security

- [ ] 他UserのConnectionへアクセスできない
- [ ] 他UserのBalanceへアクセスできない
- [ ] 他UserのPositionへアクセスできない
- [ ] 他UserのActivityへアクセスできない
- [ ] API CredentialをFrontendへ返さない
- [ ] Credentialがログへ出ない
- [ ] Encryption KeyがGitへ含まれない
- [ ] SecretがGitへ含まれない
- [ ] DBをInternetへ直接公開していない
- [ ] Backend内部Portを不要にInternetへ直接公開していない
- [ ] Session / CSRFが正しく動作する

## Providers

以下の資産を統合表示できる。

- [ ] bitbank
- [ ] Solana
- [ ] Hyperliquid

## Market Data

- [ ] Current Crypto Price / USD-JPY FX
- [ ] 24h price changeはMarket Data quote由来
- [ ] price / FX unavailable・staleを0扱いしない
- [ ] CoinGecko attributionをDashboard / Assetsへ表示し、外部ユーザー提供前に利用Plan / Termsを確認する
- [ ] Market Price History DBがない

## Portfolio

- [ ] Net Worth
- [ ] Holdings Value
- [ ] Directional Value
- [ ] Stablecoin Value
- [ ] Market Exposure
- [ ] Position Value
- [ ] Margin
- [ ] Unrealized PnL
- [ ] Exposure Ratio
- [ ] JPY換算

二重計上がないこと。

## Dashboard

- [ ] Net Worth
- [ ] JPY表示
- [ ] 24h Change
- [ ] History
- [ ] 7D
- [ ] 30D
- [ ] 90D
- [ ] 1Y
- [ ] Allocation
- [ ] Exposure
- [ ] Positions
- [ ] COMPLETE / STALE
- [ ] History欠損点を0表示しない

## States

- [ ] Loading
- [ ] Empty
- [ ] Error
- [ ] Partial Failure
- [ ] stale
- [ ] unavailable
- [ ] not synced

を確認する。

## Activity

- [ ] Service横断表示
- [ ] Cursor Pagination
- [ ] 重複排除
- [ ] Header / Legs
- [ ] Swap
- [ ] Fee
- [ ] originalAmount / originalCurrency
- [ ] valuation metadata
- [ ] JPY valuation unavailable
- [ ] 削除済Connectionの履歴を所有Userへ表示

を確認する。

## Connections

- [ ] Provider別Add Form / validation
- [ ] Delete / Disconnect
- [ ] Manual Sync
- [ ] Connected
- [ ] Syncing
- [ ] Error / Partial Failure
- [ ] Disconnected
- [ ] Capabilities
- [ ] ConnectionごとのJPY評価額
- [ ] fresh / stale / unavailable
- [ ] Last Attempt / Last Successful Sync
- [ ] マスク済みIdentifier

## Retention

MVPでは以下が自動削除されないこと。

- [ ] Activity / Activity Legs
- [ ] Sync Run / Sync Run Results
- [ ] Portfolio Snapshot

Connection削除時:

- [ ] Credentialは削除
- [ ] Current Balanceは削除
- [ ] Current Positionは削除
- [ ] Provider Account Stateは削除
- [ ] Activity Historyは保持
- [ ] Sync Historyは保持
- [ ] Portfolio Snapshotは保持

## Quality

- [ ] Frontend lint成功
- [ ] Frontend typecheck成功
- [ ] Frontend test成功
- [ ] Frontend build成功
- [ ] Backend compile成功
- [ ] Backend unit test成功
- [ ] Backend integration test成功
- [ ] Docker build成功
- [ ] Docker Compose起動成功
- [ ] Flyway migration成功
- [ ] Production deployment成功
- [ ] Backup / Restore確認

---

# Luna実行ルール

実装時は以下を守る。

1. 原則として本書のStep順に進める。
2. ユーザーから指定されたStepだけを実装する。
3. 指定されていない次Stepへ勝手に進まない。
4. 実装前に関連する `requirements.md`、`screen-design.md`、`development-guidelines.md`、`database-design.md` を確認する。
5. APIを追加・変更した場合は `docs/api-design.md` を実装と一致するよう作成・更新する。
6. APIはDBテーブルをそのまま公開せず、画面・ユースケースに必要なDTOとして設計する。
7. DB Schema変更が必要な場合は、変更前に `database-design.md` との整合性を確認する。
8. 外部Provider仕様を推測で実装しない。公式仕様を確認する。
9. Providerから取得できない値を推測値や0で埋めない。
10. `requirements.md` のNet Worth / Market Exposure等の金融定義を変更しない。
11. Position Value、Margin、Account Equity、Unrealized PnLを二重計上しない。
12. Price / Margin / PnLのCurrency / FXを暗黙に共用しない。
13. 実Credential、Secret、個人データをGitへCommitしない。
14. API Key / Secret、Encryption Key、Cookie、Session ID、Token、Credential ciphertextをログへ出さない。
15. User所有Resourceの取得に、所有者確認なしの単純な `findById()` を使わない。
16. Backendでは認証済みSessionからUserを特定し、Client supplied userIdを認可根拠にしない。
17. Current StateのSync失敗時に、前回成功データを削除しない。
18. Activity Header / Legsはイベント単位で一貫して保存する。
19. Snapshotを不完全な値や0補完で作成しない。
20. History Chartの欠損Snapshotを0として補間しない。
21. Activity / Sync Run / Portfolio SnapshotのRetention削除処理をMVPに勝手に追加しない。
22. 既存Frontend UIを不要に全面書き換えしない。
23. requirementsに存在しない機能を勝手に追加しない。
24. MVPに不要なMicroservices / Kafka / Redis / Kubernetes等を導入しない。
25. 各Step終了時に、そのStepで必要なテストを実行する。各機能Testは機能Step内で作成し、後半のRegression Phaseへ先送りしない。
26. 各Step終了時に、実装と設計資料の差分を確認する。
27. 各Step終了時に、そのStepに関係する変更だけをCommitする。
28. テスト失敗や未解決事項を隠してStep完了扱いにしない。
29. User所有Resourceを扱う各Vertical SliceでUser A / User Bのアクセス境界を検証し、最終Backend Regressionでも全Resourceを再確認する。

---

# Step完了報告フォーマット

各Step完了時は以下の形式で報告する。

```text
Completed:
- ...

Changed:
- ...

Tests:
- ...

Docs updated:
- ...

Remaining / unresolved:
- ...

Commit:
- ...
```

`Remaining / unresolved` がある場合は、後続Stepへ進む前に影響範囲を明記する。

---

# 次に実行するStep

Database設計レビューは完了している。

次は以下から開始する。

```text
Phase 3
Step 3-1: Google OAuth2 Login
```

今回の連続実装では、完了条件を満たしたStepごとにCommitし、次の未完了Stepへ進む。
