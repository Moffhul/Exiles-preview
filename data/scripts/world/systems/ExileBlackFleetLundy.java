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
 * Lundy -- the Black Fleet's forward supply anchor (built 2026-07-07). Sits between Sallee
 * and the Midan systems. PLACEHOLDER coordinates [+19500, +5500] pending Sean's map review.
 *
 * Naming: Lundy island, which Jan Janszoon ("Murat Reis") occupied and raided from for five
 * years; Shutter is its infamous rock. AUTHOR MAY VETO.
 *
 * Same anatomy as Sallee minus the court: low-tech Battlestation + Mysterious Stockpiles on
 * a size-3 market. If Sallee falls first, the lifecycle script (M5) relocates Mourad here.
 * Both anchors dead = Black Fleet quality collapses + temp-base spigot closes.
 */
public class ExileBlackFleetLundy {

    public static final String BF_FACTION = "exileblackfleet";
    public static final String STATION_ID = "exilebf_lundy_station";

    public StarSystemAPI generate(SectorAPI sector, float locX, float locY) {
        StarSystemAPI system = sector.createStarSystem("Lundy");
        system.getLocation().set(locX, locY);

        system.setBackgroundTextureFilename("graphics/backgrounds/background5.jpg");

        PlanetAPI star = system.initStar(
                "exilebf_lundy_star",
                "star_red_dwarf",
                500f,
                350f
        );
        system.setLightColor(new Color(255, 180, 160));

        HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(hyperTerrain);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 900f, 0, 360f);

        // A cold granite lump -- worthless, defensible, home
        PlanetAPI granite = system.addPlanet(
                "exilebf_lundy_granite",
                star,
                "Granite",
                "barren",
                60f,
                90f,
                2600f,
                110f
        );

        // Frozen outer rock
        PlanetAPI shutter = system.addPlanet(
                "exilebf_lundy_shutter",
                star,
                "Shutter",
                "frozen",
                200f,
                120f,
                4600f,
                230f
        );

        system.addAsteroidBelt(star, 100, 3500f, 450f, 140f, 200f, Terrain.ASTEROID_BELT, "Lundy Shoals");
        system.addRingBand(star, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 3500f, 190f);

        // --- The anchor: Lundy Hold ------------------------------------------------
        SectorEntityToken station = system.addCustomEntity(
                STATION_ID,
                "Lundy Hold",
                "station_lowtech1",
                BF_FACTION
        );
        station.setCircularOrbitPointingDown(granite, 30f, 320f, 35f);
        station.setInteractionImage("illustrations", "pirate_station");

        // MARKET: declared in data/campaign/econ/lundy.json (vanilla-style custom faction
        // convention, ratified 2026-07-07 — structure in Java, market in econ JSON).

        // Makeshift sensor array (playtest fix 2026-07-07: eyes for the patrols)
        SectorEntityToken sensor = system.addCustomEntity(
                "exilebf_lundy_sensor",
                "Lundy Sensor Array",
                "sensor_array_makeshift",
                BF_FACTION
        );
        sensor.setCircularOrbitPointingDown(star, 320f, 5400f, 240f);

        // Jump point near the hold
        JumpPointAPI jump = Global.getFactory().createJumpPoint(
                "exilebf_lundy_jump",
                "Lundy Jump-point"
        );
        OrbitAPI jumpOrbit = Global.getFactory().createCircularOrbit(star, 280f, 3900f, 200f);
        jump.setOrbit(jumpOrbit);
        jump.setRelatedPlanet(granite);
        jump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(jump);

        system.autogenerateHyperspaceJumpPoints(false, false);
        return system;
    }
}
