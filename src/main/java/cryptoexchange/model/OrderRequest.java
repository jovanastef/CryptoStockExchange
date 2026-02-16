package cryptoexchange.model;

import java.io.Serializable;

//Zahtev klijenta za slanje naloga

public class OrderRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String clientId;
    private final String symbol;
    private final Order.Side side;
    private final double price;
    private final double quantity;
    
    public OrderRequest(String clientId, String symbol, Order.Side side, 
                        double price, double quantity) {
        this.clientId = clientId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
    }
    
    public String getClientId() {
        return clientId;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public Order.Side getSide() {
        return side;
    }
    
    public double getPrice() {
        return price;
    }
    
    public double getQuantity() {
        return quantity;
    }
}