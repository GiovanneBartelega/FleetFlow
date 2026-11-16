# Entrega — Gabriel Assis: massa de 12 meses para provar o desempenho

**Resultado em uma linha:** um gerador determinístico cria, em ≈10 s, 12 meses de operação de uma frota (34.915 movimentações, 6.583 viagens, 17.551 abastecimentos) num banco isolado, e um medidor chama a API real, cronometra 24 cenários e dá o veredito contra a meta de 2 s. A massa passa na conferência do projeto (**0 divergências, 0 problemas de integridade**).

O medidor é o que prova a entrega do Luis Felipe ([entrega-luis-sql-agregado-indices.md](entrega-luis-sql-agregado-indices.md)); esta entrega cobre os dados e a prova.

---

## 1. O que foi entregue

| Arquivo | Para que serve |
|---|---|
| [scripts/massa/gerador.js](../../scripts/massa/gerador.js) | Gerador **puro** (sem banco): mesma semente, mesma massa. Respeita as regras de integridade do schema e da conferência. |
| [scripts/massa/gerar-massa.js](../../scripts/massa/gerar-massa.js) | `npm run massa`: recria um banco `*_perf`, aplica o schema original, carrega a massa em lotes numa transação e atualiza as estatísticas do otimizador. `--conferir` roda a conferência ao final. |
| [scripts/massa/medir.js](../../scripts/massa/medir.js) | `npm run massa:medir`: sobe a API (Express + autenticação) numa porta efêmera e cronometra os relatórios por HTTP. Aplica a meta, grava JSON e compara com uma medição anterior. |
| [test/massa.test.js](../../test/massa.test.js) | 14 testes do gerador, sem MySQL (rodam sempre). |
| [docs/sprint/medicoes/](medicoes/) | Resultados brutos das medições (1× e 5×, antes e depois dos índices). |

## 2. O que a massa contém

Janela padrão: os **12 meses cheios anteriores a hoje** (aqui, 01/11/2025 a 31/10/2026), semente 42.

| Tabela | 1× (padrão) | 5× (`--escala=5`) |
|---|---:|---:|
| veículos | 80 | 400 |
| motoristas (usuários) | 90 | 450 |
| rotas | 40 | 40 |
| viagens | 6.583 | 36.236 |
| movimentações | 34.915 | 176.192 |
| abastecimentos | 17.551 | 81.631 |
| ordens de serviço | 426 | 1.991 |
| dívidas (financiamentos) | 37 | 209 |

Como é composta (para exercitar **todos** os caminhos dos relatórios):

- **Viagens** em sequência por veículo (nunca duas ao mesmo tempo), 4% canceladas, hodômetro encadeado, km = hodômetro final − inicial, motoristas sem sobreposição quando possível.
- **Receita:** um frete por viagem concluída, vencimento 15/30/45 dias após a entrega, ≈93% pago.
- **Custo ligado à viagem:** pedágio e um abastecimento a cada ≈550 km, com preço do diesel variando ao longo do ano.
- **Custo do veículo sem viagem:** seguro mensal, impostos trimestrais, manutenção (preventiva ao cruzar 30.000 km, corretiva ocasional, cada uma com sua movimentação) e abastecimentos de pátio (sem viagem).
- **Dívidas:** financiamento em metade da frota, com **todas** as parcelas (pagas e a vencer, 36/48/60) e `NumeroParcela` de 1 a N.
- **Custo sem veículo** (salários dos motoristas, despesas avulsas): cai em "custos não alocados".
- **Pendentes** (≈6%) para o regime de competência; **movimentações inativas** (1 a cada 25 despesas avulsas) para o filtro `Ativo`.
- Tudo que vence depois de `--hoje` fica `Pendente`; nada é "pago no futuro".

## 3. Como usar

```bash
npm run massa -- --hoje=2026-11-16                       # banco fleetflow_perf, 12 meses, 1×
npm run massa -- --db=fleetflow5x_perf --escala=5        # estresse (≈30 s)
npm run massa -- --conferir                              # gera e já roda a conferência nos dois regimes
npm run massa:medir -- --db=fleetflow_perf               # mede 24 cenários; exit 1 se algum passar de 2 s
```

Opções: `--meses`, `--inicio`, `--hoje`, `--escala`, `--semente`. Para reproduzir **exatamente** a mesma massa, fixe `--hoje` (ele decide o que está pago ou pendente) e a semente.

**Segurança:** o banco informado é **apagado e recriado**, então o comando só aceita nomes terminados em `_perf` (`fleetflow` e `fleetflow_test` são recusados). Nada é escrito fora dele.

O medidor mostra por cenário o tempo da 1ª execução (frio), p50, p95 e o pior; o veredito usa o **pior** (incluindo o frio). Códigos de saída: `0` tudo dentro da meta · `1` algum estourou · `3` erro.

## 4. A prova

1. **A massa é consistente.** `npm run conferencia` (SQL escrito à mão × service do app) sobre os 12 meses, nos dois regimes: **1.969 comparações cada, 0 divergências, 0 verificações de integridade com problema.** Isso vale também com as consultas novas e os índices do Luis aplicados.
2. **O volume dá trabalho de verdade.** Sem índices novos, a 5× os relatórios levam **3,5–5,7 s** e 22 de 24 cenários estouram os 2 s. A 1× (o volume de referência) o pior caso era 836 ms, já dentro da meta: a massa de 5× é que mostra até onde a abordagem aguenta.
3. **O medidor mede a API real**, não só o service: autenticação, rota, serialização e leitura do corpo entram na conta.
4. Resultados antes/depois dos índices e do SQL agregado: veja a seção 4 de [entrega-luis-sql-agregado-indices.md](entrega-luis-sql-agregado-indices.md).

## 5. Testes

`npm test` inclui `test/massa.test.js` (14 testes, sem banco): determinismo e escala; janela de 12 meses; ids/placas/e-mails únicos e placa no padrão do `CHECK`; `Pago` sempre com `DataPagamento` e nunca no futuro; chaves estrangeiras resolvíveis; viagem concluída com km coerente; veículo nunca em duas viagens ao mesmo tempo; abastecimento e OS com o mesmo valor da movimentação (e sem duplicar); dívidas com parcelas completas; `HodometroAtual` nunca abaixo do maior hodômetro; cobertura de todos os caminhos dos relatórios.

Um desses testes pegou um defeito real do próprio gerador durante o desenvolvimento: as movimentações inativas dependiam de sorte e podiam não aparecer em frotas pequenas. Agora são determinísticas.

## Limites

- **Os dados são sintéticos.** Fretes, preços e consumos seguem faixas plausíveis, mas não substituem a distribuição real da operação (sazonalidade, concentração de clientes, etc.). A prova é de **volume e de forma** dos dados, não de valores de negócio.
- A massa é regenerada do zero a cada execução (não faz carga incremental) e o ritmo de crescimento é linear com `--escala`.
- Os números absolutos dependem da máquina (aqui, MySQL 9.2 local) e variam ±10–20% entre execuções; por isso o medidor repete cada cenário e mostra p50, p95 e o pior.
- O gerador reaproveita o divisor de scripts SQL de `test/bancoTeste.js` (já usado pelos testes de conferência) para carregar o schema.
