package cryptoexchange.server;

import cryptoexchange.rmi.ExchangeServiceInterface;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
//Glavna klasa za pokretanje RMI servera

public class CryptoExchangeServer {
	private static ExchangeServiceImpl serviceImpl; // cuvam referencu na implementaciju
    public static void main(String[] args) {
        try {
            // Kreiraj RMI registry na portu 1099
            Registry registry = LocateRegistry.createRegistry(1099);
            System.out.println("[Server] RMI registry created on port 1099");
            
            // Instanciraj servis
            serviceImpl = new ExchangeServiceImpl();
            
            // Registruj servis pod imenom "CryptoExchange"
            registry.rebind("CryptoExchange", serviceImpl);
            System.out.println("[Server] Exchange service bound to RMI registry");
            System.out.println("[Server] Ready to accept client connections");
            System.out.println("[Server] Simulation time: 1 real second = 1 simulation minute");
            System.out.println("[Server] Press Ctrl+C to shutdown");
            
            // Cekaj za shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Server] Shutting down gracefully...");
                if (serviceImpl != null) {
                System.out.println("[Server] Shutdown complete");
                }
            }));
            
            // Beskonacna petlja da server ostane aktivan
            while (true) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
            e.printStackTrace();
            
         // Pokusaj cleanup cak i pri gresci
            if (serviceImpl != null) {
                serviceImpl.shutdown();
            }
            
            System.exit(1);
        }
    }
}