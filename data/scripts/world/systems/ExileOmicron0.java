package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.exiles.OmicronDirector;
import data.scripts.exiles.OmicronDirectorConfig;
import data.scripts.exiles.OmicronStationBuilder;
import data.scripts.exiles.OmicronWarCapacity;

/**
 * Omicron Prime — the original machine home system (war-capacity node {@code omicron_prime}).
 * SPREAD BUILD (2026-07-06): relocated to the far west with the rest of the Omicron cluster
 * (Cipher, Vigil, and the under-construction Lattice); its in-system UC-Nexus moved to the
 * dedicated Lattice system, per war-capacity-design.md §2.
 */
public class ExileOmicron0 {

    public static final String OMICRON_FACTION = "exileomicron";

    /** War-capacity node id (config nodes row + station placement specs). */
    public static final String NODE_ID = "omicron_prime";

    // Station-mode battlestation ship variant (vanilla Remnant station hull) for the Nexus.
    private static final String STATION_STANDARD = "remnant_station2_Standard";

    // Orbital-tier logistics stations (Mining / Fuel): vanilla High Tech Orbital Station,
    // ~half the Nexus's strength. Placeholder look; core to be reskinned to an industrial /
    // fuel-storage appearance later. (remnant_station1 was tried but its modules misalign.)
    // Vanilla Orbital-tier hull for BOTH map and combat — RULED 2026-07-06: stations stay
    // vanilla station-mode fleets (visual-split slice dropped; real fleets keep the Director
    // kill-switch wiring and the 120d rebuild simple). The author's custom station sprites
    // (graphics/stations/) are preserved for a possible revisit at the spread build or the
    // release-jar milestone (colony-pattern split with a custom rule command becomes legal then).
    private static final String MINING_STATION_VARIANT = "station1_hightech_Standard";
    private static final String FUEL_STATION_VARIANT = "station1_hightech_Standard";

    public StarSystemAPI generate(SectorAPI sector, float locX, float locY) {
        StarSystemAPI system = sector.createStarSystem("Omicron");

        system.setBackgroundTextureFilename("graphics/backgrounds/background5.jpg");

        PlanetAPI omicron_star = system.initStar(
                "exileomicron_star",
                "star_blue_giant",
                1100f,
                700f
        );

        system.setLightColor(new Color(165, 210, 255));
        // Far west, WELL beyond the vanilla core's western edge (Tyle sits at -19100): the
        // Omicron cluster is Prime + Cipher + Vigil + the under-construction Lattice.
        // (Pushed -10000 further west 2026-07-06 after Sean's map check.)
        system.getLocation().set(locX, locY);

        // Fortress-system tag loadout (docs/EXILES_SYSTEM_TAG_INDEX.md §3): remnant theme
        // trio + explicit story/base veto + never respawn a defeated player in here. A
        // far-west system left untagged would auto-THEME_CORE and stretch the core-extent
        // box across the whole map.
        system.addTag(Tags.THEME_REMNANT_RESURGENT);
        system.addTag(Tags.THEME_REMNANT_MAIN);
        system.addTag(Tags.THEME_UNSAFE);
        system.addTag(Tags.THEME_SPECIAL);
        system.addTag(Tags.DO_NOT_RESPAWN_PLAYER_IN);

        HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(hyperTerrain);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 1000f, 0, 360f);

        // Inner irradiated rock
        PlanetAPI p1 = system.addPlanet(
                "exileomicron_glass",
                omicron_star,
                "Glass",
                "irradiated",
                30f,
                95f,
                2600f,
                100f
        );

        // Barren observation world
        PlanetAPI p2 = system.addPlanet(
                "exileomicron_null",
                omicron_star,
                "Null",
                "barren",
                130f,
                120f,
                4300f,
                180f
        );

        // Gas giant — major visual anchor
        PlanetAPI p3 = system.addPlanet(
                "exileomicron_leviathan",
                omicron_star,
                "Leviathan",
                "gas_giant",
                230f,
                340f,
                7200f,
                360f
        );

        // Frozen outer world
        PlanetAPI p4 = system.addPlanet(
                "exileomicron_silence",
                omicron_star,
                "Silence",
                "frozen",
                310f,
                130f,
                9700f,
                520f
        );

        // Resource conditions (survey-only / not-in-economy market — the magec.java vanilla pattern).
        // Representational only (no economy): they make the supply web read coherently when surveyed
        // and mark where Omicron's extractor/refinery stations sit.
        p2.getMarket().addCondition(Conditions.ORE_MODERATE);        // Null: ore for the Mining Station
        p2.getMarket().addCondition(Conditions.RARE_ORE_SPARSE);
        p1.getMarket().addCondition(Conditions.RARE_ORE_MODERATE);   // Glass: irradiated, transplutonic-rich (unexploited)
        p3.getMarket().addCondition(Conditions.VOLATILES_DIFFUSE);   // Leviathan: volatiles for the Fuel Station
        p4.getMarket().addCondition(Conditions.VOLATILES_TRACE);     // Silence: icy outer, light volatiles

        // Major asteroid field / debris region
        system.addAsteroidBelt(omicron_star, 300, 5900f, 900f, 160f, 300f, Terrain.ASTEROID_BELT, "Omicron Debris Field");
        system.addRingBand(omicron_star, "misc", "rings_asteroids0", 256f, 3, Color.white, 256f, 5700f, 260f);
        system.addRingBand(omicron_star, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 6100f, 280f);
        system.addRingBand(omicron_star, "misc", "rings_dust0", 256f, 2, Color.gray, 256f, 6300f, 300f);

