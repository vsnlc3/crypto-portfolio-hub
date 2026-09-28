# Crypto Portfolio Hub 画面設計

文書ステータス: Draft
対象: MVP画面の情報設計・状態設計
関連仕様: [requirements.md](./requirements.md)

## 1. 設計方針

- 本書は `requirements.md` を要件の基準とし、現在のv0生成UIの構成を活かしてMVP画面を定義する。
- 現在のフロントエンドは静的なモックデータによる読み取り専用デモである。以下の画面状態や操作のうち、実データ取得・認証・永続化を要するものはMVPの目標仕様であり、現状実装済みであることを示すものではない。
- ログイン後の画面は共通ナビゲーションを持ち、Dashboard、Assets、Activity、Connections間を移動できる。デスクトップではサイドバー、モバイルではアイコンナビゲーションを使う。
- Portfolioの評価額はJPYを基本通貨とする。暗号資産の単価やPerpetualのEntry / Mark / Liquidation PriceなどはUSD表示を許容し、通貨単位を明記する。
- 同期時刻、接続状態、価格・為替レートが古い場合や取得できない場合は、数値を最新・確定値のように見せない。1接続先の取得失敗は他接続先の表示を妨げない。
- Portfolioデータは認証済みユーザー自身のものだけを表示する。

## 2. 共通表示・状態

### 共通ナビゲーション

| 項目 | 遷移先 |
| --- | --- |
| Dashboard | `/` |
| Assets | `/assets` |
| Activity | `/activity` |
| Connections | `/connections` |

ヘッダーのユーザー表示は認証済みGoogleアカウントに基づく。全画面でログアウト操作を提供する。現在の検索欄とヘッダーSyncボタンは操作未実装のプレースホルダーであるため、MVPで有効化する場合は対象・結果・同期範囲が明確な操作として実装する。

### 共通データ状態

- **Loading:** 画面構成を保ったカード・行のスケルトンと同期中表示を出す。初回取得中は未取得値をゼロと誤認させない。
- **Partial error / stale:** 取得できた接続先のデータは表示し、失敗した接続先にエラー状態、再試行手段、最終成功同期時刻を表示する。古い値を表示する場合は古いデータであることを示す。
- **Error:** 画面に必要なデータをすべて取得できない場合は、エラー理由を簡潔に示して再試行を提供する。既に取得済みの他接続先データは可能な範囲で保持する。
- **Empty:** 未接続、残高なし、履歴なしを区別する。未取得や取得失敗を空データとして扱わない。

## 3. Sign in

### 画面の目的

Googleアカウントでユーザーを認証し、本人のPortfolio画面へ安全に案内する。

### 確認・操作すること

- Crypto Portfolio Hubの用途と読み取り専用であることを確認する。
- Googleログインを開始する。

### 主なUI要素

- Meridian / Portfolioのブランド表示。現在の共通シェルと整合するロゴ・名称を使う。
- 「Googleでログイン」ボタン（MVPで唯一の認証手段）。
- 認証中の進行表示と、認証失敗時のメッセージ。
- PortfolioやConnectionsの個人データはログイン前には表示しない。

### 表示するデータ

- アプリ名と短い説明。
- 認証処理の状態。
- 認証済みの場合はGoogleアカウントの表示情報を必要な範囲で使用する。Portfolio情報は認証後にのみ取得する。

### 主な操作

- Google認証を開始する。
- 認証失敗後に再試行する。

### Loading / Empty / Error

- **Loading:** Google認証への遷移中であることを表示し、ログインボタンの連続操作を防ぐ。
- **Empty:** Portfolioデータは表示しない。ログイン前の状態を空Portfolioとは表現しない。
- **Error:** 認証キャンセル・失敗・セッション確認失敗を通知し、再試行できるようにする。詳細な機密情報は画面に出さない。

### 他画面への遷移

- ログイン成功後は、要求された保護画面があればその画面へ戻し、なければDashboard (`/`) を表示する。
- 未認証の状態で他の保護画面へアクセスした場合はSign inへ遷移し、認証後に元の画面へ戻す。
- ログアウト後はSign inへ遷移し、保護画面とPortfolioデータを再表示しない。

### 現行UIとの差分

