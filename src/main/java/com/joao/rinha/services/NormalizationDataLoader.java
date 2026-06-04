package com.joao.rinha.services;

import com.joao.rinha.configs.NormalizationConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;

@Service
public class NormalizationDataLoader {
    @Value("classpath:static/reference/normalization.json")
    private Resource resourceFile;

    private final tools.jackson.databind.ObjectMapper objectMapper;
    private NormalizationConfig config;

    public NormalizationDataLoader(tools.jackson.databind.ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadData() {
        try {
            this.config = objectMapper.readValue(resourceFile.getInputStream(), NormalizationConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar o arquivo normalization.json", e);
        }
    }

    public NormalizationConfig getConfig() {
        return this.config;
    }
}
