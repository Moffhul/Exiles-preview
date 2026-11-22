package data.scripts.exiles;

import java.awt.Color;
import java.util.List;
import java.util.Random;

import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ExplosionEntityPlugin.ExplosionParams;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.util.Misc;

/**
 * EVENTIDE'S WOUND -- Pass 7 step 7e-2 (war doc sec 6.1 rung 3, ruled 2026-10-14; the split and the
 * pace by Sean, 2026-11-01). The third and final Midan blow, and the only PERMANENT one:
 * <ol>
 * <li>the Orbital Solar Array dies ({@code solar_array} removed; vanilla's {@code poor_light} is
 * unmasked) -- with the FIRST beat, whatever it is;</li>
 * <li>Eventide's sky is wrecked, beat by beat: the configured mirrors and shades are destroyed
 * (a thin, no-salvage debris field where each one hung) or knocked adrift (a plain orbit that no
 * longer tracks the planet, and its own description). The rest are left untouched -- something of
 * Eventide visibly survives (Sean);</li>
 * <li>the bombardment comes LAST: a FULL VANILLA SATURATION BOMBARDMENT (Sean, 2026-11-01,
 * superseding the 2026-10-14 farmland-scorch and description steps: "let the sat-bomb do the heavy
 * lifting") -- see {@link #saturationBombard}.</li>
 * </ol>
 * The Hybrasil scar ({@code ExilesModPlugin.applyHybrasilMirrorDamageOnce}) is the precedent for
 * every entity operation here.
 *
 * <p><b>VANILLA ID BUG:</b> Samarra's three shades reuse the mirrors' ids
 * ({@code eventide_mirror1/3/5}), so entities are found by custom entity TYPE and NAME, never by id.
 *
 * <p>Two callers share these helpers so the off-screen and on-screen wounds cannot drift apart:
 * {@link #applyAll} (the strike lands with nobody watching: everything at once) and
 * {@link ExileEventideSequence} (the player is there: one beat at a time).
 */
public class ExileEventideWound {

    /** Find a stellar mirror or shade in the system by its (unique) name; null if gone. */
    public static SectorEntityToken find(StarSystemAPI system, String name) {
        if (system == null || name == null) return null;
        List all = system.getCustomEntities();
        for (int i = 0; i < all.size(); i++) {
            CustomCampaignEntityAPI e = (CustomCampaignEntityAPI) all.get(i);
            String type = e.getCustomEntityType();
            if (!Entities.STELLAR_MIRROR.equals(type) && !Entities.STELLAR_SHADE.equals(type)) continue;
            if (name.equals(e.getName())) return e;
        }
        return null;
    }

    /** The array dies (idempotent). Returns true if it was there. */
    public static boolean killArray(MarketAPI market) {
        if (market == null || !market.hasCondition("solar_array")) return false;
        market.removeCondition("solar_array");
        market.reapplyConditions();
        return true;
    }

    /**
     * One beat on one entity. {@code action} "adrift" knocks it off station; anything else destroys
     * it. {@code explode}: the on-screen flash (vanilla explosion entity, fleet damage NONE).
     * Returns a short log fragment, or null if the entity is already gone.
     */
    public static String applyBeat(MarketAPI market, String name, String action, String adriftDescId,
                                   int index, boolean explode) {
        if (market == null || market.getPrimaryEntity() == null) return null;
        SectorEntityToken planet = market.getPrimaryEntity();
        if (!(planet.getContainingLocation() instanceof StarSystemAPI)) return null;
        StarSystemAPI system = (StarSystemAPI) planet.getContainingLocation();
        SectorEntityToken e = find(system, name);
        if (e == null) return null;

        float angle = Misc.getAngleInDegrees(planet.getLocation(), e.getLocation());
        float radius = Misc.getDistance(planet.getLocation(), e.getLocation());

        if ("adrift".equals(action)) {
            if (explode) explode(system, e.getLocation(), 90f);
            // Plain setCircularOrbit no longer tracks the planet: it visibly aims nowhere useful;
            // the wider, slower orbit reads as knocked outward and left (the Hybrasil pattern).
            e.setCircularOrbit(planet, angle, radius * 1.3f, 90f);
            if (adriftDescId != null && adriftDescId.length() > 0 && descriptionExists(adriftDescId)) {
                e.setCustomDescriptionId(adriftDescId);
            }
            return name + " knocked adrift";
        }

        Vector2f where = new Vector2f(e.getLocation());
        if (explode) explode(system, where, 160f);
        system.removeEntity(e);

        // Thin and poor, as at Hybrasil: scenery that explains itself, not a reward.
        DebrisFieldParams params = new DebrisFieldParams(140f, 0.35f, 1000000f, 0f);
        params.source = DebrisFieldSource.MIXED;
        params.baseSalvageXP = 0;
        params.defenderProb = 0f;
        SectorEntityToken debris = Misc.addDebrisField(system, params, new Random());
        if (debris != null) {
            debris.setSensorProfile(null);
            debris.setDiscoverable(null);
            debris.setId("exiles_eventide_wreck_" + index);
            debris.setCircularOrbit(planet, angle, radius, 200f);
        }
        return name + " destroyed";
    }

