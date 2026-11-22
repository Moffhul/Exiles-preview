import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import org.codehaus.janino.JavaSourceClassLoader;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStatWithTempMods;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.*;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

/** Offline boundary checks against the Janino-loaded production helper and vanilla temp-mod class.
 * Proxies replace campaign engine objects; this does not test movement, visuals, or save serialization.
 */
public class ExileRefugeeSalvageCheck {
    interface Call { Object call(String method, Object[] args); }
    static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            Object value = call.call(m.getName(), a == null ? new Object[0] : a);
            if (value != null || !m.getReturnType().isPrimitive()) return value;
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == float.class) return 0f;
            if (m.getReturnType() == long.class) return 0L;
            if (m.getReturnType() == double.class) return 0d;
            return 0;
        }));
    }
    static void require(boolean pass, String message) { if (!pass) throw new AssertionError(message); }
    static String withoutComments(String text) {
        StringBuilder result = new StringBuilder();
        boolean quoted=false, escaped=false, comment=false;
        for (char c : text.toCharArray()) {
            if (c=='\ufeff') continue;
            if (comment) { if (c=='\n') { comment=false; result.append(c); } continue; }
            if (!quoted && c=='#') { comment=true; continue; }
            result.append(c);
            if (escaped) { escaped=false; continue; }
            if (quoted && c=='\\') { escaped=true; continue; }
            if (c=='"') quoted=!quoted;
        }
        return result.toString();
    }
    static class Cargo {
        Map<String, Float> amounts = new LinkedHashMap<>();
        CargoAPI api = proxy(CargoAPI.class, (m,a) -> {
            if (m.equals("getStacksCopy")) {
                List<CargoStackAPI> result = new ArrayList<>();
                amounts.forEach((id,n) -> { if (n > 0) result.add(stack(id,n)); });
                return result;
            }
            if (m.equals("getQuantity")) return amounts.getOrDefault((String)a[1],0f);
            if (m.equals("addItems") || m.equals("removeItems")) {
                float sign = m.equals("addItems") ? 1 : -1;
                amounts.merge((String)a[1], sign * ((Number)a[2]).floatValue(), Float::sum);
            }
            if (m.equals("clear")) amounts.clear();
            return null;
        });
    }
    static CargoStackAPI stack(String id, float amount) {
        boolean weapon = id.equals("tachyonlance");
        return proxy(CargoStackAPI.class, (m,a) -> {
            switch (m) {
                case "isWeaponStack": return weapon;
                case "isCommodityStack": return !weapon && !id.equals("hullmod_item") && !id.equals("talon_wing");
                case "getType": return weapon ? CargoAPI.CargoItemType.WEAPONS : CargoAPI.CargoItemType.RESOURCES;
                case "getData": case "getCommodityId": return id;
                case "getSize": return amount;
            }
            return null;
        });
    }

    static MemoryAPI memory(Map<String,Object> values) {
        return proxy(MemoryAPI.class,(m,a) -> {
            switch (m) {
                case "get": return values.get(a[0]);
                case "contains": return values.containsKey(a[0]);
                case "getBoolean": return Boolean.TRUE.equals(values.get(a[0]));
                case "getString": return values.get(a[0]) == null ? null : String.valueOf(values.get(a[0]));
                case "set": values.put((String)a[0],a[1]); break;
                case "unset": values.remove(a[0]); break;
            }
            return null;
        });
    }
    static PerShipData ship(HullSize size) {
        ShipHullSpecAPI hull = proxy(ShipHullSpecAPI.class,(m,a) -> m.equals("getHullSize") ? size : null);
        ShipVariantAPI variant = proxy(ShipVariantAPI.class,(m,a) -> m.equals("getHullSpec") ? hull : null);
        return new PerShipData(variant, ShipCondition.AVERAGE, "Fixture", "independent", 0);
    }
    static void selectionAndProximity(JavaSourceClassLoader loader) throws Exception {
        Class<?> helper = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageRecovery");
        Class<?> manager = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageExpeditionManager");
        loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageAssignmentAI");
        List<SectorEntityToken> wrecks = new ArrayList<>();
        List<CampaignTerrainAPI> fields = new ArrayList<>();
        Vector2f systemPos = new Vector2f();
        StarSystemAPI system = proxy(StarSystemAPI.class,(m,a) -> {
            switch (m) {
                case "getEntitiesWithTag": return wrecks;
                case "getTerrainCopy": return fields;
                case "getLocation": return systemPos;
            }
            return null;
        });
        PerShipData capital = ship(HullSize.CAPITAL_SHIP);
        PerShipData frigate = ship(HullSize.FRIGATE);
        Map<String,Object> capMemory = new HashMap<>();
        Vector2f capPos = new Vector2f(2000,0);
        for (PerShipData ship : new PerShipData[]{frigate,capital}) {
            DerelictShipData data = new DerelictShipData(ship,true);
            DerelictShipEntityPlugin plugin = new DerelictShipEntityPlugin() {
                public DerelictShipData getData() { return data; }
            };
            MemoryAPI mem = memory(ship==capital ? capMemory : new HashMap<>());
            Vector2f pos = ship==capital ? capPos : new Vector2f(100,0);
            wrecks.add(proxy(SectorEntityToken.class,(m,a) -> {
                switch (m) {
                    case "isAlive": return true;
                    case "getContainingLocation": return system;
                    case "getMemoryWithoutUpdate": return mem;
                    case "getLocation": return pos;
                    case "getCustomEntityType": return Entities.WRECK;
                    case "getCustomPlugin": return plugin;
                    case "hasTag": return Tags.SALVAGEABLE.equals(a[0]) || Tags.EXPIRES.equals(a[0]);
                }
                return null;
            }));
        }
        Method select = helper.getMethod("findPreferredTarget",LocationAPI.class,Vector2f.class);
        Vector2f origin = new Vector2f();
        require(select.invoke(null,system,origin)==wrecks.get(1),"Near frigate masked valuable capital");
        capPos.x=50000;
        require(select.invoke(null,system,origin)==wrecks.get(0),"Fleet pursued impractically distant capital");
        capPos.x=2000;
        capMemory.put(MemFlags.ENTITY_MISSION_IMPORTANT,true);
        require(select.invoke(null,system,origin)==wrecks.get(0),"Value bypassed mission protection");
        capMemory.clear();
        capMemory.put("$exiles_refugee_salvage_consumed",true);
        require(select.invoke(null,system,origin)==wrecks.get(0),"Value bypassed consumed protection");
        capMemory.clear();
        capMemory.put(MemFlags.SALVAGE_SPECIAL_DATA,new ShipRecoverySpecialData(null));
        require(select.invoke(null,system,origin)==wrecks.get(0),"Spent wreck reused original plugin ship");
        helper.getMethod("collect",SectorEntityToken.class,CampaignFleetAPI.class,CargoAPI.class)
                .invoke(null,wrecks.get(1),null,null);
        capMemory.clear();
        wrecks.remove(1);
        ShipRecoverySpecialData recovery = new ShipRecoverySpecialData(null);
        recovery.ships.add(capital);
        Map<String,Object> fieldMemory = new HashMap<>();
        fieldMemory.put(MemFlags.SALVAGE_SPECIAL_DATA,recovery);
        MemoryAPI fieldMem = memory(fieldMemory);
        DebrisFieldTerrainPlugin debris = new DebrisFieldTerrainPlugin();
        debris.params = new DebrisFieldTerrainPlugin.DebrisFieldParams(100,1,10,1);
        debris.params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.BATTLE;
        CampaignTerrainAPI terrain = proxy(CampaignTerrainAPI.class,(m,a) -> {
            switch (m) {
                case "isAlive": return true;
                case "getContainingLocation": return system;
                case "getMemoryWithoutUpdate": return fieldMem;
                case "getLocation": return capPos;
                case "getPlugin": return debris;
            }
            return null;
        });
        fields.add(terrain);
        require(select.invoke(null,system,origin)==terrain,"Ship-bearing debris lost capital preference");
        require(recovery.ships.size()==1 && !debris.isScavenged() && fieldMemory.size()==1,
                "Scoring mutated salvage");
        recovery.ships.clear();
        require(select.invoke(null,system,origin)==wrecks.get(0),"Empty debris kept stale capital value");
        capital.variant=null;
        capital.variantId="broken_fixture";
        recovery.ships.add(capital);
        require(select.invoke(null,system,origin)==wrecks.get(0),"Missing variant broke selection");

        LocationAPI[] playerLocation = {null};
        Vector2f playerPos = new Vector2f();
        boolean[] transition = {false};
        CampaignFleetAPI player = proxy(CampaignFleetAPI.class,(m,a) -> {
            switch (m) {
                case "getContainingLocation": return playerLocation[0];
                case "getLocationInHyperspace": return playerPos;
                case "isInHyperspaceTransition": return transition[0];
            }
            return null;
        });
        Method inRange = manager.getMethod("withinSpawnRange",StarSystemAPI.class,CampaignFleetAPI.class,float.class);
        float unitsPerLY = 1f/Misc.getDistanceLY(new Vector2f(),new Vector2f(1,0));
        playerPos.x=1.59f*unitsPerLY;
        require((Boolean)inRange.invoke(null,system,player,1.6f),"Nearby hyperspace excluded");
        playerPos.x=1.61f*unitsPerLY;
        require(!(Boolean)inRange.invoke(null,system,player,1.6f),"Distant launch allowed");
        playerLocation[0]=system;
        require((Boolean)inRange.invoke(null,system,player,0f),"Current system excluded at zero radius");
        transition[0]=true;
        require(!(Boolean)inRange.invoke(null,system,player,1.6f),"Transition allowed a launch");
        transition[0]=false; playerLocation[0]=null; playerPos.x=0;
        require(!(Boolean)inRange.invoke(null,system,player,0f),"Zero radius allowed another location");
        require(!(Boolean)inRange.invoke(null,system,null,1.6f),"Null player allowed launch");
        System.out.println("PASS: large-wreck preference, travel penalty, mission/consumed exclusions, ship-bearing debris, read-only scoring, malformed variant fallback and proximity boundaries.");
    }
    static void dispatchLifecycle(JavaSourceClassLoader loader) throws Exception {
        Class<?> managerClass = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageExpeditionManager");
        Object manager = managerClass.getConstructor().newInstance();
        Class<?> cfgClass = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageConfig");
        Object cfg = cfgClass.getConstructor().newInstance();
        cfgClass.getField("maxActiveExpeditions").setInt(cfg,1);
        Field cfgField = managerClass.getDeclaredField("config"); cfgField.setAccessible(true); cfgField.set(manager,cfg);
        List<SectorEntityToken> targets = new ArrayList<>();
        List<CampaignFleetAPI> liveFleets = new ArrayList<>();
        Map<String,Object> systemValues = new HashMap<>();
        MemoryAPI systemMemory = memory(systemValues);
        int[] scans = {0};
        StarSystemAPI system = proxy(StarSystemAPI.class,(m,a) -> {
            switch (m) {
                case "hasTag": return "theme_core".equals(a[0]);
                case "getLocation": return new Vector2f();
                case "getMemoryWithoutUpdate": return systemMemory;
                case "getFleets": return liveFleets;
                case "getEntitiesWithTag": scans[0]++; return targets;
                case "getTerrainCopy": return new ArrayList<CampaignTerrainAPI>();
            }
            return null;
        });
        DebrisFieldTerrainPlugin debris = new DebrisFieldTerrainPlugin();
        debris.params = new DebrisFieldTerrainPlugin.DebrisFieldParams(100,1,10,1);
        debris.params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.BATTLE;
        MemoryAPI targetMemory = memory(new HashMap<>());
        targets.add(proxy(CampaignTerrainAPI.class,(m,a) -> {
            switch (m) {
                case "isAlive": return true;
                case "getContainingLocation": return system;
                case "getMemoryWithoutUpdate": return targetMemory;
                case "getPlugin": return debris;
            }
            return null;
        }));
        MemoryAPI activeMemory = memory(new HashMap<>());
        CampaignFleetAPI active = proxy(CampaignFleetAPI.class,(m,a) -> {
            switch (m) {
                case "isAlive": return true;
                case "getContainingLocation": return system;
                case "getMemoryWithoutUpdate": return activeMemory;
            }
            return null;
        });
        liveFleets.add(active);
        Field fleetField = managerClass.getDeclaredField("fleets"); fleetField.setAccessible(true);
        ((List<CampaignFleetAPI>)fleetField.get(manager)).add(active);
        Vector2f playerPos = new Vector2f();
        CampaignFleetAPI player = proxy(CampaignFleetAPI.class,(m,a) -> m.equals("getLocationInHyperspace") ? playerPos : null);
        Industry industry = proxy(Industry.class,(m,a) -> m.equals("isFunctional") ? true : null);
        SectorEntityToken[] dock = {null};
        MarketAPI home = proxy(MarketAPI.class,(m,a) -> {
            switch (m) {
                case "getFactionId": return "exilerefugee";
                case "hasIndustry": return true;
                case "getIndustry": return industry;
                case "getPrimaryEntity": return dock[0];
            }
            return null;
        });
        SectorEntityToken recipient = proxy(SectorEntityToken.class,(m,a) -> {
            switch (m) {
                case "isAlive": return true;
                case "getMarket": return home;
                case "getContainingLocation": return system;
                case "getLocation": return new Vector2f();
            }
            return null;
        });
        dock[0]=recipient;
        EconomyAPI economy = proxy(EconomyAPI.class,(m,a) -> m.equals("getMarketsCopy") ? Arrays.asList(home) : null);
        CampaignClockAPI clock = proxy(CampaignClockAPI.class,(m,a) -> m.equals("convertToDays") ? a[0] : null);
        List<StarSystemAPI> systems = new ArrayList<>(Arrays.asList(system));
        SectorAPI oldSector = Global.getSector();
        Global.setSector(proxy(SectorAPI.class,(m,a) -> {
            switch (m) {
                case "getClock": return clock;
                case "getPlayerFleet": return player;
                case "getStarSystems": return systems;
                case "getEconomy": return economy;
                case "getEntityById": return "corvus_abandoned_station".equals(a[0]) ? recipient : null;
            }
            return null;
        }));
        try {
            Method advance = managerClass.getMethod("advance",float.class);
            Field pendingField = managerClass.getDeclaredField("pending"); pendingField.setAccessible(true);
            Map<StarSystemAPI,Float> pending = (Map<StarSystemAPI,Float>)pendingField.get(manager);
            advance.invoke(manager,1f);
            Float deadline = pending.get(system);
            require(deadline!=null && scans[0]==1,"Nearby opportunity did not start response timer");
            playerPos.x=100000;
            advance.invoke(manager,5f);
            require(scans[0]==1 && deadline.equals(pending.get(system)),"Leaving range scanned distant salvage or reset timer");
            require(((List<?>)fleetField.get(manager)).contains(active),"Leaving range removed an active fleet");
            playerPos.x=0;
            advance.invoke(manager,1f);
            require(scans[0]==2 && deadline.equals(pending.get(system)),"Re-entry reset response deadline");
            require(((List<?>)fleetField.get(manager)).size()==1,"Global fleet cap was bypassed");
            targets.clear(); advance.invoke(manager,1f);
            require(pending.isEmpty(),"Empty nearby battlefield kept response timer");
            pending.put(system,10f); playerPos.x=100000; systems.clear(); advance.invoke(manager,1f);
            require(pending.isEmpty(),"Removed system retained pending reference");
            pending.put(system,10f); cfgClass.getField("maxActiveExpeditions").setInt(cfg,0); advance.invoke(manager,1f);
            require(pending.isEmpty() && ((List<?>)fleetField.get(manager)).contains(active),"Disable did not clear timers or killed active fleet");
            com.fs.starfarer.api.FactoryAPI oldFactory = Global.getFactory();
            Global.setFactory(proxy(com.fs.starfarer.api.FactoryAPI.class,(m,a) -> m.equals("createCargo") ? new Cargo().api : null));
            try {
                Vector2f returnPos = new Vector2f(1000,0);
                int[] despawns = {0};
                CampaignFleetAPI returning = proxy(CampaignFleetAPI.class,(m,a) -> {
                    switch (m) {
                        case "isAlive": return true;
                        case "getContainingLocation": return system;
                        case "getLocation": return returnPos;
                        case "getLocationInHyperspace": return playerPos;
                        case "despawn": despawns[0]++; break;
                    }
                    return null;
                });
                Class<?> aiClass = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageAssignmentAI");
                Object ai = aiClass.getConstructor(CampaignFleetAPI.class,MarketAPI.class,cfgClass).newInstance(returning,home,cfg);
                Field isReturning = aiClass.getDeclaredField("returning"); isReturning.setAccessible(true); isReturning.setBoolean(ai,true);
                Method step = aiClass.getMethod("advance",float.class);
                step.invoke(ai,1f);
                require(despawns[0]==0,"Visible returning fleet retired before arriving");
                returnPos.x=100;
                step.invoke(ai,1f);
                require(despawns[0]==1,"Arrival at home failed to retire visible fleet");
                step.invoke(ai,1f);
                require(despawns[0]==1,"Arrival retirement ran twice");
                System.out.println("PASS: visible return waits for arrival, retires at home, and cannot deliver/despawn twice.");
            } finally { Global.setFactory(oldFactory); }
        } finally { Global.setSector(oldSector); }
        System.out.println("PASS: real dispatcher skips distant salvage scans, preserves response deadlines across travel, keeps active fleets, obeys global cap and clears obsolete timers.");
    }
    static void dangerPolicy(JavaSourceClassLoader loader) throws Exception {
        Class<?> cfgClass = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageConfig");
        Object cfg = cfgClass.getConstructor().newInstance();
        Class<?> threats = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageThreats");
        List<CampaignFleetAPI> fleets = new ArrayList<>();
        LocationAPI location = proxy(LocationAPI.class,(m,a) -> m.equals("getFleets") ? fleets : null);
        FactionAPI pirates = proxy(FactionAPI.class,(m,a) -> {
            if (m.equals("getId")) return Factions.PIRATES;
            if (m.equals("isHostileTo")) return true;
            return null;
        });
        Vector2f marketPos = new Vector2f();
        SectorEntityToken marketEntity = proxy(SectorEntityToken.class,(m,a) -> {
            if (m.equals("getContainingLocation")) return location;
            if (m.equals("getLocation")) return marketPos;
            return null;
        });
        MarketAPI pirateMarket = proxy(MarketAPI.class,(m,a) -> {
            switch (m) {
                case "getId": return "pirate_market";
                case "getFactionId": return Factions.PIRATES;
                case "getFaction": return pirates;
                case "getPrimaryEntity": return marketEntity;
                case "getContainingLocation": return location;
            }
            return null;
        });
        EconomyAPI economy = proxy(EconomyAPI.class,(m,a) -> {
            if (m.equals("getMarkets")) return Arrays.asList(pirateMarket);
            if (m.equals("getMarket") && "pirate_market".equals(a[0])) return pirateMarket;
            return null;
        });
        Vector2f targetPos = new Vector2f(5000,0);
        SectorEntityToken target = proxy(SectorEntityToken.class,(m,a) -> {
            if (m.equals("getContainingLocation")) return location;
            if (m.equals("getLocation")) return targetPos;
            return null;
        });
        SectorAPI oldSector = Global.getSector();
        Global.setSector(proxy(SectorAPI.class,(m,a) -> m.equals("getEconomy") ? economy : null));
        try {
            Method safe = threats.getMethod("isTargetSafe",SectorEntityToken.class,cfgClass);
            Method find = threats.getMethod("findLaunchThreat",LocationAPI.class,SectorEntityToken.class,cfgClass);
            require((Boolean)safe.invoke(null,target,cfg),"Distant pirate market excluded safe salvage");
            targetPos.x=2000;
            require(!(Boolean)safe.invoke(null,target,cfg),"Hostile market bubble allowed unsafe salvage");
            targetPos.x=5000;

            Map<String,Object> stationMem = new HashMap<>();
            CampaignFleetAPI station = threatFleet(location,pirates,new Vector2f(),stationMem,true);
            fleets.add(station);
            require(find.invoke(null,location,target,cfg)==null,"Station poisoned the whole system");
            fleets.clear();

            Map<String,Object> patrolMem = new HashMap<>();
            patrolMem.put(MemFlags.MEMORY_KEY_FLEET_TYPE,FleetTypes.PATROL_SMALL);
            patrolMem.put(MemFlags.MEMORY_KEY_SOURCE_MARKET,"pirate_market");
            Vector2f patrolPos = new Vector2f(500,0);
            CampaignFleetAPI patrol = threatFleet(location,pirates,patrolPos,patrolMem,false);
            fleets.add(patrol);
            require(find.invoke(null,location,target,cfg)==null,"Contained home patrol blocked distant salvage");
            patrolPos.x=3500;
            require(find.invoke(null,location,target,cfg)==patrol,"Roaming patrol failed to block dispatch");
            patrolPos.x=500; targetPos.x=2600;
            require(find.invoke(null,location,target,cfg)==patrol,"Home patrol beside the operation was ignored");
            targetPos.x=5000; patrolMem.put(MemFlags.MEMORY_KEY_RAIDER,true);
            require(find.invoke(null,location,target,cfg)==patrol,"Raider received home-patrol exemption");
            patrolMem.remove(MemFlags.MEMORY_KEY_RAIDER);
            patrolMem.put(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE,true);
            require(find.invoke(null,location,target,cfg)==null,"Explicitly non-aggressive fleet blocked dispatch");
        } finally { Global.setSector(oldSector); }
        System.out.println("PASS: hostile-market bubble, station exclusion, contained/roaming patrol split, raider override and non-aggressive exclusion.");
    }
    static CampaignFleetAPI threatFleet(LocationAPI location, FactionAPI faction, Vector2f position,
                                         Map<String,Object> values, boolean station) {
        MemoryAPI mem = memory(values);
        return proxy(CampaignFleetAPI.class,(m,a) -> {
            switch (m) {
                case "isAlive": return true;
                case "isStationMode": return station;
                case "getContainingLocation": return location;
                case "getLocation": return position;
                case "getFaction": return faction;
                case "getMemoryWithoutUpdate": return mem;
                case "getEffectiveStrength": return 20f;
                case "getName": return "Test threat";
                case "getId": return "test_threat";
            }
            return null;
        });
    }
    public static void main(String[] args) throws Exception {
        Global.setSettings(proxy(SettingsAPI.class, (m,a) -> m.equals("getFloat") ? 1f : null));
        JavaSourceClassLoader loader = new JavaSourceClassLoader(ClassLoader.getSystemClassLoader(), new File[]{new File(args[0])}, null);
        Class<?> helper = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageRecovery");
        Class<?> configClass = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageConfig");
        Object config = configClass.getConstructor().newInstance();
        org.json.JSONObject director = new org.json.JSONObject(withoutComments(java.nio.file.Files.readString(
                new File(args[0],"data/config/exiles/director_config.json").toPath())));
        org.json.JSONObject faction = director.getJSONObject("exilerefugee");
        org.json.JSONObject tuning = faction.getJSONObject("salvageExpeditions");
        require(tuning.getInt("maxActiveExpeditions")==3 && tuning.getDouble("responseMinDays")==2
                && tuning.getDouble("responseMaxDays")==4 && tuning.getDouble("salvageDays")==0.5
                && tuning.getJSONObject("danger").getDouble("hostileMarketSafetyRadius")==2500,
                "Shipped tuning disagrees with approved defaults");
        Method allowed = helper.getMethod("allowed", CargoStackAPI.class);
        for (String id : new String[]{"supplies","fuel","metals","tachyonlance"})
            require((Boolean)allowed.invoke(null,stack(id,1)), "Rejected eligible cargo " + id);
        for (String id : new String[]{"alpha_core","exiles_omicron_core_1","hullmod_item","talon_wing"})
            require(!(Boolean)allowed.invoke(null,stack(id,1)), "Allowed excluded cargo " + id);

        Map<String,Object> memory = new HashMap<>();
        MemoryAPI mem = proxy(MemoryAPI.class,(m,a) -> {
            if (m.equals("get")) return memory.get(a[0]);
            if (m.equals("contains")) return memory.containsKey(a[0]);
            if (m.equals("getBoolean")) return Boolean.TRUE.equals(memory.get(a[0]));
            return null;
        });
        DebrisFieldTerrainPlugin debris = new DebrisFieldTerrainPlugin();
        debris.params = new DebrisFieldTerrainPlugin.DebrisFieldParams(100,1,10,1);
        debris.params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.BATTLE;
        LocationAPI location = proxy(LocationAPI.class,(m,a) -> null);
        Set<String> tags = new HashSet<>();
        CampaignTerrainAPI terrain = proxy(CampaignTerrainAPI.class,(m,a) -> {
            switch (m) {
                case "isAlive": return true;
                case "getContainingLocation": return location;
                case "getPlugin": return debris;
                case "getMemoryWithoutUpdate": return mem;
                case "hasTag": return tags.contains(a[0]);
            }
            return null;
        });
        Method eligible = helper.getMethod("eligible",SectorEntityToken.class);
        require((Boolean)eligible.invoke(null,terrain),"Battle debris must qualify");
        memory.put(MemFlags.ENTITY_MISSION_IMPORTANT,true);
        require(!(Boolean)eligible.invoke(null,terrain),"Mission target qualified"); memory.clear();
        debris.setScavenged(true);
        require(!(Boolean)eligible.invoke(null,terrain),"Consumed debris qualified"); debris.setScavenged(false);
        debris.params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.GEN;
        require(!(Boolean)eligible.invoke(null,terrain),"Exploration debris qualified");
        debris.params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.BATTLE;
        debris.params.lastsDays = 100;
        require(!(Boolean)eligible.invoke(null,terrain),"Long-lived debris qualified");
        debris.params.lastsDays = 10; tags.add(Tags.FADING_OUT_AND_EXPIRING);
        require(!(Boolean)eligible.invoke(null,terrain),"Expiring target qualified");

        Cargo stock = new Cargo(); Cargo stores = new Cargo();
        MutableStatWithTempMods bonus = new MutableStatWithTempMods(0);
        bonus.addTemporaryModFlat(5,"exile_refugee_salvage_delivery_old",450);
        bonus.addTemporaryModFlat(100,"unrelated_mod",1000);
        CommodityOnMarketAPI commodity = proxy(CommodityOnMarketAPI.class,(m,a) -> {
            if (m.equals("getTradeModPlus")) return bonus;
            if (m.equals("addTradeModPlus")) bonus.addTemporaryModFlat(((Number)a[2]).floatValue(),(String)a[0],((Number)a[1]).floatValue());
            return null;
        });
        boolean[] refreshed = {false};
        SubmarketPlugin plugin = proxy(SubmarketPlugin.class,(m,a) -> {
            if (m.equals("updateCargoPrePlayerInteraction")) { stock.amounts.clear(); refreshed[0]=true; }
            return null;
        });
        SubmarketAPI open = proxy(SubmarketAPI.class,(m,a) -> m.equals("getPlugin") ? plugin : m.equals("getCargo") ? stock.api : null);
        MarketAPI home = proxy(MarketAPI.class,(m,a) -> m.equals("getSubmarket") ? open : m.equals("getCommodityData") ? commodity : m.equals("getName") ? "Test enclave" : null);
        CampaignFleetAPI fleet = proxy(CampaignFleetAPI.class,(m,a) -> m.equals("getCargo") ? stores.api : null);
        Cargo manifest = new Cargo();
        manifest.amounts.put("supplies",200f); stores.amounts.put("supplies",300f);
        manifest.amounts.put("tachyonlance",2f); stores.amounts.put("tachyonlance",1f);
        manifest.amounts.put("alpha_core",1f); stores.amounts.put("alpha_core",1f);
        Method deliver = helper.getMethod("deliver",MarketAPI.class,CampaignFleetAPI.class,CargoAPI.class,configClass);
        deliver.invoke(null,home,fleet,manifest.api,config);
        require(refreshed[0] && stock.amounts.get("tachyonlance")==1f,"Weapon refresh/order or cargo-loss handling failed");
        require(!stock.amounts.containsKey("alpha_core"),"Core reached market");
        require(stores.amounts.get("supplies")==100f,"Starting supplies sold");
        require(bonus.getModifiedValue()==1500f,"Commodity cap or unrelated modifier handling failed");
        bonus.advance(6);
        require(bonus.getModifiedValue()==1050f,"New delivery renewed old contribution");
        bonus.advance(25);
        require(bonus.getModifiedValue()==1000f,"New contribution did not expire");
        require(manifest.amounts.isEmpty(),"Delivery manifest retained items");
        selectionAndProximity(loader);
        dispatchLifecycle(loader);
        dangerPolicy(loader);
        System.out.println("PASS: item exclusions, target exclusions, weapon refresh ordering, loss-aware delivery, starting stores, commodity cap and independent expiry.");
    }
}
