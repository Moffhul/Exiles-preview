package data.scripts.exiles;

import java.util.HashMap;
import java.util.List;
import java.util.Random;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.SourceBasedFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantSeededFleetManager;
import com.fs.starfarer.api.util.Misc;

/**
 * Home-defense gauntlet (asset model §2b): one manager per Omicron facility station, spawning
 * defender fleets sized by the facility's tier. Extends the vanilla
 * {@link SourceBasedFleetManager}, which supplies every ratified verb for free:
 * <ul>
 *   <li><b>Proximity ramp</b> — defenders only exist near the player ({@code thresholdLY});
 *       an empty Omicron system costs nothing.</li>
 *   <li><b>Decaying suppression</b> — each defender killed suppresses one respawn slot for
 *       ~{@code respawnDelay} days, so grinding the guards down opens a raid window.</li>
 *   <li><b>Kill switch</b> — the facility station dying zeroes {@code currMax}; surviving
 *       defenders despawn naturally and the script removes itself (leak-safe).</li>
 * </ul>
 * Deliberately DROPPED from the Remnant template (asset model §3): the {@code totalLost*4}
 * escalation — Omicron replacements must NOT get stronger the more the player kills.
 *
 * <p>Tiers come from director_config.json {@code defense} block via {@link #ensureManagers},
 * which runs on every game load: it attaches managers to tagged stations that lack one
 * (save-compatible) and re-applies config values to managers restored from saves (so tuning
 * edits reach existing games on restart).
 */
public class OmicronDefenseFleetManager extends SourceBasedFleetManager {

    /** One-time sector-memory flag: the "attack fleets come from a Nexus" warning was shown. */
    public static final String NEXUS_WARNING_KEY = "$exiles_omicron_nexus_warning_shown";

    /** Fleet-memory role marker: true = anchored station guard; absent = roaming patrol. */
    public static final String GUARD_ROLE_KEY = "$exiles_omicron_station_guard";
    /** One of a full Nexus's existing guards holds orbit instead of patrolling. */
    public static final String ORBIT_GUARD_KEY = "$exiles_omicron_nexus_orbit_guard";

    protected String tierKey; // "nexus" | "ucnexus" | "mining" | "fuel" | "sensor" | "comm"
    protected int guardCap;   // anchored close-defense fleets; rest of maxFleets = patrols
    protected int fpMin;
    protected int fpMax;
    protected transient boolean warned = false;

    public OmicronDefenseFleetManager(SectorEntityToken source, String tierKey,
                                      OmicronDirectorConfig.DefenseTier tier) {
        // minFleets 0: nothing exists while the player is far away. maxFleets = the whole
        // pool; guardCap says how many of them anchor to the station (the rest roam).
        super(source, tier.thresholdLY, 0, tier.maxFleets + tier.patrolFleets, tier.respawnDelayDays);
        this.tierKey = tierKey;
        // Copy primitives only — the config object is transient and must never enter the save.
        this.guardCap = tier.maxFleets;
        this.fpMin = tier.fpMin;
        this.fpMax = tier.fpMax;
    }

    public String getTierKey() {
        return tierKey;
    }

    /** Superseded array tiers (Sensor/Comm buoys): the docked-campaign-fleet approach was
     *  replaced same-day by dialog-layer defenders (DefenderDataOverride + rules, the Domain
     *  survey-ship pattern, per author) — this only retires managers from saves made in the
     *  brief window when arrays had fleet managers. */
    protected boolean isArrayTier() {
        return "sensor".equals(tierKey) || "comm".equals(tierKey);
    }

    /** Re-apply config on load so tuning edits reach managers persisted in old saves. */
    public void applyTier(OmicronDirectorConfig.DefenseTier tier) {
        this.thresholdLY = tier.thresholdLY;
        this.minFleets = 0;
        this.maxFleets = tier.maxFleets + tier.patrolFleets;
        this.guardCap = tier.maxFleets;
        this.respawnDelay = tier.respawnDelayDays;
        this.fpMin = tier.fpMin;
        this.fpMax = tier.fpMax;
        // Adopt one existing guard on load; do not add fleets or bypass loss suppression.
        if ("nexus".equals(tierKey) && guardCap > 0 && !hasOrbitGuard()) {
            for (int i = 0; i < fleets.size(); i++) {
                CampaignFleetAPI f = (CampaignFleetAPI) fleets.get(i);
                if (f != null && f.isAlive() && f.getBattle() == null
                        && f.getMemoryWithoutUpdate().getBoolean(GUARD_ROLE_KEY)) {
                    configureOrbitGuard(f);
                    break;
                }
            }
        }
    }

