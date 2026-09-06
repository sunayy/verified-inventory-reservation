# 再現性

commit 済みの lock/config file は検証境界の一部です。check が失敗したとき、別 toolchain へ黙って差し替えてはいけません。

| ツール | 固定 version / 定義元 |
| --- | --- |
| Node | 22.18.0（`.nvmrc`） |
| Quint | 0.31.0（`package-lock.json`） |
| Lean / Lake | Lean 4.29.0（`verified/lean-toolchain`） |
| Java runtime | CI では Temurin 24.0.1+9 |
| Gradle | 8.14.3（`backend/gradle/wrapper`） |
| Kotlin | 2.0.21（`backend/build.gradle.kts`） |
| Spring Boot | 3.4.13（`backend/build.gradle.kts`） |
| TypeScript | 5.7.3（`frontend/package-lock.json`） |
| React | 19.1.0（`frontend/package-lock.json`） |
| Vite / Vitest | 6.2.6 / 3.1.2（`frontend/package-lock.json`） |

## ローカル検証

固定 Node、Java 24、elan 経由の Lean 4.29.0、JNI header を含む C toolchain を用意してから依存を入れます。

```bash
npm ci
npm --prefix frontend ci
make verify
```

`make verify` は Quint の境界付き検証、Lean proof/native check、Kotlin の JNI/PBT/MBT/HTTP check、TypeScript の boundary/type/unit/build check を行います。無限の在庫システムの完全証明を主張するものではありません。

native JNI build は現在 macOS を対象とします。Linux/Windows対応には `scripts/build-lean-native.sh` の platform 分岐を実装し、native test を省略せずに実行する必要があります。
