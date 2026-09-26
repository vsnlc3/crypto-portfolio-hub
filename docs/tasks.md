# Crypto Portfolio Hub 実装タスク

文書ステータス: Draft

関連文書:

- [requirements.md](./requirements.md)
- [screen-design.md](./screen-design.md)
- [development-guidelines.md](./development-guidelines.md)
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
Activity / Activity Legs
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

- [ ] User
- [ ] Connection
- [ ] ConnectionCredential
- [ ] ConnectionSyncState
- [ ] SyncRun
- [ ] SyncRunResult
- [ ] AssetBalance
- [ ] PerpetualPosition
- [ ] ProviderAccountState
- [ ] Activity
- [ ] ActivityLeg
- [ ] PortfolioSnapshot

### 方針

- [ ] Many-to-Oneは原則LAZY
- [ ] 巨大なObject Graphを作らない
- [ ] One-to-Manyを常時EAGER取得しない
- [ ] `CascadeType.ALL` を機械的に使わない
- [ ] History EntityをConnection操作から誤削除しない
- [ ] JPA EntityをREST DTOとして直接公開しない
- [ ] User所有ResourceのRepository QueryはuserIdを条件に含める

### 禁止

User所有Resourceに対し、

```text
findById(id)
```

だけで取得し、そのままAPIへ返す実装を作らない。

### 完了条件

- [ ] EntityとSchemaが一致する
- [ ] Repository Testの土台と、User IDを条件にしたRepository Testがある
- [ ] User ownershipを検索条件に含められる

---

# Phase 3: Authentication / User

## Step 3-1: Google OAuth2 Login

- [ ] Spring Security OAuth2 Loginを設定する
- [ ] Google OIDC `sub` を取得する
- [ ] 初回ログイン時にUserを作成する
- [ ] 再ログイン時に既存Userへ紐付ける
- [ ] EmailではなくGoogle SubjectをIdentityの基準にする
- [ ] Backend Sessionを利用する
- [ ] HttpOnly Cookieを使用する
- [ ] ProductionでSecure Cookieを使用できる構成にする
- [ ] SameSite方針を設定する
- [ ] Logoutを実装する
- [ ] CSRF対策を有効にする

### API

このStepで必要なAPIを設計し、`api-design.md` を新規作成または更新する。

候補:

```text
GET  /api/v1/auth/me
POST /api/v1/auth/logout
```

Endpoint名は実装時に既存RoutingとUIを確認して確定する。

### 完了条件

- [ ] Googleログインできる
- [ ] ログインUserをBackendで特定できる
- [ ] 未認証状態では保護APIへアクセスできない
- [ ] ClientからUser IDを指定して認証を回避できない
- [ ] Logout後に保護APIへアクセスできない

### テスト

- [ ] OAuth成功・失敗をMockで検証する
- [ ] Google `sub` から同一Userへ紐付くことを検証する
- [ ] Email変更・重複だけでUser identityが変わらないことを検証する
- [ ] 未認証拒否、Logout後のSession無効化、CSRF保護APIを検証する
- [ ] Client supplied userIdを認可根拠にできないことを検証する

---

## Step 3-2: Frontend Sign in

- [ ] Sign in画面を実装する
- [ ] Googleログインへの導線を追加する
- [ ] 未認証時のRoute Guardを追加する
- [ ] 固定プロフィール表示をGoogleログインUserへ置き換える
- [ ] Logoutを接続する
- [ ] 認証Loadingを表示する
- [ ] 認証Errorを表示する
- [ ] Vitest / React Testing LibraryのFrontend Test基盤を用意する

### テスト

- [ ] Route Guardが未認証時にSign inへ遷移する
- [ ] 認証中 / 認証Error / Logout後の画面状態を検証する

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

- [ ] Balance API
- [ ] Activity / Transaction API
- [ ] Deposit / Withdrawal API
- [ ] API Key
- [ ] API Secret
- [ ] API Key署名方式
- [ ] 必要なread-only権限
- [ ] 注文・出金権限が不要であること
- [ ] 接続確認方法
- [ ] Account identifierの有無
- [ ] Connection作成時に必要な入力項目
- [ ] Balanceのavailable / locked / total semantics
- [ ] Event ID
- [ ] Pagination
- [ ] Rate Limit
- [ ] 履歴取得可能範囲
- [ ] BUY / SELL時に取得できる資産情報
- [ ] Base / Quote Assetの数量
- [ ] Fee Asset / Fee Quantity
- [ ] Activity Legsへの変換方法
- [ ] Event dedup key

