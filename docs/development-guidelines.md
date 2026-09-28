# Crypto Portfolio Hub 開発ガイドライン

文書ステータス: Draft  
最終確認日: 2026-09-28
対象: MVPの設計・実装方針

関連文書:

- [requirements.md](./requirements.md) — MVPの機能要件・用語定義
- [screen-design.md](./screen-design.md) — 画面と状態の設計
- [provider-specifications.md](./provider-specifications.md) — 公式Provider仕様の確認結果と未確定事項

## 1. この文書の目的

本書は、Crypto Portfolio Hubの今後のDB設計、API設計、実装、テスト、デプロイで共通して使う技術・設計方針を定める。テーブルや具体的なEndpointなど、後続の設計文書で確定する詳細はここでは定義しない。

要件に書かれた機能であっても、実リポジトリにコードがなければ「実装済み」とは扱わない。技術を採用する判断と、その実装が完了している状態も区別する。

### 1.1 状態の表記

| Status | 意味 |
| --- | --- |
| Existing | 現在のリポジトリで存在を確認した実装・設定。動作確認済みを意味するとは限らない |
| Adopted | MVPで採用する設計判断。コードへの実装は別途行う |
| Planned | MVPに必要だが、現在は未実装。今後実装する |
| Future | MVP後、または具体的な必要性が生じた場合に検討する |

### 1.2 リポジトリで確認した現在地

- **Existing:** `frontend/` にNext.js、React、TypeScript、Tailwind CSS、shadcn/uiを使ったDashboard、Assets、Activity、Connections画面がある。
- **Existing:** Dashboard、Assets、Activity、ConnectionsはBackend APIに接続済み。DashboardはPortfolio Summary / History、Assets、Positions、Connectionsは一覧 / 作成 / 削除 / Manual Sync、ActivityはActivity APIを使用する。Google Sign in、Frontend route guard、Session user表示、CSRF付きLogoutも実装済み。`frontend/lib/mock-data.ts`には旧UI用データと書式関数が残るが、現行のPortfolio値の取得元にはしない。
- **Existing:** 開発用ルートComposeはFrontend、Backend、PostgreSQLを起動する。`docker-compose.production.yml`はCaddy、Next.js standalone、Backend、PostgreSQLを本番用に起動し、Public PortをCaddyの80 / 443だけにする。Backend / PostgreSQLはHostへPortを公開せず、BackendはPostgreSQL、Caddy ingress、Provider egressを分けたNetworkへ接続する。`frontend/Dockerfile`はNode.js 22 / pnpmの開発起動設定、`frontend/Dockerfile.production`は非rootのNext.js standalone runtime、`backend/Dockerfile`はMaven buildとJava 25 runtimeのmulti-stage buildである。
- **Existing:** `frontend/package.json` は `pnpm@12.3.4` を指定し、lockfileもpnpm 12.3.4である。
- **Existing:** `frontend/tsconfig.json` は `strict: true` で、Next.js build時のTypeScriptエラーを隠さない。Vitest / React Testing Library、`test`、`typecheck`、`lint` scriptがある。TanStack QueryでDashboard / Assets / Positions / Activity / ConnectionsのServer StateとManual Sync後の再取得を扱う。ConnectionsはReact Hook Form、Zodを使い、Provider別Form、Loading / Empty / Error、追加・削除・同期をテストする。`.github/workflows/ci.yml` でFrontend検査を自動実行する。
- **Existing:** `backend/` にJava 25 / Spring Boot 4.1.1のMavenプロジェクトがあり、Spring MVC、Jackson 3、JPA、PostgreSQL Driver、Flyway、Security、OAuth2 Client、Validation、Actuator、JUnit、Testcontainersを設定している。`/actuator/health` のHTTP応答、10本のFlyway Migration、主要FK / CHECK制約、Problem DetailsとRequest IDをPostgreSQL Testcontainers付きで検証する。
- **Existing:** `backend/src/main/java/com/cryptoportfoliohub/domain/money/` に通貨付きMoney / Price / Quantity / FX Value、JPY換算、PerpetualのPosition Value / 線形Unrealized PnL、表示用丸め基盤がある。Javaの計算には `BigDecimal` を使い、金融数値のUnit Testを持つ。
- **Existing:** `backend/src/main/java/com/cryptoportfoliohub/persistence/` に13 Entityと13 Repositoryがある。Hibernate `ddl-auto: validate` でFlyway Schemaとの整合を検証し、所有データQueryにはUser IDを含める。TestcontainersでUser A / Bの分離とConnection論理削除後の履歴参照を検証する。
- **Existing:** BackendにGoogle OIDC Login、SubjectによるUser作成・再紐付け、Session Cookie、CSRF対応のLogout、認証User確認API、Connection一覧・追加・削除APIを実装し、`.env`から注入したCredentialでローカル実Google OAuth E2Eを確認した。Secret値はGit管理外である。公開環境のHost / Schemeに対応したRedirect URI登録はDeployment設計時に行う。
- **Existing:** BackendにProvider Sync Portと共通非同期Sync lifecycle、Solana / bitbank / Hyperliquidの手動Syncを実装した。Connection行のPessimistic Lockで重複Syncを防ぎ、Capability別状態とSync Run結果、Solanaの履歴cursor再開、Activity重複排除、Hyperliquid Perp Fill Detail、User所有権、部分失敗とCurrent State保持をTestcontainersで検証する。
- **Existing:** BackendにCoinGecko DemoのCurrent Price / 24h quote / USDJPY FX Provider Adapter、Canonical Asset ID mapping、共有Cache、鮮度判定、Provider error分類を実装した。両API keyはBackend環境変数で任意注入し、未設定時はUnavailableを返す。2026-09-28に実API Smoke Testで両Providerのresponseと評価時刻の読み取りを確認した。
- **Existing:** Solana Wallet Addressのbase58 / 32-byte validation、Mainnet Native SOL / Classic Token / Token-2022 Balance Adapter、Helius Signature paginationとParsed Eventsから共通Balance / Activity Header / Legsへの変換を追加した。Solana / Helius仕様fixtureのMock HTTP Testを実行済み。Helius API keyは任意Backend環境変数であり、Live履歴requestは未確認。
- **Existing:** Hyperliquid Read-only AdapterでAccount Mode、Spot Balance、Perp DEX別Account State / Position、Funding、Spot / Perp Fillを正規化する。Perp Fill DetailはActivity Headerと1:1で保持し、Fill数量をSpotの資産IN / OUTにしない。Fixture TestとV10 Schema / User ownership Integration Testを実装済み。
- **未実装:** 定期自動Sync、Portfolio評価、Portfolio業務API、デプロイ環境。Provider API keyを`.env`へ注入する設定は任意である。

