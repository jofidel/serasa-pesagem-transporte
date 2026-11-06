Atue como arquiteto de software e engenheiro backend sênior. Leia o PDF anexado “desafio-tecnico-backend_ia.pdf” e implemente uma solução completa para o desafio usando Java 21, Quarkus, PostgreSQL com Hibernate ORM/JPA e Apache Kafka.

Use o PDF como especificação das regras do negócio. Quando ele não definir um detalhe técnico ou uma regra, escolha uma solução razoável e registre-a como decisão de projeto, sem apresentá-la como requisito original.

## Tecnologias e arquitetura

- Java 21 e Quarkus, com versões estáveis e compatíveis.
- Maven.
- PostgreSQL e Hibernate ORM com JPA ou Panache. Escolha uma abordagem e use-a consistentemente.
- Apache Kafka para processar as leituras de forma assíncrona.
- Flyway ou Liquibase para versionamento do schema.
- OpenAPI/Swagger UI para documentação da API.
- Docker e Docker Compose para execução local.
- Testes unitários e de integração.
- API REST stateless, configurável por variáveis de ambiente e preparada para escala horizontal.
- Health checks, logs estruturados e métricas. Inclua rastreamento distribuído quando viável.

Confira a compatibilidade das versões com Java 21 e Quarkus usando documentação oficial atual. Se não puder consultar fontes externas, informe isso e não afirme que verificou as versões.

Descreva também uma arquitetura de produção em cloud, incluindo implantação, escalabilidade, observabilidade, segurança, banco gerenciado, Kafka e recuperação de falhas. Diferencie claramente a arquitetura local da arquitetura de produção.

## Requisitos do domínio

O sistema atende uma empresa de transporte de grãos com filiais, caminhões e balanças. Um caminhão parte para buscar um tipo de grão e, ao retornar à filial, é pesado. O ESP32 envia leituras a cada 100 ms enquanto o caminhão está na balança, sem aguardar resposta do servidor.

A mensagem enviada pela balança tem este formato:

{
"id": "<id da balança>",
"plate": "<placa do caminhão>",
"weight": 12345.67
}

O desafio pede cadastros de caminhões, tipos de grão, filiais, balanças e transações de transporte. Cada tipo de grão possui preço de compra por tonelada. A margem de venda deve ficar entre 5% e 20% e aumentar quando a disponibilidade do grão diminuir.

## 1. Cadastros e transações

Implemente endpoints para criar, consultar, atualizar e desativar, quando apropriado:

- Caminhões, com placa e tara.
- Tipos de grão, com preço de compra por tonelada.
- Filiais.
- Balanças vinculadas a filiais, com identificador, status e credencial.
- Transações de transporte, com caminhão, filial, tipo de grão, início, fim e estado.

Defina chaves, relacionamentos, índices, restrições, validações e regras de exclusão. Preserve o histórico: evite apagar fisicamente registros referenciados por transações ou pesagens.

Modele estados e transições explícitos para a transação, como iniciada, aguardando pesagem, concluída, cancelada ou com erro. Documente as transições permitidas.

## 2. Ingestão das leituras

Crie um endpoint HTTP para receber as mensagens das balanças.

- Valide o payload, o peso, a placa e a autorização da balança.
- Mantenha o endpoint leve: valide o necessário e publique a leitura no Kafka.
- Defina o status HTTP e a semântica da resposta, considerando o modo fire-and-forget da balança.
- Considere leituras simultâneas de várias balanças, falhas do Kafka, retentativas e mensagens duplicadas.
- Defina uma chave de partição que preserve a ordem das leituras de uma mesma balança.
- Não prometa entrega “exactly once” de ponta a ponta sem explicar seus limites.

O protocolo original não traz identificador único por leitura. Explique como tratar duplicatas nesse formato e, se necessário, proponha uma extensão retrocompatível com um identificador ou timestamp gerado pela balança.

## 3. Estabilização e persistência

Implemente um consumidor Kafka que agrupe as leituras por balança e placa, detecte quando o peso está estável e persista somente a pesagem estabilizada.

