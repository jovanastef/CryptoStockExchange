# Crypto Exchange Simulation System

Simulacija kripto berze sa RMI komunikacijom i TCP broadcast-om trzisnih podataka.

##  Kljucne karakteristike
- RMI za RPC pozive: Snapshot, order book, nalozi, istorija trgovina

- TCP broadcast: Real-time azuriranja cena i notifikacije o trgovinama

- Skalirano vreme

- 12 kripto instrumenata

- Order matching engine: Automatsko izvrsavanje trgovina kada se bid/ask poklope

- ANSI UI: Dinamicni prikaz sa bojama i promenama cena

- Auto-traderi: Vise botova koji generisu realisticne naloga

- Trade archiving: Sve trgovine se cuvaju u 'data/trades_archive.txt'

### Build projekta u cmd

mvn clean compile

### Pokreni server 

mvn exec:java -Dexec.mainClass="cryptoexchange.server.CryptoExchangeServer"

### Pokreni klijenta (novi terminal)

mvn exec:java -Dexec.mainClass="cryptoexchange.client.ExchangeClient" -Dexec.args="MojTrader"

### Pokreni vise auto-tradera (novi terminal)

mvn exec:java -Dexec.mainClass="cryptoexchange.client.AutoTraderClient" -Dexec.args="Bot1"