    @Override
    protected CampaignFleetAPI spawnFleet() {
        if (source == null) return null;

        Random random = new Random();
        int fp = fpMin;
        if (fpMax > fpMin) fp += random.nextInt(fpMax - fpMin + 1);

        String type = FleetTypes.PATROL_LARGE;
        if (fp <= 45) type = FleetTypes.PATROL_SMALL;
        else if (fp <= 90) type = FleetTypes.PATROL_MEDIUM;

        FleetParamsV3 params = new FleetParamsV3(
                source.getMarket(),               // null for station fleets -> factory fake market
                source.getLocationInHyperspace(),
                OmicronDirector.OMICRON_FACTION,
                1f,                               // quality override (no real market to derive from)
                type,
                fp,                               // all combat
                0f, 0f, 0f, 0f, 0f,               // freighter / tanker / transport / liner / utility
                0f                                // quality mod
        );
        params.random = random;
        params.ignoreMarketFleetSizeMult = Boolean.TRUE;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) return null;

        // Guard/patrol split (author 2026-07-05: close defense is the priority, but Omicron
        // shouldn't leave jump points unguarded either — and a patient player watching the
        // patrol routes earns a window to hit a station with only its anchored guards home).
        boolean guardRole = countGuards() < guardCap;

        // The campaign tooltip automatically prepends the faction display name.
        fleet.setName(guardRole ? "Defense Pattern" : "Patrol Pattern");
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
        // Defenders, NOT raiders: no RAIDER flag / RAIDER_TAG — keeps them out of the raid
        // bounty listener and the ground-raid AI paths.

