# Detecção de Fraude por Busca Vetorial — IVF + SQ8 + mmap

> Solução para a **[Rinha de Backend 2026](https://github.com/zanfranceschi/rinha-de-backend-2026)**.
> Stack: **Java 21 · Spring Boot 4 · Docker Compose · nginx**.

Uma API que classifica transações de cartão como **fraude** ou **legítima** comparando cada
transação com **3 milhões** de casos históricos — feita para caber em **1 CPU e 350 MB de RAM**
sem perder precisão nem velocidade.

---

## 📌 Sobre o desafio

Este projeto resolve o desafio sugerido da **Rinha de Backend 2026**, cujo tema é
**detecção de fraude por busca vetorial** (_fraud detection via vector search_).
A documentação oficial está em
[`docs/br/README.md`](https://github.com/zanfranceschi/rinha-de-backend-2026/blob/main/docs/br/README.md)
do repositório do desafio.

### O que o desafio pede

Para **cada transação** recebida, o serviço deve:

1. Transformar o payload em um **vetor de 14 dimensões** (usando fórmulas de normalização fornecidas);
2. Encontrar os **5 vetores mais parecidos** numa base de referência (k-NN, distância euclidiana);
3. Calcular o `fraud_score = nº_de_fraudes_entre_os_5 / 5`;
4. **Aprovar** se o score for **menor que 0,6**; senão, reprovar.

**Endpoints (porta `9999`):**

| Método | Rota           | Descrição                              |
|-------:|----------------|----------------------------------------|
| `GET`  | `/ready`       | _Health check_ (2xx quando operacional) |
| `POST` | `/fraud-score` | Classificação da transação              |

**Resposta:**

```json
{ "approved": true, "fraud_score": 0.4 }
```

**Restrições de infraestrutura:**

- **No mínimo 1 load balancer + 2 instâncias** da API (round-robin);
- **Total de no máximo 1 CPU e 350 MB de RAM** somando todos os serviços;
- Rede em modo **bridge**; imagens públicas `linux/amd64`; via **Docker Compose**.

**Base de referência (arquivos fixos, não mudam no teste):**

- `references.json.gz` — **3 milhões** de vetores rotulados (fraude/legítima);
- `mcc_risk.json` — risco por categoria de comerciante (MCC);
- `normalization.json` — constantes de normalização do vetor.

**Pontuação** = componente de **latência (p99)** + componente de **detecção** (erros ponderados:
falsos positivos, falsos negativos e — com peso maior — erros HTTP).

---

## 🧠 A ideia da solução

Uma busca exata (força bruta) sobre 3 milhões de vetores a cada requisição custaria **O(N)** de
tempo e manteria **~216 MB** na RAM (um `float[3_000_000][14]`). Não cabe no orçamento.

A solução **pré-computa um índice** no **build** e em produção apenas o **lê via mmap** e busca rápido:

```
  BUILD (uma vez, dentro do mvn package)            RUNTIME (cada requisição)
  ┌───────────────────┐   ┌───────────────┐          ┌──────────────┐   ┌─────────────────┐
  │references.json.gz │─▶ │IvfIndexBuilder│──┐       │references.ivf│──▶│ IvfIndexReader  │
  └───────────────────┘   │k-means + SQ8  │  │ ▶ .ivf└──────────────┘   │(mmap, off-heap) │
                          └───────────────┘  │         (43 MB)          └───────┬─────────┘
                                             └─────────────▶┌───────────────────▼─────────┐
                                                            │busca IVF: 8 clusters → top-5│
                                                            │  → voto de fraude → decisão │
                                                            └─────────────────────────────┘
```

### As três técnicas combinadas

#### 1. IVF — _Inverted File Index_ (buscar pouco)
Em vez de comparar com tudo, agrupamos os 3 milhões de vetores em **1024 clusters** (k-means).
Cada cluster tem um **centroide**. Na busca, comparamos a consulta só com os **8 clusters mais
próximos** (`nprobe = 8`) → de **3.000.000** para **~23.000** comparações por requisição.

#### 2. SQ8 — _Scalar Quantization_ int8 (ocupar pouco)
Cada vetor são 14 `float` (56 bytes). Guardamos cada dimensão em **1 byte** (0–255):
**56 → 14 bytes** por vetor → o índice inteiro cai de ~168 MB para **~43 MB**.

- **Resíduo:** não quantizamos o valor bruto, e sim a diferença `valor − centroide`. Como dentro de
  um cluster os vetores estão perto do centroide, o resíduo varia pouco → 8 bits ficam muito mais
  finos → **erro mínimo**.
- **Distância assimétrica:** na busca, a **consulta fica em `float` exato** e só o vetor armazenado é
  reconstruído de forma aproximada. Erro de um lado só → mais preciso, de graça.

> Por que **SQ8** e não **PQ**? _Product Quantization_ brilha em vetores grandes (128+ dims). Com só
> **14 dimensões**, o SQ8 entrega praticamente a mesma compressão com matemática trivial e erro menor.

#### 3. mmap — índice fora do heap e compartilhado
O bloco de códigos (~42 MB) é mapeado com `MappedByteBuffer`, ficando como **page cache do SO**
(memória limpa e _reclaimable_ — não causa `OOMKilled` como o heap). Como **as duas instâncias usam a
mesma imagem**, ambas mapeiam o **mesmo arquivo** → o cgroup cobra os ~42 MB de **uma instância só**;
a outra reaproveita as mesmas páginas físicas. É isso que viabiliza 2 réplicas em 350 MB.

---

## ⚙️ Parâmetros

| Parâmetro    | Valor   | Onde                              | Significado                                  |
|--------------|---------|-----------------------------------|----------------------------------------------|
| `NLIST`      | `1024`  | `IvfIndexBuilder`                 | nº de clusters (centroides) criados          |
| `NPROBE`     | `8`     | `ReferenceVectorDataLoader`       | nº de clusters visitados por busca           |
| `K`          | `5`     | `ReferenceVectorDataLoader`       | vizinhos no k-NN                             |
| `THRESHOLD`  | `0.6`   | `ReferenceVectorDataLoader`       | aprova se `fraud_score < 0.6`                |
| `SAMPLE_SIZE`| `50000` | `IvfIndexBuilder`                 | amostra de treino do k-means                 |
| `DIM`        | `14`    | `IvfIndex`                        | dimensões do vetor                           |

---

## ▶️ Como rodar

### Pré-requisitos
- **Docker** + **Docker Compose** (modo recomendado, igual ao do desafio).
- Para build/local: **JDK 21** (o projeto traz o Maven Wrapper `mvnw`).

### Subir a stack completa (nginx + 2 apps)

```bash
docker compose up --build
```

O índice `references.ivf` é **gerado automaticamente durante o build** da imagem (não precisa de passo
manual). A API fica disponível em **http://localhost:9999**.

Teste rápido:

```bash
curl -s -X POST http://localhost:9999/fraud-score \
  -H "Content-Type: application/json" \
  -d '{
    "id": "tx-1",
    "transaction": { "amount": 1200.0, "installments": 3, "requested_at": "2026-06-05T14:30:00Z" },
    "customer": { "avg_amount": 800.0, "tx_count_24h": 5, "known_merchants": ["m-99"] },
    "merchant": { "id": "m-1", "mcc": "5812", "avg_amount": 500.0 },
    "terminal": { "is_online": true, "card_present": false, "km_from_home": 12.5 },
    "last_transaction": { "timestamp": "2026-06-05T13:00:00Z", "km_from_current": 8.0 }
  }'
# → {"approved":false,"fraud_score":1.0}
```

Acompanhar recursos / verificar que não há `OOMKilled`:

```bash
docker stats
docker compose ps
```

Derrubar:

```bash
docker compose down
```

### Rodar localmente (sem Docker)

```bash
# gera o índice + empacota
./mvnw clean package        # no Windows: .\mvnw.cmd clean package

# executa (o índice está embutido; usa o fallback do classpath se /app/references.ivf não existir)
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

### Validar a qualidade do índice (recall vs. busca exata)

Um teste compara a decisão do IVF+SQ8 contra o **brute-force exato** sobre todo o dataset
(desligado por padrão para não pesar no build):

```bash
./mvnw test -Dtest=IvfRecallTest -Divf.recall=true
# → [IVF] decisões iguais ao brute-force: 100,0% | erro médio de score: 0,0007
```

---

## 🗂️ Estrutura do projeto

```
src/main/java/com/joao/rinha/
├── index/
│   ├── IvfIndexBuilder.java   # BUILD: lê o .gz, treina centroides, quantiza (SQ8) e grava o .ivf
│   ├── KMeans.java            # treino dos centroides (k-means++ + Lloyd)
│   ├── IvfIndexWriter.java    # serialização do formato binário .ivf
│   ├── IvfIndexReader.java    # leitura via mmap (runtime) e em heap (testes)
│   └── IvfIndex.java          # índice em memória + busca (assimétrica, resíduo, sem alocação)
├── services/
│   ├── ReferenceVectorDataLoader.java  # mmap do .ivf + processVectorSearch
│   ├── FraudService.java               # monta o vetor de 14 dim. e decide
│   ├── NormalizationDataLoader.java    # carrega normalization.json
│   └── MccRiskDataLoader.java          # carrega mcc_risk.json
├── controller/FraudController.java     # POST /fraud-score e GET /ready
├── dto/ · pojo/                        # contratos de request/response
src/main/resources/static/reference/
├── references.json.gz   # base de 3M vetores (insumo de build; excluída do jar)
├── mcc_risk.json · normalization.json
└── (references.ivf gerado no build)

Dockerfile · docker-compose.yml · nginx.conf · pom.xml
```

### Formato do arquivo `references.ivf`

```
cabeçalho   magic, versão, N, dim, nlist                (20 B)
centroides  1024 × 14 floats                            (~57 KB)
qMin / qMax faixa do resíduo por dimensão               (112 B)
counts      nº de vetores por cluster                   (~4 KB)
codes ★     3M × 14 bytes (resíduos int8 por cluster)   (~42 MB)  ← mmap
labels      0 = legítima, 1 = fraude                    (~3 MB)
```

---

## 🐳 Topologia e limites (`docker-compose.yml`)

| Serviço | CPU    | Memória | Papel                          |
|---------|-------:|--------:|--------------------------------|
| `app1`  | 0.45   | 163 MB  | instância da API               |
| `app2`  | 0.45   | 163 MB  | instância da API               |
| `nginx` | 0.10   | 24 MB   | load balancer (round-robin)    |
| **Total** | **1.00** | **350 MB** | dentro do limite do desafio |

**Tuning da JVM** (índice fora do heap → heap pequeno):

```
-Xmx80m -XX:+UseSerialGC -XX:MaxMetaspaceSize=96m -Xss512k
```

`SerialGC` é o mais econômico em containers com < 1 CPU; `application.properties` mantém o pool do
Tomcat enxuto (`server.tomcat.threads.max=16`) para não disputar a fração de CPU.

**Build (`pom.xml`)**: o `exec-maven-plugin` roda o `IvfIndexBuilder` na fase `process-test-classes`
(acontece dentro do `mvn package`, mesmo com `-DskipTests`); o `maven-jar-plugin` exclui o `.gz` e o
`.ivf` do jar (o `.ivf` é copiado para `/app/references.ivf` pelo `Dockerfile`).

---

## 📊 Resultados

Resultado de uma execução do teste de carga (54.100 requisições):

| Métrica                         | Valor          |
|---------------------------------|----------------|
| Erros HTTP                      | **0**          |
| Acurácia geral                  | **99,76%**     |
| Recall de fraude                | **99,92%**     |
| Precisão de fraude              | **99,77%**     |
| `p99`                           | **111,5 ms** (sem corte) |
| `final_score`                   | **3025,20**    |
| RAM do índice                   | 216 MB → **~45 MB** |
| Comparações por busca           | 3.000.000 → **~23.000** |
| Recall vs. busca exata (amostra)| **100%** (erro médio 0,0007) |

---
## 📝 Licença / Créditos

Projeto desenvolvido para a **[Rinha de Backend 2026](https://github.com/zanfranceschi/rinha-de-backend-2026)**
(desafio criado por [@zanfranceschi](https://github.com/zanfranceschi) e comunidade).