---

## Step 4-2: Solana

確認する。

- [ ] Wallet Address形式
- [ ] Address validation
- [ ] Connection作成時に必要な入力項目
- [ ] Native SOL Balance
- [ ] SPL Token Balance
- [ ] Token Mintによる識別
- [ ] Token metadata取得範囲
- [ ] Transaction履歴
- [ ] Transfer分類
- [ ] Swap分類
- [ ] SwapのOUT asset
- [ ] SwapのIN asset
- [ ] Fee asset / quantity
- [ ] Transaction Signature
- [ ] Activity Event ID / dedup key
- [ ] RPC / API Provider
- [ ] Historical Data取得範囲
- [ ] Pagination
- [ ] Rate Limit
- [ ] RPC費用

秘密鍵・Seed Phraseは要求しない。

---

## Step 4-3: Hyperliquid

確認する。

- [ ] Account Address
- [ ] Address validation
- [ ] Connection作成時に必要な入力項目
- [ ] Spot Balance
- [ ] Perpetual Position
- [ ] Entry Price
- [ ] Mark Price
- [ ] Liquidation Price
- [ ] Position Quantity
- [ ] Leverage
- [ ] Margin
- [ ] Collateral
- [ ] Account Equity
- [ ] Unrealized PnL
- [ ] Funding
- [ ] Activity
- [ ] Stable Position Key
- [ ] Event ID
- [ ] Historical Data取得範囲
- [ ] Pagination
- [ ] Rate Limit

特に以下を確認する。

```text
Account EquityにUnrealized PnLが含まれるか

Account EquityとSpot / Collateral Balanceの包含関係

Position MarginとAccount Collateralの包含関係

Price Currency
Margin Currency
PnL Currency

FundingのAsset / Amount / Direction
```

Net Worthへ二重計上しないため、Providerの数値定義を公式仕様で確認する。

---

## Step 4-4: Market Data

以下を決定する。

- [ ] Current Crypto Price取得元
- [ ] USD / JPY FX取得元
- [ ] 24h price changeを含むmarket quote / tickerの取得可否・比較期間
- [ ] 対応Assetとsymbol / asset_keyからMarket Data IDへの対応
- [ ] Price Currency
- [ ] Price Source / evaluatedAt
- [ ] FX Source / evaluatedAt
- [ ] Price timestamp
- [ ] FX timestamp
- [ ] Rate Limit
- [ ] Failure時の扱い
- [ ] stale判定に利用する鮮度基準
- [ ] JPYの場合のidentity conversion
- [ ] Provider障害時のfallbackを設けるか
- [ ] 価格変化・FXが取得不能な場合にunavailableとして返せるか

MVPで独立したMarket Price History DBは作らない。Provider仕様・Market Data Providerの公式資料を確認し、取得できる項目・頻度・条件を推測で確定しない。

### Connectionsへ引き継ぐ確定事項

- bitbank Create RequestのAPI Key / API Secret、read-only権限、接続確認、identifier、入力項目
- Solana Create RequestのWallet Address形式、validation、入力項目
- Hyperliquid Create RequestのAccount Address形式、validation、入力項目

これらの確認が完了するまで、次のConnections PhaseでProvider固有DTO / validation / Formを確定しない。

---

# Phase 5: Connections

## Step 5-1: Credential暗号化

- [ ] AES-256-GCM暗号化を実装する
- [ ] nonceをCredentialごとに生成する
- [ ] key versionを保存する
- [ ] Encryption Keyを環境変数等のBackend Secretから取得する
- [ ] Encryption KeyをDBへ保存しない
- [ ] Encryption KeyをDocker imageへ埋め込まない
- [ ] Encryption Key未設定時に平文保存へfallbackしない
- [ ] CredentialをResponseへ返さない
- [ ] Credentialをログへ出さない
- [ ] Credential ciphertextもログへ出さない
- [ ] Connection削除時にCredentialを即時削除する

### テスト

- [ ] encrypt → decrypt
- [ ] 同一値でも異なるnonce
- [ ] 不正ciphertext
- [ ] 不正tag
- [ ] key未設定
- [ ] key version
- [ ] Credential削除

