package com.joao.rinha.configs;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class NormalizationConfig {

    @JsonProperty("max_amount")
    private int maxAmount;

    @JsonProperty("max_installments")
    private int maxInstallments;

    @JsonProperty("amount_vs_avg_ratio")
    private int amountVsAvgRatio;

    @JsonProperty("max_minutes")
    private int maxMinutes;

    @JsonProperty("max_km")
    private int maxKm;

    @JsonProperty("max_tx_count_24h")
    private int maxTxCount24h;

    @JsonProperty("max_merchant_avg_amount")
    private int maxMerchantAvgAmount;

    public NormalizationConfig() {}

    public int getMaxAmount() { return maxAmount; }
    public void setMaxAmount(int maxAmount) { this.maxAmount = maxAmount; }

    public int getMaxInstallments() { return maxInstallments; }
    public void setMaxInstallments(int maxInstallments) { this.maxInstallments = maxInstallments; }

    public int getAmountVsAvgRatio() { return amountVsAvgRatio; }
    public void setAmountVsAvgRatio(int amountVsAvgRatio) { this.amountVsAvgRatio = amountVsAvgRatio; }

    public int getMaxMinutes() { return maxMinutes; }
    public void setMaxMinutes(int maxMinutes) { this.maxMinutes = maxMinutes; }

    public int getMaxKm() { return maxKm; }
    public void setMaxKm(int maxKm) { this.maxKm = maxKm; }

    public int getMaxTxCount24h() { return maxTxCount24h; }
    public void setMaxTxCount24h(int maxTxCount24h) { this.maxTxCount24h = maxTxCount24h; }

    public int getMaxMerchantAvgAmount() { return maxMerchantAvgAmount; }
    public void setMaxMerchantAvgAmount(int maxMerchantAvgAmount) { this.maxMerchantAvgAmount = maxMerchantAvgAmount; }
}
