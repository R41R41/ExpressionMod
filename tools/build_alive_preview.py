"""Build a two-skin conversation demo from native Minecraft screenshots."""
import argparse
import base64
import json
from io import BytesIO
from pathlib import Path
from PIL import Image, ImageDraw

STAGES = ['Idle', 'Listening', 'Thinking', 'Replying', 'Settling']
def stage(i): return 0 if i < 10 else 1 if i < 20 else 2 if i < 40 else 3 if i < 62 else 4

def frames(path):
    return [next(path.glob(f'*_alive-{i:02d}.png')) for i in range(84)]

def gif(paths, target, label):
    imgs=[]
    for i,path in enumerate(paths):
        with Image.open(path) as source:
            image=Image.new('RGB',(856,680),'#1b2934')
            image.paste(source.crop((320,92,534,418)).resize((428,652),Image.Resampling.NEAREST),(0,28))
            image.paste(source.crop((374,92,478,188)).resize((416,384),Image.Resampling.NEAREST),(440,28))
            draw=ImageDraw.Draw(image);draw.text((12,8),label,fill='#dceef8')
            draw.text((452,8),STAGES[stage(i)],fill='#dceef8');imgs.append(image)
    atlas=Image.new('RGB',(856,680*len(imgs)))
    for i,img in enumerate(imgs): atlas.paste(img,(0,680*i))
    palette=atlas.quantize(colors=256,dither=Image.Dither.NONE)
    indexed=[img.quantize(palette=palette,dither=Image.Dither.NONE) for img in imgs]
    indexed[0].save(target,save_all=True,append_images=indexed[1:],duration=100,loop=0,optimize=False)
    imgs[45].save(target.with_suffix('.png'))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--minimal',type=Path,required=True)
    p.add_argument('--overalls',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
    a.output.mkdir(parents=True,exist_ok=True)
    data={}
    for key,folder,label in [('minimal',a.minimal,'Minimal lines'),('overalls',a.overalls,'Overalls')]:
        paths=frames(folder);data[key]={'body':[], 'head':[]}
        for path in paths:
            with Image.open(path) as source:
                for view,box in [('body',(320,92,534,418)),('head',(374,92,478,188))]:
                    stream=BytesIO();source.crop(box).save(stream,format='PNG')
                    data[key][view].append('data:image/png;base64,'+base64.b64encode(stream.getvalue()).decode())
        gif(paths,a.output/f'{key}-alive.gif',label)
    html='''<!doctype html><html lang="ja"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>シャノン — 会話の動き</title><style>
*{box-sizing:border-box}body{margin:0;background:#111c27;color:#e8f4fc;font:16px/1.6 system-ui,sans-serif}main{max-width:1200px;margin:auto;padding:24px}h1{font-size:27px;margin:0}p{color:#adc1d1;margin:10px 0}a{color:#8cdfee}button{background:#293b4d;color:#e8f4fc;border:1px solid #536b7b;border-radius:8px;padding:8px 18px;cursor:pointer}button.active{background:#315d70;border-color:#7edbea}.controls{display:flex;align-items:center;gap:12px;flex-wrap:wrap;padding:14px 0}.stages{display:flex;gap:8px;flex-wrap:wrap}.stage{padding:7px 14px;border-radius:8px;color:#90a7b9;background:#1b2b39}.stage.active{background:#28576b;color:#d8ffff}.grid{display:grid;grid-template-columns:1fr 1fr;gap:18px;margin-top:18px}.card{background:#1b2b39;border:1px solid #3d5365;border-radius:14px;overflow:hidden}.card h2{font-size:18px;padding:14px 18px;margin:0}.views{display:grid;grid-template-columns:1fr 1fr}.body,.head{background-repeat:no-repeat;image-rendering:pixelated}.body{height:430px;background-size:280%;background-position:50% 66%}.head{height:210px;background-size:800%;background-position:50% 26%;margin:25px 6px 0 0}.note{padding:12px 16px;color:#b5cad9;font-size:14px}input{flex:1;min-width:160px;accent-color:#66c6d9}time{font-variant-numeric:tabular-nums}footer{font-size:14px;padding-top:18px;color:#94aabc}@media(max-width:800px){.grid{grid-template-columns:1fr}.body{height:440px}}@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto}}</style>
<main><h1>シャノン — 会話の動き</h1><p>目が先に動き、頭が少し遅れて追う。考えるときは視線を外し、返し終わりに軽く瞬きます。</p>
<div class="controls"><button id="play">一時停止</button><button id="replay">最初から</button><input id="scrub" aria-label="会話の時刻" type="range" min="0" max="83" value="0"><time id="time">0.0 秒</time></div>
<div class="stages" aria-label="会話の流れ"><span class="stage">待機</span><span class="stage">聞く</span><span class="stage">考える</span><span class="stage">返す</span><span class="stage">余韻</span></div>
<div class="grid"><section class="card"><h2>体のライン入り · 10 最小限</h2><div class="views"><div class="body" id="minimal-body"></div><div class="head" id="minimal-head"></div></div><div class="note">肩・前腕・脚の回路と、目の揺らぎを維持。</div></section>
<section class="card"><h2>旧オーバーオール</h2><div class="views"><div class="body" id="overalls-body"></div><div class="head" id="overalls-head"></div></div><div class="note">Tシャツと脇の薄い水色ラインには電流を流しません。</div></section></div>
<footer>Minecraftの実描画で撮影した、会話の動作の見本です。本番の会話連動はまだ反映していません。<br><a href="../minimal-lines-10/index.html">体のライン入りの表情一覧</a> · <a href="../index.html">旧オーバーオールの表情一覧</a></footer>
<script>const frames=__DATA__;let frame=0,playing=!matchMedia('(prefers-reduced-motion:reduce)').matches,last=performance.now(),carry=0;
const play=document.querySelector('#play'),scrub=document.querySelector('#scrub');
function draw(){for(const key of ['minimal','overalls'])for(const view of ['body','head'])document.getElementById(key+'-'+view).style.backgroundImage='url("'+frames[key][view][frame]+'")';scrub.value=frame;document.querySelector('#time').textContent=(frame/10).toFixed(1)+' 秒';const stage=frame<10?0:frame<20?1:frame<40?2:frame<62?3:4;document.querySelectorAll('.stage').forEach((x,i)=>x.classList.toggle('active',i===stage));play.textContent=playing?'一時停止':'再生';}
play.onclick=()=>{playing=!playing;carry=0;draw();};document.querySelector('#replay').onclick=()=>{frame=0;carry=0;playing=true;draw();};scrub.oninput=()=>{frame=Number(scrub.value);playing=false;draw();};
function tick(now){const dt=Math.min(250,now-last);last=now;if(playing&&!document.hidden){carry+=dt;if(carry>=100){frame=(frame+Math.floor(carry/100))%84;carry%=100;draw();}}requestAnimationFrame(tick);}draw();requestAnimationFrame(tick);</script></main></html>'''
    html=html.replace('</style>', '.body,.head{background-size:contain;background-position:center center}.head{margin:20px 8px 0 0}@media(max-width:800px){main{padding:18px}.grid{grid-template-columns:1fr 1fr;gap:10px}.card h2{font-size:14px;padding:10px}.views{grid-template-columns:1fr}.head{height:155px;order:1;margin:0}.body{height:270px;order:2}.note{font-size:12px;padding:10px}}@media(max-width:440px){.grid{grid-template-columns:1fr}.head{height:220px}.body{height:320px}}</style>')
    (a.output/'index.html').write_text(html.replace('__DATA__',json.dumps(data)),encoding='utf-8')
    print(f'Created native two-skin preview: {a.output.resolve()}')

if __name__=='__main__':main()
