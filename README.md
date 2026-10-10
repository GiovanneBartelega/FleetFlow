# FleetFlow API

API REST em Node.js + Express para o app Android (Kotlin) do FleetFlow. Fala com o MySQL
criado por `fleetflow_mysql_financeiro.sql`.

**Escopo atual:** login com Google (JWT), usuários e perfis, categorias, formas de pagamento,
dívidas e movimentações, com permissão por perfil. Veículos, rotas, viagens, manutenção,
abastecimentos e checklist ainda não têm rotas.

```
App Kotlin ──HTTPS/JSON──▶ API (Express) ──▶ MySQL
```

## Rodando

Requisitos: Node 20+ e MySQL 8.0.16+.

```bash
npm install
cp .env.example .env     # preencha JWT_SECRET, GOOGLE_CLIENT_IDS e os dados do banco
npm start                # ou: npm run dev
npm test                 # testes automáticos (não precisam de MySQL)
```

1. Rode `fleetflow_mysql_financeiro.sql` no Workbench (cria o banco, as seis tabelas e os seeds).
2. Crie um usuário de banco para a API. Como a exclusão é lógica (`Ativo = 0`), ele não precisa de `DELETE`:

```sql
CREATE USER 'fleetflow_api'@'%' IDENTIFIED BY 'troque-esta-senha';
GRANT SELECT, INSERT, UPDATE ON fleetflow.* TO 'fleetflow_api'@'%';
```

3. Gere o segredo do JWT:
   `node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"`.

## Login com Google

1. No Google Cloud, crie um **Web Client ID** e coloque em `GOOGLE_CLIENT_IDS`.
2. No app, peça o ID token usando esse mesmo Web Client ID (`setServerClientId(...)` no Credential Manager).
3. Envie o token para a API:

```
POST /api/auth/google
{ "idToken": "<token do Google>" }
```

Resposta `200`: `{ token, tokenType, expiresIn, usuario, permissoes }`. Mande
`Authorization: Bearer <token>` nas demais chamadas.

| Situação | Resposta |
|---|---|
| Conta ativa | `200` com token |
| Primeiro acesso | cria o usuário como `AguardandoAprovacao` e responde `403 ACCOUNT_PENDING` |
| Pré-cadastrada pelo admin ou pelo seed (`seed-…`/`pre-…`) | casa pelo e-mail, grava o ID real do Google e entra |
| Bloqueada / desativada | `403 ACCOUNT_BLOCKED` / `ACCOUNT_DISABLED` |
| E-mail ligado a outra conta Google | `409 EMAIL_IN_USE` |

O usuário é relido do banco a cada requisição: bloquear uma conta ou trocar o perfil vale
na hora, sem esperar o token expirar (caso de teste CT-04).

`GET /api/auth/me` devolve o usuário e a matriz de permissões do perfil, para o app montar a
navegação dinâmica:

```json
{ "permissoes": { "categorias": "L", "movimentacoes": null, "veiculos": "E" } }
```

`E` = ver e editar · `L` = somente leitura · `null` = sem acesso.

## Endpoints

Todos exigem token, exceto `/health` e `/api/auth/google`.

| Módulo | Rotas | Quem acessa |
|---|---|---|
| Usuários | `GET/POST /api/usuarios`, `GET/PATCH/DELETE /api/usuarios/:id`, `GET /api/perfis` | Administrador |
| Categorias | `GET/POST /api/categorias`, `GET/PATCH/DELETE /api/categorias/:id` | Admin e Financeiro editam; GestorFrota lê |
| Formas de pagamento | `GET/POST /api/formas-pagamento`, `GET/PATCH/DELETE /api/formas-pagamento/:id` | idem |
| Dívidas | `GET/POST /api/dividas`, `GET/PATCH/DELETE /api/dividas/:id` | Admin e Financeiro |
| Movimentações | `GET/POST /api/movimentacoes`, `GET/PATCH/DELETE /api/movimentacoes/:id`, `POST /api/movimentacoes/:id/pagar` | Admin e Financeiro |

