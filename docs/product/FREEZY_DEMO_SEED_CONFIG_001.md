# FREEZY_DEMO_SEED_CONFIG_001

Data: 2026-07-06
Escopo: seed/config controlado do Demo FREEZY para `CONSUMA_PONTO_V1` com QR Publico.
Base backend: `backend/test-infra-stabilization-001` em `fe7622e3f2c41c8382d08a3fcb18abb64b30dd2c`.
Base frontend: `ui/test-infra-stabilization-001` em `0e9969883d29c417710e60a47d494dc0bf327c59`.

## 1. Objectivo

Preparar dados minimos, consistentes e auditaveis para executar o Demo FREEZY em sandbox/local, sem implementar ordem de pagamento expiravel, confirmacao de pagamento, entrega/finalizacao, PDV, caixa, KDS, gateway ou alteracoes visuais.

O seed FREEZY cria a base para a proxima fase: implementar a ordem de pagamento com tempo de expiracao apos o aceite do pedido.

## 2. Tenant FREEZY

Tenant:
- Nome comercial: `FREEZY`.
- Slug: `freezy-demo`.
- Tenant code: `FREEZY_DEMO`.
- Tipo: `VENDEDOR_RUA`.
- Estado: ativo.

O tenant e criado pelo mesmo mecanismo de Business Template usado pelo sandbox demo, com idempotencia por slug.

## 3. Template

Template operacional:
- Provisionamento: `CONSUMA_PONTO_V1`.
- Persistencia esperada: `CONSUMA_PONTO` com `templateVersion = 1`.

O seed nao mistura FREEZY com `CONSUMA_REST_V1`, Delivery, Service, KDS ou PDV.

## 4. Unidade

Unidade principal:
- Nome: `FREEZY Principal`.
- Estado: ativa.
- Uso: ponto/balcao principal do demo.

O template cria a unidade base e o seed a normaliza para o nome oficial do Demo FREEZY.

## 5. Operador

Operador:
- Nome: `Operador FREEZY`.
- Email/login: `operador.freezy@consuma.local`.
- Telefone: `+244930000303`.
- Senha temporaria local: `FreezyDemo@2026`.

Tratamento de senha:
- A senha e apenas de sandbox/local e fica no seed demo desativado por padrao.
- O seed so roda quando `consuma.sandbox.demo-seed.enabled=true`, normalmente via `CONSUMA_SEED_DEMO=true`.
- Nao e senha de producao e nao deve ser reutilizada em ambientes reais.

## 6. Permissoes

O template exige um owner tecnico para provisionar o tenant. Para o demo, o mesmo usuario fica com:

- `TENANT_OWNER`: vinculo tecnico criado pelo template.
- `TENANT_OPERATOR`: permissao operacional explicita do demo.

Permissoes cobertas:
- ver pedidos;
- aceitar pedido quando `allowedActions` permitir;
- rejeitar pedido quando o contrato existente permitir;
- ficar preparado para futura confirmacao de pagamento e entrega, sempre governadas pelo backend.

## 7. Cardapio

O cardapio FREEZY fica publicado ao fim do seed.

O seed desativa itens genericos criados pelo template PONTO e deixa ativo apenas o cardapio controlado do demo:

- categorias: `Gelados`, `Bebidas`, `Combos`;
- produtos: seis itens amigaveis para apresentacao;
- sem dependencia de imagens externas.

## 8. Produtos

| Produto | Categoria | Preco | Disponivel | Visivel no cardapio publico | Observacao |
|---|---|---:|---:|---:|---|
| Gelado de Baunilha | Gelados | 1200.00 AOA | Sim | Sim | Produto principal do demo |
| Gelado de Chocolate | Gelados | 1300.00 AOA | Sim | Sim | Produto principal do demo |
| Gelado de Morango | Gelados | 1300.00 AOA | Sim | Sim | Produto principal do demo |
| Agua | Bebidas | 500.00 AOA | Sim | Sim | Bebida simples |
| Refrigerante | Bebidas | 800.00 AOA | Sim | Sim | Bebida simples |
| Combo FREEZY | Combos | 3000.00 AOA | Sim | Sim | Dois gelados e uma bebida |

## 9. QR Publico

QR:
- Nome: `QR Publico FREEZY`.
- Tipo fisico/operacional: QR principal de unidade (`UNIDADE_ATENDIMENTO`).
- URL esperada: `/q/{token}` no frontend publico.
- Tenant: `FREEZY`.
- Unidade: `FREEZY Principal`.
- Mesa: nenhuma.

Origem resultante esperada:
- Como o QR nao tem mesa, o pedido publico deve ser tratado como `QR_PRINCIPAL` pelo dominio atual.
- Para o demo, este e o QR Publico/Cardapio Publico aprovado.

## 10. Turno

Turno:
- Nome: `Turno Demo FREEZY`.
- Unidade: `FREEZY Principal`.
- Estado: `ABERTO`.
- Tipo: `DIARIO`.
- Aberto por: `Operador FREEZY`.
- Idempotencia: se ja existir turno `ABERTO` ou `EM_FECHO` para tenant/instituicao/unidade, o seed reutiliza e nao cria duplicado.

O seed nao desliga a regra de turno obrigatorio.

## 11. Parametro de expiracao

Parametro preparado:
- `consuma.demo.freezy.payment-order-expiration-minutes=${CONSUMA_FREEZY_PAYMENT_ORDER_EXPIRATION_MINUTES:10}`.

Valor demo:
- `10` minutos.

Onde fica:
- `src/main/resources/application-sandbox.properties`.

Uso atual:
- Apenas configuracao/documentacao.

Uso previsto:
- Proxima fase de ordem de pagamento expiravel.

## 12. Dados fora do escopo

O seed FREEZY nao cria:

- pedido publico;
- pedido pago;
- ordem de pagamento;
- pagamento;
- gateway fake;
- estorno;
- dados fiscais;
- PDV;
- caixa completo;
- KDS;
- delivery;
- service.

## 13. Como executar seed

O seed roda apenas no profile `sandbox` e com a flag explicitamente ativada.

Exemplo local:

```bash
SPRING_PROFILES_ACTIVE=sandbox CONSUMA_SEED_DEMO=true ./mvnw spring-boot:run
```

ou usando o comando Maven/ambiente equivalente do projeto.

## 14. Como validar seed

Validador automatizado:

```bash
mvn -Pit -Dit.test=SandboxDemoSeedRunnerIT verify
```

Comandos amplos de backend desta fase:

```bash
mvn -DskipTests compile
mvn test
mvn -Pit verify
```

## 15. Como aceder ao demo

Dados de desenvolvimento:

- Login operador: `operador.freezy@consuma.local`.
- Senha temporaria local: `FreezyDemo@2026`.
- Tenant: `FREEZY`.
- QR publico: consultar o token do QR `QR Publico FREEZY` no banco/log/endpoint administrativo aplicavel e abrir `/q/{token}`.

Credenciais reais de producao nao devem usar estes valores.

## 16. Riscos

- A senha temporaria e aceitavel apenas em sandbox/local; em ambiente compartilhado deve ser sobrescrita por fluxo seguro.
- O QR final depende do token gerado pelo servico de QR; a URL exata precisa ser coletada no ambiente do demo.
- A ordem de pagamento expiravel ainda nao e implementada nesta fase.
- Confirmacao de pagamento e entrega/finalizacao permanecem para prompts seguintes.
- O seed antigo de REST/PONTO sandbox ainda cria pedidos demonstrativos; o seed FREEZY, especificamente, nao cria pedido, ordem nem pagamento.

