package data.scripts.exiles;

import java.util.ArrayList;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;

/**
 * EVENTIDE'S WOUND, ON-SCREEN -- Pass 7 step 7e-2 (war doc sec 6.1 rung 3, option (b), ruled
 * 2026-10-14; start "after a visible win" and ~0.6 day per beat, Sean 2026-11-01).
 *
 * <p>Started by {@link ExileHegLeagueMidanStrikeStage} when the Strike Force wins at Eventide with
 * the player in range. Every {@code beatDays} one mirror or shade goes (the array dies with the
 * first); the bombardment is the LAST beat, and only then has the rung landed
 * ({@link ExileHegLeagueMidanStrikeStage#onEventideSequenceComplete}).
 *
 * <p><b>Failure refinement (ruled 2026-10-14):</b> if the Strike Force is broken mid-sequence (the
 * stage fails), the script stops. What is destroyed stays destroyed; there is no bombardment, so no
 * size loss -- the player saved Eventide's people but not its sky. The rung is FOILED (+12).
 *
 * <p><b>Fleets in orbit (7e-2 test fix, 2026-11-01):</b> the clock runs only while a Strike Force
 * fleet holds over Eventide, out of battle ({@link ExileHegLeagueMidanStrikeStage#holdOverEventide});
 * if the player leaves spawn range, the rest lands at once, as off-screen.
 *
 * <p>Saved: the beats were snapshot at the start, so a config edit cannot change a sequence already
 * running. One script per sequence; it removes itself when done or stopped.
 */
public class ExileEventideSequence implements EveryFrameScript {

    protected ExileHegLeagueMidanStrikeStage stage;
    protected String marketId;
    protected ArrayList beatNames = new ArrayList();   // String
    protected ArrayList beatActions = new ArrayList(); // String
    protected float beatDays;
    protected String planetDescId;
    protected String illustration;
    protected String adriftDescId;

    protected int nextBeat = 0;
    /** Starts full, so the first mirror falls at once after the win. */
    protected float sinceBeat;
    /** Game days since the last check; checks run every {@link #CHECK_DAYS}, not every frame. */
    protected float pending = 0f;
    protected boolean paused = false;
    protected static final float CHECK_DAYS = 0.1f;
    protected boolean done = false;
    protected StringBuffer summary = new StringBuffer();

    public ExileEventideSequence(ExileHegLeagueMidanStrikeStage stage, String marketId,
                                 ExileHegLeagueConfig config) {
        this.stage = stage;
        this.marketId = marketId;
        for (int i = 0; i < config.eventideBeatNames.size(); i++) {
            beatNames.add(config.eventideBeatNames.get(i));
            beatActions.add(config.eventideBeatActions.get(i));
        }
        this.beatDays = config.eventideBeatDays;
        this.planetDescId = config.eventidePlanetDescId;
        this.illustration = config.eventideIllustration;
        this.adriftDescId = config.eventideAdriftDescId;
        this.sinceBeat = beatDays;
    }

    /** Total on-screen length in days: one beat per entity plus the bombardment. */
    public static float lengthDays(ExileHegLeagueConfig config) {
        return config.eventideBeatDays * (config.eventideBeatNames.size() + 1);
    }

    public boolean isDone() {
        return done;
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void advance(float amount) {
        if (done) return;
        if (stage == null || stage.getStatus() == RaidStageStatus.FAILURE || stage.isStrikeAborted()) {
            done = true;
            log("Exiles Midan EVENTIDE SEQUENCE STOPPED: the Strike Force was broken after "
                    + nextBeat + " of " + beatNames.size() + " beat(s) -- what fell stays fallen; no"
                    + " bombardment | " + summary);
            return;
        }
        pending += Global.getSector().getClock().convertToDays(amount);
        if (pending < CHECK_DAYS) return;
        float step = pending;
        pending = 0f;

        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) {
            done = true;
            log("Exiles Midan EVENTIDE SEQUENCE STOPPED: market " + marketId + " is gone");
            return;
        }

        // The player has left: the fleets are abstract and no one is watching, so the rest of the
        // wound lands at once, as the off-screen path lands all of it.
        if (!stage.isOnScreen()) {
            while (nextBeat < beatNames.size()) applyNextBeat(market, false);
            bombard(market, " (completed off-screen: the player left)");
            return;
        }

        // 7e-2 test fix (2026-11-01): the clock runs only while a Strike Force fleet is over
        // Eventide, out of battle. Vanilla bombards from orbit; a sky falling with no fleet in
        // orbit was the bug. Pulled away by a fight, the sequence waits for them to return.
        if (!stage.hasFleetOverTarget(market)) {
            if (!paused) {
                paused = true;
                log("Exiles Midan EVENTIDE SEQUENCE PAUSED: no Strike Force fleet over "
                        + market.getName() + " after " + nextBeat + " of " + beatNames.size()
                        + " beat(s)");
            }
            return;
        }
        if (paused) {
            paused = false;
            log("Exiles Midan EVENTIDE SEQUENCE RESUMED: a Strike Force fleet is back over "
                    + market.getName());
        }
        sinceBeat += step;
        if (sinceBeat < beatDays) return;
        sinceBeat = 0f;

        if (nextBeat < beatNames.size()) {
            applyNextBeat(market, true);
            return;
        }
        bombard(market, "");
    }

    /** One mirror or shade (the array dies with the first). */
    protected void applyNextBeat(MarketAPI market, boolean explode) {
        if (nextBeat == 0 && ExileEventideWound.killArray(market)) append("array removed");
        String r = ExileEventideWound.applyBeat(market, (String) beatNames.get(nextBeat),
                (String) beatActions.get(nextBeat), adriftDescId, nextBeat, explode);
        nextBeat++;
        if (r != null) append(r);
        log("Exiles Midan EVENTIDE BEAT " + nextBeat + "/" + beatNames.size() + ": "
                + (r != null ? r : beatNames.get(nextBeat - 1) + " already gone"));
    }

    /** The last beat: a full vanilla saturation bombardment, under the strike's false flag. Only
     *  now has the rung landed. */
    protected void bombard(MarketAPI market, String note) {
        String flag = stage.getActorFactionId();
        append(ExileEventideWound.saturationBombard(market, flag, planetDescId, illustration));
        done = true;
        log("Exiles Midan EVENTIDE BOMBARDMENT" + note + ": " + summary);
        stage.onEventideSequenceComplete(market, summary.toString());
    }

    protected void append(String s) {
        if (summary.length() > 0) summary.append(", ");
        summary.append(s);
    }

    protected static void log(String line) {
        Global.getLogger(ExileEventideSequence.class).info(line);
    }
}
