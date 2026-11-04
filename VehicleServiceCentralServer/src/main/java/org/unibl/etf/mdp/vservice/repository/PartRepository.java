package org.unibl.etf.mdp.vservice.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.unibl.etf.mdp.vservice.model.Part;
import org.unibl.etf.mdp.vservice.util.AppLogger;
import org.unibl.etf.mdp.vservice.util.PropertiesUtil;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

public class PartRepository {

    private static final Logger LOGGER = AppLogger.getLogger();

    private static PartRepository instance = null;

    public static String instanceName;
    public static String redisHost;
    public static List<Part> partsToAdd = new ArrayList<>();
    public static List<Map<String, String>> parts = new ArrayList<>(); // Ovo parts se čini kao keš, ali ga getAllParts refresuje
    private static Properties prop;

    public static void generateParts() {
        try (Jedis jedis = new Jedis(redisHost);) {
            jedis.select(0);
            // Imati na umu: jedis.del(instanceName) će obrisati samo ključ instanceName,
            // ne sve dijelove. Ako želimo obrisati sve dijelove pri inicijalizaciji,
            // treba pronaći sve ključeve koji počinju sa instanceName + ":parts:map:"
            // i onda ih obrisati.
            // Za sada, ostavljam ovo kako je, ali imati na umu za brisanje cijele baze dijelova.
            // jedis.del(instanceName); 
        }

        partsToAdd.add(new Part("P001","Engine Filter","FilterCo",25.50,150,"High-performance engine air filter."));
        partsToAdd.add(new Part("P002","Brake Pad Set","BrakeMax",75.00,80,"Ceramic brake pads for superior stopping power."));
        partsToAdd.add(new Part("P003","Spark Plug (Iridium)","IgniteTech",12.99,300,"Long-life iridium spark plugs for improved combustion."));
        partsToAdd.add(new Part("P004","Oil Filter","OilGuard",8.75,200,"Premium oil filter for optimal engine protection."));
        partsToAdd.add(new Part("P005","Headlight Bulb (LED)","BrightLight",35.99,60,"Energy-efficient LED headlight bulb."));
        partsToAdd.add(new Part("P006","Tire Pressure Sensor","SensorPro",45.00,90,"Accurate tire pressure monitoring system sensor."));
        partsToAdd.add(new Part("P007","Wiper Blade Set","ClearView",18.25,120,"All-weather wiper blade set for clear visibility."));
        partsToAdd.add(new Part("P008","Alternator","PowerGen",180.00,20,"High-output alternator for reliable power supply."));
        partsToAdd.add(new Part("P009","Fuel Pump","FuelFlow",95.00,35,"Electric fuel pump for consistent fuel delivery."));
        partsToAdd.add(new Part("P010","Suspension Bushing","FlexRide",22.00,100,"Durable suspension bushing for improved ride comfort."));
    }

    public static PartRepository getInstance() {
        if (instance == null)
            instance = new PartRepository();
        return instance;
    }

    static {
        LOGGER.log(Level.INFO, "--> Entering PartRepository static initializer block.");

        instanceName = PropertiesUtil.getProperty("REDIS_INSTANCE_NAME");
        redisHost = PropertiesUtil.getProperty("REDIS_HOST");

        LOGGER.log(Level.INFO, "PartRepository static initializer: After reading properties.");
        LOGGER.log(Level.INFO, "Value read for REDIS_INSTANCE_NAME: '" + instanceName + "'");
        LOGGER.log(Level.INFO, "Value read for REDIS_HOST: '" + redisHost + "'");

        initializePartDb();
        parts = getAllParts(); //Popunjen in-memory keš odmah pri pokretanju, osigurava da lista nije nikada null kada joj se pokusa pristupiti

        LOGGER.log(Level.INFO, "<-- Exiting PartRepository static initializer block.");
    }

