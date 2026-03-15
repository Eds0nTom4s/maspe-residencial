# Fluxo de Recarga (Carregamento Local de Fundos)

Bem-vindos equipa Frontend! 👋 

Até que a integração com o gateway de pagamentos externo corporativo (Ex: AppyPay, Multicaixa) fique totalmente homologada e concluída, nós aprovisionámos a API para que o carregamento das carteiras `FundoConsumo` aconteça **localmente de forma administrativa**. 

Ou seja, o garçom recebe o valor em numerário ou TPA físico da loja, lê o QR Code da mesa do cliente no seu Tablet, e aciona a nossa API de recarga para depositar o equivalente em saldo digital pré-pago naquela mesa.

Este guia rápido explica como invocar as rotas.

---

## Passo 1: O Garçom Lê o QR Code da Sessão

Cada mesa/cliente recebe uma Sessão encapsulada num **Token Único** (QR Code).
Toda a operação financeira ao balcão é feita invocando esse Token. 

Para confirmar qual é o fundo antes de introduzir dinheiro (opcional mas recomendado para a UI do Tablet do Atendente/Garçom), pode-se consultar o estado atual:

**ENDPOINT:** `GET /api/fundos/{qrCodeSessao}`  
**HEADERS:** `Authorization: Bearer <JWT_DO_GARCOM_OU_GERENTE>` *(Requer Role ADMIN, GERENTE ou ATENDENTE)*

**RESPOSTA ESPERADA (HTTP 200 OK):**
```json
{
  "success": true,
  "message": "Fundo encontrado",
  "data": {
    "saldoAtual": 0.00,
    "ativo": true,
    "sessaoId": 14,
    "qrCodeSessao": "MESA-05-UUID-1234"
  }
}
```

---

## Passo 2: Executar a Recarga do Fundo (Bypass AppyPay)

O Cliente pagou 5000 Kz fisicamente. O Garçom vai "injetar" esse crédito de volta no ecossistema (Fundo de Sessão) chamando a rota de `recarregar`. O motor recalcula tudo em formato transacional ACID.

> ⚠️ ATENÇÃO PRÉVIA: Este endpoint de mutação de saldo tem a restrição de role `@PreAuthorize("hasAnyRole('GERENTE', 'ADMIN')")`. O JWT utilizado na requisição tem de pertencer a um Back-Office user autorizado e não ao telemóvel do próprio Cliente, para evitar inflação fraudulenta de saldos. 

**ENDPOINT:** `POST /api/fundos/{qrCodeSessao}/recarregar`  
**HEADERS:** 
- `Authorization: Bearer <JWT_DO_GERENTE_OU_ADMIN>` 
- `Content-Type: application/json`

**FORMATO DO BODY (JSON):**
```json
{
  "valor": 5000.00,
  "observacoes": "Recarga em Numerário Física"
}
```
*(Nota: O valor não pode ser negativo zero nem inferior ao Minimo da Casa. O campo `observacoes` é excelente para o Front-end passar de onde veio o dinheiro "TPA Caixa 1", etc).*

**RESPOSTA ESPERADA (HTTP 200 OK):**
```json
{
  "success": true,
  "message": "Recarga concluída. Novo saldo: 5.000,00 Kz",
  "data": {
    "tipo": "CREDITO",
    "valor": 5000.00,
    "saldoAnterior": 0.00,
    "saldoNovo": 5000.00,
    "observacoes": "Recarga em Numerário Física"
  }
}
```

---

## Passo 3: Fechar Conta (Frontend do Cliente)

Após o passo 2 ser concluído, se o cliente der reload na App dele (injetando a sua própria JWT de Cliente desta vez neste endpoint abaixo), verá o seu poder de compra atualizado.

`GET /api/fundos/{qrCodeSessao}/saldo` -> Devolverá `5000.00`.

O Cliente agora pode ir no Catálogo, escolher Pratos, e invocar `/api/pedidos/cliente` e o nosso backend vai automaticamente deduzir destes `5000.00` recém-injetados com tranquilidade. Nenhuma chamada complexa de pagamento externa é necessária nesta fase V1!

---

## Passo Extra: Auto-Recarga do Cliente (Mock AppyPay)

Para a App do Cliente (onde ele mesmo clica no botão "Carregar Saldo"), também adicionámos um endpoint exclusivo para a "role" `CLIENTE` que auto-deduz a sessão aberta dele, assim nem precisa de passar qual é o QR Code na URL. O backend já sabe quem é!

**ENDPOINT:** `POST /api/fundos/cliente/recarregar`  
**HEADERS:** 
- `Authorization: Bearer <JWT_DO_CLIENTE>` 
- `Content-Type: application/json`

**FORMATO DO BODY (JSON):**
```json
{
  "valor": 10000.00,
  "observacoes": "Recarga Teste Mock AppyPay (Cliente APP)"
}
```

Usando isto na App do Cliente, vocês cimentam o fluxo de recarga end-to-end do lado do Consumidor.

---

### Resumo dos Códigos de Erro Esperados ao Recarregar:
- `400 BAD REQUEST`: `"Valor de recarga deve ser positivo"` ou malformado.
- `401 UNAUTHORIZED`: Token expirado do Garçom/Gerente.
- `403 FORBIDDEN`: O utilizador não tem Role de GERENTE nem ADMIN.
- `404 NOT FOUND`: "Fundo de consumo não encontrado para o QR Code". A sessão dessa mesa pode já ter sido encerrada.
