package cryptoexchange.model;

import java.io.Serializable;
import java.util.List;

//Podaci o order book-u za jedan instrument i jednu stranu (BID/ASK)

public class OrderBookData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Side { BID, ASK }
    
    private final String symbol;
    private final Side side;
    private final List<Order> orders; // sortirano: BID opadajuce, ASK rastuce
    
    public OrderBookData(String symbol, Side side, List<Order> orders) {
        this.symbol = symbol;
        this.side = side;
        this.orders = orders;
    }
    
    public String getSymbol() {
        return symbol;
    }
    
    public Side getSide() {
        return side;
    }
    
    public List<Order> getOrders() {
        return orders;
    }
}