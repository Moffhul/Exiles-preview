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
 * Cipher — second active Omicron fortress system (spread build 2026-07-06; war-capacity node
 * {@code omicron_cipher}). Its asymmetric role is fuel-processing: one Nexus and two Fuel
 * stations (rebuildable, ruling #8) + Sensor/Comm arrays behind drone screens. Names are
 * draft (author may rename). Volatiles on the gas giant only, per resource rules.
 */
public class ExileOmicronCipher {

    public static final String OMICRON_FACTION = "exileomicron";
    public static final String NODE_ID = "omicron_cipher";

    private static final String STATION_STANDARD = "remnant_station2_Standard";
    private static final String MINING_STATION_VARIANT = "station1_hightech_Standard";
    private static final String FUEL_STATION_VARIANT = "station1_hightech_Standard";

    public StarSystemAPI generate(SectorAPI sector, float locX, float locY) {
        StarSystemAPI system = sector.createStarSystem("Cipher");

        system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");

        PlanetAPI star = system.initStar(
                "exilecipher_star",
                "star_orange",
                750f,
                400f
        );

        system.setLightColor(new Color(255, 220, 180));
        // Far-west Omicron cluster, southwest of Prime (-36500, -2500).
        system.getLocation().set(locX, locY);

        // Fortress tag loadout (EXILES_SYSTEM_TAG_INDEX §3) — never leave a far-west system
        // untagged (auto-THEME_CORE would stretch the core-extent box across the map).
        system.addTag(Tags.THEME_REMNANT);
        system.addTag(Tags.THEME_REMNANT_MAIN);
        system.addTag(Tags.THEME_UNSAFE);
        system.addTag(Tags.THEME_SPECIAL);
        system.addTag(Tags.DO_NOT_RESPAWN_PLAYER_IN);

        HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(hyperTerrain);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 1000f, 0, 360f);

        // Inner scorched rock
        PlanetAPI cinder = system.addPlanet(
                "exilecipher_cinder",
                star,
                "Cinder",
                "irradiated",
                60f,
                90f,
                2200f,
                90f
        );

        // Barren mining anchor
        PlanetAPI rote = system.addPlanet(
                "exilecipher_rote",
                star,
                "Rote",
                "barren",
                170f,
                115f,
                3800f,
                160f
        );

        // Gas giant — fuel anchor
        PlanetAPI behemoth = system.addPlanet(
                "exilecipher_behemoth",
                star,
                "Behemoth",
                "gas_giant",
                300f,
                330f,
                6400f,
                330f
        );

        // Frozen outer world
        PlanetAPI staticP = system.addPlanet(
                "exilecipher_static",
                star,
                "Static",
                "frozen",
                20f,
                120f,
                8800f,
                480f
        );

        // Survey-only resource conditions (magec.java pattern; representational, no economy).
        cinder.getMarket().addCondition(Conditions.RARE_ORE_MODERATE);
        rote.getMarket().addCondition(Conditions.ORE_MODERATE);      // local mineral signature
        rote.getMarket().addCondition(Conditions.RARE_ORE_SPARSE);
        behemoth.getMarket().addCondition(Conditions.VOLATILES_DIFFUSE); // Behemoth: volatiles for the Fuel Station
        staticP.getMarket().addCondition(Conditions.ORE_SPARSE);
        staticP.getMarket().addCondition(Conditions.VOLATILES_TRACE);

        // Asteroid belt around the Nexus orbit
        system.addAsteroidBelt(star, 240, 5100f, 700f, 150f, 280f, Terrain.ASTEROID_BELT, "Cipher Belt");
        system.addRingBand(star, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 5000f, 240f);
        system.addRingBand(star, "misc", "rings_dust0", 256f, 2, Color.gray, 256f, 5300f, 260f);

        // === Strategic assets: fuel-heavy node; specs persisted for the 120d rebuild ===
        OmicronWarCapacity wc = OmicronWarCapacity.get();

        CampaignFleetAPI nexus = OmicronStationBuilder.build(
                system, star, STATION_STANDARD, "Nexus",
                140f, 5100f, 240f, false, "nexus"
        );
        nexus.addTag(OmicronDirector.NEXUS_TAG);
        OmicronStationBuilder.writeSpec(NODE_ID, "nexus", star, STATION_STANDARD,
                "Nexus", 140f, 5100f, 240f, false);
        wc.setBuilt(NODE_ID, "nexus", true);

        CampaignFleetAPI fuelStation = OmicronStationBuilder.build(
                system, behemoth, FUEL_STATION_VARIANT, "Fuel Station",
                255f, 510f, 85f, false, "cipher_fuel_1"
        );
        fuelStation.addTag(OmicronDirectorConfig.ASSET_TAG_FUEL);
        fuelStation.addTag(OmicronDirectorConfig.ASSET_TAG_CIPHER_FUEL_1);
        OmicronStationBuilder.writeSpec(NODE_ID, "cipher_fuel_1", behemoth, FUEL_STATION_VARIANT,
                "Fuel Station", 255f, 510f, 85f, false);
        wc.setBuilt(NODE_ID, "cipher_fuel_1", true);

        CampaignFleetAPI secondFuelStation = OmicronStationBuilder.build(
                system, staticP, FUEL_STATION_VARIANT, "Fuel Station",
                105f, 390f, 70f, false, "cipher_fuel_2"
        );
        secondFuelStation.addTag(OmicronDirectorConfig.ASSET_TAG_FUEL);
        secondFuelStation.addTag(OmicronDirectorConfig.ASSET_TAG_CIPHER_FUEL_2);
        OmicronStationBuilder.writeSpec(NODE_ID, "cipher_fuel_2", staticP, FUEL_STATION_VARIANT,
                "Fuel Station", 105f, 390f, 70f, false);
        wc.setBuilt(NODE_ID, "cipher_fuel_2", true);

        // Sensor + Comm arrays (dialog-layer drone screens via the shared rules tag).
        SectorEntityToken sensor = system.addCustomEntity(
                "exilecipher_sensor_array",
                "Omicron Sensor Array",
                "sensor_array",
                OMICRON_FACTION
        );
        sensor.setCircularOrbitPointingDown(star, 200f, 7200f, 300f);
        sensor.addTag(OmicronDirectorConfig.ARRAY_TAG);
        sensor.addTag(OmicronDirectorConfig.ARRAY_TAG_SENSOR);

        SectorEntityToken comm = system.addCustomEntity(
                "exilecipher_comm_array",
                "Omicron Comm Array",
                "comm_relay",
                OMICRON_FACTION
        );
        comm.setCircularOrbitPointingDown(star, 10f, 8200f, 380f);
        comm.addTag(OmicronDirectorConfig.ARRAY_TAG);
        comm.addTag(OmicronDirectorConfig.ARRAY_TAG_COMM);

        // Jump points: one near the Nexus belt, one near the gas giant.
        JumpPointAPI innerJump = Global.getFactory().createJumpPoint(
                "exilecipher_inner_jump",
                "Cipher Jump-point"
        );
        OrbitAPI innerOrbit = Global.getFactory().createCircularOrbit(star, 320f, 5500f, 240f);
        innerJump.setOrbit(innerOrbit);
        innerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(innerJump);

        JumpPointAPI outerJump = Global.getFactory().createJumpPoint(
                "exilecipher_outer_jump",
                "Behemoth Jump-point"
        );
        OrbitAPI outerOrbit = Global.getFactory().createCircularOrbit(star, 60f, 7000f, 330f);
        outerJump.setOrbit(outerOrbit);
        outerJump.setRelatedPlanet(behemoth);
        outerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(outerJump);

        system.autogenerateHyperspaceJumpPoints(false, false);
        return system;
    }
}