この一覧は本リポジトリのファイル・設定に基づく。以下の採用方針は、別途Existingと記載したものを除き、実装済みであることを意味しない。

## 2. 技術スタック

| Category | Technology | Status | Reason |
| --- | --- | --- | --- |
| Frontend | Next.js 16.3.3 / React 19 / TypeScript 5.7.3 | Existing | 現在のUIと設定を継続して活用する |
| Frontend styling | Tailwind CSS 4 / shadcn/uiの既存コンポーネント | Existing | v0生成UIのデザインと部品を活かし、全面書き換えを避ける |
| Frontend package manager | pnpm 12.3.4、lockfile固定 | Existing | `package.json` と `pnpm-lock.yaml` でバージョンが一致している |
| Frontend server state | TanStack Query | Existing | 認証Userのcacheと状態管理に利用開始。Portfolio APIでもcache、再取得、同期中・失敗状態を扱う |
| Frontend forms | React Hook Form + Zod | Existing | ConnectionsのProvider別Formに導入済み。クライアント側の入力補助として使い、サーバー検証の代わりにはしない |
| Frontend global client state | Zustand | Future | MVPでは必須でない。画面をまたぐクライアント専用状態が実際に増えた場合のみ採用する |
| Backend runtime | Java 25 LTS / Spring Boot 4.1.1 | Existing | Backend基盤、Authentication、Connection / Sync APIとSolana Syncを実装済み。Portfolio機能を後続Vertical Sliceで追加する |
| Backend build | Maven Wrapper 3.9.12 | Existing | `backend/mvnw` とWrapper設定でビルドツールを固定する |
| Market data | CoinGecko Demo + ExchangeRate-API | Existing | Batch price / optional 24h quote / USDJPY FX、全ユーザー共通のInstance内Cache、Provider timestamp freshness。Key未設定時はUnavailable。2026-09-28に両ProviderのLive Smoke Testを確認済み |
| Backend security | Spring Security / OAuth2 Login | Existing | Google OIDC Login、Backend Session、CSRF保護、Logout、User Subject紐付けを実装し、ローカル実OAuth E2Eを確認済み。公開環境には環境固有のClient設定とRedirect URI登録が必要 |
| Backend persistence | Spring Data JPA / Hibernate | Existing | 13 Entity / Repositoryを作成済み。Migration SchemaとHibernate validateをIntegration Testで確認し、所有Resource QueryはUser IDを条件に含める |
| Backend API | REST / JSON、Jackson 3 | Adopted | Next.jsとの責務境界を明確にし、HTTPで確認・テストしやすくする。Problem Details、Connection CRUD、Manual Sync / Sync Run取得Endpointを実装済み |
| Database | PostgreSQL | Existing | PostgreSQL Driver、Testcontainers、Compose上のPostgreSQLを構成済み。Named Volumeにデータを保持する |
| Database migration | Flyway | Existing | 10本のSQL Migrationを導入済み。起動時に検証・適用し、HibernateはSchema validateのみ行う |
| External integrations | Provider / Adapter | Adopted | bitbank、Solana、Hyperliquid固有形式をアプリの共通モデルから隔離する |
| Local runtime | Docker Compose | Existing | Frontend、Backend、PostgreSQLを内部Networkで起動する。Frontendは3000番、ローカルOAuth callback用Backendは8080番をHostへ公開し、PostgreSQLは内部のみ |
| First deployment target | AWS Lightsail + Docker Compose | Planned | 個人開発の単一環境から始め、運用負荷と費用を抑える候補とする |
| Public reverse proxy | Caddy | Existing | Production ComposeとCaddyfileでTLS終端、API / OAuth / Frontendの経路振り分けを構成済み。実Domain上の稼働は未確認 |
| Backend tests | JUnit 5 / Spring Boot Test / Testcontainers PostgreSQL | Existing | 起動health check、初期Migration / 制約、Error Response、Entity Schema mapping、User ownershipのIntegration TestとMoney / FXのUnit Testを実装済み |
| Frontend tests | Vitest / React Testing Library | Existing | 認証画面・Route Guardの状態テストを導入済み。Portfolio/API機能のカバレッジは各Vertical Sliceで追加する |
| CI | GitHub Actions | Existing | Frontend、Backend、開発 / 本番Compose、Caddy validation / buildをPull RequestとPushで実行する |
| Queue / cache / orchestration | Kafka、Redis、Kubernetes等 | Future / MVPでは不採用 | 現在の規模・要件では運用対象を増やす明確な必要がない |

バージョン表記のあるFrontend値は現在のmanifestやDockerfileから確認したもの。BackendはJava 25 / Spring Boot 4.1.1で初期プロジェクトを構成し、Maven WrapperでMaven 3.9.12を使う。Spring Bootの管理依存によりSecurity、JPA、Flyway、PostgreSQL Driver等のバージョンを揃える。Java 25はOracleのLTSロードマップに記載され、Spring Boot 4.1.1はJava 17以上を必要としJava 26まで互換性がある。詳細は[Java SE roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)と[Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)を参照する。

## 3. Frontend方針

### 3.1 現行UIからの移行

- `frontend/app/` と `frontend/components/` の画面構成・部品を土台として、API連携と状態表示を段階的に加える。UIをゼロから作り直さない。
- Dashboard、Assets、Activity、ConnectionsはAPIのUser所有データを表示する。`mock-data.ts`の旧fixtureを画面の取得元に戻さず、requirementsとscreen-designに沿ったJPY集計、Google認証、ユーザー分離、Loading / Empty / Errorを維持する。
- `frontend/lib/mock-data.ts` は本番データの取得元にしない。モックを残す場合は開発・テスト専用のfixtureとして扱う。
- TypeScriptのstrict設定を維持する。`typescript.ignoreBuildErrors` は解除済みとし、buildと `typecheck` を品質ゲートとして使う。
- pnpmバージョンとlockfileを一致させ、CIとDockerでも `pnpm install --frozen-lockfile` を使う。

