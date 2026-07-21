# ADR 0005 — Sessão e cookie

## Status

Aceito para implementação futura.

## Decisão

Usar sessão autenticada e cookie seguro, evitando JWT no `localStorage`.

## Consequências

- CSRF deve permanecer ativo;
- frontend e API devem preferencialmente operar no mesmo domínio;
- sessões devem poder ser revogadas.