---

## Step 5-2: Connection Backend

Phase 4で確認したProvider仕様に基づき、Provider別Create Request、validation、Connection一覧・削除を実装する。

- [ ] Connection一覧
- [ ] Connection追加
- [ ] Connection削除
- [ ] ProviderごとのCreate Request / Response DTO
- [ ] Providerごとのvalidation
- [ ] User ownership
- [ ] display name
- [ ] masked identifier
- [ ] ProviderごとのCapabilities
- [ ] Connection status
- [ ] last attempt / last success
- [ ] 論理削除
- [ ] Current State削除方針をDB設計どおり実装する
- [ ] CredentialはConnection削除時に即時削除する
- [ ] Historyは保持し、削除済ConnectionをActive一覧に返さない

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

- [ ] Provider別Create Requestのvalidationを検証する
- [ ] Repository / ControllerでUser AがUser BのConnectionを取得・変更・削除できないことを検証する
- [ ] Client supplied userIdで所有権を変更できないことを検証する
- [ ] 論理削除後のConnectionがActive一覧に含まれないことを検証する
- [ ] User AがUser BのCredentialを取得できず、Connection操作からBのCredentialへアクセスできないことを検証する
- [ ] Connection削除時にCredential / Current Stateを削除し、Activity / Sync Historyを保持することを検証する

---

## Step 5-3: Connections Frontend

既存Connections UIを実データへ接続する。

- [ ] TanStack Query導入
- [ ] Connection一覧
- [ ] Loading
- [ ] Empty
- [ ] Error
- [ ] Add Source
- [ ] Delete / Disconnect
- [ ] マスク済みIdentifier
- [ ] Connection Status
- [ ] Capabilities
- [ ] last attempt / last successful sync
- [ ] Toast
- [ ] React Hook Form
- [ ] Zod

### 完了条件

- [ ] Provider仕様で確定したbitbank / Solana Wallet Address / HyperliquidのFormからConnectionを追加できる
- [ ] Connectionを一覧表示できる
- [ ] Connectionを削除できる
- [ ] 他UserのConnectionが表示されない
- [ ] 入力validationとAPI validationの結果を表示する
- [ ] Credentialが画面/API Response/ログへ露出しないことをテストする

このPhaseではJPY評価額を表示しない。Portfolio計算完了後にConnections画面へ追加する。手動Sync操作もProvider Sync API完成後のDashboard Phaseで統合する。

### Frontend Test

- [ ] Provider別Form、validation、Loading / Empty / Errorを検証する
- [ ] Connection追加・削除後の一覧更新を検証する

---

# Phase 6: Market Data / Valuation基盤

Phase 4で取得元・仕様を確認した後、Market Data Providerを実装する。Market Price History DBは作成しない。Provider Adapter / SyncによるPortfolioデータ取得はPhase 7、そのデータに価格・FXを適用するValuationとPortfolio集計はPhase 8で行う。

## Step 6-1: Market Data Provider

- [ ] 公式仕様に基づくCurrent Crypto Price取得
- [ ] USD / JPY FX取得
- [ ] `symbol` / `asset_key` とMarket Data IDの対応
- [ ] Price Currencyを保持する
- [ ] Price Source / `price_evaluated_at` を追跡する
- [ ] FX Source / `fx_evaluated_at` を追跡する
- [ ] JPYのidentity conversionはRate `1` / Source `IDENTITY` とする
- [ ] Rate Limit / Timeoutを扱う
- [ ] Provider Errorを分類する
- [ ] Price / FXの鮮度基準を適用できる
- [ ] fallbackはPhase 4で採用を決めた場合だけ実装する

## Step 6-2: 24h Market Quote

Assetsで表示する銘柄ごとの価格変化はMarket Data Providerのcurrent quote / tickerとして扱う。Portfolio Snapshotから算出しない。

- [ ] 固定比較期間 `24h` のprice changeを取得する
- [ ] Providerの返すchangeの単位（価格差 / percentage等）を保持する
- [ ] Comparison periodを `24h` として返す
- [ ] Market Data Sourceを返す
- [ ] quoteの `evaluatedAt` を返す
- [ ] Providerが値を返さない場合は `null / unavailable` とする
- [ ] 取得不能値を推測値や0にしない
- [ ] Price History DBやTicker History DBを作らない

