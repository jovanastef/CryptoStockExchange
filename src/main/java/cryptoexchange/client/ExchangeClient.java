package cryptoexchange.client;

import cryptoexchange.model.*;
import cryptoexchange.rmi.ExchangeServiceInterface;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

//Osnovni klijent za testiranje RMI konekcije

public class ExchangeClient {
    private final String clientId;
    private final ExchangeServiceInterface exchangeService;
    
    public ExchangeClient(String clientName) throws Exception {
        // Povezi se na RMI registry
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        exchangeService = (ExchangeServiceInterface) registry.lookup("CryptoExchange");
        
        // Registruj klijenta
        this.clientId = exchangeService.registerClient(clientName);
        System.out.println("✓ Connected as client: " + clientId.substring(0, 8) + "...");
    }
    
    public void testConnection() {
        try {
            // Test 1: Dohvati market snapshot
            MarketSnapshot snapshot = exchangeService.getMarketSnapshot();
            System.out.println("\n=== MARKET SNAPSHOT (Simulation time: " + snapshot.getSimulationTimestamp() + " min) ===");
            System.out.printf("%-6s %-15s %12s%n", "SYM", "NAME", "PRICE");
            System.out.println("-----------------------------------");
            
            snapshot.getInstruments().stream()
                .limit(5) // prikazi prvih 5 instrumenata
                .forEach(inst -> 
                    System.out.printf("%-6s %-15s %12.2f%n", 
                        inst.getSymbol(), 
                        inst.getName().length() > 14 ? inst.getName().substring(0, 13) + "…" : inst.getName(),
                        inst.getCurrentPrice())
                );
            
            // Test 2: Dohvati order book za BTC
            OrderBookData btcBids = exchangeService.getOrderBook("BTC", OrderBookData.Side.BID);
            System.out.println("\n=== BTC BID ORDERS (Top 3) ===");
            btcBids.getOrders().stream().limit(3).forEach(order -> 
                System.out.printf("Price: $%.2f  Qty: %.4f  Client: %s%n", 
                    order.getPrice(), order.getQuantity(), order.getClientId().substring(0, 8))
            );
            
            System.out.println("\n✓ All RMI calls completed successfully!");
        } catch (Exception e) {
            System.err.println("✗ RMI call failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        try {
            String clientName = (args.length > 0) ? args[0] : "TestClient-" + System.currentTimeMillis() % 1000;
            ExchangeClient client = new ExchangeClient(clientName);
            client.testConnection();
            
            // Cekaj unos da se prozor ne zatvori odmah
            System.out.println("\nPress Enter to exit...");
            new Scanner(System.in).nextLine();
        } catch (Exception e) {
            System.err.println("Client startup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}