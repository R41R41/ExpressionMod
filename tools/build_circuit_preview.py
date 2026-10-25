#!/usr/bin/env python3
"""Build an offline circuit trial from actual Minecraft screenshots, without painting over the skin."""
import argparse
import base64
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--screenshots", type=Path, default=Path("build/run/clientGameTest/screenshots"))
    parser.add_argument("--output", type=Path, default=Path("build/preview/circuit.html"))
    parser.add_argument("--variant", choices=("current", "soft-robot-v5", "minimal-lines-10"), default="current")
    args = parser.parse_args()
    images = {}
    for path in args.screenshots.glob("*_circuit-*.png"):
        key = path.stem.split("_", 1)[1]
        images[key] = "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode("ascii")
    expected = {f"circuit-{view}-{index:02d}" for view in ("front", "side") for index in range(84)}
    expected |= {f"circuit-{view}-off" for view in ("front", "side")}
    if missing := expected - images.keys():
        raise SystemExit("Missing circuit screenshots: " + ", ".join(sorted(missing)))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    document = TEMPLATE
    if args.variant != "current":
        label = "10 最小限（体のライン入り）" if args.variant == "minimal-lines-10" else "Soft Robot v5"
        circuit = "「10 最小限」の頭・肩・前腕・脚のラインと胸・背中のパネル" if args.variant == "minimal-lines-10" else "Soft Robot v5 の頭のラインと胸・背中の水色パネル"
        document = document.replace("全身の回路と目の揺らぎ", "回路と目の揺らぎ · " + label)
        document = document.replace("頭・腕・脚の水色ラインに、濃い水色の帯が流れます。回路は5.6秒で一巡。Tシャツと脇の薄い水色の縦ラインは元のままです。",
                circuit + "に、濃い水色が流れます。回路は5.6秒で一巡。白い外装や布の色は元のままです。")
        document = document.replace('<a href="index.html">', '<p><a href="../circuit.html">← 従来スキンの回路へ</a></p><a href="index.html">', 1)
    args.output.write_text(document.replace("__IMAGES__", json.dumps(images)), encoding="utf-8")
    print(f"Created {args.output.resolve()} with {len(images)} native screenshots.")


TEMPLATE = r'''<!doctype html>
<html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>全身の回路と目の揺らぎ — シャノン</title>
<style>
:root{color-scheme:dark;font-family:system-ui,'Yu Gothic UI',sans-serif;color:#e7f0f7;background:#101922}
*{box-sizing:border-box}body{max-width:1080px;margin:auto;padding:30px 24px 50px}a{color:#8cc9da}h1{font-size:28px;margin:22px 0 12px}
p{color:#a5bacb;line-height:1.75}.tag{font-size:12px;letter-spacing:.12em;color:#8bc8d9}
.views{display:grid;grid-template-columns:1fr 1fr;gap:20px;margin-top:20px}.panel{background:#182530;border:1px solid #2e414f;border-radius:16px;padding:18px}
h2{font-size:17px;margin:0 0 15px}.frame{height:360px;background-color:#344050;border-radius:12px;background-repeat:no-repeat;
background-size:800% auto;background-position:50% 26%;image-rendering:pixelated}.frame.body{height:500px;background-size:280% auto;background-position:50% 66%}
.toolbar{display:flex;gap:10px;flex-wrap:wrap;margin-top:20px}button{font:inherit;font-size:14px;cursor:pointer;color:#dceef8;
background:#243b49;border:1px solid #3f5664;border-radius:8px;padding:10px 14px}button:hover,button.active{background:#315667;border-color:#83bfce}
input{width:100%;accent-color:#82c6d5}output{font-variant-numeric:tabular-nums;color:#b2d7e0}.note{font-size:12px}
@media(max-width:650px){.views{grid-template-columns:1fr}body{padding:20px 14px}.frame{height:280px}h1{font-size:24px}}
</style>
<a href="index.html">← 表情のプレビューへ</a><h1>全身の回路と目の揺らぎ</h1><div class="tag">試作 · 通常表示と比較できます</div>
<p>頭・腕・脚の水色ラインに、濃い水色の帯が流れます。回路は5.6秒で一巡。Tシャツと脇の薄い水色の縦ラインは元のままです。<br>目は白・水色・青の境界が8.4秒でゆっくり揺れ、表情の形は保ちます。</p>
<div class="toolbar"><button id="off">通常のライン</button><button id="on" class="active">淡い流れ（試作）</button>
<button id="pause">停止</button><button id="size">顔を拡大する</button></div>
<div class="views"><section class="panel"><h2>正面</h2><div id="front" class="frame body" role="img" aria-label="正面の全身の回路と目の揺らぎ"></div></section>
<section class="panel"><h2>斜め</h2><div id="side" class="frame body" role="img" aria-label="側面と体の回路ライン"></div></section></div>
<p><input id="phase" type="range" min="0" max="83" value="0" step="1" aria-label="回路と目の揺らぎの位置"><output id="time">0.0 秒</output></p>
<p class="note">表情を固定して、回路と目の色の動きを見比べられます。Minecraft実クライアントで撮影した84時点の繰り返しです。<br>
MODでは「回路ラインの淡い流れ（試作）」を有効にすると全身に適用。「目のグラデーションの揺らぎ」は別にオン・オフできます。</p>
<script>
const images=__IMAGES__,slider=document.querySelector('#phase'),pause=document.querySelector('#pause');
let enabled=true,playing=true,index=0,start=performance.now();
function show(){for(const view of ['front','side'])document.querySelector('#'+view).style.backgroundImage=`url(${images[`circuit-${view}-${enabled?String(index).padStart(2,'0'):'off'}`]})`;
slider.value=index;document.querySelector('#time').textContent=(index*.2).toFixed(1)+' 秒';}
function controls(){document.querySelector('#on').classList.toggle('active',enabled);document.querySelector('#off').classList.toggle('active',!enabled);
pause.textContent=playing?'停止':'再生';pause.disabled=!enabled;slider.disabled=!enabled;}
document.querySelector('#on').addEventListener('click',()=>{enabled=true;start=performance.now()-index*200;controls();show();});
document.querySelector('#off').addEventListener('click',()=>{enabled=false;controls();show();});
pause.addEventListener('click',()=>{playing=!playing;start=performance.now()-index*200;controls();});
slider.addEventListener('input',()=>{playing=false;index=Number(slider.value);controls();show();});
document.querySelector('#size').addEventListener('click',()=>{const body=document.querySelector('#front').classList.toggle('body');document.querySelector('#side').classList.toggle('body',body);
document.querySelector('#size').textContent=body?'顔を拡大する':'全身を見る';});
function tick(now){if(playing&&enabled){const next=Math.floor((now-start)/200)%84;if(next!==index){index=next;show();}}requestAnimationFrame(tick);}
show();controls();requestAnimationFrame(tick);
</script></html>'''

if __name__ == "__main__":
    main()
