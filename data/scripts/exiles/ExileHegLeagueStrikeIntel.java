package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * Operation intel for a Hegemony-Persean League SURGICAL STRIKE -- PASS 3 of
 * docs/EXILES_HEG_LEAGUE_WAR.md sec 4.3.
 *
 * <p><b>A subclass, not a fork</b> (the sec 13 reuse map's instruction, and the prompt's binding
 * constraint). Everything that made Pass 2's raid intel work is inherited unchanged and untouched:
 * the {@code maxShipSize} guardrail in {@code createFleet}, the raider tag, the per-kill bounty
 * paid by the struck side, the joined-the-raiders exploit guard, the side-agnostic per-battle
 * weariness attribution with its one-battle-one-credit seed guard, the deferred payout popup, the
 * hidden-until-arrival reveal and the end-of-op system cooldown. Measured, forking would have been
 * ~250 lines to change three of them.
 *
 * <p>What differs is <b>three overrides</b>: the op noun in the bullet text, the completion test
 * (a strike is completed when it LANDS, not when a presence window runs out), and the
 * interdiction-op flag that keeps a live strike from counting against interdiction's per-side
 * concurrency cap.
 *
 * <p><b>Who pays the bounty.</b> Inherited unchanged: the raided side. For a RETALIATION strike
 * that is the cycle's INCIDENT OFFENDER -- the side being punished posts the bounty for the
 * punishers, which is exactly the sec 4.7 shape (the player can profitably take either side, or
 * both across cycles).
 */
public class ExileHegLeagueStrikeIntel extends ExileHegLeagueRaidIntel {

    /**
     * The strike stage, so {@link #reportOutcome()} can ask whether the strike actually landed.
     * Held as a direct reference for the reason recorded on the Pass-2 field it mirrors: RaidIntel
     * exposes no public stage accessor, and the stage is already saved as part of the stage list,
     * so this adds no serialization surface.
     */
    protected ExileHegLeagueStrikeStage strikeStage = null;

    public ExileHegLeagueStrikeIntel(StarSystemAPI target, FactionAPI actor, String victimFactionId,
                                     int maxShipSize, float bountyPerFrigate,
                                     float wearinessPerFpDestroyed, float systemCooldownDays) {
        super(target, actor, victimFactionId, maxShipSize, bountyPerFrigate,
                wearinessPerFpDestroyed, systemCooldownDays);
    }

    public void setStrikeStage(ExileHegLeagueStrikeStage stage) {
        this.strikeStage = stage;
    }

    @Override
    public boolean isInterdictionOp() {
        return false;
    }

    @Override
    protected String getOpEndedText() {
        return "The punitive strike in the " + getSystem().getNameWithLowercaseType()
                + " has ended";
    }

    // getName() is deliberately NOT overridden: "System Bounty - <system>" is correct for a strike
    // too -- the struck side really does post a bounty for the strikers.

    /**
     * sec 3.2.1 "Up": a completed op is small upward pressure on the meter.
     *
     * <p>For a strike, "completed" means the disruption LANDED. A strike group that was driven off,
     * timed out, or arrived to find its target Battered is not a completed operation and reports
     * nothing -- and says so, permanently, in the log. This is the same reading Pass 2 applies to a
     * presence window that never ran, and it is what keeps the meter honest about a war going
     * badly.
     *
     * <p>Reported from {@code notifyEnded()} (inherited), never from the stage's {@code finish()}:
     * an op that aborts on losses never reaches {@code finish()} at all (the 2026-08-09 Ashen
     * finding).
     */
    @Override
    protected void reportOutcome() {
        if (outcomeReported) return;
        outcomeReported = true;

        String summary = strikeStage != null ? strikeStage.getOutcomeSummary() : "no stage";
        Global.getLogger(ExileHegLeagueStrikeIntel.class).info(
                "Exiles Heg-League strike outcome in "
                        + (getSystem() != null ? getSystem().getBaseName() : "?")
                        + " -- " + summary + " (actor=" + getActorFactionId()
                        + ", struck=" + getVictimFactionId() + ")");

        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director == null) return;
        boolean landed = strikeStage != null && strikeStage.hasLanded();
        // sec 3.2.6: weariness reads BOTH outcomes -- the struck side when it lands, the actor
        // when it does not. Tension still reads only a landed strike, unchanged.
        director.reportOperationWeariness(getActorFactionId(), getVictimFactionId(), landed, true);
        if (!landed) return;
        director.reportOperationCompleted(getActorFactionId());
    }
}