### 3.2 Server StateとClient State

- Backend由来のPortfolio、Connections、Activity、同期状態はServer Stateとして扱い、採用方針はTanStack Queryとする。cache、再取得、mutation後のinvalidate、loading/error状態をQuery側に集約する。
- サーバーデータをZustand、Context、localStorageへ複製しない。ブラウザーに機密Credentialや長期利用する認証Tokenを置かない。
- ZustandはMVPでは追加しない。開閉状態、選択中の一時的な表示設定など、Server Stateではない共通Client Stateを複数画面で共有する必要が確認された時点で判断する。
- Next.js Server Componentは静的レイアウトや認証済みページの初期描画に利用できる。TanStack Queryを使うClient Componentは、再同期や更新などクライアント操作が必要な領域に絞る。

### 3.3 Forms、状態、入力

- 接続追加等のフォームではReact Hook FormとZodを使う方針とする。入力値はサーバーでも再検証する。
- Loading中のSkeleton、同期状態、操作成功・失敗のToast、画面単位のEmpty / Error Stateを用意する。状態は色だけで表さない。
- 接続先単位の同期結果を画面で表示し、一部接続先の失敗によって他の成功データを隠さない。
- 選択期間やActivityのcursor等、URL共有・再読み込みで維持すべき画面状態はQuery Parameterで表す。秘密情報やユーザー識別の根拠はURLへ含めない。

### 3.4 Runtime設定

- ブラウザーへ公開してよい設定だけを `NEXT_PUBLIC_` 変数にする。Backend URL、API Key、OAuth Client Secret、Credential暗号化鍵などの秘密値にこの接頭辞を付けない。
- FrontendからBackendへは同一Originの `/api/*` 経由でアクセスする。ローカル開発ではNext.js rewriteが `BACKEND_INTERNAL_URL` へ転送し、本番ではreverse proxyが同じパスをBackendへ転送する。
- OAuth LoginはFrontendの `/auth/google` routeからBackend Authorization URLへTop-level browser redirectする。Backendは認証成功・失敗後に `FRONTEND_BASE_URL` へ戻す。ローカルComposeではGoogle callback用にBackendの8080番を公開し、Productionではpublic同一Originのreverse proxyだけを公開する。
- 検索欄や全体Syncなど現状プレースホルダーのUIは、仕様・API・結果表示が定まるまでは業務操作として扱わない。

## 4. Backend方針

- BackendはJava 25 LTS、Spring Boot 4.1.1を使用する。初期依存の相互互換性を公式のSpring Boot dependency managementに従って確認し、GradleではなくMavenを使う。Maven Wrapperは3.9.12に固定する。
- Spring MVCでREST APIを提供し、Spring SecurityでOAuth2 Login、Session、API認可を管理する。
- 永続化はSpring Data JPA / HibernateとPostgreSQLを使用する。Spring Data RESTでEntityを自動公開せず、ControllerからEntityを返さない。
- BackendはPortfolioの正規化・評価額計算・ユーザー境界・Provider呼び出しを担う。Frontendにサービス固有の秘密情報や連携ロジックを置かない。
- 1つのSpring Bootアプリケーションを機能別Packageに分ける。複数の独立BackendやMicroserviceには分割しない。

## 5. Authentication / Authorization

### 5.1 採用方式

- 認証方式はGoogleログインのみ。Spring Security OAuth2 Login / OpenID Connectを入口とし、Googleから返る認証結果をBackendで検証する。
- ログイン後のアプリ認証はBackend管理のServer-side HTTP SessionとSession Cookieを使用する。FrontendへGoogle Access Token、ID Token、Client Secretを保存・公開しない。
- Cookieは `HttpOnly`、本番では `Secure`、`SameSite=Lax`、適切な有効期間・Pathを設定する。ログアウトでサーバーSessionを破棄し、ログイン成功時はSessionを更新する。
- Connectionsの登録・削除、同期要求など状態を変更するAPIにはCSRF対策を適用する。CORSを広く許可してCookieを跨いで送る設計にしない。
- OAuth Loginは `GOOGLE_OAUTH_ENABLED=true` で有効にし、`SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID` と `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET` をBackend環境へ注入する。Composeでは `.env` から受け渡すが、実値をGit・イメージへ含めない。Google Cloud Consoleには対象環境の `/login/oauth2/code/google` callback URLを登録する。ローカルComposeでは `http://localhost:8080/login/oauth2/code/google`、Productionでは公開する同一OriginのURLを使う。
- Session Cookieは `application.yml` で `HttpOnly` / `SameSite=Lax` を設定する。本番HTTPS環境では `SESSION_COOKIE_SECURE=true` を設定する。開発用 `.env.example` の値はOAuth無効・Secureなしで、実OAuthを有効化するには利用者のGoogle OAuth設定が必要。
- CSRF tokenは `GET /api/v1/auth/csrf` で取得し、変更系Requestの `X-CSRF-TOKEN` headerへ設定する。Session Cookieと異なりCSRF tokenはFrontendがRequest headerへ渡すため、API Responseに含める。

Spring SecurityのOAuth2 LoginはGoogle等のOIDC Providerによるログインに対応し、Authorization Code Grantを使用する。本プロジェクトでは、この認証フローとBackend Sessionを一体で管理する。[Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/) を実装時の参照資料とする。

### 5.2 Frontend / Backendの認証状態維持

- 公開アクセスは同一Originとし、`/api/*`、`/oauth2/*`、OAuth callbackなどBackend管理の経路はreverse proxyからBackendへ送る。画面本体はNext.jsへ送る。
- OAuth redirectはBackendが処理し、認証完了後に同一ホストのFrontendへ戻す。ブラウザーはHttpOnly Session Cookieを自動送信する。
- 開発時もFrontendの同一Origin proxy/rewriteでAPIをBackendへ転送する。ブラウザーからCompose内の `backend` hostnameやDBへ直接接続しない。ローカルOAuth browser redirectとcallbackだけは `localhost:8080` を使う。
- Frontendのルートガードは画面遷移のための補助とする。APIの認証・認可は必ずBackendで検証し、cookieの存在や画面側判定をアクセス制御の代わりにしない。
- 初期はBackend単一インスタンスのSessionを使う。再起動で再ログインになる可能性を許容し、複数インスタンスが必要になった時点でPostgreSQLを使ったSpring Session JDBC等を検討する。Redisは前提にしない。

