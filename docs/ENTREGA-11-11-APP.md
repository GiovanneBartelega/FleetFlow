# Entrega de 11/11: app (Douglas + Gabriel Claro)

Base: `fleetflow-integrado-04-11` (ec70b28). Neste pacote estão:

- os alertas de frota do Douglas (04/11), que ainda não estavam nessa branch;
- o ZIP do Douglas de 07 e 08/11;
- a parte do Gabriel Claro de 11/11: resolução de conflito e tela de pendências de sincronização.

Nenhum arquivo da API ou do banco MySQL mudou.

O ZIP traz **só os arquivos que mudaram** em relação a `fleetflow-integrado-04-11`, nas mesmas pastas do repositório. É só copiar por cima. Ele não traz o projeto inteiro de propósito: assim não sobrescreve o que as outras frentes mudaram desde aquela branch. A lista completa está no fim deste documento.

## Alertas de frota (Douglas, 04/11)

`AlertasScreen`, `RegrasAlertas` e o teste `RegrasAlertasTest`, mais o botão "Alertas" no topo da Frota. A rota `"alertas"` aparece uma vez só no `FleetFlowApp`; no ZIP ela estava duplicada.

## ZIP do Douglas de 07–08/11

### Entrou como veio

- Dropdown com pesquisa (`Components.kt`), com a mesma assinatura do anterior.
- Campo "Viagem (opcional)" no lançamento (`FinancasScreen`, `FinanceModels`, `FinanceRepository`), que grava o `viagemId` na movimentação.
- `FinanceRepository.movimentacoesDaViagem`.
- `ViagensScreen` com o detalhe da viagem. Ela ainda não tem entrada na navegação; a aba Viagens continua sendo a `ListaViagensScreen`.

### Mudou na junção, porque quebrava as viagens offline

| No ZIP | Problema | Como ficou |
|---|---|---|
| Banco local versão 4 com migração 3→4 | A migração lê a coluna `origem`, que não existe na versão 3 oficial: o app fechava ao abrir | Versão 5, que só cria a tabela `avisos_sync`. Ela sobe a partir da 3 oficial e também da 4 do ZIP, que terminava com as mesmas tabelas da 3 |
| `ViagensDao` e `ViagensEntities` | A cada carga, `DELETE FROM viagens`: apagava as viagens feitas sem internet que ainda não tinham subido e duplicava as que já tinham subido | Removidos. `ViagensRepository` agora só lê `GET /api/viagens` e guarda em memória, para as telas de Finanças e do detalhe |
| `ViagensApi` e `ViagensModels` | Repetiam a `ViagemApi` e o `ViagemDto` com outro formato | Removidos. O `ViagemDto` ganhou `ativo`, `criadoEm` e `atualizadoEm` e o `RotaDto` ganhou `ativo` |
| `MainActivity` sem `ViagemRepository.init` | A lista de viagens e o seletor de rotas podiam fechar o app | O init voltou, junto com o dos repositórios novos e a limpeza ao sair da conta |
| `RotasScreen` com `RotaRepo` em memória | Rota cadastrada nunca ia para a API: era a causa do problema "seleção de rotas na abertura de viagem" | A tela usa o `RotasRepository` (`GET`/`POST`/`PATCH`/`DELETE /api/rotas`), que também atualiza a cópia de rotas do aparelho. A rota nova aparece na hora em "Abrir viagem" |

Na tela de rotas, os campos agora são os mesmos da API e da especificação: nome, origem, destino, distância e descrição. Saíram tempo estimado, pedágio e o botão "ativa", porque a API não tem esses campos. A API não deixa excluir rota com viagens, e a tela já avisa isso antes de tentar. Sem conexão, a tela mostra as rotas salvas no aparelho.

A "Abrir viagem" baixa rotas e viagens de novo ao abrir, como o Douglas propôs.

## Parte do Gabriel Claro: resolução de conflito

As regras ficam em `RegrasConflito.kt`, sem Android, e o `RegrasConflitoTest` cobre cada linha da tabela "Conflitos" da especificação. Quem aplica as regras é o `ViagemRepository.enviarPendentes`.

