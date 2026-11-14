# Integração de 28 de outubro

A base deste pacote é `FleetFlow-integracao-21-10`. Os ZIPs recebidos são snapshots, sem histórico Git utilizável; por isso a integração foi feita comparando o conteúdo com a base e preservando contratos já funcionais.

| Origem recebida | Avaliação | Resultado na integração |
|---|---|---|
| `FleetFlow-integracao-21-10 (1)` | Base definida pela equipe | Mantida como ponto de partida |
| `FleetFlow-gabriel-entregas front end 28-10` | Snapshot mais antigo do back-end: removia os endpoints de frota, rotas e viagens | Não aplicado, para não regredir a API |
| `Fleetflow-Douglas-entregas-28-10` | Não possui diferença de código-fonte em relação à base | Nenhuma cópia necessária |
| `FleetFlow-backend-28-10` | Não possui mudança no `src` do back-end; contém alterações de Android fora do escopo de rotas/viagens e arquivos duplicados | Não aplicado automaticamente |
| `FleetFlow-banco-de-dados-gabriel-28-10` | Trouxe os routers de checklist, mas sem migration e com modelo de rota incompatível com a base | Checklist adaptado ao contrato da integração e migration criada |

## Recursos consolidados

- CRUD protegido de rotas: criação, consulta, alteração, exclusão lógica e bloqueio de exclusão quando há viagens.
- Abertura e fechamento de viagens em transação, com bloqueio do veículo, estado do veículo e validação do hodômetro.
- Catálogo de itens do checklist e respostas por viagem, com observação obrigatória quando um item não está conforme.
- Fechamento bloqueado enquanto houver item obrigatório do checklist sem resposta conforme.
- Migration `database/005_rotas_viagens_checklist_28_10.sql`, com as tabelas `rotas`, `viagens`, `itens_checklist` e `checklists_viagem`, índices, triggers e itens iniciais.

## Aplicação no banco

No MySQL, execute uma única vez e nesta ordem: `database/001_schema.sql`, `database/004_veiculos_21_10.sql` e `database/005_rotas_viagens_checklist_28_10.sql`. Se a base já tinha as migrations 001 a 004, execute somente a 005.

## Observação sobre o aplicativo Android

As telas `RotasScreen` e `ViagensScreen` existem, mas o código recebido ainda usa dados de exemplo em memória. Portanto, elas não consomem a API e não gravam viagens no SQLite. Isso não foi considerado uma funcionalidade concluída da entrega de 28/10; a API e o banco, porém, estão preparados para a ligação do aplicativo.