Defina uma estratégia objetiva, configurável e testável. Considere, por exemplo, janela de tempo, número mínimo de amostras, tolerância absoluta ou percentual e duração mínima dentro da faixa. Documente os parâmetros escolhidos e seus trade-offs.

Trate:

- Oscilações e picos de leitura.
- Leituras atrasadas ou fora de ordem.
- Intervalos sem mensagens e timeout.
- Mudança de placa na balança.
- Processamento simultâneo de balanças.
- Reinício ou escala horizontal dos consumidores.

Não mantenha estado crítico apenas em memória se isso puder causar perda ou duplicação ao reiniciar ou escalar instâncias. Explique como o estado de estabilização será recuperado ou compartilhado.

Para cada pesagem estabilizada, armazene:

- Placa do caminhão.
- Peso bruto estabilizado.
- Tara lida do cadastro do caminhão.
- Peso líquido (peso bruto menos tara).
- Data e hora, com timezone definido.
- Balança e filial.
- Tipo de grão da transação ativa.
- Custo da carga, com unidade e fórmula documentadas.

Preserve na pesagem os valores de preço, custo e margem usados naquele momento para que alterações futuras nos cadastros não alterem o histórico. Defina o comportamento para ausência de transação ativa, cadastro inexistente ou peso bruto inferior à tara. Garanta que uma mesma pesagem não seja persistida duas vezes.

## 4. Estoque, custos e margem

Modele a disponibilidade de grãos por filial ou doca, com atualizações transacionais que impeçam quantidade negativa e inconsistências por concorrência.

O PDF não define como o estoque muda. Declare a regra escolhida e implemente-a de forma consistente. Se o processo de descarga ou venda estiver fora do escopo, deixe explícita a limitação.

Implemente uma função determinística, configurável e testável para calcular a margem entre 5% e 20% de forma inversamente proporcional à disponibilidade. Documente unidades, limites, fórmula, arredondamento e tratamento de estoque ausente ou zerado. Não apresente essa fórmula como se estivesse especificada no PDF.

## 5. Consultas e relatórios

Disponibilize endpoints documentados para consultas de pesagens, transações e relatórios. Use paginação, filtros e ordenação quando apropriado.

Inclua indicadores administrativos úteis, como:

- Volumes bruto e líquido por período, filial e tipo de grão.
- Custo total das cargas.
- Preço de venda estimado e margem aplicada.
- Disponibilidade de cada grão por filial.
- Pesagens por balança, caminhão e período.
- Leituras descartadas, instabilidade e falhas operacionais.

Diferencie valores efetivamente registrados de estimativas calculadas.

## Segurança, confiabilidade e operação

- Use DTOs; não exponha entidades JPA diretamente.
- Separe recursos REST, serviços de domínio, persistência e integração Kafka.
- Centralize validações e tratamento de erros.
- Armazene segredos fora do código e permita rotação das credenciais das balanças.
- Proteja endpoints administrativos com autenticação e autorização.
- Não registre credenciais ou dados sensíveis nos logs.
- Inclua health/readiness checks e métricas para ingestão, filas, estabilização, erros e persistência.
- Considere outbox transacional ou uma alternativa para reduzir inconsistências entre banco e Kafka; explique a escolha.
- Documente retenção e particionamento dos tópicos Kafka.
- Defina políticas de retentativa, dead-letter topic, backup, migração e recuperação de falhas.

## Entregáveis

Gere um projeto executável contendo:

1. Estrutura de diretórios organizada por responsabilidade.
2. `pom.xml` com extensões e plugins necessários.
3. Configuração Quarkus para desenvolvimento e produção, sem credenciais reais.
4. Entidades, repositórios, serviços, DTOs, validações e recursos REST.
5. Produção e consumo Kafka, incluindo a estratégia de estabilização.
6. Migrações do banco de dados.
7. Dockerfile e Docker Compose para executar localmente a aplicação, PostgreSQL e Kafka.
8. Exemplos de chamadas HTTP e um simulador que envie leituras a cada 100 ms.
9. Testes automatizados para regras de domínio, estabilização, idempotência e integração.
10. README em português com pré-requisitos, configuração, inicialização, execução dos testes, simulação das balanças, endpoints, decisões de projeto, limitações e próximos passos.
11. Resumo da arquitetura e da estratégia de estabilização, adequado para apresentar junto ao desafio.

