# 検証記録

## 検証環境

- 実機: SHARP AQUOS R8 pro（`SH-R80P` / `Kamille`）
- OS: Android 16（API 36）
- ABI: `arm64-v8a, armeabi-v7a, armeabi`
- インストール済み povo 2.0: 1.70.0-JP（versionCode 857、Google Play 版）
- ReVanced Patcher: 22.0.1
- ReVanced CLI: 6.0.0（公式 asset の SHA-256 を照合）
- APKEditor: 1.4.9（公式 asset の SHA-256 を照合）

## 自動検証結果

2026-08-31 に以下を確認した。

| 対象 | 結果 |
|---|---|
| Gradle `build` | 成功 |
| Android lint | エラー・警告なし |
| Java/Kotlin コンパイル | 成功 |
| PromoCodeExtractor・商品モデル・結果対応付け・再試行ポリシーのユニットテスト（13件） | 成功 |
| RVP を ReVanced CLI 6.0.0 で列挙 | 成功 |
| Gradle `:patches:buildAndroid` | 成功、RVPに`classes.dex`を同梱 |
| `scripts/verify-android-rvp.ps1` | 成功、DEX・manifest・extensionを確認 |
| actionlint 1.7.12 | 成功 |
| OSV Scanner 2.5.1 | 解決済みGradle依存32件に既知脆弱性なし |
| ReVanced Manager 2.6.0へのローカルRVP追加 | 成功、`0.1.3`・2パッチを表示 |
| 1.68.0-JP base.apk へ適用 | 成功 |
| 1.69.0-JP base.apk へ適用 | 成功 |
| 1.70.0-JP base.apk へ適用 | 成功 |
| 1.70.0-JP APKM を単体 APK へ統合後に適用 | 成功 |
| 検証用別ID化と公式版との並行インストール | 成功 |
| 検証版 1.70.0-JP のコールド起動 | 成功、クラッシュなし |
| ホーム常設カードの表示・クリック | 成功 |
| 自動更新設定画面への直接遷移 | 成功 |
| 途中利用の終了日時 `2026-08-31 16:42` 保存 | 成功 |
| メール本文からのコード登録・暗号化保存 | 成功（コード値は取得・出力せず確認） |
| 最大24・現在4・1回168時間の保存 | 成功 |
| 正確なアラーム権限 | `allow` |
| 16:37の正確な `RTC_WAKEUP` 予約 | 成功 |
| v0.1.3通常版上書き後の設定保持 | 成功、`5/24`・168時間・次回2026-09-07 16:55・有効状態を維持 |
| v0.1.3通常版上書き後のservice・WakeLock停止 | 成功、境界5分前まで常駐なし |
| 2026-09-07 16:50の正確な `RTC_WAKEUP` 予約 | 成功、`window=0`・`exactAllowReason=permission` |
| 旧スキーマから汎用商品モデルへの実機上書き移行 | 成功、コードを再入力せず `repeatable_time_code`・`4/24`・168時間・16:42・有効状態を維持 |
| 最終検証APKの16 KiB alignment・v2/v3署名 | 成功 |
| 初期化を `Application.super.onCreate()` 直後へ注入 | 実機ログで成功 |
| ログイン済みAPI controllerの遅延解決 | 実機ログで成功 |
| 初期化・契約payload指紋変更後の1.68/1.69回帰適用 | 成功 |
| 追加 DEX クラスの存在 | 3世代すべて成功 |
| manifest 権限・Activity・Service・Receiver | 3世代すべて成功 |
| APK Signature Scheme v2/v3 | 3世代すべて成功 |
| 16 KiB page alignment を含む zipalign | 3世代すべて成功 |

統合版 1.70.0-JP では `arm64-v8a` と `armeabi-v7a` の native library を含み、`requiredSplitTypes` と `com.android.vending.splits.required` が残っていないことを確認した。

## 検証した状態遷移

