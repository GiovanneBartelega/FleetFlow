# Entrega de 18/11: app (Douglas + Gabriel Claro)

Base: `fleetflow-integrado-11-11` (e07b608). Neste pacote estão:

- a correção da base: o app dessa branch não compila (detalhes abaixo);
- a parte do Douglas de 14 e 15/11: detalhe da viagem com os lançamentos financeiros dela;
- a parte do Gabriel Claro de 18/11: cache local dos painéis e estados de carregamento.

Nenhum arquivo da API (`src/`, `test/`) nem do banco (`database/`) mudou. O ZIP do Douglas trazia dois arquivos da API de rotas, que ficaram de fora até o grupo decidir qual banco vale (seção 2).

O ZIP traz **só os arquivos que mudaram** em relação a `fleetflow-integrado-11-11`, nas mesmas pastas do repositório. É só copiar por cima e **apagar os 10 arquivos da lista "Apagar"**, no fim deste documento: o ZIP não consegue apagar arquivo.

## 1. Correção da base: o app da 11-11 não compila

Os 32 arquivos do ZIP de 11/11 entraram certos. Mas outros arquivos do app ficaram diferentes do que foi entregue: alguns voltaram à versão do primeiro commit do repositório (02/10), outros foram reescritos na integração, e o Gradle veio de um projeto novo do Android Studio. Este pacote devolve o app ao estado das entregas de 04/11 e 11/11, que compila e roda:

| O que estava na 11-11 | Problema | Como ficou |
|---|---|---|
| `app/build.gradle.kts` sem Room, KSP, WorkManager, ícones estendidos, desugaring e `BuildConfig` | Não compila: o banco do aparelho, a fila de sincronização e a URL da API dependem disso | Voltou o da entrega de 11/11 |
| Raiz do Gradle de um projeto novo: AGP 9.4.1, Kotlin 2.2.10, Gradle 9.6 e JDK 25 (o `libs.versions.toml` é o da branch `banco-de-dados-gabriel`) | O KSP do Room acompanha a versão do Kotlin do projeto (2.1.20) e não roda nessa combinação | Voltaram os arquivos de 04/11: AGP 8.9.2, Kotlin 2.1.20, KSP 2.1.20-1.0.32, Compose BOM 2025.04.01, Gradle 8.11.1 e JDK 21. Se o Android Studio sugerir atualizar o AGP, **não atualize** |
| `SyncWorker.kt` apagado e `SyncScheduler.kt` sem WorkManager | A fila de viagens offline parou de enviar em segundo plano e de tentar de novo a cada 15 minutos | Os dois voltaram |
| Login, autenticação e tema na versão do primeiro commit (02/10): `ApiClient` (porta 8080), `AuthApi`, `AuthInterceptor`, `UserApi`, `TokenManager`, `AuthorizationManager`, `AuthRepository`, `NetworkModels`, `LoginScreen`, `UsuariosScreen`, `Color`, `Theme` e `Type` | A API Node fica na porta 3000; sem a sessão salva (US15) e sem o modo escuro | Voltaram os de 11/11 |
| `FrotaDao`, `FrotaRepository`, `RegrasViagem`, `ChecklistViagemScreen` e `FecharViagemScreen` reescritos na integração | O hodômetro do veículo no aparelho podia voltar para trás; as mensagens do hodômetro saíram das da especificação, e o fechamento passou a aceitar hodômetro final igual ao inicial (a especificação bloqueia); o fechamento perdeu a confirmação de km alto | Voltaram os de 04/11 e 11/11, que o `ViagemRepository` e a fila usam |
| `RegrasViagemTest` apagado | Perdeu os testes das regras de viagem | Voltou |
| Cópias em `app/src/main/java/ui/screens/` e `app/src/main/java/data/MockData.kt`, fora do pacote `com.fleetflow.mobile` | Erro de declaração repetida | **Apagar** (lista no fim) |
| `TokenAuthenticator.kt` | Tinha saído em 21/10 e voltou com os arquivos do primeiro commit | **Apagar** |

## 2. Parte do Douglas (14 e 15/11): detalhe da viagem

### Entrou

- `DetalheViagemScreen` (novo): identificação, rota, veículo e motorista, hodômetro e o financeiro da viagem, com receitas, despesas, resultado e os lançamentos vinculados (`FinanceRepository.movimentacoesDaViagem`). Viagem que ainda não subiu para a API mostra que os lançamentos aparecem depois da sincronização.
- Botão "Ver detalhes" em cada viagem da aba Viagens (`ListaViagensScreen`).

### Mudou na junção

