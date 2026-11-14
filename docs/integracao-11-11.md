# Integração de 11 de novembro

Base: integração de 04/11.

Foram unidos os arquivos alterados do aplicativo, os endpoints e fórmulas de rentabilidade/custo por km, e as consultas manuais de conferência do banco. A definição de tabelas já existente foi mantida; não há uma segunda cópia do banco de dados.

## Testes

O back end possui 165 testes automatizados aprovados. Antes do teste integrado em Android, execute os scripts de `database` na ordem numérica e use as consultas de `database/conferencia` para comparar os valores do app/API com o MySQL.

## Contratos de sincronização ainda dependentes de banco

O front envia `Idempotency-Key` e prevê viagem em conflito para revisão. A persistência desses dois recursos exige migration específica e não foi inventada nesta integração para evitar dados de sincronização inconsistentes.
