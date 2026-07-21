# Feature 000 — Primeira fatia vertical

## Objetivo

Provar o fluxo técnico e de negócio mínimo sem construir módulos completos.

## História

Como proprietária, quero cadastrar um serviço e um cliente, criar um agendamento e visualizá-lo, para substituir o primeiro registro feito em caderno ou planilha.

## Inclui

- autenticação real da proprietária;
- organização vinculada;
- serviço com nome e duração;
- cliente com nome e telefone;
- agendamento com início e fim;
- lista simples de agendamentos;
- bloqueio de sobreposição;
- auditoria da criação;
- interface mínima;
- testes multiempresa.

## Não inclui

- calendário visual completo;
- recorrência;
- remarcação;
- cancelamento;
- lista de espera;
- secretária;
- relatórios;
- importação;
- WhatsApp;
- agenda pública.

## Critérios de aceite

1. Usuária não autenticada não acessa endpoints de negócio.
2. Proprietária autenticada acessa somente sua organização.
3. Serviço exige nome e duração válida.
4. Cliente exige nome e telefone normalizado.
5. Possível telefone duplicado gera aviso conforme regra definida.
6. Agendamento exige cliente, serviço, início e fim válidos.
7. Agendamento sobreposto é rejeitado pela aplicação e pelo banco.
8. Agendamento da Organização A nunca aparece para a Organização B.
9. Criação de cliente, serviço e agendamento gera auditoria.
10. O fluxo pode ser demonstrado no painel de ponta a ponta.
11. Todos os checks passam em clone limpo.

## Ordem sugerida

1. sessão e autenticação;
2. contexto da organização;
3. serviço;
4. cliente;
5. agendamento e constraint;
6. lista no frontend;
7. auditoria;
8. E2E.