- **実装済み:** Google専用Sign in画面、未認証Route Guard、認証Userのヘッダー表示、CSRF付きLogoutを実装した。Portfolio画面の表示データは引き続き静的モックであり、Portfolio API接続は後続Stepで行う。
- **確認済み:** Frontend実ブラウザーでGoogle Login後のDashboard表示、ログインユーザー表示、画面Logout後のSign in復帰を確認した。以後の実Google OAuthブラウザー確認は外部Smoke Testとして扱う。

## 4. Dashboard

### 画面の目的

複数の接続先に分散した資産、総資産の推移、市場エクスポージャー、先物ポジションを一画面で俯瞰する。

### 確認・操作すること

- JPY基準のNet Worthと24時間変化・履歴推移を確認する。
- 現物の保有額とMarket Exposureを区別し、方向性のある現物、ステーブルコイン、Perpetual建玉、Unrealized PnLを確認する。
- サービス別評価額、銘柄配分、主な保有銘柄、HyperliquidのPerpetualポジションを確認する。
- 履歴期間を選ぶ。接続や同期に問題がある場合はConnectionsで状態を確認する。

### 主なUI要素

- **Net Worthカード:** 総資産、24時間の金額・率変化、期間推移エリアチャート、7D / 30D / 90D / 1Yの期間選択。
- **Exposureカード:** Holdings Value、Market Exposure、Exposure Ratio、現物・Perp建玉・ステーブルコインの内訳、Unrealized PnL。
- **サービスカード:** 接続先名・種別、評価額、ポートフォリオ比率、24時間変化。該当する場合はPnLも表示。
- **Allocationカード:** 銘柄別の現物配分ドーナツチャートと金額・構成比。
- **通貨一覧:** 銘柄、数量、価格、評価額、24時間価格変化、保有サービス。
- **Perpetual positions表:** 銘柄、Long / Short、レバレッジ、Position Value、証拠金、Unrealized PnL、Entry / Mark / Liquidation Price。
- 最終同期時刻と接続先ごとの状態。
- CoinGecko Demo attribution `Powered by CoinGecko`を10px以上の読みやすい文字で表示し、CoinGecko API pageへリンクする。

### 表示するデータ

- Net Worth (JPY): 現金・現物残高を基礎とし、残高や口座Equityに含まれていない未実現PnLだけを符号付きで一度加算する。実現PnLは別加算しない。
- Holdings Value (JPY): 現物保有評価額。担保・証拠金を重複して足さない。
- Market Exposure (JPY): ステーブルコインを除く方向性のある現物と、Long / Shortを相殺しないPerpetualのグロス建玉額。
- Position Value (JPY): `abs(数量 × Mark Price)`。Market Exposureに含め、Net Worthには加えない。
- Unrealized PnL (JPY): 符号付きで表示し、口座Equityまたは残高にすでに含まれる場合はNet Worthへ重ねて加算しない。
- Entry / Mark / Liquidation Priceなどの建値はUSD表示を許容する。JPY評価額には使用レートと評価時刻を追跡できる形で換算する。
- 各接続先の評価額・比率、銘柄別の数量・評価額・価格変化、データ更新時刻。

### 主な操作

- 7D / 30D / 90D / 1Yを選択して履歴チャートを切り替える。
- 共通ナビゲーションでAssets、Activity、Connectionsへ移動する。
- 同期状態やエラーがある場合にConnectionsを開き、該当接続先の状態を確認・同期する。

### Loading / Empty / Error

- **Loading:** Net Worth、Exposure、Allocation、保有銘柄、ポジション各領域にスケルトンを表示する。部分的に取得できた接続先は準備でき次第表示する。
- **Empty:** 接続先がない場合は総資産未取得として表示し、Connectionsへの案内を出す。残高がゼロの場合は取得済みのゼロとして区別する。履歴がない場合はチャート領域に「選択期間の履歴なし」を表示する。
- **Error:** 接続先単位の失敗をサービスカードまたは共通同期表示に示し、他の成功データを保持する。全体取得エラーでは再試行を提供する。古い評価額には最終更新時刻を付ける。

### 他画面への遷移

