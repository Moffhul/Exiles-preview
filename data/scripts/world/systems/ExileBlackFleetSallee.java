package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

/**
 * Sallee -- Admiral Mourad's seat, the Black Fleet's command anchor (built 2026-07-07).
 * Closest Black Fleet system to the Core's eastern fringe. PLACEHOLDER coordinates
 * [+15500, -3000] pending Sean's map review; fold into the system-placement-randomization
 * east-region band when that lands.
 *
 * Naming: Sallee = the Republic of Sale, the historical corsair state Jan Janszoon
 * ("Murat Reis" -- Mourad's namesake) sailed for; Bouregreg is its river. AUTHOR MAY VETO.
 *
 * The anchor station carries the Mysterious Stockpiles industry (the faction's quality
 * engine) and a low-tech Battlestation. Dead-is-dead: the lifecycle script (M5) watches
 * this market; Mourad relocates to Lundy if Sallee falls first.
 */
public class ExileBlackFleetSallee {

    public static final String BF_FACTION = "exileblackfleet";
    public static final String STATION_ID = "exilebf_sallee_station";

    public StarSystemAPI generate(SectorAPI sector, float locX, float locY) {
        StarSystemAPI system = sector.createStarSystem("Sallee");
        system.getLocation().set(locX, locY);

        system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");

        PlanetAPI star = system.initStar(
                "exilebf_sallee_star",
                "star_orange",
                650f,
                400f
        );
        system.setLightColor(new Color(255, 210, 170));

        HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(hyperTerrain);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 900f, 0, 360f);

        // Inner scorched rock -- nothing here worth defending, which is the point
        PlanetAPI qasbah = system.addPlanet(
                "exilebf_sallee_qasbah",
                star,
                "Qasbah",
                "barren",
                40f,
                80f,
                2400f,
                100f
        );

        // Gas giant the anchorage hides behind
        PlanetAPI bouregreg = system.addPlanet(
                "exilebf_sallee_bouregreg",
                star,
                "Bouregreg",
                "gas_giant",
                210f,
                300f,
                5200f,
                280f
        );

        // Debris of a hundred prize hulls picked clean
        system.addAsteroidBelt(star, 140, 3800f, 500f, 150f, 220f, Terrain.ASTEROID_BELT, "Prize Field");
        system.addRingBand(star, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 3800f, 200f);

        // --- The anchor: Sallee Anchorage, Mourad's court -------------------------
        SectorEntityToken station = system.addCustomEntity(
                STATION_ID,
                "Sallee Anchorage",
                "station_lowtech2",
                BF_FACTION
        );
        station.setCircularOrbitPointingDown(bouregreg, 60f, 450f, 40f);
        station.setInteractionImage("illustrations", "pirate_station");

        // MARKET: declared in data/campaign/econ/sallee.json (vanilla-style custom faction
        // convention, ratified 2026-07-07 — structure in Java, market in econ JSON, exactly
        // like Ashen/Midan). Admiral Mourad is created post-econ-load in
        // ExilesModPlugin.applyBlackFleetSetupOnce (the market doesn't exist yet at gen time).

        // Makeshift relay -- stolen, naturally
        SectorEntityToken relay = system.addCustomEntity(
                "exilebf_sallee_relay",
                "Sallee Relay",
                "comm_relay_makeshift",
                BF_FACTION
        );
        relay.setCircularOrbitPointingDown(star, 150f, 3200f, 140f);

        // Makeshift sensor array (playtest fix 2026-07-07: eyes for the patrols)
        SectorEntityToken sensor = system.addCustomEntity(
                "exilebf_sallee_sensor",
                "Sallee Sensor Array",
                "sensor_array_makeshift",
                BF_FACTION
        );
        sensor.setCircularOrbitPointingDown(star, 300f, 6100f, 260f);

        // Jump point near the anchorage
        JumpPointAPI jump = Global.getFactory().createJumpPoint(
                "exilebf_sallee_jump",
                "Sallee Jump-point"
        );
        OrbitAPI jumpOrbit = Global.getFactory().createCircularOrbit(star, 250f, 4400f, 220f);
        jump.setOrbit(jumpOrbit);
        jump.setRelatedPlanet(bouregreg);
        jump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(jump);

        system.autogenerateHyperspaceJumpPoints(false, false);

        // Existence marker: ExileBlackFleetLifecycle only acts on saves where the anchors were
        // actually generated (guards old saves from a false "both anchors dead" defeat).
        Global.getSector().getPersistentData().put("exilebf_anchors_generated", Boolean.TRUE);
        return system;
    }
}
