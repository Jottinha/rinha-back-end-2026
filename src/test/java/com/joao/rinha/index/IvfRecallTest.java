package com.joao.rinha.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validação manual de recall: compara a decisão do IVF+SQ8 contra o brute-force
 * exato (k=5 sobre os 3M vetores em full-precision). Roda só com -Divf.recall=true.
 */
@EnabledIfSystemProperty(named = "ivf.recall", matches = "true")
class IvfRecallTest {

    private static final int DIM = IvfIndex.DIM;
    private static final int K = 5;
    private static final int NPROBE = 8;
    private static final double THRESHOLD = 0.6;
    private static final int QUERIES = 300;

    private record TrueNeighbor(float distance, boolean isFraud) {}

    @Test
    void ivfDecisionMatchesBruteForce() throws Exception {
        IvfIndex index;
        try (InputStream is = resource("static/reference/references.ivf")) {
            index = IvfIndexReader.read(is, NPROBE, K);
        }

        // Seleciona QUERIES vetores de consulta (reservoir determinístico).
        float[][] queries = new float[QUERIES][];
        Random rnd = new Random(7);
        int[] seen = {0};
        forEachVector((v, fraud) -> {
            int i = seen[0]++;
            if (i < QUERIES) {
                queries[i] = v.clone();
            } else {
                int j = rnd.nextInt(i + 1);
                if (j < QUERIES) queries[j] = v.clone();
            }
        });

        // Ground truth: top-5 exato de cada query em uma passagem sobre todo o dataset.
        @SuppressWarnings("unchecked")
        PriorityQueue<TrueNeighbor>[] heaps = new PriorityQueue[QUERIES];
        for (int q = 0; q < QUERIES; q++) {
            heaps[q] = new PriorityQueue<>((a, b) -> Float.compare(b.distance(), a.distance()));
        }
        forEachVector((v, fraud) -> {
            for (int q = 0; q < QUERIES; q++) {
                float dist = 0f;
                float[] query = queries[q];
                for (int d = 0; d < DIM; d++) {
                    float diff = query[d] - v[d];
                    dist += diff * diff;
                }
                PriorityQueue<TrueNeighbor> h = heaps[q];
                if (h.size() < K) h.offer(new TrueNeighbor(dist, fraud));
                else if (dist < h.peek().distance()) {
                    h.poll();
                    h.offer(new TrueNeighbor(dist, fraud));
                }
            }
        });

        int decisionMatches = 0;
        double scoreErrorSum = 0;
        for (int q = 0; q < QUERIES; q++) {
            int fraud = 0;
            for (TrueNeighbor n : heaps[q]) if (n.isFraud()) fraud++;
            float trueScore = (float) fraud / heaps[q].size();
            float ivfScore = index.fraudScore(queries[q]);

            boolean trueApproved = trueScore < THRESHOLD;
            boolean ivfApproved = ivfScore < THRESHOLD;
            if (trueApproved == ivfApproved) decisionMatches++;
            scoreErrorSum += Math.abs(trueScore - ivfScore);
        }

        double decisionAgreement = (double) decisionMatches / QUERIES;
        double meanScoreError = scoreErrorSum / QUERIES;
        System.out.printf("[IVF] decisões iguais ao brute-force: %.1f%% | erro médio de score: %.4f%n",
                decisionAgreement * 100, meanScoreError);

        assertTrue(decisionAgreement > 0.9,
                "Concordância de decisão muito baixa: " + decisionAgreement);
    }

    private InputStream resource(String path) {
        return getClass().getClassLoader().getResourceAsStream(path);
    }

    @FunctionalInterface
    private interface VecConsumer {
        void accept(float[] vector, boolean isFraud);
    }

    private void forEachVector(VecConsumer consumer) throws Exception {
        ObjectMapper mapper = JsonMapper.builder().build();
        try (InputStream is = resource("static/reference/references.json.gz");
             GZIPInputStream gis = new GZIPInputStream(is);
             JsonParser parser = mapper.tokenStreamFactory().createParser(gis)) {

            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("O JSON deve iniciar com um array");
            }
            while (parser.nextToken() == JsonToken.START_OBJECT) {
                boolean isFraud = false;
                float[] vector = new float[DIM];
                int pos = 0;
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();
                    if ("label".equals(fieldName)) {
                        isFraud = "fraud".equals(parser.getString());
                    } else if ("vector".equals(fieldName)) {
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            JsonToken t = parser.currentToken();
                            if (t == JsonToken.VALUE_NUMBER_FLOAT || t == JsonToken.VALUE_NUMBER_INT) {
                                vector[pos++] = parser.getFloatValue();
                            }
                        }
                    }
                }
                consumer.accept(vector, isFraud);
            }
        }
    }
}
