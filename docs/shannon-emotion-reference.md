# 現行 VM の感情設計と ExpressionMod

2026-10-08、日本時間。Windows Desktop から既存の OpenSSH 設定で `azure-constant-server` に読み取り接続した。ホスト名は `vm-constant`。Tailscale に対応する環境変数と CLI は Windows 実行環境では確認できない。Cloud Onboarding: Setup と managed environment のツールもこのチャットには公開されていないため、Cloud 設定に保存したキーの有効性は未検証。秘密情報の値は表示・記録していない。

## 読み取った稼働コード

- `shannon-ios.service` の WorkingDirectory とプロセスの cwd は `/home/azureuser/shannon-ios`。
- `DEPLOYED_REVISION`: `ecfd0e9db381859fbc7da12c137fca9dbf65f48d`。起動日時2026-10-08 00:55:55 JST。この配置には `.git` がないため Git HEAD としては確認していない。
- `packages/cognition/src/expression.ts`: SHA-256 `d15bcc8bb53939ae3645cb813976acc186f21180f9666bbd02d87a398b0083ea`。
- `apps/ios/Sources/ShannonAppCore/ShannonEyeRig.swift`: SHA-256 `31742e890f22a4979dc4696277a508fd160fb0d09d2f5997ce90d765e3d8dc89`。
- `services/api/src/surfaces/body/shannonBody.ts`: SHA-256 `c2c8da65cce7e9339c0018e3b6e35e1fa96bfb0bc4fc5a7b9fe5c6158ee39166`。

VM のファイル編集、デプロイ、サービス再起動、DB の操作、感情の変更は行っていない。VM の `/home/azureuser/ExpressionMod` は `74814abc` で別の未コミット変更があるため復元先には使っていない。

## 一つの感情源

`presentShannonFeeling` が反応・気分・長く残る気持ちを同じ状態から読む。`deriveShannonFace` は見える非 neutral の気持ちがあればその表情、なければ valence / arousal / openness から気分の顔を導く。低い気分の表示は concerned。74の感情を MOD 側で再分類しない。

表示は22種類: joyful、excited、amused、laughing、bashful、embarrassed、relieved、surprised、curious、thinking、confused、determined、tender、calm、jealous、wry、exasperated、annoyed、concerned、sad、sleepy、neutral。proud / flustered / apologetic は既存の顔と補足 feeling を使う。

取得契約は認証済み `GET /v1/body/presence` の **`mood.expression`**。応答 root の `expression` ではない。原因の文章は返さない。既定 `pollAfterSeconds` は10だが応答値に従う。etag が同じ場合の `unchanged: true` にも更新期限を返す。bridge は前の表情を保持して表示期限を更新する必要がある。

現行 `docs/minecraft-body-contract.md` には専用 paired device と Minecraft 用の認証・会話・依頼契約がある。今回は API と Minebot を変更せず MOD の受け取りと描画だけを拡張した。認証 API への live presence 呼び出しや Minecraft 本番での感情連動は未検証。

## 感情が続く時間

`emotionTime.ts` と `mind/emotion/emotionTiming.ts` を読み、systemd が環境ファイルを渡すことを確認した。稼働 API の初期環境で、コード指定の10個の `SHANNON_EMOTION_*` 上書き項目はすべて未設定。ソースの既定は起きている時間を使い、反応2分、短い気持ち5分、気分90分、気分値2時間、arousal 30分、長く残る気持ち8時間の半減期。無活動区間の加算上限12分、実時間の上限4日、和らいだ気持ちの保持30分。

MOD の最大30秒の期限は通信上の表示期限であり気持ちの寿命とは別。通信が途切れた表示だけが neutral へ戻る。

## アプリの目の動き

ユーザーの2026-10-08の指定により、動く目の参考は VM の `ShannonWaterlightEyeView` / `ShannonEyeRig` / `ShannonWaterlightCanvas` / `ShannonWaterlightVisualTokens`。静止アバターの L06 とは別の、目の幅・高さ・傾き・グラデーションを動かす描画を使う。

Waterlight は450msの ease-out-quint 遷移。割込みはその時点の見える形から始まる。左右別に幅・高さ・傾き・開き・視線があり、amused / confused / jealous / wry は左右が異なる。目の3色は `#C4FBFF → #76E7FF → #6397FF`。`ShannonEyeRig+Activity.swift` は聞く・考える・発話などの動きを重ねる。

MOD 1.2.0 では **笑顔の ∩ / ^ の曲線を削除**。22表情の幅・高さ・左右別の傾きをアプリの rig の比率からスキンの2ピクセル領域へ変換し、常に直線4辺の四角形として描く。回転しても輪郭を切り落とさず、四角形全体を領域に収める。瞬きは縦方向を縮めて完全に閉じる。アプリは角丸だが、MOD はユーザー指定の四角形を使う。

追加の見た目レビューで、笑顔・悲しみ・大笑い・はにかみ・ほっとした顔・穏やか・心配の7表情をMinecraft用に調整した。目の高さを残し、傾きを弱めた案で、アプリの数値の完全な複製ではない。笑顔は中程度に開いた広い目、大笑いはさらに横長、はにかみは軽い左右差と頬、ほっとした顔は水平で半開き、穏やかは通常に近い目、悲しみは短い目と下向きの視線、心配は左右で開きの違う目にした。感情IDとVMの判定は変更していない。

シャノンの確認済みロボットスキンには app-owned の3色を頂点色の連続グラデーションとして描き、発光ディスプレイのように照度を保つ。marker スキンは従来の素材色と方向素材の連続ブレンドを保持する。両方ともフレーム画像の差し替えは行わない。表情の遷移は450msの ease-out-quint、割込みは現在の幅・高さ・角度から。微小な視線移動と小さな呼吸のスケールを重ねる。

1.3.0でMinecraft会話の実行イベントから活動を送るアダプターと、活動別の視線・瞬きをローカル実装した（[shannon-alive-motion.md](shannon-alive-motion.md)）。アプリの瞬きプロファイルの完全な移植、音声同期、補足 feeling ごとの描画は未実装。感情そのものは VM の設計を変更していない。本番接続は未検証。