## Processo

1. Leia o PDF e resuma os requisitos que serão implementados.
2. Apresente brevemente as decisões técnicas que o PDF não especifica.
3. Inspecione o repositório existente e suas instruções locais antes de alterar arquivos. Preserve mudanças preexistentes e adapte a solução ao projeto.
4. Mostre a árvore de arquivos proposta.
5. Implemente o projeto completo. Não deixe pseudocódigo, métodos vazios, TODOs essenciais ou dependências implícitas.
6. Execute os testes e, se o ambiente permitir, suba a aplicação e verifique os principais fluxos.
7. Revise a consistência entre código, migrações, configuração, testes e documentação.
8. No final, informe o que foi implementado, quais comandos foram executados, os resultados e qualquer limitação que impeça a execução.

## Regras a implementar

1. Cada caminhão deve estar associado a uma transação de transporte que informe o tipo de grão que ele buscará antes de sair para a fazenda.
2. Ao retornar à filial, a pesagem deve ser associada à transação ativa daquele caminhão, à filial e ao tipo de grão previsto.
3. Enquanto houver caminhão na balança, o ESP32 envia leituras a cada 100 ms sem aguardar resposta. A aplicação deve aceitar esse comportamento fire-and-forget sem bloquear o processamento das leituras.
4. O caminhão pode receber uma nova demanda após concluir a descarga. Implemente estados e validações para impedir transações simultâneas indevidas para o mesmo caminhão e permitir uma nova transação após a conclusão da anterior.
5. Cada tipo de grão deve possuir um preço de compra por tonelada. O preço usado em cada transação deve ser preservado no histórico para que alterações futuras no cadastro não modifiquem custos já registrados.
6. A margem de lucro deve permanecer entre 5% e 20%.
7. A margem deve ser inversamente proporcional à quantidade disponível do tipo de grão: quanto menor a disponibilidade, maior a margem. O cálculo deve ser determinístico e testável. Como o PDF não informa a fórmula, defina e documente uma fórmula adequada, incluindo limites, unidades, arredondamento e comportamento para disponibilidade zero ou desconhecida.
8. O preço de venda deve ser calculado usando o preço de compra e a margem vigente, preservando os valores aplicados na transação.

## Implementação

- Inspecione a estrutura do projeto e as instruções locais antes de editar arquivos.
- Localize as entidades, serviços, endpoints e persistência envolvidos em caminhões, grãos, filiais, balanças, transações, pesagens e estoque.
- Implemente as regras na camada de domínio ou serviço apropriada, evitando duplicar lógica em endpoints.
- Garanta que as alterações de transação, pesagem e disponibilidade sejam consistentes e seguras contra concorrência.
- Valide estados inválidos, como caminhão sem transação ativa, tentativa de iniciar uma segunda demanda antes de concluir a primeira, peso líquido negativo ou tipo de grão incompatível com a transação.
- Preserve compatibilidade com o formato de leitura existente da balança.
- Não altere partes fora do escopo, exceto quando necessário para integrar corretamente as regras.

## Indentação e estilo do código

Ao concluir as alterações, padronize a indentação dos arquivos modificados:

- Java: 4 espaços por nível, sem tabulações.
- YAML e XML: mantenha indentação consistente com o padrão do arquivo.
- Não reformate arquivos não relacionados.
- Se o projeto tiver formatter configurado, use-o. Caso contrário, aplique o padrão acima aos arquivos alterados.

## Testes e entrega

- Crie ou atualize testes para cobrir as regras implementadas, incluindo limites da margem, relação entre disponibilidade e margem, vínculo da transação com o tipo de grão, conclusão de uma demanda e início de uma nova demanda.
- Execute os testes disponíveis e informe os comandos e resultados reais.
- Revise a consistência entre código, banco de dados, testes e documentação.
- Ao final, resuma os arquivos alterados, as decisões tomadas para regras não especificadas no PDF e qualquer limitação encontrada.