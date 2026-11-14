# Integração de 04/11

Esta entrega parte da integração de 28/10 e une, sem duplicação, as alterações compatíveis da branch do banco ao back-end de frota.

## Funcionalidades incluídas

- Cálculo de quilômetros rodados ao encerrar uma viagem.
- Atualização segura do hodômetro em viagem, abastecimento e manutenção preventiva.
- Regras e alertas de manutenção preventiva, licenciamento e seguro em `GET /api/alertas`.
- CRUD de abastecimentos, filtros, totais, exclusão lógica e sincronização por `syncVersion`.
- Geração transacional de despesa em movimentações para cada abastecimento.

## Banco de dados

Não foi copiada uma segunda pasta de banco nem uma segunda definição de abastecimentos. A pasta `database` da integração principal foi preservada e recebeu somente a migration `006_abastecimentos_alertas_04_11.sql`.

Em uma instalação existente, execute os scripts na ordem numérica: `001`, `004`, `005` e `006`. Os arquivos `002` e `003` são dados de teste/demonstração.
