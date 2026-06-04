package com.joao.rinha.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.Map;

@Service
public class MccRiskDataLoader {

    @Value("classpath:static/reference/mcc_risk.json")
    private Resource resourceFile;

    private final tools.jackson.databind.ObjectMapper objectMapper;
    private Map<String, Double> mccRiskMap;

    public MccRiskDataLoader(tools.jackson.databind.ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadData() {
        try {
            this.mccRiskMap = objectMapper.readValue(
                    resourceFile.getInputStream(),
                    new tools.jackson.core.type.TypeReference<Map<String, Double>>() {}
            );
        } catch (IOException e) {
            throw new RuntimeException("Falha ao carregar o arquivo mcc_risk.json", e);
        }
    }

    public Map<String, Double> getMccRiskMap() {
        return mccRiskMap;
    }
}
