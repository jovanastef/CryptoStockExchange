package cryptoexchange.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

// TCP server koji broadcast-uje real-time azuriranja trzista svim konektovanim klijentima
//Format poruka:
// UPDATE|symbol|timestamp|price|change1h|change24h|change7d
// TRADE|symbol|timestamp|price|quantity|buyerId|sellerId

public class MarketDataBroadcaster {
    private final int port;
    private final ServerSocket serverSocket;
    private final Set<PrintWriter> clients = ConcurrentHashMap.newKeySet();
    private final ExecutorService clientHandlerExecutor;
    private volatile boolean running = true;
    
    public MarketDataBroadcaster(int port) throws IOException {
        this.port = port;
        this.serverSocket = new ServerSocket(port);
        this.clientHandlerExecutor = Executors.newCachedThreadPool();
        
        // Prihvataj konekcije u zasebnoj niti
        new Thread(() -> {
            System.out.println("[Broadcaster] TCP server started on port " + port);
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                } catch (IOException e) {
                    if (running && !serverSocket.isClosed()) {
                        System.err.println("[Broadcaster] Accept error: " + e.getMessage());
                    }
                }
            }
        }, "Broadcaster-Acceptor").start();
    }
    
    private void handleClient(Socket socket) {
        clientHandlerExecutor.submit(() -> {
            String clientInfo = socket.getInetAddress() + ":" + socket.getPort();
            System.out.println("[Broadcaster] New client connected: " + clientInfo);
            
            PrintWriter out = null;
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                
                // Dodaj klijenta u listu za broadcast
                clients.add(out);
                
                // Obradi subscribe zahtev (FORMAT: SUBSCRIBE|BTC,ETH,SOL)
                String line;
                while (running && (line = in.readLine()) != null) {
                    if (line.startsWith("SUBSCRIBE|")) {
                        // Klijent salje listu simbola koje prati, trenutno ignorisano
                        // U buducnosti: filtriraj broadcast po simbolima
                        System.out.println("[Broadcaster] " + clientInfo + " subscribed");
                    } else if (line.equals("UNSUBSCRIBE")) {
                        break;
                    }
                }
            } catch (IOException e) {
                // Klijent se diskonektovao
            } finally {
                if (out != null) {
                    clients.remove(out);
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
                System.out.println("[Broadcaster] Client disconnected: " + clientInfo);
            }
        });
    }
    
    // Broadcast-uje azuriranje cene instrumenta
    public void broadcastPriceUpdate(String symbol, long timestamp, double price, 
                                     double change1h, double change24h, double change7d) {
        if (!running) return;
        
        String message = String.format("UPDATE|%s|%d|%.2f|%.2f|%.2f|%.2f%n",
            symbol, timestamp, price, change1h, change24h, change7d);
        broadcast(message);
    }
    
    // Broadcast-uje izvrsenu trgovinu

    public void broadcastTrade(String symbol, long timestamp, double price, 
                               double quantity, String buyerId, String sellerId) {
        if (!running) return;
        
        // Skrati ID-jeve za prikaz (samo prvih 8 karaktera)
        String buyerShort = buyerId.substring(0, Math.min(8, buyerId.length()));
        String sellerShort = sellerId.substring(0, Math.min(8, sellerId.length()));
        
        String message = String.format("TRADE|%s|%d|%.2f|%.4f|%s|%s%n",
            symbol, timestamp, price, quantity, buyerShort, sellerShort);
        broadcast(message);
    }
    
    private void broadcast(String message) {
        Iterator<PrintWriter> iterator = clients.iterator();
        while (iterator.hasNext()) {
            PrintWriter out = iterator.next();
            try {
                out.print(message);
                out.flush();
            } catch (Exception e) {
                // Ukloni klijenta koji vise nije dostupan
                iterator.remove();
                try {
                    out.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
        }
    }
    
    public void shutdown() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            // ignore
        }
        clientHandlerExecutor.shutdown();
        System.out.println("[Broadcaster] Shutdown complete");
    }
    
    public int getClientCount() {
        return clients.size();
    }
}