package cryptoexchange.client;

import cryptoexchange.model.*;
import cryptoexchange.rmi.ExchangeServiceInterface;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

//Automatski trgovac koji generiše nasumične naloga za dinamičnu simulaciju tržišta
//Pokreće se kao zaseban proces: 
//mvn exec:java -Dexec.mainClass="cryptoexchange.client.AutoTraderClient" -Dexec.args="Bot1"

public class AutoTraderClient {
    private final String clientId;
    private final ExchangeServiceInterface exchangeService;
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final String traderName;
    
    // Instrumenti za trgovinu (prioritet na volatilnije)
    private static final List<String> TRADING_SYMBOLS = Arrays.asList(
        "BTC", "ETH", "SOL", "DOGE", "XRP", "ADA", "BNB", "LINK"
    );
    
    // Konfiguracija ponasanja
    private static final double BUY_PROBABILITY = 0.65; // 65% sanse za kupovinu
    private static final double MAX_PRICE_OFFSET = 0.03; // ±3% od trenutne cene
    private static final int MIN_DELAY_SEC = 4;
    private static final int MAX_DELAY_SEC = 12;

    public AutoTraderClient(String traderName) throws Exception {
        this.traderName = traderName;
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        exchangeService = (ExchangeServiceInterface) registry.lookup("CryptoExchange");
        this.clientId = exchangeService.registerClient(traderName);
        System.out.printf("[%s] Auto-trader activated! ID: %s%n", 
            traderName, clientId.substring(0, 8));
    }

    public void startTrading() {
        // Generisi prvi nalog nakon kratkog kasnjenja
        scheduler.scheduleAtFixedRate(
            this::generateAndPlaceOrder,
            3,
            MIN_DELAY_SEC + random.nextInt(MAX_DELAY_SEC - MIN_DELAY_SEC + 1),
            TimeUnit.SECONDS
        );

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.printf("[%s] Shutting down...%n", traderName);
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            System.out.printf("[%s] Shutdown complete%n", traderName);
        }));

        System.out.printf("[%s] Trading started! Generating orders every %d-%d seconds%n",
            traderName, MIN_DELAY_SEC, MAX_DELAY_SEC);
    }

    private void generateAndPlaceOrder() {
        try {
            // Dohvati trenutno stanje trzista
            MarketSnapshot snapshot = exchangeService.getMarketSnapshot();
            if (snapshot.getInstruments().isEmpty()) return;

            // Nasumicno izaberi instrument
            String symbol = TRADING_SYMBOLS.get(random.nextInt(TRADING_SYMBOLS.size()));
            Optional<Instrument> instOpt = snapshot.getInstruments().stream()
                .filter(i -> i.getSymbol().equals(symbol))
                .findFirst();
            
            if (instOpt.isEmpty()) return;
            Instrument instrument = instOpt.get();
            double currentPrice = instrument.getCurrentPrice();

            // Odluci BUY/SELL (vise kupovina za aktivnije trziste)
            Order.Side side = (random.nextDouble() < BUY_PROBABILITY) ? Order.Side.BUY : Order.Side.SELL;

            // Izracunaj cenu sa malim offsetom
            double priceOffset = (random.nextDouble() * 2 - 1) * MAX_PRICE_OFFSET;
            double orderPrice = currentPrice * (1 + priceOffset);
            
            // Odredi kolicinu prema instrumentu
            double quantity = calculateQuantity(symbol, side, currentPrice);

            // Kreiraj i pošalji nalog
            OrderRequest request = new OrderRequest(clientId, symbol, side, orderPrice, quantity);
            OrderConfirmation confirmation = exchangeService.placeOrder(request);

            // Loguj rezultat
            if (confirmation.getStatus() == OrderConfirmation.Status.ACCEPTED) {
                String action = (side == Order.Side.BUY) ? "🟢 BUY" : "🔴 SELL";
                System.out.printf("[%s] %s %s %.4f @ $%.2f | Vol: %.2f%% | ID: %s%n",
                    traderName,
                    action,
                    symbol,
                    quantity,
                    orderPrice,
                    Math.abs(priceOffset) * 100,
                    confirmation.getOrderId().substring(0, 6)
                );
            } else if (!confirmation.getReason().contains("Insufficient")) {
                // Loguj samo znacajne odbijene naloge (ignorisi "Insufficient funds" jer je cesto)
                System.out.printf("[%s] Rejected %s %s: %s%n",
                    traderName,
                    (side == Order.Side.BUY) ? "BUY" : "SELL",
                    symbol,
                    confirmation.getReason()
                );
            }
        } catch (Exception e) {
            // Ne prekida rad, samo loguj gresku
            if (!e.getMessage().contains("Connection refused")) {
                System.err.printf("[%s] Error: %s%n", traderName, e.getMessage());
            }
        }
    }

    private double calculateQuantity(String symbol, Order.Side side, double currentPrice) {
        // Prilagodi kolicinu prema instrumentu i strani
        switch (symbol) {
            case "BTC":
                return 0.001 + random.nextDouble() * (side == Order.Side.BUY ? 0.02 : 0.05);
            case "ETH":
                return 0.01 + random.nextDouble() * (side == Order.Side.BUY ? 0.1 : 0.3);
            case "SOL":
            case "BNB":
                return 0.1 + random.nextDouble() * (side == Order.Side.BUY ? 0.5 : 1.5);
            case "DOGE": // Vece kolicine za jeftinije valute
                return 50 + random.nextDouble() * (side == Order.Side.BUY ? 200 : 500);
            default:
                return 1.0 + random.nextDouble() * (side == Order.Side.BUY ? 5 : 15);
        }
    }

    public static void main(String[] args) {
        try {
            // Generisi ime ako nije prosledjeno
            String name = (args.length > 0) ? args[0] : 
                String.format("Trader-%03d", new Random().nextInt(900) + 100);
            
            AutoTraderClient bot = new AutoTraderClient(name);
            bot.startTrading();
            
            // Cekaj dok se ne prekine (Ctrl+C)
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Startup failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}