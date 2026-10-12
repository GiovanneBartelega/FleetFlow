# Registro do merge

Os anexos são snapshots ZIP, sem histórico Git. A integração foi feita por comparação de arquivos e resolução manual dos contratos, sem presumir uma ancestralidade que não veio nos arquivos. O pacote final é a versão unificada para importar no repositório da equipe.

| Origem | Conteúdo incorporado |
|---|---|
| FleetFlow-main | Base do projeto, autenticação original e recursos compartilhados |
| FleetFlow-front-gabriel | MainActivity, FleetFlowApp, tema claro/escuro, componentes, Finanças, estrutura Room e navegação de quatro perfis |
| FleetFlow-feat-rbac-motorista | Categorias, formas de pagamento, financiamento, câmera/galeria, FileProvider e regra de motorista iniciar em Viagens |
| FleetFlow-banco-de-dados-gabriel | API Express/MySQL, testes, contrato português de usuário/perfil, telas de login/usuários integradas, seed de quatro contas |

## Resoluções

- Main tinha arquivos em `java/ui/screens` e `java/com/fleetflow/mobile/ui/screens` declarando os mesmos pacotes. Mantida uma única cópia no pacote `com.fleetflow.mobile`.
- O front-gabriel esperava `user.role` em inglês; a API de Dados fornece `usuario.perfil.nome` em português. A navegação usa agora Administrador, Financeiro, GestorFrota e OperadorMotorista.
- A feature trazia dois perfis e repositórios locais em memória. O resultado mantém os quatro perfis e permissões aplicadas também no servidor. Admin e Financeiro editam cadastros/lançamentos; Gestor lê categorias e pagamentos; Motorista não acessa caixa.
- O layout, paleta, recursos de imagem e barra de abas do front-gabriel foram mantidos. As telas da feature usam o mesmo tema; seus callbacks gravam na API.
- Room e modelos locais foram mantidos para continuidade futura; os lançamentos financeiros desta entrega são persistidos pela API. Não é apresentada uma fila offline financeira incompleta.
- Backend Kotlin original guardado em `legacy/backend-kotlin`; a API ativa foi consolidada em Node para conservar o trabalho de Dados e seu contrato MySQL.
- SQL ausente no anexo de Dados reconstruído em `database/001_schema.sql`, com comprovantes e refresh tokens adicionados.
- Removidos caches, binários de build, configurações pessoais de IDE, caminhos locais e arquivos duplicados do pacote final.
- Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20 e JDK 17 configurados em conjunto; removida exigência de download automático do JDK 25. O wrapper e o Android Studio usam uma configuração portátil.

## Aplicação no GitHub da equipe

Faça backup/commit das alterações locais. Crie uma branch `integracao-14-10`, copie o conteúdo deste projeto para a raiz do repositório, revise `git diff`, faça commit e abra o PR. Não copie `.env`, `local.properties`, `node_modules` ou diretórios de build. `docs/merge-front.patch` contém o diff do app e configurações Android em relação ao snapshot main recebido; use-o apenas se a base ainda for exatamente aquele snapshot e confira `git apply --check` antes de aplicar. O ZIP integrado é a fonte final, incluindo também o backend.