- メール本文の「プリペイドコード」ラベルからコードを抽出する。
- メール本文の「入力期限」に続く日時を優先し、販売予定日など後続の別日付を期限にしない。
- 単純なコード入力は大文字へ正規化し、メール扱いにしない。
- 7日24回分はコード24回、24時間5回分はコード5回、購入直後1回＋残り11回コードの7日12回分はコード11回として抽出する。
- 月末っちょの2時間単発コードは `single_time_code` と判定し、終端で自動再適用しない。
- 旧スキーマの7日24回分は `repeatable_time_code` へ移行し、現在4回目を `4/24` のまま保持する。
- `current=true` かつ `start_date` から `expiry_date` が抽出済み有効時間と一致する一般 addon だけを対象にする。
- 適用前は現在の終了時刻まで待機する。
- 反復コードの適用成功後だけコード適用済み回数を1増やし、抽出済み有効時間後へ仮予約する。次の契約応答で実時刻へ補正する。
- HTTP 401/403 相当ではコードを削除せず停止し、再ログインを通知する。
- 再起動とアプリ更新後は保存済み終了時刻からアラームを復元する。

## 実機導入結果

公式版を保護するため、検証版は `com.kddi.kdla.jp.revanced` へ別ID化した。provider authority、独自 permission/action、process に元IDとの衝突が0件であることを manifest 実測で確認後、公式版と並行インストールした。

- 公式版: `com.kddi.kdla.jp` 1.70.0-JP（857）を維持
- 検証版: `com.kddi.kdla.jp.revanced` 1.70.0-JP（857）を追加
- `adb install -r`: 成功
- `LaunchActivity` の cold start: 610 ms、process 生存、FATAL EXCEPTION なし
- 初回利用規約画面: 表示成功
- 検証用IDへのログイン: 成功
- ホームカード: `povo プロモコード自動更新`、`タップしてメール本文を登録` をUI階層で確認
- 設定画面: 手順、メール本文、最大・現在回数、有効時間、現在終了日時、主保存、一時停止、削除を確認し、正確なアラームは保存後に自動案内
- 初回操作: 入力項目を1つの主ボタンで保存し、設定済みの場合だけ一時停止と削除を表示
- 進捗表示: `利用回数 4/24`、`1回 168時間`、`自動更新: 有効`、次回16:42を確認
- Koin controller: Activity再開時の遅延解決成功をログで確認
- 初期化経路: `Application.onCreate` の早期returnに影響されず、ホーム再開時にカードを追加
- Google/Firebase の別package未登録警告: analytics/config 系で確認したが、起動継続には影響なし

汎用商品モデル追加後は、前回と同じ署名鍵で生成した1.70.0-JP検証APKを `adb install -r` した。アンインストールやアプリデータ削除は行っていない。上書き前後をUI階層と `dumpsys alarm` で比較し、次を確認した。

- 上書き前: `4/24`、168時間、次回16:42、自動更新有効、16:37の正確なアラーム
- 上書き後: 商品種別 `repeatable_time_code`、`4/24`、168時間、次回16:42、自動更新有効、一時停止操作、16:37の正確なアラーム
- APK: 既存版と署名証明書一致、APK Signature Scheme v2/v3有効、16 KiB alignment正常
- 起動後: FATAL EXCEPTIONなし

## ReVanced Manager互換性

v0.1.0で公開したRVPは通常のGradle `build`だけで生成され、JVM用`.class`は含むがAndroid用`classes.dex`を含んでいなかった。ReVanced CLIでは列挙できた一方、ReVanced Manager 2.6.0では`EmptyMultiDexContainerException`になり、URL追加とローカル追加の両方で読み込めなかった。

v0.1.1ではCIとReleaseを`build :patches:buildAndroid`へ変更した。修正版RVPをAQUOS R8 proのDownloadへ転送し、公開予定ファイルと端末上ファイルのSHA-256一致を確認後、Managerの「Patches」→「ストレージから選択」で追加した。UI階層で次を確認した。

- bundle名: `povo 2.0 automation patches`
- version: `0.1.1`
- パッチ数: 2
- `EmptyMultiDexContainerException`およびbundle load失敗: なし

同じManagerセッションで統合済みpovo 1.70.0-JP APKへ2パッチを適用し、準備・パッチ適用・APK書込・署名がすべて`2/2`で完了した。端末へ導入された`com.kddi.kdla.jp` 1.70.0-JP（857）に`AutomationBootReceiver`が登録されていることも確認した。再ログイン後の自動更新設定は未実施。

remote source経路はManager 2.6.0と同じUser-Agentでraw `patches.json`を取得し、HTTP 200・version `0.1.1`を確認した。続く`download_url`もHTTP 200でAndroid RVPを返し、公開ReleaseのSHA-256と一致した。端末UIでのremote source追加はユーザーが他アプリを操作中だったため実行していない。

## 16:42実終端の自動入力監視