        // === Strategic assets (see docs/EXILES_OMICRON_ASSET_MODEL.md) ===
        // Marketless faction footprint: the Nexus (fleet engine + command) plus a destroyable
        // logistics chain. The stations are station-mode fleets built via OmicronStationBuilder,
        // which also persists each station's placement spec so the Director can rebuild a
        // destroyed Mining/Fuel station 120 days later while the Nexus lives (ruling 2026-07-06).
        // The UC-Nexus that used to sit here lives in the Lattice system since the spread build.
        OmicronWarCapacity wc = OmicronWarCapacity.get();

        // Omicron Nexus — the fleet engine + command, the apex defended target.
        CampaignFleetAPI nexus = OmicronStationBuilder.build(
                system, omicron_star, STATION_STANDARD, "Nexus",
                70f, 5900f, 260f, false, "nexus_alpha"
        );
        // Stable handle for the Director (the node's fleet engine): resolved by tag each tick.
        nexus.addTag(OmicronDirector.NEXUS_TAG);
        OmicronStationBuilder.writeSpec(NODE_ID, "nexus", omicron_star, STATION_STANDARD,
                "Nexus", 70f, 5900f, 260f, false);
        wc.setBuilt(NODE_ID, "nexus", true);

        // Mining Station — mines + refines Ore/Rare Ore at Null; contributes network strength.
        CampaignFleetAPI miningStation = OmicronStationBuilder.build(
                system, p2, MINING_STATION_VARIANT, "Mining Station",
                30f, 420f, 60f, false, "prime_mining_1"
        );
        // Counted-network asset tag: destroying it lowers Omicron's effective Mining total.
        miningStation.addTag(OmicronDirectorConfig.ASSET_TAG_MINING);
        miningStation.addTag(OmicronDirectorConfig.ASSET_TAG_PRIME_MINING_1);
        OmicronStationBuilder.writeSpec(NODE_ID, "prime_mining_1", p2, MINING_STATION_VARIANT,
                "Mining Station", 30f, 420f, 60f, false);
        wc.setBuilt(NODE_ID, "prime_mining_1", true);

        CampaignFleetAPI secondMiningStation = OmicronStationBuilder.build(
                system, p1, MINING_STATION_VARIANT, "Mining Station",
                210f, 360f, 45f, false, "prime_mining_2"
        );
        secondMiningStation.addTag(OmicronDirectorConfig.ASSET_TAG_MINING);
        secondMiningStation.addTag(OmicronDirectorConfig.ASSET_TAG_PRIME_MINING_2);
        OmicronStationBuilder.writeSpec(NODE_ID, "prime_mining_2", p1, MINING_STATION_VARIANT,
                "Mining Station", 210f, 360f, 45f, false);
        wc.setBuilt(NODE_ID, "prime_mining_2", true);

        // Fuel Station — mines + refines Volatiles into Fuel at the Leviathan gas giant.
        CampaignFleetAPI fuelStation = OmicronStationBuilder.build(
                system, p3, FUEL_STATION_VARIANT, "Fuel Station",
                200f, 520f, 90f, false, "prime_fuel_1"
        );
        // Counted-network asset tag: destroying it lowers readiness generation.
        fuelStation.addTag(OmicronDirectorConfig.ASSET_TAG_FUEL);
        fuelStation.addTag(OmicronDirectorConfig.ASSET_TAG_PRIME_FUEL_1);
        OmicronStationBuilder.writeSpec(NODE_ID, "prime_fuel_1", p3, FUEL_STATION_VARIANT,
                "Fuel Station", 200f, 520f, 90f, false);
        wc.setBuilt(NODE_ID, "prime_fuel_1", true);

        // Sensor Array — in-system tactical data; governs local detection + the raid-warning window.
        SectorEntityToken sensor = system.addCustomEntity(
                "exileomicron_sensor_array",
                "Omicron Sensor Array",
                "sensor_array",
                OMICRON_FACTION
        );
        sensor.setCircularOrbitPointingDown(omicron_star, 150f, 7600f, 320f);
        // Array tags: dialog-layer drone defenders (DefenderDataOverride + rules gate, the
        // Domain survey-ship pattern). The array is never destroyed; beating the screen
        // unlocks the vanilla buoy interaction for a window. Shared tag drives the rules.
        sensor.addTag(OmicronDirectorConfig.ARRAY_TAG);
        sensor.addTag(OmicronDirectorConfig.ARRAY_TAG_SENSOR);

        // Comm Array — long-range tactical data; the Phase-2 infiltration-quest surface.
        SectorEntityToken comm = system.addCustomEntity(
                "exileomicron_comm_array",
                "Omicron Comm Array",
                "comm_relay",
                OMICRON_FACTION
        );
        comm.setCircularOrbitPointingDown(omicron_star, 300f, 9200f, 400f);
        comm.addTag(OmicronDirectorConfig.ARRAY_TAG);
        comm.addTag(OmicronDirectorConfig.ARRAY_TAG_COMM);

        // Jump point near the debris field / Nexus
        JumpPointAPI innerJump = Global.getFactory().createJumpPoint(
                "exileomicron_inner_jump",
                "Nexus Jump-point"
        );
        OrbitAPI innerOrbit = Global.getFactory().createCircularOrbit(
                omicron_star,
                95f,
                6300f,
                260f
        );
        innerJump.setOrbit(innerOrbit);
        innerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(innerJump);

        // Outer jump point near the gas giant
        JumpPointAPI outerJump = Global.getFactory().createJumpPoint(
                "exileomicron_outer_jump",
                "Leviathan Jump-point"
        );
        OrbitAPI outerOrbit = Global.getFactory().createCircularOrbit(
                omicron_star,
                220f,
                7800f,
                360f
        );
        outerJump.setOrbit(outerOrbit);
        outerJump.setRelatedPlanet(p3);
        outerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(outerJump);

        system.autogenerateHyperspaceJumpPoints(false, false);
        return system;
    }
}