| No ZIP | Problema | Como ficou |
|---|---|---|
| `ListaViagensScreen` montada sobre a versão de 04/11 | Apagava o que entrou em 11/11: tela de pendências, ícone de sincronização, selo de conflito e faixa de sem conexão | Só as mudanças do detalhe entraram, na versão de 11/11 |
| O detalhe recebia a viagem como estava no toque | Se a viagem subisse para a API com a tela aberta, os lançamentos não apareciam | O detalhe lê a viagem do Room a cada mudança |
| Datas em ISO (`2026-11-09T23:56:00.000Z`), placa e hodômetro sem formato, falha de rede com a mensagem do OkHttp em inglês | Diferente da lista e difícil de ler | Mesmo formato da lista; sem rede, "Sem conexão com o servidor." |
| Card de receitas cor-de-rosa | O tema não definia o container do terciário, e ficava a cor padrão do Material 3 | O tema ganhou o verde das entradas para esse container, no claro e no escuro (`Theme.kt`, `Color.kt`) |

### Não entrou

- **13 arquivos do app iguais aos do ZIP de 07 e 08/11**: `FleetFlowApp`, `AppDatabase` (versão 4), `ViagensDao`, `ViagensEntities`, `ViagensApi`, `ViagensModels`, `ViagensRepository`, `RotasRepository`, `AbrirViagemScreen`, `RotasScreen`, `FinanceRepository`, `FinanceModels` e `FinancasScreen`. Eles já entraram na entrega de 11/11, com as correções do `docs/ENTREGA-11-11-APP.md` (as versões do ZIP apagavam as viagens feitas sem internet). `FinanceModels` e `FinancasScreen` já são iguais aos da 11-11.
- **API de rotas** (`src/mappers.js` e `src/routes/rotas.js`): o Douglas adaptou a API ao banco "versão final" da branch `banco-de-dados-gabriel`, em que a rota tem `CidadeOrigem`, `UfOrigem`, `CidadeDestino` e `UfDestino`. A 11-11 usa os scripts de `database/` (`005_rotas_viagens_checklist_28_10.sql`), em que a rota tem `Origem`, `Destino` e `Descricao`. Nesse banco, com os dois arquivos do ZIP, o cadastro de rotas e todas as consultas de viagens dão erro de coluna desconhecida. E o `mappers.js` do ZIP veio de uma versão anterior, sem o mapeamento de abastecimentos que o `src/routes/abastecimentos.js` importa: a API nem sobe. **O grupo precisa decidir qual banco vale.** Se for o "versão final", o `rotas.js` do Douglas pode entrar, e a mudança dele no `mappers.js` (rota e viagem) tem que ser juntada com o `mappers.js` da 11-11.

## 3. Cache local dos painéis

Especificação, Módulo 5, "Desempenho": *os resultados ficam em cache local com carimbo de hora, exibido como atualizado às HH:mm*.

- **Tabela `cache_paineis` no Room** (banco do aparelho na versão 6, migração 5→6 que só cria a tabela). Uma linha por painel e período: a resposta da API em JSON e a hora em que ela chegou. Uma tela de BI nova não precisa de tabela nova, só de uma chave.
- **Abre na hora**: a tela mostra o que está salvo e busca a versão nova por trás, sem apagar os números da tela.
- **Validade de 5 minutos**: voltar para a home dentro desse tempo (trocar de aba, girar o celular) não chama a API de novo. O botão Atualizar sempre busca.
- **Sem conexão**: ficam os números salvos, com a hora deles e o aviso "Sem conexão. Mostrando os números salvos no aparelho.".
- **Segurança** (especificação: *banco local limpo ao sair da conta*): sair da conta apaga o cache. Cada linha leva o id da conta: se a sessão expirar e outra pessoa entrar no mesmo celular, ela não vê os números da anterior, porque a API confere a permissão a cada leitura e o cache não.
- Painel que ninguém abre há 30 dias sai do aparelho.

Arquivos: `data/local/CachePainel.kt` (tabela e DAO), `data/local/AppDatabase.kt` (versão 6), `data/repository/RegrasCache.kt` (regras puras: validade, chave, carimbo, textos), `data/repository/PainelRepository.kt` (cache e a classe `Painel`), `data/repository/FinanceRepository.kt` (`buscarSaldo()`) e `MainActivity.kt` (conta dona do cache e limpeza ao sair).

## 4. Estados de carregamento

Especificação, "Feedback visual": *lista carregando: skeleton nos cards, e não tela em branco*; *falha de rede: Tentar de novo*; *gráfico sem dados mostra estado vazio explicando o que fazer*.

