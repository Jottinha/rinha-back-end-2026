package com.joao.rinha.index;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Serialização do índice IVF+SQ8 (formato {@code .ivf}, big-endian).
 *
 * <pre>
 * int   magic   = 0x49564653 ("IVFS")
 * int   version = 1
 * int   n                       número de vetores
 * int   dim                     dimensão (14)
 * int   nlist                   número de clusters
 * float[nlist*dim] centroids    centroides (exatos)
 * float[dim] qMin               mínimo do resíduo por dimensão
 * float[dim] qMax               máximo do resíduo por dimensão
 * int[nlist] counts             vetores por cluster (offsets = soma de prefixo)
 * byte[n*dim] codes             resíduos quantizados, agrupados por cluster
 * byte[n] labels                0 = legítimo, 1 = fraude, agrupados por cluster
 * </pre>
 */
final class IvfIndexWriter {

    static final int MAGIC = 0x49564653;
    static final int VERSION = 1;

    private IvfIndexWriter() {}

    static void write(OutputStream out, int n, int dim, int nlist,
                      float[][] centroids, float[] qMin, float[] qMax,
                      int[] counts, byte[] codes, byte[] labels) throws IOException {
        DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(out));

        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);
        dos.writeInt(n);
        dos.writeInt(dim);
        dos.writeInt(nlist);

        for (int c = 0; c < nlist; c++) {
            for (int d = 0; d < dim; d++) {
                dos.writeFloat(centroids[c][d]);
            }
        }
        for (int d = 0; d < dim; d++) dos.writeFloat(qMin[d]);
        for (int d = 0; d < dim; d++) dos.writeFloat(qMax[d]);
        for (int c = 0; c < nlist; c++) dos.writeInt(counts[c]);

        dos.write(codes, 0, n * dim);
        dos.write(labels, 0, n);

        dos.flush();
    }
}
