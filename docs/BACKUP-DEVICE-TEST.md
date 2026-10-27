# Backup — teste em Android, 27/10/2026

Motorola Edge 70 Fusion / Android 17. Código da versão 1.2 em pacote isolado `com.pixelricardo.game.backuptest`, com nome de teste e assinatura de desenvolvimento. As alterações para isolamento foram apenas applicationId e nome; o código do backup foi preservado. A instalação pessoal `com.pixelricardo.game` não foi alterada.

Foi preparada uma fixture sintética no save privado da cópia de teste (não obtida por partidas): 321 pedrinhas, uma espécie evoluída, visual comprado e equipado, conquista, célula visitada, dificuldade, som e contadores diários.

- Exportar abriu ACTION_CREATE_DOCUMENT; o arquivo salvo pelo seletor Android correspondeu a todos os campos persistidos.
- Após mudar a fixture para 11 pedrinhas e nenhuma espécie, importar um arquivo artificial de formato futuro foi recusado sem substituir os dados.
- Selecionar o backup válido mostrou o resumo correto (uma espécie, 321 pedrinhas). Cancelar a confirmação manteve o save alterado.
- Confirmar a substituição restaurou todos os campos exportados. A única diferença foi playCount +1, pois a carga do jogo conta uma abertura.
- Encerrar o processo e reabrir preservou os campos restaurados, com apenas o incremento normal adicional em playCount.

A cópia isolada e os arquivos artificiais foram removidos. A configuração de manter a tela ligada foi restaurada de 3 para o valor original 0. Nenhum save da instalação pessoal foi lido ou modificado.

Limites: teste feito via ADB, inspeção de interface e comparação de JSON. Não valida jogabilidade, todos os provedores de arquivos, todos os aparelhos, interrupção durante gravação nem recuperação de dados antigos da 1.0/1.1 já desinstaladas. A assinatura alterada da 1.2 continua impedindo atualização direta dessas versões.
