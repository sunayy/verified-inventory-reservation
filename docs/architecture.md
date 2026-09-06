# アーキテクチャ設計

この文書は `Spec.md` で求められた設計判断を定義する設計契約です。実装状況そのものを主張するものではありません。

## 1. 対象範囲と集約境界

MVP では 1 注文につき 1 SKU を扱います。将来、注文明細のコレクションへ拡張しても予約演算の境界を変えないよう、`OrderId`、`Sku`、`Quantity` は異なるドメイン値として扱います。

各注文は数量と、次の閉じた状態のいずれかを持ちます。

```text
Draft --reserve--> Reserved --confirm--> Confirmed
                         └--cancel--> Cancelled
```

`Reserved` の注文だけが SKU の予約済み数量に寄与します。予約済み注文の取消は予約を解放し、確定は予約を売却済みに振り替えます。下書きの取消は可能ですが在庫を変更しません。確定済みまたは取消済みの注文に対する操作は、状態を変えずに拒否されます。作成は下書き注文を作るだけで、在庫を変更しません。

## 2. Quint モデル契約

境界付き有限モデルでは、固定された空でない `Skus`、`OrderIds`、および境界付き自然数の数量領域を使います。状態は次のとおりです。

```text
initialStock : Sku -> Nat       // Init 後は定数
onHand       : Sku -> Nat       // 未売却の物理在庫
reserved     : Sku -> Nat       // Reserved 注文により確保された容量
sold         : Sku -> Nat       // 確定済み数量
orders       : OrderId -> OrderRecord | Absent
```

`OrderRecord` は `{ sku, quantity, status }`、`status` は `Draft`、`Reserved`、`Confirmed`、`Cancelled` のいずれかです。`Absent` により、固定の注文 ID 集合を維持しつつ作成を状態遷移として表現します。`available(sku)` は `onHand[sku] - reserved[sku]` から導かれます。

実装済みモデルには、注文が最初に確定または取消された時点で設定される履歴変数 `terminalOrders : OrderId -> Bool` もあります。これにより `TerminalState` は、終端状態からの誤った遷移を不変条件違反として検出できます。

### アクションとトレースメタデータ

実装では Quint の `--mbt` メタデータを使い、モデル変数として重複保持しません。各名前付きアクションは本体内で非決定的な引数を選ぶため、`mbt::actionTaken` と `mbt::nondetPicks` によりトレースを独立に再生できます。

```text
CreateOrder { orderId, sku, quantity }
Reserve     { orderId }
Confirm     { orderId }
Cancel      { orderId }
```

`createOrder` は未使用 ID、SKU、正の境界付き数量を非決定的に選択します。ほかのアクションは存在する注文 ID を選択します。`reserve` には `Draft` であり `quantity <= available(sku)` であること、`confirm` には `Reserved` であること、`cancel` には `Draft` または `Reserved` であることが必要です。メタデータには状態差分から推測するのではなく、選択された値を記録します。

### 不変条件

Quint モデルは `Init` 後とすべての `Next` 遷移後に、次を検査します。

| 名称 | 式／意味 |
| --- | --- |
| `StockNonNegative` | すべての SKU で `onHand[sku] >= 0` |
| `ReservationWithinStock` | すべての SKU で `reserved[sku] <= onHand[sku]` |
| `StockConservation` | すべての SKU で `initialStock[sku] = onHand[sku] + sold[sku]` |
| `ReservationConsistency` | `reserved[sku]` は、その SKU の `Reserved` 注文の数量合計と等しい |
| `OrderWellFormed` | 存在する注文は有効な SKU、正の数量、閉じた状態を持つ |
| `TerminalState` | 終端注文には有効な reserve／confirm／cancel 遷移がない |

Kotlin 実装の前に、モデルを型検査し、不変条件付きでシミュレーションし、TLC で有限状態検査します。Quint 0.31.0 には独立した lint コマンドがないため、静的な CLI ゲートは型検査です。境界とシードは生成トレースとともに記録します。これらは無制限のすべての実行を証明するものではありません。

## 3. Lean／Kotlin の境界

Lean は全域的かつ非負の予約演算だけを担います。

```text
available(onHand, reserved) : Nat
canReserve(onHand, reserved, requested) : Bool
reserve(onHand, reserved, requested) : Option Nat
cancel(reserved, quantity) : Nat
```

