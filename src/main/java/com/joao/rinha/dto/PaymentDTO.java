package com.joao.rinha.dto;

import com.joao.rinha.pojo.*;

public class PaymentDTO {
    private String id;
    private Transaction transaction;
    private Customer customer;
    private Merchant merchant;
    private Terminal terminal;
    private LastTransaction last_transaction;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public Merchant getMerchant() {
        return merchant;
    }

    public void setMerchant(Merchant merchant) {
        this.merchant = merchant;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public void setTerminal(Terminal terminal) {
        this.terminal = terminal;
    }

    public LastTransaction getLast_transaction() {
        return last_transaction;
    }

    public void setLast_transaction(LastTransaction last_transaction) {
        this.last_transaction = last_transaction;
    }
}
