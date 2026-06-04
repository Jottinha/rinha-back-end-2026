package com.joao.rinha.services;

import com.joao.rinha.dto.FraudEvaluationDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.PriorityQueue;
import java.util.zip.GZIPInputStream;

@Service
public class ReferenceVectorDataLoader {

    @Value("classpath:static/reference/references.json.gz")
    private Resource resourceFile;

    private final tools.jackson.databind.ObjectMapper objectMapper;
    private float[][] datasetVectors;
    private boolean[] datasetLabels;
    private int totalRecords = 0;
    public record Neighbor(int index, float distance, boolean isFraud) {}

    public ReferenceVectorDataLoader(tools.jackson.databind.ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadData() {
        int expectedSize = 3_000_000;
        datasetVectors = new float[expectedSize][];
        datasetLabels = new boolean[expectedSize];

        try (InputStream is = resourceFile.getInputStream();
             GZIPInputStream gis = new GZIPInputStream(is);
             tools.jackson.core.JsonParser parser = objectMapper.tokenStreamFactory().createParser(gis)) {

            if (parser.nextToken() != tools.jackson.core.JsonToken.START_ARRAY) {
                throw new IllegalStateException("O JSON deve iniciar com um array");
            }

            int currentIndex = 0;

            while (parser.nextToken() == tools.jackson.core.JsonToken.START_OBJECT && currentIndex < expectedSize) {
                boolean isFraud = false;
                float[] vector = new float[14];
                int vectorPos = 0;

                while (parser.nextToken() != tools.jackson.core.JsonToken.END_OBJECT) {
                    String fieldName = parser.currentName();
                    parser.nextToken();

                    if ("label".equals(fieldName)) {
                        isFraud = "fraud".equals(parser.getString());
                    } else if ("vector".equals(fieldName)) {
                        while (parser.nextToken() != tools.jackson.core.JsonToken.END_ARRAY) {
                            if (parser.currentToken() == tools.jackson.core.JsonToken.VALUE_NUMBER_FLOAT ||
                                    parser.currentToken() == tools.jackson.core.JsonToken.VALUE_NUMBER_INT) {
                                vector[vectorPos++] = parser.getFloatValue();
                            }
                        }
                    }
                }

                datasetVectors[currentIndex] = vector;
                datasetLabels[currentIndex] = isFraud;
                currentIndex++;
            }
            this.totalRecords = currentIndex;

        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar e processar o arquivo references.json.gz", e);
        }
    }

    public FraudEvaluationDTO processVectorSearch(float[] inputVector) {
        int k = 5;
        double threshold = 0.6;

        PriorityQueue<Neighbor> topK = new PriorityQueue<>(
                (a, b) -> Float.compare(b.distance(), a.distance())
        );

        for (int i = 0; i < totalRecords; i++) {
            float distance = calculateSquaredDistance(inputVector, datasetVectors[i]);
            if (topK.size() < k) {
                topK.offer(new Neighbor(i, distance, datasetLabels[i]));
            } else if (distance < topK.peek().distance()) {
                topK.poll();
                topK.offer(new Neighbor(i, distance, datasetLabels[i]));
            }
        }

        int fraudCount = 0;
        for (Neighbor n : topK) {
            if (n.isFraud()) fraudCount++;
        }

        float fraudScore = (float) fraudCount / topK.size();
        boolean isApproved = fraudScore < threshold;

        return new FraudEvaluationDTO(isApproved, fraudScore);
    }

    private float calculateSquaredDistance(float[] v1, float[] v2) {
        float sum = 0.0f;
        for (int i = 0; i < 14; i++) {
            float diff = v1[i] - v2[i];
            sum += diff * diff;
        }
        return sum;
    }
}