# Validação da entrega de 14 de outubro

Verificação realizada em 10 de outubro de 2026, sobre o código entregue neste pacote.

| Verificação | Resultado |
|---|---|
| Testes Node `npm test` | 127 testes, 127 aprovados, zero falhas |
| MySQL real | MySQL Community Server 8.4.11, schema executado com sucesso |
| Integração API e MySQL | 38 verificações aprovadas |
| Dados de demonstração | 24 lançamentos, oito em cada mês de agosto, setembro e outubro de 2026 |
| Compilação Android | `:app:assembleDebug` concluído com BUILD SUCCESSFUL, APK debug gerado |
| Merge do front | `git apply --check docs/merge-front.patch` aprovado sobre main recebido |
| Duplicidade de telas | Uma implementação ativa por tela, sem marcadores de conflito |

A integração em MySQL real verificou a primeira conta Google como Administrador, nova conta pendente, aprovação, rotação de refresh e rejeição do token antigo, categorias e pagamentos, CRUD financeiro, versão concorrente, filtros/paginação, totais, saldo atual/previsto, pagamento, exclusão lógica, envio/leitura/remoção de comprovante, rejeição de arquivo inválido e acima de 5 MiB, geração de parcelas e ajuste do fim de mês.

O rollback foi provocado na segunda parcela e verificado no banco: as contagens de dívidas e movimentações permaneceram iguais às anteriores à tentativa, sem dívida nem parcela parcial gravadas.

Os testes de API simulam a validação Google. O login com contas Google reais requer as credenciais do grupo. A compilação Android verifica recursos, Kotlin, Room, manifesto, Java e empacotamento; testes de câmera, galeria, Google e usabilidade em celular real devem ser executados com a configuração e dispositivos da equipe.

Os comandos de execução e o roteiro funcional estão no README. Frota, viagens e sincronização offline pertencem às próximas datas do cronograma.