エクスポートされる実行時関数は、証明済み定義から直接定義されます。Lean は少なくとも次を証明します。

- `reserved <= onHand` のもとで `available(onHand, reserved) <= onHand`
- 成功した予約では `newReserved <= onHand` が保存されること
- 成功した予約で過剰予約が発生しないこと
- 有効な予約を取消した後の予約済み数量が自然数、すなわち非負であること

Kotlin は ID、状態遷移、リポジトリの原子性、ドメインエラー対応付け、ユースケース、HTTP シリアライズ、ネイティブ呼出し前の検証を担います。`Long` 入力は JNI 呼出し前に非負かつ表現可能であることを検証します。JNI／ネイティブの失敗はドメイン結果ではなくインフラ障害です。薄い C/JNI シムがプリミティブな `Long`／boolean 結果だけを公開し、Lean のオブジェクト表現を隠します。Kotlin は予約演算を別実装して正解オラクルにしてはなりません。

`Reservation/Logic.lean`、`Proofs.lean`、`Export.lean` はこの境界を実装します。`Export` は `Logic` 定義への直接ラッパーであり、別実装ではありません。Lake は `libinventory_x2dreservation_Reservation.dylib` を生成し、C シムは `uint64_t` と Lean `Nat` を変換します。

Kotlin 側は閉じた `OrderStatus`、`OrderId`／`Sku`／`Quantity` のスマートコンストラクター、sealed なドメイン結果／エラー、インメモリリポジトリ、4 種類のアプリケーションコマンドを実装します。`VerifiedReservationNative` が唯一のネイティブエントリーポイントです。

## 4. Quint ITF トレースから Kotlin MBT への流れ

```text
固定シードの Quint モデル
  -> mbt::actionTaken / mbt::nondetPicks を含む ITF JSON トレース
  -> Kotlin ITF デコーダー
  -> 型付き ModelAction
  -> アプリケーションユースケース（または HTTP テストアダプター）
  -> ApplicationState.toModelProjection()
  -> 各ステップ後に期待する Quint 状態と比較
```

`ItfTraceParser` は ITF 入力だけを受理し、Quint の `--mbt` フィールドを必須とします。アクション引数、`#map`、`#bigint`、注文タグ、実装非依存の `ModelState` を復号します。Quint で存在しない注文は、対応する集約が本番実装にないためモデル射影から省きます。デコーダーは次状態を計算しません。

全 100 個の生成トレースを `ReservationService` で再生し、初期状態および各コマンドの後に `ApplicationState.toModelProjection()` を Quint 状態と比較します。不一致時にはトレースファイル、シード、ステップ番号、アクションと引数、期待状態、実際の状態を出力します。期待状態の生成に本番コードは使用しません。

## 5. フロントエンド EDSL とケイパビリティ

AST はデータ中心で、インタープリターだけが描画します。

```text
Screen<C, QueryResult> { title, query?, body }
Node = Section | Text | Field | Action | Table | UnsafeCustom
Query<Request, Response>
Command<Request, Response, Error>
```

`Screen` はファントムケイパビリティ `C` を持ちます。`ReadOnly` は `Query` とナビゲーションだけを提供し、`Interactive` は `Query`、`Command`、ナビゲーションを提供します。`action` コンストラクターには `CommandCapability` が必要なため、読み取り専用画面からは型安全に更新アクションを作れません。`Field` の値は型付きで、`Action` は対応する `Command` が宣言した正確なリクエスト型だけを受け付けます。

機能モジュールは `defineScreen(...)` 定義をエクスポートし、HTTP クライアントを import できません。インタープリターがエンドポイント振り分け、`fetch`、リクエスト符号化、レスポンス復号、ロード中表示、エラー表示を担当します。`src/features` では `fetch`、`axios`、エンドポイントリテラル、HTTP クライアント import を禁止します。`unsafeCustom(reason, node)` だけが明示的かつ検索可能なエスケープハッチです。`@ts-expect-error` による否定テストで、`ReadOnly` 上のコマンドと不正なペイロードがコンパイルエラーであり続けることを確認します。