A matriz fica em `src/permissions.js`. **Ela foi derivada do modelo de dados e precisa ser
confirmada pelo grupo**; se mudar, é só editar esse arquivo. A mesma matriz está no roteiro de
teste de permissões e nos testes automáticos (`test/permissions.test.js`).

Listas de dívidas, movimentações e usuários são paginadas (`?page=1&pageSize=20`, máx. 100) e
respondem `{ data, page, pageSize, total, totalPages }`. Movimentações aceitam os filtros
`status`, `tipo`, `categoriaId`, `formaPagamentoId`, `dividaId`, `veiculoId`, `viagemId`,
`vencimentoDe/Ate`, `pagamentoDe/Ate` e `busca`, e trazem `totais: { entradas, saidas }` do filtro.
Categorias, formas de pagamento e perfis voltam inteiros em `{ data: [...] }`.

Exemplo:

```bash
curl -X POST http://localhost:3000/api/movimentacoes \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"valor":"1500.00","descricao":"Frete SP-MG","categoriaId":"<uuid>",
       "formaPagamentoId":"<uuid>","dataVencimento":"2026-10-30"}'
```

## Convenções

- **Dinheiro** trafega como string com 2 casas (`"1500.00"`); a API também aceita número. Não use `Double` no app para somar valores.
- **Datas** (`DATE`) em `AAAA-MM-DD`. **Carimbos** (`criadoEm`, `atualizadoEm`) em ISO 8601 UTC com `Z`.
- **Exclusão lógica:** `DELETE` marca `Ativo = 0` e devolve `204`.
- **Edição concorrente:** envie `syncVersion` no `PATCH` para detectar conflito; se outra pessoa alterou antes, volta `409 VERSION_CONFLICT`.
- **Registros de sistema** (`ehSistema = true`, como Frete e Pix) não podem ser alterados nem removidos por ninguém, nem pelo Administrador.
- **Erros** têm sempre o formato:

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "Dados inválidos.",
             "details": [{ "campo": "dataPagamento", "mensagem": "Informe a data de pagamento…" }] } }
```

Códigos principais: `UNAUTHENTICATED`/`INVALID_TOKEN` (401), `FORBIDDEN`, `ACCOUNT_*`, `SYSTEM_RECORD`
(403), `NOT_FOUND` (404), `DUPLICATE`, `IN_USE`, `VERSION_CONFLICT` (409), `VALIDATION_ERROR`,
`INVALID_REFERENCE` (400), `DB_UNAVAILABLE` (503).

## Testando permissões sem Google

Para executar o roteiro de teste sem depender de login Google, ligue `DEV_LOGIN=true` no `.env`
(só funciona fora de produção) e use:

```
POST /api/auth/dev-login   { "email": "teste.financeiro@exemplo.com" }
```

Ele devolve o mesmo token do login real, para qualquer usuário `Ativo` do banco.

## Pontos de atenção

- **Sem FK para veículos e viagens:** `VeiculoId` e `ViagemId` são aceitos, mas não verificados, porque essas tabelas não estão no banco atual. Quando existirem, rode os `ALTER TABLE` comentados no fim do SQL e adicione a checagem nas rotas.
- **`formas_pagamento.Nome` é `UNIQUE` mesmo entre inativas:** depois de excluir uma forma, o nome não pode ser reutilizado. Se isso incomodar, o ajuste é no banco (mesma técnica da coluna gerada de `categorias`).
- **Dívidas não geram parcelas sozinhas:** o app (ou uma rota futura) cria as movimentações com `dividaId` e `numeroParcela`.
- **`GoogleSubjectId` é `NOT NULL`:** por isso os pré-cadastros usam valores provisórios (`seed-…`, `pre-…`), trocados no primeiro login.
- Os testes usam um banco simulado. As consultas foram checadas contra a gramática do MySQL, mas **ainda não foram executadas em um MySQL real**: rode `npm start` contra o banco do grupo e passe pelos casos do roteiro.
