# povo-2.0-revanced の実現手段と調査知見

作業着手前にこの文書を全文読む。共通方針と目的・行動義務・禁止事項は [AGENTS.md](AGENTS.md) を参照する。

## 共通方針に対する固有の実現手段

- 対象の共通ルール: 検証のプロジェクト別ビルド・パッケージング。
- 理由: RVPにはAndroid DEXを同梱し、APKMをそのままManagerへ渡せないため。
- 適用範囲: GradleのRVPと既存CYD中継API。
- 代替手段・検証: buildとbuildAndroid、RVP構造検査、relayの単体テスト、複数世代の適用と実機確認を使う。
- 文書構成: 実現手段・環境・過去の調査知見はこの文書を正本とする。利用者向け導入・使用法はREADME、更新の入口はhow-to-update、実測結果はverification、変更意図はCHANGELOGに残す。共通方針の品質・秘密情報保護・ユーザー指示を免除しない。
- 移行基準: 2026-10-16の既定ブランチ `6d2b98f804020c3c590d734eda356702dff2ee3e`。以下は出典の本文を移動したもので、過去の確認日時・適用条件・未検証事項を維持する。


<a id="agents-4"></a>

移動元: [AGENTS.md](AGENTS.md)。

## Environment
- `povo-2.0-apks`にあるファイルはapkではなくapkm(bundle)である
- `povo-2.0-apks`にあるapkmで複数世代の適用確認ができる


<a id="agents-5"></a>

移動元: [AGENTS.md](AGENTS.md)。

## Dependabotラベルの運用

- 明示指定のラベルはDependabotが自動初期化するとは限らない。既定ブランチの設定を正本とする専用workflowで不足分だけ維持する。検証と復旧は `how-to-update.md` のDependabotラベル節、回帰は `.github/tests/test-dependabot-labels.py`。既存の自動マージ・承認は別のworkflowであり、ラベルの変更で回避しない。


<a id="readme-11"></a>

移動元: [README.md](README.md)。

## 公式版を残した検証

「検証用別パッケージID」は既定で無効の開発・実機検証専用パッチです。有効にすると package を `com.kddi.kdla.jp.revanced` へ変更し、provider authority、アプリ固有 permission/action、process も同じ別IDへ変更します。ログイン redirect の URL scheme/host は変更しません。

これにより公式版 `com.kddi.kdla.jp` のアプリデータとログイン状態を残したまま並行インストールできます。検証版は別アプリ扱いなので、利用規約の確認と検証用IDへのログインは改めて必要です。本番利用用 release ではこのパッチを選択しないでください。


<a id="readme-13"></a>

移動元: [README.md](README.md)。

## ビルド

JDK 21、Android SDK、GitHub Packages を読める GitHub token が必要です。

```powershell
./gradlew build :patches:buildAndroid
```

成果物は`patches/build/libs/patches-<version>.rvp`です。`buildAndroid`を省略するとManagerが読み込めるDEXが入らないため、通常の`build`だけで作ったRVPを配布しないでください。認証情報をファイルへコミットしないでください。


<a id="readme-15"></a>

移動元: [README.md](README.md)。

## 依存更新の自動処理

Dependabot は対象の依存関係を毎週確認します。patch／minor 更新は PR のチェック（Build）が成功した後に自動で squash merge されます。CI の失敗ジョブは 1 回だけ再実行します。再失敗した PR は残して手動で修正します。major 更新は手動で確認します。


<a id="update-1"></a>

移動元: [how-to-update.md](how-to-update.md)。

## 前提

- JDK 21
- Android SDK（API 36、Build Tools 36.1.0 以上）
- GitHub CLI で認証済み
- GitHub Packages を読める token
- 検証対象の正規な povo 2.0 APKM


<a id="update-2"></a>

移動元: [how-to-update.md](how-to-update.md)。

## 1. 依存関係を更新する

1. `settings.gradle.kts` の ReVanced patches Gradle plugin を公式最新版へ更新する。
2. `gradle/libs.versions.toml` の Patcher と smali を公式最新版へ更新する。
3. `gradle/wrapper/gradle-wrapper.properties` の Gradle URL と公式 SHA-256 を更新する。
4. 代替候補、採用理由、互換性を CHANGELOG に記録する。

依存更新後は lockfile の有無を確認する。本プロジェクトの Gradle 構成には lockfile をまだ生成していない。

現在の追加テスト依存は JUnit 4.13.2 だけで、メール解析の JVM ユニットテストに限定して使う。Android 実行時へ同梱されず、独自 test runner より標準の Gradle レポートと失敗判定を利用できるため採用した。Kotlin test への移行は、Java 実装を Kotlin 化する場合に再評価する。


<a id="update-3"></a>

移動元: [how-to-update.md](how-to-update.md)。

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


<a id="update-4"></a>

移動元: [how-to-update.md](how-to-update.md)。

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


<a id="update-5"></a>

移動元: [how-to-update.md](how-to-update.md)。

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


<a id="update-6"></a>

移動元: [how-to-update.md](how-to-update.md)。

## 5. バージョンと文書を更新する