`ScreenRenderer` は EDSL ノードを React 要素に変換します。`interpreter/api.ts` は、不透明なクエリー／コマンド ID を `fetch` 呼出しに変換できる唯一のモジュールです。Vitest はネットワークアクセスをモックし、その対応付けを検査します。

## 6. フェーズと検証ゲート

| フェーズ | 成果物 | 次へ進む前のゲート |
| --- | --- | --- |
| 0 | この設計書 | 設計レビュー／合意 |
| 1 | Quint モデル、CLI スクリプト、トレース契約 | 型検査、シミュレーション、TLC 有限状態不変条件検証 |
| 2 | Lean 定義・証明・エクスポート・ネイティブビルド | Lake ビルド、`sorry` が 0、エクスポート定義が証明済み定義 |
| 3 | Kotlin ドメイン、リポジトリ、アプリケーションコマンド、JNI アダプター | Gradle コンパイルとドメインテスト |
| 4 | Kotest プロパティスイート | ドメインを考慮した 1,000 回以上の反復と再現可能なシード |
| 5 | ITF トレースデコーダー | デコーダーフィクスチャテスト |
| 6 | MBT 再生エンジン | 全生成トレースを各遷移で比較 |
| 7 | Spring HTTP アダプター | シリアライズとエラー境界テスト |
| 8 | TypeScript EDSL とケイパビリティ型 | 否定コンパイルテストを含む `tsc` |
| 9 | React インタープリターと EDSL 機能定義 | Vitest と機能 import 制約 |
| 10 | Makefile、CI、再現性の固定、文書 | すべての実ゲートを `make verify` で実行 |

## 7. 技術的リスクと対策

| リスク | 対策／判断 |
| --- | --- |
| Quint の CLI／MBT フラグがリリースごとに異なる | 対応 Quint バージョンを固定し、実際の ITF スキーマを検証し、復号をフィクスチャの背後に隔離する |
| ITF に選択されたアクション引数がない | `--mbt` を使い、生成トレースに `mbt::actionTaken` と `mbt::nondetPicks` があることを検査する |
| Lean ネイティブ export ABI がツールチェーンに依存する | Lean ツールチェーンを固定し、ABI を C シムへ隔離して JNI 境界テストを行う |
| Kotlin の `Long` が Lean ネイティブ変換の前提を超える | JNI アダプターで負値／範囲外を拒否し、境界を個別にテストする |
| インメモリリポジトリが並行性の意味論を隠す | MVP ではトランザクション並行性を対象外と明記し、将来の実装のためインターフェースを維持する |
| Quint の有限境界がバグを見落とす | 正確な境界を示し、境界付き検査を PBT とトレース再生と組み合わせ、形式保証を過大に主張しない |
| モデルと実装の内部表現が異なる | 文書化した射影のみを比較し、私有構造を比較しない |
| 機能コードで EDSL 制約が迂回される | ケイパビリティ型、import 制約、否定コンパイルテスト、および検索可能な `unsafeCustom` だけを使う |
| 寄稿者環境にネイティブツールチェーンがない | 前提条件と固定バージョンを明示し、未検証の代替手段を黙って使わず CI で明確に失敗させる |

## 8. 保証マトリクス

| 性質 | Quint | Lean | Kotlin の型 | PBT | MBT | TS EDSL |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 予約が在庫を超えない | 不変条件 | 定理 |  | プロパティ | 各ステップのトレース |  |
| 補充なしの在庫保存 | 不変条件 |  |  |  | 各ステップのトレース |  |
| 予約済み数量が予約済み注文と一致する | 不変条件 |  | 閉じた状態 | プロパティ | 各ステップのトレース |  |
| 終端状態が吸収的である | 不変条件 |  | sealed な状態／結果 | プロパティ | 各ステップのトレース |  |
| 予約演算の境界 |  | 定理 | 検証済みネイティブ入力 | プロパティ |  |  |
| HTTP コマンドとエラー形状 |  |  | sealed なコマンド／エラー | 例示テスト | 再生アダプター | 型付きコマンド |
| 読み取り専用 UI は更新不可 |  |  |  |  |  | ケイパビリティ型＋否定テスト |

各セルは完了宣言ではなく、採用する検証機構を示します。実装済みの具体的なコマンド、定理名、テストパスは `README.md` と各モジュールの設定を参照してください。
