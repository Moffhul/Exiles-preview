package data.scripts.exiles;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import com.fs.starfarer.api.util.Misc;

/** Saved, bounded battlefield dispatcher. Uses vanilla fleets; no Nex dependency. */
public class ExileRefugeeSalvageExpeditionManager implements EveryFrameScript {
    public static final String FACTION = "exilerefugee";
    public static final String FLEET_FLAG = "$exiles_refugee_salvage_expedition";
    public static final String COOLDOWN = "$exiles_refugee_salvage_cooldown";
    public static final String SYSTEM_KEY = "$exiles_refugee_salvage_system";
    public static final String[] RECIPIENTS = {"corvus_abandoned_station", "mairaath_abandoned_station1"};
    protected List<CampaignFleetAPI> fleets = new ArrayList<CampaignFleetAPI>();
    protected Map<StarSystemAPI, Float> pending = new LinkedHashMap<StarSystemAPI, Float>();
    protected float clock;
    protected float untilCheck;
    protected transient ExileRefugeeSalvageConfig config;
    protected transient Random random = new Random();
    protected transient ExileSalvageDiagnostics diagnostics;
    protected transient Map<StarSystemAPI, String> dangerDeferred = new LinkedHashMap<StarSystemAPI, String>();
    /** REF-3: effective strength of the last expedition dispatched (0 until one is measured this
     *  session); the launch screen's danger floor is this x danger.strengthMult. */
    protected transient float lastExpeditionStrength = 0f;

    public ExileRefugeeSalvageConfig getConfig() {
        if (config == null) config = ExileRefugeeSalvageConfig.load();
        return config;
    }
    protected Object readResolve() {
        config = null;
        random = new Random();
        dangerDeferred = new LinkedHashMap<StarSystemAPI, String>();
        return this;
    }

