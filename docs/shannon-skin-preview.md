# 現在のシャノンスキンの描画とプレビュー

## 採用するスキン（2026-10-08）

ユーザーの指定で、次の2種類を採用する。

- 体のライン入り「10 最小限」: `shannon-D-lines-10-minimal.png`。プレビューは `minimal-lines-10/`。
- 旧オーバーオール版: `d7cde7740671d6aa.png`。プレビューはルートの `index.html` / `circuit.html`。胴体と脇のTシャツには電流を流さない。

ラインなしの `shannon-D-soft-robot-v5.png` は採用候補から外し、既存のプレビューと検証素材は過去の比較用に保持する。体のライン入り版が同じ顔素材を使うため、共通の描画処理は引き続き必要。元のスキン画像は変更しない。この採用決定はVMへのデプロイやMinecraftアカウントのスキン変更を伴わない。

1.3.0では既存の `mood.expression` とMODの接続アダプター、会話活動（聞く・考える・話す）の受け取り、視線と瞬きの活動別調整をローカル実装した。本番へは未反映。見た目だけで会話活動を推測せず、実際のMinebotの会話処理イベントを使う。契約と検証範囲は [shannon-alive-motion.md](shannon-alive-motion.md)。

2026-10-08。ZIP の `preview/shannon-current.png` は2026-10-07に取得された64×64 classic スキン。アカウント `I_am_Shannon`、UUID `accf3442-b95f-414b-95fd-f70bbd2c9764`。今回新たに公開プロフィールを取得していない。

## 製品版での適用（1.3.2 から設定なし）

2026-10-26、マルチで他の人からシャノンの表情が見えない不具合を直した。原因は、1.3.0〜1.3.1 ではこの対応が既定で無効（`enableShannonSkinProfile: false`）で、設定を変えた人の画面にしか表情が出なかったこと。1.3.2 では設定をなくし、シャノンの UUID `accf3442-b95f-414b-95fd-f70bbd2c9764` には観察者全員の画面で適用する。古い設定ファイルの `enableShannonSkinProfile` は読まれない（あっても害はない）。`shannonSkinPlayers` に書いた UUID は、シャノンに加えて同じ顔のスキンを使う人として扱う。

対象 UUID と確認済みの顔8×8ピクセルの SHA-256 が両方一致したときだけ適用する。スキンが変わって指紋が違う場合は適用しない。一般的な顔の自動検出ではない。cyan marker のスキンは従来通り対応する。

`ShannonSkinProfile` が元の目・まぶた・背景色から CPU 素材を作る。vanilla が GPU にアップロードする画像は変更せず、描画時に図形を重ねる。

両頬には通常時から淡いピンクの横長長方形を描く。8×8の顔座標で幅2.4、高さ0.55、上端6.2。左右の外端は0.35と7.65で、黒い画面の横端（1と7）から白い縁へ少し広がる。照れ・はにかみ等では `FacePose.blush` に従って濃くなり、表情と同じ450msの補間で変化する。マーカースキンにはこの常時表示を適用しない。

## Windows で再現する

Java 21 がある状態でリポジトリ直下から PowerShell で実行する。

```powershell
.\gradlew.bat test build --no-daemon --console=plain
$env:EXPRESSIONMOD_PREVIEW_SKIN = '原本PNGの絶対パス'
.\gradlew.bat runClientGameTest --no-daemon --console=plain
```

GameTest はオフラインのプレイヤーでローカルのテストワールドを生成し、通常描画と22表情の実クライアント画像を撮る。テスト Mod はスキンと UUID を供給するが、素材は製品版の capture / profile 経路を使う。テスト内の擬似 marker による適応は除去した。

成果物は `build/libs/expressionmod-1.3.0.jar`。画像は `build/run/clientGameTest/screenshots/`。製品 JAR に test mod / fixture / preview mixin は含まれない。本番 shannon-home、他の描画 MOD との互換性、感情送信 bridge の本番接続は別途検証が必要。
