# 調査知見

- 保存済み入力はAPKM bundle。単一APKへ統合してからpatchし、native ABI、split必須metadata除去、v2/v3署名、16KiB alignmentを確認する。
- RVPは `buildAndroid` でAndroid `classes.dex` を同梱する。通常 `build` だけのRVPを配布しない。
- 新版fingerprintは難読化名でなく、`promoCode` + String引数、PromoCodeModel結果、addon parserのfield、Application/Koinの安定文字列で複数世代確認する。
- promo mail parserは商品名列挙より、code種別、紐付く回数、即時適用回数、有効時間、入力期限を抽出する。24回/5回/購入直後+残回数/単発の境界をtestする。
- 保存schema変更ではcode、適用回数、有効時間、入力期限、現在終端、active状態を明示migrationし、background・screen off・低速network・境界前拒否・timeout・成功停止を確認する。
- 公式版併存用package変更ではprovider authority、独自permission/action、processも別IDへ変え、login redirect scheme/hostは維持する。本番releaseではこのpatchを選ばない。
- 16KiB alignmentは旧 `META-INF` 署名entryを除去→`zipalign -P 16`→署名の順で行い、最終署名後に再検証する。署名entry除去はnative offsetを変える。
- Manager sourceはrelease page URLでなくraw `patches.json` を登録し、JSONのdownload URLとRVP versionを一致させる。
