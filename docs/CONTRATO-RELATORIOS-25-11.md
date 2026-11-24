# Contrato para exportação de relatórios — 25/11

Os dois endpoints são protegidos pela permissão de leitura de `relatorios`.
Eles aceitam datas inclusivas no formato `AAAA-MM-DD`.

## Financeiro

```
GET /api/relatorios/financeiro?inicio=2026-11-01&fim=2026-11-30&regime=caixa
```

Filtros opcionais: `status` (`Pendente` ou `Pago`), `categoriaId`,
`formaPagamentoId`, `veiculoId`, `rotaId` e `motoristaId`.
O `regime` aceita `caixa` (padrão, data de pagamento e somente pagos) ou
`competencia` (data de vencimento).

```json
{
  "parametros": {
    "inicio": "2026-11-01",
    "fim": "2026-11-30",
    "regime": "caixa"
  },
  "totais": {
    "transacoes": 2,
    "receitas": 2500,
    "despesas": 780,
    "resultado": 1720
  },
  "transacoes": [
    {
      "id": "mov-1",
      "data": "2026-11-03",
      "descricao": "Frete Campinas",
      "categoria": "Frete",
      "tipo": "Entrada",
      "formaPagamento": "Pix",
      "valor": 2500,
      "status": "Pago"
    }
  ],
  "resumoPorCategoria": [
    {
      "categoriaId": "cat-frete",
      "categoria": "Frete",
      "tipo": "Entrada",
      "quantidade": 1,
      "valor": 2500
    }
  ]
}
```

## Logístico

```
GET /api/relatorios/logistico?inicio=2026-11-01&fim=2026-11-30&regime=caixa
```

Filtros opcionais: `veiculoId`, `rotaId` e `motoristaId`. Retorna uma
linha por viagem encerrada, já pronta para uma tabela de PDF ou CSV.

```json
{
  "parametros": {
    "inicio": "2026-11-01",
    "fim": "2026-11-30",
    "regime": "caixa"
  },
  "linhas": [
    {
      "id": "viagem-1",
      "data": "2026-11-03",
      "rota": "Varginha → Campinas",
      "veiculo": "ABC-1D24",
      "motorista": "Maria Silva",
      "kmRodados": 320,
      "receitas": 2500,
      "despesas": 780,
      "resultado": 1720,
      "custoPorKm": 2.4375,
      "litros": 40,
      "consumoMedio": 8
    }
  ],
  "totais": {
    "viagens": 1,
    "kmRodados": 320,
    "receitas": 2500,
    "despesas": 780,
    "litros": 40,
    "resultado": 1720,
    "custoPorKm": 2.4375,
    "consumoMedio": 8
  }
}
```
