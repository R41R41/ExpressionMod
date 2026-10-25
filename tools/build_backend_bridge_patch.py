"""Build a reviewable patch against the read-only VM snapshot; never writes the VM."""
from pathlib import Path
import argparse
import difflib
import hashlib
import json

def replace(text, old, new):
    if text.count(old) != 1:
        raise SystemExit(f"Snapshot seam changed: {old[:100]!r}")
    return text.replace(old, new)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    mod = Path(__file__).resolve().parent.parent
    rel = 'backend/src/services/minebot/'
    originals, revised = {}, {}
    for name in ('skillAgent.ts', 'MinebotCompanionBody.ts'):
        path = rel + ('' if name == 'skillAgent.ts' else 'integration/') + name
        originals[path] = (args.reference / name).read_text(encoding='utf-8')
        revised[path] = originals[path]
    path = rel+'skillAgent.ts'
    text = replace(revised[path], "import { CompanionBodyClient } from './integration/CompanionBodyClient.js';",
        "import { CompanionBodyClient } from './integration/CompanionBodyClient.js';\nimport { MinebotExpressionBridge } from './integration/expression-bridge.mjs';")
    text = replace(text, '      serverId: settings.serverId,\n      uiModBaseUrl:',
        "      serverId: settings.serverId,\n      expressionBridge: process.env.MINEBOT_EXPRESSION_BRIDGE === 'true'\n        ? new MinebotExpressionBridge(this.bot, { baseUrl: settings.url, token }) : undefined,\n      uiModBaseUrl:")
    revised[path] = text
    path = rel+'integration/MinebotCompanionBody.ts'
    text = "import type { ExpressionBridge } from './expression-bridge.mjs';\n"+revised[path]
    text = replace(text, 'export interface MinebotCompanionBodyOptions {',
        'export interface MinebotCompanionBodyOptions {\n  /** Optional expression transport; no model, memory or navigation ownership. */\n  expressionBridge?: ExpressionBridge;')
    text = replace(text, '    this.started = true;', '    this.started = true;\n    this.options.expressionBridge?.start();')
    text = replace(text, '    this.stopped = true;', '    this.stopped = true;\n    this.options.expressionBridge?.stop();')
    text = replace(text, "    const outcome = await takeCompanionTurn(this.client,",
        "    const faceTurn = this.options.expressionBridge?.beginTurn(speaker.uuid);\n    try {\n    const outcome = await takeCompanionTurn(this.client,")
    text = replace(text, '      reply => speakCompanionReply(this.bot, reply, { uiModBaseUrl: this.options.uiModBaseUrl(),',
        '      reply => { faceTurn?.speaking(); speakCompanionReply(this.bot, reply, { uiModBaseUrl: this.options.uiModBaseUrl(),')
    text = replace(text, '        lineLimit: this.options.lineLimit, maxLines: this.options.maxLines, fetcher: this.options.fetcher }));',
        '        lineLimit: this.options.lineLimit, maxLines: this.options.maxLines, fetcher: this.options.fetcher }); });')
    text = replace(text, '    return true;\n  }\n\n  /** The runtime',
        '    return true;\n    } finally { faceTurn?.finish(); }\n  }\n\n  /** The runtime')
    # Indent the original method body inside try so the reviewed diff has normal formatting.
    start = text.index('    try {\n    const outcome')+len('    try {\n')
    end = text.index('    } finally { faceTurn?.finish(); }', start)
    text = text[:start]+''.join('  '+line if line.strip() else line for line in text[start:end].splitlines(keepends=True))+text[end:]
    revised[path] = text
    for name in ('expression-bridge.mjs', 'expression-bridge.d.mts'):
        path = rel+'integration/'+name
        originals[path] = ''
        revised[path] = (mod/'integration/minebot'/name).read_text(encoding='utf-8')
    args.output.mkdir(parents=True, exist_ok=True)
    patch = []
    for path, text in revised.items():
        target = args.output/'candidate'/path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding='utf-8', newline='\n')
        patch.append(f'diff --git a/{path} b/{path}\n')
        if not originals[path]: patch.append('new file mode 100644\n')
        patch.extend(difflib.unified_diff(originals[path].splitlines(keepends=True), text.splitlines(keepends=True),
            fromfile='a/'+path if originals[path] else '/dev/null', tofile='b/'+path))
    (args.output/'shannon-backend.patch').write_text(''.join(patch),encoding='utf-8',newline='\n')
    (args.output/'reference-hashes.json').write_text(json.dumps({name:hashlib.sha256((args.reference/name).read_bytes()).hexdigest()
        for name in ('skillAgent.ts','MinebotCompanionBody.ts','CompanionBodyClient.ts')},indent=2)+'\n',encoding='utf-8')
    print(f'Created local backend candidate and patch: {args.output.resolve()}')

if __name__ == '__main__': main()