2026-08-31の通常版`com.kddi.kdla.jp`を対象に、現在の4回目終端16:42を監視した。結果は自動入力失敗だった。

- 16:36:35: AQUOS R8 pro接続、正確なアラーム権限`allow`、16:37:00の`AutomationAlarmReceiver`予約を確認
- 16:37:00: receiverから`AutomationService`が許可済みbackground foreground serviceとして起動
- 16:42以降: 通知は「次のプロモコードを適用中」のまま継続
- 16:56: foreground serviceは約19分間継続し、次回終端の`AutomationAlarmReceiver`予約は作成されなかった
- 16:56頃: ユーザーが待機を中止して手動入力したため、それ以降は自動成功判定の対象外
- `FATAL EXCEPTION`、プロモコード本文、token、個人情報のログ出力: 検出なし

実装を再確認すると、`Automation.attempt()`は`promoController`が未解決の場合に2秒後の再試行を予約するだけで、`ensurePromoController()`による再解決を行わない。Activity再開前のbackground起動でcontrollerが未解決だと、serviceは起動していてもプロモコードAPI呼び出しへ進めない。この経路が今回の停止状態と一致する。

ネットワーク側にも失敗要因があった。16:40から16:59までpovoプロセスで毎分`Unable to resolve host`が記録され、少なくとも一部のDNS通信が成立していなかった。端末のdefault networkはWi-Fiとして`VALIDATED`を維持しており、ConnectivityService上のdefault network切断は確認できなかったが、上流がトッピング終了後のpovo回線だった場合、128kbpsへの速度低下やDNS/TLS遅延がAPI適用を妨げた可能性がある。記録された名前解決失敗はpovoプロモコードAPIのhostではないため、ネットワークだけを原因と断定はできない。

ユーザー観測ではDocomo回線経由なら操作が円滑だった。次回はcontroller再解決を修正した上で、Docomo回線またはpovo以外の独立Wi-Fiをdefault networkにして再試験し、アプリ内部要因とpovo回線終端時の通信要因を分離する。

## 128kbps境界対策

v0.1.2では次の対策を追加した。

- serviceの各試行からKoin promo controllerを再解決し、Activityを開いていないbackground起動でもAPI呼び出しへ進む
- 終了12秒前から送信を開始し、終了前拒否を利用してDNS・TLS・API接続を事前確立する
- 終了前後10分はAPI結果受信後3秒で再試行し、その後は60秒へ減速する
- 非同期API応答を最大60秒待ち、watchdog timeoutまでは重複送信しない
- active networkに`INTERNET` capabilityがない場合は5秒待ち、`VALIDATED`状態を秘密情報なしで診断ログへ残す
- foreground service中だけ最大20分のPartial WakeLockを取得し、画面消灯中のCPU suspendを防ぐ
- povo標準画面から保存済みの同一コードを手動適用した場合も、成功回数と次回予約を同期する

再試行ポリシーは境界前開始、境界前後の高速再試行、10分後の減速、2時間後の停止をJVMユニットテストで固定した。実際の128kbps回線での完遂確認は2026-09-07 16:55終端の監視で行う。

v0.1.2 Android RVPをpovo 1.70.0-JP統合APKへ適用し、「プロモコード自動更新」と「検証用別パッケージID」の両方が成功した。最終APKで`ACCESS_NETWORK_STATE`、`WAKE_LOCK`、`SCHEDULE_EXACT_ALARM`、別package ID、APK v2/v3署名、16KiB alignmentを確認した。

通常版`com.kddi.kdla.jp`へReVanced Manager 2.6.0から上書きし、Playプロテクトのスキャン通過、アプリデータ・ログイン状態・暗号化済みコードの保持を確認した。手動適用済み分を反映して`5/24`、168時間、次回2026-09-07 16:55へ補正し、AlarmManagerに2026-09-07 16:50の`RTC_WAKEUP`が`window=0`、`exactAllowReason=permission`で登録された。

この上書き回帰では、更新直後に期限切れの旧時刻を復元したserviceが起動し、新しい将来時刻の保存後もforeground serviceとWakeLockを保持する更新時限定の問題も検出した。サーバー応答はすべて拒否で適用回数は増加していない。v0.1.3では、手動時刻補正時の即時service終了と、5分を超える将来時刻をservice内で待たずAlarmManagerへ戻すガードを追加した。

