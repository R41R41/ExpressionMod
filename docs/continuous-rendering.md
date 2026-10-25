# 連続する目・表情の描画

## 参考にした仕組み

調査対象は次の2つです。ユーザーが記憶している Mod の名称は未特定ですが、部品の変換を補間する方式が今回の目的に適合します。

- [Customizable Player Models](https://github.com/tom5454/CustomPlayerModels): MIT。調査 commit `1e94ed23f7d1d9b253d285c98acc3427bd71bdd0`。`CustomPlayerModels/src/shared/java/com/tom/cpm/shared/animation/interpolator/TrigonometricInterpolator.java` はキー間の補間を行い、1.21.11 の renderer integration はプレイヤーごとの render state を使います。
- [Figura](https://github.com/FiguraMC/Figura): 調査版の LICENSE.md は PolyForm Noncommercial 1.0.0。調査 commit `0940c220f520d494318b4a4be33202e806ac2593`。`common/src/main/java/org/figuramc/figura/animation/AnimationPlayer.java` はキー間の位置・回転・スケールを補間してモデル部品へ適用します。

ExpressionMod のコードとテスト素材は独立した実装です。CPM・Figura は実行時の依存に追加していません。

## 描画経路

1.3.1では以下の連続描画を確認済みシャノンのロボット顔に限定します。普通のスキンは `LegacySkinTextures` が vanilla のアップロード前に素材を保存し、VM版の左上マーカーと旧右端マーカーを読みます。通常・半目・閉じ目・見開きと左右視線、および5種類の8×5表情を遅延生成してスキンテクスチャを切り替えます。フレームはスキンごとに共有し、選択状態はプレイヤーのUUIDごとです。最大64スキンのGPUフレームを保持し、退避・切断・リセット時に描画スレッドで破棄します。マント、エリトラ、腕のモデルと外側レイヤーを保持します。

1. `PlayerSkinDownloadMixin` が vanilla の GPU アップロード前にスキンのピクセルを読み、`FaceSkinData` に CPU データとして保持します。スキンの URL への追加アクセスはありません。キャッシュは最大256テクスチャで、GPU画像のコピーやプレイヤーごとの GPU テクスチャは作りません。
2. `PlayerEntityRendererMixin` が `PlayerEntityRenderState` に、そのプレイヤーの UUID・素材・顔パラメータ・視線・まばたきの immutable snapshot を付けます。
3. `PlayerEntityModelMixin` がモデルの頭へ、連続的な表情の角度補正を加えます。1.21.11 の hat は head の子なので、頭の変換を二重に適用しません。
4. `ContinuousFaceFeatureRenderer` が head の変換に追従した面を Minecraft の deferred render queue へ渡します。背景色と四角い目を独立した図形として描き、頂点色を補間します。1枚の静的な白い texel を着色して使います。
5. 描画 callback はそのフレームの頂点データと保存された行列を使います。共有される現在のプレイヤーや、スキンから UUID を逆引きする状態は持ちません。

透明なスキンや帽子の扱い、Sodium・Iris・3D Skin Layers 等との組合せは実際の素材と導入 Mod で追加確認してください。外側レイヤーの目の前に不透明な素材がある場合は、保持された外側レイヤーが目の図形を覆います。

## 時間と同期

`EmoteState` と `BlinkState` は単調時計を使い、tick の回数ではなく描画時の時刻から状態をサンプリングします。表情は450msの ease-out-quint 補間、まばたきは閉じる・保持・開く各区間を解析的に求めます。

表情を途中で切り替えると、その時点の顔を新しい開始点にします。同じ表情の更新は期限だけを延ばします。期限切れは実際の deadline を開始点として neutral へ戻すため、見えていなかったプレイヤーにも古い顔が残りません。クライアント側でもリモートの状態を tick します。

シングルプレイの integrated server と client には別々の状態 manager を用います。C2S の主張する UUID と名前は信頼せず、接続元の本人から作り直します。配信と初期同期は対象 channel を受け取れる観察者に限定し、JOIN 後の channel 登録にも対応します。

`emote_sync` と `eye_sync` の形式は維持します。`full_state_sync` は末尾の `hasEyeTarget` により、初期同期で「明示的に中央を見る」と「自然な視線を使う」を区別します。この Mod の1.2同士で使用する契約です。

## 検証の範囲

Java 21、Gradle 9.2.0、Minecraft 1.21.11、Fabric Loader 0.18.2、Fabric API 0.141.6+1.21.11 で検証します。

- `build`: 表情の割込み・更新・期限切れ・非表示中の経過、まばたきの連続性、目の全閉と境界、旧版とVM版のマーカー形式、回路の波と目のグラデーション、会話活動の期限・視線と頭の時間差等の37件の JUnit テスト。
- `runClientGameTest`: 実際の Minecraft クライアントとシングルプレイのサーバー、合成スキンで実描画を確認。スキン upload hook、UUID の詐称を受け入れないこと、持続時間の上限、初期同期の表情と自然な視線、同じスキンでも別々の顔を表示することを確認します。笑顔・怒りの画像が neutral と異なることも検査します。
- `LegacyFaceGameTest`: Rai1241 の原本をテスト用素材として読み、製品側のスキンアップロードと描画を使います。5表情の同期と各ピクセル、左右視線、自動瞬き、盲目での閉じ目、同じスキンを使う二人の独立した表情、細い腕モデルの保持を確認します。`EXPRESSIONMOD_LEGACY_TEST=true` でこのテストだけを実行できます。既定の連続描画テストは採用済みの最小限ライン版を使います。

追加で、公開プロフィールから取得したシャノン本人のスキンを使う [ローカルプレビュー](shannon-skin-preview.md) を確認しました。この原本はマーカー形式に未対応なので、1.2.0では明示的な設定と顔指紋を照合し、製品側の素材対応を使います。実アカウントのセッション、shannon-home の本番ワールド、Shannon の感情送信 bridge、外部の描画 Mod との互換性は未確認です。
