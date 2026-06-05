package com.joao.rinha.index;

import java.util.Random;

/**
 * K-means (Lloyd) com inicialização k-means++, usado no build para treinar os
 * centroides do IVF a partir de uma amostra do dataset.
 */
final class KMeans {

    private KMeans() {}

    static float[][] train(float[][] data, int k, int dim, int iters, long seed) {
        int n = data.length;
        Random rnd = new Random(seed);

        float[][] centroids = kmeansPlusPlusInit(data, k, dim, rnd);
        int[] assign = new int[n];

        for (int it = 0; it < iters; it++) {
            boolean changed = false;
            for (int i = 0; i < n; i++) {
                int best = nearest(data[i], centroids, dim);
                if (assign[i] != best) {
                    assign[i] = best;
                    changed = true;
                }
            }

            float[][] sum = new float[k][dim];
            int[] cnt = new int[k];
            for (int i = 0; i < n; i++) {
                int c = assign[i];
                cnt[c]++;
                float[] v = data[i];
                float[] s = sum[c];
                for (int d = 0; d < dim; d++) {
                    s[d] += v[d];
                }
            }

            for (int c = 0; c < k; c++) {
                if (cnt[c] > 0) {
                    float inv = 1.0f / cnt[c];
                    for (int d = 0; d < dim; d++) {
                        centroids[c][d] = sum[c][d] * inv;
                    }
                } else {
                    // Cluster vazio: reinicia em um ponto aleatório do dataset.
                    System.arraycopy(data[rnd.nextInt(n)], 0, centroids[c], 0, dim);
                }
            }

            if (!changed && it > 0) break;
        }

        return centroids;
    }

    private static float[][] kmeansPlusPlusInit(float[][] data, int k, int dim, Random rnd) {
        int n = data.length;
        float[][] centroids = new float[k][dim];
        System.arraycopy(data[rnd.nextInt(n)], 0, centroids[0], 0, dim);

        float[] d2 = new float[n];
        java.util.Arrays.fill(d2, Float.MAX_VALUE);

        for (int c = 1; c < k; c++) {
            double sum = 0.0;
            float[] last = centroids[c - 1];
            for (int i = 0; i < n; i++) {
                float dd = squaredDistance(data[i], last, dim);
                if (dd < d2[i]) d2[i] = dd;
                sum += d2[i];
            }

            int chosen = n - 1;
            if (sum <= 0.0) {
                chosen = rnd.nextInt(n);
            } else {
                double r = rnd.nextDouble() * sum;
                double acc = 0.0;
                for (int i = 0; i < n; i++) {
                    acc += d2[i];
                    if (acc >= r) {
                        chosen = i;
                        break;
                    }
                }
            }
            System.arraycopy(data[chosen], 0, centroids[c], 0, dim);
        }

        return centroids;
    }

    static int nearest(float[] v, float[][] centroids, int dim) {
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int c = 0; c < centroids.length; c++) {
            float dist = squaredDistance(v, centroids[c], dim);
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }

    private static float squaredDistance(float[] a, float[] b, int dim) {
        float sum = 0.0f;
        for (int d = 0; d < dim; d++) {
            float diff = a[d] - b[d];
            sum += diff * diff;
        }
        return sum;
    }
}
