@echo off
start "Bot1" cmd /k "mvn exec:java -Dexec.mainClass=cryptoexchange.client.AutoTraderClient -Dexec.args=Bot1"
start "Bot2" cmd /k "mvn exec:java -Dexec.mainClass=cryptoexchange.client.AutoTraderClient -Dexec.args=Bot2"
start "Bot3" cmd /k "mvn exec:java -Dexec.mainClass=cryptoexchange.client.AutoTraderClient -Dexec.args=Bot3"
start "Bot4" cmd /k "mvn exec:java -Dexec.mainClass=cryptoexchange.client.AutoTraderClient -Dexec.args=Bot4"
start "Bot5" cmd /k "mvn exec:java -Dexec.mainClass=cryptoexchange.client.AutoTraderClient -Dexec.args=Bot5"
echo Pokrenuto 5 auto-tradera!
pause