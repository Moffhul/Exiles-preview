package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * THE CRUSADE GOES HOME (§7, Sean 2026-08-16).
 *
 * <p>After Culann is successfully raided, every Ashen fleet in Hybrasil stops picking fights and
 * leaves. Sean's live test: the armadas took the objective and then would not disengage --
 * repeatedly aborting their departure to chase Tri-Tachyon fleets, including a Capital Assurance
 * Reserve that respawned and re-engaged twice.
 *
 * <p><b>The obvious flag is dead code.</b> {@code MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE}
 * ({@code $cfai_makeNonAggressive}) is declared in the API and read <b>nowhere in the engine</b> --
 * verified by scanning all 2,818 classes in {@code starfarer_obf.jar} for the string literal: zero
 * hits, against 1 for {@code $cfai_holdVsStronger} and 4 each for {@code $cfai_makeAggressive} and
 * {@code $cfai_makeHostile}. Setting it would have been a silent no-op, and it is the flag a
 * reasonable person reaches for first. (Method: obfuscation mangles identifiers but leaves string
 * literals intact -- see the §14.4 method note.)
 *
 * <p><b>What works is the mechanism this mod already uses</b> for keeping blockade fleets off
 * stations: {@code ModularFleetAIAPI.getStrategicModule().getDoNotAttack()}, a
 * {@code TimeoutTracker} of entities the fleet will not select as targets. Note the documented
 * limit -- it prevents target <i>selection</i>, not battle <i>participation</i>. That is exactly
 * the ask: they stop <i>initiating</i>, they still defend themselves if intercepted.
 *
 * <p><b>Why a script rather than a stage.</b> {@code ReturnStage.updateStatus()} sets SUCCESS on
 * its first tick, so a stage subclass would get a single frame -- useless when the Reserve respawns
 * days later. This re-applies on an interval and covers hostiles that did not exist when Culann
 * fell.
 *
 * <p><b>Bounded by construction</b> (CLAUDE.md §7): it holds no fleet references between ticks,
 * runs on an {@link IntervalUtil} rather than per frame, and {@link #isDone()} retires it once the
 * window expires or the Crusade has left -- the engine drops finished scripts, so there is nothing
 * to orphan.
 */
public class AshenStandDownScript implements EveryFrameScript {

    /** How long the stand-down holds. Long enough to cross Hybrasil and jump out. */
    public static float DEFAULT_DAYS = 45f;
    /** Timeout written into each fleet's do-not-attack tracker; refreshed every tick. */
    public static float DO_NOT_ATTACK_DAYS = 60f;

    protected StarSystemAPI system;
    protected String factionId;
    protected float daysLeft;
    protected transient IntervalUtil interval;

    public AshenStandDownScript(StarSystemAPI system, String factionId, float days) {
        this.system = system;
        this.factionId = factionId;
        this.daysLeft = days;
        this.interval = new IntervalUtil(0.9f, 1.1f);
    }

    protected Object readResolve() {
        if (interval == null) interval = new IntervalUtil(0.9f, 1.1f);
        return this;
    }

    public boolean isDone() {
        return daysLeft <= 0f;
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void advance(float amount) {
        if (system == null || Global.getSector() == null) return;
        if (interval == null) interval = new IntervalUtil(0.9f, 1.1f);

        float days = Global.getSector().getClock().convertToDays(amount);
        daysLeft -= days;
        if (daysLeft <= 0f) return;

        interval.advance(days);
        if (!interval.intervalElapsed()) return;

        standDown();
    }

    /** Tell every Crusade fleet still in the system to ignore everything and go home. */
    protected void standDown() {
        List ashen = new ArrayList();     // CampaignFleetAPI
        List hostiles = new ArrayList();  // SectorEntityToken

        List fleets = system.getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            if (fleet == null || !fleet.isAlive() || fleet.getFaction() == null) continue;
            if (fleet.isPlayerFleet()) continue;
            if (factionId.equals(fleet.getFaction().getId())) {
                ashen.add(fleet);
            } else if (fleet.getFaction().isHostileTo(
                    Global.getSector().getFaction(factionId))) {
                hostiles.add(fleet);
            }
        }

        // Nobody left to order home -- retire early rather than tick out the clock.
        if (ashen.isEmpty()) {
            daysLeft = 0f;
            return;
        }

        // Station fleets too: they are the thing a withdrawing raider is most likely to turn on.
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market == null) continue;
            CampaignFleetAPI station = Misc.getStationFleet(market);
            if (station != null) hostiles.add(station);
        }

        for (int i = 0; i < ashen.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) ashen.get(i);

            if (fleet.getAI() instanceof ModularFleetAIAPI) {
                ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
                if (ai.getStrategicModule() != null) {
                    for (int k = 0; k < hostiles.size(); k++) {
                        ai.getStrategicModule().getDoNotAttack().add(
                                (SectorEntityToken) hostiles.get(k), DO_NOT_ATTACK_DAYS);
                    }
                }
            }

            // And actually send them home. Re-issued each tick because the thing Sean observed was
            // departures being ABORTED, not never ordered -- one order at the start would be
            // overridden the first time the AI re-evaluated.
            Misc.giveStandardReturnToSourceAssignments(fleet);
        }
    }

    /**
     * Start the stand-down, replacing any that is already running for this system.
     *
     * <p>Registered on the sector rather than held by the raid, because the ops that need ordering
     * home outlive the one that took the objective -- the sweep, the gate blockade and the
     * screening armada are separate operations with their own timers.
     */
    public static void begin(StarSystemAPI system, String factionId) {
        if (system == null || Global.getSector() == null) return;

        List scripts = Global.getSector().getScripts();
        for (int i = 0; i < scripts.size(); i++) {
            Object script = scripts.get(i);
            if (script instanceof AshenStandDownScript) {
                AshenStandDownScript existing = (AshenStandDownScript) script;
                if (existing.system == system) {
                    existing.daysLeft = DEFAULT_DAYS; // refresh rather than stack
                    return;
                }
            }
        }
        Global.getSector().addScript(new AshenStandDownScript(system, factionId, DEFAULT_DAYS));
        Global.getLogger(AshenStandDownScript.class).info(
                "Ashen stand-down: all Crusade fleets in " + system.getBaseName()
                        + " ordered to disengage and return for " + (int) DEFAULT_DAYS + "d.");
    }
}