| Situação | O que a home mostra |
|---|---|
| Primeira vez, nada salvo, API ainda não respondeu | Skeleton pulsando no lugar do carimbo, dos 4 valores e do gráfico. Os cards não mudam de tamanho quando o número chega |
| Números salvos ou recém-chegados | "Atualizado às 18:42" (outro dia: "Atualizado em 14/11 às 18:42") e o botão Atualizar, que gira enquanto busca |
| Sem conexão, com números salvos | Os números salvos, a hora deles e o aviso com "Tentar de novo" |
| Sem conexão e nada salvo | Aviso "Sem conexão com o servidor. Os números aparecem quando a conexão voltar.", "—" nos cards (antes aparecia R$ 0,00, que parecia saldo zerado) e "Tentar de novo" |
| Erro da API | A mensagem da API no aviso, com "Tentar de novo" |
| Nenhuma receita ou despesa paga | Estado vazio no gráfico explicando o que fazer, com o botão "Abrir Finanças" |

Os componentes ficam em `ui/components/EstadosPainel.kt`: `rememberPainel`, `BlocoEsqueleto`, `CarimboAtualizacao`, `AvisoPainel` e `EstadoVazioPainel`. As cores saem do tema; a home atual, que usa cores fixas, passa as dela.

## 5. Para as telas do Douglas (home, seletor de período e gráficos)

Cada card, gráfico ou ranking vira um painel com a sua chave. O período entra na chave: trocar o período troca o painel, e cada período fica salvo separado.

```kotlin
val painel = rememberPainel(
    chave = RegrasCache.chave("bi/kpis", mapOf("inicio" to inicio, "fim" to fim)),
    tipo = KpisDto::class.java,   // lista: object : TypeToken<List<RankingDto>>() {}.type
) { BiRepository.kpis(inicio, fim) }   // chama a API e lança exceção se falhar

when (val estado = painel.estado) {
    EstadoPainel.Carregando -> BlocoEsqueleto(Modifier.fillMaxWidth().height(180.dp))
    is EstadoPainel.Dados -> {
        CarimboAtualizacao(estado.atualizadoEm, estado.atualizando, onAtualizar = { scope.launch { painel.carregar(forcar = true) } })
        estado.falha?.let { AvisoPainel(it, temDadosSalvos = true, onTentarDeNovo = { /* o mesmo do Atualizar */ }) }
        // desenha estado.valor
    }
    is EstadoPainel.Erro -> AvisoPainel(estado.falha, temDadosSalvos = false, onTentarDeNovo = { /* o mesmo do Atualizar */ })
}
```

A home desta entrega já usa isso com `GET /api/movimentacoes/saldo`. Quando os endpoints de BI da Bruna e da Giovanne estiverem prontos, é só trocar a chamada e a chave.

## 6. O que já foi testado

- **Testes unitários:** 29 novos. São 17 do `RegrasCacheTest` (validade, chave, carimbo e textos) e 12 do `PainelTest`, que usa o `Painel` de verdade com o Room trocado por um mapa em memória e cobre: primeira carga, validade, salvo vencido, sem conexão com e sem salvo, erro da API, Atualizar, falha ao atualizar, outra conta no mesmo aparelho, sair da conta, JSON inválido e descarte de 30 dias. Junto com os 32 que já existiam, os 61 passam. No Android Studio, o `PainelTest` rodou também com o Compose e o Gson do app: 12 de 12.
- **Android Studio:** compilou sem erros e instalou no emulador por cima da versão de 11/11. O banco local migrou da 5 para a 6 sem perder as viagens.
- **API desligada, nada salvo:** aviso de sem conexão, "—" nos cards e o gráfico explicando que os números aparecem quando chegarem. "Tentar de novo" mostrou o skeleton nos cards e no gráfico e voltou ao aviso.
- **Detalhe da viagem (com a parte do Douglas):** compilou e instalou. Na aba Viagens, "Ver detalhes" abre o detalhe com as datas, a placa e o hodômetro no formato da lista, e a seta volta para a lista. Com a API desligada, o detalhe abre normalmente com os dados do aparelho.
- **Ainda não feito no emulador:** a home e o financeiro do detalhe com a API ligada (passos 3 a 6 e 8 abaixo).

## Como testar

1. App instalado por cima da versão anterior: o banco local migra para a 6 sem apagar nada.
2. API desligada e primeira vez: aviso de sem conexão, "—" nos cards e "Tentar de novo".
3. API no ar (`npm start`): os valores aparecem com "Atualizado às HH:mm".
4. Trocar de aba e voltar: os números aparecem na hora, sem skeleton e sem nova chamada à API.
5. Desligar a API e tocar em Atualizar: continuam os números salvos, com a hora deles e o aviso "Sem conexão. Mostrando os números salvos no aparelho.".
6. Sair da conta e entrar de novo sem API: os números não aparecem mais, porque o cache foi apagado.
7. Viagens › "Ver detalhes": identificação, rota, veículo, motorista e hodômetro da viagem; a seta volta para a lista.
8. Com a API no ar, lançar em Finanças uma despesa com o campo "Viagem" preenchido: ela aparece no financeiro do detalhe dessa viagem, com receitas, despesas e resultado.