- Assets / Activity / Connectionsへ共通ナビゲーションから移動する。
- 同期や接続の確認が必要な状態からConnectionsへ移動する。
- 銘柄やポジションの行は現行UIでは詳細画面へのリンクではない。MVP画面一覧にない詳細画面への遷移は定義しない。

### 現行UIとの差分

- **要修正:** Dashboardの概要カード、Net Worthチャート、24時間変化などのモック金額はUSD表示。これら集計値はJPY基準にする。Perpetual Positions表は`GET /api/v1/positions`接続済みで、合計とJPY評価をJPY、Entry / Mark / Liquidation Priceを応答のCurrencyで表示する。
- **要修正:** 「Synced 2m ago」は固定表示で、実際の同期状態を反映しない。実データの最終同期時刻と接続状態に置き換える。
- **要修正:** 期間ボタンは選択表示だけ変わり、チャートは同じモック30日分のまま。期間に対応する履歴を表示し、データがない期間はその旨を示す。
- **要修正:** 現行のExposure表示ではExposure Ratioを「leverage」と表記している。要件上は `Market Exposure ÷ Net Worth` の比率であり、Net Worthへの加算項目ではないため、誤解のない名称にする。

## 5. Assets

### 画面の目的

サービスをまたいで現物資産を銘柄ごとに集約し、数量・JPY評価額・保有元を確認する。

### 確認・操作すること

- 全接続先の現物評価額、方向性のある資産、ステーブルコインの内訳を確認する。
- 銘柄ごとの合計数量と保有サービスを確認する。
- DashboardやActivity、Connectionsへ移動する。

### 主なUI要素

- **集計カード:** Spot holdings、Directional、Stablecoinsと各合計値。
- **Allocationカード:** 銘柄別評価額と現物ポートフォリオ内の構成比。
- **Holdings by currency一覧:** トークンアイコン、シンボル・名称、保有サービス、合計数量、単価、評価額、24時間変化、構成比。
- 集計時に対象接続先数または取得状態を表示する。
- CoinGecko Demo attribution `Powered by CoinGecko`を10px以上の読みやすい文字で表示し、CoinGecko API pageへリンクする。

### 表示するデータ

- 接続先を横断して正規化した現物の銘柄、数量、評価額 (JPY)、保有サービス。
- Spot holdings (JPY)、ステーブルコインを除いたDirectional (JPY)、Stablecoins (JPY)。Directional比率はNet Worthに対する割合とする。
- 暗号資産の単価はUSD表示を許容し、通貨単位を明示する。評価額はJPYとする。
- 価格・為替レートの評価時刻、接続先ごとの取得・更新状態。

### 主な操作

- 共通ナビゲーションで他画面へ移動する。
- 保有元アイコンから銘柄の保有サービスを識別する。現行UIでは銘柄行選択による詳細操作はない。

### Loading / Empty / Error

- **Loading:** 集計カードと銘柄一覧にスケルトンを表示する。接続先ごとにデータ到着後、利用可能な銘柄を順次表示する。
- **Empty:** 接続がない場合はConnectionsへ案内する。接続済みだが残高がない場合は「保有資産なし」と明示する。
- **Error:** 失敗した接続先と最終成功同期時刻を表示し、他接続先から取得できた残高は表示する。すべて取得できない場合は再試行を提供する。未取得価格・為替レートをゼロとして評価しない。

### 他画面への遷移

- Dashboard、Activity、Connectionsへ共通ナビゲーションから移動する。
- 接続先の追加・修復・同期はConnectionsへ誘導する。

### 現行UIとの差分

- Assetsは `GET /api/v1/assets` へ接続済み。集計値と評価額をJPY、銘柄単価はAPIの通貨コード付きで表示し、部分取得・stale・unavailable・空・Loading・Errorを区別する。

## 6. Activity

### 画面の目的

ユーザーの各接続先で発生した資産関連イベントを時系列にまとめ、入出金・売買・送金・先物関連の履歴を確認する。

### 確認・操作すること

- 日付ごとのイベントHeader、サービス、イベント種別、資産移動Leg、原通貨数量、JPY評価、日時、処理状態を確認する。
- 接続状態に問題がある場合にConnectionsで確認する。

### 主なUI要素