        // Machine classification: an unidentified contact (transponder off) is a threat,
        // even though Omicron is NEUTRAL to the identified player. Per-fleet flag — faction
        // relations untouched (playtest feedback 2026-07-05).
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, true);

        LocationAPI location = source.getContainingLocation();
        location.addEntity(fleet);
        // Machine-fleet properties: patrol flags, no-jump, hold-vs-stronger, remnant-style
        // interaction dialog, salvage seed. Faction-agnostic vanilla helper.
        RemnantSeededFleetManager.initRemnantFleetProperties(random, fleet, false);
        fleet.setLocation(source.getLocation().x, source.getLocation().y);
        fleet.setFacing(random.nextFloat() * 360f);

        if (guardRole) {
            // Anchored: a permanent LOCAL PATROL keeps the close defense AT the facility but
            // visibly moving (playtest 2026-07-06: ORBIT_AGGRESSIVE read as "just sitting
            // there" at the smaller stations). Never expires; despawn is the manager's job.
            fleet.getMemoryWithoutUpdate().set(GUARD_ROLE_KEY, true);
            if ("nexus".equals(tierKey) && !hasOrbitGuard()) {
                configureOrbitGuard(fleet);
            } else {
                fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, source, 1000000f,
                        "guarding " + source.getName());
            }
        } else {
            // Roamer: vanilla-style patrol AI with an Omicron target picker — jump points +
            // the Sensor/Comm arrays, IGNORING battle wreckage (the vanilla picker triple-
            // weights neutrino-high salvage, so patrols clustered around wrecks).
            fleet.addScript(new OmicronPatrolAssignmentAI(fleet,
                    (StarSystemAPI) source.getContainingLocation(), source));
        }
        fleet.getMemoryWithoutUpdate().set("$sourceId", source.getId());

        return fleet;
    }

    private boolean hasOrbitGuard() {
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI f = (CampaignFleetAPI) fleets.get(i);
            if (f != null && f.isAlive()
                    && f.getMemoryWithoutUpdate().getBoolean(ORBIT_GUARD_KEY)) return true;
        }
        return false;
    }

    private void configureOrbitGuard(CampaignFleetAPI fleet) {
        if (source == null || !source.isAlive()) return;
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.set(ORBIT_GUARD_KEY, true);
        mem.unset(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE);
        // $cfai_makeNonAggressive is read NOWHERE in the engine (jar string scan, re-checked
        // 2026-10-06). It is kept as an Exiles MARKER: the refugee salvage threat screens skip
        // fleets carrying it. The real dormancy comes from ORBIT_PASSIVE + doNotGetSidetracked.
        mem.set(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE, true);
        mem.set(MemFlags.MEMORY_KEY_MAKE_HOLD_VS_STRONGER, true);
        mem.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);
        // Do not use ignore-other-fleets, non-hostile, or null AI: these prevent battle joining.
        fleet.clearAssignments();
        fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, source, 1000000f,
                "maintaining Nexus defense orbit");
    }

    /** Live anchored guards among this manager's fleets. */
    protected int countGuards() {
        int n = 0;
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI f = (CampaignFleetAPI) fleets.get(i);
            if (f != null && f.isAlive()
                    && f.getMemoryWithoutUpdate().getBoolean(GUARD_ROLE_KEY)) n++;
        }
        return n;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        // One-time diegetic warning when the player first closes on a Nexus: the raids they
        // have been seeing sector-wide come FROM here — and anything mid-assembly will join
        // the defense (author request 2026-07-05). Cheap: transient bool short-circuits.
        if (!warned && "nexus".equals(tierKey) && source != null) {
            MemoryAPI mem = Global.getSector().getMemoryWithoutUpdate();
            if (mem.getBoolean(NEXUS_WARNING_KEY)) {
                warned = true;
                return;
            }
            CampaignFleetAPI player = Global.getSector().getPlayerFleet();
            if (player == null || player.getContainingLocation() != source.getContainingLocation()) return;
            if (Misc.getDistance(player, source) > 2500f) return;
            warned = true;
            mem.set(NEXUS_WARNING_KEY, true);
            Global.getSector().getCampaignUI().addMessage(
                    "Sensor correlation: outbound drive signatures from this Nexus match the"
                            + " Omicron attack fleets seen across the sector. This is their origin"
                            + " — and any fleets it is currently assembling will join its defense.",
                    Color.ORANGE);
        }
    }

    /**
     * OMI-11 (2026-10-06): retire every fleet this manager spawned. Used when Lattice completion
     * replaces the UC-Nexus: the old guards and patrols hold million-day orders on an entity that
     * is about to be removed, and the replacement manager spawns its own at the completed Nexus.
     * Fades rather than vanishes, in case the player is watching. Returns the count retired.
     */
    public int retireFleets() {
        int n = 0;
        List copy = new java.util.ArrayList(fleets);
        fleets.clear();
        for (int i = 0; i < copy.size(); i++) {
            CampaignFleetAPI f = (CampaignFleetAPI) copy.get(i);
            if (f == null || !f.isAlive()) continue;
            f.clearAssignments();
            Misc.fadeAndExpire(f);
            n++;
        }
        return n;
    }

    /**
     * Load-time wiring (called from {@code ExilesModPlugin.onGameLoad}): scan every star
     * system for live, tagged Omicron facility stations; attach a manager to any that lack
     * one and re-apply the config tier to those restored from the save. A tier with
     * {@code maxFleets <= 0} is disabled (no manager added; existing ones drain to zero via
     * the applied cap).
     */
    public static void ensureManagers(OmicronDirectorConfig config) {
        if (Global.getSector() == null || config == null) return;

        HashMap bySourceId = new HashMap(); // source entity id (String) -> manager
        List scripts = Global.getSector().getScripts();
        for (int i = 0; i < scripts.size(); i++) {
            Object s = scripts.get(i);
            if (s instanceof OmicronDefenseFleetManager) {
                OmicronDefenseFleetManager m = (OmicronDefenseFleetManager) s;
                // Retire superseded array-fleet managers restored from old saves (arrays now
                // use dialog-layer defenders instead — see ExilesModPlugin).
                if (m.isArrayTier()) {
                    m.maxFleets = 0;
                    m.minFleets = 0;
                    m.setDone(true);
                    continue;
                }
                if (m.getSource() != null) {
                    bySourceId.put(m.getSource().getId(), m);
                }
            }
        }

        int added = 0;
        List systems = Global.getSector().getStarSystems();
        for (int i = 0; i < systems.size(); i++) {
            StarSystemAPI system = (StarSystemAPI) systems.get(i);
            List fleets = system.getFleets();
            for (int j = 0; j < fleets.size(); j++) {
                CampaignFleetAPI f = (CampaignFleetAPI) fleets.get(j);
                if (f == null || !f.isAlive() || !f.isStationMode()) continue;

                // UCNEXUS checked BEFORE NEXUS: the Lattice UC-Nexus carries BOTH tags
                // (node fleet-engine + escalation asset) and must get the lighter
                // Battlestation-tier guard, not the full Nexus gauntlet (spread build).
                String tierKey = null;
                if (f.hasTag(OmicronDirectorConfig.ASSET_TAG_UCNEXUS)) tierKey = "ucnexus";
                else if (f.hasTag(OmicronDirector.NEXUS_TAG)) tierKey = "nexus";
                else if (f.hasTag(OmicronDirectorConfig.ASSET_TAG_MINING)) tierKey = "mining";
                else if (f.hasTag(OmicronDirectorConfig.ASSET_TAG_FUEL)) tierKey = "fuel";
                if (tierKey == null) continue;

                OmicronDirectorConfig.DefenseTier tier = config.defenseTier(tierKey);
                if (tier == null) continue;

                OmicronDefenseFleetManager existing =
                        (OmicronDefenseFleetManager) bySourceId.get(f.getId());
                if (existing != null) {
                    existing.applyTier(tier);
                    continue;
                }
                if (tier.maxFleets <= 0 && tier.patrolFleets <= 0) continue; // tier disabled

                Global.getSector().addScript(new OmicronDefenseFleetManager(f, tierKey, tier));
                added++;
            }
        }

        if (added > 0) {
            Global.getLogger(OmicronDefenseFleetManager.class).info(
                    "Omicron home defense: attached " + added + " defense manager(s)");
        }
    }

}
