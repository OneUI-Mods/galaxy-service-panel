# 設計と検証

## 対象の実機調査

Android 17 / One UI 9のModemServiceModeで、ServiceModeAppは `com.android.phone` プロセスのActivityです。`keyString=0011` を受け、ListViewとString型のArrayAdapterへ最大102行を流し込みます。行タップは元配列の添字で処理し、純正メニューはタイトルで分岐します。モデムへの送信・Messenger・Handlerは変更しません。抽出APKと画面は `artifacts/research/` のみです。

## 実装の境界

- API 102のonPackageReadyを対象パッケージで判定します。共有電話プロセスの最初のパッケージとは限らないため、isFirstPackageだけでは絞りません。
- 対象ActivityのonCreate/onResume/onDestroyだけを登録します。Activity全体や電話・モデムメソッドをフックしません。
- Android 16/17のプロファイルは別に保持し、ListViewとArrayAdapterの構造が一致する場合だけ整形します。0011以外は触りません。
- 純正アダプタと行リスナーを保持し、DataSetObserverで表示を追従します。clear/addAll/notifyを次のメインループへ集約し、空の中間通知で画面を消しません。定期ポーリングなし。
- 空行を除いた表示にも元の行番号と原文を保存します。選択直前に純正の現在の行と一致を確認し、純正ListViewへ元番号を渡します。行が変化した場合は押下を実行せず再表示します。
- 複数値の分割は認識済みラベルに限定し、未知の形式は単一値または原文へフォールバックします。値や単位を推測・変換しません。
- 構造化表示と原文表示は同じデータを使います。診断値をディスク、Provider、ログへ書きません。プレビューは架空のサンプルです。
- ModuleUiContextはモジュールのResources/ClassLoaderとホストActivityのwindow tokenを組み合わせ、ホスト側AppCompatとの資源混在を避けます。
- 設定Providerは表示設定のみ公開し、状態更新は自アプリと実際のSamsung電話UIDだけを許可します。設定IPCは専用worker、ビュー操作はメインスレッドです。
- OFFは追加ビューとObserverを外し、純正の子ビュー・LayoutParams・ActionBarを復元します。監視用のActivityフックは再ON用に残ります。
- 整形例外は電話プロセスへ伝播させず次のメインループで復元し、設定側の停止をラッチします。終了履歴の保護は、整形が付いていた区間の対応するPID・プロセス名・時刻・確認済みクラッシュ理由だけを使います。手動kill、正常終了、未確認理由、履歴重複は数えません。2分内3回で停止し、明示解除nonceで再開します。

## 検証

JVM試験：空行と選択番号の対応、非メニューの括弧、原文と順序の保持、複数メトリクス、欠損値、未知形式・長文、概要の抽出、クラッシュ時間窓・履歴重複・不明/手動終了・時刻逆行。

実機での整形表示・OFF復元・ライブ更新・電話プロセスへの初回読み込みは、この時点では未確認です。署名済みrelease版を作成し、アプリのUI検証、最後にフックの検証を行います。電話、SystemUI、端末の自動再起動処理はありません。

## ソースの書式

Javaはgoogle-java-format、PythonはBlackの100桁を使用します。対象OSごとの記録と表示ロジック、認可・クラッシュ判定を分け、逆コンパイルしたコードは製品へ含めません。
