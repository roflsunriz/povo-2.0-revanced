# セキュリティポリシー

## 対応バージョン

最新 release と main を対象にセキュリティ修正を行います。

## 報告方法

GitHub の Private vulnerability reporting から報告してください。公開 issue へ次を記載しないでください。

- プリペイド・プロモコード
- povo の token、Cookie、session key
- メール本文、電話番号、契約情報
- 署名前 keystore や秘密鍵

再現に必要な場合も値はマスクし、種類、発生箇所、影響、再現条件だけを先に共有してください。

## セキュリティ設計

- パッチは povo のログイン情報を独自保存せず、アプリのログイン済み API client を再利用する。
- プリペイドコードは Android Keystore の非エクスポート AES 鍵で暗号化する。
- Activity、Service、Receiver は外部公開しない。
- 期限切れ、認証失効、永続失敗では無限再試行しない。
- ログへコードや token を出力しない。