## Arquivos

### Apagar (o ZIP não apaga)

```
app/src/main/java/data/MockData.kt
app/src/main/java/ui/screens/AcessoNegadoScreen.kt
app/src/main/java/ui/screens/AguardandoScreen.kt
app/src/main/java/ui/screens/BarChart.kt
app/src/main/java/ui/screens/HomeScreen.kt
app/src/main/java/ui/screens/LoginScreen.kt
app/src/main/java/ui/screens/PerfilScreen.kt
app/src/main/java/ui/screens/UsuariosScreen.kt
app/src/main/java/ui/screens/WelcomeScreen.kt
app/src/main/java/com/fleetflow/mobile/data/api/TokenAuthenticator.kt
```

Atenção: as pastas `app/src/main/java/ui` e `app/src/main/java/data` saem inteiras. As telas de verdade continuam em `app/src/main/java/com/fleetflow/mobile/ui/screens/`.

### Novos e alterados (estão no ZIP)

Parte do Douglas, 2 arquivos:

```
app/src/main/java/com/fleetflow/mobile/ui/screens/DetalheViagemScreen.kt
app/src/main/java/com/fleetflow/mobile/ui/screens/ListaViagensScreen.kt
```

Parte do Gabriel Claro, 11 arquivos:

```
app/src/main/java/com/fleetflow/mobile/MainActivity.kt
app/src/main/java/com/fleetflow/mobile/data/local/AppDatabase.kt
app/src/main/java/com/fleetflow/mobile/data/local/CachePainel.kt
app/src/main/java/com/fleetflow/mobile/data/repository/FinanceRepository.kt
app/src/main/java/com/fleetflow/mobile/data/repository/PainelRepository.kt
app/src/main/java/com/fleetflow/mobile/data/repository/RegrasCache.kt
app/src/main/java/com/fleetflow/mobile/ui/components/EstadosPainel.kt
app/src/main/java/com/fleetflow/mobile/ui/screens/HomeScreen.kt
app/src/test/java/com/fleetflow/mobile/PainelTest.kt
app/src/test/java/com/fleetflow/mobile/RegrasCacheTest.kt
docs/ENTREGA-18-11-APP.md
```

Correção da base (versões de 04/11 e 11/11), 28 arquivos. `Theme.kt` e `Color.kt` também têm o verde do card de receitas do detalhe:

```
app/build.gradle.kts
app/src/main/java/com/fleetflow/mobile/data/api/ApiClient.kt
app/src/main/java/com/fleetflow/mobile/data/api/AuthApi.kt
app/src/main/java/com/fleetflow/mobile/data/api/AuthInterceptor.kt
app/src/main/java/com/fleetflow/mobile/data/api/UserApi.kt
app/src/main/java/com/fleetflow/mobile/data/auth/AuthorizationManager.kt
app/src/main/java/com/fleetflow/mobile/data/auth/TokenManager.kt
app/src/main/java/com/fleetflow/mobile/data/local/FrotaDao.kt
app/src/main/java/com/fleetflow/mobile/data/model/NetworkModels.kt
app/src/main/java/com/fleetflow/mobile/data/repository/AuthRepository.kt
app/src/main/java/com/fleetflow/mobile/data/repository/FrotaRepository.kt
app/src/main/java/com/fleetflow/mobile/data/repository/RegrasViagem.kt
app/src/main/java/com/fleetflow/mobile/data/sync/SyncScheduler.kt
app/src/main/java/com/fleetflow/mobile/data/sync/SyncWorker.kt
app/src/main/java/com/fleetflow/mobile/ui/screens/ChecklistViagemScreen.kt
app/src/main/java/com/fleetflow/mobile/ui/screens/FecharViagemScreen.kt
app/src/main/java/com/fleetflow/mobile/ui/screens/LoginScreen.kt
app/src/main/java/com/fleetflow/mobile/ui/screens/UsuariosScreen.kt
app/src/main/java/com/fleetflow/mobile/ui/theme/Color.kt
app/src/main/java/com/fleetflow/mobile/ui/theme/Theme.kt
app/src/main/java/com/fleetflow/mobile/ui/theme/Type.kt
app/src/test/java/com/fleetflow/mobile/RegrasViagemTest.kt
build.gradle.kts
gradle.properties
gradle/gradle-daemon-jvm.properties
gradle/libs.versions.toml
gradle/wrapper/gradle-wrapper.properties
settings.gradle.kts
```
