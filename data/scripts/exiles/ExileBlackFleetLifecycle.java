package data.scripts.exiles;

import java.awt.Color;

import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * Black Fleet anchor lifecycle (M5 of the split build, 2026-07-07). Watches the two anchor
 * markets and enforces the ratified defeatability rules:
 *
 *  1. MOURAD RELOCATES: if Sallee (his seat) is dead but Lundy survives, Mourad moves to
 *     Lundy Hold -- new admin + comm-directory entry + a campaign message. One-time.
 *  2. SPIGOT CLOSES: when BOTH anchors are dead, the makesPirateBases custom flag is removed
 *     from the faction so vanilla stops seeding Black Fleet temp bases. The flag lives on the
 *     faction SPEC, which reloads from the .faction file every game launch -- so this script
 *     RE-ASSERTS the removal on every check (it is serialized with the save; its state
 *     survives, the spec's does not). This is the fix for the vanilla "pirate bases never
 *     stop spawning" complaint: kill the anchors and the spigot actually closes.
 *
 * "Anchor dead" = its market is gone from the economy, OR decivilized, OR no longer owned by
 * exileblackfleet (Nexerelin conquest counts as taking the anchor). NOTE: destroying the
 * battlestation's combat-station fleet in battle disrupts the Battlestation industry but does
 * NOT kill the market -- that is vanilla station-market behavior, kept as-is for v1. The
 * player-facing kill paths are saturation bombardment, deciv via sustained pressure, or (with
 * Nexerelin) invasion. Revisit after playtest if station-kill should count for more.
 *
 * Perf discipline (CLAUDE.md par.7): all work gated on a 10-day IntervalUtil; no allocation
 * in the steady state.
 */
public class ExileBlackFleetLifecycle implements EveryFrameScript {

    public static final String BF_FACTION = "exileblackfleet";
    // Econ-JSON refactor 2026-07-08: markets are declared in econ/sallee.json + lundy.json and
    // get loader-assigned ids, so anchors are resolved via their STATION ENTITIES (stable ids).
    public static final String SALLEE_STATION = "exilebf_sallee_station";
    public static final String LUNDY_STATION = "exilebf_lundy_station";
    public static final String MOURAD_ID = "exile_mourad";
    public static final String DEFEATED_FLAG = "$exilebf_defeated";

    public static final float CHECK_DAYS = 10f;

    protected IntervalUtil interval = new IntervalUtil(CHECK_DAYS * 0.8f, CHECK_DAYS * 1.2f);
    protected boolean mouradRelocated = false;
    protected boolean defeatAnnounced = false;

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void advance(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);
        interval.advance(days);
        if (!interval.intervalElapsed()) return;

        // Old-save guard: never generated (pre-split save) = nothing to watch. Without this,
        // a legacy save would read "both anchors dead" and falsely announce the defeat.
        if (!Global.getSector().getPersistentData().containsKey("exilebf_anchors_generated")) {
            return;
        }

        boolean salleeAlive = isAnchorAlive(SALLEE_STATION);
        boolean lundyAlive = isAnchorAlive(LUNDY_STATION);

        if (!salleeAlive && lundyAlive && !mouradRelocated) {
            relocateMourad();
        }

        if (!salleeAlive && !lundyAlive) {
            closeSpigot();
        }
    }

    protected boolean isAnchorAlive(String stationEntityId) {
        MarketAPI market = getAnchorMarket(stationEntityId);
        if (market == null) return false;
        if (!market.isInEconomy()) return false;
        if (market.hasCondition(Conditions.DECIVILIZED)) return false;
        if (!BF_FACTION.equals(market.getFactionId())) return false; // conquered = lost
        return true;
    }

    /** Anchor market via its station entity (markets are econ-JSON-declared; ids are the loader's). */
    protected MarketAPI getAnchorMarket(String stationEntityId) {
        SectorEntityToken station = Global.getSector().getEntityById(stationEntityId);
        if (station == null) return null;
        return station.getMarket();
    }

    /** The king's seat fell; the king did not. One-time story beat. Mourad is a HIDDEN NPC
     *  (author ruling 2026-07-08) — no comm-directory seating; the relocation is tracked in
     *  memory for the lifecycle and future quests, and announced narratively. */
    protected void relocateMourad() {
        mouradRelocated = true;

        PersonAPI mourad = Global.getSector().getImportantPeople().getPerson(MOURAD_ID);
        if (mourad == null) return;

        Global.getSector().getMemoryWithoutUpdate().set("$exilebf_mourad_at", "lundy");

        Global.getSector().getCampaignUI().addMessage(
                "Word spreads through the fringe: Sallee Anchorage has fallen, "
                        + "but Admiral Mourad's flag now flies over Lundy Hold.",
                Color.RED);
        Global.getLogger(ExileBlackFleetLifecycle.class).info(
                "Black Fleet lifecycle: Mourad relocated to Lundy Hold (hidden; memory flag updated).");
    }

    /**
     * Both anchors dead: stop vanilla from seeding new Black Fleet temp bases. Re-asserted
     * every check because the faction spec (and its custom JSONObject) reloads from the
     * .faction file on every game launch.
     */
    protected void closeSpigot() {
        FactionAPI bf = Global.getSector().getFaction(BF_FACTION);
        if (bf == null) return;

        JSONObject custom = bf.getCustom();
        if (custom != null && custom.has("makesPirateBases")) {
            custom.remove("makesPirateBases");
            Global.getLogger(ExileBlackFleetLifecycle.class).info(
                    "Black Fleet lifecycle: makesPirateBases removed (spigot closed).");
        }

        if (!defeatAnnounced) {
            defeatAnnounced = true;
            Global.getSector().getMemoryWithoutUpdate().set(DEFEATED_FLAG, true);
            Global.getSector().getCampaignUI().addMessage(
                    "The Black Fleet is broken. Without its anchorages, the armada is "
                            + "just scattered raiders with nowhere to refit.",
                    Color.RED);
            Global.getLogger(ExileBlackFleetLifecycle.class).info(
                    "Black Fleet lifecycle: both anchors dead; " + DEFEATED_FLAG + " set.");
        }
    }
}
