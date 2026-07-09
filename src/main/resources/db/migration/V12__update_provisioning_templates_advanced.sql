-- CONSUMA Tenant Core (V12)
-- Advanced provisioning templates: mesas + QR por mesa settings
-- Date: 2026-05-16

-- Update existing templates (keep id/codigo stable)
update provisioning_templates
set configuracao_json = $$
{
  "unidadeAtendimentoDefault": { "nome": "Balcão Principal", "tipo": "BAR" },
  "categoriaDefault": { "nome": "Geral", "slug": "geral" },
  "qrPrincipal": { "nome": "Consuma aqui", "tipo": "BALCAO" },
  "criarMesas": false,
  "quantidadeMesas": 0,
  "criarQrPorMesa": false
}
$$
where codigo = 'VENDEDOR_RUA';

-- Default para PILOTO (maxQrCodes=10): 9 mesas + 1 QR principal = 10 QRs.
update provisioning_templates
set configuracao_json = $$
{
  "unidadeAtendimentoDefault": { "nome": "Salão Principal", "tipo": "RESTAURANTE" },
  "categoriaDefault": { "nome": "Geral", "slug": "geral" },
  "qrPrincipal": { "nome": "QR Salão Principal", "tipo": "UNIDADE_ATENDIMENTO" },
  "criarMesas": true,
  "quantidadeMesas": 9,
  "prefixoMesa": "Mesa",
  "criarQrPorMesa": true
}
$$
where codigo = 'RESTAURANTE_SIMPLES';

update provisioning_templates
set configuracao_json = $$
{
  "unidadeAtendimentoDefault": { "nome": "Balcão Principal", "tipo": "BAR" },
  "categoriaDefault": { "nome": "Geral", "slug": "geral" },
  "qrPrincipal": { "nome": "QR Balcão", "tipo": "BALCAO" },
  "criarMesas": false,
  "quantidadeMesas": 0,
  "criarQrPorMesa": false
}
$$
where codigo = 'BAR';

update provisioning_templates
set configuracao_json = $$
{
  "unidadeAtendimentoDefault": { "nome": "Atendimento", "tipo": "CAFETERIA" },
  "categoriaDefault": { "nome": "Geral", "slug": "geral" },
  "qrPrincipal": { "nome": "QR Loja", "tipo": "TENANT_GERAL" },
  "criarMesas": false,
  "quantidadeMesas": 0,
  "criarQrPorMesa": false
}
$$
where codigo = 'LOJA';

update provisioning_templates
set configuracao_json = $$
{
  "unidadeAtendimentoDefault": { "nome": "Balcão Evento", "tipo": "EVENTO" },
  "categoriaDefault": { "nome": "Geral", "slug": "geral" },
  "qrPrincipal": { "nome": "QR Evento", "tipo": "EVENTO" },
  "criarMesas": false,
  "quantidadeMesas": 0,
  "criarQrPorMesa": false
}
$$
where codigo = 'EVENTO';

