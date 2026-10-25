# 感情接続と会話の動き（Windowsローカル検証）

2026-10-08。採用するスキンは「10 最小限（体のライン入り）」と旧オーバーオール。ラインなしv5は比較素材としてのみ残す。1.3.0は両方に対応する。

## 感情 → MOD

`integration/minebot/expression-bridge.mjs` は、既存のcompanion worldで作られたMinebotに追加するアダプター。既存のMinecraft bodyのpaired device tokenとloopback URLを使う。認証済み `GET /v1/body/presence?scopeKey=owner` の `mood.expression` だけを22表情へ送る。原因、会話、記憶、補足feelingの文章をゲームのpayloadへ載せない。新しい感情判定やモデル呼び出しは追加しない。

schemaVersion=1、16桁etag、既知expressionを検証する。`pollAfterSeconds`に従い、同じetagの`unchanged:true`でも表示期限を更新する。表示のfreshnessは最大30秒、通常10秒pollでは25秒。Minecraftへの5秒ごとの再送はfreshnessの残りだけを送るので、古い状態が永久に延長されない。通信失敗時は5〜30秒backoffし、期限切れでneutral。401/403はポーリング停止。リダイレクト拒否、5秒timeout、応答64KiB上限。秘密値や応答本文をログへ出さない。切断時はlistener/timerを片付け、遅延応答を世代で破棄する。

## 会話活動 → MOD

APIの `doing` は自律活動であり、目の前のMinecraft会話の状態として読み替えない。現行 `MinebotCompanionBody.answer` の実行イベントを使う。呼びかけ受理でlistening、600ms後から返答待ちのthinking、実際の返信callbackでspeaking、失敗時はidleへ戻す。テキスト返信なのでspeakingは送信後2.2秒の控えめな視覚的な動きで、音声の音量や音素との同期ではない。返信直後に感情を再取得する。並行する返信で古い活動が最新の活動を上書きしない。

相手のUUIDから、現在のMinebotのplayer listにある近くのentityへ視線を向ける。角度だけを送る。座標を外部APIへ送らず、Minebotのlook/pathfinderや移動操作を呼ばない。後ろにいる、8mより遠い、姿がない相手は追跡しない。

新しい `expressionmod:dialogue_sync` はUUID文字列・名前・activity文字列・varlong表示期限・target有無・yaw/pitch float32BE。activityはidle/listening/thinking/speaking。C2Sで申告されたUUID/名前はゲームサーバーの認証済み送信者に置き換える。角度は有限値のみ、±30度、期限は最大5秒。1秒ごとのheartbeatが止まればidleへ戻る。遅れて参加・channel登録した観測クライアントにも残り期限を送る。

外部botはplayの `minecraft:register` でchannelを知らせ、サーバーの `expressionmod:bridge_ready`（1 byte、version=1）を待つ。late attachmentにも応答するので、最初の登録通知を取り逃しても動く。バージョン確認前には表情payloadを送らない。既存emote/eye/full-state channelの形式は変更しない。

## 描画

既に採用した表情の傾き・頬は保つ。聞くときは目を少し開き、考えるときは小さく視線を外し、返すときは目の幅・開きを3〜4%以内で動かす。視線は速く、頭は小さく遅れて追従する。待機中は異なる周期を組み合わせた微小な動き。眠いときは開く瞬きを遅く、落ち着いた表情でも少しゆっくり。聞く・考える間は瞬きを少なめ、返答終了時は一度の瞬き。新しい描画は確認済みシャノンスキンだけ。設定 `enableShannonDialogueMotion`（既定true）で戻せる。回路と目のグラデーションの設定は独立。

## バックエンドに反映する差分

VMを読み取り、稼働 `Shannon-current` の `skillAgent.ts` / `MinebotCompanionBody.ts` / `CompanionBodyClient.ts` とAPI route契約を参照した。ローカル成果物の `alive-motion/backend/shannon-backend.patch` は、前2ファイルへの最小の接続変更とアダプター2ファイルを含む。原本hashは `reference-hashes.json`。`candidate/` は差分の確認用であり、稼働バックエンドではない。

既存のcompanion bodyが有効なworldだけで、追加の `MINEBOT_EXPRESSION_BRIDGE=true` がある場合に作る。既定offのまま、既存のcompanion URL・token file・server認証を使う。配備時には元ファイルの差分を照合する。設定・デプロイ・サービス再起動・本番API呼出し・本番Minecraft接続は今回行っていない。

## 検証の範囲

- Java単体: lease期限、活動割込み、感情の形を保つこと、視線と頭の時間差、フレームレート、ゆっくり開く瞬き。
- Node: loopback HTTP、22表情、unchanged、期限切れ、失敗、401/403、遅延応答、ポーリングの直列化、会話割込み。
- ローカルbackend glue: 実際のcandidateの `MinebotCompanionBody` と取得・返信処理をNodeで変換して実行。モデル・UI・タスクloopをモックし、返信callbackから活動を送り、既定offと失敗fallbackを確認。バックエンド全体の型検査ではない。
- Minecraft実クライアント: Nodeが生成した22種類のwire bytesを実codecとC2S handlerへ通す。送信者の置換、活動受信、残り期限のS2C同期、不正角度の拒否を確認。採用2スキンの表情・回路・会話動作を実描画で撮影。

動くプレビューは8.4秒の決めた会話シナリオ。製品と同じAliveMotion・EmoteState・BlinkStateを時刻指定で使い、実rendererに渡した静止画を順に再生する。本番の会話連動の確認とは区別する。HTTPとゲームsocketを同時に本番へ接続する確認は配備後に必要。
