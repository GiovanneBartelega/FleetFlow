# Validação da integração de 18 11

## Base usada

- Integração corrigida de 11/11: app Android, API Node e migrations 001 a 006.
- Pacote de app de 18/11: detalhe da viagem, cache local do painel e estados de carregamento.
- Pacote de dados de 18/11: índices para relatórios, documentação de consultas agregadas e massa de doze meses.

## O que entrou sem duplicação

- O app de 18/11 foi aplicado sobre o app de 11/11. Foram incluídos o detalhe de viagem, o cache Room `cache_paineis`, os estados de carregamento, os testes de cache e as correções da base Android.
- Foram removidas as cópias fora do pacote `com.fleetflow.mobile` e o `TokenAuthenticator.kt` obsoleto, conforme a entrega do app.
- A migração `database/007_indices_relatorios_18_11.sql` adiciona os índices compatíveis com as migrations 001 a 006. Ela não recria tabelas nem dados.
- O backend Kotlin antigo e a pasta `legacy` não foram mantidos: a API ativa desta integração é a API Node em `src/`, evitando duas implementações do mesmo backend.

## Conferência contra o cronograma de 18 11

| Frente | Situação | Evidência |
| --- | --- | --- |
| App: home com cards, seletor de período e gráficos | Completo | A Home oferece 7 dias, 30 dias e 6 meses, gráfico de receitas x despesas, estados de carregamento, erro e dados em cache. |
| App: cache local e estados de carregamento | Completo | `CachePainel.kt`, `PainelRepository.kt`, `EstadosPainel.kt`, `PainelTest.kt` e `RegrasCacheTest.kt`. |
| App: detalhe de viagem e vínculo financeiro | Completo | `DetalheViagemScreen.kt` e a atualização de `ListaViagensScreen.kt`. |
| Dados: índices para relatórios | Completo para o schema integrado | `database/007_indices_relatorios_18_11.sql`. |
| Dados: SQL agregado e massa de 12 meses | Completo | As migrations `008` e `009` foram refeitas para o schema integrado, com rollback e consultas de conferência. |
| API: KPIs, fluxo de caixa, despesas por categoria, rankings, previsão de 12 meses e alerta de saldo negativo | Completo | Rotas em `src/routes/indicadores.js`, regras em `src/services/indicadores.js` e testes em `test/indicadores.test.js`. |

## Decisão de banco

O arquivo de banco recebido, `banco-de-dados versao final.sql`, é um schema completo alternativo. Ele não pode ser executado por cima das migrations 001 a 006 porque altera nomes e regras de tabelas que a API atual usa. Para a integração permanecer funcional, use as migrations sequencialmente:

1. `001_schema.sql`
2. `002_test_users.sql`
3. `003_demo_financeiro.sql`
4. `004_veiculos_21_10.sql`
5. `005_rotas_viagens_checklist_28_10.sql`
6. `006_abastecimentos_alertas_04_11.sql`
7. `007_indices_relatorios_18_11.sql`
8. `008_consultas_agregadas_18_11.sql`
9. `009_massa_12_meses_18_11.sql` (somente desenvolvimento e homologação)

Antes de adotar o schema alternativo, a equipe precisa ajustar e testar junto a API Node, principalmente rotas, viagens e relatórios.

## Back end incluído depois da primeira integração

Os endpoints da entrega de 18/11 foram implementados e registrados em `docs/backend-18-11.md`. A Home recebida foi integrada com seletor de período, gráfico, cache local e estados de carregamento. A consulta mensal da API de relatórios também foi corrigida para não usar uma coluna inexistente no cálculo de abastecimentos.
