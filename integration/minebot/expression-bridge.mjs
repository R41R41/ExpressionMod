// No imports from Shannon's model, memory, DB or chat classification. Node 22+, no dependencies.
export const EMOTIONS = Object.freeze(['joyful','excited','amused','laughing','bashful','embarrassed','relieved',
  'surprised','curious','thinking','confused','determined','tender','calm','jealous','wry','exasperated','annoyed','concerned','sad','sleepy','neutral']);
export const CHANNELS = Object.freeze({ emote:'expressionmod:emote_sync', dialogue:'expressionmod:dialogue_sync', ready:'expressionmod:bridge_ready' });
const validUuid = value => typeof value === 'string' && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value);
const clamp = (n, lo, hi) => Math.max(lo, Math.min(hi, n));

export class PresenceLease {
  etag; expression = 'neutral'; until = 0; pollMs = 10000;
  accept(data, now) {
    if (!data || data.schemaVersion !== 1 || !/^[0-9a-f]{16}$/.test(data.etag ?? '')
      || !Number.isFinite(data.pollAfterSeconds) || data.pollAfterSeconds < 1 || data.pollAfterSeconds > 300) return false;
    const unchanged = data.unchanged === true;
    if (unchanged ? !this.etag || data.etag !== this.etag : !EMOTIONS.includes(data.mood?.expression)) return false;
    if (!unchanged) this.expression = data.mood.expression;
    this.etag = data.etag;
    this.pollMs = data.pollAfterSeconds * 1000;
    this.until = now + Math.min(30000, this.pollMs * 2 + 5000);
    return true;
  }
  sample(now) { return { expression: now < this.until ? this.expression : 'neutral', remaining: Math.max(0, this.until - now) }; }
}

function varint(value) {
  const bytes = []; let v = BigInt(value);
  do { const b = Number(v & 127n); v >>= 7n; bytes.push(v ? b | 128 : b); } while (v);
  return Buffer.from(bytes);
}
function string(value) { const bytes = Buffer.from(value, 'utf8'); return Buffer.concat([varint(bytes.length), bytes]); }
export function emoteBytes(uuid, name, expression, lease) {
  return Buffer.concat([string(uuid), string(name), string(expression), varint(clamp(Math.round(lease), 1, 30000))]);
}
export function dialogueBytes(uuid, name, activity, lease, target) {
  const end = Buffer.alloc(9); end[0] = target ? 1 : 0;
  end.writeFloatBE(target?.yaw ?? 0, 1); end.writeFloatBE(target?.pitch ?? 0, 5);
  return Buffer.concat([string(uuid), string(name), string(activity), varint(clamp(Math.round(lease), 250, 5000)), end]);
}

/** Local visual target only. No coordinates leave this process; bot.look/navigation are never called. */
export function speakerAngles(bot, uuid) {
  const self = bot.entity;
  const other = Object.values(bot.players ?? {}).find(p => p.uuid?.toLowerCase() === uuid?.toLowerCase())?.entity;
  if (!self?.position || !other?.position || !Number.isFinite(self.yaw)) return null;
  const dx = other.position.x-self.position.x, dz = other.position.z-self.position.z;
  const dy = other.position.y+(other.height ?? 1.8)*.9-self.position.y-(self.height ?? 1.8)*.9;
  const distance = Math.hypot(dx,dy,dz);
  if (!Number.isFinite(distance) || distance > 8 || distance < .1) return null;
  const diff = Math.atan2(-dx,-dz)-self.yaw;
  const yaw = -Math.atan2(Math.sin(diff),Math.cos(diff))*180/Math.PI;
  const pitch = -Math.atan2(dy,Math.hypot(dx,dz))*180/Math.PI;
  // A face cannot look behind itself. Leave body orientation to the existing Minebot controller.
  return Math.abs(yaw) <= 60 ? { yaw:clamp(yaw,-30,30), pitch:clamp(pitch,-30,30) } : null;
}

