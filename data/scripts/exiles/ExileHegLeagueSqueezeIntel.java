package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * Operation intel for a Hegemony-Persean League PLANETARY BLOCKADE, "the squeeze" -- PASS 6 of
 * docs/EXILES_HEG_LEAGUE_WAR.md sec 4.4.
 *
 * <p><b>A subclass, not a fork</b> -- the {@link ExileHegLeagueStrikeIntel} template, three
 * overrides and nothing else. Everything Pass 2's raid intel earned is inherited unchanged: the
 * {@code maxShipSize} guardrail in {@code createFleet}, the raider tag, the per-kill bounty paid by
 * the squeezed side, the joined-the-raiders exploit guard, the side-agnostic per-battle weariness
 * attribution with its one-battle-one-credit seed guard, the deferred payout popup, the
 * hidden-until-arrival reveal and the end-of-op system cooldown.
 *
 * <p><b>Presentation is inherited verbatim</b> (D80.8): hidden until arrival, source NOT concealed
 * (Hegemony and League ports are public knowledge, sec 3.4), and no
 * {@code getMapLocation()}/{@code getArrowData()} overrides. It posts as "System Bounty -
 * &lt;system&gt;" like its siblings, and that name is correct here too -- the squeezed side really
 * does pay for dead squeezers.
 *
 * <p><b>Who pays the bounty.</b> Inherited unchanged: the side being squeezed. For a RETALIATION
 * squeeze that is the cycle's INCIDENT OFFENDER -- the side being punished posts the bounty for
 * the punishers, which is the sec 4.7 shape exactly (the player can profitably take either side,
 * or both across cycles).
 *
 * <p>The one thing worth noting about {@link #isInterdictionOp()}: it returns false so a live
 * squeeze cannot consume an interdiction slot. That is the Pass-3 precedent and the entire reason
 * that method exists -- {@code getIntel(ExileHegLeagueRaidIntel.class)} returns subclasses, so
 * without it this op would silently throttle Pass-2 behaviour that has already been signed off in
 * a live test.
 */
public class ExileHegLeagueSqueezeIntel extends ExileHegLeagueRaidIntel {

    public ExileHegLeagueSqueezeIntel(StarSystemAPI target, FactionAPI actor,
                                      String victimFactionId, int maxShipSize,
                                      float bountyPerFrigate, float wearinessPerFpDestroyed,
                                      float systemCooldownDays) {
        super(target, actor, victimFactionId, maxShipSize, bountyPerFrigate,
                wearinessPerFpDestroyed, systemCooldownDays);
    }

    /** Launch FP, for the victory bonus's size scaling (sec 14.15). Set by the manager at launch;
     *  a pre-2026-09-28 save restores 0, which scales as the reference size. Saved. */
    protected float launchFp = 0f;
    /** One victory-bonus resolution per op. Saved. */
    protected boolean victoryBonusResolved = false;

    public void setLaunchFp(float launchFp) {
        this.launchFp = launchFp;
    }

    /** A squeeze must not consume an interdiction concurrency slot. */
    @Override
    public boolean isInterdictionOp() {
        return false;
    }

    /**
     * "BREAK THE BLOCKADE" (sec 14.15, ratified 2026-09-28): a squeeze driven off through LOSSES
     * pays the player a contribution-based victory bonus from the squeezed side. The parent logs
     * the abort line and records whether the failure was vanilla's losses abort; any other failure
     * (target gone, organise-stage loss of a source) is not a break and pays nothing.
     */
    @Override
    protected void failedAtStage(RaidStage stage) {
        super.failedAtStage(stage);
        if (victoryBonusResolved || !lastFailureWasLossesAbort) return;
        victoryBonusResolved = true;
        try {
            ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
            ExileHegLeagueConfig config = director != null ? director.getConfig() : null;
            if (config == null) return;
            float ref = config.victoryBonusRefFpSqueeze;
            float sizeRatio = (ref > 0f && launchFp > 0f) ? launchFp / ref : 1f;
            ExileHegLeagueBattleRewards.payVictoryBonus(config, "planetary blockade",
                    getSystem() != null ? getSystem().getNameWithLowercaseType() : "the system",
                    getActorFactionId(), getVictimFactionId(),
                    config.victoryBonusSqueezeBase, sizeRatio,
                    victoryPlayerFp, victoryTotalLostFp, ExileHegLeagueSqueezeIntel.class);
        } catch (Exception e) {
            // A reward must never break the op's failure path.
            Global.getLogger(ExileHegLeagueSqueezeIntel.class).warn(
                    "Exiles Heg-League squeeze victory bonus failed: " + e);
        }
    }

    @Override
    protected String getOpEndedText() {
        return "The blockade in the " + getSystem().getNameWithLowercaseType() + " has ended";
    }

    /**
     * sec 3.2.1 "Up": a completed op is small upward pressure on the meter.
     *
     * <p>For a squeeze, "completed" means the cordon held its whole presence window -- the same
     * reading Pass 2 applies to an interdiction, and the reason the stage's
     * {@code isPresenceCompleted()} is inherited rather than redefined. A cordon driven off,
     * aborted on losses or timed out is not a completed operation, reports no tension, and says so
     * permanently in the log.
     *
     * <p>Reported from {@code notifyEnded()} (inherited), never from the stage's {@code finish()}:
     * an op that aborts on losses never reaches {@code finish()} at all (the 2026-08-09 Ashen
     * finding).
     */
    @Override
    protected void reportOutcome() {
        if (outcomeReported) return;
        outcomeReported = true;

        boolean completed = interdictionStage != null && interdictionStage.isPresenceCompleted();
        String anchor = interdictionStage instanceof ExileHegLeagueSqueezeStage
                ? ((ExileHegLeagueSqueezeStage) interdictionStage).getAnchorMarketId() : "?";
        Global.getLogger(ExileHegLeagueSqueezeIntel.class).info(
                "Exiles Heg-League squeeze outcome in "
                        + (getSystem() != null ? getSystem().getBaseName() : "?")
                        + " -- anchor " + anchor + ": "
                        + (completed ? "HELD its presence window" : "did NOT hold")
                        + " (actor=" + getActorFactionId()
                        + ", squeezed=" + getVictimFactionId() + ")");

        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director == null) return;
        // sec 3.2.6: weariness reads BOTH outcomes -- the victim when the cordon held, the actor
        // when it was driven off. Tension still reads only a completed op, unchanged.
        director.reportOperationWeariness(getActorFactionId(), getVictimFactionId(), completed,
                ExileHegLeagueDirector.OP_TYPE_PLANETARY);
        if (!completed) return;
        director.reportOperationCompleted(getActorFactionId());
    }
}