- 日付でグループ化したcursor pagination対応イベントタイムライン。
- イベント種別アイコンとラベル（Buy、Sell、Deposit、Withdraw、Transfer、Perp、Fundingなど）。
- サービスバッジ・名称、Activity Header、処理状態、同期状態、日時。
- Activity LegごとのIN / OUT / FEE、Asset、quantityとoriginal amount / currency、JPY評価額と評価状態。
- Perpetual FillはSpot資産移動Legと分け、Position方向・約定数量・価格・開始Position・実現PnLを表示する。
- 取得できる場合はPendingなどの処理状態。
- 追加の期間・種別フィルターは現行UIにないため、必要なら別途要件化する。

### 表示するデータ

- 認証済みユーザーのbitbank、Solana Wallet Address（UI表示例: Phantom）、Hyperliquidに関連するイベント。
- 共通イベント種別、サービス、Leg単位のdirection、銘柄、quantity、原通貨金額、JPY評価額、日時、取得可能な処理状態。
- Entry / fill priceなどUSD建てが自然な値はUSDを含む元通貨を明示する。JPY評価がない場合はUnavailableとし、0円に置き換えない。
- Perpetual FillはActivity Headerに対するDetailであり、Fill数量をIN / OUT資産移動にしない。実際のFeeはFEE Legに分ける。
- 取得元のイベントID等を用いて重複イベントを避ける。イベント種別を共通化できない場合は元サービスの種別を表示する。

### 主な操作

- タイムラインを閲覧する。
- 共通ナビゲーションで他画面へ移動する。
- 接続・同期に関する問題がある場合にConnectionsを開く。

### Loading / Empty / Error

- **Loading:** 日付見出しとイベント行のスケルトンを表示する。ページを追加取得中も既に取得した履歴を表示する。
- **Empty:** 成功同期後に履歴がない場合は「No Activity yet」と表示する。接続がない場合はConnectionsへの案内を表示する。同期またはActivityデータがUnavailableの場合は既知の0件として扱わない。
- **Error:** 初回取得失敗では再試行を提供する。部分同期は成功履歴と件数を示し、取得済みページを維持する。追加ページ取得失敗時は既存履歴を残して再試行できる。古い履歴には最後の成功同期時刻を示す。

### 他画面への遷移

- Dashboard、Assets、Connectionsへ共通ナビゲーションから移動する。
- 接続先の詳細な状態確認・同期はConnectionsへ移動する。イベント行から取引実行や送金を開始しない。

### 現行UIとの差分

- **対応済み:** Activity一覧を`GET /api/v1/activities`へ接続し、Header + Legs、Perpetual Fill Detail、日付Grouping、追加取得、JPY評価と取得状態を表示する。評価不能値は0円にしない。
- **対応済み:** User ownership、論理削除ConnectionのHistory、dedup済み履歴をAPIで扱う。部分同期・stale・追加ページErrorを表示し、取得済み履歴を保持する。

## 7. Connections

### 画面の目的

ユーザー自身のExchange、Wallet、DeFi接続先の状態と取得範囲を確認し、接続追加や手動同期へ進む。

### 確認・操作すること

- bitbank、Solana Wallet Address、Hyperliquidの接続状態とマスク済み識別子を確認する。
- 接続先が扱う機能、追跡対象額、最終同期時刻を確認する。
- 接続を追加し、必要な接続先の同期を依頼する。
- エラーまたは切断状態を識別し、再接続・再試行へ進む。

### 主なUI要素

- **接続先一覧カード:** サービス名・種別、マスク済みアカウント識別子、Connected / Syncing / Error / Disconnected状態。
- **接続情報:** Value tracked (JPY)、Capabilities（spot、perp、historyなど）、最終同期時刻。
- **Add sourceボタン:** MVP対象の接続先を追加する導線。
- **Syncボタン:** 該当接続先の読み取り同期を依頼する導線。
- 接続の説明には取引・送金を行わない読み取り専用であることを示す。

### 表示するデータ

