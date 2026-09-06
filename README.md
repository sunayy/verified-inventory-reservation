# 在庫予約システム

重要なルールを、人によるコードレビューの前に機械的に検査する在庫予約 MVP の実験プロジェクトです。Quint による状態モデル、Lean の証明、Kotlin の型とプロパティテスト、トレースベースのモデルテスト、ケイパビリティで制約された TypeScript UI DSL を重ねて検証します。

全実装フェーズは完了しています。境界付き Quint モデルと検査、証明・エクスポート済みの Lean 予約コア、そのコアを JNI 経由で呼び出す Kotlin のドメイン／アプリケーションコードを備えています。Kotest はドメインを考慮したプロパティを各 1,000 回実行し、Quint の ITF トレースは型付きアクションおよびモデル射影へ復号されます。HTTP アダプターを提供し、フロントエンド EDSL は React インタープリターからのみ描画されます。

状態モデル、境界に関する判断、トレース契約、フロントエンド DSL、リスク、段階的な計画については、[アーキテクチャ設計](docs/architecture.md)を参照してください。

## リポジトリ構成

```text
.
├── AGENTS.md
├── README.md
├── Makefile
├── Spec.md
├── docs/
│   └── architecture.md
├── spec/
│   ├── inventory.qnt                # フェーズ 1
│   ├── traces/                      # 生成された ITF トレース（手書きしない）
│   └── README.md
├── verified/
│   ├── lakefile.lean                # フェーズ 2
│   ├── lean-toolchain
│   ├── Reservation/
│   │   ├── Domain.lean
│   │   ├── Logic.lean
│   │   ├── Proofs.lean
│   │   └── Export.lean
│   └── native/
│       ├── reservation_jni.c
│       └── reservation_jni.h
├── backend/
│   ├── build.gradle.kts             # フェーズ 3
│   └── src/{main,test}/kotlin/
├── frontend/
│   ├── package.json                 # フェーズ 8
│   └── src/{dsl,interpreter,features}/
└── scripts/
```

## 保証マトリクス

| 性質 | Quint | Lean | Kotlin の型 | PBT | MBT | TS EDSL |
| --- | :---: | :---: | :---: | :---: | :---: | :---: |
| `reserved ≤ onHand` | 境界付き不変条件 | 定義を証明 | コンストラクター／状態検査 | 容量プロパティ | 各ステップのトレース |  |
| 在庫保存（補充なし） | 境界付き不変条件 |  | 在庫表現 |  | 各ステップのトレース |  |
| 終端注文は遷移不可 | アクションガード＋履歴不変条件 |  | 閉じた status／result | 終端状態プロパティ | 各ステップのトレース |  |
| 予約演算の容量 |  | `successfulReservationPreservesCapacity` | JNI の `Nat` 検証 | 容量プロパティ |  |  |
| コマンドペイロード形状 |  |  | スマートコンストラクター |  | HTTP 例 | 型付きコマンド＋否定テスト |
| 読み取り専用 UI は更新不可 |  |  |  |  |  | 存在しないケイパビリティメンバー＋否定テスト |

Quint の項目は有限・境界付きの検査であり、無制限の全実行を対象とする証明ではありません。コマンドと対象範囲の詳細は[再現性ノート](docs/reproducibility.md)を参照してください。

再現可能な変更と、それぞれを検出する独立した検査については、[障害注入実験](docs/failure-injection.md)を参照してください。

## 検証

```bash
make verify
```

個別のゲートには `make verify-spec`、`make verify-lean`、`make build-native`、`make test-pbt`、`make generate-traces`、`make test-mbt`、`make test-backend`、`make test-frontend` を利用できます。

## HTTP API

アダプターは `cd backend && ./gradlew bootRun` で起動します。提供するルートは次のとおりです。

- `POST /orders`
- `POST /orders/{id}/reserve`、`POST /orders/{id}/confirm`、`POST /orders/{id}/cancel`
- `GET /orders/{id}`、`GET /inventory/{sku}`

アダプターは閉じたドメイン結果を HTTP レスポンスへ対応付けます。業務上の状態遷移ルールはドメイン／アプリケーション層に置かれます。

## フロントエンド EDSL の検証ゲート

```bash
cd frontend
npm run typecheck
npm run verify:features
```

型検査には、読み取り専用画面がアクションを追加できないこと、および誤ったコマンドペイロードがコンパイルできないことを証明する否定テストが含まれます。機能境界検査では、`src/features` 内での直接的な `fetch`、`axios`、バックエンドのエンドポイントリテラルを拒否します。

## フロントエンドアプリケーション

```bash
cd frontend
npm test
npm run build
npm run dev
```

`ScreenRenderer` は EDSL ノードを描画し、クエリー／コマンドの実行をインタープリター内の単一ネットワークモジュールへ委譲します。Vite の開発サーバーは `/orders` と `/inventory` をポート 8080 の Spring バックエンドへプロキシします。デプロイ時は代わりに `VITE_API_BASE_URL` を設定できます。
