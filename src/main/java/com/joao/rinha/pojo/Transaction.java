package com.joao.rinha.pojo;

import java.time.Instant;

public class Transaction {
    private float amount;
    private int installments;
    private Instant requested_at;

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public int getInstallments() {
        return installments;
    }

    public void setInstallments(int installments) {
        this.installments = installments;
    }

    public Instant getRequested_at() {
        return requested_at;
    }

    public void setRequested_at(Instant requested_at) {
        this.requested_at = requested_at;
    }
}
