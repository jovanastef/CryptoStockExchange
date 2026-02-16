package cryptoexchange.server;

import cryptoexchange.model.*;
import cryptoexchange.rmi.ExchangeServiceInterface;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

//Implementacija RMI servisa za kripto berzu

public class ExchangeServiceImpl extends UnicastRemoteObject implements ExchangeServiceInterface {
    private static final long serialVersionUID = 1L;
    
    // Mapa svih instrumenata (BTC, ETH, SOL...)
    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();
    
    // Order book za svaki instrument
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    
    // Nalozi klijenata i njihovi balansi
    private final Map<String, ClientAccount> clientAccounts = new ConcurrentHashMap<>();
    
    // Istorija svih izvrsenih trgovina
    private final List<Trade> tradeHistory = new ArrayList<>();
    
    // Trenutno simulaciono vreme u minutima (0 = ponedeljak 00:00)
    private long simulationTimeMinutes = 0;
    
    protected ExchangeServiceImpl() throws RemoteException {
        super();
        initializeInstruments();
        initializeOrderBooks();
        System.out.println("[Server] Exchange service initialized with " + instruments.size() + " instruments");
    }
    
    //Inicijalizuje 12+ kripto instrumenata sa realnim početnim cenama
    private void initializeInstruments() {
        // Realne pocetne cene sa CoinMarketCap
        addInstrument("BTC", "Bitcoin", 68883.07);
        addInstrument("ETH", "Ethereum", 1985.46);
        addInstrument("SOL", "Solana", 85.92);
        addInstrument("BNB", "Binance Coin", 618.34);
        addInstrument("XRP", "XRP", 1.49);
        addInstrument("ADA", "Cardano", 0.2839);
        addInstrument("DOGE", "Dogecoin", 0.1027);
        addInstrument("USDT", "Tether", 0.9997);
        addInstrument("USDC", "USDC", 0.9999);
        addInstrument("TRX", "Tron", 0.2803);
        addInstrument("BCH", "Bitcoin Cash", 556.43);
        addInstrument("LEO", "Unus Sed Leo", 8.49);
    }
    
    private void addInstrument(String symbol, String name, double openingPrice) {
        instruments.put(symbol, new Instrument(symbol, name, openingPrice));
    }
    
    private void initializeOrderBooks() {
        instruments.keySet().forEach(symbol -> 
            orderBooks.put(symbol, new OrderBook(symbol)));
    }
    
    @Override
    public MarketSnapshot getMarketSnapshot() throws RemoteException {
        return new MarketSnapshot(simulationTimeMinutes, new ArrayList<>(instruments.values()));
    }
    
    @Override
    public OrderBookData getOrderBook(String symbol, OrderBookData.Side side) throws RemoteException {
        OrderBook orderBook = orderBooks.get(symbol.toUpperCase());
        if (orderBook == null) {
            throw new RemoteException("Instrument not found: " + symbol);
        }
        
        List<Order> orders = (side == OrderBookData.Side.BID) ? 
            new ArrayList<>(orderBook.getBids()) : 
            new ArrayList<>(orderBook.getAsks());
        
        return new OrderBookData(symbol, side, orders);
    }
    
    @Override
    public OrderConfirmation placeOrder(OrderRequest request) throws RemoteException {
        // Provera validnosti simbola
        if (!instruments.containsKey(request.getSymbol().toUpperCase())) {
            return new OrderConfirmation("", OrderConfirmation.Status.REJECTED, 
                "Invalid instrument symbol: " + request.getSymbol());
        }
        
        // Provera klijenta
        ClientAccount account = clientAccounts.get(request.getClientId());
        if (account == null) {
            return new OrderConfirmation("", OrderConfirmation.Status.REJECTED, 
                "Unknown client. Please register first.");
        }
        
        // Kreiraj i dodaj order u order book
        Order order = new Order(
            request.getClientId(),
            request.getSymbol().toUpperCase(),
            request.getSide(),
            request.getPrice(),
            request.getQuantity(),
            simulationTimeMinutes
        );
        
        OrderBook orderBook = orderBooks.get(request.getSymbol().toUpperCase());
        orderBook.addOrder(order);
        
        return new OrderConfirmation(order.getId(), OrderConfirmation.Status.ACCEPTED, "");
    }
    
    @Override
    public List<Trade> getTradeHistory(String symbol, long simulationDayStart) throws RemoteException {
        long dayEnd = simulationDayStart + 24 * 60; // 24 sata = 1440 minuta
        
        return tradeHistory.stream()
            .filter(trade -> trade.getSymbol().equalsIgnoreCase(symbol))
            .filter(trade -> trade.getTimestamp() >= simulationDayStart && trade.getTimestamp() < dayEnd)
            .collect(Collectors.toList());
    }
    
    @Override
    public String registerClient(String clientName) throws RemoteException {
        String clientId = UUID.randomUUID().toString();
        clientAccounts.put(clientId, new ClientAccount(clientId, clientName, 100000.0)); // $100k pocetni balance
        System.out.println("[Server] New client registered: " + clientName + " (" + clientId.substring(0, 8) + "...)"); 
        return clientId;
    }
    
    // Interna klasa za order book
    private static class OrderBook {
        private final String symbol;
        private final List<Order> bids = new ArrayList<>(); // descending by price
        private final List<Order> asks = new ArrayList<>(); // ascending by price
        
        public OrderBook(String symbol) {
            this.symbol = symbol;
        }
        
        public synchronized void addOrder(Order order) {
            if (order.getSide() == Order.Side.BUY) {
                bids.add(order);
                bids.sort((o1, o2) -> Double.compare(o2.getPrice(), o1.getPrice())); // descending
            } else { // SELL
                asks.add(order);
                asks.sort(Comparator.comparingDouble(Order::getPrice)); // ascending
            }
        }
        
        public List<Order> getBids() {
            return Collections.unmodifiableList(bids);
        }
        
        public List<Order> getAsks() {
            return Collections.unmodifiableList(asks);
        }
        
        public String getSymbol() {
            return symbol;
        }
    }
    
    // Interna klasa za klijentski nalog
    private static class ClientAccount {
        private final String id;
        private final String name;
        private double balance; // USDT balance
        private final Map<String, Double> assets = new ConcurrentHashMap<>(); // symbol -> quantity
        
        public ClientAccount(String id, String name, double initialBalance) {
            this.id = id;
            this.name = name;
            this.balance = initialBalance;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public double getBalance() { return balance; }
        public double getAssetBalance(String symbol) { return assets.getOrDefault(symbol, 0.0); }
    }
}