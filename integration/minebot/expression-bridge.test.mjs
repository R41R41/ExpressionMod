import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { createServer } from 'node:http';
import { PresenceLease, MinebotExpressionBridge, CHANNELS, EMOTIONS, speakerAngles, emoteBytes, dialogueBytes } from './expression-bridge.mjs';
const uuid='accf3442-b95f-414b-95fd-f70bbd2c9764', etag='0123456789abcdef';
const presence = (expression='joyful') => ({schemaVersion:1,etag,pollAfterSeconds:10,mood:{expression}});
const flush = async () => { for(let i=0;i<8;i++) await new Promise(resolve=>setImmediate(resolve)); };
function fixture(fetcher) {
  let now=0; let id=0; const timers=new Map(); const sent=[];
  const bot=new EventEmitter(); bot._client=new EventEmitter(); bot._client.uuid=uuid;
  bot.username='I_am_Shannon'; bot.player={uuid}; bot.entity={position:{x:0,y:0,z:0},height:1.8,yaw:0}; bot.players={};
  bot._client.write=(name,packet)=>sent.push(packet);
  const bridge=new MinebotExpressionBridge(bot,{baseUrl:'http://127.0.0.1:9999',token:'synthetic-test-token',fetcher,
    clock:()=>now,setTimer:(fn,ms)=>{timers.set(++id,{fn,ms});return id;},clearTimer:id=>timers.delete(id)});
  return {bot,bridge,sent,timers,setNow:n=>now=n,ready:()=>bot._client.emit('custom_payload',{channel:CHANNELS.ready,data:Buffer.from([1])})};
}
test('all 22 expressions come only from mood.expression and unchanged renews the lease',()=>{
  for(const expression of EMOTIONS){ const lease=new PresenceLease(); assert(lease.accept(presence(expression),0));
    assert.equal(lease.sample(24999).expression,expression); assert.equal(lease.sample(25000).expression,'neutral');
    assert(lease.accept({schemaVersion:1,etag,unchanged:true,pollAfterSeconds:10},25001)); assert.equal(lease.sample(25002).expression,expression); }
  assert.equal(new PresenceLease().accept({schemaVersion:1,etag,pollAfterSeconds:10,expression:'joyful'},0),false);
});
test('unknown shapes and mismatched etags cannot revive a cached face',()=>{
  const lease=new PresenceLease(); assert(!lease.accept({schemaVersion:1,etag,unchanged:true,pollAfterSeconds:10},0));
  lease.accept(presence(),0);
  assert(!lease.accept({schemaVersion:1,etag:'abcdef0123456789',unchanged:true,pollAfterSeconds:10},1));
  assert(!lease.accept({...presence(),pollAfterSeconds:0},1)); assert(!lease.accept(presence('private-cause'),1));
});
test('bridge waits for version acknowledgement, authenticates GET and honors unchanged',async()=>{
  const requests=[]; const f=fixture(async(url,init)=>{requests.push({url:String(url),init});return Response.json(requests.length===1?presence():{schemaVersion:1,etag,unchanged:true,pollAfterSeconds:7});});
  f.bridge.start();await flush();assert.equal(f.sent.filter(p=>p.channel===CHANNELS.emote).length,0);
  f.ready(); assert(f.sent.some(p=>p.channel===CHANNELS.emote));
  assert.equal(new URL(requests[0].url).searchParams.get('scopeKey'),'owner'); assert.equal(requests[0].init.method,'GET');
  assert.equal(requests[0].init.redirect,'error'); assert.equal(requests[0].init.headers.authorization,'Bearer synthetic-test-token');
  f.setNow(10000); f.bridge.refresh();await flush();assert.equal(new URL(requests[1].url).searchParams.get('etag'),etag);
  assert([...f.timers.values()].some(t=>t.ms===7000)); f.bridge.stop(); assert.equal(f.timers.size,0);
});
test('stop rejects delayed network results and removes every listener',async()=>{
  let resolve; const f=fixture(()=>new Promise(r=>resolve=r)); f.bridge.start();f.ready();f.bridge.stop();const count=f.sent.length;
  resolve(Response.json(presence()));await flush(); assert.equal(f.sent.length,count);assert.equal(f.timers.size,0);
  assert.equal(f.bot._client.listenerCount('custom_payload'),0);assert.equal(f.bot.listenerCount('spawn'),0);
});
test('401/403 stop polling and clear the display without recording the response',async()=>{
  const f=fixture(async()=>new Response('synthetic secret must not be logged',{status:403}));f.bridge.start();f.ready();await flush();
  assert.equal(f.bridge.running,false);assert.equal(f.timers.size,0);
});
test('network failure keeps last expression only until freshness deadline',async()=>{
  let fail=false; const f=fixture(async()=>{if(fail)throw Error('secret-bearing exception');return Response.json(presence());});
  f.bridge.start();f.ready();await flush();fail=true;f.setNow(10000);f.bridge.refresh();await flush();
  assert.equal(f.bridge.lease.sample(24000).expression,'joyful');assert.equal(f.bridge.lease.sample(25000).expression,'neutral');
  f.setNow(25000);f.bridge.publish();assert(f.sent.at(-2).data.includes(Buffer.from('neutral')));f.bridge.stop();
});
test('actual turn events drive listening/thinking/speaking, including interruption and failure',async()=>{
  const f=fixture(async()=>Response.json(presence()));f.bridge.start();f.ready();await flush();
  const a=f.bridge.beginTurn(uuid);assert.equal(f.bridge.activity,'listening');f.setNow(601);f.bridge.publish();
  assert(f.sent.at(-1).data.includes(Buffer.from('thinking')));
  const b=f.bridge.beginTurn(uuid);a.speaking();assert.equal(f.bridge.activity,'listening');b.speaking();b.finish();
  assert.equal(f.bridge.activity,'speaking');f.setNow(3000);f.bridge.publish();assert(f.sent.at(-1).data.includes(Buffer.from('idle')));
  const c=f.bridge.beginTurn(uuid);c.finish();assert.equal(f.bridge.activity,'idle');f.bridge.stop();
});
test('speaker gaze is local, finite, near and forward; it cannot rotate navigation',()=>{
  const bot={entity:{position:{x:0,y:0,z:0},yaw:0},players:{other:{uuid,entity:{position:{x:1,y:0,z:-3}}}}};
  assert(speakerAngles(bot,uuid).yaw>0);bot.players.other.entity.position.z=3;assert.equal(speakerAngles(bot,uuid),null);
  bot.players.other.entity.position.z=-30;assert.equal(speakerAngles(bot,uuid),null);assert.equal(speakerAngles(bot,'bad'),null);
});
test('loopback HTTP integration consumes the real contract without conversation or memory calls',async()=>{
  const paths=[]; const server=createServer((req,res)=>{paths.push(req.url);assert.equal(req.headers.authorization,'Bearer synthetic-test-token');
    res.setHeader('content-type','application/json');res.end(JSON.stringify(presence('concerned')));});
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  const f=fixture(fetch);f.bridge.baseUrl=`http://127.0.0.1:${server.address().port}`;
  try { f.bridge.start();f.ready();for(let i=0;i<30&&!f.bridge.lease.etag;i++)await new Promise(resolve=>setTimeout(resolve,10));
    assert.equal(f.bridge.lease.expression,'concerned');assert.equal(paths.length,1);assert(paths[0].startsWith('/v1/body/presence?'));
  } finally {f.bridge.stop();await new Promise(resolve=>server.close(resolve));}
});
test('URL validation prevents sending the device credential off loopback',()=>{
  assert.throws(()=>new MinebotExpressionBridge({}, {baseUrl:'https://example.com',token:'synthetic-test-token'}),/LOOPBACK/);
});
test('refresh while a request is in flight schedules one follow-up, not overlapping polls',async()=>{
  let calls=0,resolve;const f=fixture(()=>{calls++;return new Promise(r=>resolve=r);});f.bridge.start();f.bridge.refresh();f.bridge.refresh();
  assert.equal(calls,1);resolve(Response.json(presence()));await flush();
  const follow=[...f.timers.values()].find(t=>t.ms===0);assert(follow);follow.fn();assert.equal(calls,2);
  f.bridge.stop();resolve(Response.json(presence()));await flush();
});
test('oversized response and unsupported bridge version never emit a private value',async()=>{
  const f=fixture(async()=>new Response('x'.repeat(65537)));f.bridge.start();
  f.bot._client.emit('custom_payload',{channel:CHANNELS.ready,data:Buffer.from([99])});await flush();
  assert.equal(f.bridge.ready,false);assert(!f.bridge.lease.etag);f.bridge.stop();
});