### Tests

- [ ] Current priceの取得とsymbol / asset_key mapping
- [ ] Price Currency / Source / evaluatedAtの保持
- [ ] USD / JPY FXの取得とSource / evaluatedAtの保持
- [ ] Rate Limit / Timeout / Error分類
- [ ] price unavailable / FX unavailable
- [ ] stale price / stale FXの判定
- [ ] 24h quoteのcomparison period / source / evaluatedAt
- [ ] 24h change unavailable
- [ ] 24h changeにPortfolio Snapshotを使用していないこと

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

- [ ] Sync APIの共通契約を定め、`api-design.md` へ記録する
- [ ] 同一Connectionの同時Syncを防止し、`SYNCING` を表現する
- [ ] 異なるConnectionは独立してSyncできる
- [ ] Success / Error / Partial Failureいずれでも同期中状態を解放する
- [ ] `lastAttemptAt` と `lastSuccessAt` の意味を分けて更新する
- [ ] 認証済みUserとConnection IDの両方で所有権を検証する

### 共通Sync Test

- [ ] 同一Connectionの重複Sync拒否 / 競合制御
- [ ] 異なるConnectionの並行Sync
- [ ] Error後のSync状態解放
- [ ] User AがUser BのConnectionのSyncを実行できない

---

## Step 7-2: Solana Adapter

- [ ] Wallet Address validation
- [ ] Native SOL Balance取得
- [ ] SPL Token Balance取得
- [ ] asset_key正規化
- [ ] 共通Balanceモデルへの変換
- [ ] Transaction取得
- [ ] Transfer正規化
- [ ] Swap正規化
- [ ] Activity Header生成
- [ ] IN / OUT / FEE Activity Legs生成
- [ ] Event dedup key
- [ ] Error分類
- [ ] Timeout
- [ ] Rate Limit考慮
- [ ] fixture / Mock HTTPテスト

取得できない情報を推測して埋めない。

---

## Step 7-3: Solana Sync

- [ ] Manual Sync
- [ ] Sync Run作成
- [ ] Sync Run Result
- [ ] Capability Sync State
- [ ] Current Balance更新
- [ ] Activity Header / Legs保存
- [ ] 同一Activityを重複保存しない
- [ ] Activity Header / Legsを同一Transactionで保存する
- [ ] Providerから取得できたoriginalAmount / originalCurrencyとvaluation metadataを保持する
- [ ] Historical Legを現在価格で後から再評価しない。評価情報がなければNULL / unavailableにする
- [ ] Sync失敗時に前回成功Current Stateを保持する
- [ ] stale判定に必要な情報を保持する
- [ ] Balance完全成功時のみCurrent Balance集合を更新する
- [ ] Activity失敗がBalance成功を巻き戻さない
- [ ] User AがUser BのConnection / Sync / Balance / Activityへアクセスできない
- [ ] User AがUser BのActivity Legsを親Activity経由でも取得できない

### API

```text
POST /api/v1/connections/{connectionId}/sync
```

共通Sync契約を利用する。Provider別のCapability、Response、Partial Failure表現を `api-design.md` に記録する。

### 完了条件

- [ ] 1つのCapability失敗が他Capabilityの成功を失敗扱いにしない
- [ ] 前回成功値が残る
- [ ] 未取得と0を区別できる
- [ ] Current State・Activityを取得したUser所有Connectionだけへ保存できることを検証する

---

## Step 7-4: bitbank Adapter

- [ ] Credential復号処理の呼出し
- [ ] API署名
- [ ] Provider DTO
- [ ] Balance取得・正規化
- [ ] Activity / Transaction取得・正規化
- [ ] BUY / SELLのActivity Header / Legs
- [ ] Deposit / WithdrawalのActivity Header / Legs
- [ ] Fee Leg
- [ ] dedup key mapping
- [ ] Error mapping
- [ ] Timeout / Rate Limit
- [ ] fixture / Adapter Test

取得できない項目を推測せず、必要情報がないLegはNULL / unavailableにする。

---

## Step 7-5: bitbank Sync

