# Pesagem de transporte de grãos

Backend Java 21/Quarkus para ingestão assíncrona das leituras de balanças, estabilização e registro de pesagens em PostgreSQL. Kafka desacopla a resposta HTTP do processamento; a chave de partição é o identificador da balança.

## Requisitos implementados

Cadastros REST de filiais, caminhões, grãos, balanças e abertura de transações. Ao iniciar uma transação, o sistema captura o preço de compra do grão; uma mudança futura no cadastro não altera esse preço histórico. Uma restrição única parcial no PostgreSQL impede mais de uma transação ativa por caminhão. A ingestão valida balança ativa e token, entrega a leitura ao emitter para publicação no tópico `scale-readings` com a chave da balança e responde `202 Accepted`. Esse status confirma o aceite local pelo emitter, não a gravação durável no broker.

O consumidor persiste o estado de estabilização por balança e reinicia a janela quando a placa muda. Considera estável uma sequência de pelo menos 20 amostras cuja amplitude máxima-mínima não exceda 2 kg. No retorno, associa a placa à transação ativa da filial da balança, calcula peso líquido (bruto estabilizado menos tara), registra custo e preço estimado e conclui a transação. Peso líquido zero ou negativo não é registrado. Depois da conclusão, o caminhão pode receber nova transação.

## Decisões e limites

O protocolo legado não contém ID de leitura. Um `readingId` opcional é aceito; quando ausente, o servidor gera UUID. A tabela `processed_reading` impede que o mesmo evento Kafka seja aplicado duas vezes, mas não identifica o reenvio físico da mesma amostra pelo ESP32 se ele não enviar um ID estável. A restrição única por transação também impede gravar duas pesagens para a mesma demanda. Para idempotência ponta a ponta, recomenda-se que o ESP32 envie `readingId` estável em tentativas repetidas. O Kafka e o banco operam com entrega ao menos uma vez, não há promessa de exactly-once ponta a ponta.

A política escolhida (não especificada no PDF) é `margem = clamp(20% - min(max(estoqueKg, 0), 1.000.000) / 1.000.000 × 15%, 5%, 20%)`. A unidade é kg por filial e tipo de grão; estoque desconhecido é tratado como zero (margem de 20%). A margem é persistida como fração decimal com quatro casas decimais, arredondada HALF_UP. Assim, 0 kg produz 20%, 1.000.000 kg ou mais produz 5%, e os valores entre esses limites interpolam linearmente. Custo = preço de compra por tonelada capturado no início da transação × peso líquido em kg ÷ 1000, arredondado para centavos HALF_UP. Preço estimado = custo × (1 + margem), também em centavos HALF_UP. Os valores aplicados ficam armazenados na pesagem.

Ao concluir a pesagem, o peso líquido é somado ao estoque da filial e tipo de grão. A atualização de estoque existente bloqueia a linha para escrita; a criação/atualização concorrente de uma combinação ainda inexistente usa `INSERT ... ON CONFLICT` para não perder incrementos. Fluxos de venda/baixa de estoque não foram definidos no desafio e ficam fora deste escopo.

Autenticação de balança usa segredo compartilhado enviado por `X-Scale-Token`, armazenando SHA-256. Em produção, substituir por HMAC/mTLS e hash adequado a credenciais de baixa entropia, aplicar rate limits e TLS. A configuração local usa credenciais demonstrativas; nunca use-as em cloud. A implementação cobre os fluxos de cadastro e consulta necessários à regra de negócio, mas não inclui endpoints de edição/desativação de todos os cadastros nem autenticação de usuários administrativos.

## Execução local

```bash
docker compose -f compose.yaml up -d postgres kafka
./mvnw quarkus:dev
```

Ou construa e suba tudo com `docker compose -f compose.yaml up --build`. Swagger UI: http://localhost:8080/q/swagger-ui. Health: `/q/health`. Métricas: `/q/metrics`.

## Fluxo de exemplo

Crie filial, caminhão e grão via `POST /api/branches`, `/api/trucks`, `/api/grains`. Registre a balança com `POST /api/scales` usando `{ "externalId":"BAL-01", "branchId":1, "secret":"troque-este-segredo" }`; crie transação em `/api/transports` com IDs cadastrados. Envie leituras:

```bash
curl -X POST http://localhost:8080/api/readings -H 'Content-Type: application/json' -H 'X-Scale-Token: troque-este-segredo' -d '{"id":"BAL-01","plate":"ABC1D23","weight":12000}'
```

Simulador a cada 100 ms: `SCALE_ID=BAL-01 SCALE_TOKEN=troque-este-segredo PLATE=ABC1D23 ./scripts/simulate-scale.sh`. Para simular carga estabilizada, configure `WEIGHT_KG` e mantenha o processo ativo.

## Cloud

Imagem JVM stateless em Kubernetes/ECS; múltiplas réplicas API e consumidores Kafka separados, PostgreSQL gerenciado com HA/PITR, Kafka gerenciado com TLS/SASL e tópicos replicados, segredos em secret manager, Ingress com TLS, HPA baseado em CPU/lag, readiness/liveness, Micrometer/Prometheus, logs correlacionados e OpenTelemetry. Dimensione partições por volume e retenha mensagens conforme política operacional. Para atomicidade entre persistência e evento de entrada, este serviço publica antes do commit local e delega durabilidade ao Kafka; a gravação resultante é idempotente. Uma futura integração de eventos de domínio deve usar outbox transacional.

## Configuração principal

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS`, `STABLE_SAMPLES` e `STABLE_TOLERANCE_KG`. Migrações Flyway ficam em `src/main/resources/db/migration`. Testes: `./mvnw test`.
