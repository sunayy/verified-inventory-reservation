# 障害注入実験

意図的な defect をどの独立層が拒否するかを確認する実験です。使い捨て branch で mutation を加え、終了後に revert してください。

| mutation | 実行 command | 検出器 |
| --- | --- | --- |
| `spec/inventory.qnt` の reserve capacity guard を削除 | `make verify-spec` | `ReservationWithinStock` または `ReservationConsistency` 違反 |
| Lean の capacity 条件を壊す | `make verify-lean` | theorem elaboration / build 失敗 |
| Kotlin の capacity-plus-one を成功させる | `make test-pbt` | Kotest の seed・縮小済み counterexample を伴う property 失敗 |
| Kotlin cancellation で reservation を解放しない | `make test-mbt` | trace、seed、step、action、expected、actual を含む `TraceReplayFailure` |
| read-only DSL context に `action(...)` を追加 | `make test-frontend` | 未使用の `@ts-expect-error` による TypeScript compile 失敗 |
| feature module に `fetch(...)` を追加 | `make test-frontend` | `verify:features` の静的 boundary 失敗 |

これらは duplicate oracle ではありません。Quint は MBT expected state、Lean は実行する definition の証明、PBT は独立した関係、TypeScript は UI language の表現可能性を担当します。
