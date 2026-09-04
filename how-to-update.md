# 更新手順

## 前提

- JDK 21
- Android SDK（API 36、Build Tools 36.1.0 以上）
- GitHub CLI で認証済み
- GitHub Packages を読める token
- 検証対象の正規な povo 2.0 APKM

## 1. 依存関係を更新する

1. `settings.gradle.kts` の ReVanced patches Gradle plugin を公式最新版へ更新する。
2. `gradle/libs.versions.toml` の Patcher と smali を公式最新版へ更新する。
3. `gradle/wrapper/gradle-wrapper.properties` の Gradle URL と公式 SHA-256 を更新する。
4. 代替候補、採用理由、互換性を CHANGELOG に記録する。

依存更新後は lockfile の有無を確認する。本プロジェクトの Gradle 構成には lockfile をまだ生成していない。

現在の追加テスト依存は JUnit 4.13.2 だけで、メール解析の JVM ユニットテストに限定して使う。Android 実行時へ同梱されず、独自 test runner より標準の Gradle レポートと失敗判定を利用できるため採用した。Kotlin test への移行は、Java 実装を Kotlin 化する場合に再評価する。

## 2. povo 新版を調査する

1. APKM の `info.json` と `base.apk` を一時領域へ展開する。
2. package 名、versionCode、versionName、minSdk、targetSdk を確認する。
3. 次の安定点が残っていることを確認する。
   - プロモコード入力の `promoCode` 文字列と1個の String 引数
   - `PromoCodeModel` を受け取る結果通知
   - `expiry_date`、`start_date`、`current`、`future` を読む general addon parser
   - `AmApplication.onCreate`
   - Koin の `KoinApplication has not been started` 例外文字列
4. 難読化されたクラス名・メソッド名を固定値へ追加しない。
5. 公式の商品構成と実メールの表現を確認し、商品名の列挙より、コード種別、コードに紐づく回数、即時適用回数、有効時間、入力期限の自然言語・数字抽出を優先する。
6. 7日24回分、24時間5回分、購入直後1回＋残り11回コードの7日12回分、2時間単発コードで、回数境界と自動反復可否のユニットテストを通す。
7. 保存スキーマを変更する場合は、既存コード、適用済み回数、有効時間、入力期限、現在終端、有効状態を保持する明示的マイグレーションと実機上書き回帰を行う。
8. background起動、画面消灯、低速または一時的に未検証のnetwork、境界前拒否、応答timeout、成功後停止の状態遷移を確認する。

## 3. ビルドする

PowerShell の現在プロセスだけへ資格情報を渡す。値をログやファイルへ出力しない。

```powershell
$revancedToken = gh auth token
$revancedActor = gh api user --jq .login
$env:GITHUB_TOKEN = $revancedToken
$env:GITHUB_ACTOR = $revancedActor
$env:ORG_GRADLE_PROJECT_githubPackagesUsername = $revancedActor
$env:ORG_GRADLE_PROJECT_githubPackagesPassword = $revancedToken
./gradlew clean build :patches:buildAndroid
```

## 4. 複数世代へ適用する

CYD用中継APIを変更した場合は `python -B -m unittest relay.test_relay -v` も実行する。中継の起動・HTTPS・DBバックアップと復旧は `relay/README.md` に従う。API v1のフィールド名・型・認証を変更する場合はAndroid送信側とCYDの読み取り側の互換性を確認する。状態保存スキーマ3への移行では既存のコード・回数・期限を保持し、過去の期限取得元を `unknown` とする。

1. APKEditor の公式 release asset と SHA-256 を確認する。
2. 各 APKM を一時領域で単体 APK へ統合する。
3. ReVanced CLI の公式 release asset と SHA-256 を確認する。
4. `patches/build/libs/patches-<version>.rvp` を各単体 APK へ適用する。
5. 次を確認する。
   - パッチ成功
   - `scripts/verify-android-rvp.ps1`による`classes.dex`同梱確認
   - extension DEX の存在
   - manifest コンポーネントと権限
   - native ABI
   - split 必須 metadata の除去
   - APK v2/v3 署名
   - 16 KiB page alignment

公式版を消さず実機確認する場合は「プロモコード自動更新」と「検証用別パッケージID」を同時に選ぶ。出力 package が `com.kddi.kdla.jp.revanced` で、元IDの provider authority・独自 permission/action・process が残っていないことを確認してからインストールする。

16 KiB alignment は最終署名後に再検証する。既存APKの `META-INF/*.SF`、`*.RSA`、`*.DSA`、`*.EC`、`MANIFEST.MF` が残っていると、再署名時の削除でnative entryのoffsetが変わる。検証用APKは旧署名entryを除去してから `zipalign -P 16` を行い、その後に署名する。

結果と対策を `verification.md` へ追記する。

## 5. バージョンと文書を更新する

1. `gradle.properties` の `version` を更新する。
2. `CHANGELOG.md` の `[Unreleased]` から同じバージョンの節を作る。
3. README、`patches.json`、この手順、検証記録の更新要否を確認する。
4. `scripts/generate-patches-json.ps1` で Manager source metadata を生成し、JSON と RVP の版を一致させる。
5. Managerへ登録するraw URL（`https://raw.githubusercontent.com/roflsunriz/povo-2.0-revanced/main/patches.json`）がJSONを返し、その`download_url`から最新RVPを取得できることを確認する。

## 6. リリースする

`v<version>` タグを main の対象コミットへ付けて push する。release workflow は次を行う。

- lint・テスト・RVP ビルド
- `buildAndroid`によるAndroid用`classes.dex`のRVP同梱
- `CHANGELOG.md` の該当バージョンだけを release 本文へ抽出
- RVP を安定名 `povo-2.0-patches.rvp` として添付
- Manager source metadata `patches.json` を添付
- build provenance を生成

公開後、ReVanced ManagerへGitHub ReleaseのURLを直接登録しない。README記載のraw URLからremote sourceを追加する経路と、公開RVPを「Select from storage」で追加する経路の両方で、パッチ列挙まで確認する。

## ロールバック

- パッチ bundle の問題: 直前の正常 release の `patches.json` URL または RVP を Manager へ登録する。
- パッチ版アプリの問題: パッチ版をアンインストールし、Google Play から公式版を再導入して再ログインする。
- 状態データの問題: 自動更新画面の「コードと履歴を削除」で暗号化コード、時刻、履歴を消去する。
- release の誤り: release や tag を破壊的に付け替えず、修正版を新しい patch version として公開する。
