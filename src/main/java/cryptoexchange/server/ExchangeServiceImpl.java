package cryptoexchange.server;

import cryptoexchange.model.*;
import cryptoexchange.rmi.ExchangeServiceInterface;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.io.IOException;

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
    private final List<Trade> tradeHistory = new CopyOnWriteArrayList<>();
    
    private final MarketDataBroadcaster broadcaster;
    private final TradeArchiver tradeArchiver;
    private final ScheduledExecutorService simulationScheduler;
    private final AtomicLong simulationTimeMinutes = new AtomicLong(0);
    
    // Konfiguracija simulacije: 1 realna sekunda = 1 simulacioni minut
    private static final long SIMULATION_TICK_MS = 1000; // 1 sekunda
    
    protected ExchangeServiceImpl() throws RemoteException {
        super();
        try {
            this.broadcaster = new MarketDataBroadcaster(9090);
            this.tradeArchiver = new TradeArchiver("data/trades_archive.txt");
        } catch (IOException e) {
            throw new RemoteException("Failed to initialize server infrastructure", e);
        }
        this.simulationScheduler = Executors.newScheduledThreadPool(2);
        
        initializeInstruments();
        initializeOrderBooks();
        startSimulationEngine();
        
        System.out.println("[Server] Exchange service initialized with " + instruments.size() + " instruments");
        System.out.println("[Server] Market data broadcast on TCP port 9090");
        System.out.println("[Server] Simulation speed: 1 real second = 1 simulation minute");
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
    
    private void startSimulationEngine() {
        simulationScheduler.scheduleAtFixedRate(() -> {
            long currentTick = simulationTimeMinutes.addAndGet(1);
            
            instruments.values().forEach(instrument -> {
                double volatility = getVolatility(instrument.getSymbol());
                double changePercent = (Math.random() * 2 - 1) * volatility;
                double oldPrice = instrument.getCurrentPrice();
                double newPrice = oldPrice * (1 + changePercent / 100);
                
                instrument.setCurrentPrice(newPrice);
                instrument.setLastUpdateTimestamp(currentTick);
                
                double change = ((newPrice - oldPrice) / oldPrice) * 100;
                instrument.setChange1h(change * 0.8);
                instrument.setChange24h(change * 1.2);
                instrument.setChange7d(change * 0.9);
                
                broadcaster.broadcastPriceUpdate(
                        instrument.getSymbol(),
                        currentTick,
                        newPrice,
                        instrument.getChange1h(),
                        instrument.getChange24h(),
                        instrument.getChange7d()
                    );
                });
                
                if (currentTick % 2 == 0) {
                    matchOrders();
                }
                
                if (currentTick % 30 == 0) {
                    System.out.printf("[Server] Simulation time: %02d:%02d (clients: %d)%n",
                        (currentTick / 60) % 24, currentTick % 60, broadcaster.getClientCount());
                }
            }, SIMULATION_TICK_MS, SIMULATION_TICK_MS, TimeUnit.MILLISECONDS);
    }
    
    private double getVolatility(String symbol) {
        switch (symbol) {
            case "BTC": return 0.8;
            case "ETH": return 1.2;
            case "SOL": return 3.5;
            case "DOGE": return 5.0;
            default: return 2.0;
        }
    }
    
    private void matchOrders() {
        orderBooks.values().forEach(orderBook -> {
            List<Order> bids = orderBook.getBids();
            List<Order> asks = orderBook.getAsks();
            
            while (!bids.isEmpty() && !asks.isEmpty()) {
                Order bestBid = bids.get(0);
                Order bestAsk = asks.get(0);
                
                if (bestBid.getPrice() >= bestAsk.getPrice()) {
                    double tradePrice = bestAsk.getPrice();
                    double tradeQuantity = Math.min(bestBid.getQuantity(), bestAsk.getQuantity());
                    
                    Trade trade = new Trade(
                        orderBook.getSymbol(),
                        tradePrice,
                        tradeQuantity,
                        bestBid.getClientId(),
                        bestAsk.getClientId(),
                        simulationTimeMinutes.get()
                    );
                    
                    bestBid.setQuantity(bestBid.getQuantity() - tradeQuantity);
                    bestAsk.setQuantity(bestAsk.getQuantity() - tradeQuantity);
                    
                    if (bestBid.getQuantity() <= 0.0001) bids.remove(0);
                    if (bestAsk.getQuantity() <= 0.0001) asks.remove(0);
                    
                    updateClientBalances(trade);
                    tradeHistory.add(trade);
                    tradeArchiver.archiveTrade(trade);
                    
                    broadcaster.broadcastTrade(
                        trade.getSymbol(),
                        trade.getTimestamp(),
                        trade.getPrice(),
                        trade.getQuantity(),
                        trade.getBuyerId(),
                        trade.getSellerId()
                    );
                    
                    System.out.printf("[Server] TRADE: %s %.4f @ $%.2f (%s ↔ %s)%n",
                            trade.getSymbol(),
                            trade.getQuantity(),
                            trade.getPrice(),
                            trade.getBuyerId().substring(0, 8),
                            trade.getSellerId().substring(0, 8));
                    } else {
                        break;
                    }
                }
            });
    }
    
    private void updateClientBalances(Trade trade) {
        ClientAccount buyer = clientAccounts.get(trade.getBuyerId());
        ClientAccount seller = clientAccounts.get(trade.getSellerId());
        
        if (buyer == null || seller == null) {
            System.err.println("[WARN] Buyer/seller account missing for trade");
            return;
        }
        
        double totalCost = trade.getPrice() * trade.getQuantity();
        
        // Da li buyer ima dovoljno sredstava?
        if (!buyer.subtractBalance(totalCost)) {
            System.err.println("[CRITICAL] Buyer balance mismatch: " + buyer.getId() + 
                              " needed $" + totalCost + ", has $" + buyer.getBalance());
            return; // PREKINI ne azuriraj dalje
        }
        buyer.addAsset(trade.getSymbol(), trade.getQuantity());
        
        // Da li seller ima dovoljno asseta?
        if (!seller.subtractAsset(trade.getSymbol(), trade.getQuantity())) {
            System.err.println("[CRITICAL] Seller asset mismatch: " + seller.getId() + 
                              " needed " + trade.getQuantity() + " " + trade.getSymbol());
            // ROLLABACK buyer-a (jer smo vec oduzeli balance i dodali asset)
            buyer.subtractAsset(trade.getSymbol(), trade.getQuantity());
            buyer.addBalance(totalCost);
            return;
        }
        seller.addBalance(totalCost);
        
        System.out.println("[Server] Balances updated for trade " + trade.getSymbol());
    }
    
    @Override
    public MarketSnapshot getMarketSnapshot() throws RemoteException {
        return new MarketSnapshot(simulationTimeMinutes.get(), new ArrayList<>(instruments.values()));
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
        
     // Provera balansa za BUY order
        if (request.getSide() == Order.Side.BUY) {
            double totalCost = request.getPrice() * request.getQuantity();
            if (account.getBalance() < totalCost) {
                return new OrderConfirmation("", OrderConfirmation.Status.REJECTED,
                    String.format("Insufficient funds: need $%.2f, have $%.2f", totalCost, account.getBalance()));
            }
        } 
        // Provera kolicine za SELL order
        else {
            double available = account.getAssetBalance(request.getSymbol());
            if (available < request.getQuantity()) {
                return new OrderConfirmation("", OrderConfirmation.Status.REJECTED,
                    String.format("Insufficient asset: need %.4f, have %.4f", request.getQuantity(), available));
            }
        }
        
        // Validacija minimalne cene
        if (request.getPrice() < 0.01) {
            return new OrderConfirmation("", OrderConfirmation.Status.REJECTED,
                "Cena mora biti veca od $0.01");
        }
        
        // Validacija kolicine
        if (request.getQuantity() <= 0 || request.getQuantity() > 1000000) {
            return new OrderConfirmation("", OrderConfirmation.Status.REJECTED,
                "Neispravna kolicina (min: 0.0001, max: 1,000,000)");
        }
        
        // Kreiraj i dodaj order u order book
        Order order = new Order(
            request.getClientId(),
            request.getSymbol().toUpperCase(),
            request.getSide(),
            request.getPrice(),
            request.getQuantity(),
            simulationTimeMinutes.get()
        );
        
        OrderBook orderBook = orderBooks.get(request.getSymbol().toUpperCase());
        orderBook.addOrder(order);
        
        return new OrderConfirmation(order.getId(), OrderConfirmation.Status.ACCEPTED, "");
    }
    
    @Override
    public List<Trade> getTradeHistory(String symbol, long simulationDayStart) throws RemoteException {
    	System.out.printf("[Server] Zahtev za istorijom trgovina: %s, dan %d (start: %d min)%n",
    	        symbol, simulationDayStart / (24 * 60), simulationDayStart);
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
    
    public void shutdown() {
        System.out.println("[Server] Shutting down simulation engine...");
        
        // Zaustavi scheduler prvo
        if (simulationScheduler != null && !simulationScheduler.isShutdown()) {
            simulationScheduler.shutdown();
            try {
                if (!simulationScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    simulationScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                simulationScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Zatvori TCP broadcaster
        if (broadcaster != null) {
            broadcaster.shutdown();
        }
        
        // Zaustavi archiver (sa cekanjem da se isprazni red)
        if (tradeArchiver != null) {
            tradeArchiver.shutdown();
        }
        
        System.out.println("[Server] All resources released");
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
        
        public synchronized void addBalance(double amount) {
            this.balance += amount;
        }
        
        public synchronized boolean subtractBalance(double amount) {
            if (balance >= amount) {
                balance -= amount;
                return true;
            }
            return false;
        }
        
        public synchronized void addAsset(String symbol, double quantity) {
            assets.put(symbol, assets.getOrDefault(symbol, 0.0) + quantity);
        }
        
        public synchronized boolean subtractAsset(String symbol, double quantity) {
            double current = assets.getOrDefault(symbol, 0.0);
            if (current >= quantity) {
                assets.put(symbol, current - quantity);
                return true;
            }
            return false;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public double getBalance() { return balance; }
        public double getAssetBalance(String symbol) { return assets.getOrDefault(symbol, 0.0); }
    }
}