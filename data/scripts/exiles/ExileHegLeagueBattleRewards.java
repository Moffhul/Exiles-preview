package data.scripts.exiles;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.util.Misc;

import data.scripts.exiles.OmicronRaidIntel.BountyResult;

/**
 * The two things a Hegemony-Persean League operation pays out when the player fights it
 * (PASS 4 of docs/EXILES_HEG_LEAGUE_WAR.md, decision D4): the raided side's per-kill bounty, and
 * side-agnostic war weariness from player-inflicted losses.
 *
 * <h2>Why this exists</h2>
 * Pass 4's system blockade is a STANDALONE operation on vanilla's {@code BlockadeFGI} pipeline,
 * not a stage of a raid -- and neither {@code FleetGroupIntel} nor {@code BaseIntelPlugin}
 * implements {@code FleetEventListener}, so the blockade has to be its own listener. That would
 * have made it a second holder of ~60 lines already written and live-tested in
 * {@link ExileHegLeagueRaidIntel}. Two callers of identical code is exactly the measured bar this
 * project sets for extraction (CLAUDE.md sec 6), and unlike the Pass-5b candidates the bodies here
 * are genuinely identical rather than superficially similar: same bounty formula, same exploit
 * guard, same weariness ruling, same log lines.
 *
 * <h2>What it deliberately does NOT own</h2>
 * <b>State stays with the caller.</b> The raid intel keeps its {@code pendingBounties} queue and
 * its {@code wearinessBattleSeed} guard as its own saved fields, and the blockade keeps its own;
 * this class is pure static behaviour and holds nothing between calls. That is why
 * {@link #applyPlayerInflictedWeariness} takes the previous seed and RETURNS the new one instead
 * of tracking it: a shared mutable guard across two independent operations would let one op's
 * battle silently suppress the other's credit. It also keeps this class out of saved state
 * entirely.
 *
 * <p><b>Behaviour is byte-identical to the Pass-2/3 raid-intel bodies</b>, including the order of
 * the early returns (the seed is recorded even when the director is missing) and the exact log
 * wording, which existing traces are read against. The one parameterised difference is the logger
 * class, so a line still names the operation type that produced it.
 */
public class ExileHegLeagueBattleRewards {

    private ExileHegLeagueBattleRewards() {
    }

    /**
     * War weariness from player-inflicted losses -- <b>RULED by Sean, 2026-08-27</b> (sec 14.3).
     *
     * <p><b>The rule:</b> weariness is credited to whichever faction OWNED the ships the player
     * killed, battle by battle. Player kills the operation's fleets -> the acting faction wearies.
     * Player sides with the operation and kills defenders -> the DEFENDING faction wearies. Both
     * can happen in one battle (a three-way), and both are credited.
     *
     * <p><b>Implementation:</b> the player's kills are, by definition, losses on the side opposing
     * the player, so the scan is {@code battle.getNonPlayerSideSnapshot()} -- the same list vanilla
     * {@code SystemBountyIntel} pays out from. Fleets belonging to neither war faction (pirates, a
     * passing Independent) are skipped; each war faction's fleet-point losses are totalled
     * separately and credited to its own ledger, scaled by
     * {@code playerInvolvementFraction * wearinessPerFpDestroyed}.
     *
     * <p><b>SCOPE LIMIT (documented, not a defect):</b> a caller attaches itself per-fleet to its
     * own ships, so it only ever sees battles those fleets are in. Hegemony/League battles
     * elsewhere in the sector contribute nothing. Sector-wide accounting would need a
     * ListenerManager-level listener owned by the director; that is deliberately not built.
     *
     * <p><b>One battle, one credit.</b> The listener fires once per fleet, so an operation with two
     * fleets in the same battle would otherwise scan the losing side twice and double-credit.
     *
     * @param lastBattleSeed the caller's stored guard, or null if it has credited nothing yet.
     * @return the guard value the caller must now store -- the battle's seed once this battle has
     *         been considered, or {@code lastBattleSeed} unchanged when nothing was considered.
     */
    public static Long applyPlayerInflictedWeariness(BattleAPI battle, String actorFactionId,
                                                     String victimFactionId,
                                                     float wearinessPerFpDestroyed,
                                                     Long lastBattleSeed, Class logClass) {
        if (wearinessPerFpDestroyed <= 0f) return lastBattleSeed;
        if (battle == null) return lastBattleSeed;

        float involvement = battle.getPlayerInvolvementFraction();
        if (involvement <= 0f) return lastBattleSeed;

        long seed = battle.getSeed();
        if (lastBattleSeed != null && lastBattleSeed.longValue() == seed) return lastBattleSeed;
        // Recorded BEFORE the remaining guards, exactly as the Pass-2 body did: a battle that has
        // been looked at is a battle that has been looked at, whatever came of it.
        Long newSeed = Long.valueOf(seed);

        ExileHegLeagueDirector director = ExileHegLeagueDirector.get();
        if (director == null) return newSeed;

        List side = battle.getNonPlayerSideSnapshot();
        if (side == null) return newSeed;

        float actorFp = 0f;
        float victimFp = 0f;
        for (int i = 0; i < side.size(); i++) {
            CampaignFleetAPI other = (CampaignFleetAPI) side.get(i);
            if (other == null || other.getFaction() == null) continue;

            String fid = other.getFaction().getId();
            boolean isActor = actorFactionId != null && actorFactionId.equals(fid);
            boolean isVictim = victimFactionId != null && victimFactionId.equals(fid);
            if (!isActor && !isVictim) continue;

            float fp = 0f;
            List lost = Misc.getSnapshotMembersLost(other);
            if (lost == null) continue;
            for (int j = 0; j < lost.size(); j++) {
                FleetMemberAPI loss = (FleetMemberAPI) lost.get(j);
                if (loss == null) continue;
                fp += loss.getFleetPointCost();
            }
            if (fp <= 0f) continue;

            if (isActor) {
                actorFp += fp;
            } else {
                victimFp += fp;
            }
        }

        if (actorFp <= 0f && victimFp <= 0f) return newSeed;

        if (actorFp > 0f) {
            director.addWeariness(actorFactionId,
                    actorFp * involvement * wearinessPerFpDestroyed);
        }
        if (victimFp > 0f) {
            director.addWeariness(victimFactionId,
                    victimFp * involvement * wearinessPerFpDestroyed);
        }

        Global.getLogger(logClass).info(
                "Exiles Heg-League player-inflicted weariness: " + actorFactionId + " +"
                        + (actorFp * involvement * wearinessPerFpDestroyed) + " (" + actorFp
                        + " FP), " + victimFactionId + " +"
                        + (victimFp * involvement * wearinessPerFpDestroyed) + " (" + victimFp
                        + " FP); involvement=" + involvement);
        return newSeed;
    }