- [ ] Sync Run / Result / Capability State
- [ ] Balance Current State更新
- [ ] Activity Header / Legsを同一Transactionで保存
- [ ] originalAmount / originalCurrencyと取得済みvaluation metadataを保持
- [ ] 取得できない値を推測せず、NULL / unavailableにする
- [ ] 重複排除・再実行時の冪等性
- [ ] Partial Failure
- [ ] Sync失敗時に前回成功値を保持
- [ ] lastAttemptAt / lastSuccessAt
- [ ] stale情報
- [ ] User ownership
- [ ] User AがUser BのCredential / Balance / Activity / Sync情報へアクセスできない
- [ ] Sync Integration Test

---

## Step 7-6: Hyperliquid Adapter

- [ ] Provider DTO
- [ ] Spot Balance取得・正規化
- [ ] Account State取得・正規化
- [ ] Position取得・正規化
- [ ] Funding / Activity取得
- [ ] Activity Header / Legs
- [ ] Event ID / Position stable key mapping
- [ ] Account Equityの意味を反映
- [ ] Collateralの意味を反映
- [ ] Unrealized PnLの意味を反映
- [ ] Price Currencyを保持
- [ ] Margin Currencyを保持
- [ ] PnL Currencyを保持
- [ ] Provider Error mapping
- [ ] Timeout / Rate Limit
- [ ] fixture / Adapter Test

---

## Step 7-7: Hyperliquid Sync

- [ ] Sync Run / Result / Capability State
- [ ] Balance / Account State / Position Current State更新
- [ ] Activity Header / Legsを同一Transactionで保存
- [ ] originalAmount / originalCurrencyと取得済みvaluation metadataを保持
- [ ] 取得できない値を推測せず、NULL / unavailableにする
- [ ] 重複排除・再実行時の冪等性
- [ ] Partial Failure
- [ ] Sync失敗時に前回成功値を保持
- [ ] lastAttemptAt / lastSuccessAt
- [ ] stale情報
- [ ] User ownership
- [ ] User AがUser BのBalance / Position / Account State / Activity / Sync情報へアクセスできない
- [ ] Sync Integration Test

---

# Phase 8: Portfolio Domain / Valuation

## Step 8-1: Current State Valuation / Portfolio計算

Phase 7で取得・正規化したBalance / Position / Account Stateに、Phase 6で実装したCurrent Price / FXを適用する。`requirements.md` の定義を正として、評価と集計を行う。

- [ ] Holdings Value
- [ ] Directional Value
- [ ] Stablecoin Value
- [ ] Net Worth
- [ ] Market Exposure
- [ ] Position Value
- [ ] Margin JPY
- [ ] Unrealized PnL JPY
- [ ] Exposure Ratio

### Valuation persistence

- [ ] Asset BalanceのPrice / FX / JPY ValueとSource / evaluatedAtを保存する
- [ ] Perpetual PositionのPosition Value / Margin / Unrealized PnLをそれぞれの通貨でJPY評価する
- [ ] Position Price FX / Margin FX / PnL FXのRate / Source / evaluatedAtを別々に保存する
- [ ] Provider Account StateのJPY Value / Source / evaluatedAtを保存する
- [ ] Market Data取得失敗時も既知のProvider数量・状態を0評価にせず保持する

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

- [ ] Position ValueをNet Worthへ加算しない
- [ ] Marginを資産として二重計上しない
- [ ] Account EquityとBalanceを二重計上しない
- [ ] Unrealized PnLを二重計上しない
- [ ] Realized PnLを残高へ別途再加算しない
- [ ] Long / ShortをMarket Exposureで相殺しない
- [ ] Price取得不能値を0扱いしない
- [ ] FX取得不能値を0扱いしない
- [ ] 未取得と実際の0を区別する

### テスト

- [ ] JPY Assetのidentity conversion
- [ ] USD AssetのJPY換算
- [ ] Crypto AssetのCurrent PriceによるJPY評価
- [ ] Spotのみ
- [ ] Stablecoinのみ
- [ ] Long Position
- [ ] Short Position
- [ ] 複数Position
- [ ] EquityがUnrealized PnLを含むケース
- [ ] EquityがUnrealized PnLを含まないケース
- [ ] Price Currency / Margin Currency / PnL Currencyが異なるケース
- [ ] FX不足
- [ ] Price不足
- [ ] 0 Balance
- [ ] Long + Short Exposure
- [ ] stale price / stale FXを反映した評価状態
- [ ] unavailable valuationが0へ変換されないこと
- [ ] Price / Margin / PnLのCurrencyが異なるPerpetual Position
- [ ] Provider Account Stateのvalue / source / evaluatedAtを保持する
- [ ] Provider Account Stateの評価とEquity / Balance二重計上防止

