# ExpressionMod

Minecraft Java **1.21.11** / Fabric 用の表情 Mod です。
シャノンには四角い目の幅・高さ・傾き・グラデーション、視線、頬の赤み、頭の角度を連続描画します。普通のユーザーには従来のスキン内素材によるテクスチャ切り替えを使い、瞬き・視線と5種類の描き込んだ表情を表示します。

## 導入

- Minecraft 1.21.11、Java 21、Fabric Loader 0.18.2 以上
- 対応する Fabric API（検証版: 0.141.6+1.21.11）
- 設定画面を使う場合: Mod Menu 17 と Cloth Config 21.11（検証版: 17.0.0-beta.2 / 21.11.151）

`build/libs/expressionmod-1.3.1.jar` を観察者の Minecraft の `mods/` に入れてください。マルチプレイの表情・会話活動同期には、サーバーにも ExpressionMod と Fabric API が必要です。1.3.1 はクライアント描画の修正で、1.3.0 サーバーとの通信形式は同じです。Mod 未導入の観察者には通常のスキンが見えます。

普通のユーザーのスキンは [SKIN_FORMAT.md](SKIN_FORMAT.md) のシアンマーカー形式で準備します。VM版の左上マーカーと、旧版の右端マーカーの両方に対応します。現在のシャノンスキンには、既定無効の明示的な適用設定があります（[手順](docs/shannon-skin-preview.md)）。それ以外のマーカーなしスキンには適用しません。以前の **1.21.4 用 JAR は対象外**です。

## 操作

| キー | 表情・操作 |
| --- | --- |
| テンキー1 | 笑顔 |
| テンキー2 | 怒り |
| テンキー3 | 悲しみ |
| テンキー4 | 驚き |
| テンキー5 | 考え中 |
| テンキー6 | 照れ |
| テンキー7 | 通常顔に戻す |
| テンキー0 | アニメーションをリセット |
| Ctrl + 右クリック | 普通のスキンに描き込んだ5種類の表情を選択 |

シャノンの表情は450msの ease-out で滑らかに移り、切替途中の変更は現在の形から始まります。普通のスキンはテンキー1〜5でも表情スロット1〜5を選べます。対応スロットのない表情では普段の瞬き・視線に戻ります。既定の持続時間は3秒。同じ表情の更新は持続時間を延長します。自動まばたきは既定3〜6秒間隔、閉じる55ms・閉じた状態20ms・開く110msです。

設定は `config/expressionmod.json` に保存されます。視線・頭の動き、まばたき、微小な目の動き等を切り替えられます。Mod Menu の設定画面には Cloth Config も必要です。

## Shannon との接続

この変更は **ExpressionMod 側**の描画・同期です。Shannon の Minebot が shannon-ios の感情を取得して送信する処理は別途必要です。

Minebot は自身の Minecraft 接続から `expressionmod:emote_sync` custom payload を送れます。サーバーは接続元本人の UUID・名前を使用し、Mod 対応の観察者へ配信します。Minebot プロセスに Java Mod をロードする必要はありません。

| 順序 | wire 型 | 内容 |
| --- | --- | --- |
| 1 | Minecraft String | UUID のハイフン付き文字列表記 |
| 2 | Minecraft String | プレイヤー名 |
| 3 | Minecraft String | 22表情の ID（下記） |
| 4 | VarLong | 持続時間（ms） |

Minecraft String は UTF-8 の**バイト数**を VarInt で前置します。JSON や UUID の16バイト直書きにはしません。持続時間は最大30秒、0以下は既定3秒。`neutral` は持続時間にかかわらず表情を解除します。継続して表示する間は期限前に更新してください。

表情の理由・会話本文はこの payload に含めません。`emote_sync` の wire 形式は既存と同じです。初期同期の `full_state_sync` は1.1で視線指示の有無を表す boolean を末尾に追加しているため、サーバー・観察者は同じ Mod 版を使用します。

## ビルド・検証

```bash
./gradlew build
./gradlew runClientGameTest
```

Java 21 とネット接続が必要です。通常の build は37件の JUnit テストを実行します。クライアント GameTest は別のテスト Mod・使い捨てシングルプレイワールドを使い、実描画、スキン画像の取り込み、本人の状態だけの更新、期限の上限、初期同期、同じスキンを使う二人を確認します。シャノンと Rai1241 の実スキンをテスト用素材として使い、原本のPNGやアカウントのスキンは変更しません。Minecraft アカウントへのログインや本番ワールドは使いません。

GameTest には OpenGL を利用できる画面が必要です。Linux の仮想画面では次のように実行できます。

```bash
xvfb-run -a env LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null ./gradlew runClientGameTest
```

スクリーンショットは `build/run/clientGameTest/screenshots/`、単体テスト結果は `build/reports/tests/test/` に出ます。テスト Mod と合成スキンは製品 JAR に入りません。

設計・参考実装・検証の範囲は [docs/continuous-rendering.md](docs/continuous-rendering.md) を参照してください。

シャノンの現在のロボットスキンを使うローカルプレビューは [docs/shannon-skin-preview.md](docs/shannon-skin-preview.md) を参照してください。原本の PNG を保持し、製品版の明示的なスキン対応経路を検証します。

左右のラインに淡い波を流す試作は [docs/circuit-preview.md](docs/circuit-preview.md) を参照してください。設定 `enableShannonCircuitFlow` は既定でオフです。

感情とアプリの目の動きを参照した範囲、VM 接続の現状、笑顔の修正については [docs/shannon-emotion-reference.md](docs/shannon-emotion-reference.md) を参照してください。

## 現行の22表情

`GET /v1/body/presence` の `mood.expression` の22値を `emote_sync` で受け取れます。旧 ID も維持します。

| 現行 API の値 | MOD 内の ID |
| --- | --- |
| joyful | smile（joyful も受信可） |
| annoyed | angry（annoyed も受信可） |
| thinking | think（thinking も受信可） |
| embarrassed | shy（embarrassed も受信可） |
| excited / amused / laughing / bashful / relieved / surprised / curious / confused / determined / tender / calm / jealous / wry / exasperated / concerned / sad / sleepy / neutral | 同名 |

表情と会話活動の描画には観察者も1.3.0が必要です。テンキーの従来7操作は維持します。感情を取得してパケットを更新送信する `integration/minebot/expression-bridge.mjs` を追加しました。既存のMinecraft bodyの感情と会話イベントを使います。本番へは未反映。導入する差分・設定・期限・検証範囲は [shannon-alive-motion.md](docs/shannon-alive-motion.md)。Nodeの検証は `node --test integration/minebot/expression-bridge.test.mjs`。ローカルbackend candidateがある場合、作業フォルダから `node --test ExpressionMod/integration/minebot/backend-glue.test.mjs` で実接続部分のモック検証も実行できます。
