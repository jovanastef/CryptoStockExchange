package cryptoexchange.model;

import java.io.Serializable;
import java.util.List;

//Snapshot trenutnog stanja tržišta sa svim instrumentima

public class MarketSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final long simulationTimestamp; // simulaciono vreme u minutima
    private final List<Instrument> instruments;
    
    public MarketSnapshot(long simulationTimestamp, List<Instrument> instruments) {
        this.simulationTimestamp = simulationTimestamp;
        this.instruments = instruments;
    }
    
    public long getSimulationTimestamp() {
        return simulationTimestamp;
    }
    
    public List<Instrument> getInstruments() {
        return instruments;
    }
}