---

## Step 8-2: Portfolio Snapshot

- [ ] Current PortfolioからSnapshot候補を計算する
- [ ] Snapshot生成条件を判定する
- [ ] `portfolio_snapshots` へ保存する
- [ ] `COMPLETE` を保存する
- [ ] `STALE` を保存する
- [ ] `data_as_of_at` を計算する
- [ ] 同一タイミングの過剰なSnapshot作成を防ぐ

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

- [ ] Portfolio評価対象Connectionがなく、未接続を0円と誤認させる場合
- [ ] 必要なPortfolio Capabilityが未同期
- [ ] 前回成功Current Stateも存在しない
- [ ] 必要なPriceが取得不能
- [ ] 必要なFXが取得不能
- [ ] 主要集計値の一部しか計算できない

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

- [ ] COMPLETE作成
- [ ] 前回成功状態によるSTALE作成
- [ ] 未同期 / 前回成功状態なしでは作成しない
- [ ] 必要なPrice / FX不足では作成しない
- [ ] Activity失敗だけなら生成可能
- [ ] 0 Balanceは取得済みの0として扱う
- [ ] 未取得を0扱いしない
- [ ] 欠損履歴点を生成・0補間しない
- [ ] User AがUser BのSnapshotを生成・取得できない

## Step 8-3: Connection別Portfolio評価 API

Portfolio計算後に、Connections画面用のConnection別JPY評価額と状態を集計する。

- [ ] ConnectionごとにNet Worthへ反映される保有資産額を集計する
- [ ] Position ValueやMarginを保有資産として二重計上しない
- [ ] Connection内の必要な評価値が不足する場合は0にせずunavailable / partial statusを返す
- [ ] Capabilitiesとlast successful sync metadataを返す
- [ ] authenticated user idでConnectionとCurrent Stateを必ず絞る
- [ ] API変更を `api-design.md` に反映する

### Test / ownership

- [ ] 複数Connectionの合計がPortfolio定義と一致する
- [ ] unavailable / staleを0として扱わない
- [ ] User AがUser BのConnection別評価額を取得できない

---

# Phase 9: Assets

## Step 9-1: Assets API

既存Assets画面を確認し、必要なQuery / DTOを実装する。

最低限:

- [ ] 銘柄別合計数量
- [ ] JPY評価額
- [ ] Spot Holdings
- [ ] Directional Assets
- [ ] Stablecoins
- [ ] 保有Connection内訳
- [ ] Current Price
- [ ] Price Currency
- [ ] Price Source / evaluatedAt
- [ ] 24h Price Change
- [ ] Comparison period (`24h`)
- [ ] Market Data Source / evaluatedAt
- [ ] 24h change unavailableを `null / unavailable` で返す
- [ ] Fresh / stale / unavailable情報

24h Price ChangeはPhase 6のMarket Data quote / ticker由来とし、Portfolio Snapshotから算出しない。

Connectionを跨いだAsset集約はApplication / Query Serviceで行う。

API仕様を `api-design.md` に反映する。

### API Test / ownership

- [ ] 銘柄の数量 / JPY評価額 / quote項目を検証する
- [ ] 24h changeの比較期間・単位・source・evaluatedAtを検証する
- [ ] 24h change unavailableはnull/statusで表現し0にしない
- [ ] User AがUser BのBalance / Asset summaryを取得できない

---

## Step 9-2: Assets Frontend

静的mockをAPIへ置き換える。

- [ ] TanStack Query
- [ ] JPY表示
- [ ] Source表示
- [ ] Current Price / Price Currency表示
- [ ] 24h Price Change / 24h比較期間表示
- [ ] Market Data Source / evaluatedAtの状態表示
- [ ] unavailable price change表示（0と区別）
- [ ] Loading
- [ ] Empty
- [ ] Error
- [ ] Partial Error
- [ ] stale表示
- [ ] unavailable表示

### Frontend Test

