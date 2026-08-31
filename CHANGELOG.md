# 変更履歴

このプロジェクトの重要な変更はこのファイルに記録します。書式は [Keep a Changelog](https://keepachangelog.com/ja/1.1.0/) に従い、バージョンは Semantic Versioning に準拠します。

## [Unreleased]

## [0.1.0] - 2026-08-31

### Added

- 168時間トッピングの終端で回線カバレッジが途切れる時間を最小化するため、実終了時刻の検出、正確なアラーム、foreground service、成功までの再試行を追加した。
- 途中利用から開始できるように、メール本文から同一プリペイドコードと入力期限を抽出し、サーバー側の残り利用可否を正として繰り返す処理を追加した。
- 認証情報を複製せずログイン済みセッションを利用し、失効時にコードを保留して再ログイン後に復旧する処理を追加した。
- プリペイドコードを端末内で保護するため、Android Keystore と AES-GCM による暗号化保存を追加した。
- Android 16 の AQUOS R8 pro を対象に、再起動復旧、正確なアラーム権限、バックグラウンド実行用コンポーネントを追加した。
- ReVanced Manager が取得できる `patches.json` と Patcher 22 対応 RVP のリリース基盤を追加した。

[Unreleased]: https://github.com/roflsunriz/povo-2.0-revanced/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/roflsunriz/povo-2.0-revanced/releases/tag/v0.1.0
