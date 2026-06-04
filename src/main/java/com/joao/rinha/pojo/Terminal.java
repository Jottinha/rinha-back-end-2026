package com.joao.rinha.pojo;

import java.math.BigDecimal;

public class Terminal {
    private boolean is_online;
    private boolean card_present;
    private float km_from_home;

    public boolean isIs_online() {
        return is_online;
    }

    public void setIs_online(boolean is_online) {
        this.is_online = is_online;
    }

    public boolean isCard_present() {
        return card_present;
    }

    public void setCard_present(boolean card_present) {
        this.card_present = card_present;
    }

    public float getKm_from_home() {
        return km_from_home;
    }

    public void setKm_from_home(float km_from_home) {
        this.km_from_home = km_from_home;
    }
}
