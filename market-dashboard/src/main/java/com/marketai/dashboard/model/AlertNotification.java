package com.marketai.dashboard.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "alert_notifications")
public class AlertNotification {

    @Id
    private String id;

    @Indexed
    private String symbol;

    private String message;
    private String type;         // ANOMALY / SIGNAL / SYSTEM
    private double priceChange;
    private double price;
    private boolean emailSent = false;

    @Indexed
    private Instant timestamp = Instant.now();

    public AlertNotification() {}

    public AlertNotification(String symbol, String message, String type,
                             double priceChange, double price) {
        this.symbol = symbol;
        this.message = message;
        this.type = type;
        this.priceChange = priceChange;
        this.price = price;
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getPriceChange() { return priceChange; }
    public void setPriceChange(double priceChange) { this.priceChange = priceChange; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public boolean isEmailSent() { return emailSent; }
    public void setEmailSent(boolean emailSent) { this.emailSent = emailSent; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}