1. `gradle.properties` の `version` を更新する。
2. `CHANGELOG.md` の `[Unreleased]` から同じバージョンの節を作る。
3. README、`patches.json`、この手順、検証記録の更新要否を確認する。
4. `scripts/generate-patches-json.ps1` で Manager source metadata を生成し、JSON と RVP の版を一致させる。
5. Managerへ登録するraw URL（`https://raw.githubusercontent.com/roflsunriz/povo-2.0-revanced/main/patches.json`）がJSONを返し、その`download_url`から最新RVPを取得できることを確認する。


<a id="update-7"></a>

移動元: [how-to-update.md](how-to-update.md)。

## 6. リリースする

`v<version>` タグを main の対象コミットへ付けて push する。release workflow は次を行う。

- lint・テスト・RVP ビルド
- `buildAndroid`によるAndroid用`classes.dex`のRVP同梱
- `CHANGELOG.md` の該当バージョンだけを release 本文へ抽出
- RVP を安定名 `povo-2.0-patches.rvp` として添付
- Manager source metadata `patches.json` を添付
- build provenance を生成

公開後、ReVanced ManagerへGitHub ReleaseのURLを直接登録しない。README記載のraw URLからremote sourceを追加する経路と、公開RVPを「Select from storage」で追加する経路の両方で、パッチ列挙まで確認する。


<a id="update-8"></a>

移動元: [how-to-update.md](how-to-update.md)。

## ロールバック

- パッチ bundle の問題: 直前の正常 release の `patches.json` URL または RVP を Manager へ登録する。
- パッチ版アプリの問題: パッチ版をアンインストールし、Google Play から公式版を再導入して再ログインする。
- 状態データの問題: 自動更新画面の「コードと履歴を削除」で暗号化コード、時刻、履歴を消去する。
- release の誤り: release や tag を破壊的に付け替えず、修正版を新しい patch version として公開する。


<a id="update-9"></a>

移動元: [how-to-update.md](how-to-update.md)。

## Dependabot PR の更新

前提は `.github/dependabot.yml` と PR 用 CI（Build）です。更新 PR の head SHA と `gh pr checks <PR番号>` の結果を確認してください。patch／minor は全チェック成功後に自動取り込みされます。初回 CI 失敗は failed jobs のみを 1 回再実行し、再失敗した PR は残して手動で修正します。

設定を変えたときは `actionlint .github/workflows/dependabot-automation.yml` と実際の PR の Actions 結果を確認します。問題があれば呼び出し先の共通 workflow SHA を直前の検証済み値へ戻すコミットを push します。取り込まれた依存更新に問題があれば通常の revert コミットで復旧します。

CI 完了より Dependabot の分類が遅れる場合は、`callback_workflow_file` が指す呼び出し側 workflow を `workflow_dispatch` し、同じ PR 番号・head SHA・全チェックを再確認する。呼び出し側のファイル名を変える際はこの入力も一緒に更新する。


<a id="update-10"></a>

移動元: [how-to-update.md](how-to-update.md)。

## Dependabotラベルの維持

`.github/workflows/dependabot-labels.yml` は既定ブランチのDependabot設定を読み、`updates` と `multi-ecosystem-groups` が明示指定するラベルの不足分だけ作成します。設定変更、ラベルの編集・削除、毎日04:17 UTC、手動実行で検査します。既存ラベルの色・説明やPRへの付与状態は変更しません。書込権限は専用ジョブの `issues: write` に限定し、PRの検証ジョブは読み取り専用です。

ラベルが不足する場合、Actionsの「Maintain Dependabot labels」を既定ブランチで手動実行し、`All configured Dependabot labels exist` を確認します。権限エラーやarchivedラベルとの競合は失敗として通知されます。Dependabot設定のラベル名を別名へ付け替えたり、CI・承認条件を無効化して回避しないでください。

変更時は次を実行し、Actions上でも検証してください。

```powershell
python -m pip install PyYAML==6.0.3
python .github/tests/test-dependabot-labels.py
actionlint .github/workflows/dependabot-labels.yml
```

Dependabot更新そのものの成功は、更新ジョブのログと結果で確認します。指定ラベルが無い場合はGitHubの仕様では無視されるため、実際のジョブ失敗をラベル不足だけと決めつけないでください。更新チェックの手動実行はGitHubのDependabot更新画面で提供される場合に使用し、Actions APIが再試行不可と返す実行は成功扱いしません。


<a id="agents-context"></a>

移動元: [AGENTS.md](AGENTS.md)。

## 対象サービスと過去の利用条件

- Povo 2.0 はKDDI株式会社が提供するモバイル回線ブランドである
- Povo 2.0は「トッピング」と呼ばれる回線プランを適宜購入して「プロモコード」と呼ばれるコードを手入力して回線の使用を開始するスタイルである
- ユーザーは7日間有効・24回分の使い放題トッピングを利用している（1個のプリペイド・プロモコードを24回入力する）
- 8/31 10:40時点で24回のうち4回目の終端が16:42に来るので途中から利用できる方法も考案する
- povo 2.0アプリはログインが必要
