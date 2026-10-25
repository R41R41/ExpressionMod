import { writeFileSync } from 'node:fs';
import { emoteBytes, dialogueBytes, EMOTIONS } from './expression-bridge.mjs';
const uuid='accf3442-b95f-414b-95fd-f70bbd2c9764';
const fixture = { emotes: EMOTIONS.map(expression => ({expression,hex:emoteBytes(uuid,'I_am_Shannon',expression,25000).toString('hex')})),
  dialogue: dialogueBytes(uuid,'I_am_Shannon','thinking',5000,{yaw:22,pitch:-5}).toString('hex') };
writeFileSync(new URL('../../src/gametest/resources/bridge-wire.json',import.meta.url),JSON.stringify(fixture,null,2)+'\n');
