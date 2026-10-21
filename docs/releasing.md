# 署名とリリース

JDK 21 / Android SDK 37で `python3 tools/package_release.py --source-ref HEAD` を実行します。アプリ、ツール、ドキュメントを先にコミットします。release variant、target SDK 37、非debuggable、検証Activity非混入、modern API 102 metadata、対象スコープ、依存ライセンス、16 KiB alignment、配布証明書を検証します。JVM単体試験にdebug variantのホスト用クラスを使用しますが、debug APKは配布しません。

秘密情報はGit対象外の `.signing/` のみに保存します。ディレクトリ0700、`release.keystore` と `password` は0600。aliasは `galaxyservicepanel-release`。鍵・パスワードをGitや配布物、ログへ出しません。以後も同じ鍵を使って上書き更新します。

配布証明書SHA-256：`4cda6775c402ba4cbc2591ffb3490b825d53a0aa7fafd98d67e85b337e8df0b0`。

`dist/` に署名済みAPK、対応するソースZIP、SHA256SUMS、署名検証結果を作ります。原本APK・逆コンパイル結果・画面画像・端末ログ・接続先IPはGit対象外の `artifacts/` に保存し、ソースZIPへ含めません。

リリース文は外部利用者へ機能・対応環境・導入操作・制約を書きます。内部クラス名や個人の値、試験経緯は開発用記録に分けます。既存タグは移動せず、新しい版として公開します。
