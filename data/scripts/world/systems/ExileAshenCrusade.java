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

public class ExileAshenCrusade {

    public static final String ASHEN_FACTION = "exileashencrusade";

    public StarSystemAPI generate(SectorAPI sector, float locX, float locY) {
        StarSystemAPI system = sector.createStarSystem("Providence");
        system.setBackgroundTextureFilename("graphics/backgrounds/background6.jpg");

        PlanetAPI ashen_star = system.initStar(
                "exileashen_star",
                "star_red_giant",
                850f,
                500f
        );

        system.setLightColor(new Color(255, 185, 145));
        system.getLocation().set(locX, locY);

        HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(hyperTerrain);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 850f, 0, 360f);

        // Scorched inner world
        PlanetAPI p1 = system.addPlanet(
                "exileashen_penitent",
                ashen_star,
                "Penitent",
                "barren-bombarded",
                20f,
                125f,
                1800f,
                95f
        );

        // Harsh support colony / prison-mining world
        PlanetAPI p2 = system.addPlanet(
                "exileashen_steadfast",
                ashen_star,
                "Steadfast",
                "toxic",
                120f,
                145f,
                4100f,
                180f
        );
        p2.getSpec().setGlowTexture(Global.getSettings().getSpriteName("hab_glows", "volturn"));
        p2.getSpec().setGlowColor( new Color(255,20,60,255) );
        p2.getSpec().setAtmosphereColor(new Color(186, 250, 161,255));
        p2.getSpec().setCloudColor(new Color(236, 255,190,255));
        p2.getSpec().setAtmosphereThickness(0.8f);

        // Frozen outer supply cache world
        PlanetAPI p3 = system.addPlanet(
                "exileashen_vigil",
                ashen_star,
                "Vigil",
                "frozen",
                250f,
                120f,
                6800f,
                300f
        );

        // Main hidden crusader command station
        // Asha = Associated with truth, righteousness, and cosmic order in ancient Persian tradition.
        SectorEntityToken command = system.addCustomEntity(
                "exileashen_command_bastion",
                "Elpis",
                "station_lowtech3",
                ASHEN_FACTION
        );
        command.setCircularOrbitPointingDown(ashen_star, 165f, 4700f, 220f);
        command.setInteractionImage("illustrations", "orbital_construction");

        // Smaller forward listening/raid support post
        SectorEntityToken outpost = system.addCustomEntity(
                "exileashen_silent_watch",
                "Silent Watch Outpost",
                "station_lowtech1",
                ASHEN_FACTION
        );
        outpost.setCircularOrbitPointingDown(ashen_star, 310f, 7200f, 320f);
        outpost.setInteractionImage("illustrations", "pirate_station");

        // Defensive/dusty belt to make the system feel concealed and ugly
        system.addAsteroidBelt(ashen_star, 180, 5200f, 700f, 180f, 260f, Terrain.ASTEROID_BELT, "Providence Debris Belt");
        system.addRingBand(ashen_star, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 5150f, 240f);
        system.addRingBand(ashen_star, "misc", "rings_dust0", 256f, 2, Color.gray, 256f, 5350f, 260f);

        // Crusade comms/navigation infrastructure
        SectorEntityToken relay = system.addCustomEntity(
                "exileashen_comm_relay",
                "Ashen Comm Relay",
                "comm_relay_makeshift",
                ASHEN_FACTION
        );
        relay.setCircularOrbitPointingDown(ashen_star, 80f, 3600f, 160f);

        SectorEntityToken sensor = system.addCustomEntity(
                "exileashen_sensor_array",
                "Ashen Sensor Array",
                "sensor_array",
                ASHEN_FACTION
        );
        sensor.setCircularOrbitPointingDown(ashen_star, 210f, 6100f, 260f);

        SectorEntityToken nav = system.addCustomEntity(
                "exileashen_nav_buoy",
                "Ashen Nav Buoy",
                "nav_buoy_makeshift",
                ASHEN_FACTION
        );
        nav.setCircularOrbitPointingDown(ashen_star, 20f, 8200f, 360f);

        // Inner jump point near the support colony
        JumpPointAPI innerJump = Global.getFactory().createJumpPoint(
                "exileashen_inner_jump",
                "Steadfast Jump-point"
        );
        OrbitAPI innerOrbit = Global.getFactory().createCircularOrbit(
                ashen_star,
                110f,
                4600f,
                180f
        );
        innerJump.setOrbit(innerOrbit);
        innerJump.setRelatedPlanet(p2);
        innerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(innerJump);

        // Outer jump point near the hidden outpost
        JumpPointAPI outerJump = Global.getFactory().createJumpPoint(
                "exileashen_outer_jump",
                "Silent Watch Jump-point"
        );
        OrbitAPI outerOrbit = Global.getFactory().createCircularOrbit(
                ashen_star,
                300f,
                7600f,
                320f
        );
        outerJump.setOrbit(outerOrbit);
        outerJump.setRelatedPlanet(p3);
        outerJump.setStandardWormholeToHyperspaceVisual();
        system.addEntity(outerJump);

        system.autogenerateHyperspaceJumpPoints(false, false);

        return system;
    }
}
