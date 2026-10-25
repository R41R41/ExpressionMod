#!/usr/bin/env python3
"""Embed real ClientGameTest screenshots in one offline, interactive review file."""
import argparse
import base64
import html
import json
from pathlib import Path

NAMES = {
    "neutral": "通常", "smile": "笑顔", "angry": "怒り", "sad": "悲しみ", "surprised": "驚き",
    "think": "考え中", "shy": "照れ", "excited": "わくわく", "amused": "いたずら笑い",
    "laughing": "大笑い", "bashful": "はにかみ", "relieved": "ほっとした顔", "curious": "興味津々",
    "confused": "戸惑い", "determined": "決意", "tender": "やさしい顔", "calm": "穏やか",
    "jealous": "やきもち", "wry": "苦笑い", "exasperated": "あきれ顔", "concerned": "心配", "sleepy": "眠そう",
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--screenshots", type=Path, default=Path("build/run/clientGameTest/screenshots"))
    parser.add_argument("--output", type=Path, default=Path("build/preview/index.html"))
    parser.add_argument("--before", type=Path, help="Optional native screenshots before the seven-face adjustment")
    parser.add_argument("--variant", choices=("current", "soft-robot-v5", "minimal-lines-10"), default="current")
    args = parser.parse_args()
    images = {}
    for path in sorted(args.screenshots.glob("*.png")):
        key = path.stem.split("_", 1)[1]
        if key.startswith("circuit-"):
            continue
        images[key] = "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")
    expected = ["shannon-original-unmodified", "two-players-same-skin", "face-blink-start", "face-blink-moving"]
    expected += ["face-" + key for key in NAMES]
    expected += [f"transition-{target}-{index:02d}" for target in ("smile", "curious") for index in range(13)]
    if missing := set(expected) - images.keys():
        raise SystemExit("Missing native screenshots: " + ", ".join(sorted(missing)))
    cards = "".join(f'<button class="card" data-key="face-{key}" aria-label="{label}">'
                    f'<span class="thumb" style="background-image:url({images["face-" + key]})"></span>'
                    f'<span>{html.escape(label)}</span></button>' for key, label in NAMES.items())
    review = ""
    if args.before:
        revised = ("smile", "sad", "laughing", "bashful", "relieved", "calm", "concerned")
        pairs = []
        for key in revised:
            path = next(args.before.glob(f"*_face-{key}.png"))
            images["before-face-" + key] = "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")
            pairs.append(f'<div class="revision-pair"><strong>{NAMES[key]}</strong>'
                         f'<div class="thumb" style="background-image:url({images["before-face-" + key]})"></div>'
                         f'<div class="thumb" style="background-image:url({images["face-" + key]})"></div></div>')
        review = '<h2>今回見直した7表情</h2><p>上：変更前 ／ 下：目の高さを残し、傾きを弱めた変更後</p><div class="review-grid">' + "".join(pairs) + '</div>'
    document = TEMPLATE.replace("__CARDS__", cards).replace("__DATA__", json.dumps(images))
    document = document.replace("__REVIEW__", review)
    document = document.replace("__LABELS__", json.dumps(NAMES, ensure_ascii=False))
    if args.variant != "current":
        label = "10 最小限（体のライン入り）" if args.variant == "minimal-lines-10" else "Soft Robot v5"
        document = document.replace("シャノンの表情", "シャノンの表情 · " + label)
        document = document.replace("今のロボットスキンで", label + "のスキンで")
        document = document.replace('<header>', '<p><a href="../index.html">← 従来スキンの表情へ</a></p><header>', 1)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(document, encoding="utf-8")
    print(f"Created {args.output.resolve()} with {len(images)} native screenshots; no external resources.")

