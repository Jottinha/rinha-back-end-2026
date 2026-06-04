package com.joao.rinha.services;

import com.joao.rinha.dto.FraudEvaluationDTO;
import com.joao.rinha.dto.PaymentDTO;
import com.joao.rinha.dto.PaymentResponseDTO;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;

@Service
public class FraudService {

    private NormalizationDataLoader normalizationLoader;
    private final MccRiskDataLoader mccRiskLoader;
    private final ReferenceVectorDataLoader vectorDataLoader;

    public FraudService(NormalizationDataLoader normalizationLoader, MccRiskDataLoader mccRiskLoader, ReferenceVectorDataLoader vectorDataLoader) {
        this.normalizationLoader = normalizationLoader;
        this.mccRiskLoader = mccRiskLoader;
        this.vectorDataLoader = vectorDataLoader;
    }

    public PaymentResponseDTO evaluateTransaction(PaymentDTO dto) {
        float[] transactionVector = transformInVector(dto);
        FraudEvaluationDTO evaluation = vectorDataLoader.processVectorSearch(transactionVector);

        PaymentResponseDTO response = new PaymentResponseDTO();
        response.setApproved(evaluation.isApproved());
        response.setFraud_score(evaluation.fraudScore());

        return response;
    }

    public float[] transformInVector(PaymentDTO dto){
        float[] vector = new float[14];

        vector[0] = limit(dto.getTransaction().getAmount() / normalizationLoader.getConfig().getMaxAmount());
        vector[1] = limit((float) dto.getTransaction().getInstallments() / normalizationLoader.getConfig().getMaxInstallments());
        vector[2] = limit((dto.getTransaction().getAmount() / dto.getCustomer().getAvg_amount()) /
                normalizationLoader.getConfig().getAmountVsAvgRatio());
        int hourUtc = dto.getTransaction().getRequested_at()
                .atZone(ZoneOffset.UTC)
                .getHour();
        vector[3] = limit(hourUtc / 23.0f);
        int dayOfWeek = dto.getTransaction().getRequested_at()
                .atZone(ZoneOffset.UTC)
                .getDayOfWeek().getValue() - 1;
        vector[4] = limit(dayOfWeek / 6.0f);
        vector[5] = -1;
        vector[6] = -1;
        if (dto.getLast_transaction() != null){
            long minutos = Duration.between(
                    dto.getLast_transaction().getTimestamp(),
                    dto.getTransaction().getRequested_at()
            ).toMinutes();
            vector[5] = limit((float) minutos / normalizationLoader.getConfig().getMaxMinutes());
            vector[6] = limit(dto.getLast_transaction().getKm_from_current() / normalizationLoader.getConfig().getMaxKm());
        }
        vector[7] = limit(dto.getTerminal().getKm_from_home() / normalizationLoader.getConfig().getMaxKm());
        vector[8] = limit((float) dto.getCustomer().getTx_count_24h() / normalizationLoader.getConfig().getMaxTxCount24h());
        vector[9] = (dto.getTerminal().isIs_online() ? 1 : 0);
        vector[10] = (dto.getTerminal().isCard_present() ? 1 : 0);
        vector[11] = 1;
        if (Arrays.asList(dto.getCustomer().getKnown_merchants()).contains(dto.getMerchant().getId())){
            vector[11] = 0;
        }
        Map<String, Double> riskMap = mccRiskLoader.getMccRiskMap();
        vector[12] = riskMap.getOrDefault(dto.getMerchant().getMcc(), 0.5).floatValue();
        vector[13] = limit(dto.getMerchant().getAvg_amount() / normalizationLoader.getConfig().getMaxMerchantAvgAmount());

        return vector;
    }

    public float limit(float value){
        if (value > 1.0) return 1.0f;
        if (value < 0) return 0.0f;
        return value;
    }
}