    public void advance(float amount) {
        if (Global.getSector().isInNewGameAdvance()) return;
        float days = Global.getSector().getClock().convertToDays(amount);
        if (getConfig().diagnostics) {
            if (diagnostics == null) diagnostics = new ExileSalvageDiagnostics();
            diagnostics.advance(days);
        }
        clock += days;
        untilCheck -= days;
        if (untilCheck > 0) return;
        ExileRefugeeSalvageConfig cfg = getConfig();
        untilCheck = cfg.cycleDays;
        for (Iterator<CampaignFleetAPI> it = fleets.iterator(); it.hasNext();) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) it.next();
            if (!fleet.isAlive() || fleet.getContainingLocation() == null
                    || !fleet.getContainingLocation().getFleets().contains(fleet)) it.remove();
        }
        if (!cfg.enabled || cfg.maxActiveExpeditions == 0) {
            pending.clear(); dangerDeferred.clear(); return;
        }
        List<MarketAPI> homes = getRecipients();
        if (homes.isEmpty()) { pending.clear(); dangerDeferred.clear(); return; }
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        List<StarSystemAPI> coreSystems = new ArrayList<StarSystemAPI>();
        WeightedRandomPicker<StarSystemAPI> ready = new WeightedRandomPicker<StarSystemAPI>(random);
        for (StarSystemAPI system : Global.getSector().getStarSystems()) {
            if (!system.hasTag("theme_core")) continue;
            coreSystems.add(system);
            // Keep existing response deadlines while away, without scanning distant salvage.
            if (!withinSpawnRange(system, player, cfg.playerSpawnRangeLY)) continue;
            if (ExileRefugeeSalvageRecovery.findTarget(system, null, cfg) == null) {
                pending.remove(system);
                dangerDeferred.remove(system);
                continue;
            }
            if (!pending.containsKey(system)) pending.put(system, Float.valueOf(clock + cfg.responseMinDays
                    + random.nextFloat() * (cfg.responseMaxDays - cfg.responseMinDays)));
            if (((Float) pending.get(system)).floatValue() > clock) continue;
            if (system.getMemoryWithoutUpdate().contains(COOLDOWN)) continue;
            int count = 0;
            for (CampaignFleetAPI fleet : fleets) {
                if (fleet.getMemoryWithoutUpdate().get(SYSTEM_KEY) == system) count++;
            }
            if (count < cfg.maxPerSystem) ready.add(system);
        }
        pending.keySet().retainAll(coreSystems);
        dangerDeferred.keySet().retainAll(coreSystems);
        while (!ready.isEmpty() && fleets.size() < cfg.maxActiveExpeditions) {
            StarSystemAPI system = (StarSystemAPI) ready.pickAndRemove();
            if (spawn(system, (MarketAPI) homes.get(random.nextInt(homes.size())), cfg)) {
                pending.remove(system);
                system.getMemoryWithoutUpdate().set(COOLDOWN, true, cfg.systemLaunchSpacingDays);
            }
        }
    }

    public static boolean withinSpawnRange(StarSystemAPI system, CampaignFleetAPI player, float rangeLY) {
        if (system == null || player == null || player.isInHyperspaceTransition()) return false;
        return player.getContainingLocation() == system
                || (rangeLY > 0 && Misc.getDistanceLY(player.getLocationInHyperspace(), system.getLocation()) <= rangeLY);
    }

    public static List<MarketAPI> getRecipients() {
        List<MarketAPI> result = new ArrayList<MarketAPI>();
        for (int i = 0; i < RECIPIENTS.length; i++) {
            SectorEntityToken entity = Global.getSector().getEntityById(RECIPIENTS[i]);
            if (entity != null && entity.isAlive() && eligibleRecipient(entity.getMarket())) result.add(entity.getMarket());
        }
        return result;
    }
    public static boolean eligibleRecipient(MarketAPI market) {
        if (market == null || !FACTION.equals(market.getFactionId()) || market.isHidden()
                || !Global.getSector().getEconomy().getMarketsCopy().contains(market)) return false;
        boolean named = false;
        for (int i = 0; i < RECIPIENTS.length; i++) {
            SectorEntityToken entity = Global.getSector().getEntityById(RECIPIENTS[i]);
            if (entity != null && entity.isAlive() && entity.getMarket() == market) named = true;
        }
        return named && functional(market, "exile_salvage_yards")
                && (functional(market, Industries.SPACEPORT) || functional(market, Industries.MEGAPORT));
    }
    protected static boolean functional(MarketAPI market, String id) {
        return market.hasIndustry(id) && market.getIndustry(id).isFunctional();
    }

    protected boolean spawn(StarSystemAPI system, MarketAPI home, ExileRefugeeSalvageConfig cfg) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (!withinSpawnRange(system, player, cfg.playerSpawnRangeLY)) return false;
        Vector2f origin = player.getContainingLocation() == system ? player.getLocation() : new Vector2f();
        SectorEntityToken target = ExileRefugeeSalvageRecovery.findPreferredTarget(system, origin, cfg);
        if (target == null) return false;
        CampaignFleetAPI threat = ExileRefugeeSalvageThreats.findLaunchThreat(
                system, target, lastExpeditionStrength, cfg);
        if (threat != null) {
            // Logged on every CHANGE of blocking fleet, not once per episode: the Isirah read
            // (2026-09-21) found one line hiding three months of deferral. Class + distance are the
            // numbers launchThreatRadius is tuned from.
            if (cfg.diagnostics && !threat.getId().equals(dangerDeferred.get(system))) {
                boolean local = ExileRefugeeSalvageThreats.isLocalDefense(threat);
                Global.getLogger(getClass()).info("Refugee salvage dispatch deferred in "
                        + system.getName() + " by " + threat.getName() + " ["
                        + threat.getFaction().getId() + "]"
                        + " class=" + (local ? "local-defense" : "mobile")
                        + " strength=" + (int) threat.getEffectiveStrength()
                        + " (floor " + (int) ExileRefugeeSalvageThreats.getLaunchDangerFloor(
                                lastExpeditionStrength, cfg)
                        + (lastExpeditionStrength > 0f ? "" : ", unmeasured: raw-FP fallback") + ")"
                        + " distToWreck=" + (int) Misc.getDistance(threat, target)
                        + " (radius " + (int) cfg.launchThreatRadius + ")");
            }
            dangerDeferred.put(system, threat.getId());
            return false;
        }
        if (dangerDeferred.containsKey(system)) {
            dangerDeferred.remove(system);
            if (cfg.diagnostics) Global.getLogger(getClass()).info(
                    "Refugee salvage dispatch danger cleared in " + system.getName());
        }
        FleetParamsV3 params = new FleetParamsV3(home, system.getLocation(), FACTION, null,
                FleetTypes.SCAVENGER_SMALL, cfg.combatFp, 0, 0, 0, 0, 0, -0.2f);
        params.maxShipSize = cfg.maxShipSize;
        params.maxNumShips = Integer.valueOf(cfg.maxShips - 3);
        params.doNotAddShipsBeforePruning = Boolean.TRUE;
        params.ignoreMarketFleetSizeMult = Boolean.TRUE;
        params.withOfficers = true;
        params.maxOfficersToAdd = Integer.valueOf(0);
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null) return false;
        // The faction may gain new role entries later; support hulls are explicit, never rolled twice.
        int escorts = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            String hull = member.getHullSpec().getBaseHullId();
            HullSize size = member.getHullSpec().getHullSize();
            if ("exile_tarsus_2".equals(hull) || "shepherd".equals(hull) || "tarsus".equals(hull)
                    || size == HullSize.CRUISER || size == HullSize.CAPITAL_SHIP || member.isFighterWing()
                    || escorts >= cfg.maxShips - 3) {
                fleet.getFleetData().removeFleetMember(member);
            } else escorts++;
        }
        if (fleet.isEmpty()) fleet.getFleetData().addFleetMember("hound_Standard");
        FleetMemberAPI flagship = (FleetMemberAPI) fleet.getFleetData().getMembersListCopy().get(0);
        fleet.getFleetData().setFlagship(flagship);
        fleet.getFleetData().addFleetMember("exile_tarsus_2_Standard");
        fleet.getFleetData().addFleetMember("shepherd_Frontier");
        fleet.getFleetData().addFleetMember("tarsus_Standard");
        fleet.inflateIfNeeded();
        // Experienced civilian logistics leadership: vanilla burn and capacity bonuses.
        fleet.getCommander().getStats().setSkillLevel(Skills.BULK_TRANSPORT, 1);
        fleet.getFlagship().setCaptain(fleet.getCommander());
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
        }
        fleet.forceSync();
        fleet.setFaction(FACTION, true);
        fleet.setName("Refugee Salvage Expedition");
        fleet.setNoFactionInName(true);
        fleet.setTransponderOn(true);
        fleet.setAIMode(true);
        fleet.getMemoryWithoutUpdate().set(FLEET_FLAG, true);
        fleet.getMemoryWithoutUpdate().set(SYSTEM_KEY, system);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SCAVENGER, true);
        // 0.98.5a GUARD (found in play 2026-11-22). Vanilla's new comm-link row
        // csf_sellStuffOptOther runs "SellToFleet willBuyStuff" on every $isScavenger fleet, and
        // SellToFleet.execute() nulls its fleet when this rate is missing or zero and then
        // dereferences it: a NullPointerException printed in red in the dialogue. Vanilla sets the
        // rate on its own scavengers (RuinsFleetRouteManager), so only a modded scavenger trips it.
        // The rate alone is the guard. $fleetBuysStuff is deliberately NOT set, so willBuyStuff
        // answers false and no "sell cargo" option appears: whether refugee salvagers buy the
        // player's cargo is a design question for Sean, not part of this fix.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_FLEET_BUYS_STUFF_RATE, 0.25f);
        // NO HYPERSPACE CHARTS FOR SALE (Sean's ruling, 2026-11-22). Vanilla's scavBuyHTDataOpt
        // offers "charts of local hyperspace and slipstream routes" on every $isScavenger fleet;
        // poor salvagers who never leave the Core have none to sell. $boughtHTData is the flag
        // that row itself tests (vanilla sets it for 60 days after a purchase and reads it
        // nowhere else), so setting it for good hides the option with no rules.csv override.
        fleet.getMemoryWithoutUpdate().set("$boughtHTData", true);
        // NOT player-gated (TacticalModule decompiled 2026-10-06): a fleet carrying it never
        // chooses ENGAGE just because it is stronger (pickEncounterOption) and lets any fleeing
        // opponent go (pickPursuitOption). The player dialog also reads it.
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        // MEMORY_KEY_MAKE_NON_AGGRESSIVE removed 2026-10-06 (bug sweep REF-1): the engine never
        // reads it, and the only Exiles readers are the refugee threat screens, which never
        // inspect refugee fleets. No behaviour change.
        Vector2f pos = new Vector2f(target.getLocation());
        if (player.getContainingLocation() == system) {
            float range = Math.max(1000, player.getMaxSensorRangeToDetect(fleet)) + 500;
            Vector2f offset = Vector2f.sub(pos, player.getLocation(), null);
            if (offset.length() < range) {
                if (offset.lengthSquared() < 1) offset.set(1, 0);
                offset.normalise(); offset.scale(range);
                pos = Vector2f.add(player.getLocation(), offset, null);
            }
        }
        system.addEntity(fleet);
        fleet.setLocation(pos.x, pos.y);
        fleet.addScript(new ExileRefugeeSalvageAssignmentAI(fleet, home, cfg));
        fleets.add(fleet);
        if (cfg.diagnostics) ExileSalvageDiagnostics.event("DISPATCH", target, fleet);
        lastExpeditionStrength = fleet.getEffectiveStrength(); // REF-3 measurement
        Global.getLogger(getClass()).info("Refugee salvage dispatched to " + system.getName() + "; recipient " + home.getName()
                + "; expeditionStrength=" + (int) lastExpeditionStrength);
        if (cfg.debugMessages) Global.getSector().getCampaignUI().addMessage(
                "Refugee salvage expedition dispatched to " + system.getBaseName() + ".", Color.YELLOW);
        return true;
    }
    public boolean isDone() { return false; }
    public boolean runWhilePaused() { return false; }
}