### 5.3 ユーザー境界

- Googleの安定したProvider subjectを内部Userへ紐付ける。Emailだけをユーザーの永続識別子にしない。
- 現在の認証PrincipalはOIDC `sub` として扱い、Backendはそれを `users.google_subject` で内部Userへ解決する。`GET /api/v1/auth/me` のResponseには内部User IDと表示情報だけを含め、Google `sub` やTokenを返さない。
- API利用者の内部User IDはBackend Sessionに保持したSpring Security principalのGoogle `sub` を内部Userへ解決して取得する。Requestの `userId` を認可判断の根拠にしない。
- Controller、Application Service、Repositoryの各境界でUser所有権を保つ。Connection ID等の推測可能なIDを指定されても、他ユーザーのレコードを返さない。
- Portfolio、Connections、Credential、Balance、Position、Activity、評価・同期履歴をユーザー単位で分離する。DB設計では外部キー、Unique制約、User IDを含む検索条件を検討する。

## 6. Database方針

### 6.1 採用判断

**MVPのRDBはPostgreSQLを採用する。** 現リポジトリにはアプリDB、MySQLデータ、Migrationが存在せず、切替対象がない。requirements.mdが予定基盤として示すPostgreSQLとも整合する。MySQLも要件を満たすが、このリポジトリには既存DBがないため、PostgreSQLを選んでも既存データの移行は発生しない。

| 観点 | MySQL | PostgreSQL | 判断 |
| --- | --- | --- | --- |
| Spring Boot / JPA | Spring Data JPAとHibernateで利用可能 | Spring Data JPAとHibernateで利用可能 | どちらも要件を満たし、この項目単独では決め手にならない |
| データ構造 | ユーザー、接続、残高、ポジション、イベント等のRDB構造を扱える | 同様のRDB構造を扱え、将来の柔軟な検索・拡張余地もある | MVPは通常の正規化RDBを主とし、特定DBのJSON機能へ依存しない |
| 金額・数量精度 | `DECIMAL` で正確な小数を保存可能 | `NUMERIC` で正確な小数を保存可能 | 両者で浮動小数点を避ける。PostgreSQLで要件を満たせる |
| Index / Constraint | PK、FK、Unique、Indexを提供 | PK、FK、Unique、Indexを提供 | 所有者境界を含む制約・Indexをどちらでも設計できる |
| Migration | Flywayで管理可能 | Flywayで管理可能 | どちらも対応可能。PostgreSQL向けFlywayを基準にする |
| 学習コスト | MySQLに慣れていれば既存知識を活かせる | PostgreSQL固有の運用・型を学ぶ必要がある | DB未導入のため、学習差以外の切替コストはない |
| Portfolioとしての価値 | 標準的なRDB構成を示せる | Spring/JPAとPostgreSQL、Migrationを一貫して示せる | 特定製品の新しさではなく、requirementsとの整合と設計判断を示す |
| 不必要な技術変更か | 新規に選んでもよい | 既存実装からの移行ではない | PostgreSQLを新規基盤として選ぶため、既存データ移行を伴わない |

