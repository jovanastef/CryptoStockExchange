package cryptoexchange.model;

import java.io.Serializable;
import java.util.UUID;

public class Order implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Side { BUY, SELL }
    
    private final String id;
    private final String clientId;
    private final String symbol;
    private final Side side;
    private final double price;
    private double quantity;
    private final long timestamp;
    
    public Order(String clientId, String symbol, Side side, 
                 double price, double quantity, long timestamp) {
        this.id = UUID.randomUUID().toString();
        this.clientId = clientId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }
    
    public String getId() { return id; }
    public String getClientId() { return clientId; }
    public String getSymbol() { return symbol; }
    public Side getSide() { return side; }
    public double getPrice() { return price; }
    public double getQuantity() { return quantity; }
    public long getTimestamp() { return timestamp; }
    
    public void setQuantity(double quantity) { this.quantity = quantity; }
}