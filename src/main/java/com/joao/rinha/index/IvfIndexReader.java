package com.joao.rinha.index;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Leitura do arquivo {@code .ivf} para um {@link IvfIndex} (runtime). */
public final class IvfIndexReader {

    private IvfIndexReader() {}

    /**
     * Lê via memory-map: o bloco de códigos (~42 MB) fica fora do heap, como page
     * cache do SO (memória limpa, reclaimable e compartilhável entre instâncias que
     * mapeiam o mesmo arquivo). Só os metadados e os labels ficam no heap.
     */
    public static IvfIndex readMapped(Path file, int nprobe, int k) throws IOException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            // Header (5 ints).
            ByteBuffer header = readBlock(ch, 0, 5 * Integer.BYTES);
            int magic = header.getInt();
            int version = header.getInt();
            validate(magic, version);
            int n = header.getInt();
            int dim = header.getInt();
            checkDim(dim);
            int nlist = header.getInt();

            // Bloco de metadados: centroids + qMin + qMax + counts.
            long metaPos = 5L * Integer.BYTES;
            int metaSize = (nlist * dim + 2 * dim) * Float.BYTES + nlist * Integer.BYTES;
            ByteBuffer meta = readBlock(ch, metaPos, metaSize);

            float[][] centroids = new float[nlist][dim];
            for (int c = 0; c < nlist; c++) {
                for (int d = 0; d < dim; d++) centroids[c][d] = meta.getFloat();
            }
            float[] qMin = new float[dim];
            for (int d = 0; d < dim; d++) qMin[d] = meta.getFloat();
            float[] qMax = new float[dim];
            for (int d = 0; d < dim; d++) qMax[d] = meta.getFloat();
            int[] counts = new int[nlist];
            for (int c = 0; c < nlist; c++) counts[c] = meta.getInt();

            // Códigos: mapeados (off-heap).
            long codesPos = metaPos + metaSize;
            long codesLen = (long) n * dim;
            MappedByteBuffer codes = ch.map(FileChannel.MapMode.READ_ONLY, codesPos, codesLen);
            codes.order(ByteOrder.BIG_ENDIAN);

            // Labels: pequenos (~3 MB), no heap.
            ByteBuffer labelBuf = readBlock(ch, codesPos + codesLen, n);
            byte[] labels = new byte[n];
            labelBuf.get(labels);

            return new IvfIndex(n, nlist, nprobe, k, centroids, qMin, qMax, counts, codes, labels);
        }
    }

    /** Leitura totalmente em heap (usada em testes). */
    public static IvfIndex read(InputStream in, int nprobe, int k) throws IOException {
        DataInputStream dis = new DataInputStream(new BufferedInputStream(in));

        validate(dis.readInt(), dis.readInt());
        int n = dis.readInt();
        int dim = dis.readInt();
        checkDim(dim);
        int nlist = dis.readInt();

        float[][] centroids = new float[nlist][dim];
        for (int c = 0; c < nlist; c++) {
            for (int d = 0; d < dim; d++) centroids[c][d] = dis.readFloat();
        }
        float[] qMin = new float[dim];
        for (int d = 0; d < dim; d++) qMin[d] = dis.readFloat();
        float[] qMax = new float[dim];
        for (int d = 0; d < dim; d++) qMax[d] = dis.readFloat();
        int[] counts = new int[nlist];
        for (int c = 0; c < nlist; c++) counts[c] = dis.readInt();

        byte[] codes = new byte[n * dim];
        dis.readFully(codes);
        byte[] labels = new byte[n];
        dis.readFully(labels);

        return new IvfIndex(n, nlist, nprobe, k, centroids, qMin, qMax, counts,
                ByteBuffer.wrap(codes), labels);
    }

    private static ByteBuffer readBlock(FileChannel ch, long position, int size) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        int read = 0;
        while (read < size) {
            int r = ch.read(buf, position + read);
            if (r < 0) throw new IOException("Fim de arquivo inesperado no .ivf");
            read += r;
        }
        buf.flip();
        return buf;
    }

    private static void validate(int magic, int version) throws IOException {
        if (magic != IvfIndexWriter.MAGIC) {
            throw new IOException("Arquivo .ivf inválido (magic): " + Integer.toHexString(magic));
        }
        if (version != IvfIndexWriter.VERSION) {
            throw new IOException("Versão do .ivf não suportada: " + version);
        }
    }

    private static void checkDim(int dim) throws IOException {
        if (dim != IvfIndex.DIM) throw new IOException("Dimensão inesperada no .ivf: " + dim);
    }
}