- [ ] JPY金額と価格通貨の表示
- [ ] 24h changeとcomparison periodの表示
- [ ] unavailableを0として表示しない
- [ ] Partial Error / stale表示

---

# Phase 10: Positions

## Step 10-1: Positions API

- [ ] Symbol
- [ ] Long / Short
- [ ] Leverage
- [ ] Quantity
- [ ] Entry Price
- [ ] Mark Price
- [ ] Liquidation Price
- [ ] Price Currency
- [ ] Position Value JPY
- [ ] Margin Amount
- [ ] Margin Currency
- [ ] Margin JPY
- [ ] Unrealized PnL
- [ ] PnL Currency
- [ ] Unrealized PnL JPY
- [ ] Price FX metadata
- [ ] Margin FX metadata
- [ ] PnL FX metadata
- [ ] Freshness

JPY換算不能値を0として返さない。

API仕様を `api-design.md` に反映する。

### API Test / ownership

- [ ] Price / Margin / PnL FXが独立して適用されることを検証する
- [ ] FX不足を0へ変換しない
- [ ] User AがUser BのPositionを取得できない

---

## Step 10-2: Positions Frontend

Dashboard上のPerpetual Position表示を実APIへ置き換える。

- [ ] Entry
- [ ] Mark
- [ ] Liquidation
- [ ] Position Value
- [ ] Margin
- [ ] Unrealized PnL
- [ ] JPY / USD等のCurrency表示
- [ ] Loading
- [ ] Empty
- [ ] stale
- [ ] unavailable

### Frontend Test

- [ ] JPY / USD通貨単位を正しく表示する
- [ ] unavailable / stale表示を検証する

---

# Phase 11: Activity

## Step 11-1: Activity API

UserのConnectionを横断してActivity Header + Legsを取得する。論理削除済みConnectionのActivity Historyも所有Userには表示する。検索では `authenticated user id` を必須条件とし、Activity取得に `connection.deleted_at IS NULL` を必須条件として使用しない。

- [ ] `occurredAt DESC, id DESC`
- [ ] Cursor Pagination
- [ ] Default limit
- [ ] Max limit
- [ ] Provider
- [ ] Event Type
- [ ] Original Event Type
- [ ] Status
- [ ] Activity Legs
- [ ] direction
- [ ] assetKey
- [ ] symbol
- [ ] quantity
- [ ] originalAmount
- [ ] originalCurrency
- [ ] jpyValue
- [ ] valuationStatus
- [ ] valuationBasis
- [ ] priceUsed
- [ ] priceCurrency
- [ ] priceSource
- [ ] priceEvaluatedAt
- [ ] fxRateToJpy
- [ ] fxSource
- [ ] fxEvaluatedAt
- [ ] Fee Leg
- [ ] valuation availability
- [ ] 取得不能値のNULL表現

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

- [ ] Cursor順序とpagination境界を検証する
- [ ] Activity dedup / Header + Legs / Swap / Feeを検証する
- [ ] User AがUser BのActivity / Activity Legsを取得できない
- [ ] 論理削除Connectionの履歴を所有Userが取得できる
- [ ] 論理削除Connectionの履歴を別Userが取得できない
- [ ] valuation unavailableをnullで返し、0にしない

---

## Step 11-2: Activity Frontend

- [ ] mock-data依存を削除
- [ ] API取得
- [ ] Activity Header表示
- [ ] Activity Legs表示
- [ ] Swap IN / OUT表示
- [ ] Fee表示
- [ ] 日付Grouping
- [ ] Infinite Queryまたは追加読込
- [ ] Loading
- [ ] Empty
- [ ] Error
- [ ] Partial Error
- [ ] stale表示
- [ ] unavailable valuation表示
- [ ] 元通貨とquantity / originalAmountの単位を区別する

### Frontend Test

- [ ] Swap IN / OUT / FEEの表示
- [ ] 元通貨 / JPY valuation表示
- [ ] unavailable valuationを0表示しない
- [ ] 日付Grouping / pagination追加読込

---

# Phase 12: Dashboard

Dashboardは他機能の集約になるため後半に実装する。

## Step 12-1: Portfolio Summary API

既存Dashboard UIに必要なQueryを実装する。

返すもの:

