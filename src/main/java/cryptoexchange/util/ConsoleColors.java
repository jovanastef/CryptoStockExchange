package cryptoexchange.util;

//ANSI escape codes za boje u konzoli
//Koristi se u klijentskom UI-u za vizuelnu indikaciju promena cena

public class ConsoleColors {
    // Reset
    public static final String RESET = "\033[0m";
    
    // Boje
    public static final String RED = "\033[0;31m";     // Crvena za pad
    public static final String GREEN = "\033[0;32m";   // Zelena za rast
    public static final String YELLOW = "\033[0;33m";  // Zuta za upozorenja
    public static final String CYAN = "\033[0;36m";    // Cijan za informacije
    
    // Bold boje
    public static final String RED_BOLD = "\033[1;31m";
    public static final String GREEN_BOLD = "\033[1;32m";
    
    // Simboli
    public static final String ARROW_UP = "↑";
    public static final String ARROW_DOWN = "↓";
    public static final String ARROW_STABLE = "→";
}