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
 * Vigil — third active Omicron fortress system (spread build 2026-07-06; war-capacity node
 * {@code omicron_vigil}). Same facility profile as Omicron Prime. Names are draft (author
 * may rename). Volatiles on the gas giant only, per resource rules.
 */
public class ExileOmicronVigil {

    public static final String OMICRON_FACTION = "exileomicron";
    public static final String NODE_ID = "omicron_vigil";

    private static final String STATION_STANDARD = "remnant_station2_Standard";
    private static final String MINING_STATION_VARIANT = "station1_hightech_Standard";
    private static final String FUEL_STATION_VARIANT = "station1_hightech_Standard";

    public StarSystemAPI generate(SectorAPI sector, float locX, float locY) {
        StarSystemAPI system = sector.createStarSystem("Vigil");

        system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");

        PlanetAPI star = system.initStar(
                "exilevigil_star",
                "star_white",
                850f,
                500f
        );

        system.setLightColor(new Color(245, 250, 255));
        // Far-west Omicron cluster, northwest of Prime (-36500, -2500).
        system.getLocation().set(locX, locY);

        // Fortress tag loadout (EXILES_SYSTEM_TAG_INDEX §3).
        system.addTag(Tags.THEME_REMNANT);
        system.addTag(Tags.THEME_REMNANT_MAIN);
        system.addTag(Tags.THEME_UNSAFE);
        system.addTag(Tags.THEME_SPECIAL);
        system.addTag(Tags.DO_NOT_RESPAWN_PLAYER_IN);

        HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(hyperTerrain);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 1000f, 0, 360f);

        // Inner irradiated rock
        PlanetAPI pyre = system.addPlanet(
                "exilevigil_pyre",
                star,
                "Pyre",
                "irradiated",
                200f,
                100f,
                2400f,
                95f
        );

        // Barren mining anchor
        PlanetAPI mote = system.addPlanet(
                "exilevigil_mote",
                star,
                "Mote",
                "barren",
                340f,
                110f,
                4100f,
                170f
        );

        // Gas giant — fuel anchor
        PlanetAPI goliath = system.addPlanet(
                "exilevigil_goliath",
                star,
                "Goliath",
                "gas_giant",
                80f,
                350f,
                7000f,
                350f
        );

        // Frozen outer world
        PlanetAPI torpor = system.addPlanet(
                "exilevigil_torpor",
                star,
                "Torpor",
                "frozen",
                160f,
                125f,
                9200f,
                500f
        );

        // Survey-only resource conditions (representational, no economy).
        pyre.getMarket().addCondition(Conditions.RARE_ORE_MODERATE);
        mote.getMarket().addCondition(Conditions.ORE_ABUNDANT);      // Mote: rich ore for the Mining Station
        goliath.getMarket().addCondition(Conditions.VOLATILES_DIFFUSE); // Goliath: volatiles for the Fuel Station
        torpor.getMarket().addCondition(Conditions.ORE_SPARSE);

        // Dust rings around the Nexus orbit
        system.addAsteroidBelt(star, 260, 5600f, 750f, 150f, 290f, Terrain.ASTEROID_BELT, "Vigil Shoal");
        system.addRingBand(star, "misc", "rings_asteroids0", 256f, 3, Color.white, 256f, 5500f, 250f);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.gray, 256f, 5800f, 270f);

        // === Strategic assets: balanced support node; specs persisted for the 120d rebuild ===
        OmicronWarCapacity wc = OmicronWarCapacity.get();

        CampaignFleetAPI nexus = OmicronStationBuilder.build(
                system, star, STATION_STANDARD, "Nexus",
                290f, 5600f, 250f, false, "nexus"
        );
        nexus.addTag(OmicronDirector.NEXUS_TAG);
        OmicronStationBuilder.writeSpec(NODE_ID, "nexus", star, STATION_STANDARD,
                "Nexus", 290f, 5600f, 250f, false);
        wc.setBuilt(NODE_ID, "nexus", true);

        CampaignFleetAPI miningStation = OmicronStationBuilder.build(
                system, mote, MINING_STATION_VARIANT, "Mining Station",
                120f, 390f, 50f, false, "vigil_mining_1"
        );
        miningStation.addTag(OmicronDirectorConfig.ASSET_TAG_MINING);
        miningStation.addTag(OmicronDirectorConfig.ASSET_TAG_VIGIL_MINING_1);
        OmicronStationBuilder.writeSpec(NODE_ID, "vigil_mining_1", mote, MINING_STATION_VARIANT,
                "Mining Station", 120f, 390f, 50f, false);
        wc.setBuilt(NODE_ID, "vigil_mining_1", true);

        CampaignFleetAPI fuelStation = OmicronStationBuilder.build(
                system, goliath, FUEL_STATION_VARIANT, "Fuel Station",
                15f, 530f, 95f, false, "vigil_fuel_1"
        );
        fuelStation.addTag(OmicronDirectorConfig.ASSET_TAG_FUEL);
        fuelStation.addTag(OmicronDirectorConfig.ASSET_TAG_VIGIL_FUEL_1);
        OmicronStationBuilder.writeSpec(NODE_ID, "vigil_fuel_1", goliath, FUEL_STATION_VARIANT,
                "Fuel Station", 15f, 530f, 95f, false);
        wc.setBuilt(NODE_ID, "vigil_fuel_1", true);

        // Sensor + Comm arrays (dialog-layer drone screens via the shared rules tag).
        SectorEntityToken sensor = system.addCustomEntity(
                "exilevigil_sensor_array",
                "Omicron Sensor Array",
                "sensor_array",
                OMICRON_FACTION
        );
        sensor.setCircularOrbitPointingDown(star, 45f, 7800f, 320f);
        sensor.addTag(OmicronDirectorConfig.ARRAY_TAG);
        sensor.addTag(OmicronDirectorConfig.ARRAY_TAG_SENSOR);

        SectorEntityToken comm = system.addCustomEntity(
                "exilevigil_comm_array",
                "Omicron Comm Array",
                "comm_relay",
                OMICRON_FACTION
        );
        comm.setCircularOrbitPointingDown(star, 225f, 8600f, 400f);
        comm.addTag(OmicronDirectorConfig.ARRAY_TAG);
        comm.addTag(OmicronDirectorConfig.ARRAY_TAG_COMM);

        // Jump points: one near the Nexus shoal, one near the gas giant.
        JumpPointAPI innerJump = Global.getFactory().createJumpPoint(
                "exilevigil_inner_jump",
                "Vigil Jump-point"
        );
        OrbitAPI innerOrbit = Global.getFactory().createCircularOrbit(star, 110f, 6000f, 250f);
        innerJump.setOrbit(innerOrbit);
        innerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(innerJump);

        JumpPointAPI outerJump = Global.getFactory().createJumpPoint(
                "exilevigil_outer_jump",
                "Goliath Jump-point"
        );
        OrbitAPI outerOrbit = Global.getFactory().createCircularOrbit(star, 190f, 7400f, 350f);
        outerJump.setOrbit(outerOrbit);
        outerJump.setRelatedPlanet(goliath);
        outerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(outerJump);

        system.autogenerateHyperspaceJumpPoints(false, false);
        return system;
    }
}
