import { expect, test } from '@playwright/test';

/**
 * Fluxo crítico da Feature 000 (primeira fatia vertical): login, cadastro de serviço
 * e cliente, criação de agendamento e bloqueio de sobreposição — de ponta a ponta,
 * contra a API e o Postgres reais (não mockado).
 *
 * Pré-requisitos (ver docs/operations/local-development.md):
 * - `docker compose up -d` (Postgres com o seed de dev do perfil `local`);
 * - API rodando em :8080 com o perfil `local`;
 * - `ng serve` (ou `npm start`) rodando em :4300.
 */

const OWNER_EMAIL = 'dona@exemplo.test';
const OWNER_PASSWORD = 'TrocarSenha123!';

test('proprietária cadastra serviço, cliente e agendamento, e tem o conflito de horário bloqueado', async ({
  page
}) => {
  const unique = Date.now();
  const clientName = `Cliente E2E ${unique}`;
  const serviceName = `Serviço E2E ${unique}`;

  // O horário varia por execução (constraint de sobreposição é por organização inteira,
  // não por cliente/serviço), para não colidir com agendamentos de execuções anteriores.
  const anchor = Date.UTC(2030, 0, 1);
  const dayOffset = Math.floor(unique / 1000) % 3000;
  const appointmentDay = new Date(anchor + dayOffset * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const startAt = `${appointmentDay}T09:00`;

  await page.goto('/login');
  await page.getByLabel('E-mail').fill(OWNER_EMAIL);
  await page.getByLabel('Senha').fill(OWNER_PASSWORD);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.goto('/servicos');
  await page.getByLabel('Nome').fill(serviceName);
  await page.getByLabel('Duração (minutos)').fill('30');
  await page.getByRole('button', { name: 'Adicionar serviço' }).click();
  await expect(page.getByText(serviceName)).toBeVisible();

  await page.goto('/clientes');
  await page.getByLabel('Nome').fill(clientName);
  await page.getByLabel('Telefone').fill('(21) 98888-7777');
  await page.getByRole('button', { name: 'Adicionar cliente' }).click();
  await expect(page.getByText(clientName)).toBeVisible();

  await page.goto('/agenda');
  await page.getByLabel('Cliente').selectOption({ label: clientName });
  await page.getByLabel('Serviço').selectOption({ label: `${serviceName} (30 min)` });
  await page.getByLabel('Início').fill(startAt);
  await page.getByRole('button', { name: 'Criar agendamento' }).click();

  await expect(page.getByText(clientName).last()).toBeVisible();
  await expect(page.getByText(serviceName).last()).toBeVisible();

  // Mesmo horário novamente: deve ser bloqueado tanto pela aplicação quanto, em última
  // instância, pela constraint de exclusão do PostgreSQL (ADR 0006).
  await page.getByLabel('Cliente').selectOption({ label: clientName });
  await page.getByLabel('Serviço').selectOption({ label: `${serviceName} (30 min)` });
  await page.getByLabel('Início').fill(startAt);
  await page.getByRole('button', { name: 'Criar agendamento' }).click();

  await expect(page.getByText('Já existe um agendamento nesse horário.')).toBeVisible();
});