- ログイン中のユーザーが所有する接続だけを表示する。
- サービス種別・UI表示名、アカウント識別子のマスク表示、接続状態、対応機能、JPY評価額、最終同期時刻。
- Connection作成直後の`CONNECTED`は設定登録済みを示す。Provider APIの接続確認と同期成否は手動SyncのCapability結果へ反映し、各Capabilityの最終成功時刻を保持する。
- Solana接続の内部対象はWalletアプリではなくSolana Wallet Addressとする。接続バッジやラベルはPhantomと表示してよい。
- bitbank等の秘密情報やAPIキーそのものは表示しない。

### 主な操作

- Add sourceから対象サービスを選んで接続手順を開始する。
- 接続カードのSyncから、その接続先の読み取り同期を依頼する。
- Error / Disconnectedの場合は状態に応じた再試行または再接続へ進む。
- 接続の追加・同期はいずれも売買・出金・送金を行わない。

### Loading / Empty / Error

- **Loading:** 接続カードのスケルトンを表示する。同期中の対象はSyncingと進行状態を表示し、重複同期を防ぐ。
- **Empty:** 接続先がまだない場合は対象サービスの接続を始める説明とAdd source操作を表示する。
- **Error:** 接続先単位でError状態と利用可能な再試行操作を表示する。失敗したサービスがあっても他の接続カード・データを表示し続ける。最終成功同期時刻と、取得済みPortfolio値が古い場合の表示を維持する。

### 他画面への遷移

- Dashboard、Assets、Activityへ共通ナビゲーションから移動する。
- DashboardやAssetsから接続が必要な場合はConnectionsを開く。
- 接続追加完了または同期完了後はConnectionsに留まり、状態・最終同期時刻を更新する。完了したデータは他画面へ戻った際に反映する。

### 現行UIとの差分

- Solana ConnectionはBackendへ`SOLANA`として保存し、Wallet Addressをマスク表示する。表示用バッジはPhantom。
- Add source、一覧、DisconnectはBackend APIへ接続済み。bitbank Credentialは画面上で再表示せず、Backend APIの応答にも含めない。
- **実装済み:** ConnectionカードのSyncからManual Sync APIを呼び、Sync Runをpollする。対象ConnectionだけSync / Disconnectをdisableし、成功・部分失敗・失敗を表示する。完了後はConnections、Assets、Positions、Activity、Portfolio Summary / Historyのcacheをinvalidateして再取得する。
- Connections応答の`portfolioValue`、Capability状態、`lastAttemptAt`、`lastSuccessAt`を表示する。評価不能なJPY額はUnavailableとし、0円に置き換えない。Credentialを画面表示せず、Sync requestにも送信しない。
- 固定の「Read-only demo」「Google sign-in coming soon」文言は削除済み。

## 8. 現行UIとの差分のまとめ

以下は元のv0 UIとMVP要件との差分に対する、現在の実装状況を示す。

| 対象 | 現行UI | MVP要件 / 対応 |
| --- | --- | --- |
| 認証・ユーザー表示 | Google Sign inとログインユーザー表示を実装済み。保護画面は認証後に表示する。 | 本人所有のConnectionsとPortfolioデータを分離する。 |
| 通貨 | Net Worth、Assets、Exposure、Positions、Connection評価額はJPY。価格・Perpetual建値は原通貨も併記する。 | 集計値はJPYを基本とし、未取得値を0円にしない。 |
| データ取得 | Dashboard Summary / History、Assets、Positions、Activity、ConnectionsをBackend APIから取得する。 | User所有データを表示し、Loading / Empty / Errorを区別する。 |
| 同期 | ConnectionカードからManual Syncを実行し、Sync RunとCapability結果を表示する。 | stale値・前回成功時刻・部分失敗を確認し、同じConnectionの重複Syncを防ぐ。 |
| 履歴チャート | 7D / 30D / 90D / 1YのSnapshotを表示し、欠損Snapshotを0円で補間しない。 | 保存済み履歴がない期間は空状態を明示する。 |
| Solana接続 | ConnectionsのProviderは `SOLANA`、表示バッジはPhantom。旧Mock画面にはサービスID `phantom` が残る。 | 内部対象はSolana Wallet Address。UIラベルはPhantom可。 |
| 状態表示 | DashboardとConnectionsを含む各データ画面でLoading / Empty / Error、stale / partial状態を表示する。 | API値と同期結果に応じた状態表示を維持する。 |
