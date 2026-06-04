package com.joao.rinha.pojo;

import java.math.BigDecimal;

public class Customer {
    private float avg_amount;
    private int tx_count_24h;
    private String[] known_merchants;

    public float getAvg_amount() {
        return avg_amount;
    }

    public void setAvg_amount(float avg_amount) {
        this.avg_amount = avg_amount;
    }

    public int getTx_count_24h() {
        return tx_count_24h;
    }

    public void setTx_count_24h(int tx_count_24h) {
        this.tx_count_24h = tx_count_24h;
    }

    public String[] getKnown_merchants() {
        return known_merchants;
    }

    public void setKnown_merchants(String[] known_merchants) {
        this.known_merchants = known_merchants;
    }
}
