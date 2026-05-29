-- Ajuste de configuração (plano seed) para compatibilizar limites do plano PILOTO
-- com o template CONSUMA_REST_V1 (10 mesas + 1 QR principal => 11 QR codes planejados).
--
-- Regra: não reduz limite existente; apenas eleva o teto mínimo necessário.

update planos
set max_qr_codes = greatest(max_qr_codes, 20),
    updated_at = now()
where codigo = 'PILOTO';

