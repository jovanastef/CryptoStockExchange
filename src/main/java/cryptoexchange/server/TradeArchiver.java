package cryptoexchange.server;

import cryptoexchange.model.Trade;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

//Asinhrono arhiviranje trgovina u fajl kroz posebnu nit
//Koristi BlockingQueue za thread-safe komunikaciju

public class TradeArchiver {
    private final String filename;
    private final BlockingQueue<Trade> tradeQueue = new LinkedBlockingQueue<>();
    private final Thread archiverThread;
    private volatile boolean running = true;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    public TradeArchiver(String filename) {
        this.filename = filename;
        
        // Kreiraj direktorijum ako ne postoji
        File file = new File(filename);
        if (file.getParentFile() != null) {
        	file.getParentFile().mkdirs();
        }
        
        this.archiverThread = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true))) {
                // Dodaj header ako fajl ne postoji ili je prazan
                if (file.length() == 0) {
                    writer.write("timestamp|symbol|price|quantity|buyer_id|seller_id\n");
                    writer.flush();
                }
                
                while (running || !tradeQueue.isEmpty()) {
                    try {
                        Trade trade = tradeQueue.poll(1, TimeUnit.SECONDS);
                        if (trade != null) {
                            String line = String.format("%d|%s|%.2f|%.4f|%s|%s%n",
                                trade.getTimestamp(),
                                trade.getSymbol(),
                                trade.getPrice(),
                                trade.getQuantity(),
                                trade.getBuyerId(),
                                trade.getSellerId());
                            writer.write(line);
                            writer.flush();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (IOException e) {
                System.err.println("[Archiver] Write error: " + e.getMessage());
            }
        }, "Trade-Archiver");
        
        archiverThread.setDaemon(true);
        archiverThread.start();
        System.out.println("[Archiver] Initialized: " + filename);
    }
    
    // Dodaje trgovinu u red za arhiviranje
    public void archiveTrade(Trade trade) {
        tradeQueue.offer(trade);
    }
    
    //Ceka da se sve trgovine arhiviraju pre shutdown-a
    public void shutdown() {
        running = false;
        archiverThread.interrupt();
        try {
            archiverThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[Archiver] Shutdown complete. Total trades archived: " + tradeQueue.size());
    }
}