    /**
     * The last beat: a FULL VANILLA SATURATION BOMBARDMENT (Sean, 2026-11-01: "this is supposed to be
     * huge ... let the sat-bomb do the heavy lifting"). Vanilla's own NPC entry point, exactly as its
     * punitive expeditions call it ({@code PEActionStage}): {@code MarketCMD.doBombardment(faction,
     * SATURATION)} -- Stability -10 ({@code bombardSaturationStability}), Pollution on a habitable
     * world, every bombardable industry disrupted for 365-456 days, size -1 (or decivilized if at or
     * below {@code bombardDestroyThreshold}; Eventide is far above it), the 30-day RECENTLY_BOMBARDED
     * flag, and the bombardment animation when the player is in the system. The atrocity reputation
     * hits live only in the PLAYER's dialog path, so they do not apply.
     *
     * <p>The bombarding faction is the strike's own flag (the Persean League -- the false flag).
     * The description and illustration swaps are optional extras, off by default (empty ids).
     * Returns a short log fragment.
     */
    public static String saturationBombard(MarketAPI market, String factionId, String planetDescId,
                                           String illustration) {
        if (market == null || market.getPrimaryEntity() == null) return "no market";
        int before = market.getSize();
        boolean hadPollution = market.hasCondition("pollution");
        FactionAPI faction = Global.getSector().getFaction(factionId != null ? factionId : Factions.PERSEAN);
        new MarketCMD(market.getPrimaryEntity()).doBombardment(faction, BombardType.SATURATION);

        int disrupted = 0;
        List inds = market.getIndustries();
        for (int i = 0; i < inds.size(); i++) {
            if (((Industry) inds.get(i)).getDisruptedDays() > 300f) disrupted++;
        }
        SectorEntityToken planet = market.getPrimaryEntity();
        if (planetDescId != null && planetDescId.length() > 0 && descriptionExists(planetDescId)) {
            planet.setCustomDescriptionId(planetDescId);
        }
        if (illustration != null && illustration.length() > 0) {
            planet.setInteractionImage("illustrations", illustration);
        }
        return "SATURATION BOMBARDMENT (vanilla): size " + before + " -> " + market.getSize()
                + ", " + disrupted + " industries disrupted"
                + (!hadPollution && market.hasCondition("pollution") ? ", pollution" : "")
                + ", stability -" + MarketCMD.getSaturationBombardmentStabilityPenalty();
    }

    /** Off-screen: the whole wound at once, no explosions (nobody is there to see). */
    public static String applyAll(MarketAPI market, ExileHegLeagueConfig config) {
        StringBuffer sb = new StringBuffer();
        sb.append(killArray(market) ? "array removed" : "array already gone");
        for (int i = 0; i < config.eventideBeatNames.size(); i++) {
            String r = applyBeat(market, (String) config.eventideBeatNames.get(i),
                    (String) config.eventideBeatActions.get(i), config.eventideAdriftDescId, i, false);
            if (r != null) sb.append(", ").append(r);
        }
        sb.append(", ").append(saturationBombard(market, Factions.PERSEAN, config.eventidePlanetDescId,
                config.eventideIllustration));
        return sb.toString();
    }

    protected static void explode(LocationAPI where, Vector2f loc, float radius) {
        ExplosionParams params = new ExplosionParams(new Color(255, 180, 110), where, loc, radius, 1f);
        SectorEntityToken boom = where.addCustomEntity(Misc.genUID(), "Explosion", Entities.EXPLOSION,
                Factions.NEUTRAL, params);
        boom.setLocation(loc.x, loc.y);
    }

    /** A missing descriptions.csv row would show an error string on the entity; check first. */
    protected static boolean descriptionExists(String id) {
        try {
            return Global.getSettings().getDescription(id, Description.Type.CUSTOM) != null;
        } catch (Exception e) {
            return false;
        }
    }
}
