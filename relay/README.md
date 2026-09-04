# CYD 用 povo 状態中継 API

既存 PC で動かす、1 台の Android と CYD 向けの中継サーバーです。Android が状態を PUT し、CYD は GET で取得します。Python 標準ライブラリだけを使用します。プロモコード、メール本文、povo 認証情報は受け付けません。

## 起動

Python 3.10 以降で、リポジトリ直下から実行します。PowerShell で読み取り・書き込みを別々の乱数トークンにします。下記は値を画面に表示しません。

```powershell
$env:POVO_WRITE_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
$env:POVO_READ_TOKEN = python -c "import secrets; print(secrets.token_urlsafe(32))"
python -m relay.server --database relay/povo-status.sqlite3
```

トークンは英数字・`_`・`-` の 32〜256 文字で、両者は別の値が必要です。Android には書き込み用、CYD には読み取り用だけを設定します。値をコミット、ログ、画面写真へ残さないでください。再起動時も同じ値を安全なローカル保管先から環境変数に設定します。上記の生成を再実行すると旧端末の認証は無効になります。

既定は `127.0.0.1:8787`。Ctrl+C で終了します。DB は直近の状態 1 件を保存し、再起動後も返します。PC のスリープ中・停止中は取得できません。

## Android から接続する HTTPS

Android 送信 URL は HTTPS 専用です。中継の HTTP はローカルテストか HTTPS リバースプロキシの後段に使用します。外部から利用する場合、端末が信頼する証明書を持つ HTTPS リバースプロキシから `http://127.0.0.1:8787` へ転送し、パスと Authorization ヘッダーを維持します。プロキシにも本文上限 4096 bytes、接続・読み取りタイムアウト、接続数制限を設定し、Authorization と本文をログへ出さないでください。

サーバー自体でも PEM 証明書と秘密鍵を指定して TLS を有効にできます。

```powershell
python -m relay.server --host 0.0.0.0 --port 8787 --database relay/povo-status.sqlite3 --tls-cert C:/certs/fullchain.pem --tls-key C:/certs/private.key
```

証明書には設定するホスト名に対応する SAN と中間証明書が必要です。Android のアプリと CYD が信頼する CA の証明書を使用します。自己署名証明書や Android のユーザー追加 CA が、そのままホストアプリで信頼されるとは限りません。証明書検証を無効化せず、信頼済み証明書へ置き換えてください。秘密鍵の閲覧権限は実行ユーザーに限定します。Windows ファイアウォールは必要な LAN 機器だけに接続を許可し、ルーターのポート開放は行わず利用できます。

## API v1

両メソッドのパスは `/api/v1/status`。URL クエリーや URL 内トークンは使いません。

- PUT: `Authorization: Bearer <POVO_WRITE_TOKEN>`、`Content-Type: application/json`、Content-Length 必須。最大 4096 bytes。転送エンコーディングは非対応。
- GET: `Authorization: Bearer <POVO_READ_TOKEN>`。成功時 200。すべての応答は JSON と `Cache-Control: no-store`。

PUT は次の全フィールドを必要とし、未知フィールド・重複キー・型違いを拒否します。日時は UTC Unix epoch のミリ秒で、正の整数（最大 `253402300799999`）。不明日時には `null` を使い、`0` を送信しません。

| フィールド | 値 |
|---|---|
| schema_version | 整数 `1` |
| observed_at_ms | Android が状態スナップショットを採取した日時。必須。PC より 5 分を超える未来は拒否 |
| expiry_at_ms | 現在のトッピング終端、または null |
| expiry_source | `unknown` / `manual` / `estimated` / `server` |
| expiry_observed_at_ms | 終端情報を観測した日時、または null |
| code_deadline_at_ms | コード入力期限、または null |
| automatic_renewal | boolean |
| applied_uses | 端末内で記録した成功回数。0 以上、max_uses 以下 |
| max_uses | 総利用回数。0〜2147483647 |
| renewal_state | `unknown` / `idle` / `applying` / `retrying` / `auth_required` / `needs_review` / `completed` / `code_expired` |
| last_applied_at_ms | 最終適用日時、または null |

PUT 成功は `{"accepted":true,"received_at_ms":...}`。保存済みと同じか古い observed_at_ms は 409 `outdated_snapshot` です。時刻を自動設定し、Android 側では新しいスナップショットを再採取します。受信時刻と観測時刻は意味が異なります。

GET は保存した全フィールドに次を追加します。

| フィールド | 意味 |
|---|---|
| received_at_ms | PC が最後に受け付けた日時 |
| server_time_ms | この GET を処理した PC の日時 |
| stale | 受信から 15 分以上なら true |
| remaining_seconds | max(0, floor((expiry_at_ms − server_time_ms) / 1000))。不明なら null |
| renewal_confirmation_pending | 終端経過済み、または推定終端なら true |

`stale` は中継への受信鮮度です。povo の公式サーバーから最新終端を取得できた保証ではありません。`expiry_source` と `expiry_observed_at_ms` も確認してください。推定値や期限経過時は「更新確認中」を表示し、新たな購入・適用成功を CYD 側で推測しません。

主な失敗: 400 不正 JSON/値、401 認証失敗、404 パス違い、408 本文タイムアウト、409 古い送信、413 サイズ超過、415 Content-Type 違い、503 `status_unavailable`（未受信）または `storage_unavailable`（DB 不調）。エラー本文は `{"error":"識別子"}`。PUT の失敗時は既存状態を保持します。

## CYD の表示

GET を 5 分ごとに実行し、受信した server_time_ms と端末の単調増加時計の差分で現在時刻を進め、終端からの残り時間を毎分計算します。端末の壁時計がずれていても、取得時刻からの経過時間で表示できます。タイムアウト・503・不正 JSON 時は最後の値を保持しつつ通信失敗を表示し、最後の成功取得から 15 分経過したら古い情報として扱います。`null` は「不明」、0 は期限経過です。

## 検証と運用

```powershell
python -B -m unittest relay.test_relay -v
```

実際の HTTP ソケットを使用し、認証分離、正常送受信、再起動保持、遅延送信拒否、不正型・未知キー・本文制限、stale 境界、終端経過を検証します。テストサーバーと一時 DB は終了時に片付けます。

この実装は個人 PC の低頻度通信向けです。同時接続上限 16、ソケットタイムアウト 10 秒、DB 待機上限 5 秒。複数アカウントや不特定多数からの直接アクセス向けではありません。インターネットへ公開する場合はレート制限・TLS 終端を備えたリバースプロキシを必須とし、大量通信が必要になれば専用サービスへ移行します。

DB のスキーマバージョンと保存状態を起動時に検証します。破損・非対応バージョンは起動を止めます。復旧は全中継プロセスを停止して、同じ起動コマンドに `--reset-database` を一度だけ追加します。元の DB は `.backup-ランダム識別子` へ退避され、新しい DB を作成します。このオプションは通常の起動には残さないでください。Android の次回送信で再構築されるまで GET は 503 です。元の DB は確認が済むまで保持します。更新時も停止して DB をバックアップし、同じ DB パスとトークンで新しいコードを起動します。起動できなければ旧コードとバックアップ DB へ戻します。
