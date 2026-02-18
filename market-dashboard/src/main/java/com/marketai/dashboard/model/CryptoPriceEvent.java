package com.marketai.dashboard.model;



import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "crypto_prices")
public class CryptoPriceEvent {

    @Id
    private String id;

    private String symbol;
    private double price;

    public CryptoPriceEvent() {}

    public CryptoPriceEvent(String symbol, double price) {
        this.symbol = symbol;
        this.price = price;
    }

    // getters & setters
}
