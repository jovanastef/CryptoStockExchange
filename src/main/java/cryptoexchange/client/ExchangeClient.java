package cryptoexchange.client;

import cryptoexchange.model.*;
import cryptoexchange.rmi.ExchangeServiceInterface;
import cryptoexchange.util.ConsoleColors;

import java.io.*;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Scanner;

public class ExchangeClient {
    private final String clientId;
    private final ExchangeServiceInterface exchangeService;
    private final Map<String, Instrument> trackedInstruments = new ConcurrentHashMap<>();
    private final Deque<String> tradeNotifications = new ConcurrentLinkedDeque<>();
    private Socket tcpSocket;
    private PrintWriter tcpOut;
    private BufferedReader tcpIn;
    private volatile boolean running = true;
    private volatile long currentSimulationTime = 0;
    
    public ExchangeClient(String clientName) throws Exception {
        // Povezi se na RMI registry
        Registry registry = LocateRegistry.getRegistry("localhost", 1099);
        exchangeService = (ExchangeServiceInterface) registry.lookup("CryptoExchange");
        
        // Registruj klijenta
        this.clientId = exchangeService.registerClient(clientName);
        System.out.println(ConsoleColors.CYAN + "Connected as client: " + clientId.substring(0, 8) + "...");
        
        MarketSnapshot snapshot = exchangeService.getMarketSnapshot();
        snapshot.getInstruments().forEach(inst -> 
            trackedInstruments.put(inst.getSymbol(), inst));
    }
    
    public void start() {
        new Thread(this::connectToTcpServer, "TCP-Listener").start();
        startConsoleUI();
    }
    
