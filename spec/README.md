# 形式仕様ワークスペース

[`inventory.qnt`](inventory.qnt) は在庫予約遷移の、実行可能で有限な Source of Truth です。SKU は2種類、注文 ID は3種類、数量は1〜3に限定し、simulation と model checking を実用的な規模に保ちます。state には `onHand`、`reserved`、`sold`、`orders`、注文 metadata、終端状態履歴を含めます。

```bash
npm run verify:spec
```

この command は typecheck、すべての invariant を有効にした固定 seed の TypeScript-backend simulation（1,000回）、有限状態 TLC verification を実行します。Quint 0.31.0 には独立した `lint` command がないため、static gate は typecheck です。境界付き検証は無限の実行すべてを証明するものではありません。

MBT metadata 付きの再現可能な ITF trace を100本生成します。

```bash
npm run generate:traces
```

生成物は `traces/` に置かれ、Git 管理しません。`generation.json` は version、seed、本数、最大 step、MBT 有効化を記録します。Kotlin decoder は `mbt::actionTaken` と `mbt::nondetPicks` を使用し、state diff から action を推測してはなりません。
