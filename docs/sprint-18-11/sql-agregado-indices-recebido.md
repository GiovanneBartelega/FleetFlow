# Entrega — Luis Felipe: SQL agregado e índices (meta: resposta < 2 s)

**Escopo:** os relatórios `GET /api/relatorios/rentabilidade` e `GET /api/relatorios/custo-km`.
**Resultado em uma linha:** com 12 meses de dados da frota de referência (80 veículos, ≈35 mil movimentações) o pior caso caiu de **836 ms para 452 ms** e a consulta típica de 12 meses de **≈620 ms para ≈300 ms**; com a frota 5× maior (estresse) o tempo caiu de **3,5–5,7 s para 1,5–2,0 s**, no limite da meta (ver [Limites](#limites-e-riscos-leia-antes-de-apresentar)).

A massa usada na medição é a da entrega do Gabriel Assis ([entrega-gabriel-massa-12-meses.md](entrega-gabriel-massa-12-meses.md)).

---

## 1. O que foi entregue

| Arquivo | Para que serve |
|---|---|
| [src/queries/relatorios.sql.js](../../src/queries/relatorios.sql.js) | As três consultas dos relatórios, reescritas em **duas etapas** (agrega primeiro, junta depois). Mesmas exportações e mesmos resultados. |
| [db/indices-relatorios.sql](../../db/indices-relatorios.sql) | Migração com os 5 índices novos (só `CREATE INDEX`, cada um comentado com o filtro que atende). |
| [scripts/perf/aplicar-indices.js](../../scripts/perf/aplicar-indices.js) | `npm run perf:indices` aplica a migração de forma idempotente; `-- --remover` desfaz; `-- --simular` só mostra. |
| [scripts/perf/explain.js](../../scripts/perf/explain.js) | `npm run perf:explain` roda `EXPLAIN` nas 30 variações de consulta e acusa se algum plano deixou de usar o índice esperado. |
| [test/indices.test.js](../../test/indices.test.js) | 10 testes: migração, forma das consultas, resultado idêntico com e sem índices, planos. |
| [docs/sprint/medicoes/](medicoes/) | JSONs das medições antes/depois (1× e 5×). |

Não há mudança de contrato da API, de coluna, de dado nem do schema original (`banco-de-dados versao final.sql` continua intacto).

## 2. Diagnóstico: por que não bastava criar índices

Medido a 5× o volume (176 mil movimentações), com `EXPLAIN ANALYZE`:

1. **O MySQL ignorava o filtro de status/data.** A consulta antiga começava por `categorias` (9 linhas) e entrava em `movimentacoes` pelo índice de categoria. Resultado: lia **todas** as movimentações (≈1,7 s só nesse passo) e descartava na tabela o que estava fora do período. Testei índices novos *sem* mexer na consulta: o otimizador nem os usava.
2. **Um JOIN por linha da tabela grande** (viagens, dívidas, e uma busca em `ordens_servico` por movimentação).
3. **Combustível varria 100% das movimentações** e sondava `abastecimentos` para cada uma, embora só metade seja abastecimento.

## 3. O que mudou

### 3.1 SQL agregado em duas etapas

1. **Etapa 1:** agrega a tabela grande **sozinha**, sobre um índice que cobre todas as colunas lidas (sem ir à tabela), agrupando pelas chaves cruas (categoria, viagem, dívida, veículo). Sem JOIN, o MySQL é obrigado a usar o índice do filtro.
2. **Etapa 2:** só então junta `categorias`, `viagens` e `dívidas` sobre o resultado **já agregado** e agrupa pela chave do relatório. Só junta o que a dimensão pedida usa (por viagem, sem filtros, não junta viagens nem dívidas).

Detalhes que importam:

- **Ordens de serviço:** as movimentações *com* OS e *sem* OS viram dois ramos de `UNION ALL` (JOIN × anti-join). Os ~99% sem OS deixam de pagar uma busca por linha (966 → 524 ms na etapa 1 a 5×). A regra anti-fan-out continua: a OS é pré-agregada por `MovimentacaoId`.
- **Combustível:** percorre o intervalo no índice de `movimentacoes` e busca os litros no índice de `abastecimentos`; o custo acompanha o tamanho do período (1 mês a 5×: ≈105 ms; a alternativa de partir sempre de `abastecimentos` custaria ≈570 ms, porque varre todos, e só vence em 12 meses: ≈1,0 s contra ≈1,3 s).
- **Mês:** a etapa 1 agrupa pelo inteiro `AAAAMM` e a formatação `AAAA-MM` acontece só sobre os grupos.
- **Rótulos de viagem:** com mais de 2.000 chaves, lê todos os rótulos em vez de um `IN (?)` gigante (34 mil ids: 789 → 325 ms).
- **Regra de ouro preservada:** cada fonte continua agregada separadamente e juntada no service por chave; nenhum JOIN de movimentações com viagens/abastecimentos para somar depois. Todo valor do usuário continua entrando por `?`.

### 3.2 Índices (todos cobrindo)

| Índice | Atende | Tamanho a 1× / 5× |
|---|---|---|
| `ix_mov_caixa` (`Status, DataPagamento, Ativo, CategoriaId, ViagemId, DividaId, VeiculoId, Valor`) | movimentações, regime caixa | 6,6 / 28,8 MB |
| `ix_mov_competencia` (`DataVencimento, Ativo, CategoriaId, …, Valor`) | movimentações, regime competência | 6,6 / 28,8 MB |
| `ix_os_movimentacao` (`MovimentacaoId, Ativo, Tipo`) | classificação preventiva/corretiva | 0,1 / 0,2 MB |
| `ix_viagens_concluidas` (`Status, Ativo, DataFim, VeiculoId, RotaId, MotoristaId, KmRodados, Hodômetros`) | km rodados | 1,5 / 7,6 MB |
| `ix_abast_relatorio` (`MovimentacaoId, Ativo, VeiculoId, ViagemId, Litros`) | litros | 3,5 / 14,7 MB |

Medido no passo de leitura de `movimentacoes` (regime caixa, 5×): **1.718 ms → 190 ms** com o índice (±9×).

**Hints de otimizador.** As consultas usam `/*+ INDEX(m ix_mov_caixa) */` porque, mesmo com o índice criado, o otimizador preferia o índice antigo (`idx_movimentacoes_status_vencimento`) por uma estimativa de custo equivocada. Diferente de `FORCE INDEX`, o hint em comentário **não dá erro** se o índice não existir (só um *warning*): um ambiente que ainda não rodou a migração continua funcionando, apenas mais lento. Isso é testado.

## 4. Resultados

Medição pela **API real** (HTTP, autenticação e serialização incluídas), pior tempo entre 3–5 repetições + a execução fria, nas combinações de 12 meses × 5 dimensões × 2 regimes × 2 endpoints. Reproduzível com o roteiro da seção 5. Máquina de desenvolvimento (MySQL 9.2 local), então use os números como **ordem de grandeza e proporção**.

| | Antes | Depois |
|---|---|---|
| **1× (80 veículos, 34.915 movimentações)** — pior caso de 12 meses | 836 ms | **452 ms** |
| 1× — mediana dos cenários de 12 meses | 617 ms | **297 ms** |
| 1× — 1 mês, regime competência | 261 ms | **29 ms** |
| 1× — cenários dentro da meta de 2 s | 24 de 24 | **24 de 24** |
| **5× (400 veículos, 176.192 movimentações)** — mediana de 12 meses | 4.250 ms | **≈1.900 ms** |
| 5× — pior caso (dimensão *viagem*) | 5.725 ms | **2.577–2.928 ms** |
| 5× — pior caso sem a dimensão *viagem* | 4.919 ms | **2.120–2.347 ms** |
| 5× — 1 mês, regime competência | 1.932 ms | **≈160–200 ms** |
| 5× — cenários dentro da meta | 2 de 24 | **17–18 de 24** (duas execuções) |

Todas as linhas brutas estão em [medicoes/](medicoes/) (`1x-antes.json`, `1x-depois.json`, `5x-antes.json`, `5x-depois.json`, `5x-depois-repeticao.json`).

## 5. Como reproduzir e operar

```bash
npm run massa -- --db=fleetflow_perf --hoje=2026-11-16              # massa 1× (≈10 s)
npm run massa:medir -- --db=fleetflow_perf --saida=antes.json        # ANTES (schema original, sem ix_*)
npm run perf:indices -- --db=fleetflow_perf                          # aplica a migração
npm run perf:explain -- --db=fleetflow_perf                          # planos usam os ix_* esperados?
npm run massa:medir -- --db=fleetflow_perf --comparar=antes.json     # DEPOIS, com a variação por cenário
```

Para o "antes" fiel é preciso o código antigo das consultas (commit `8556732`); os JSONs em `medicoes/` já guardam essa medição. Para um estresse maior, use `--escala=5` no `npm run massa`.

**Em cada ambiente real (inclusive o banco de desenvolvimento `fleetflow`)**, rodar uma vez `npm run perf:indices`. É idempotente e só cria índices (não altera dados). Desfazer: `npm run perf:indices -- --remover`.
**Criação de banco novo:** o schema original não traz os `ix_*`; aplique a migração depois dele (o banco de teste já faz isso automaticamente).

## 6. Como sabemos que os números continuam certos

- **Equivalência com a consulta original:** 125 comparações a 1× (2 regimes × 5 dimensões × 5 combinações de filtro, para movimentações, km e combustível) e 50 a 5×: **0 diferenças** (resultado idêntico, linha a linha).
- **Conferência independente** (`npm run conferencia`: SQL escrito à mão × service) sobre os 12 meses da massa final, com as consultas novas e os índices: **1.969 comparações em cada regime, 0 divergências, 0 problemas de integridade**.
- **Suíte de testes:** a fixture calculada à mão (`fixtures/esperado.json`) passa com as consultas novas e com os índices aplicados. `test/indices.test.js` garante o mesmo resultado **com e sem** a migração.

## Limites e riscos (leia antes de apresentar)

1. **A meta de 2 s tem folga no volume de referência, não no estresse de 5×.** A 1× o pior caso é 0,45 s (4,4× de folga). A 5× os cenários típicos ficam em 1,5–2,0 s e **6–7 dos 24 passam de 2 s**: sempre a dimensão *viagem* (2,2–2,9 s) e, por pouco (2,0–2,1 s), alguns relatórios de 12 meses por veículo ou rota. A fronteira de ≈4–5× o volume de referência é onde a abordagem atual (agregar na hora) se esgota.
2. **Dimensão `viagem` em 12 meses é pesada por natureza:** a 5× devolve ≈35 mil grupos (≈20 MB de JSON). O custo restante é de serialização e processamento em Node, não de SQL. Recomendação (não implementada, muda o contrato): limitar essa dimensão a períodos curtos ou paginar.
3. **Custo dos índices:** +18 MB sobre ≈16 MB de dados a 1× (os dois índices de `movimentacoes` somam 13 MB sobre uma tabela de 9,6 MB) e dois índices a mais a atualizar a cada `INSERT`/`UPDATE` de movimentação. Para este sistema, que lê relatórios muito mais do que grava, vale a troca; se a escrita virar gargalo, o `ix_mov_competencia` é o candidato a sair (só atende o regime competência).
4. **O filtro por veículo/rota/motorista não é empurrado para a etapa 1** (a regra de veículo é `COALESCE(movimentação, viagem, dívida)`, e reescrevê-la com segurança é delicado). 12 meses para 1 veículo a 5× levam ≈1,3–1,6 s; a 1× ≈0,2 s.
5. **Próximo passo se o volume passar de 5×:** tabela de resumo mensal por veículo/categoria (pré-agregada), atualizada na gravação ou por rotina noturna. Foge ao escopo desta entrega (traz risco de números defasados) e só se justifica com dados reais de volume.
6. As medições foram feitas numa máquina de desenvolvimento, com variação de ±10–20% entre execuções (por isso as faixas). Numa instância de produção os valores absolutos serão outros.
