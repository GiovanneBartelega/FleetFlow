# FleetFlow integrado para 14 de outubro de 2026

Projeto Android em Kotlin e Jetpack Compose, preservando o front-gabriel e reunindo as alterações das quatro versões anexadas. A API ativa é a Node.js/Express com MySQL que veio na versão de Dados, completada para a entrega financeira de 14/10. Leia `docs/MERGE.md` para a origem de cada parte.

## Preparar o banco

Requisitos: Node.js 20 ou superior, MySQL 8.0.16 ou superior (testado em 8.4.11), Android Studio com SDK 35 e JDK 17.

No MySQL Workbench, abra e execute `database/001_schema.sql`. Ele cria o banco, tabelas, índices, triggers de versão e os quatro perfis. O SQL financeiro mencionado no README original não estava nos anexos; este script foi construído a partir dos contratos da API.

Para testar sem Google, execute também, nesta ordem:

1. `database/002_test_users.sql` — quatro contas de demonstração, uma por perfil.
2. `database/003_demo_financeiro.sql` — lançamentos fictícios de agosto, setembro e outubro de 2026.

Os scripts de demonstração são opcionais. Sem contas já cadastradas, a primeira conta Google validada vira Administrador; os próximos usuários aguardam aprovação. Em uma instalação com usuários de teste, o administrador é `admin.teste@fleetflow.com`.

Alternativa com Docker: defina `MYSQL_ROOT_PASSWORD` e execute `docker compose up -d`. O schema é aplicado na primeira criação do volume. Configure no `.env` a mesma senha para `DB_PASSWORD`. Nunca apague um volume com dados para atualizar o schema.

## Iniciar a API

Na pasta raiz do projeto:

```bash
npm ci
```

Copie `.env.example` para `.env`. Defina a conexão MySQL e substitua `JWT_SECRET` por um segredo aleatório:

```bash
node -e "console.log(require('crypto').randomBytes(48).toString('hex'))"
npm start
```

No Windows, copie com `Copy-Item .env.example .env` no PowerShell. No Linux, `cp .env.example .env`.

- API e banco: http://localhost:3000/health
- Swagger: http://localhost:3000/docs/
- Contrato OpenAPI: http://localhost:3000/openapi.json

Para os quatro botões de teste do app, use `DEV_LOGIN=true` somente em desenvolvimento e execute os seeds de usuários. Em produção, o servidor ignora essa opção.

Para Google real, crie/configure suas credenciais OAuth, preencha `GOOGLE_CLIENT_IDS` com o Web Client ID e substitua `default_web_client_id` em `app/src/main/res/values/strings.xml` pelo mesmo ID. Cadastre também o package `com.fleetflow.mobile` e a impressão SHA do certificado Android no cliente OAuth Android. Credenciais reais não fazem parte deste pacote.

Usuário de banco da API: conceda SELECT, INSERT, UPDATE e DELETE em `fleetflow.*`. O DELETE físico é usado somente para remover comprovantes; entidades de negócio têm exclusão lógica. Execute schema e triggers com uma conta de administração do banco.

## Abrir o aplicativo

Abra a pasta raiz `FleetFlow-integrado` no Android Studio e aguarde o Gradle Sync. Use JDK 17, SDK 35 e Build Tools 35.0.0. O Android Studio cria `local.properties` com o caminho do SDK da sua máquina.

O emulador usa `http://10.0.2.2:3000/`. Para celular físico, no `gradle.properties` local ou em `~/.gradle/gradle.properties`, configure:

```properties
FLEETFLOW_API_URL=http://IP_DO_COMPUTADOR:3000/
```

Celular e computador precisam acessar a mesma rede; permita a porta 3000 no firewall local. Mantenha a barra `/` no fim da URL. Recompile após mudar a URL.

O modo de teste aparece apenas no build debug. Faça login, abra Finanças e crie um lançamento. Categorias e formas de pagamento usam os cadastros da API. O formulário permite vencimento, pagamento e comprovante por câmera/galeria. A imagem é comprimida antes de ser enviada. Lançamentos e cadastros confirmados só aparecem após sucesso na API; falhas são exibidas na tela. O financeiro desta entrega exige conexão.

## Entrega até 14/10

| Data | Entrega integrada |
|---|---|
| 23/09 | Google, JWT, refresh rotativo, logout, primeira conta administradora e aprovação |
| 30/09 | Quatro perfis, permissões na API, abas por perfil, motorista inicia em Viagens, gestão de usuários |
| 07/10 | CRUD de categorias e pagamentos, exclusão lógica, proteção de registros de sistema, schema e seeds |
| 14/10 | CRUD de receitas/despesas, busca, filtros, paginação, status/data, comprovante e saldo atual |

Inclui também financiamento parcelado: informar forma de pagamento ao criar dívida gera todas as parcelas na mesma transação. O vencimento mantém o dia original, ajustando meses mais curtos. O plano não pode ser editado quando possui parcelas; cada parcela pode ser gerenciada como movimentação. Sem `formaPagamentoId`, a API cria apenas a dívida, mantendo compatibilidade com o contrato anterior.

O saldo atual soma receitas pagas e subtrai despesas pagas até `dataAte`; saldo previsto também inclui todas as pendências ativas, inclusive vencidas. O resumo de entradas/saídas da lista considera todo o filtro, não apenas a página visível. Valores monetários são strings decimais na API e centavos no formulário.

Veículos (21/10), viagens, frota completa, sincronização offline do motorista, dashboards completos e relatórios continuam como etapas futuras do documento. As abas correspondentes do front foram preservadas; não são funcionalidades concluídas em 14/10.

## Validar

```bash
npm test
# Em um banco de TESTE vazio, já inicializado com 001_schema.sql:
node scripts/smoke-mysql.mjs
# Compilar o Android:
./gradlew :app:assembleDebug
# Windows:
.\gradlew.bat :app:assembleDebug
```

O smoke usa validação Google simulada e cria dados fictícios. Não execute no banco usado pela equipe. Ele recusa uma base que já tem usuários. Veja `docs/VALIDACAO.md` para os resultados desta entrega.

## Leitura técnica

- `src/` e `test/`: API ativa e testes.
- `app/`: aplicativo Android integrado.
- `database/`: inicialização e demonstração MySQL.
- `docs/openapi.json`: contrato dos endpoints.
- `legacy/backend-kotlin/`: backend Kotlin original preservado para consulta; não iniciar junto com a API ativa.
- `legacy/front-rbac-original.zip`: fonte integral da branch motorista, para consultar a implementação anterior das telas.

A divisão de tarefas original está em `docs/Divisao-de-tarefas.docx`. O merge de conteúdo está concluído neste pacote; publicação no GitHub não foi realizada, pois não foi fornecido um repositório de destino.