async function readJson(response) {
  if (Number(response.headers.get('content-length')) > 65536) throw new Error('RESPONSE_LIMIT');
  const reader = response.body?.getReader(); if (!reader) throw new Error('RESPONSE_EMPTY');
  const chunks = []; let length = 0;
  try { for (;;) { const {done,value} = await reader.read(); if (done) break;
    length += value.byteLength; if (length > 65536) throw new Error('RESPONSE_LIMIT'); chunks.push(value); }
  } finally { await reader.cancel().catch(() => {}); }
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

/** Optional body adapter. Construct only for the already configured companion world/device. */
export class MinebotExpressionBridge {
  constructor(bot, options) {
    const url = new URL(options.baseUrl);
    if (url.protocol !== 'http:' || !['127.0.0.1','localhost','[::1]'].includes(url.hostname)
      || url.username || url.password) throw new Error('EXPRESSION_URL_MUST_BE_LOOPBACK');
    if (typeof options.token !== 'string' || options.token.length < 16 || /[\r\n]/.test(options.token)) throw new Error('EXPRESSION_TOKEN_MISSING');
    this.bot = bot; this.baseUrl = url.origin; this.token = options.token;
    this.fetcher = options.fetcher ?? fetch; this.clock = options.clock ?? (() => performance.now());
    this.setTimer = options.setTimer ?? setTimeout; this.clearTimer = options.clearTimer ?? clearTimeout;
    this.lease = new PresenceLease(); this.running = false; this.ready = false; this.generation = 0;
    this.activity = 'idle'; this.activityUntil = 0; this.turnId = 0; this.lastEmoteAt = -Infinity;
    this.onPayload = packet => {
      if (packet.channel === CHANNELS.ready && Buffer.isBuffer(packet.data) && packet.data.length === 1 && packet.data[0] === 1) {
        this.ready = true; this.publish(true);
      }
    };
    this.onSpawn = () => this.advertise(); this.onEnd = () => this.stop();
  }
  start() {
    if (this.running) return;
    this.running = true; this.generation++;
    this.bot._client.on('custom_payload',this.onPayload); this.bot.on('spawn',this.onSpawn); this.bot.on('end',this.onEnd);
    if (this.bot.entity) this.advertise();
    void this.poll(this.generation); this.tick(this.generation);
  }
  advertise() {
    if (!this.running) return;
    this.ready = false;
    // Register after late attachment too: ExpressionMod replies with the protocol version.
    this.bot._client.write('custom_payload',{ channel:'minecraft:register', data:Buffer.from(Object.values(CHANNELS).join('\0')) });
  }
  stop() {
    if (!this.running) return;
    this.activity = 'idle'; this.activityUntil = 0;
    this.publish(true, true);
    this.running = false; this.ready = false; this.generation++; this.turnId++;
    this.refreshRequested = false;
    this.abort?.abort(); this.clearTimer(this.pollTimer); this.clearTimer(this.tickTimer);
    this.bot._client.removeListener('custom_payload',this.onPayload); this.bot.removeListener('spawn',this.onSpawn); this.bot.removeListener('end',this.onEnd);
    this.lease = new PresenceLease();
  }
  async poll(generation) {
    if (!this.running || generation !== this.generation) return;
    this.pollTimer = undefined; this.inFlight = true;
    let delay = this.lease.pollMs;
    const url = new URL('/v1/body/presence',this.baseUrl); url.searchParams.set('scopeKey','owner');
    if (this.lease.etag) url.searchParams.set('etag',this.lease.etag);
    const abort = this.abort = new AbortController(); const timer = this.setTimer(() => abort.abort(),5000);
    try {
      const response = await this.fetcher(url,{method:'GET',headers:{authorization:`Bearer ${this.token}`},redirect:'error',signal:abort.signal});
      if (!this.running || generation !== this.generation) return;
      if (response.status === 401 || response.status === 403) { this.stop(); return; }
      if (!response.ok) throw new Error('PRESENCE_UNAVAILABLE');
      const data = await readJson(response);
      if (!this.running || generation !== this.generation) return;
      if (!this.lease.accept(data,this.clock())) throw new Error('PRESENCE_INVALID');
      this.failures = 0; delay = this.lease.pollMs; this.publish(true);
    } catch {
      // Do not log the request, exception, token, server response, mood cause or conversation.
      if (this.running && generation === this.generation) {
        this.failures = (this.failures ?? 0)+1; delay = Math.min(30000,5000*2**Math.min(3,this.failures-1));
      }
    } finally { this.clearTimer(timer); if (this.abort === abort) this.inFlight = false; }
    if (this.running && generation === this.generation) {
      if (this.refreshRequested) { delay = 0; this.refreshRequested = false; }
      this.pollTimer = this.setTimer(() => { void this.poll(generation); },delay);
    }
  }
  tick(generation) {
    if (!this.running || generation !== this.generation) return;
    this.publish(); this.tickTimer = this.setTimer(() => this.tick(generation),1000);
  }
  publish(force = false, clearing = false) {
    if (!this.running || !this.ready) return;
    const uuid = this.bot.player?.uuid ?? this.bot._client.uuid;
    const name = this.bot.username;
    if (!validUuid(uuid) || typeof name !== 'string' || !/^[.A-Za-z0-9_]{1,17}$/.test(name)) return;
    const now = this.clock(); const face = this.lease.sample(now);
    try {
      if (force || now-this.lastEmoteAt >= 5000 || face.expression !== this.lastExpression) {
        this.bot._client.write('custom_payload',{channel:CHANNELS.emote,data:emoteBytes(uuid,name,clearing?'neutral':face.expression,Math.max(1,face.remaining))});
        this.lastEmoteAt = now; this.lastExpression = face.expression;
      }
      const activity = now < this.activityUntil ? (this.activity === 'listening' && now >= this.thinkingAt ? 'thinking' : this.activity) : 'idle';
      const target = activity === 'idle' ? null : speakerAngles(this.bot,this.speaker);
      this.bot._client.write('custom_payload',{channel:CHANNELS.dialogue,data:dialogueBytes(uuid,name,activity,5000,target)});
    } catch { /* Disconnecting a game socket must not stop her task runtime. Leases expire on observers. */ }
  }
  beginTurn(speakerUuid) {
    const id = ++this.turnId; this.speaker = validUuid(speakerUuid) ? speakerUuid : undefined;
    this.activity = 'listening'; this.thinkingAt = this.clock()+600; this.activityUntil = this.clock()+65000; this.publish();
    let spoken = false;
    return {
      speaking: () => { if (!this.running || id !== this.turnId) return;
        spoken = true; this.activity = 'speaking'; this.activityUntil = this.clock()+2200; this.publish();
        // Fetch the mind's actual expression after the turn, without inventing a reply emotion.
        this.refresh();
      },
      finish: () => { if (!this.running || id !== this.turnId || spoken) return;
        this.activity = 'idle'; this.activityUntil = 0; this.publish(); }
    };
  }
  refresh() {
    // Serialize poll calls: refresh at next scheduled tick rather than overlap an in-flight request.
    if (!this.running) return;
    if (this.inFlight) { this.refreshRequested = true; return; }
    if (!this.pollTimer) return;
    this.clearTimer(this.pollTimer); this.pollTimer = undefined; void this.poll(this.generation);
  }
}
