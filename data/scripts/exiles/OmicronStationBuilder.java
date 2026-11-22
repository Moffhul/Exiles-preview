package data.scripts.exiles;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantThemeGenerator;
import com.fs.starfarer.api.util.Misc;

/**
 * Shared builder for Omicron's station-mode battlestation fleets (extracted from
 * ExileOmicron0 for the spread build) — a defended, combat-destroyable presence with no
 * market (setMarket deliberately NOT called; marketless decision in
 * EXILES_OMICRON_FACTION_ARCHITECTURE.md).
 *
 * <p>Placement SPECS: system generators register each station's placement as a persisted
 * string ({@code $exiles_omicron_station_spec_<nodeId>_<assetKey>}) so the Director can
 * respawn a destroyed station later (the ratified 120d rebuild, war-capacity-design.md #8)
 * and stage-spawn the UC system's stations as it matures — without hardcoding placement in
 * Director code. Format: {@code focusEntityId|variantId|name|angle|orbitRadius|orbitDays|damaged}.
 */
public class OmicronStationBuilder {

    public static final String OMICRON_FACTION = "exileomicron";

    /** Persistent-data key prefix for placement specs (see class doc). */
    public static final String SPEC_PREFIX = "$exiles_omicron_station_spec_";

    /**
     * Builds a station-mode battlestation fleet for Omicron (mirrors
     * RemnantThemeGenerator.addBattlestations).
     *
     * @param damaged true for the $damagedStation 0.5-strength node (skips the elite
     *                commander-skill buff) — the UC-Nexus and under-construction stations.
     */
    /**
     * Asset-aware station build. Nexuses use Grade III tactical command processes while
     * Mining and Fuel Stations use ordinary Gamma Cores for general-purpose operation.
     */
    public static CampaignFleetAPI build(StarSystemAPI system, SectorEntityToken focus,
                                         String stationVariantId, String name,
                                         float angle, float orbitRadius, float orbitDays,
                                         boolean damaged, String assetKey) {
        Random random = new Random();

        CampaignFleetAPI fleet = FleetFactoryV3.createEmptyFleet(OMICRON_FACTION, FleetTypes.BATTLESTATION, null);

        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, stationVariantId);
        fleet.getFleetData().addFleetMember(member);

        fleet.setName(name);

        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_NO_JUMP, true);
        fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE, true);
        fleet.addTag(Tags.NEUTRINO_HIGH);

        fleet.setStationMode(true);

        // Gives the station the "attack the station" interaction dialog.
        RemnantThemeGenerator.addRemnantStationInteractionConfig(fleet);

        system.addEntity(fleet);

        fleet.clearAbilities();
        fleet.addAbility(Abilities.TRANSPONDER);
        fleet.getAbility(Abilities.TRANSPONDER).activate();
        fleet.getDetectedRangeMod().modifyFlat("gen", 1000f);

        // null AI -> stationary; it does not rove.
        fleet.setAI(null);

        fleet.setCircularOrbitPointingDown(focus, angle, orbitRadius, orbitDays);

        if (damaged) {
            fleet.getMemoryWithoutUpdate().set("$damagedStation", true);
        }

        String coreId = getCoreIdForAsset(assetKey);
        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(coreId);
        PersonAPI commander = plugin.createPerson(coreId, fleet.getFaction().getId(), random);
        fleet.setCommander(commander);
        fleet.getFlagship().setCaptain(commander);

        if (!damaged) {
            RemnantOfficerGeneratorPlugin.integrateAndAdaptCoreForAIFleet(fleet.getFlagship());
            RemnantOfficerGeneratorPlugin.addCommanderSkills(commander, fleet, null, 3, random);
        }

        member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());

        return fleet;
    }

    private static String getCoreIdForAsset(String assetKey) {
        if (assetKey != null && (assetKey.indexOf("mining") >= 0
                || assetKey.indexOf("fuel") >= 0)) {
            return Commodities.GAMMA_CORE;
        }
        if ("nexus_alpha".equals(assetKey)) return Commodities.ALPHA_CORE;
        return ExileOmicronCoreIds.GRADE_III;
    }

    /**
     * Persist a station's placement spec so the Director can (re)spawn it later. Called by
     * the system generators for every rebuildable/stageable station; safe at generation time
     * (the sector and its persistent data exist during onNewGame).
     */
    public static void writeSpec(String nodeId, String assetKey, SectorEntityToken focus,
                                 String stationVariantId, String name,
                                 float angle, float orbitRadius, float orbitDays,
                                 boolean damaged) {
        String spec = focus.getId() + "|" + stationVariantId + "|" + name + "|"
                + angle + "|" + orbitRadius + "|" + orbitDays + "|" + damaged;
        Global.getSector().getPersistentData().put(SPEC_PREFIX + nodeId + "_" + assetKey, spec);
    }

    /**
     * (Re)build a station from its persisted spec. Returns null (with a WARN) if the spec is
     * missing/garbled or its orbit-focus entity no longer resolves — the caller must treat
     * that as "no rebuild possible", never crash.
     */
    public static CampaignFleetAPI buildFromSpec(String nodeId, String assetKey) {
        Object v = Global.getSector().getPersistentData().get(SPEC_PREFIX + nodeId + "_" + assetKey);
        if (!(v instanceof String)) {
            Global.getLogger(OmicronStationBuilder.class).warn(
                    "Omicron station spec missing: " + nodeId + "/" + assetKey);
            return null;
        }
        String[] parts = ((String) v).split("\\|");
        if (parts.length < 7) {
            Global.getLogger(OmicronStationBuilder.class).warn(
                    "Omicron station spec garbled: " + nodeId + "/" + assetKey + " -> " + v);
            return null;
        }
        SectorEntityToken focus = Global.getSector().getEntityById(parts[0]);
        if (focus == null || !(focus.getContainingLocation() instanceof StarSystemAPI)) {
            Global.getLogger(OmicronStationBuilder.class).warn(
                    "Omicron station spec focus not found: " + nodeId + "/" + assetKey
                            + " (focus id " + parts[0] + ")");
            return null;
        }
        StarSystemAPI system = (StarSystemAPI) focus.getContainingLocation();
        try {
            float angle = Float.parseFloat(parts[3]);
            float radius = Float.parseFloat(parts[4]);
            float days = Float.parseFloat(parts[5]);
            boolean damaged = "true".equals(parts[6]);
            return build(system, focus, parts[1], parts[2], angle, radius, days, damaged, assetKey);
        } catch (Exception e) {
            Global.getLogger(OmicronStationBuilder.class).warn(
                    "Omicron station spec rebuild failed: " + nodeId + "/" + assetKey, e);
            return null;
        }
    }
}
