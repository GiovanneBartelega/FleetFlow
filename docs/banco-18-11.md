# Banco de 18/11: consultas agregadas e massa de 12 meses

Os dois itens que vieram incompatíveis (`docs/sprint-18-11/`) foram refeitos em cima do schema atual
(migrations 001 a 007). **Nenhum schema novo, nenhuma tabela ou coluna alterada**: só arquivos novos.

## Arquivos novos

| Arquivo | O que é |
|---|---|
| `database/008_consultas_agregadas_18_11.sql` | 8 views com as consultas agregadas (só `CREATE OR REPLACE VIEW`; pode reexecutar) |
| `database/009_massa_12_meses_18_11.sql` | Massa sintética de 12 meses (~10 s; reexecutar não duplica) |
| `database/rollback/009_remover_massa_12_meses.sql` | Remove tudo o que a 009 criou (Ids com prefixo `f1ee7f10-`) |
| `database/conferencia/04_views_agregadas_vs_tabelas.sql` | Views × SQL direto nas tabelas, por regime |
| `database/conferencia/05_integridade_massa_12_meses.sql` | Integridade de viagens, rotas e abastecimentos |

## Ordem de execução

`001`, `002`, `003` (opcionais, como antes), `004`, `005`, `006`, `007`, **`008`**, **`009`**.
Com o banco `fleetflow` selecionado. A 009 é para desenvolvimento e homologação, não para produção.

## Como cada coisa foi adaptada ao schema atual

| O pacote recebido assumia | O schema integrado tem | Uso nas novas migrations |
|---|---|---|
| `KmRodados` em `viagens` | não existe | `HodometroFinal - HodometroInicial` |
| `DataFim`, status `Concluida` | `DataFechamento`, `Status = 'Encerrada'` | idem |
| colunas de rota diferentes | `rotas(Nome, Origem, Destino, DistanciaEstimadaKm)` | idem |
| abastecimento sem vínculo | `abastecimentos.MovimentacaoId` (1 para 1 com a despesa) | valor, litros, veículo e viagem iguais aos da despesa |

## Views da 008 (granularidade mensal, regimes `caixa` e `competencia`)

`vw_fin_mensal_chaves` (etapa 1: agrega `movimentacoes` sozinha), `vw_rentabilidade_mensal_veiculo`,
`vw_rentabilidade_mensal_rota`, `vw_rentabilidade_mensal_motorista`, `vw_km_mensal_veiculo`,
`vw_combustivel_mensal_veiculo`, `vw_custo_km_mensal_veiculo`, `vw_viagem_resultado` (uma linha por viagem).

Mesmas regras de `src/services/relatorios.js`: movimentação ativa com categoria ativa; veículo =
`COALESCE(movimentação, viagem, dívida)`; custo variável = despesa ligada a viagem; `Mes` no formato
`AAAA-MM`. Linhas com `VeiculoId`/`RotaId`/`MotoristaId` nulos são custos não alocados (salários, avulsas).
A API continua consultando as tabelas para períodos em dias; as views servem a análise mensal, BI e conferência.

## Massa da 009

80 veículos, 90 motoristas (1 por veículo + 10 reservas), 40 rotas, 6.597 viagens (6.591 encerradas, 6 abertas),
32.750 movimentações (3 inativas, 2.331 pendentes), 15.382 abastecimentos (1.376 de pátio, sem viagem),
418 ordens de serviço, 40 financiamentos com todas as parcelas. Janela: 01/11/2025 a 31/10/2026.
Cobre os caminhos dos relatórios: receita por viagem, pedágio e combustível ligados à viagem, seguro, impostos,
manutenção preventiva (a cada 30.000 km) e corretiva, parcelas, salários sem veículo, movimentações inativas,
pendentes em atraso, 6 veículos em viagem e 3 em manutenção, licenciamento e seguro vencidos ou a vencer.

## Validação (MySQL 8.0.46, banco novo, 001 a 009 em sequência)

- A 009 roda em ~9 s e uma segunda execução não insere nada. O rollback zera tudo.
- `conferencia/05`: 0 problemas (nenhuma viagem sobreposta no veículo ou no motorista, hodômetro encadeado, abastecimento = despesa em valor/litros/veículo/viagem, parcelas completas, nada pago no futuro). Para provar que a conferência detecta erro, 6 defeitos foram injetados e os 6 foram acusados.
- `conferencia/04`: 0 divergências nas 4 comparações. Também com sabotagem de uma view: acusou 160 linhas.
- Views × serviço real `gerarRelatorio` (veículo, rota, motorista, nos dois regimes): 1.368 comparações, 0 divergências.
- Tempo das views com filtro de regime e mês: 0,35 a 0,45 s; sem filtro, 0,6 a 1,6 s (80 veículos, 33 mil movimentações).

## Limites e o que ficou de fora

- **Dados sintéticos.** Provam volume e forma dos dados, não valores de negócio.
- A 009 não preenche `checklists_viagem` (as telas de checklist da viagem aparecem vazias para essas viagens) e `viagens.Status` só tem `Aberta`/`Encerrada`, então não há viagem cancelada.
- Testado só em MySQL 8.0.46 local. Não foi rodado no banco `fleetflow` da equipe.
- **Defeito existente na API, fora deste escopo:** `GET /api/relatorios` com `agrupar=mes` falha. Em `src/services/relatorios.js`, a consulta de abastecimentos usa `data_ref`, coluna que só existe na CTE de movimentações (erro `Unknown column 'data_ref'`). Os outros agrupamentos funcionam.
- Esta entrega não mexe em arquivos existentes. O `docs/VALIDACAO-18-11.md` ainda diz que a massa e o SQL agregado estão "incompatíveis"; vale atualizar essa linha.
