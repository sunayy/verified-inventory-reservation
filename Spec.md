[200~# Codex Development Instructions

## 0. このプロジェクトの目的

このプロジェクトの主目的は、単にWebアプリケーションを完成させることではない。

**AIエージェントによる実装を、人間による詳細レビューではなく、形式仕様・型・証明・生成テスト・DSLによって機械的に制約する開発方式を実験すること**を目的とする。

したがって、以下を重視する。

1. 仕様を実装コードより先に定義する
2. 可能な性質は静的に保証する
3. 状態遷移はQuintをOracleとして検証する
4. 汎用的な純粋ロジックはLeanで実装・証明する
5. Kotlin実装についてProperty-Based Testingを行う
6. Quintが生成したTraceをKotlin実装へreplayするModel-Based Testingを行う
7. Reactアプリケーションを直接自由記述せず、EDSLの制約下で記述する
8. 「テストが通った」ではなく「どの層が何を保証しているか」を明示する

---

# 1. 題材

## Inventory Reservation System

ECサイト向けの簡易在庫予約システムを作る。

ユーザーは商品について注文を作成し、以下の状態遷移を行う。

```text
Draft
  |
  | reserve
  v
Reserved
  |       |
confirm   cancel
  |       |
  v       v
Confirmed Cancelled
```

MVPでは注文1件につきSKU 1種類としてよい。

後から複数SKU注文に拡張可能な設計にする。

---

# 2. 技術スタック

## Formal Specification

* Quint
* ITF trace
* Quint simulator / verifier

## Backend

* Kotlin/JVM
* Spring Boot
* Gradle Kotlin DSL
* Kotest
* Kotest Property Testing

## Verified Logic

* Lean 4
* Lake
* Leanからnative shared libraryを生成する
* Lean `@[export]`
* C ABI
* JNI shim
* Kotlin/JVM

呼び出し経路は以下とする。

```text
Kotlin
  ↓
JNI
  ↓
C shim
  ↓
Lean generated native library
  ↓
Lean implementation
```

Leanの内部表現を直接Kotlinへ露出させないこと。

JNI境界はprimitiveな値を中心にした小さなAPIにすること。

## Frontend

* React
* TypeScript
* Vite
* 独自EDSL
* Vitest

---

# 3. リポジトリ構成

原則として以下のmonorepo構成にする。

```text
/
├── AGENTS.md
├── README.md
├── Makefile
│
├── spec/
│   ├── inventory.qnt
│   ├── traces/
│   └── README.md
│
├── verified/
│   ├── lakefile.lean
│   ├── lean-toolchain
│   ├── Reservation/
│   │   ├── Domain.lean
│   │   ├── Logic.lean
│   │   ├── Proofs.lean
│   │   └── Export.lean
│   └── native/
│       ├── reservation_jni.c
│       └── reservation_jni.h
│
├── backend/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
│
├── frontend/
│   ├── package.json
│   └── src/
│       ├── dsl/
│       ├── interpreter/
│       └── features/
│
└── scripts/
    ├── generate-traces.sh
    ├── verify-spec.sh
    └── build-lean-native.sh
```

依存方向を厳守する。

```text
Quint
   ↓ specification / traces

Lean
   ↓ native function

Kotlin domain/application
   ↓ HTTP

Frontend EDSL
   ↓ interpreter
React
```

QuintやLeanがSpringやReactに依存する構造にしてはならない。

---

# 4. Quintをシステム仕様のSource of Truthとする

最初にKotlinコードを書いてはならない。

まず以下を `spec/inventory.qnt` に定義すること。

## State

最低限以下を持つ。

```text
onHand
reserved
sold
orders
```

概念的には、

```text
available = onHand - reserved
```

とする。

Orderは最低限以下の状態を持つ。

```text
Draft
Reserved
Confirmed
Cancelled
```

---

# 5. Quint Actions

最低限、

```text
createOrder
reserve
confirm
cancel
```

を定義する。

必要なIDや数量についてはnondeterministicに生成可能なモデルとする。

重要：

MBTでactionとparameterを復元できるようにQuintモデルを書くこと。

Trace生成時にMBT metadataを使用できる構造にする。

---

# 6. Quint Invariants

最低限以下のInvariantを定義する。

## StockNonNegative

```text
onHand >= 0
```

## ReservationWithinStock

すべての商品について、

```text
reserved <= onHand
```

## StockConservation

MVPではrestockを実装しない。

したがって、

```text
initialStock = onHand + sold
```

が常に成立する。

## ReservationConsistency

Reserved状態の注文だけがreservationを保持できる。

概念的には、

```text
order.status == Reserved
iff
reservation exists for order
```

に近い性質を定義する。

ただし実際のモデル表現に応じて適切に定式化する。

## TerminalState

ConfirmedまたはCancelledとなった注文は、

```text
reserve
confirm
cancel
```

によって別状態へ遷移してはならない。

---

# 7. Quint verification

仕様作成後、まずQuintだけで検証する。

少なくとも、

* lint
* simulation
* invariant checking
* bounded verification

を実行する。

実装を開始する前に、仕様単体が正常であることを確認すること。

反例が発見された場合、実装側で回避するのではなく、まず仕様が正しいか検討する。

---

# 8. MBT用Trace生成

QuintからITF Traceを生成する。

概念的には以下の形式で実行する。

```bash
quint run spec/inventory.qnt \
  --mbt \
  --n-traces=100 \
  --out-itf="spec/traces/trace_{seq}.itf.json"
```

実際のQuintバージョンで利用できるCLI optionを確認し、必要なら調整する。

乱数seedを保存する。

CIで失敗したTraceはartifactとして残す。

Traceには少なくとも、

```text
state
actionTaken
nondeterministic parameters
```

をKotlin側で復元するために必要な情報が含まれるようにする。

---

# 9. Kotlin Domain Model

Kotlinではprimitive obsessionを避ける。

例：

```kotlin
@JvmInline
value class OrderId(val value: String)

@JvmInline
value class Sku(val value: String)

@JvmInline
value class Quantity(val value: Long)
```

ただし不正な値をvalue classのconstructorから自由に作れる設計にはしない。

可能ならSmart Constructorを使用する。

OrderStatusはsealed hierarchyまたはenumなど、状態の閉世界性を保証できる方法を使用する。

when式はexhaustiveにする。

---

# 10. Application Command

UseCaseは最低限、

```text
CreateOrder
ReserveOrder
ConfirmOrder
CancelOrder
```

とする。

Controllerへbusiness logicを書いてはならない。

---

# 11. 状態遷移の表現

以下のような、

```kotlin
if (status == ...) {
    ...
}
```

がコードベース全体へ散らばる設計を避ける。

Domain layerで状態遷移規則を集約する。

可能なら不正状態を型で表現不能にする。

例えば、

```text
DraftOrder
ReservedOrder
ConfirmedOrder
CancelledOrder
```

などの型表現も検討する。

ただし不必要な抽象化を増やすためではなく、

**illegal states unrepresentable**

を達成できる場合に採用する。

---

# 12. Leanで実装する領域

すべてをLeanへ移植してはならない。

Leanは、

**数学的で純粋かつ他のインフラへ依存しないcore logic**

だけを担当する。

最初の対象はReservation計算とする。

概念的には、

```text
available(onHand, reserved)

canReserve(onHand, reserved, requested)

reserve(onHand, reserved, requested)
```

をLeanで定義する。

---

# 13. Lean theorem

最低限以下に相当する定理を証明する。

## AvailableNeverExceedsOnHand

```text
available(onHand, reserved) <= onHand
```

## SuccessfulReservationPreservesCapacity

前提：

```text
reserved <= onHand
requested <= available(onHand, reserved)
```

なら、

```text
reserved + requested <= onHand
```

となる。

## CancellationPreservesNonNegativeReservation

有効な予約quantityをcancelした結果、

```text
remainingReserved >= 0
```

となる。

Nat等の型表現によって自明になる場合、その型による保証を積極的に利用する。

## NoOverReservation

reservation operationがsuccessを返した場合、

```text
newReserved <= onHand
```

を保証する。

---

# 14. 証明対象と実行対象を分離しない

極めて重要。

以下のような構造は禁止する。

```text
Lean theorem用関数
Lean runtime用関数
```

を別々に実装し、同じロジックをコピーすること。

証明したdefinitionそのもの、またはそのdefinitionから直接構成された関数をruntimeで使用すること。

目的は

```text
prove(f)
execute(g)
```

ではなく、

```text
prove(f)
execute(f)
```

に近づけることである。

---

# 15. Lean品質条件

最終状態で、

```lean
sorry
```

を残してはならない。

`axiom` を証明回避目的で追加してはならない。

`unsafe` はFFI等、本質的に必要な境界以外では使用しない。

Proofを通すために定義自体を無意味なものへ変更してはならない。

---

# 16. Lean → JNI

Lean側でexport用の薄いAPIを定義する。

例：

```lean
@[export reservation_available]
def reservationAvailable (...) := ...
```

実際のABIに適したsignatureに調整すること。

Lean特有の複雑なobject representationをJNI側へ漏らさない。

必要ならC wrapperを挟む。

JNI APIは例えば、

```kotlin
internal object VerifiedReservation {
    external fun available(
        onHand: Long,
        reserved: Long
    ): Long

    external fun canReserve(
        onHand: Long,
        reserved: Long,
        requested: Long
    ): Boolean
}
```

程度に留める。

---

# 17. JNI境界の責務

JNI boundaryでは必ず、

```text
Kotlin Long
   ↓ validation
non-negative integer
   ↓
Lean-compatible representation
```

という変換を行う。

負数などLean domainに存在しない値を渡さない。

Native call failureをdomain errorと混同しない。

---

# 18. Kotlin PBT

Kotest Property Testingを使用する。

Example-based testだけで終了してはならない。

最低1000 iteration程度を基本とする。

失敗時にはseedとshrunk counterexampleが再現可能であること。

---

# 19. PBT Properties

最低限以下を書く。

## available bounds

任意の、

```text
0 <= reserved <= onHand
```

について、

```text
0 <= available <= onHand
```

## reservable capacity

任意のvalid stateに対して、

```text
requested <= available
```

ならreservation後も、

```text
reserved <= onHand
```

## too-large reservation rejection

```text
requested > available
```

ならReserveは成功してはならない。

## cancellation restoration

reserve後にcancelすると、

```text
available
```

がreserve前の値へ戻る。

## terminal status

Confirmed/Cancelledの注文に対して任意のcommand sequenceを試してもterminal stateを抜けない。

---

# 20. PBTでは生成器をDomain-awareにする

無意味に、

```text
Long.MIN_VALUE..Long.MAX_VALUE
```

を投入するだけのテストにしない。

有効状態generatorと不正状態generatorを分離する。

例えば、

```kotlin
Arb.bind(
    Arb.long(0..1_000_000),
    ...
)
```

などを利用し、

```text
reserved <= onHand
```

を満たすState generatorを定義する。

同時にboundary caseを十分に生成する。

特に、

```text
0
1
onHand
onHand - 1
available
available + 1
```

付近を重視する。

---

# 21. Model-Based Testing

QuintはModel Oracleとする。

Kotlin側にQuintモデルをコピー実装してはならない。

ITF parserを実装し、

```text
Quint Trace
     ↓
Trace Parser
     ↓
Model Action
     ↓
HTTP / Application API
     ↓
Actual State
     ↓
Quint Expected State
```

というreplay engineを作る。

---

# 22. Trace replay

例えばTraceが、

```text
create(order1)
reserve(order1, sku1, 3)
confirm(order1)
```

なら、同じ順序でKotlin applicationへcommandを送る。

各step終了時に、

```text
expected model state
actual implementation state
```

を比較する。

最終stateだけ比較してはならない。

**各transition後に比較すること。**

---

# 23. State projection

Quint modelとproduction implementationは内部表現が違ってよい。

そのためKotlin側に、

```kotlin
fun ApplicationState.toModelProjection(): ModelState
```

に相当するprojectionを定義する。

比較対象は、

```text
Quint state
vs
Kotlin model projection
```

とする。

implementation detailをQuintへ漏らさない。

---

# 24. MBT Failure output

失敗時に最低限、

```text
trace file
seed
step index
action
parameters
expected state
actual state
```

を表示する。

例えば、

```text
Trace: trace_042.itf.json
Step: 7
Action: reserve
Order: order-2
Quantity: 4

Expected:
...

Actual:
...
```

のようなdiagnosticsを出す。

---

# 25. Frontendの基本思想

Featureコードで任意のReact JSXを書く方式にしない。

画面実装者は原則としてEDSLを書く。

```text
Feature Definition
       ↓
EDSL AST
       ↓
Interpreter
       ↓
React
```

という構造にする。

---

# 26. React EDSL

最低限、

```text
Screen
Section
Text
Field
Action
Query
Command
Table
```

程度の概念を定義する。

例えば概念的に、

```typescript
defineScreen({
  title: "Inventory",

  query: inventoryQuery,

  body: ({ data }) => [
    section([
      table(...)
    ])
  ]
})
```

のようにする。

具体的なsyntaxはより型安全なものを設計してよい。

---

# 27. EDSL Capability

任意のcomponentが任意のoperationを実行できないようにする。

例えば、

```text
QueryCapability
CommandCapability
NavigationCapability
```

を定義する。

read-only screenからCommandを発行できないよう型で制約する。

概念的には、

```typescript
defineScreen<ReadOnly>({
   ...
})
```

ではmutation actionを定義不能にする。

---

# 28. EDSL Command

Featureから直接、

```typescript
fetch(...)
axios(...)
```

してはならない。

例えば、

```typescript
command(reserveOrder, ...)
```

を通す。

Command definitionはrequest/response/errorを型として持つ。

---

# 29. EDSLで禁止するもの

`frontend/src/features` 以下では原則禁止：

```text
fetch
axios
useEffectによるserver fetch
生のform submit
任意のURL文字列
backend endpoint文字列
```

Network accessはDSL interpreter側へ閉じ込める。

必要ならESLint custom ruleまたはimport restrictionで機械的に禁止する。

---

# 30. EDSL escape hatch

完全禁止によって実用性を失わせない。

どうしてもDSLで表現できない場合、

```typescript
unsafeCustom(...)
```

のような明示的escape hatchを一箇所用意してもよい。

ただし利用箇所を検索可能にし、

```bash
grep unsafeCustom
```

で一覧できる状態にする。

通常コードと区別できないescape hatchは禁止。

---

# 31. Compile-time negative tests

EDSLについて、

「正しいコードがcompileする」

だけでは不十分。

**間違ったコードがcompileしないこと**もテストする。

例えば、

```typescript
// @ts-expect-error
readOnlyScreen.command(...)

// @ts-expect-error
commandWithWrongPayload(...)
```

のようなnegative compile testsを用意する。

---

# 32. API

最低限、

```text
POST /orders
POST /orders/{id}/reserve
POST /orders/{id}/confirm
POST /orders/{id}/cancel

GET /orders/{id}
GET /inventory/{sku}
```

を用意する。

ただしAPIの都合でDomain Modelを歪めない。

---

# 33. Persistence

第一段階ではin-memory repositoryでよい。

今回の実験対象は、

```text
Formal Specification
Lean
PBT
MBT
EDSL
```

の統合である。

Database transactional semanticsはMVPではスコープ外とする。

ただしRepository interfaceを用意し、後からPostgreSQL等へ交換可能にする。

---

# 34. Error Modeling

Exceptionを通常のdomain control flowとして乱用しない。

最低限、

```text
OrderNotFound
AlreadyReserved
InsufficientStock
InvalidTransition
AlreadyTerminal
```

などを閉じた型として表現することを検討する。

Kotlinではsealed interface等を利用する。

---

# 35. 実装順序

以下の順番を厳守する。

## Phase 1

Quintのみ。

```text
domain model
actions
invariants
simulation
verification
```

Kotlinを実装しない。

## Phase 2

Lean core。

```text
definitions
theorems
native export
```

## Phase 3

Kotlin domain。

Lean functionをJNI経由で利用する。

## Phase 4

PBT。

Kotlin/Lean統合をpropertyとして検証する。

## Phase 5

Quint trace parser。

ITFをKotlin modelへdecodeする。

## Phase 6

MBT。

Traceをbackendへreplayする。

## Phase 7

HTTP API。

## Phase 8

Frontend EDSL。

## Phase 9

EDSL interpreterによるReact UI。

## Phase 10

CIとdocumentation。

---

# 36. 各Phaseの終了条件

次Phaseへ進む前に、

```text
build
typecheck
lint
tests
formal verification
```

のうち該当するものを通す。

エラーを残したまま先へ進まない。

---

# 37. CI

CIで最低限以下を実行する。

```text
Quint lint
Quint verification
Lean build
Lean proof check
native library build
Kotlin compile
Kotlin unit tests
Kotlin PBT
Kotlin MBT
TypeScript typecheck
frontend unit tests
EDSL negative compile tests
```

---

# 38. Make targets

rootから最低限以下が実行できるようにする。

```bash
make verify-spec
make verify-lean
make build-native
make test-pbt
make generate-traces
make test-mbt
make test-backend
make test-frontend
make verify
```

最終的に、

```bash
make verify
```

一発で全mechanical checksを実行できること。

---

# 39. Dockerを必須にしない

ローカルでの形式検証やunit testのためだけにDockerを必須にしない。

可能な限り直接toolchainを使用する。

---

# 40. Reproducibility

可能な限りバージョンを固定する。

最低限、

```text
Java
Kotlin
Gradle
Lean
Quint
Node
TypeScript
```

について再現可能にする。

Nixを導入してもよい。

Nixを使用する場合、開発者が、

```bash
nix develop
```

した後に、

```bash
make verify
```

を実行できる状態が望ましい。

---

# 41. Documentation

READMEに「使い方」だけを書くのではなく、

## Guarantee Matrix

を作る。

例えば、

| Property                   | Quint | Lean | Kotlin Type | PBT | MBT | TS EDSL |
| -------------------------- | ----: | ---: | ----------: | --: | --: | ------: |
| reserved <= stock          |     ✓ |    ✓ |             |   ✓ |   ✓ |         |
| terminal state             |     ✓ |      |           ✓ |   ✓ |   ✓ |         |
| reservation arithmetic     |       |    ✓ |             |   ✓ |     |         |
| command payload shape      |       |      |           ✓ |     |     |       ✓ |
| read-only UI cannot mutate |       |      |             |     |     |       ✓ |

実際にどの仕組みが何を保証しているかに合わせて作成する。

---

# 42. 「保証」の表現

READMEで、

```text
formally verified
proved
guaranteed
```

等の表現を安易に使わない。

例えばQuint bounded verificationしか行っていないものを、

```text
all possible executions are proven correct
```

などと表現してはならない。

保証範囲を正確に記述する。

---

# 43. Duplicate Oracle禁止

非常に重要なルール。

「テストを通すための期待値」をproduction implementationと同じロジックから作ってはならない。

禁止例：

```kotlin
val expected = productionFunction(input)
val actual = productionFunction(input)
```

MBTのOracleはQuint。

Lean theoremの対象はLean definition。

PBTは独立したpropertyを記述する。

それぞれ役割を分離する。

---

# 44. Example-Based Testも禁止しない

PBTですべてを置き換えない。

Example testは、

```text
HTTP serialization
specific regression
known edge case
JNI error handling
trace parser
```

などに使用してよい。

テストを、

```text
Example tests
Property tests
Model-based tests
Formal verification
Proofs
```

という複数層で構成する。

---

# 45. Agent Development Policy

AIエージェントは、単にコードを追加してテストを通すことを目的としてはならない。

変更するたびに、

```text
この変更によって
どのInvariantが影響を受けるか
どのProofが影響を受けるか
どのPropertyが影響を受けるか
どのTrace semanticsが影響を受けるか
どのEDSL constraintが影響を受けるか
```

を考える。

---

# 46. Constraint First

新機能を実装する際、

```text
実装
↓
テスト
```

ではなく、可能な限り、

```text
Invariant / Type / Proof / Property
↓
Implementation
```

の順にする。

---

# 47. 機械的制約を優先する

コードレビューで、

```text
ここではfetchを使わないでください
```

と指摘するだけのルールは禁止。

可能なら、

```text
ESLint
TypeScript type system
module visibility
architecture test
Gradle module boundary
EDSL
```

などで違反自体を不可能にする。

同様にKotlin側でもpackage conventionだけに依存せず、module boundaryやvisibilityを利用する。

---

# 48. 不正コードを試す

各mechanismについて、

**意図的に壊した場合、本当に検出されるか**

を確認する。

例：

### Quint

reserve capacity checkを削除する。

→ invariant violationが検出されること。

### Lean

証明対象の条件を破る。

→ theoremがcompileしなくなること。

### PBT

境界条件を意図的に `>` / `>=` で間違える。

→ counterexampleを発見すること。

### MBT

cancel implementationでreservationを解放しない。

→ Quint Trace replayが失敗すること。

### EDSL

read-only画面からmutationを記述する。

→ TypeScript compile errorになること。

これらをREADMEの

```text
Failure Injection Experiments
```

として記録する。

---

# 49. 完成条件

MVPはUIが綺麗だから完成なのではない。

以下を満たして完成とする。

```text
[ ] Quint modelが存在する
[ ] invariantが定義されている
[ ] model checkingが通る
[ ] ITF Traceを生成できる
[ ] MBT metadataを利用できる
[ ] Lean core implementationが存在する
[ ] Lean theoremが存在する
[ ] sorryがゼロ
[ ] Lean native libraryをbuildできる
[ ] KotlinからJNI経由でLean functionを呼べる
[ ] PBTが存在する
[ ] shrinkingされた反例を再現できる
[ ] Quint TraceをKotlinへreplayできる
[ ] 各stepのmodel stateとactual stateを比較する
[ ] React featureがEDSLで記述されている
[ ] Feature codeから直接network accessできない
[ ] EDSL negative compile testが存在する
[ ] make verifyが成功する
[ ] Guarantee MatrixがREADMEに存在する
[ ] Failure Injection Experimentが記録されている
```

---

# 50. 最重要方針

このプロジェクトでは、

> AIに正しいコードを書かせる

ことだけを目標としない。

目標は、

> AIが誤ったコードを書いたときに、できるだけ人間が読む前に機械が拒否するシステムを作る

ことである。

したがって、可読性や一般的なベストプラクティスだけでなく、

```text
formal specification
proof
types
property
model
DSL
architecture boundary
compiler
static analysis
```

を積極的に利用すること。

そして、可能な限り、

```text
Prompt says you must not do X
```

を、

```text
It is mechanically impossible to do X
```

へ変換すること。

---

# First Task

最初のタスクとして、実装コードを書かずに以下のみ行うこと。

1. 上記要件を分析する
2. 最終的なdirectory structureを提案する
3. Quintで扱うstate/action/invariantを具体化する
4. Leanで証明する領域とKotlinに残す領域の境界を決定する
5. Quint Trace → Kotlin MBTのデータフローを具体化する
6. React EDSLのAST案とcapability modelを提案する
7. Guarantee Matrixの初版を作る
8. 実装Phaseを依存関係順に整理する
9. 技術的リスクを列挙する

この時点ではproduction implementationを開始してはならない。

特に、

* Quint MBT metadata
* ITF serialization
* Lean C ABI
* Lean runtime initialization
* JNIとの型変換
* native library packaging
* TypeScript EDSL capability modeling

について、現在使用するtool versionの仕様を実際に確認した上で設計すること。

設計結果を `docs/architecture.md` として作成し、その内容を提示すること。

