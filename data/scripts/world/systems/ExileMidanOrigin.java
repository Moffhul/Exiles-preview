package data.scripts.world.systems;

import java.awt.Color;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;

/**
 * Meridian -- Midan's ORIGINAL colony site, now a graveyard (built 2026-07-07, author spec).
 * Uninhabited. PLACEHOLDER coordinates [+23000, +1500] pending Sean's map review: between the
 * Black Fleet anchors and Midan's current system, on the retreat line east.
 *
 * LORE (author, 2026-07-07): this was Midan's first home. The Core noticed it, tried to
 * strongarm them, then invaded and saturation-bombed the colony flat. Midan retreated further
 * east, found the current system, named its terran world Haven -- after the one that died
 * here. The system physically grounds Midan's "collapse is mercy" nihilism (§3 guardrail:
 * principled nihilists with a REAL wound, not cartoon villains) and is a natural Phase-2
 * quest site (evidence trails; the recruitment pitch gains teeth when Midan can SHOW the
 * player what the Core did).
 *
 * Deliberately NO markets, NO faction presence, NO props beyond conditions -- Phase-2 quests
 * add staging when they need it. The ruined worlds are surveyable/colonizable: settling
 * Midan's graveyard is allowed and should feel like something. NAMES: First Haven / Refuge /
 * Toll -- AUTHOR MAY VETO.
 */
public class ExileMidanOrigin {

    public StarSystemAPI generate(SectorAPI sector, float locX, float locY) {
        StarSystemAPI system = sector.createStarSystem("Meridian");
        system.getLocation().set(locX, locY);

        system.setBackgroundTextureFilename("graphics/backgrounds/background3.jpg");

        PlanetAPI star = system.initStar(
                "exilemidanorigin_star",
                "star_yellow",
                700f,
                420f
        );
        system.setLightColor(new Color(255, 240, 200));

        HyperspaceTerrainPlugin hyperTerrain = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
        NebulaEditor editor = new NebulaEditor(hyperTerrain);
        editor.clearArc(system.getLocation().x, system.getLocation().y, 0, 900f, 0, 360f);

        // --- First Haven: the murdered colony --------------------------------------
        // A Terran world that should be a paradise; the conditions tell the story.
        PlanetAPI firstHaven = system.addPlanet(
                "exilemidanorigin_firsthaven",
                star,
                "First Haven",
                "terran",
                130f,
                170f,
                3800f,
                170f
        );
        Misc.initConditionMarket(firstHaven);
        firstHaven.getMarket().addCondition("habitable");
        firstHaven.getMarket().addCondition("mild_climate");
        firstHaven.getMarket().addCondition("pollution");        // the bombing's residue (author spec)
        firstHaven.getMarket().addCondition("ruins_extensive");  // the colony that was (author spec)
        firstHaven.getMarket().addCondition("farmland_poor");    // fields burned from orbit
        firstHaven.setCustomDescriptionId(null);

        // --- Refuge: where the survivors dug in before the retreat -----------------
        PlanetAPI refuge = system.addPlanet(
                "exilemidanorigin_refuge",
                star,
                "Refuge",
                "desert",
                90f,
                130f,
                5400f,
                260f
        );
        Misc.initConditionMarket(refuge);
        refuge.getMarket().addCondition("hot");
        refuge.getMarket().addCondition("ruins_widespread");
        refuge.getMarket().addCondition("ore_moderate");

        // --- Toll: the outer mining camp, abandoned in place ------------------------
        PlanetAPI toll = system.addPlanet(
                "exilemidanorigin_toll",
                star,
                "Toll",
                "frozen",
                150f,
                110f,
                7200f,
                380f
        );
        Misc.initConditionMarket(toll);
        toll.getMarket().addCondition("cold");
        toll.getMarket().addCondition("ruins_scattered");
        toll.getMarket().addCondition("volatiles_trace");

        // Shattered inner belt -- orbital bombardment leaves debris rings
        system.addAsteroidBelt(star, 120, 2600f, 500f, 140f, 210f, Terrain.ASTEROID_BELT, "Bombardment Ring");
        system.addRingBand(star, "misc", "rings_asteroids0", 256f, 2, Color.white, 256f, 2600f, 190f);

        system.autogenerateHyperspaceJumpPoints(true, true);
        return system;
    }
}