v0.1.3 Android RVPをReVanced Manager 2.6.0へローカル追加し、通常版1.70.0-JPへ1パッチだけを再適用した。Playプロテクトは「このアプリは安全です」と判定し、同じManager署名による上書き後もログイン状態、暗号化済みコード、`5/24`、168時間、次回2026-09-07 16:55を保持した。更新後とアプリ再起動後の両方で`AutomationService`と`povo-automation:boundary` WakeLockが存在しないこと、および2026-09-07 16:50の正確なアラームが維持されることを確認した。

## 未実施の実機確認

検証用別IDアプリはユーザー操作でアンインストール済みのため、次の項目を次回実終端で確認する。

1. Docomo回線、povo以外の独立Wi-Fi、または128kbpsへ低下したpovo回線で、background起動からAPI呼び出しと成功判定まで進むことを確認する。
2. 端末再起動後とセッション失効後に、コードを失わず復旧することを確認する。

## 障害時の対策

- 正確なアラームが許可されていない場合は通常の idle 対応アラームへフォールバックし、許可を通知する。
- 圏外、429、5xx、終了時刻直前の拒否は短間隔から段階的に再試行する。
- 終了から2時間成功しない場合は無限試行せず停止し、確認を通知する。
- 入力期限経過後は自動更新を無効化する。
- コード復号に失敗した場合は API を呼ばず、再登録を要求する。

## 中間生成物

APKM 展開物、統合 APK、パッチ済み APK、検証用 keystore、CLI、APKEditor は OS の一時領域だけに作成する。リポジトリ内の `povo-2.0-apks` に APKEditor が作る `tmp_*` が残っていないことを検証後に確認する。
# 2026-09-05: CYD用状態中継API

## v0.2.0公開前検証

- 利用者からpushとreleaseの許可を受け、v0.2.0としてクリーンビルド・lint・Android単体16件・中継API10件を再検証して成功した。
- Gradleで解決したコンパイル・実行時・単体テスト用のMaven依存34バージョンをOSV APIへ照会し、該当する既知の脆弱性は0件だった。これは解決済みアプリ／パッチ依存の照会結果であり、Gradleプラグイン全体やPython実行環境の包括監査ではない。中継APIに外部Python依存はない。
- リリースワークフローに `povo-cyd-relay.zip` の作成・公開・provenance対象への追加を行った。実機は引き続きADB接続なし。

## 実装時検証

- `gradlew build :patches:buildAndroid`: 成功。Android単体テスト16件（うちHTTPS送信先・トークン検証3件）、lintエラー0。警告は既存の依存表記、同期的マイグレーション保存、および拡張モジュールのアイコン未指定。拡張はホストpovoのアイコンを使うため独立アプリアイコンを追加しない。
- `python -B -m unittest relay.test_relay -q`: 10件成功。実HTTPの読み書き認証分離、不正入力拒否、保存・再起動・古い送信拒否、残り秒数・鮮度境界、DB退避復旧を確認。HTTPSは一時localhost SAN証明書を明示信頼してPUT/GETし、未信頼証明書の拒否も確認した。テスト用証明書と秘密鍵は削除済み。
- 公式配布のReVanced CLI 6.0.0とAPKEditor 1.4.9を使用し、保存済みAPKM 1.68.0、1.69.0、1.70.0を統合してパッチ適用成功。各APKのmanifestでDisplaySyncJobとBIND_JOB_SERVICEを確認。最終RVPのclasses.dex・extension同梱も確認した。
- 送信処理の監査で、メインスレッドとネットワークの共通ロック、設定変更時に旧トークンと新URLが混在する競合、停止済みjobへの完了通知、再起動後に残る更新中状態を修正した。
- 既存v0.1.0〜v0.1.3の全リリースにpatches.jsonがあることを確認した。今回の公開リリース・pushは行っていない。
- `adb devices`に接続実機がなく、今回の設定画面操作、AndroidからPCへの実通信、Android省電力下の定期送信、Manager上の再適用、CYD画面は未検証。UIとJobServiceの実動作が確認済みとは扱わない。
- 実利用の前に、PCで信頼済みHTTPS証明書と読み書き別トークンを設定し、アプリの「保存して送信」→「送信結果を確認」→CYD用GETを確認する。停止・再開、再起動、PC停止、再ログイン要求、期限経過、最終回成功、コード削除後のstale化も実機で確認する。Androidの定期jobは15分以上かつ省電力で遅延し得るため、5分のCYD取得をAndroid側の同期保証と解釈しない。
