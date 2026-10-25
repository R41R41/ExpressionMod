export interface ExpressionTurn { speaking(): void; finish(): void; }
export interface ExpressionBridge { start(): void; stop(): void; beginTurn(speakerUuid: string): ExpressionTurn; }
export class MinebotExpressionBridge implements ExpressionBridge {
  constructor(bot: any, options: { baseUrl: string; token: string; fetcher?: typeof fetch });
  start(): void; stop(): void; beginTurn(speakerUuid: string): ExpressionTurn;
}
