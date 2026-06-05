package com.joao.rinha.services;

import com.joao.rinha.dto.FraudEvaluationDTO;
import com.joao.rinha.index.IvfIndex;
import com.joao.rinha.index.IvfIndexReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class ReferenceVectorDataLoader {

    private static final int NPROBE = 8;
    private static final int K = 5;
    private static final double THRESHOLD = 0.6;

    @Value("${ivf.path:/app/references.ivf}")
    private String ivfPath;

    @Value("classpath:static/reference/references.ivf")
    private Resource fallbackResource;

    private IvfIndex index;

    @PostConstruct
    public void loadData() {
        try {
            Path file = Path.of(ivfPath);
            if (!Files.isReadable(file)) {
                // Sem o arquivo embarcado (testes/local): extrai do classpath para um temporário.
                file = Files.createTempFile("references", ".ivf");
                file.toFile().deleteOnExit();
                try (InputStream is = fallbackResource.getInputStream()) {
                    Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            this.index = IvfIndexReader.readMapped(file, NPROBE, K);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar o índice references.ivf", e);
        }
    }

    public FraudEvaluationDTO processVectorSearch(float[] inputVector) {
        float fraudScore = index.fraudScore(inputVector);
        boolean isApproved = fraudScore < THRESHOLD;
        return new FraudEvaluationDTO(isApproved, fraudScore);
    }
}
