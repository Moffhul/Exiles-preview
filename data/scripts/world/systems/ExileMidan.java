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

public class ExileMidan {

    public static final String MIDAN_FACTION = "exilemidan";

     public StarSystemAPI generate(SectorAPI sector, float locX, float locY) {
        StarSystemAPI system = sector.createStarSystem("Midan");
 
        system.setBackgroundTextureFilename("graphics/backgrounds/background2.jpg");

        PlanetAPI exilemidan_star = system.initStar(
                "exilemidan_star",
                "star_white",
                180f,
                300f
        );

        system.setLightColor(new Color(205, 210, 215));

        // Map location (2026-07-08): moved out of starmap.json into the generator, matching every
        // other Exiles system. starmap.json only fed the ECONOMY layer (trade distances) at load;
        // actual map placement is this call. Must precede the clearArc below, which reads
        // getLocation() — without it the nebula was being cleared at [0,0] instead of at Midan.
        system.getLocation().set(locX, locY);

		HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
		NebulaEditor editor = new NebulaEditor(hyperTerrain);
		editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 900f, 0, 360f);

        // Mostly empty inner region: red-giant display scale is a visual reference,
        // not a reconstructed engulfment radius. Sparse rocks mark its broken boundary.
        system.addAsteroidBelt(exilemidan_star, 35, 1800f, 180f, 100f, 150f,
                Terrain.ASTEROID_BELT, null);

        // Barren inner exception: warm rock tones, not lava or a cold colony.
        PlanetAPI p1 = system.addPlanet(
                "exilemidan_fawkes",
                exilemidan_star,
                "Fawkes",
                "barren",
                20f,
                90f,
                2400f,
                90f
        );
        p1.getSpec().setPlanetColor(new Color(235, 210, 185));
        p1.applySpecChanges();

        // Cold refuge named for First Haven in Meridian; authored market stays in econ JSON.
        PlanetAPI p2 = system.addPlanet(
                "exilemidan_haven",
                exilemidan_star,
                "Haven",
                "tundra",
                125f,
                160f,
                4000f,
                160f
        );
        p2.getSpec().setPlanetColor(new Color(215, 225, 225));
        p2.getSpec().setAtmosphereColor(new Color(125, 150, 160, 130));
        p2.getSpec().setCloudColor(new Color(180, 190, 195, 210));
        p2.applySpecChanges();

        // Restrained pale-blue ice giant beyond the industrial belt.
        PlanetAPI p3 = system.addPlanet(
                "exilemidan_celsius",
                exilemidan_star,
                "Celsius",
                "ice_giant",
                180f,
                210f,
                7600f,
                300f
        );
        p3.getSpec().setPlanetColor(new Color(190, 215, 230));
        p3.getSpec().setAtmosphereColor(new Color(145, 175, 200, 140));
        p3.applySpecChanges();
        // Only uncolonized worlds get condition-only markets; never reset authored colonies.
        Misc.initConditionMarket(p3);
        p3.getMarket().addCondition("cold");
        p3.getMarket().addCondition("poor_light");

        // Dark outer gas giant. Its broad icy rings end well inside Grave's orbit.
        PlanetAPI p4 = system.addPlanet(
                "exilemidan_gas_giant",
                exilemidan_star,
                "Brahmagupta",
                "gas_giant",
                260f,
                320f,
                10400f,
                440f
        );
        p4.getSpec().setPlanetColor(new Color(150, 160, 175));
        p4.getSpec().setAtmosphereColor(new Color(110, 130, 155, 150));
        p4.applySpecChanges();
        system.addRingBand(p4, "misc", "rings_ice0", 256f, 2,
                new Color(205, 220, 235), 256f, 650f, 40f, Terrain.RING, null);
        system.addRingBand(p4, "misc", "rings_ice0", 256f, 1,
                new Color(185, 205, 225), 256f, 850f, 50f, Terrain.RING, null);

        // Grave is Brahmagupta's rocky moon; climate follows the parent's stellar distance.
        PlanetAPI p5 = system.addPlanet(
                "exilemidan_outer_world",
                p4,
                "Grave",
                "rocky_unstable",
                330f,
                110f,
                1500f,
                70f
        );
        Misc.initConditionMarket(p5);
        p5.getMarket().addCondition("very_cold");
        p5.getMarket().addCondition("poor_light");

        // Prominent industrial belt between Haven and the giants, distinct from inner remnants.
        system.addAsteroidBelt(exilemidan_star, 220, 5600f, 800f, 180f, 260f, Terrain.ASTEROID_BELT, "Midan Belt");
        system.addRingBand(exilemidan_star, "misc", "rings_asteroids0", 256f, 3, Color.white, 256f, 5450f, 220f);
        system.addRingBand(exilemidan_star, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 5650f, 240f);
        system.addRingBand(exilemidan_star, "misc", "rings_asteroids0", 256f, 1, Color.white, 256f, 5850f, 260f);

        // Star-orbiting station embedded in the belt; no planetary climate conditions.
        SectorEntityToken station = system.addCustomEntity(
                "exilemidan_mining_station",
                "Midan Mining Station",
                "station_hightech2",
                MIDAN_FACTION
        );
        station.setCircularOrbitPointingDown(exilemidan_star, 45f, 5600f, 240f);
        station.setInteractionImage("illustrations", "orbital");

        // Stable locations
        SectorEntityToken relay = system.addCustomEntity(
                "midan_comm_relay",
                "Midan Comm Relay",
                "comm_relay",
                MIDAN_FACTION
        );
        relay.setCircularOrbitPointingDown(exilemidan_star, 40f, 4800f, 180f);

        SectorEntityToken sensor = system.addCustomEntity(
                "midan_sensor_array",
                "Midan Sensor Array",
                "sensor_array",
                MIDAN_FACTION
        );
        sensor.setCircularOrbitPointingDown(exilemidan_star, 160f, 8600f, 350f);

        SectorEntityToken nav = system.addCustomEntity(
                "midan_nav_buoy",
                "Midan Nav Buoy",
                "nav_buoy",
                MIDAN_FACTION
        );
        nav.setCircularOrbitPointingDown(exilemidan_star, 320f, 10400f, 440f);

        // Jump point near Haven, with matching period to preserve separation.
        JumpPointAPI innerJump = Global.getFactory().createJumpPoint(
                "midan_inner_jump",
                "Haven Jump-point"
        );
        OrbitAPI innerOrbit = Global.getFactory().createCircularOrbit(
                exilemidan_star,
                105f,
                4300f,
                160f
        );
        innerJump.setOrbit(innerOrbit);
        innerJump.setRelatedPlanet(p2);
        innerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(innerJump);

        // Co-orbital with Brahmagupta, 60 degrees away from its rings and moon.
        JumpPointAPI outerJump = Global.getFactory().createJumpPoint(
                "midan_outer_jump",
                "Moloch Jump-point"
        );
        OrbitAPI outerOrbit = Global.getFactory().createCircularOrbit(
                exilemidan_star,
                200f,
                10400f,
                440f
        );
        outerJump.setOrbit(outerOrbit);
        outerJump.setRelatedPlanet(p4);
        outerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(outerJump);

        system.autogenerateHyperspaceJumpPoints(false, false);
        return system;

      }
}
