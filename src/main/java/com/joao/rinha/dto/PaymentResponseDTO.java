package com.joao.rinha.dto;

public class PaymentResponseDTO {
    private boolean approved;
    private float fraud_score;

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public float getFraud_score() {
        return fraud_score;
    }

    public void setFraud_score(float fraud_score) {
        this.fraud_score = fraud_score;
    }
}
