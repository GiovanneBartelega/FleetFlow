# FleetFlow integração de 28 de outubro

Este pacote parte da integração de 21 de outubro e consolida o que pôde ser validado das entregas recebidas para 28 de outubro.

## Executar a API

1. Copie `.env.example` para `.env` e informe as credenciais do MySQL.
2. Execute as migrations na pasta `database`, incluindo `005_rotas_viagens_checklist_28_10.sql`.
3. Execute `npm ci` e depois `npm start`.
4. Rode os testes com `node --test "test/**/*.test.js"`.

## Abrir o Android

Abra a pasta no Android Studio e configure **Gradle JDK** como JDK 17 ou 21. A configuração que exigia Java 25 foi removida porque é incompatível com o Gradle 8.11.1.

Consulte `docs/MERGE.md` para saber exatamente o que foi integrado e `docs/VALIDACAO.md` para o resultado das verificações.