PostgreSQLの `NUMERIC` は正確な数値が必要な金額・数量に向くが、型の精度・scaleはDB設計時に定める。[PostgreSQL Numeric Types](https://www.postgresql.org/docs/current/datatype-numeric.html) を参照する。

### 6.2 MigrationとORM

- Flywayを採用し、Schema変更は順序付きSQL MigrationとしてGit管理する。
- Hibernateの `ddl-auto` で本番Schemaを自動生成・更新しない。MigrationでSchemaを変更し、アプリ起動時はSchemaとの整合確認を行う。
- 初期開発ではSpring BootからFlywayを実行する。デプロイが複数インスタンス化した場合はアプリ起動とMigration実行を分ける方法を再評価する。
- Entity、Constraint、Index、削除方針、NUMERIC精度の具体値は [database-design.md](./database-design.md) で定める。この文書ではテーブル設計を行わない。

Spring BootにはFlywayの起動時連携があり、PostgreSQL用Flywayモジュールを含めた構成が案内されている。[Spring Boot Database Initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html) を参照する。

## 7. Backend Architecture

機能単位のPackage構成を基本にし、各機能に必要な範囲でController / Application Service / Domain / Repositoryを分ける。Providerの境界はPortfolio集約処理から切り離す。全クラスにInterfaceを作る、全処理を別モジュールへ分ける、といった形式的な抽象化はしない。

```text
backend/src/main/java/<package>/
  config/                 # Spring・HTTP等の設定
  security/               # Google OAuth2、Session、User Principal、認可
  common/
    api/                  # 共通Response・pagination型
    error/                # 例外とHTTPエラー変換
  user/                   # 内部UserとGoogle主体の紐付け
  connection/
    api/                  # Connection Controller / DTO
    application/          # 追加、一覧、同期要求のユースケース
    domain/               # Connection状態・所有権
    persistence/          # JPA Entity / Repository
  portfolio/
    api/
    application/          # Dashboard / Assets用集約、評価額計算
    domain/               # 共通Balance、Position、Money等
    persistence/
  activity/
    api/
    application/
    domain/
    persistence/
  provider/
    port/                 # BalanceProvider等のアプリ側契約
    bitbank/               # bitbank API通信・署名・変換
    solana/                # 公開Wallet Addressによる取得・変換
    hyperliquid/           # API通信・変換
    marketdata/             # USD価格・JPY為替の取得
```

- ControllerはHTTP、認証Principal、DTO入出力を扱う。業務計算やProvider固有処理を置かない。
- Application Serviceはユースケースと複数Portの調整を担う。Portfolio集約、同期起動、ユーザー境界を組み合わせる。
- Domainは通貨付き金額、数量、ポジション、集計ルール等の中核ロジックを置く。Springや外部APIのDTOへ不必要に依存させない。
- Repositoryはアプリ内部の永続化境界を担い、Spring Data JPAのEntityはAPIから隠す。
- Provider Adapterは外部HTTP API、署名、rate limit、response mappingを担当し、正規化済みデータか明確なProvider Errorを返す。

## 8. External Provider / Adapter方針

### 8.1 責務の分け方

単一の巨大 `PortfolioProvider` に全機能を要求せず、アプリが必要とする能力ごとのPortを設ける。各接続先Adapterは実際に提供できる能力だけを実装する。

```text
BalanceProvider
PositionProvider
ActivityProvider
MarketDataProvider  # USD価格・JPY FX等。資産サービスの接続Providerと分離
```

- bitbank Adapterは要件で扱う現物残高、評価に必要な値、取得可能なActivityを共通モデルへ変換する。
- Solana Adapterは特定WalletアプリではなくSolana Wallet Addressを対象とする。秘密鍵やSeed Phraseを要求しない。UI上の表示名はPhantomとしてよい。
- Hyperliquid Adapterは現物残高、Perpetual Position、Funding等を読み取れる範囲で共通モデルへ変換する。
- HyperliquidのAccount ModeはRead-only `userAbstraction`で判定する。`disabled`はStandard相当、`unifiedAccount` / `portfolioMargin`は各々のModeへ対応させる。`default`、旧`dexAbstraction`、欠落・未知値・取得失敗はUnsupported / Unknownとして推測集計しない。Unified / Portfolio MarginはSpot Clearinghouse Balanceを基準にし、Perp account equityを重ねない。Perp FillはActivity Headerと1:1のPerpetual Fill Detailに保存し、現物IN / OUT Legへ変換しない。実際のFeeのみFee資産Legにする。詳細は[provider-specifications.md](./provider-specifications.md)と[database-design.md](./database-design.md)を参照する。
- あるサービスでPositionやActivityの取得ができない場合、未対応・未取得を明示する。推測値やゼロで埋めない。
- 外部APIの生Response、認証方式、Pagination形式をFrontendやPortfolio Domainへ漏らさない。共通モデルへの変換はAdapter内で行う。
- 価格・為替を取得するProviderも交換可能な境界に置き、金額にCurrency、取得元、評価時刻を持たせる。

対象サービスのAPI endpoint、署名方式、権限範囲、rate limit、取得可能な過去データは本リポジトリだけでは確定できない。実装時に各サービスの公式API資料で確認し、未確認の挙動を共通仕様として断定しない。

### 8.2 Portfolio集約との境界

Portfolio Application ServiceはProvider Adapterから得た正規化済みBalance / Position / Activityをユーザー単位で集約する。共通集計ロジックを各Provider実装へ重複させない。

Net Worth、Market Exposure、Position Value、Unrealized PnLの意味と二重計上防止は[requirements.md §4.3](./requirements.md#43-評価額とポジション指標の定義)を正とする。Provider Adapterは残高や口座EquityにPnLが含まれるかを明示的にマッピングし、集約側でPnLや証拠金を二度足さない。

## 9. Data Sync方針

### 9.1 初回取得と手動同期

- 接続追加後はConnection単位で初回取得を行う。同期対象・取得結果・最終成功時刻を記録する。
- Connectionsの手動SyncはそのConnectionのみを対象とする。必要に応じてDashboardの全体Syncを将来追加する場合も、内部ではConnection単位の同期を順に実行し、結果を別々に扱う。
- MVPはDBとSpring Bootを使う単純な同期処理から始める。同期時間にはProviderごとの上限を設け、UIに実行中・完了・失敗を返す。外部API呼び出し中に長時間DB Transactionを保持しない。
- 同一Connectionへの重複要求を抑止する。MVPでは同期中フラグやDB制約等の簡素な排他を使い、分散Lockサービスは導入しない。
- Solana初回Activity取得は直近90日を対象とし、1回のmanual syncで最大100件を取得する。continuation cursorと元のquery windowを保存して手動で再開できるようにし、全履歴を自動走査しない。定期同期間隔は導入前に決める。

### 9.2 Timeout、Retry、Rate Limit

- 全Provider HTTP clientにConnection Timeout 3秒 / Response Timeout 5秒を設定する。SolanaとMarket Dataは環境設定で変更でき、bitbankとHyperliquidはこの値を固定する。無期限待機を許さない。
- Providerのread-only HTTP clientは、通信I/O失敗（TLS / 証明書エラーを除く）、HTTP 408 / 429 / 500 / 502 / 503 / 504をRetryする。最大3回（初回を含む）とし、200ms開始の指数Backoffへfull jitterを適用する（待機上限2秒）。最大再試行回数を超えない。
- HTTP 401 / 403およびその他の4xx、Provider validation / authentication / permission errorは自動Retryしない。`Retry-After`のdelta-seconds / HTTP-dateは次のRetry時刻として尊重する。指定待機が2秒を超える場合は早くRetryせず、そのresponseで失敗する。
- Retry対象RestClientはbitbank / Solana RPC・Helius / Hyperliquid Info / Market Dataのread-only API専用とする。今後書込APIを追加する場合は、このRetry clientを流用せず独立したHTTP clientを設ける。Provider request URI・header・body・例外causeをログへ出さない。
- MVPでは複雑なJob Queue / Message Brokerを導入しない。定期同期が必要になった段階ではSpring SchedulerからConnection単位のユースケースを呼び出す方式をまず検討する。

### 9.3 Partial Failureとstale data

- Provider間は障害分離する。あるProviderがtimeoutや認証エラーになっても、他Providerの取得・画面表示は継続する。
- 失敗時は直前の成功済みBalance / Position / Activityデータを削除・上書きせず保持し、Sync状態Error、失敗理由の安全な分類、最終成功同期時刻を更新する。
- Provider内で能力別に一部失敗した場合、成功したデータセットは更新できる。失敗したデータセットは前回成功値を保持してstaleとして区別する。
- 未取得値と残高ゼロを区別する。価格またはFXが取れない値をゼロや別通貨の無換算値として集計しない。
- 同一Activityの重複取込を防ぐProvider event ID等の一意キーを設計する。更新同期は再実行しても重複しない形にする。
- `lastAttemptAt` と `lastSuccessAt` の意味を分ける。UIの「最終同期」は成功時刻を指し、失敗時刻で成功時刻を置き換えない。

定期同期の間隔、バックフィル期間、データ保持期間、実際のAsync化は運用とProvider制限が明らかになった段階で決める。負荷や応答時間が単一プロセスの処理限界を超える場合にのみ外部Queueを再検討する。

## 10. Credential / Security

- bitbank等のAPI Key / API SecretはBackendだけで扱い、Frontendへ返さない。ログ、例外Message、監視タグに値そのものを出力しない。
- Gitへ実値をコミットしない。ローカルは `.env` 等のGit ignore対象ファイル、本番は実行環境のSecret設定から環境変数として注入する。`.env.example` には変数名とダミー値のみを置く。
- DBへ保存する必要があるCredentialはアプリケーション層で暗号化する。採用方式はAES-256-GCM。各暗号化値にnonceと鍵バージョンを持たせ、復号はBackendの必要処理に限定する。
- 暗号化鍵はDB、Git、Docker imageへ保存しない。開発環境ではGit管理外の環境変数等からBackendへ注入し、本番ではサーバー上の保護された環境設定、または必要に応じてAWS Secrets Manager等のSecret管理サービスを使う。
- AWS Secrets Manager等の導入はMVPの必須要件としない。開発・本番で鍵を分け、鍵未設定時はCredentialを平文保存へfallbackしない。具体的な本番Secret管理方式と鍵Rotation手順はDeployment設計時に確定する。
- bitbank API Keyは残高・履歴取得に必要な最小権限で発行し、注文・出金権限を要求しない。要求仕様で非対応の情報を得るために強い権限を安易に追加しない。
- Solana接続は公開Wallet Addressを使う。秘密鍵、Seed Phrase、署名を要求・保存しない。
- DB、BackendはInternetへ直接公開しない。TLSは公開入口で終端し、Frontend / Backend / DB間は必要なDocker networkだけで通信させる。
- ログにCookie、Session ID、Google Token、API Key / Secret、Credential ciphertext、個人を識別する不要な情報を記録しない。
- エラー応答には内部Stack Trace、SQL、外部Credential、Providerの認証Responseを含めない。

## 11. API方針

- FrontendとBackendの通信はREST APIとJSONを基本とする。画面で必要なPortfolio表示をResource / Queryとして表せ、HTTPの認証・Cache・Status Code・paginationを利用しやすいため。
- Controllerの入出力にRequest / Response DTOを使用し、JPA Entityや外部Provider DTOを直接公開しない。
- 入力はFrontendのZodで補助し、BackendでもBean Validation等により必ず検証する。権限判定や所有者判定はServer側で行う。
- Error ResponseはRFC 9457 Problem Detailsを基礎とし、UIが扱う安定したerror codeを拡張情報に含める。実装詳細は返さない。[Spring Framework Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html) を参照する。
- Activity等の増加する一覧はcursor paginationを基本とし、時刻順・重複境界を安定させる。小さいConnections一覧やDashboard集計に不要なPaginationを付けない。
- 日時はUTCのISO 8601形式で送受信する。表示TimezoneはFrontendでユーザー設定または定めた既定値に従う。
- 暗号資産数量・価格・評価額は小数精度を失わないDecimal表現とする。JSONではplain base-10 stringとして返し、JavaScript `number` の暗黙変換・計算誤差を避ける。Frontendは`DecimalString`を表示・加算する際もstring / `BigInt`演算を使う。Chart座標や比率幅だけは描画境界でJS numberへ一時変換できる。未取得・算出不能値は`null`を維持する。
- API Versioningは初期にはURL prefix等の規則だけを決め、互換性を壊す変更が必要になった時点で詳細を定義する。具体的Endpoint一覧・DTO・認可表は [api-design.md](./api-design.md) で設計する。

## 12. 数値・通貨・日時

- Javaの計算・永続化直前の変換では `BigDecimal` を原則使用する。`double` / `float` による価格、数量、PnL、比率の業務計算をしない。文字列または正確なDecimal Provider値から生成する。
- Backend Domainでは `Money` / `FxRate` にCurrencyCodeを、`Price` に価格対象のassetKeyとQuote Currencyを、`AssetQuantity` にassetKeyを明示する。異なる通貨のMoney同士は加算できない。`NUMERIC(38,18)`、`NUMERIC(38,8)`、`NUMERIC(24,12)`、`NUMERIC(18,8)` はJavaの `BigDecimal` へ対応付け、DB列とAPI境界で個別のscale制約を検証する。
- `JpyConverter` は換算元からJPYへのFXを受け取り、FX未取得なら値を作らず `Optional.empty()` を返す。取得できない価格・数量・金額もゼロの代替値を作らず、呼出側で不在値として扱う。
- PerpetualのPrice / Margin / PnLにはそれぞれ `PriceFxRate` / `MarginFxRate` / `PnlFxRate` を使い、同じ型のFX Rateを誤って共用できないようにする。
- DBでは適切な `NUMERIC(precision, scale)` を使い、精度・scaleはAsset Quantity、USD Price、JPY Value、FX Rateごとにdatabase-design.mdで定義する。浮動小数点型を金額・数量に用いない。
- 金額だけを持つ値にしない。Asset Quantity、USD Price、JPY Value、FX Rate、Position Value、Unrealized PnL、Net Worth、Market Exposureは意味とCurrencyを追跡する。
- Portfolio基準通貨はJPY。暗号資産価格、Perpetual Entry / Mark / Liquidation PriceはUSDを許容し、JPYへ集計する場合は明示的なFX Rateと評価時刻を記録する。異なる通貨を無換算で加算しない。
- Net Worth等の計算ルールはrequirements.mdを正とする。Position Valueと証拠金の重複計上、口座Equityに含まれるUnrealized PnLの再加算を防止する。
- Roundingは表示時にだけ `DisplayRounding` で明示したscale / modeを使い、取得値・内部計算を途中で丸めない。サービスごとの最小単位とUI表示桁はDB/API設計で確定する。
- Activity日時と取得日時はUTCで保存・送受信し、画面表示でローカル日時へ変換する。

## 13. Error Handling / Logging

### 13.1 例外境界

- Controller Advice等の単一境界でApplication / Validation / Security / Persistence例外をHTTP応答へ変換する。
- HTTP Errorは `application/problem+json` のRFC 9457 Problem Detailsを返し、共通拡張として安定した `code` とサーバー生成の `requestId` を含める。Validation Errorは安全なfield別error一覧、Provider Errorは `providerCategory` を追加する。
- `X-Request-ID` Response HeaderとProblem Details内の `requestId` は同じUUIDとする。Client supplied IDは相関IDとして信頼せず、毎回サーバーで生成する。
- Validation Errorはfield別の安全な説明、Unauthenticatedは401、権限不足は403または情報漏えいを防ぐ404、存在しないResourceは404、想定外の失敗は汎用500にする。
- External API Errorは少なくともtimeout、rate limit、authentication/permission、provider unavailable、invalid responseへ分類し、Connection単位の同期状態に反映する。
- DB障害は安全な共通応答と相関可能なRequest IDで記録する。SQLやStack TraceをFrontendへ返さない。
- Partial FailureをHTTP全体の失敗に潰さず、Connectionごとの結果をResponse / 状態データで表す。

### 13.2 Logging

- ApplicationログへRequest ID、機能、Connection内部ID、Provider種別、同期結果、所要時間、error categoryを必要最小限記録する。
- API Key / Secret、OAuth Token、Cookie、Session ID、Wallet秘密鍵、Credential ciphertext、未マスクWallet Addressをログへ含めない。
- Providerの応答Bodyを丸ごと記録しない。調査で必要な場合もフィールドを限定し、秘密情報や個人情報を除外する。
- 秘密値・生アカウント値をMetric labelやTracing attributeにも使わない。

## 14. Testing

FrontendにはVitest / React Testing Libraryによるtest scriptがある。Backendには起動・Migration・Error HandlingのIntegration Testがあり、機能追加に合わせて以下のTestを加える。

### Backend

- JUnit 5でDomain / Application Service Unit Testを行う。特にPortfolio集計、Net Worth、Market Exposure、Position Value、Unrealized PnL、通貨換算・丸めを境界値込みで確認する。
- Provider Adapterは保存fixture / 期待値とローカルMock HTTP Server等を使い、実API・本物Credentialへ依存させない。認証署名やresponse正規化も単体テストする。
- Controller / Security Testで認証必須、user owner境界、validation、Problem Details応答を検証する。
- Repository TestはTestcontainersのPostgreSQLで実施し、H2だけでは再現しにくいPostgreSQL固有の型・Constraint・Query挙動を確認する。現在、全EntityのSchema MappingとUser ID条件付きQueryの所有者分離を検証している。
- 全機能に重いEnd-to-End環境を要求しない。同期全体やGoogle Providerの実通信をCIから実行しない。

### Frontend

- VitestとReact Testing Libraryで金額の表示、Loading / Empty / Error、主要なComponent、フォームValidationをテストする。
- APIはmockし、テストごとにユーザーや接続データを明示する。実外部APIや実ログイン情報を使わない。
- 実Google OAuthのブラウザー確認はCredentialをGitやCIへ保存せず、外部Smoke Testとして必要に応じて実施する。通常の実装・回帰テスト・CIをブロックする条件にしない。MVP初期から広範な自動ブラウザーE2E suiteを必須にしない。

### 品質ゲート

- CIではFrontend lint / typecheck / test / production build、Backend Maven verifyとPostgreSQL Testcontainers、開発・本番Compose validation / image build、Caddyfile validationを実行する。`.github/workflows/ci.yml`をPull RequestとPushで動かす。
- Backendではformat / compile / unit test / integration testを段階的に加える。
- Test用CredentialやOAuth Client SecretをCIへ登録しない。外部APIテストfixtureは公開可能な匿名データのみを使う。

## 15. Infrastructure / Deployment

### 15.1 現状と採用目標

- **Existing:** Docker ComposeでFrontend、Backend、PostgreSQLを起動する開発構成と、Caddyを入口にした本番用Compose / Dockerfileがある。Lightsail環境はない。
- **Planned:** 初回の公開環境はAWS Lightsail上の単一VMとDocker Composeを候補・目標とする。Region、OS、RAM、CPU、公開設定は本プロジェクトでは未確定であり、契約前に必要リソース・費用・バックアップを決める。
- **Existing:** Root ComposeではPostgreSQL health check後にBackendを起動し、Frontend / Backend / PostgreSQLを共通Networkへ接続する。PostgreSQLの開発用PasswordはGit管理外の`.env`から注入し、DBはNamed Volumeへ保存する。
- **Existing:** 本番設定は`docker-compose.production.yml`へ分ける。Production secretsはCompose interpolationでruntime Environmentから注入し、Docker buildへ渡さない。実値はGit管理外の保護された環境設定から渡す。`deployment/README.md`とplaceholder-onlyの`deployment/production.env.example`に手順を記載する。
- **Existing:** 公開入口のCaddyは`/api/*`、`/oauth2/*`、`/login/oauth2/*`をBackend、その他をNext.jsへ転送する。Domainが設定されるとCaddy Automatic HTTPSを使う。実際のDomain・DNS・Firewall条件は公開前に設定する。

### 15.2 Networkとデータ保持

- Internetへ公開するのは原則Caddyの80/443のみ。Frontend / Backendの内部PortとPostgreSQL PortをHostへ公開しない。
- Frontend、Backend、DBはComposeの内部Networkで通信する。ブラウザーからBackend container hostnameやPostgreSQLへ接続させない。
- PostgreSQLはNamed Volumeに保存し、イメージの再作成でデータを消さない。バックアップと復元の手順を本番利用前に作り、バックアップ復旧を確認する。
- Production SecretはGit管理せず、サーバー上の保護された環境設定またはSecret管理から注入する。Docker imageへSecretをbuild argumentやLayerとして埋め込まない。
- Health check、restart policy、disk使用量とDocker log rotationを段階的に整える。

RDS、ECS、EKS、Kubernetesは明確な可用性・運用上の要件が出るまで導入しない。Lightsail + Composeも現時点ではデプロイ実績ではなく、これから検証する計画である。

## 16. CI/CD

`.github/workflows/ci.yml`でGitHub Actionsを導入済み。

- Pull RequestとPushでFrontend lint / typecheck / test / production buildを実行する。
- BackendでMaven `verify`を実行し、JUnitとPostgreSQL Testcontainersを含む全テストを実行する。
- Docker Composeの構成検証とFrontend / BackendのImage buildを実行する。
- CIは実Provider Credential、Google Client Secret、Encryption Keyに依存しない。Compose検証では一時的なダミーDB Passwordを使う。
- 成果物が安定した段階でContainer RegistryへのPushを検討する。
- 最初の公開は手動Deployを基本とし、health check、DB backup、rollback方法を確認した後に自動Deployを検討する。

Google Client Secret、Provider Credential、Encryption KeyをGitHub Actions logやBuild artifactへ出力しない。自動Deployを先に作ることを品質の代替としない。

## 17. MVPでは採用しない技術

明確な規模・要件が生じるまでは以下を導入しない。

- Microservices（Backendは当面1つのSpring Boot application）
- Kubernetes / EKS
- Kafka、複雑なMessage Queue、Event Sourcing
- CQRSの独立モデル・独立Store
- GraphQL
- Redis（cache、Session、distributed lockを初期要件にしない）
- 分散Transaction
- 複数DBや複数ORM
- MVPに先行する汎用Plugin framework

外部Providerの違いはAdapterで隔離するが、能力や要件のない空Interface・抽象Factoryを増やさない。Background処理の負荷が明確になったらQueueやWorkerを再評価する。

## 18. 段階的な実装方針

1. **設計を揃える:** 本書の方針を `database-design.md`、`api-design.md` へ反映する。数値定義と画面状態はrequirements / screen-designと矛盾させない。
2. **BackendとDBの最小起動:** Java / Spring Boot、PostgreSQL、Flyway、Composeを用意し、health checkと設定を整える。
3. **認証とUser境界:** Google OAuth2 Login、Session Cookie、CSRF、内部User識別を実装し、ログイン・ログアウト・保護APIを確認する。
4. **ConnectionとCredential:** ユーザー所有のConnectionを作成し、秘密情報暗号化、マスク表示、read-only権限を実装する。SolanaはWallet Addressだけを扱う。
5. **ProviderごとのAdapterとSync:** bitbank / Solana / Hyperliquidを一つずつ接続し、fixtureによる正規化テスト、Connection単位のSync、状態とstale保持を加える。
6. **Portfolio集約とREST API:** JPY評価、Dashboard / Assets / Activity用Query、cursor pagination、統一Error Responseを作る。金額計算を先にテストする。
7. **Frontendの実データ化:** 既存UIへTanStack Query、フォーム検証、Loading / Empty / Errorを追加し、JPY表示へ移行する。固定プロフィールと操作未実装プレースホルダーを要件に沿って置き換える。
8. **CIとDeployment:** GitHub Actionsと本番Compose / Caddy設定を整えた。実DomainでのHTTPS、DB backup / restore、Lightsail運用を外部設定後に確認する。

依存する外部サービスの仕様が未確認の段階では、UIや共通モデルが取得できる値を仮定しない。取得可能な項目・精度・更新制限が確認できてから、該当Providerの機能範囲を決める。

## 19. 後続設計事項・未確定事項

### 今回採用した方針

- Frontendは現行Next.js UIを継続し、Server StateにTanStack Query、入力フォームにReact Hook Form + Zodを導入する。Zustandは現時点で採用しない。
- BackendはJava 25 LTS / Spring Boot 4.1.x / Spring Security / Spring Data JPA / Hibernate / Mavenで作る。
- MVPのRDBにPostgreSQL、Schema MigrationにFlywayを採用する。
- Google OAuth2 Login後はBackendのHttpOnly Session Cookieで認証状態を維持し、APIでユーザー所有権を検証する。
- REST / JSON、機能単位Package、能力別Provider Port、Connection単位同期、正確なDecimal計算、AWS Lightsail + Docker Composeの段階的導入を方針とする。

### 後続の `database-design.md` で決める事項

- User、Connection、暗号化Credential、Balance、Position、Activity、valuation / FX、Sync run、時系列Snapshot等のEntityとRelation。
- 主キー、User所有権のFK・Unique制約、ユーザーIDを含むIndex、削除・再接続方針、履歴保持期間。
- Asset Quantity / USD Price / JPY Value / FX Rateの `NUMERIC` precision・scale、Sourceごとの丸め、符号規則。
- Provider Eventの一意性・重複排除キー、現在値と履歴Snapshotの保存境界。
- Flyway Migration命名規則、開発・テスト用fixture、production適用手順。

### 後続の `api-design.md` で決める事項

- Sign in状態確認、Logout、Connections CRUD / Sync、Portfolio Summary / Assets / Positions / ActivityのEndpoint。
- Request / Response DTO、Decimal文字列形式、通貨・時刻・Null / 未取得値表現、Problem Details拡張code。
- 認可表、HTTP Status、Activity cursor paginationとsort順、cache / stale表示用Metadata。
- 同期要求の応答方式、部分成功のResponse表現、画面が必要とするQuery単位。

### まだ未確定の事項

- Hyperliquid Account Mode / Perp Fill mappingは`provider-specifications.md`と`database-design.md`で確定済み。公式Zero Addressへのread-only Info requestで`default` responseを確認したが、実User AddressのModeは未確認である。Adapter fixture testsはStep 7-6で完了済み。
- CoinGecko Demo APIのCredentialとExchangeRate-API Free planのCredentialは、Step 6-1でBackend Secretとして設定する。Market priceは共有Cache TTL 10分 / STALE判定15分、日次FXは72時間をMVP鮮度基準とする。API障害時の第二Provider fallbackは設けず、評価不能値を0にしない。Demo planのAttributionと外部ユーザー提供前のLicense確認を守る。
- Heliusの実利用Plan / 最新料金・Rate LimitのLive利用前確認、定期同期間隔。
- 履歴・価格Snapshotの保持期間。
- Google OAuthの本番Redirect URL、Domain、Session有効期間、暗号化鍵の本番保管とRotation。
- AWS LightsailのRegion / instance size / backup方式、独自Domain、DNS、Caddy公開設定、月額上限。
- Provider固有のCredential暗号化・更新手順と、ユーザーがConnectionを削除した場合のデータ保持期間。

## 20. 参照資料

- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Security OAuth2 Login](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/)
- [PostgreSQL Numeric Types](https://www.postgresql.org/docs/current/datatype-numeric.html)
- [Spring Boot Database Initialization / Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Spring Framework Error Responses / Problem Details](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [Oracle Java SE Support Roadmap](https://www.oracle.com/java/technologies/java-se-support-roadmap.html)
- [Amazon Lightsail Pricing](https://aws.amazon.com/lightsail/pricing/) and [Lightsail User Guide](https://docs.aws.amazon.com/lightsail/latest/userguide/)
