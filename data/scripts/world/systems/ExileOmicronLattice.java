package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.exiles.OmicronDirector;
import data.scripts.exiles.OmicronDirectorConfig;
import data.scripts.exiles.OmicronStationBuilder;
import data.scripts.exiles.OmicronWarCapacity;

/**
 * Lattice — the UNDER-CONSTRUCTION Omicron system (spread build 2026-07-06; war-capacity
 * node {@code omicron_lattice}, {@code uc:true}). Omicron is SPREADING: this node starts as
 * a lone UC-Nexus and matures through Director stages on its construction clock —
 * its Mining station spawns from the placement spec written here, the node begins sourcing
 * raids late in construction, and the damaged station becomes a full fourth Nexus at the
 * end of its independent construction clock.
 *
 * <p>Deliberately the CLOSEST cluster system to the core — the machine is growing toward
 * the sector. Killing the UC-Nexus freezes only Lattice construction and decapitates this
 * node; faction readiness and combat maturity continue from the surviving network.
 * No Sensor/Comm arrays: the system is young.
 */
public class ExileOmicronLattice {

    public static final String OMICRON_FACTION = "exileomicron";
    public static final String NODE_ID = "omicron_lattice";

    // The UC-Nexus is the $damagedStation 0.5-strength battlestation (Battlestation-tier
    // via its lighter defense Ordo; asset model §2b).
    private static final String STATION_DAMAGED = "remnant_station2_Damaged";
    private static final String MINING_STATION_VARIANT = "station1_hightech_Standard";

    public StarSystemAPI generate(SectorAPI sector, float locX, float locY) {
        StarSystemAPI system = sector.createStarSystem("Lattice");

        system.setBackgroundTextureFilename("graphics/backgrounds/background6.jpg");

        PlanetAPI star = system.initStar(
                "exilelattice_star",
                "star_red_dwarf",
                450f,
                250f
        );

        system.setLightColor(new Color(255, 185, 155));
        // Core-facing edge of the far-west Omicron cluster (Prime at -36500, -2500):
        // the under-construction system reaches TOWARD the sector.
        system.getLocation().set(locX, locY);

        // Fortress tag loadout (EXILES_SYSTEM_TAG_INDEX §3) — required even (especially)
        // on this quiet system: untagged far-west systems wreck the core-extent box.
        system.addTag(Tags.THEME_REMNANT);
        system.addTag(Tags.THEME_REMNANT_MAIN);
        system.addTag(Tags.THEME_UNSAFE);
        system.addTag(Tags.THEME_SPECIAL);
        system.addTag(Tags.DO_NOT_RESPAWN_PLAYER_IN);

        HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(hyperTerrain);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 1000f, 0, 360f);

        // Barren future mining anchor
        PlanetAPI seed = system.addPlanet(
                "exilelattice_seed",
                star,
                "Seed",
                "barren",
                90f,
                105f,
                2800f,
                120f
        );

        // Ice giant — future fuel anchor (volatiles per resource rules)
        PlanetAPI colossus = system.addPlanet(
                "exilelattice_colossus",
                star,
                "Colossus",
                "ice_giant",
                250f,
                300f,
                4800f,
                260f
        );

        // Frozen outer world
        PlanetAPI drift = system.addPlanet(
                "exilelattice_drift",
                star,
                "Drift",
                "frozen",
                330f,
                115f,
                6800f,
                420f
        );

        // Survey-only resource conditions (representational, no economy).
        seed.getMarket().addCondition(Conditions.ORE_MODERATE);          // Seed: ore for the future Mining Station
        colossus.getMarket().addCondition(Conditions.VOLATILES_DIFFUSE); // young-system resource trace
        drift.getMarket().addCondition(Conditions.VOLATILES_TRACE);      // icy outer, light volatiles

        // === The under-construction Nexus. Lattice's independent 540-day construction clock
        // advances only while this station survives. ===
        OmicronWarCapacity wc = OmicronWarCapacity.get();

        CampaignFleetAPI ucNexus = OmicronStationBuilder.build(
                system, star, STATION_DAMAGED, "Nexus (Under Construction)",
                180f, 3800f, 200f, true, "lattice_ucnexus"
        );
        // Both tags: NEXUS_TAG makes it this node's kill-switch/fleet-engine (findNexus);
        // the ucnexus asset tag exposes its construction state (scan + quest override flag).
        // OmicronDefenseFleetManager.ensureManagers checks the ucnexus tag FIRST so this
        // dual-tagged station gets the lighter Battlestation-tier guard, not the Nexus tier.
        ucNexus.addTag(OmicronDirector.NEXUS_TAG);
        ucNexus.addTag(OmicronDirectorConfig.ASSET_TAG_UCNEXUS);
        OmicronStationBuilder.writeSpec(NODE_ID, "lattice_ucnexus", star, STATION_DAMAGED,
                "Nexus (Under Construction)", 180f, 3800f, 200f, true);
        wc.setBuilt(NODE_ID, "lattice_ucnexus", true);
        OmicronStationBuilder.writeSpec(NODE_ID, "completed_nexus", star,
                "remnant_station2_Standard", "Nexus", 180f, 3800f, 200f, false);

        // Future stations: placement SPECS ONLY — the Director spawns these when growth
        // crosses their stage thresholds (advanceGrowthStages). NOT marked built.
        OmicronStationBuilder.writeSpec(NODE_ID, "lattice_mining_1", seed, MINING_STATION_VARIANT,
                "Mining Station", 45f, 380f, 50f, true);

        // Single jump point near the UC-Nexus; the fringe point comes from autogen.
        JumpPointAPI innerJump = Global.getFactory().createJumpPoint(
                "exilelattice_inner_jump",
                "Lattice Jump-point"
        );
        OrbitAPI innerOrbit = Global.getFactory().createCircularOrbit(star, 30f, 4200f, 210f);
        innerJump.setOrbit(innerOrbit);
        innerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(innerJump);

        system.autogenerateHyperspaceJumpPoints(false, false);
        return system;
    }
}
