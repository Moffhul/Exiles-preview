import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import org.codehaus.janino.JavaSourceClassLoader;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import exerelin.campaign.fleets.VultureFleetManager;

/** Runs installed Nex bytecode and Janino-loaded Exiles code on shared campaign API fixtures.
 * Does not simulate engine scheduling, fleet generation, movement, or save/load.
 * An empty loot table isolates target-state mutation from procedural item generation.
 */
public class ExileVultureCompatibilityCheck extends ExileRefugeeSalvageCheck {
    static class Memory {
        Map<String,Object> values = new HashMap<>();
        Map<String,Float> durations = new HashMap<>();
        MemoryAPI api = proxy(MemoryAPI.class, (m,a) -> {
            switch (m) {
                case "set": values.put((String)a[0],a[1]);
                    if (a.length > 2) durations.put((String)a[0], ((Number)a[2]).floatValue()); return null;
                case "unset": durations.remove(a[0]); return values.remove(a[0]);
                case "contains": return values.containsKey(a[0]);
                case "get": return values.get(a[0]);
                case "getBoolean": return Boolean.TRUE.equals(values.get(a[0]));
                case "getFloat": return ((Number)values.getOrDefault(a[0],0f)).floatValue();
                case "getExpire": return durations.getOrDefault(a[0],0f);
                case "getLong": return ((Number)values.getOrDefault(a[0],0L)).longValue();
            }
            return null;
        });
        void advance(float days) {
            for (String key : new ArrayList<>(durations.keySet())) {
                float left = durations.get(key)-days;
                if (left <= 0) { durations.remove(key); values.remove(key); }
                else durations.put(key,left);
            }
        }
    }
    static class Manager extends VultureFleetManager {
        int demand(StarSystemAPI system) { currSpawnLoc = system; return getDesiredNumFleetsForSpawnLocation(); }
    }
    static class World {
        Memory memory = new Memory();
        List<CampaignTerrainAPI> terrain = new ArrayList<>();
        List<CampaignFleetAPI> fleets = new ArrayList<>();
        StarSystemAPI api = proxy(StarSystemAPI.class, (m,a) -> {
            switch (m) {
                case "getTerrainCopy": return new ArrayList<>(terrain);
                case "getFleets": return fleets;
                case "getEntitiesWithTag": return new ArrayList<SectorEntityToken>();
                case "getMemoryWithoutUpdate": return memory.api;
                case "getLocation": return new Vector2f();
                case "getName": return "Shared test system";
            }
            return null;
        });
        CampaignTerrainAPI debris(float score, float x) {
            Memory mem = new Memory();
            DebrisFieldTerrainPlugin plugin = new DebrisFieldTerrainPlugin();
            plugin.params = new DebrisFieldTerrainPlugin.DebrisFieldParams(100,1,10,1);
            plugin.params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.BATTLE;
            plugin.params.baseSalvageXP = (long) score;
            List<com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData> drops = new ArrayList<>();
            CampaignTerrainAPI entity = proxy(CampaignTerrainAPI.class,(m,a) -> {
                switch (m) {
                    case "isAlive": return true;
                    case "getContainingLocation": return api;
                    case "getLocation": return new Vector2f(x,0);
                    case "getPlugin": return plugin;
                    case "getType": return Terrain.DEBRIS_FIELD;
                    case "getMemoryWithoutUpdate": return mem.api;
                    case "getDropValue": case "getDropRandom": return drops;
                    case "getId": return "fixture-"+x;
                }
                return null;
            });
            terrain.add(entity); return entity;
        }
        CampaignFleetAPI fleet(boolean hostile, float x) {
            FactionAPI faction = proxy(FactionAPI.class,(m,a) -> m.equals("isHostileTo") ? hostile : null);
            return proxy(CampaignFleetAPI.class,(m,a) -> {
                if (m.equals("getFaction")) return faction;
                if (m.equals("getLocation")) return new Vector2f(x,0);
                if (m.equals("getContainingLocation")) return api;
                if (m.equals("getNameWithFactionKeepCase")) return hostile ? "Hostile fixture" : "Neutral fixture";
                return null;
            });
        }
    }
    static void pass(String s) { System.out.println("PASS: "+s); }
    public static void main(String[] args) throws Exception {
        ModManagerAPI mods = proxy(ModManagerAPI.class,(m,a) -> m.equals("isModEnabled") ? true : null);
        Global.setSettings(proxy(SettingsAPI.class,(m,a) -> {
            if (m.equals("getFloat")) return 1f;
            if (m.equals("getModManager")) return mods;
            if (m.equals("getScriptClassLoader")) return ClassLoader.getSystemClassLoader();
            return null;
        }));
        // No active raid: exercises salvage-driven demand and its installed cache implementation.
        com.fs.starfarer.api.campaign.comm.IntelManagerAPI intel = proxy(
                com.fs.starfarer.api.campaign.comm.IntelManagerAPI.class,
                (m,a) -> m.equals("getIntel") ? new ArrayList<>() : null);
        World world = new World();
        CampaignFleetAPI refugee = world.fleet(false,0);
        Global.setSector(proxy(SectorAPI.class,(m,a) -> {
            if (m.equals("getIntelManager")) return intel;
            if (m.equals("getPlayerFleet")) return refugee;
            if (m.equals("getPersistentData")) return new HashMap<String,Object>();
            return null;
        }));
        Global.setFactory(proxy(FactoryAPI.class,(m,a) -> m.equals("createCargo") ? new Cargo().api : null));
        // Reproduce the restriction that caused the live startup crash. This is a focused
        // reflection guard, not a complete emulation of Starsector's script sandbox.
        ClassLoader restricted = new ClassLoader(ClassLoader.getSystemClassLoader()) {
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("java.lang.reflect.Method")) throw new SecurityException("Scripts cannot use reflection: " + name);
                return super.loadClass(name,resolve);
            }
        };
        JavaSourceClassLoader loader = new JavaSourceClassLoader(restricted,new File[]{new File(args[0])},null);
        Class<?> recovery = loader.loadClass("data.scripts.exiles.ExileRefugeeSalvageRecovery");
        Method find = recovery.getMethod("findTarget",LocationAPI.class,CampaignFleetAPI.class);
        Method collect = recovery.getMethod("collect",SectorEntityToken.class,CampaignFleetAPI.class,CargoAPI.class);
        Manager manager = new Manager();
        CampaignTerrainAPI first = world.debris(2400,0);
        world.fleets.add(refugee);
        world.fleets.add(world.fleet(false,100)); // Friendly Heg/League-like presence.
        require(VultureFleetManager.getClosestScavengable(new Vector2f(),world.api,null)==first,"Nex cannot see untouched debris");
        require(find.invoke(null,world.api,refugee)==first,"Refugees cannot see shared debris");
        require(manager.demand(world.api)==2,"First salvage demand must be two");
        Class<?> diagnostic = loader.loadClass("data.scripts.exiles.ExileSalvageDiagnostics");
        Map<String,Object> cacheBefore = new HashMap<>(world.memory.values);
        Map<String,Float> expiryBefore = new HashMap<>(world.memory.durations);
        String snapshot = (String)diagnostic.getMethod("snapshot",LocationAPI.class).invoke(null,world.api);
        require(snapshot.contains("baseXP=2400") && snapshot.contains("fixture-0.0") && snapshot.contains("cachedDemand=0.0"),"Missing diagnostic target/cache");
        require(world.memory.values.equals(cacheBefore) && world.memory.durations.equals(expiryBefore),"Diagnostics mutated Nex cache");
        pass("Diagnostic snapshot reports debris and Nex cache without changing cache contents or expiry.");
        pass("Both implementations accept untouched debris with neutral fleets present; Nex demand=2.");
        require(manager.demand(world.api)==0,"Installed Nex cache no longer reproduces zero-demand issue");
        require(VultureFleetManager.getClosestScavengable(new Vector2f(),world.api,null)==first,"Cache changed eligibility");
        world.memory.advance(2.01f);
        require(manager.demand(world.api)==2,"Demand did not recover after cache expiry");
        pass("Nex demand is 2 -> 0 -> 2 after cache expiry, without any Refugee collection; target stays eligible.");
        collect.invoke(null,first,refugee,new Cargo().api);
        require(((DebrisFieldTerrainPlugin)first.getPlugin()).isScavenged(),"Actual collection did not scavenge debris");
        require(world.terrain.contains(first),"Fixture terrain disappeared");
        require(VultureFleetManager.getClosestScavengable(new Vector2f(),world.api,null)==null,"Nex accepted consumed debris");
        require(find.invoke(null,world.api,refugee)==null,"Refugees accepted consumed debris");
        MarketAPI origin = proxy(MarketAPI.class,(m,a) -> m.equals("getContainingLocation") ? world.api :
                m.equals("getLocation") ? new Vector2f() : m.equals("getName") ? "Test origin" : null);
        require(manager.spawnVultureFleet(origin)==null,"Spawn should fail with only consumed debris");
        pass("Real Refugee collect leaves visible terrain but removes both target eligibility and Vulture spawn opportunity.");
        CampaignTerrainAPI second = world.debris(1300,4000);
        require(VultureFleetManager.getClosestScavengable(new Vector2f(),world.api,null)==second,"New debris blocked after collection");
        world.memory.advance(2.01f);
        require(manager.demand(world.api)==1,"New eligible debris did not restore demand");
        pass("Fresh untouched debris restores target eligibility and demand; no persistent global poisoning.");
        CampaignFleetAPI hostile = world.fleet(true,4001);
        world.fleets.add(hostile);
        require(VultureFleetManager.getClosestScavengable(new Vector2f(),world.api,null)==null,"Nex hostile exclusion failed");
        require(find.invoke(null,world.api,refugee)==second,"Refugee target finder unexpectedly applies Nex hostility gate");
        world.fleets.remove(hostile);
        pass("Hostile proximity rejects Nex targets but not the Refugee target finder (fleet AI danger handling is separate).");
        CampaignTerrainAPI third = world.debris(1300,8000);
        collect.invoke(null,second,refugee,new Cargo().api);
        require(VultureFleetManager.getClosestScavengable(new Vector2f(),world.api,null)==third,"Collecting one field blocks unrelated field");
        pass("Collecting one field preserves another field's Nex eligibility.");
        ((DebrisFieldTerrainPlugin)third.getPlugin()).params.baseSalvageXP = 1100;
        world.memory.advance(2.01f);
        require(VultureFleetManager.getClosestScavengable(new Vector2f(),world.api,null)==third,
                "Below-threshold debris should remain a valid individual target");
        require(manager.demand(world.api)==0,"Below-threshold salvage should produce zero demand without raids");
        pass("An eligible field with score 1100 produces zero salvage-driven demand even on an uncached check.");
        System.out.println("Completed shared-code boundary tests. Full fleet spawning and campaign scheduling were not simulated.");
    }
}