    private void connectToTcpServer() {
        try {
            tcpSocket = new Socket("localhost", 9090);
            tcpOut = new PrintWriter(tcpSocket.getOutputStream(), true);
            tcpIn = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
            
            String symbols = String.join(",", trackedInstruments.keySet());
            tcpOut.println("SUBSCRIBE|" + symbols);
            System.out.println(ConsoleColors.CYAN + "Subscribed to market data" + ConsoleColors.RESET);
            
            String line;
            while (running && (line = tcpIn.readLine()) != null) {
                if (line.startsWith("UPDATE|")) {
                    parseAndUpdateInstrument(line);
                } else if (line.startsWith("TRADE|")) {
                    handleTradeNotification(line);
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println(ConsoleColors.RED
                		+ "TCP connection error: " + e.getMessage() + ConsoleColors.RESET);
            }
        } finally {
            closeTcpConnection();
        }
    }
    
    private void parseAndUpdateInstrument(String line) {
        try {
            String[] parts = line.substring(7).split("\\|");
            if (parts.length < 7) return;
            
            currentSimulationTime = Long.parseLong(parts[1]); // Azuriraj globalno vreme
            
            String symbol = parts[0];
            Instrument inst = trackedInstruments.get(symbol);
            if (inst != null) {
                inst.setCurrentPrice(Double.parseDouble(parts[2]));
                inst.setChange1h(Double.parseDouble(parts[3]));
                inst.setChange24h(Double.parseDouble(parts[4]));
                inst.setChange7d(Double.parseDouble(parts[5]));
                inst.setLastUpdateTimestamp(Long.parseLong(parts[1]));
            }
        } catch (Exception e) {
            // ignore parsing errors
        }
    }
    
    private void handleTradeNotification(String line) {
        try {
            String[] parts = line.substring(6).split("\\|");
            if (parts.length < 6) return;
            
            if (trackedInstruments.containsKey(parts[0])) {
                String notification = String.format("TRADE: %s %.4f @ $%.2f (%s → %s)",
                    parts[0], Double.parseDouble(parts[3]), Double.parseDouble(parts[2]), parts[5], parts[4]);
                tradeNotifications.add(notification);
                while (tradeNotifications.size() > 5) {
                    tradeNotifications.poll();
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }
    
    private void startConsoleUI() {
        Scanner scanner = new Scanner(System.in);
        
        Thread uiThread = new Thread(() -> {
            while (running) {
                try {
                    renderMarketData();
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "UI-Renderer");
        uiThread.start();
        
        System.out.println(ConsoleColors.CYAN + "\n=== CRYPTO EXCHANGE CLIENT ===" + ConsoleColors.RESET);
        System.out.println("Commands: watch <symbol>, orders <symbol> <bid/ask>, buy <sym> <price> <qty>, sell <sym> <price> <qty>, trades <symbol>, exit");
        
        while (running && scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;
            processCommand(line);
        }
        
        scanner.close();
        shutdown();
    }
    
    private void renderMarketData() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        
        long simMinutes = currentSimulationTime; // Koristi azurirano vreme
        int hours = (int) (simMinutes / 60) % 24;
        int mins = (int) (simMinutes % 60);
        System.out.println(ConsoleColors.CYAN + "=== REAL-TIME MARKET DATA (Simulation time: " + 
            String.format("%02d:%02d", hours, mins) + ") ===" + ConsoleColors.RESET);
        
        System.out.println(String.format("%-6s %-15s %12s %10s %10s %10s", 
            "SYM", "NAME", "PRICE", "1h", "24h", "7d"));
        System.out.println("---------------------------------------------------------------");
        
        trackedInstruments.values().stream()
            .sorted(Comparator.comparing(Instrument::getSymbol))
            .limit(10)
            .forEach(this::renderInstrumentRow);
        
        if (!tradeNotifications.isEmpty()) {
            System.out.println("\n" + ConsoleColors.YELLOW + "=== RECENT TRADES ===" + ConsoleColors.RESET);
            tradeNotifications.forEach(msg -> System.out.println(ConsoleColors.YELLOW + msg + ConsoleColors.RESET));
        }
        
        System.out.println("\n" + ConsoleColors.GREEN + "Connected | Client ID: " + 
            clientId.substring(0, 8) + "..." + ConsoleColors.RESET);
        System.out.println("Commands: watch <symbol>, orders <symbol> <bid/ask>, buy <sym> <price> <qty>, sell <sym> <price> <qty>, trades <symbol>, exit");
    }
    
    private void renderInstrumentRow(Instrument inst) {
        double ch1h = inst.getChange1h();
        double ch24h = inst.getChange24h();
        double ch7d = inst.getChange7d();
        
        String color1h = ch1h >= 0 ? ConsoleColors.GREEN : ConsoleColors.RED;
        String arrow1h = ch1h >= 0 ? ConsoleColors.ARROW_UP : ConsoleColors.ARROW_DOWN;
        String sign1h = ch1h >= 0 ? "+" : "";
        
        String color24h = ch24h >= 0 ? ConsoleColors.GREEN : ConsoleColors.RED;
        String arrow24h = ch24h >= 0 ? ConsoleColors.ARROW_UP : ConsoleColors.ARROW_DOWN;
        String sign24h = ch24h >= 0 ? "+" : "";
        
        String color7d = ch7d >= 0 ? ConsoleColors.GREEN : ConsoleColors.RED;
        String arrow7d = ch7d >= 0 ? ConsoleColors.ARROW_UP : ConsoleColors.ARROW_DOWN;
        String sign7d = ch7d >= 0 ? "+" : "";
        
        String name = inst.getName();
        if (name.length() > 14) name = name.substring(0, 13) + "…";
        
        System.out.printf("%-6s %-15s %12.2f %s%9.2f%%%s %s%9.2f%%%s %s%9.2f%%%s%n",
            inst.getSymbol(),
            name,
            inst.getCurrentPrice(),
            color1h, sign1h + ch1h, ConsoleColors.RESET,
            color24h, sign24h + ch24h, ConsoleColors.RESET,
            color7d, sign7d + ch7d, ConsoleColors.RESET);
    }
    
    private void processCommand(String line) {
        String[] parts = line.split("\\s+");
        try {
            switch (parts[0].toLowerCase()) {
                case "exit":
                    running = false;
                    break;
                case "watch":
                    if (parts.length > 1) watchInstrument(parts[1]);
                    break;
                case "orders":
                    if (parts.length > 2) showOrderBook(parts[1], parts[2]);
                    break;
                case "buy":
                case "sell":
                    if (parts.length == 4) placeOrder(parts[0], parts[1], parts[2], parts[3]);
                    break;
                case "trades":
                    if (parts.length > 1) showTradeHistory(parts[1]);
                    break;
                default:
                    System.out.println(ConsoleColors.RED + "Unknown command" + ConsoleColors.RESET);
            }
        } catch (Exception e) {
            System.err.println(ConsoleColors.RED + "Command error: " + e.getMessage() + ConsoleColors.RESET);
        }
        
    }
    
    private void watchInstrument(String symbol) {
        try {
            MarketSnapshot snapshot = exchangeService.getMarketSnapshot();
            Optional<Instrument> instOpt = snapshot.getInstruments().stream()
                .filter(i -> i.getSymbol().equalsIgnoreCase(symbol))
                .findFirst();
            
            if (instOpt.isPresent()) {
                trackedInstruments.put(symbol.toUpperCase(), instOpt.get());
                if (tcpOut != null) {
                    String symbols = String.join(",", trackedInstruments.keySet());
                    tcpOut.println("SUBSCRIBE|" + symbols);
                }
                System.out.println(ConsoleColors.GREEN + "Now tracking " + symbol.toUpperCase() + ConsoleColors.RESET);
            } else {
                System.out.println(ConsoleColors.RED + "Instrument not found: " + symbol + ConsoleColors.RESET);
            }
        } catch (Exception e) {
            System.err.println(ConsoleColors.RED + "Error: " + e.getMessage() + ConsoleColors.RESET);
        }
    }
    
    private void showOrderBook(String symbol, String side) {
        try {
            OrderBookData.Side orderSide = side.equalsIgnoreCase("bid") ? 
                OrderBookData.Side.BID : OrderBookData.Side.ASK;
            
            OrderBookData book = exchangeService.getOrderBook(symbol.toUpperCase(), orderSide);
            System.out.println("\n=== " + symbol.toUpperCase() + " " + side.toUpperCase() + " ORDERS ===");
            System.out.println(String.format("%12s %15s %s", "PRICE", "QUANTITY", "CLIENT ID"));
            System.out.println("----------------------------------------");
            
            book.getOrders().stream().limit(10).forEach(order -> 
                System.out.printf("%12.2f %15.4f %s%n", 
                    order.getPrice(), order.getQuantity(), order.getClientId().substring(0, 8) + "...")
            );
        } catch (Exception e) {
            System.err.println(ConsoleColors.RED + "Error: " + e.getMessage() + ConsoleColors.RESET);
        }
    }
    
    private void placeOrder(String type, String symbol, String priceStr, String qtyStr) {
        try {
            double price = Double.parseDouble(priceStr);
            double quantity = Double.parseDouble(qtyStr);
            Order.Side side = type.equalsIgnoreCase("buy") ? Order.Side.BUY : Order.Side.SELL;
            
            OrderRequest request = new OrderRequest(clientId, symbol.toUpperCase(), side, price, quantity);
            OrderConfirmation confirmation = exchangeService.placeOrder(request);
            
            if (confirmation.getStatus() == OrderConfirmation.Status.ACCEPTED) {
                System.out.println(ConsoleColors.GREEN + "Order accepted: " + confirmation.getOrderId().substring(0, 8) + ConsoleColors.RESET);
            } else {
                System.out.println(ConsoleColors.RED + "Order rejected: " + confirmation.getReason() + ConsoleColors.RESET);
            }
        } catch (NumberFormatException e) {
            System.out.println(ConsoleColors.RED + "Invalid price or quantity format" + ConsoleColors.RESET);
        } catch (Exception e) {
            System.err.println(ConsoleColors.RED + "Error: " + e.getMessage() + ConsoleColors.RESET);
        }
    }
    
    private void showTradeHistory(String symbol) {
        try {
            List<Trade> trades = exchangeService.getTradeHistory(symbol.toUpperCase(), 0);
            System.out.println("\n=== TRADE HISTORY FOR " + symbol.toUpperCase() + " ===");
            System.out.println(String.format("%8s %12s %15s %12s", "TIME", "PRICE", "QUANTITY", "PARTIES"));
            System.out.println("--------------------------------------------------");
            
            trades.stream().limit(15).forEach(trade -> 
                System.out.printf("%8s %12.2f %15.4f %s → %s%n",
                    formatTime(trade.getTimestamp()),
                    trade.getPrice(),
                    trade.getQuantity(),
                    trade.getBuyerId().substring(0, 6),
                    trade.getSellerId().substring(0, 6))
            );
        } catch (Exception e) {
            System.err.println(ConsoleColors.RED + "Error: " + e.getMessage() + ConsoleColors.RESET);
        }
    }
    
    private String formatTime(long minutes) {
        int hours = (int) (minutes / 60) % 24;
        int mins = (int) (minutes % 60);
        return String.format("%02d:%02d", hours, mins);
    }
    
    private void closeTcpConnection() {
        try {
            if (tcpOut != null) tcpOut.close();
            if (tcpIn != null) tcpIn.close();
            if (tcpSocket != null && !tcpSocket.isClosed()) {
                tcpSocket.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }
    
    private void shutdown() {
        running = false;
        closeTcpConnection();
        System.out.println(ConsoleColors.YELLOW + "\nClient shutdown complete" + ConsoleColors.RESET);
    }
    
    public static void main(String[] args) {
        try {
            String clientName = (args.length > 0) ? args[0] : "Trader-" + new Random().nextInt(1000);
            ExchangeClient client = new ExchangeClient(clientName);
            client.start();
        } catch (Exception e) {
            System.err.println(ConsoleColors.RED + "Client startup failed: " + e.getMessage() + ConsoleColors.RESET);
            e.printStackTrace();
        }
    }
}