| Situação (especificação) | O que o app faz |
|---|---|
| Mesmo registro alterado nos dois lados | Vence a alteração com `atualizadoEm` mais recente. Fechamento feito no aparelho, mas a viagem já encerrada no servidor com outro hodômetro: se o servidor é mais recente, vale a versão dele e a pessoa recebe um aviso. Se o aparelho é mais recente, a viagem fica em **Conflito** para o gestor revisar, porque a API não reabre viagem encerrada. Se a viagem continua aberta no servidor e só a versão mudou, o fechamento é reenviado com a versão nova: os campos do fechamento não mudaram lá |
| Viagem feita offline, veículo já em outra viagem no servidor (`VEHICLE_UNAVAILABLE` / `OPEN_TRIP_EXISTS`) | A viagem fica em **Conflito**, com a explicação, e é tentada de novo a cada envio sem contar tentativa. Quando o veículo for liberado no servidor, a abertura, o checklist e o fechamento sobem sozinhos |
| Registro apagado no servidor e editado no celular (404 ou `viagemId` inválido) | A edição é descartada e a pessoa recebe um aviso |
| Hodômetro inválido depois de sincronizar (`INVALID_ODOMETER`) | A API recusa e o item fica na tela de pendências com a mensagem dela |
| Checklist enviado depois de a viagem ser encerrada no servidor (`TRIP_CLOSED`) | Só o checklist é descartado, com aviso; o fechamento segue a regra do fechamento |

Os avisos ficam na tabela `avisos_sync` até a pessoa dispensar, porque o envio às vezes roda com o app fechado.

## Parte do Gabriel Claro: o que o usuário vê

- **Ícone de sincronização** na barra de Viagens: sincronizado, sincronizando, sem conexão ou com pendências, com a contagem. A contagem fica vermelha quando algo precisa de atenção. Tocar nele abre a tela de pendências.
- **Tela de pendências de sincronização**: abre pelo ícone e pelo Perfil, que faz as vezes de configurações. Ela lista cada viagem com o que ainda não subiu (abertura, checklist, fechamento), a situação e o erro de cada operação. Tem os botões "Tentar de novo", "Descartar" e "Enviar agora", e mostra os avisos da resolução de conflito.
- **Selo "Conflito"** no card da viagem, além de "Pendente" e "Erro".
- **Faixa de sem conexão corrigida**: a situação da rede vem do `ConnectivityManager` (`Conectividade.kt`). Antes, no modo avião com fila, a faixa dizia "Enviando para o servidor". Quando a rede volta, a fila é enviada na hora, e a faixa some assim que a fila consegue falar com a API.

## O que já foi testado

- Compilado no Android Studio sem erros e instalado no emulador por cima da versão de 04/11. O banco local migrou sem perder as viagens.
- **API desligada:** viagem aberta sem servidor, com a rota salva no aparelho e o dropdown com busca. O ícone mostrou a contagem, a faixa avisou que o servidor não respondia e a tela de pendências abriu pelo ícone e pelo Perfil.
- **API ligada:**
  - A viagem aberta offline subiu sozinha.
  - A tela de Rotas lista as rotas da API com a contagem de viagens.
  - O campo "Viagem" do lançamento lista as viagens do servidor.
- **Resolução de conflito:**
  - 12 testes unitários das regras.
  - 8 cenários de envio simulados com o `ViagemRepository` de verdade e uma API falsa: servidor mais recente, aparelho mais recente, viagem apagada, versão desatualizada, veículo ocupado, checklist em viagem encerrada, hodômetro recusado e 10 falhas de rede. Todos passaram.
- **Ainda não feito no emulador:** cadastrar uma rota digitando no app e provocar os conflitos com o banco de verdade (passos 2, 4 e 5 abaixo).

## Como testar

1. API no ar (`npm start`) e app instalado por cima da versão anterior: o banco local migra para a 5 sem apagar nada.
2. Frota › Rotas: cadastrar uma rota. Em Viagens › Abrir viagem, ela já aparece no seletor.
3. Abrir viagem, responder o checklist, ligar o modo avião e fechar a viagem. O ícone mostra 1 pendência e a faixa diz "Sem conexão".
4. Conflito "servidor mais recente", ainda com o modo avião ligado: encerrar a mesma viagem direto no MySQL com outro hodômetro e desligar o modo avião. A viagem passa a mostrar o hodômetro do servidor e aparece um aviso nas pendências.

   ```sql
   UPDATE viagens SET Status='Encerrada', HodometroFinal=HodometroInicial+500, DataFechamento=UTC_TIMESTAMP(3)
   WHERE Status='Aberta' ORDER BY DataAbertura DESC LIMIT 1;
   ```

