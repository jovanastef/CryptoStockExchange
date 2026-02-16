package cryptoexchange.model;

import java.io.Serializable;

public class Instrument implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String symbol;
    private final String name;
    private final double openingPrice;
    private double currentPrice;
    private double change1h;
    private double change24h;
    private double change7d;
    private long lastUpdateTimestamp;
    
    public Instrument(String symbol, String name, double openingPrice) {
        this.symbol = symbol;
        this.name = name;
        this.openingPrice = openingPrice;
        this.currentPrice = openingPrice;
        this.lastUpdateTimestamp = 0;
    }
    
    // Getters
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public double getOpeningPrice() { return openingPrice; }
    public double getCurrentPrice() { return currentPrice; }
    public double getChange1h() { return change1h; }
    public double getChange24h() { return change24h; }
    public double getChange7d() { return change7d; }
    public long getLastUpdateTimestamp() { return lastUpdateTimestamp; }
    
    // Setters
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
    public void setChange1h(double change1h) { this.change1h = change1h; }
    public void setChange24h(double change24h) { this.change24h = change24h; }
    public void setChange7d(double change7d) { this.change7d = change7d; }
    public void setLastUpdateTimestamp(long lastUpdateTimestamp) { 
        this.lastUpdateTimestamp = lastUpdateTimestamp; 
    }
}