# Galaxy Service Panel

Samsungの電話キーパッドから `*#0011#` で開くService Modeを、読みやすく整えるVectorモジュールです。

## できること

- SIM・接続、LTE、NRを区切り、診断値を見やすく表示。
- 画面幅・文字サイズに合わせて表示を調整。
- 「原文」タブと長押しから元の文字列を確認・コピー。
- 純正メニューと診断値の更新を維持。
- 表示のON/OFF、文字サイズ、ライト／ダークテーマ、日本語・英語。

## 対応環境

Galaxy S26 Ultra（SM-S948Q）、Android 17 / One UI 9の対応するService Modeが対象です。API 102対応Vectorが必要です。Android 16用の互換プロファイルは準備していますが、この版での実機確認は未実施です。未対応の画面では純正表示を維持します。

## 導入・使い方

1. [Releases](https://github.com/OneUI-Mods/galaxy-service-panel/releases)から署名済みrelease APKをインストールします。
2. Vectorでモジュールと「Service mode RIL」を有効にします。
3. 初回・更新後に対象プロセスへモジュールを読み込ませます。Service Modeは電話機能と同じプロセスで動くため、その再読み込みは通話・通信が一時途切れても支障のない時に行ってください。
4. 電話キーパッドで `*#0011#` を入力します。

アプリの「表示」でON/OFFと文字サイズ、「プレビュー」でサンプル、「情報」で状態を確認できます。エラーや保護停止時は純正表示へ戻ります。

## 制限

表示の整理が対象です。モデム設定、周波数、SIM設定、更新間隔は変更しません。診断値の保存・送信は行わず、コピーは利用者が操作した場合だけです。アプリから電話プロセスや端末を自動で再起動することはありません。

## 開発者向け・ライセンス

ビルド、内部構造、検証方法は[開発者向けガイド](docs/development.md)に分けています。不具合報告は[Issues](https://github.com/OneUI-Mods/galaxy-service-panel/issues)へ、端末・OS・対象アプリの版と再現手順を添えてください。

本体のライセンスは[LICENSE](LICENSE)、依存ライブラリの表記はアプリ内または同梱の第三者ライセンスを参照してください。Samsungの公式アプリではありません。

[OneUI Modsのアプリ一覧](https://github.com/OneUI-Mods)
