# Conferência dos números do app

Prova que o que os relatórios (`GET /api/relatorios/rentabilidade` e `/custo-km`) mostram bate com o que o
banco contém. O runner chama o **service** do app (a função, sem HTTP) e compara, métrica a métrica e chave a
chave, com consultas SQL escritas à mão em [`sql/`](sql/), independentes do código do service
(`src/queries/relatorios.sql.js`).

## Como rodar

```bash
# contra o banco configurado no .env (somente leitura: só faz SELECT)
npm run conferencia -- --inicio=2026-10-01 --fim=2026-10-31 [--regime=caixa|competencia] [--saida=arquivo.json]
```

- `--regime` padrão: `caixa`. O período é `[inicio, fim + 1 dia)`.
- Imprime a tabela `métrica | chave | app | banco | diferença | status`, as verificações de integridade e salva
  `conferencia-resultado.json` (ignorado pelo git).
- O banco precisa estar no schema de `banco-de-dados versao final.sql`; se faltar tabela/view o runner avisa quais.

**Código de saída:** `0` tudo certo · `1` há DIVERGÊNCIA entre app e banco (ou erro de uso) · `2` os números batem,
mas há dados inconsistentes nas verificações de integridade marcadas como problema.

Tolerâncias: R$ 0,01 (dinheiro e litros), 0,001 (custo/km, km/l, preço do litro), exato para km e quantidades.

## Testes (fixture determinística)

```bash
npm test          # inclui test/conferencia.test.js
```

Os testes de conferência **recriam o banco `fleetflow_test`** (DROP + CREATE) com o schema + `fixtures/conferencia-seed.sql`
e comparam o service com `fixtures/esperado.json`, calculado à mão (cada número tem o cálculo em comentário `_...`).
O nome usado é `DB_NAME` se terminar em `_test`; senão `DB_TEST_NAME`; senão `fleetflow_test`. Nomes que não terminam
em `_test` são recusados, então o banco de desenvolvimento nunca é tocado. Sem MySQL acessível, esses testes são pulados.

Para rodar o CLI sobre a fixture: `DB_NAME=fleetflow_test npm run conferencia -- --inicio=2026-10-01 --fim=2026-10-31`
(sai com código 2 de propósito: a fixture contém duas viagens com km incompleto).

## Como interpretar uma divergência

1. Veja a linha `DIVERGE` e a chave (placa, categoria, id). Sempre há dois suspeitos: o service ou a consulta manual.
2. Abra o `.sql` manual correspondente (`sql/<metrica>.sql`) e a query do service (`src/queries/relatorios.sql.js`)
   e rode as duas no cliente MySQL com os mesmos parâmetros.
3. Causas comuns: regime errado (caixa filtra `DataPagamento`, competência `DataVencimento`), movimentação inativa,
   categoria inativa, viagem concluída sem `DataFim`, ou uma movimentação com dois abastecimentos/OS (fan-out).
4. Divergências de 0,01 ou menos em dinheiro e 0,001 em razões são aceitas; acima disso, é erro real.

## `vw_resumo_financeiro` não é bug

A view soma **todas** as movimentações ativas, de qualquer `Status` e de qualquer data, inclusive as sem veículo. O relatório
filtra por regime e período. A diferença é esperada e a conferência a reconcilia: `view − fora do escopo = app` por categoria
(`sql/integridade/fora_do_escopo_por_categoria.sql`). Só é divergência se sobrar resíduo.

## Verificações de integridade (`sql/integridade/`)

Cada uma devolve as linhas problemáticas. **Problema** (código 2): valor do abastecimento/OS ≠ movimentação; viagem concluída
com km inconsistente ou sem hodômetro final; `HodometroAtual` abaixo do maior hodômetro de viagem/abastecimento; dívida com
parcelas duplicadas, faltando ou em quantidade diferente. **Informativas**: saídas sem veículo (entram em `custosNaoAlocados`)
e movimentações sem `DataVencimento` (invisíveis no regime de competência).
