# 🚗 Sistem za administraciju servisa vozila

Sistem predstavlja distribuiranu platformu za upravljanje servisom vozila koja omogućava rad sa klijentima, zakazivanje termina, upravljanje dijelovima i naručivanje dijelova od dobavljača. Sastoji se od centralnog servera servisa, GUI aplikacije za servis, GUI aplikacije za klijente i GUI aplikacije za dobavljače. Projekat je rađen u okviru predmeta **Mrežno i distribuirano programiranje** (Elektrotehnički fakultet Banja Luka).

---

## 🛠️ Tehnologije

| Sloj                         | Tehnologija                                         |
|------------------------------|-----------------------------------------------------|
| Jezik                        | Java 17                                             |
| GUI aplikacije               | JavaFX (javafx-sdk-22.0.1)                          |
| Komunikacija klijent – servis| REST                                                |
| Komunikacija u realnom vremenu | Secure soketi (SSL/TLS) · Multicast               |
| Komunikacija dobavljač – knjigovodstvo | RMI                                       |
| Redovi narudžbi              | MQ (message queue)                                  |
| Baza dijelova                | Redis                                               |
| Podaci o klijentima          | XML (`clients.xml`)                                 |
| Parsiranje HTML stranica     | Jsoup                                               |
| Serijalizacija računa        | JSON ili binarni format                             |
| Logovanje                    | Java Logger                                         |
| Konfiguracija                | `config.properties`                                 |

---

## 🏗️ Arhitektura

```
 Klijenti (GUI) ──REST / secure soketi / multicast──┐
                                                    ▼
 Serviser (GUI) ───────REST / secure soketi────▶ Centralni server servisa ◀──▶ Redis
                                                    │
                                          secure soketi
                                                    ▼
                                  DobavljacServer + MQ ◀── secure soketi ── Dobavljač (GUI)
                                                    │
                                                   RMI
                                                    ▼
                                            RMI server (knjigovodstvo)
```

Sistem podržava **jednu instancu** aplikacije za servis i **više instanci** klijentskih i dobavljačkih aplikacija.

---

## 📁 Struktura projekta

```
vehicle-service/
├── VehicleServiceCentralServer/        # Centralni server servisa (REST, soketi, Redis)
├── VehicleServiceServicerApp/          # GUI aplikacija za servis (JavaFX)
├── VehicleServiceClientApp/            # GUI aplikacija za klijente (JavaFX) 
└── VehicleServiceSupplierApp/          # GUI aplikacija za dobavljače (JavaFX),Server dobavljača (soketi, MQ, Jsoup), RMI server knjigovodstvenog servisa
```

---

## 👥 Uloge korisnika

| Uloga          | Opis                                                                                   |
|----------------|----------------------------------------------------------------------------------------|
| **Klijent**    | Registruje se, zakazuje termine, pregleda istoriju servisa i komunicira putem chata    |
| **Serviser**   | Upravlja klijentima, terminima i dijelovima, naručuje dijelove i izdaje fakture        |
| **Dobavljač**  | Pregleda svoje dijelove, obrađuje narudžbe i ažurira ponudu                            |

---

## ✨ Funkcionalnosti

### Aplikacija za klijente
- Prijava korisničkim imenom i lozinkom, te registracija novog naloga (nalog je aktivan tek nakon odobrenja servisera)
- Zakazivanje termina: datum, vrijeme i vrsta usluge (redovni servis ili popravka), termin traje jedan sat
- Pregled istorije servisa u tabeli sa statusom (završeno, otkazano) i kratkim opisom
- Chat preko sigurnih soketa: komunikacija 1 na 1, multikast poruke drugim klijentima i komunikacija sa serviserom

### Aplikacija za servis
- Upravljanje klijentima: odobravanje registracije, blokiranje i brisanje naloga (podaci u `clients.xml`)
- Upravljanje terminima: potvrda ili odbijanje zahtjeva i dodavanje komentara
- Upravljanje dijelovima (CRUD) u Redis bazi: šifra, naziv, proizvođač, cijena, dostupna količina i opis
- Naručivanje od dobavljača: izbor dobavljača, unos količine i praćenje statusa narudžbe (primljeni dijelovi se upisuju u Redis)
- Kreiranje fakture u tekstualnom obliku (utrošeni dijelovi, vrsta usluge, podaci o korisniku i vozilu)
- Slanje fakture u ZIP arhivi na e-mail korisnika

### Aplikacija za dobavljače
- `SupplierServer` pri pokretanju učitava listu artikala sa sajta za online prodaju dijelova i parsira šifru, naslov, cijenu i URL slike (Jsoup)
- Pregled dostupnih dijelova i cijena
- Obrada narudžbi: red čekanja (prva najstarija) sa odobravanjem ili odbijanjem; odobrene narudžbe generišu račun sa PDV-om (17%) koji se putem RMI-a šalje knjigovodstvenom servisu
- Računi se čuvaju u serijalizovanom obliku na RMI serveru
- Dodavanje novih dijelova putem socket komunikacije

### Dodatno
- Svi kritični događaji (greške, uspješne transakcije) loguju se putem Logger-a
- Putanje fajlova, portovi i URL-ovi nalaze se u `config.properties`
- Pri pokretanju se automatski dodaju testni termini i dijelovi

---

## 🚀 Pokretanje projekta

### Preduslovi

- Java 17+
- JavaFX SDK 22.0.1 (`javafx-sdk-22.0.1`)
- Redis server
- MQ broker (message queue)
- SSL keystore / truststore za secure sokete
- SMTP nalog za slanje faktura e-mailom

---

### JavaFX VM opcije

Sve GUI aplikacije (`VehicleServiceServicerApp`, `VehicleServiceClientApp`, `VehicleServiceSupplierApp`) pokreću se sa sljedećim VM opcijama (putanju prilagoditi lokaciji SDK-a):

```
--module-path "putanja/do/javafx-sdk-22.0.1/lib" --add-modules javafx.controls,javafx.fxml
```

---

### Redoslijed pokretanja

1. Pokrenuti **Redis** i **MQ broker**.
2. Pokrenuti **AccountingServer** (knjigovodstveni servis).
3. Pokrenuti **SupplierServer** (jedna ili više instanci).
4. Pokrenuti **ChatServer** (jedna ili više instanci).
5. Pokrenuti **VehicleServiceCentralServer**.
6. Pokrenuti **VehicleServiceServicerApp** (jedna instanca).
7. Pokrenuti **VehicleServiceSupplierApp** i **VehicleServiceClientApp** (jedna ili više instanci).

> Portove, putanje do fajlova, URL-ove, Redis i MQ parametre te SSL postavke podesiti u `config.properties`.

## 📄 Dokumentacija

Kompletan opis projekta, model podataka i model aplikacije nalaze se u pdfu `Projektni zadatak`.
