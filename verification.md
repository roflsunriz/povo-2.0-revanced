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
| PromoCodeExtractor・商品モデル・結果対応付けユニットテスト（9件） | 成功 |
| RVP を ReVanced CLI 6.0.0 で列挙 | 成功 |
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

## 継続中の実機確認

検証用IDへのログイン後、次の項目を継続確認する。

1. 現在の4回目終端（2026-08-31 16:42 JST）で、終了前 foreground service、拒否時再試行、5回目の適用成功、`5/24`、次回予約を時系列で確認する。
2. 端末再起動後とセッション失効後に、コードを失わず復旧することを確認する。
3. `adb logcat` にコード本文、token、個人情報が出力されないことを終端処理後にも確認する。

## 障害時の対策

- 正確なアラームが許可されていない場合は通常の idle 対応アラームへフォールバックし、許可を通知する。
- 圏外、429、5xx、終了時刻直前の拒否は短間隔から段階的に再試行する。
- 終了から2時間成功しない場合は無限試行せず停止し、確認を通知する。
- 入力期限経過後は自動更新を無効化する。
- コード復号に失敗した場合は API を呼ばず、再登録を要求する。

## 中間生成物

APKM 展開物、統合 APK、パッチ済み APK、検証用 keystore、CLI、APKEditor は OS の一時領域だけに作成する。リポジトリ内の `povo-2.0-apks` に APKEditor が作る `tmp_*` が残っていないことを検証後に確認する。
