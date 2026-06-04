package com.joao.rinha.pojo;

import java.math.BigDecimal;

public class Merchant {
    private String id;
    private String mcc;
    private float avg_amount;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMcc() {
        return mcc;
    }

    public void setMcc(String mcc) {
        this.mcc = mcc;
    }

    public float getAvg_amount() {
        return avg_amount;
    }

    public void setAvg_amount(float avg_amount) {
        this.avg_amount = avg_amount;
    }
}
