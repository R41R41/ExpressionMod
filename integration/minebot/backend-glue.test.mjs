// Execute the actual patched MinebotCompanionBody/turn/reply code with model, UI and task ports mocked.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { stripTypeScriptTypes } from 'node:module';
import { EventEmitter } from 'node:events';
import { MinebotExpressionBridge, CHANNELS } from './expression-bridge.mjs';
const root=resolve(process.env.EXPRESSIONMOD_TASK_ROOT ?? process.cwd());
const candidate=join(root,'artifacts/expressionmod-desktop-20261008/alive-motion/backend/candidate/backend/src/services/minebot/integration');
const reference=join(root,'handoff-2026-10-07/vm-reference/liveness');
const dir=mkdtempSync(join(tmpdir(),'expression-backend-'));
writeFileSync(join(dir,'package.json'),'{"type":"module"}');
for(const name of ['MinebotCompanionBody','companionBodyParts','CompanionBodyClient']) {
  const source=readFileSync(join(name==='MinebotCompanionBody'?candidate:reference,name+'.ts'),'utf8');
  writeFileSync(join(dir,name+'.js'),stripTypeScriptTypes(source,{mode:'transform'}));
}
writeFileSync(join(dir,'CompanionRequestLoop.js'),'export class CompanionRequestLoop {start(){} async stop(){} taken=[];}');
writeFileSync(join(dir,'CompanionRuntimeTasks.js'),'export class CompanionRuntimeTasks {constructor(){} } export function inventoryCounts(){return {}};');
const {MinebotCompanionBody}=await import(pathToFileURL(join(dir,'MinebotCompanionBody.js')));
const {CompanionBodyClient}=await import(pathToFileURL(join(dir,'CompanionBodyClient.js')));
test.after(()=>rmSync(dir,{recursive:true,force:true}));
const uuid='accf3442-b95f-414b-95fd-f70bbd2c9764';
function setup(failure=false,enabled=true) {
  const bot=new EventEmitter();bot.username='I_am_Shannon';bot.player={uuid};bot.entity={position:{x:0,y:0,z:0},yaw:0};bot.players={};
  bot._client=new EventEmitter();const packets=[];const chats=[];const requests=[];
  bot._client.write=(name,p)=>{packets.push(p);if(p.channel==='minecraft:register')bot._client.emit('custom_payload',{channel:CHANNELS.ready,data:Buffer.from([1])});};
  bot.chat=line=>chats.push(line);
  let mood='neutral';
  const fetcher=async(url,init)=>{
    const path=new URL(url).pathname;requests.push({path,init});
    if(path==='/v1/body/presence')return Response.json({schemaVersion:1,etag:mood==='neutral'?'0000000000000000':'1111111111111111',pollAfterSeconds:10,mood:{expression:mood}});
    if(path==='/v1/body/minecraft/turns'){
      if(failure)return new Response('mock unavailable',{status:502});
      mood='joyful';return Response.json({reply:'わかったよ。',duplicate:false,intent:{kind:'none',goal:''}});
    }
    return Response.json({ok:true}); // virtual UI port, no real send
  };
  const bridge=enabled?new MinebotExpressionBridge(bot,{baseUrl:'http://127.0.0.1:9999',token:'synthetic-test-token',fetcher}):undefined;
  const client=new CompanionBodyClient({baseUrl:'http://127.0.0.1:9999',token:'synthetic-test-token',serverId:'fixture',fetcher});
  const runtime={getTaskListState:()=>({tasks:[]})};
  const body=new MinebotCompanionBody(bot,runtime,client,{serverId:'fixture',uiModBaseUrl:()=> 'http://127.0.0.1:9998',fetcher,expressionBridge:bridge});
  return {body,bridge,bot,packets,chats,requests};
}
test('patched backend drives activity through the real reply callback and re-reads emotion',async()=>{
  const f=setup();try {
    f.body.start();assert.equal(await f.body.answer({uuid,name:'FixtureOwner'},'シャノン、こんにちは'),true);
    for(let i=0;i<20&&f.bridge.lease.expression!=='joyful';i++)await new Promise(r=>setTimeout(r,5));
    assert.deepEqual(f.chats,['わかったよ。']);assert.equal(f.bridge.activity,'speaking');assert.equal(f.bridge.lease.expression,'joyful');
    assert(f.packets.some(p=>p.channel===CHANNELS.dialogue&&p.data.includes(Buffer.from('listening'))));
    assert(f.packets.some(p=>p.channel===CHANNELS.dialogue&&p.data.includes(Buffer.from('speaking'))));
    assert(f.requests.filter(r=>r.path==='/v1/body/minecraft/turns').length===1);
  }finally{await f.body.stop();}assert.equal(f.bridge.running,false);assert.equal(f.bot._client.listenerCount('custom_payload'),0);
});
test('unavailable mind clears activity while preserving the existing fallback',async()=>{
  const f=setup(true);try{f.body.start();assert.equal(await f.body.answer({uuid,name:'FixtureOwner'},'シャノン'),false);
    assert.equal(f.bridge.activity,'idle');assert.deepEqual(f.chats,[]);
  }finally{await f.body.stop();}
});
test('default off preserves backend turn and reply without a presence request',async()=>{
  const f=setup(false,false);try{f.body.start();assert.equal(await f.body.answer({uuid,name:'FixtureOwner'},'シャノン'),true);
    assert.equal(f.packets.length,0);assert.equal(f.requests.filter(r=>r.path==='/v1/body/presence').length,0);
  }finally{await f.body.stop();}
});