- [ ] Net Worth
- [ ] 24h Change
- [ ] Holdings
- [ ] Directional
- [ ] Stablecoins
- [ ] Market Exposure
- [ ] Exposure Ratio
- [ ] Unrealized PnL
- [ ] Connection別評価額
- [ ] Connection別Data Status
- [ ] last successful sync metadata

巨大なDatabase Entity Graphを返さない。

### API Test / ownership

- [ ] User AのSummaryにUser Bの保有情報が含まれない
- [ ] Connection別Data Statusのpartial / stale / unavailableを検証する
- [ ] 二重計上がないことを検証する

---

## Step 12-2: Portfolio History API

期間:

```text
7D
30D
90D
1Y
```

- [ ] Snapshot取得
- [ ] 時系列sort
- [ ] `COMPLETE`
- [ ] `STALE`
- [ ] データなし状態
- [ ] period validation
- [ ] 欠損Snapshotを0として補間しない
- [ ] 24h change計算に必要な履歴取得

API仕様を `api-design.md` に反映する。

### API Test / ownership

- [ ] period validationとUser ownershipを検証する
- [ ] COMPLETE / STALE / empty historyを検証する
- [ ] 24h changeはPortfolio Snapshot間の比較で扱う
- [ ] 24h changeに比較可能な履歴点がない場合はunavailableとし0にしない

---

## Step 12-3: Dashboard Frontend

既存DashboardをAPIへ接続する。

- [ ] Net Worth
- [ ] 24h Change
- [ ] History Chart
- [ ] Exposure
- [ ] Service Cards
- [ ] Allocation
- [ ] Asset一覧
- [ ] Positions
- [ ] 7D / 30D / 90D / 1Y切替
- [ ] COMPLETE / STALE表示
- [ ] 欠損期間の適切な表示
- [ ] Loading
- [ ] Empty
- [ ] Error
- [ ] Partial Error
- [ ] stale表示

存在しない履歴点を0円として描画しない。

巨大なDashboard API 1本に依存せず、画面上独立して扱う方が自然なQueryは分離する。

### Frontend Test

- [ ] Net Worth / Exposure / Positionの表示とLoading / Empty / Errorを検証する
- [ ] COMPLETE / STALEを区別して表示する
- [ ] Snapshot欠損点を0円として描画しない
- [ ] User切替 / Logoutで前Userのcacheを表示しない

---

## Step 12-4: Connections評価額 / Manual Sync Frontend統合

Portfolio / ValuationとProvider Sync API完成後にConnections画面を完成させる。

- [ ] ConnectionごとのJPY評価額を表示する
- [ ] Capabilitiesを表示する
- [ ] fresh / stale / unavailableを表示する
- [ ] lastAttemptAt / last successful syncを表示する
- [ ] Syncボタンから対象Connectionの `POST /api/v1/connections/{connectionId}/sync` を呼ぶ
- [ ] Syncing中はボタンをdisableし、同一Connectionへの重複操作を防ぐ
- [ ] Success / Partial Failure / Errorを表示する
- [ ] Sync完了後にConnection状態を再取得する
- [ ] `connections`、`assets`、`positions`、`activity`、Portfolio Summary / History等の関連TanStack Queryをinvalidateする
- [ ] Credentialを表示・再送しない
- [ ] JPY評価不能値を0にしない

### Test / ownership

- [ ] Sync開始中 / Success / Partial Failure / Error表示を検証する
- [ ] 同一Connectionのボタンが同期中disableされる
- [ ] Sync成功後のquery invalidation / 状態再取得を検証する
- [ ] User AがUser BのConnection IDでSyncできないことをAPI / Controller Testで検証する
- [ ] unavailable valuationを0と区別する

---

# Phase 13: Sync改善

## Step 13-1: Retry / Timeout

Providerごとに、

- [ ] Connection Timeout
- [ ] Response Timeout
- [ ] Retry対象分類
- [ ] Retry回数上限
- [ ] exponential backoff
- [ ] jitter
- [ ] `Retry-After`

を実装する。

以下を自動Retryしない。

```text
Authentication Error
Permission Error
Validation Error
```

---

# Phase 14: Frontend品質 / Regression

## Step 14-1: TypeScript

- [ ] `typescript.ignoreBuildErrors` を解除する
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
Phase 2
Step 2-3: JPA Entity / Repository基盤
```

今回の連続実装では、完了条件を満たしたStepごとにCommitし、次の未完了Stepへ進む。
