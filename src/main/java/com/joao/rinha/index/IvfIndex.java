package com.joao.rinha.index;

import java.nio.ByteBuffer;

/**
 * Índice IVF + SQ8 carregado em memória.
 *
 * <p>Os vetores ficam guardados como resíduos quantizados em 1 byte por dimensão
 * (Scalar Quantization int8). Na busca usamos distância assimétrica: a query
 * permanece em float exato e apenas o vetor armazenado é reconstruído de forma
 * aproximada a partir do centroide do seu cluster:
 *
 * <pre>x̂[d] = centroide[c][d] + qMin[d] + (code &amp; 0xFF) * qScale[d]</pre>
 *
 * onde {@code qScale[d] = (qMax[d] - qMin[d]) / 255}.
 */
public final class IvfIndex {

    public static final int DIM = 14;

    private final int n;
    private final int nlist;
    private final int nprobe;
    private final int k;

    private final float[][] centroids;   // [nlist][DIM] - usado na seleção de clusters
    private final float[][] clusterBias; // [nlist][DIM] = centroids[c][d] + qMin[d] (reconstrução)
    private final float[] qScale;        // [DIM] = (qMax - qMin) / 255
    private final int[] offsets;         // [nlist] início (em vetores) de cada cluster em codes/labels
    private final int[] counts;          // [nlist] quantidade de vetores por cluster
    private final ByteBuffer codes;      // [n * DIM] resíduos quantizados, agrupados por cluster (mmap)
    private final byte[] labels;         // [n] 0 = legítimo, 1 = fraude, agrupados por cluster

    public IvfIndex(int n, int nlist, int nprobe, int k,
                    float[][] centroids, float[] qMin, float[] qMax,
                    int[] counts, ByteBuffer codes, byte[] labels) {
        this.n = n;
        this.nlist = nlist;
        this.nprobe = Math.min(nprobe, nlist);
        this.k = k;
        this.centroids = centroids;
        this.counts = counts;
        this.codes = codes;
        this.labels = labels;

        this.qScale = new float[DIM];
        for (int d = 0; d < DIM; d++) {
            this.qScale[d] = (qMax[d] - qMin[d]) / 255.0f;
        }

        this.clusterBias = new float[nlist][DIM];
        for (int c = 0; c < nlist; c++) {
            for (int d = 0; d < DIM; d++) {
                this.clusterBias[c][d] = centroids[c][d] + qMin[d];
            }
        }

        this.offsets = new int[nlist];
        int acc = 0;
        for (int c = 0; c < nlist; c++) {
            this.offsets[c] = acc;
            acc += counts[c];
        }
    }

    /**
     * Fração dos {@code k} vizinhos mais próximos que são fraude, varrendo apenas
     * os {@code nprobe} clusters mais próximos da query.
     *
     * <p>Hot path sem alocação por candidato: top-N de clusters e top-K de vizinhos
     * são mantidos em arrays primitivos pequenos, substituindo o pior elemento.
     */
    public float fraudScore(float[] query) {
        // --- Seleção dos nprobe clusters mais próximos (top-N por substituição do pior) ---
        int[] probeId = new int[nprobe];
        float[] probeDist = new float[nprobe];
        int probeCount = 0;
        int probeWorst = 0; // índice do cluster mais distante entre os selecionados

        for (int c = 0; c < nlist; c++) {
            float[] centroid = centroids[c];
            float dist = 0.0f;
            for (int d = 0; d < DIM; d++) {
                float diff = query[d] - centroid[d];
                dist += diff * diff;
            }

            if (probeCount < nprobe) {
                probeId[probeCount] = c;
                probeDist[probeCount] = dist;
                probeCount++;
                if (probeCount == nprobe) probeWorst = argMax(probeDist, nprobe);
            } else if (dist < probeDist[probeWorst]) {
                probeId[probeWorst] = c;
                probeDist[probeWorst] = dist;
                probeWorst = argMax(probeDist, nprobe);
            }
        }

        // --- KNN nos clusters selecionados (top-K por substituição do pior) ---
        float[] bestDist = new float[k];
        boolean[] bestFraud = new boolean[k];
        int found = 0;
        int knnWorst = 0;

        for (int pi = 0; pi < probeCount; pi++) {
            int c = probeId[pi];
            float[] bias = clusterBias[c];
            int start = offsets[c];
            int end = start + counts[c];

            for (int v = start; v < end; v++) {
                int p = v * DIM;
                float dist = 0.0f;
                for (int d = 0; d < DIM; d++) {
                    float xhat = bias[d] + (codes.get(p + d) & 0xFF) * qScale[d];
                    float diff = query[d] - xhat;
                    dist += diff * diff;
                }

                if (found < k) {
                    bestDist[found] = dist;
                    bestFraud[found] = labels[v] != 0;
                    found++;
                    if (found == k) knnWorst = argMax(bestDist, k);
                } else if (dist < bestDist[knnWorst]) {
                    bestDist[knnWorst] = dist;
                    bestFraud[knnWorst] = labels[v] != 0;
                    knnWorst = argMax(bestDist, k);
                }
            }
        }

        if (found == 0) return 0.0f;

        int fraudCount = 0;
        for (int i = 0; i < found; i++) {
            if (bestFraud[i]) fraudCount++;
        }
        return (float) fraudCount / found;
    }

    private static int argMax(float[] values, int size) {
        int idx = 0;
        float max = values[0];
        for (int i = 1; i < size; i++) {
            if (values[i] > max) {
                max = values[i];
                idx = i;
            }
        }
        return idx;
    }

    public int size() {
        return n;
    }

    public int nlist() {
        return nlist;
    }
}
