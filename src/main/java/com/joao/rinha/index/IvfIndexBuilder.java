package com.joao.rinha.index;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.GZIPInputStream;

/**
 * Gerador do índice IVF+SQ8 (executado no build, via exec-maven-plugin).
 *
 * <p>Lê {@code references.json.gz}, treina os centroides do IVF numa amostra,
 * quantiza os resíduos em int8 e grava o {@code references.ivf}. O processo é
 * desenhado para baixo consumo de memória: os vetores em float nunca ficam todos
 * residentes ao mesmo tempo — o arquivo é varrido em duas passagens.
 *
 * <pre>args: [inputGz] [outputIvf]</pre>
 */
public final class IvfIndexBuilder {

    private static final int DIM = IvfIndex.DIM;
    private static final int NLIST = 1024;
    private static final int SAMPLE_SIZE = 50_000;
    private static final int KMEANS_ITERS = 15;
    private static final long SEED = 42L;

    @FunctionalInterface
    private interface VecConsumer {
        void accept(float[] vector, boolean isFraud);
    }

    public static void main(String[] args) throws IOException {
        String input = args.length > 0 ? args[0]
                : "target/classes/static/reference/references.json.gz";
        String output = args.length > 1 ? args[1]
                : "target/classes/static/reference/references.ivf";

        long t0 = System.currentTimeMillis();
        System.out.println("[IVF] Gerando índice a partir de " + input);

        // --- Passagem 1: amostra (reservoir) para treino + contagem total ---
        Reservoir reservoir = new Reservoir(SAMPLE_SIZE, SEED);
        int n = forEachVector(input, (v, fraud) -> reservoir.offer(v));
        float[][] sample = reservoir.toArray();
        System.out.printf("[IVF] %d vetores, amostra de treino: %d%n", n, sample.length);

        // --- Treino dos centroides ---
        float[][] centroids = KMeans.train(sample, NLIST, DIM, KMEANS_ITERS, SEED);
        System.out.printf("[IVF] k-means concluído (%d clusters)%n", NLIST);

        // --- Faixa de quantização do resíduo (por dimensão), estimada na amostra ---
        float[] qMin = new float[DIM];
        float[] qMax = new float[DIM];
        Arrays.fill(qMin, Float.MAX_VALUE);
        Arrays.fill(qMax, -Float.MAX_VALUE);
        for (float[] v : sample) {
            int c = KMeans.nearest(v, centroids, DIM);
            for (int d = 0; d < DIM; d++) {
                float r = v[d] - centroids[c][d];
                if (r < qMin[d]) qMin[d] = r;
                if (r > qMax[d]) qMax[d] = r;
            }
        }
        float[] qRange = new float[DIM];
        for (int d = 0; d < DIM; d++) {
            qRange[d] = qMax[d] - qMin[d];
            if (qRange[d] <= 0.0f) qRange[d] = 1.0f; // dimensão constante: evita divisão por zero
        }

        // --- Passagem 2: atribui cluster, quantiza resíduo e agrupa por cluster ---
        ByteArrayOutputStream[] codeBuf = new ByteArrayOutputStream[NLIST];
        ByteArrayOutputStream[] labelBuf = new ByteArrayOutputStream[NLIST];
        for (int c = 0; c < NLIST; c++) {
            codeBuf[c] = new ByteArrayOutputStream();
            labelBuf[c] = new ByteArrayOutputStream();
        }

        forEachVector(input, (v, fraud) -> {
            int c = KMeans.nearest(v, centroids, DIM);
            ByteArrayOutputStream cb = codeBuf[c];
            float[] centroid = centroids[c];
            for (int d = 0; d < DIM; d++) {
                float r = v[d] - centroid[d];
                int q = Math.round((r - qMin[d]) / qRange[d] * 255.0f);
                if (q < 0) q = 0;
                else if (q > 255) q = 255;
                cb.write(q);
            }
            labelBuf[c].write(fraud ? 1 : 0);
        });

        // --- Monta os blocos contíguos agrupados por cluster ---
        int[] counts = new int[NLIST];
        byte[] codes = new byte[n * DIM];
        byte[] labels = new byte[n];
        int vecPos = 0;
        int minCluster = Integer.MAX_VALUE;
        int maxCluster = 0;
        for (int c = 0; c < NLIST; c++) {
            byte[] cb = codeBuf[c].toByteArray();
            byte[] lb = labelBuf[c].toByteArray();
            counts[c] = lb.length;
            System.arraycopy(cb, 0, codes, vecPos * DIM, cb.length);
            System.arraycopy(lb, 0, labels, vecPos, lb.length);
            vecPos += lb.length;
            minCluster = Math.min(minCluster, counts[c]);
            maxCluster = Math.max(maxCluster, counts[c]);
            codeBuf[c] = null; // libera memória progressivamente
            labelBuf[c] = null;
        }
        System.out.printf("[IVF] cluster size: min=%d max=%d média=%d%n",
                minCluster, maxCluster, n / NLIST);

        try (OutputStream out = new FileOutputStream(output)) {
            IvfIndexWriter.write(out, n, DIM, NLIST, centroids, qMin, qMax, counts, codes, labels);
        }

        System.out.printf("[IVF] Arquivo gravado em %s (%.1f MB) em %.1fs%n",
                output, (n * (long) (DIM + 1)) / 1_048_576.0,
                (System.currentTimeMillis() - t0) / 1000.0);
    }

    /** Itera cada vetor do {@code .gz} via streaming, devolvendo o total lido. */
    private static int forEachVector(String gzPath, VecConsumer consumer) throws IOException {
        ObjectMapper mapper = JsonMapper.builder().build();
        int count = 0;
        try (InputStream is = new FileInputStream(gzPath);
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
                count++;
            }
        }
        return count;
    }

    /** Amostragem por reservatório (algoritmo R) para o conjunto de treino. */
    private static final class Reservoir {
        private final float[][] items;
        private final Random rnd;
        private int seen = 0;

        Reservoir(int size, long seed) {
            this.items = new float[size][];
            this.rnd = new Random(seed);
        }

        void offer(float[] vector) {
            if (seen < items.length) {
                items[seen] = vector.clone();
            } else {
                int j = rnd.nextInt(seen + 1);
                if (j < items.length) {
                    items[j] = vector.clone();
                }
            }
            seen++;
        }

        float[][] toArray() {
            if (seen >= items.length) return items;
            return Arrays.copyOf(items, seen);
        }
    }

    private IvfIndexBuilder() {}
}
