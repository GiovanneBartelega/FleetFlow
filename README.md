# FleetFlow integração de 18 de novembro

Esta é a integração do aplicativo FleetFlow até a entrega de 18/11. Ela reúne o app Android, a API Node.js, as migrations do MySQL e os indicadores de dashboard.

## Funcionalidades incluídas

- Login, autenticação e perfis de acesso.
- Cadastros financeiros, veículos, ordens de serviço, rotas, viagens e abastecimentos.
- Viagens com checklist, validações de hodômetro, sincronização offline e resolução de conflitos.
- Alertas de manutenção e abastecimento.
- Detalhe financeiro da viagem, rentabilidade e custo por km.
- Home com seletor de período, gráfico de receitas e despesas, KPIs, despesas por categoria, ranking de veículos, previsão de caixa e alerta de saldo negativo.

## Requisitos

- Node.js 20 ou superior.
- MySQL 8.0.16 ou superior.
- Android Studio com **Gradle JDK 21** selecionado.

## Configurar o banco de dados

1. Crie ou selecione o schema `fleetflow` no MySQL Workbench.
2. Execute os arquivos da pasta `database` nesta ordem:

   1. `001_schema.sql`
   2. `002_test_users.sql` (opcional)
   3. `003_demo_financeiro.sql` (opcional)
   4. `004_veiculos_21_10.sql`
   5. `005_rotas_viagens_checklist_28_10.sql`
   6. `006_abastecimentos_alertas_04_11.sql`
   7. `007_indices_relatorios_18_11.sql`
   8. `008_consultas_agregadas_18_11.sql`
   9. `009_massa_12_meses_18_11.sql` (somente para desenvolvimento e testes)

3. Para apagar somente os dados sintéticos criados pela migration 009, execute `database/rollback/009_remover_massa_12_meses.sql`.

## Executar a API

1. Copie `.env.example` para `.env`.
2. Preencha as credenciais do MySQL e um `JWT_SECRET` com pelo menos 32 caracteres.
3. No terminal, na pasta do projeto, execute:

   ```powershell
   npm ci
   npm start
   ```

4. A API fica disponível em `http://localhost:3000`.
5. Para executar os testes automatizados:

   ```powershell
   npm test
   ```

## Endpoints de indicadores

- `GET /api/indicadores/kpis`
- `GET /api/indicadores/fluxo-caixa`
- `GET /api/indicadores/despesas-por-categoria`
- `GET /api/indicadores/rankings`
- `GET /api/indicadores/previsao-caixa`
- `GET /api/indicadores/alertas-saldo`

Os detalhes de parâmetros e permissões estão em `docs/backend-18-11.md`.

## Abrir o aplicativo Android

1. Abra esta pasta no Android Studio.
2. Em **File > Settings > Build Tools > Gradle**, selecione um **JDK 21** em Gradle JDK.
3. Aguarde a sincronização do Gradle.
4. Execute **Build > Clean and Assemble Project with Tests** ou rode o app no emulador.
5. O emulador usa `http://10.0.2.2:3000/` para acessar a API que está rodando no computador.

## Testes de conferência do banco

Depois de executar a massa de dados, rode:

- `database/conferencia/04_views_agregadas_vs_tabelas.sql`
- `database/conferencia/05_integridade_massa_12_meses.sql`

Os arquivos verificam as views agregadas, viagens, hodômetros, abastecimentos e integridade da massa de testes.
