# 🌟 DailyBattlePass - Plugin Minecraft 1.20.1 (Paper)

**DailyBattlePass** è un plugin completo per server Minecraft **Paper 1.20.1**, sviluppato in **Java con Maven** e contenuto interamente in un **singolo file `.java` da oltre 950 righe**.  
Il plugin offre un sistema di **ricompense giornaliere** e **Battle Pass con livelli e punti esperienza**, tutto accessibile tramite GUI personalizzate e completamente **senza dipendenze esterne**.

---

## ✨ Caratteristiche principali

- ✅ **Ricompense giornaliere** configurabili da `rewards.yml`
- ✅ Una sola ricompensa al giorno per ogni giocatore
- ✅ GUI elegante, responsive e leggera
- ✅ **Sistema Battle Pass integrato** con livelli, punti XP e ricompense
- ✅ Comandi facili da usare per giocatori e admin
- ✅ Tutto scritto in **un solo file Java**, zero classi extra
- ✅ Compatibile con **Paper 1.20.1** (no Bukkit, no plugin esterni)

---

## 📦 Installazione

1. Scarica il file `.jar` dal build o compila con Maven (`mvn clean package`)
2. Metti il file nella cartella `plugins/` del tuo server Paper
3. Avvia o riavvia il server
4. Il plugin genererà automaticamente i file:
   - `config.yml`
   - `rewards.yml`
   - `data.yml`

---

## ⚙️ Configurazione

### `rewards.yml`

Esempio:

```yaml
rewards:
  - "give %player% diamond 3"
  - "give %player% golden_apple 2"
  - "say Congratulazioni %player%, hai vinto 500 XP!"
  - "xp add %player% 500 levels"
  - "give %player% emerald 10"
```

- Usa `%player%` per inserire il nome del giocatore nel comando.
- Puoi aggiungere **qualsiasi comando server**.

---

## 📁 Altri file

### `data.yml`
Contiene i dati di ogni giocatore:
```yaml
PlayerName:
  lastClaimed: 2025-06-09
  level: 3
  xp: 450
```

---

## ⌨️ Comandi

| Comando            | Descrizione                                      | Permessi              |
|--------------------|--------------------------------------------------|------------------------|
| `/claim`           | Apre la GUI delle ricompense giornaliere        | Nessuno                |
| `/battlepass`      | Mostra livello attuale e premi disponibili      | Nessuno                |
| `/battlepass reload` | Ricarica i file di configurazione               | `battlepass.admin`     |

---

## 📚 Dipendenze

- **Nessuna dipendenza esterna**  
  Funziona solo con **Paper 1.20.1**

---

## 👑 Credits

Creato da **Gabry**.  
Plugin interamente in Java, ottimizzato per prestazioni e compatibilità.

---

