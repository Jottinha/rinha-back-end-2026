package com.joao.rinha.pojo;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

public class LastTransaction {
    private Instant timestamp;
    private float km_from_current;

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public float getKm_from_current() {
        return km_from_current;
    }

    public void setKm_from_current(float km_from_current) {
        this.km_from_current = km_from_current;
    }
}