5. Viagem apagada no servidor: com o modo avião ligado, fechar uma viagem e apagar a viagem no MySQL. Ao voltar a conexão, a viagem sai do aparelho com aviso.

   ```sql
   UPDATE viagens SET Ativo=0 WHERE Status='Aberta' ORDER BY DataAbertura DESC LIMIT 1;
   ```
6. Testes unitários: `RegrasConflitoTest` (12), `RegrasViagemTest` (7) e `RegrasAlertasTest` (12).

## Pendências para a API (Bruna e Giovanne)

- Para o gestor ver no app dele a viagem em conflito ("sinalizada para o gestor revisar"), a API precisaria aceitar uma viagem já aberta e encerrada offline com uma marca de revisão, sem mexer no status atual do veículo.
- A API ainda não lê o cabeçalho `Idempotency-Key`. O app já manda o cabeçalho e trata os reenvios pelos códigos `OPEN_TRIP_EXISTS`, `TRIP_ALREADY_CLOSED` e `DUPLICATE`.

## Arquivos deste pacote

Base: `fleetflow-integrado-04-11`. Caminhos a partir da raiz do repositório.

| Arquivo | Situação | Origem |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | alterado | Gabriel (permissão de estado da rede) |
| `app/src/main/java/com/fleetflow/mobile/FleetFlowApp.kt` | alterado | Douglas (alertas) + Gabriel (pendências) |
| `app/src/main/java/com/fleetflow/mobile/MainActivity.kt` | alterado | Douglas + correção da junção |
| `.../data/api/ViagemApi.kt` | alterado | junção (cadastro de rotas) |
| `.../data/local/AppDatabase.kt` | alterado | Gabriel (versão 5) |
| `.../data/local/AvisoSync.kt` | novo | Gabriel |
| `.../data/local/FilaSync.kt` | alterado | Gabriel |
| `.../data/local/ViagemDao.kt` | alterado | junção (rotas) |
| `.../data/local/ViagemEntities.kt` | alterado | Gabriel (situação Conflito) |
| `.../data/model/FinanceModels.kt` | alterado | Douglas |
| `.../data/model/ViagemModels.kt` | alterado | Douglas + junção |
| `.../data/repository/FinanceRepository.kt` | alterado | Douglas |
| `.../data/repository/RegrasAlertas.kt` | novo | Douglas (04/11) |
| `.../data/repository/RegrasConflito.kt` | novo | Gabriel |
| `.../data/repository/RotasRepository.kt` | novo | Douglas + junção (API) |
| `.../data/repository/ViagemRepository.kt` | alterado | Gabriel |
| `.../data/repository/ViagensRepository.kt` | novo | Douglas + junção (só leitura) |
| `.../data/sync/Conectividade.kt` | novo | Gabriel |
| `.../ui/components/Components.kt` | alterado | Douglas |
| `.../ui/screens/AbrirViagemScreen.kt` | alterado | Douglas + Gabriel |
| `.../ui/screens/AlertasScreen.kt` | novo | Douglas (04/11) |
| `.../ui/screens/FinancasScreen.kt` | alterado | Douglas |
| `.../ui/screens/FrotaScreen.kt` | alterado | Douglas (botão Alertas) |
| `.../ui/screens/ListaViagensScreen.kt` | alterado | Gabriel |
| `.../ui/screens/PendenciasSyncScreen.kt` | novo | Gabriel |
| `.../ui/screens/PerfilScreen.kt` | alterado | Gabriel |
| `.../ui/screens/RotasScreen.kt` | alterado | Douglas + junção (API) |
| `.../ui/screens/ViagemFormatos.kt` | alterado | Gabriel |
| `.../ui/screens/ViagensScreen.kt` | alterado | Douglas |
| `app/src/test/java/com/fleetflow/mobile/RegrasAlertasTest.kt` | novo | Douglas (04/11) |
| `app/src/test/java/com/fleetflow/mobile/RegrasConflitoTest.kt` | novo | Gabriel |
| `docs/ENTREGA-11-11-APP.md` | novo | este documento |

`...` = `app/src/main/java/com/fleetflow/mobile`.

**Arquivos do ZIP do Douglas que não entram** (não copiar): `data/api/ViagensApi.kt`, `data/local/ViagensDao.kt`, `data/local/ViagensEntities.kt` e `data/model/ViagensModels.kt`. O motivo está na tabela "Mudou na junção".