    /**
     * The raided side pays for dead attackers (sec 4.7).
     *
     * <p>Strictly the victim faction paying for losses among THIS operation's fleet, scaled by
     * hull size and the player's share of the fighting, plus {@code SYSTEM_BOUNTY_REWARD}
     * reputation with that side. Vanilla pays nothing at all for raid defence, which is why this
     * exists (pillar 4, heroism).
     *
     * <p><b>No payout when the operation's fleet fought ON the player's side.</b> Without that
     * guard a player who joins the attackers collects credits and reputation from the victim
     * faction for allied ships the DEFENDERS killed -- the exploit Sean closed on 2026-08-27, and
     * this is the first theatre where both sides are joinable.
     *
     * <p>Credits and reputation are applied here; the caller owns only the deferred payout NOTICE,
     * because the two callers present it differently.
     *
     * @return the payout to queue for the notice, or null if nothing was paid.
     */
    public static BountyResult payRaidedSideBounty(CampaignFleetAPI fleet, BattleAPI battle,
                                                   String actorFactionId, String victimFactionId,
                                                   float bountyPerFrigate, Class logClass) {
        if (bountyPerFrigate <= 0f) return null;
        if (fleet == null || battle == null) return null;
        if (battle.isOnPlayerSide(fleet)) return null;

        float bounty = 0f;
        float fpDestroyed = 0f;
        List lost = Misc.getSnapshotMembersLost(fleet);
        for (int i = 0; i < lost.size(); i++) {
            FleetMemberAPI loss = (FleetMemberAPI) lost.get(i);
            bounty += Misc.getSizeNum(loss.getHullSpec().getHullSize()) * bountyPerFrigate;
            fpDestroyed += loss.getFleetPointCost();
        }

        FactionAPI paying = victimFactionId != null
                ? Global.getSector().getFaction(victimFactionId) : null;
        if (paying == null) return null;

        int payment = (int) (bounty * battle.getPlayerInvolvementFraction());
        if (payment <= 0) return null;

        Global.getSector().getPlayerFleet().getCargo().getCredits().add(payment);

        float repFP = (int) (fpDestroyed * battle.getPlayerInvolvementFraction());
        ReputationAdjustmentResult rep = Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(RepActions.SYSTEM_BOUNTY_REWARD, new Float(repFP),
                        null, null, true, false),
                victimFactionId);

        Global.getLogger(logClass).info(
                "Exiles Heg-League raider bounty: " + payment + "c from " + victimFactionId
                        + " (actor=" + actorFactionId + ", fpDestroyed=" + fpDestroyed
                        + ", involvement=" + battle.getPlayerInvolvementFraction() + ")");

