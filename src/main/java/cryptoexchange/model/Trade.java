package cryptoexchange.model;

import java.io.Serializable;
import java.util.UUID;

public class Trade implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String id;
    private final String symbol;
    private final double price;
    private final double quantity;
    private final String buyerId;
    private final String sellerId;
    private final long timestamp;
    
    public Trade(String symbol, double price, double quantity, 
                 String buyerId, String sellerId, long timestamp) {
        this.id = UUID.randomUUID().toString();
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.timestamp = timestamp;
    }
    
    public String getId() { return id; }
    public String getSymbol() { return symbol; }
    public double getPrice() { return price; }
    public double getQuantity() { return quantity; }
    public String getBuyerId() { return buyerId; }
    public String getSellerId() { return sellerId; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return String.format("%d|%s|%.2f|%.4f|%s|%s", 
            timestamp, symbol, price, quantity, 
            buyerId.substring(0, 8), sellerId.substring(0, 8));
    }
}