# エージェント開発ポリシー

このリポジトリでは、実装より先に制約を定義する。変更前に、影響する Quint invariant、Lean theorem、Kotlin property、MBT trace semantics、frontend EDSL capability を確認すること。

## 必須の依存方向

```text
Quint specification / traces → Lean の検証済み予約算術 → Kotlin domain / application
→ HTTP adapter → Frontend EDSL interpreter → React rendering
```

依存はこの方向にのみ流れる。Quint と Lean は Spring・React・application framework に依存してはならない。

## 守るべき制約

- Kotlin に Quint model を複製しない。MBT は Quint 生成 ITF trace を replay し、すべての action 後に projection を比較する。
- runtime で使う Lean export は証明済み definition そのもの、または直接の wrapper にする。
- Lean に `sorry`、証明回避の `axiom`、FFI境界以外の `unsafe` を置かない。
- frontend feature は EDSL 定義だけを書く。network access は interpreter 境界に閉じ込める。
- 通常の domain outcome は closed error/state type で表し、例外を通常制御に用いない。
- 失敗した PBT seed と Quint trace は CI artifact として残す。

## 完了条件

`make verify` が最終の統合 gate である。性質にはその所有層の check を追加し、production implementation と同じ oracle から期待値を作ってはならない。