        return new BountyResult(payment, battle.getPlayerInvolvementFraction(), rep);
    }

    // ------------------------------------------------------------------
    // "BREAK THE BLOCKADE" victory bonus (war doc sec 14.15, ratified 2026-09-28 in 14.15.11).
    // Two callers with identical needs -- the system blockade and the planetary squeeze -- so the
    // arithmetic lives here beside the per-kill bounty it tops up. State (the per-op ledger) stays
    // with each caller as saved primitives, exactly as the bounty queue and weariness guard do.
    // ------------------------------------------------------------------

    /** THIS fleet's fleet points lost in the battle just reported. Same accounting as the
     *  per-kill bounty ({@code Misc.getSnapshotMembersLost}), so the two can never disagree. */
    public static float fleetFpLost(CampaignFleetAPI fleet) {
        if (fleet == null) return 0f;
        List lost = Misc.getSnapshotMembersLost(fleet);
        if (lost == null) return 0f;
        float fp = 0f;
        for (int i = 0; i < lost.size(); i++) {
            FleetMemberAPI loss = (FleetMemberAPI) lost.get(i);
            if (loss == null) continue;
            fp += loss.getFleetPointCost();
        }
        return fp;
    }

    /**
     * The share of THIS fleet's losses in this battle credited to the player: vanilla's
     * {@code battle.getPlayerInvolvementFraction()} when the player fought AGAINST the fleet, zero
     * otherwise (not involved, or fighting on the op's own side -- the exploit guard the bounty
     * already applies). The caller multiplies it by {@link #fleetFpLost}.
     */
    public static float playerCreditFraction(CampaignFleetAPI fleet, BattleAPI battle) {
        if (fleet == null || battle == null) return 0f;
        if (!battle.isPlayerInvolved()) return 0f;
        if (battle.isOnPlayerSide(fleet)) return 0f;
        float f = battle.getPlayerInvolvementFraction();
        if (f < 0f) return 0f;
        if (f > 1f) return 1f;
        return f;
    }

    /**
     * Pay the victory bonus for an operation that has just been BROKEN (driven off through losses),
     * if the player contributed enough. Credits and victim reputation are applied here; the
     * permanent log line is written whatever the result, so a tester can see why nothing was paid.
     *
     * <p>Rules (sec 14.15.11): share = playerFp / totalLostFp at the moment of the break; below
     * {@code shareFloor} nothing; from the floor to {@code shareFull} the payout is pro-rated as
     * {@code share / shareFull} (so the floor pays 40% at the default 0.2/0.5, not a near-zero
     * sliver); at or above {@code shareFull} it is paid in full. Size scales the base by
     * {@code sizeRatio}, clamped to [0.5, maxSizeMult]. No transponder condition (ruled).
     * Reputation uses vanilla's own actions: COMBAT_HELP_MAJOR for a partial share,
     * COMBAT_HELP_CRITICAL for a full one.
     *
     * @return credits paid (0 if nothing was paid).
     */
    public static int payVictoryBonus(ExileHegLeagueConfig config, String opNoun, String systemName,
                                      String actorFactionId, String victimFactionId,
                                      float base, float sizeRatio,
                                      float playerFp, float totalLostFp, Class logClass) {
        if (config == null || !config.victoryBonusEnabled || base <= 0f) return 0;

        float share = totalLostFp > 0f ? playerFp / totalLostFp : 0f;
        if (share > 1f) share = 1f;

        float fraction;
        if (share < config.victoryBonusShareFloor) {
            fraction = 0f;
        } else if (share >= config.victoryBonusShareFull || config.victoryBonusShareFull <= 0f) {
            fraction = 1f;
        } else {
            fraction = share / config.victoryBonusShareFull;
        }

        float size = sizeRatio;
        if (size < 0.5f) size = 0.5f;
        if (size > config.victoryBonusMaxSizeMult) size = config.victoryBonusMaxSizeMult;

        int payment = (int) (base * size * fraction);
        FactionAPI paying = victimFactionId != null
                ? Global.getSector().getFaction(victimFactionId) : null;

        String head = "Exiles Heg-League VICTORY BONUS: " + opNoun + " in " + systemName
                + " BROKEN | player " + (int) playerFp + " of " + (int) totalLostFp + " FP lost"
                + " (share " + (Math.round(share * 100f)) + "%)";
        if (payment <= 0 || paying == null) {
            Global.getLogger(logClass).info(head + " -> no bonus (below "
                    + (Math.round(config.victoryBonusShareFloor * 100f)) + "% floor)"
                    + " | actor=" + actorFactionId + " victim=" + victimFactionId);
            return 0;
        }

        Global.getSector().getPlayerFleet().getCargo().getCredits().add(payment);
        RepActions action = fraction >= 1f ? RepActions.COMBAT_HELP_CRITICAL
                : RepActions.COMBAT_HELP_MAJOR;
        Global.getSector().adjustPlayerReputation(
                new RepActionEnvelope(action, null, null, null, true, false), victimFactionId);

        Global.getSector().getCampaignUI().addMessage(
                Misc.getDGSCredits(payment) + " received from " + paying.getDisplayNameWithArticle()
                        + " for helping break the " + opNoun + " of " + systemName,
                Misc.getPositiveHighlightColor());

        Global.getLogger(logClass).info(head + " -> paid " + payment + "c (base " + (int) base
                + " x size " + (Math.round(size * 100f) / 100f) + " x fraction "
                + (Math.round(fraction * 100f) / 100f) + "), rep " + action
                + " | actor=" + actorFactionId + " victim=" + victimFactionId);
        return payment;
    }
}