    public static List<Map<String, String>> getAllParts() {
        List<Map<String, String>> partsRefreshed = new ArrayList<>();

        try (Jedis jedis = new Jedis(redisHost);) {
            jedis.select(0);
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams scanParams = new ScanParams().match(instanceName + ":parts:map:*").count(100); // Dodati match pattern

            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                List<String> keys = scanResult.getResult();

                for (String key : keys) {
                    String type = jedis.type(key);

                    if ("hash".equals(type)) {
                        Map<String, String> fields = jedis.hgetAll(key);
                        partsRefreshed.add(fields);
                    }
                }

                cursor = scanResult.getStringCursor();
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));
        }
        parts = partsRefreshed;
        return parts;
    }

    public static void initializePartDb() {
        generateParts();

        try (Jedis jedis = new Jedis(redisHost)) {
            jedis.select(0);
            // Opcionalno: Obrisati sve postojeće dijelove koji pripadaju ovoj instanci prije inicijalizacije
            // To je sigurno ako želimo da baza bude čista pri svakom pokretanju.
            // Na primer, možemo obrisati sve ključeve koji počinju sa instanceName + ":parts:map:*"
            // Skeniramo i obrišemo sve ključeve
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams scanParams = new ScanParams().match(instanceName + ":parts:map:*").count(100);
            do {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                List<String> keysToDelete = scanResult.getResult();
                if (!keysToDelete.isEmpty()) {
                    jedis.del(keysToDelete.toArray(new String[0]));
                }
                cursor = scanResult.getStringCursor();
            } while (!cursor.equals(ScanParams.SCAN_POINTER_START));


            for(Part p : partsToAdd) {
                Map<String, String> partMap = new HashMap<>();
                partMap.put("code", p.getCode());
                partMap.put("name", p.getName());
                partMap.put("manufacturer", p.getManufacturer());
                partMap.put("price", String.valueOf(p.getPrice()));
                partMap.put("quantity", String.valueOf(p.getQuantity())); // Inicijalna količina
                partMap.put("description", p.getDescription());

                jedis.hmset(instanceName + ":parts:map:" + p.getCode(), partMap);
                LOGGER.log(Level.INFO, "Initialized part: " + p.getCode());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing parts database: " + e.getMessage(), e);
        }
    }

    public static void deletePartDb(String partCode) {
        try (Jedis jedis = new Jedis(redisHost)) {
            jedis.select(0);
            jedis.del(instanceName + ":parts:map:" + partCode);
            LOGGER.log(Level.INFO, "Deleted part: " + partCode);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error deleting part " + partCode + ": " + e.getMessage(), e);
        }
    }

    //metoda za dodavanje/ažuriranje dijela
    public static void addOrUpdatePart(Part part) {
        try (Jedis jedis = new Jedis(redisHost)) {
            jedis.select(0);
            String partKey = instanceName + ":parts:map:" + part.getCode();

            // Provjeri da li dio već postoji u Redis-u
            if (jedis.exists(partKey)) {
                // Dio postoji, ažuriraj samo količinu
                // HINCRBY je idealan za inkrementiranje numeričkih polja unutar Hash-a
                jedis.hincrBy(partKey, "quantity", part.getQuantity()); // Dodaj quantity iz dolaznog part objekta
                LOGGER.log(Level.INFO, "Updated quantity for existing part: " + part.getCode() + " by " + part.getQuantity());


            } else {
                // Dio ne postoji, dodaj ga kao novi unos
                Map<String, String> partMap = new HashMap<>();
                partMap.put("code", part.getCode());
                LOGGER.info("DEBUG (PartRepository): Part Name before Redis: " + part.getName());
                partMap.put("name", part.getName());
                partMap.put("manufacturer", part.getManufacturer());
                partMap.put("price", String.valueOf(part.getPrice()));
                partMap.put("quantity", String.valueOf(part.getQuantity())); // Inicijalna količina
                partMap.put("description", part.getDescription());

                jedis.hmset(partKey, partMap);
                LOGGER.log(Level.INFO, "Added new part to Redis: " + part.getCode() + " with quantity " + part.getQuantity());
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error adding or updating part " + part.getCode() + ": " + e.getMessage(), e);
        }
    }

    // Postojeća updatePart metoda može ostati za "standardno" ažuriranje SVIH polja
    // ili se može ukloniti ako je addOrUpdatePart dovoljna.
    // Ako updatePart ostane, ona bi se koristila za scenarije gdje se svi atributi dijela (osim količine) mijenjaju.
    public static void updatePart(Part part) {
        try (Jedis jedis = new Jedis(redisHost);) {
            jedis.select(0);
            Map<String, String> partMap = new HashMap<>();
            partMap.put("code", part.getCode());
            partMap.put("name", part.getName());
            partMap.put("manufacturer", part.getManufacturer());
            partMap.put("price", String.valueOf(part.getPrice()));
            partMap.put("quantity", String.valueOf(part.getQuantity())); // Ovo će *postaviti* novu količinu, ne inkrementirati
            partMap.put("description", part.getDescription());
            jedis.hmset(instanceName + ":parts:map:" + part.getCode(), partMap);
            LOGGER.log(Level.INFO, "Fully updated part (all fields overwritten): " + part.getCode());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating all fields for part " + part.getCode() + ": " + e.getMessage(), e);
        }
    }

    public static List<Map<String, String>> getPartsByCodes(List<String> partCodes) {
        List<Map<String, String>> foundParts = new ArrayList<>();

        if (partCodes == null || partCodes.isEmpty()) {
            LOGGER.warning("Info: getPartsByCodes called with null or empty list of codes.");
            return foundParts;
        }

        if (redisHost == null || redisHost.trim().isEmpty() || instanceName == null || instanceName.trim().isEmpty()) {
            LOGGER.warning("Error: Redis host or instance name not configured in PartRepository.");
            return foundParts;
        }

        try (Jedis jedis = new Jedis(redisHost);) {
            jedis.select(0);

            for (String partCode : partCodes) {
                if (partCode != null && !partCode.trim().isEmpty()) {
                    String redisKey = instanceName + ":parts:map:" + partCode.trim();

                    Map<String, String> partMap = jedis.hgetAll(redisKey);

                    if (partMap != null && !partMap.isEmpty()) {
                        if (partMap.containsKey("code") && partCode.trim().equals(partMap.get("code"))) {
                            foundParts.add(partMap);
                        } else {
                            LOGGER.warning("Warning: Data found for key " + redisKey + " does not appear to be a valid part hash (missing or mismatched 'code').");
                        }
                    } else {
                        LOGGER.info("Info: Part with CODE '" + partCode + "' not found in Redis at key '" + redisKey + "'.");
                    }
                } else {
                    LOGGER.info("Info: Skipping null or empty part code in getPartsByCodes list.");
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,"Error communicating with Redis while fetching parts by codes: " + e.getMessage(),e);
        }

        return foundParts;
    }
}