TEMPLATE = r'''<!doctype html>
<html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>シャノンの表情 — ExpressionMod 1.2.0</title>
<style>
:root{color-scheme:dark;font-family:system-ui,'Yu Gothic UI',sans-serif;color:#e7f0f7;background:#101922}
*{box-sizing:border-box}body{max-width:1140px;margin:auto;padding:32px 24px 60px}header{margin-bottom:24px}
h1{font-size:30px;letter-spacing:.06em;margin:9px 0}h2{font-size:19px;margin:22px 0 10px}p{color:#a5bacb;line-height:1.75}
.tag{font-size:12px;letter-spacing:.12em;color:#5ed7f4}.workspace{display:grid;grid-template-columns:1.4fr 1fr;gap:20px}
.panel{background:#182530;border:1px solid #2e414f;border-radius:18px;padding:18px}.frame{background:#344050;
height:280px;border-radius:12px;background-repeat:no-repeat;background-size:800% auto;background-position:50% 28%;image-rendering:pixelated}
.frame.full{background-size:contain;background-position:center}.thumb{display:block;height:112px;background-repeat:no-repeat;
background-color:#344050;background-size:800% auto;background-position:50% 28%;image-rendering:pixelated;border-radius:8px;margin-bottom:9px}
.toolbar{display:flex;gap:8px;flex-wrap:wrap;margin-top:12px}button{font:inherit;font-size:13px;cursor:pointer;color:#dceef8;
background:#243b49;border:1px solid #3f5664;border-radius:8px;padding:9px 12px}button:hover,button.active{background:#226174;border-color:#65dbed}
.grid{display:grid;grid-template-columns:repeat(6,minmax(0,1fr));gap:10px}.card{padding:8px;font-size:13px;background:#182530}
input[type=range]{width:100%;accent-color:#48c8e9}output{color:#70d9ed;font-variant-numeric:tabular-nums}
.note{font-size:12px;color:#a7bac8;line-height:1.7;margin-bottom:0}.selected{font-size:19px;margin:2px 0 12px}
.comparison{display:grid;grid-template-columns:1fr 1fr;gap:14px}.comparison .frame{height:190px}
.review-grid{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:10px}.revision-pair{background:#182530;border:1px solid #2e414f;border-radius:10px;padding:8px}.revision-pair strong{display:block;font-size:13px;margin-bottom:12px}
@media(max-width:850px){.workspace{grid-template-columns:1fr}.grid{grid-template-columns:repeat(4,1fr)}.frame{height:260px}}
@media(max-width:850px){.review-grid{grid-template-columns:repeat(4,minmax(0,1fr))}}
@media(max-width:510px){body{padding:20px 14px}.grid,.review-grid{grid-template-columns:repeat(3,1fr)}h1{font-size:24px}.thumb{height:92px}}
</style>
<header><div class="tag">EXPRESSIONMOD 1.2.0 · MINECRAFT 1.21.11</div><h1>シャノンの表情</h1>
<p>今のロボットスキンで、四角い目の幅・高さ・傾きと、水色から青のグラデーションで22の表情を確認。<br>両頬には淡い横長の赤らみがあり、照れた時は濃くなります。画像は Windows の Minecraft 実クライアントで撮影しました。</p></header>
<p><a href="circuit.html" style="color:#8cc9da">回路ラインの淡い流れ（試作）を見る →</a></p>
<div class="workspace">
<section class="panel"><div class="selected" id="selected">笑顔</div><div id="hero" class="frame" role="img" aria-label="選択中の表情"></div>
<div class="toolbar"><button id="view">全身を見る</button><button data-key="shannon-original-unmodified">元のスキン</button>
<button data-key="two-players-same-skin">同じスキンの2人</button><button data-key="face-blink-moving">瞬き</button></div></section>
<section class="panel"><div class="selected">表情が変わる途中</div><div id="motion" class="frame" role="img" aria-label="連続遷移の実描画"></div>
<div class="toolbar"><button id="smile" class="active">通常 → 笑顔</button><button id="curious">笑顔 → 興味</button>
<button id="play">再生</button><button id="speed">ゆっくり</button></div>
<p><input id="scrub" type="range" min="0" max="12" value="12" step="1" aria-label="遷移の進み具合"><output id="time">450 ms</output></p>
<p class="note">450ms の ease-out 変形を13時点で実描画したフレームの再生です。スライダーで途中の形も確認できます。</p></section>
</div>
__REVIEW__
<h2>22の表情</h2><div class="grid">__CARDS__</div>
<h2>笑顔の確認</h2><div class="comparison"><section class="panel"><div class="selected">元のスキン</div><div class="frame" id="original"></div></section>
<section class="panel"><div class="selected">Waterlight の笑顔</div><div class="frame" id="smiling"></div></section></div>
<p class="note">原本 PNG を保持しています。製品版で今のスキンを動かすには「シャノンの現在のスキンに対応」を有効にします。
対象 UUID と確認済みの顔が一致した場合だけ適用されます。感情 API・Minebot の接続と本番ワールドでの検証は今回の範囲に含みません。</p>
<script>
const images=__DATA__,labels=__LABELS__,hero=document.querySelector('#hero'),motion=document.querySelector('#motion');
const scrub=document.querySelector('#scrub'),play=document.querySelector('#play'),speed=document.querySelector('#speed');
let target='smile',timer=null,slow=false;
function show(element,key){element.style.backgroundImage=`url(${images[key]})`;element.dataset.key=key;}
function select(key){show(hero,key);document.querySelector('#selected').textContent=labels[key.slice(5)]||({
'shannon-original-unmodified':'元のスキン','two-players-same-skin':'同じスキン・別々の表情','face-blink-moving':'瞬き'})[key];
document.querySelectorAll('.card').forEach(el=>el.classList.toggle('active',el.dataset.key===key));
if(key==='two-players-same-skin'){hero.classList.add('full');document.querySelector('#view').textContent='顔を拡大する';}}
function frame(index){scrub.value=index;show(motion,`transition-${target}-${String(index).padStart(2,'0')}`);
document.querySelector('#time').textContent=`${Math.round(index/12*450)} ms`;}
function stop(){clearTimeout(timer);timer=null;play.textContent='再生';}
function animate(){if(timer!==null){stop();return;}let index=0;play.textContent='停止';
function next(){frame(index++);if(index<=12)timer=setTimeout(next,slow?150:37.5);else stop();}next();}
document.querySelectorAll('[data-key]').forEach(el=>el.addEventListener('click',()=>select(el.dataset.key)));
document.querySelector('#view').addEventListener('click',()=>{const full=hero.classList.toggle('full');document.querySelector('#view').textContent=full?'顔を拡大する':'全身を見る';});
for(const key of ['smile','curious'])document.querySelector('#'+key).addEventListener('click',()=>{stop();target=key;frame(12);
for(const other of ['smile','curious'])document.querySelector('#'+other).classList.toggle('active',other===key);});
scrub.addEventListener('input',()=>{stop();frame(Number(scrub.value));});play.addEventListener('click',animate);
speed.addEventListener('click',()=>{slow=!slow;speed.classList.toggle('active',slow);speed.textContent=slow?'通常速度':'ゆっくり';});
select('face-smile');frame(12);show(document.querySelector('#original'),'shannon-original-unmodified');show(document.querySelector('#smiling'),'face-smile');
</script></html>'''

if __name__ == "__main__":
    main()
