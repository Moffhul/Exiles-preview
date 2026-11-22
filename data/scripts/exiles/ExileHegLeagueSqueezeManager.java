package data.scripts.exiles;

import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.scripts.marketconditions.ExileHegLeaguePlanetaryBlockade;

/**
 * Hegemony-Persean League PLANETARY BLOCKADES, "the squeeze" -- PASS 6 of
 * docs/EXILES_HEG_LEAGUE_WAR.md sec 4.4 (sec 12 row 6; geography sec 2/R6, guardrails sec 5,
 * bounty sec 4.7).
 *
 * <p><b>This is the RETALIATION state's OTHER punitive option.</b> Pass 3 gave the wronged side
 * one answer -- a surgical strike on a military industry. sec 3.2.2 state 3 and sec 4.4 name a
 * second: park a cordon over one of the offender's colonies and strangle it. Which one a given
 * cycle produces is a weighted roll between {@code opWeights.surgical} and
 * {@code opWeights.planetary} (D80.1), so the Hegemony mostly takes a shipyard offline and the
 * League mostly shuts a port -- the same state machine reading as two different kinds of anger,
 * with no forked code anywhere.
 *
 * <p><b>NOT an autonomous cadence manager, and NOT an {@code EveryFrameScript}.</b> It is
 * stateless plumbing with one entry point, {@link #launchSqueeze}, called by
 * {@code ExileHegLeagueStrikeManager}'s retaliation path -- which is where the "exactly one
 * punitive op per crisis cycle" invariant lives and must stay. There is deliberately no ESCALATION
 * squeeze cadence in v1 (D80.1c): ESCALATION already runs interdictions, counter-strikes and up to
 * two blockades per side, and a fourth concurrent op type is the dogpile the guardrails exist to
 * prevent. {@code planetaryBlockade.escalationSqueezes} ships false and is read by nothing yet.
 *
 * <p><b>The feasibility gate INCLUDES the anchor's station strength</b> -- the STRIKE gate, not
 * interdiction's. D80.2 excluded it; Sean REVERSED that on 2026-09-20 (late): the squeeze fights
 * the station, so the gate must ask whether it can win that fight. Measured per anchor market,
 * the Pass-3 fix's shape. Expect the size-6 anchors (Fikenhild, Madeira, Jangala, Yama) to be
 * reachable only at the top of the tension-scaled band -- read the feasibility lines before
 * tuning {@code feasibilityMult}. A private copy of the idiom, per ruling S4 -- <b>do not extract a
 * shared helper</b> (rejected on the merits, Ashen doc sec 14.6: the differences between
 * directors' gates are playtest-earned rulings).
 *
 * <p><b>The target pool is a WHITELIST</b>, unlike interdiction's and the strike's: R6b's
 * {@code planetaryBlockadeWhitelistMarkets}, the author's own list of colonies it is acceptable to
 * squeeze. An empty list means no squeeze can launch anywhere. Ownership is still queried live
 * (R6c), so the list names acceptable targets, never owners.
 */
public class ExileHegLeagueSqueezeManager {

    /** Quest/runtime override, beating the config default (the sibling convention). */
    public static final String SQUEEZES_ENABLED_KEY = "$exiles_hl_squeeze_enabled";

    private ExileHegLeagueSqueezeManager() {
    }

    /** Config default, overridable at runtime by quests via the memory flag. */
    public static boolean squeezesEnabled(ExileHegLeagueConfig config) {
        return ExileHegLeagueDirector.switchEnabled(SQUEEZES_ENABLED_KEY,
                config != null && config.planetaryEnabled);
    }

    /**
     * Push the config's condition tunables onto the condition's statics -- the established sibling
     * arrangement, which is what makes the whole condition player-adjustable without touching
     * Java. Values arrive already timeScale'd by the config loader.
     *
     * <p><b>Called once per load from {@code ExileHegLeagueStrikeManager}'s tick</b>, not from
     * {@link #launchSqueeze}: a launch-time push would leave a condition restored from a save
     * reading the CODE defaults until the next squeeze happened to launch, and the condition's
     * {@code apply()} re-runs on every economy reapply. The strike manager is this op's only
     * launcher and is already a script with a tick, so it is the honest place for it.
     */
    public static void applyConditionTunables(ExileHegLeagueConfig config) {
        if (config == null) return;
        ExileHegLeaguePlanetaryBlockade.ACCESSIBILITY_PENALTY =
                config.planetaryAccessibilityPenalty;
        ExileHegLeaguePlanetaryBlockade.PLAYER_BUY_PRICE_MULT =
                config.planetaryPlayerBuyPriceMult;
        ExileHegLeaguePlanetaryBlockade.DURATION_DAYS = config.planetaryConditionExpiryDays;
        ExileHegLeagueSqueezeStage.SQUEEZE_CONDITION_APPLY_DAY =
                config.planetaryConditionApplyDay;
        // The squeeze's OWN flag (ruling 2026-09-20: it fights the station; interdiction does not),
        // so the two managers no longer write one shared static.
        ExileHegLeagueSqueezeStage.SUPPRESS_STATION_ATTACKS =
                config.planetarySuppressStationAttacks;
    }

    /**
     * Build and post one squeeze op. Mirrors {@code ExileHegLeagueStrikeManager.launchStrike}'s
     * signature and contract exactly, so the retaliation path can call either one.
     *
     * <p>Budget rolls BEFORE target selection, because the feasibility gate needs to know what
     * this op can plausibly take on (the Black Fleet's budget-blind picks suicided into defended
     * systems until that order was fixed -- do not reorder).
     *
     * <p><b>NOTE on {@code suppressStationAttacks}:</b> the squeeze has its own static,
     * {@code ExileHegLeagueSqueezeStage.SUPPRESS_STATION_ATTACKS}, separate from interdiction's
     * (ruling 2026-09-20: the squeeze fights its anchor's station, interdiction fights none). Since
     * 2026-10-04 it governs the ANCHOR's station only; every other station is always protected.
     *
     * @return true if an op was actually launched.
     */
    public static boolean launchSqueeze(ExileHegLeagueDirector director,
                                        ExileHegLeagueConfig config, String actorId,
                                        String targetId, String reason, Random random) {
        if (director == null || config == null) return false;
        if (!squeezesEnabled(config)) return false;
        if (random == null) random = new Random();

        FactionAPI actor = Global.getSector().getFaction(actorId);
        FactionAPI target = Global.getSector().getFaction(targetId);
        if (actor == null || target == null) return false;
        // Belt-and-braces so an op can never launch into a peace; the director idles on a lapsed
        // premise and B3 re-asserts hostility, but this is one comparison.
        if (!actor.isHostileTo(target)) return false;

        MarketAPI source = ExileHegLeagueGeography.pickSource(actorId, random);
        if (source == null) return false;

        float fp = rollFp(director, config, random);

        MarketAPI anchor = pickAnchor(config, actor, targetId, source, fp, random);
        if (anchor == null) return false;
        if (!(anchor.getContainingLocation() instanceof StarSystemAPI)) return false;
        StarSystemAPI system = (StarSystemAPI) anchor.getContainingLocation();

        ExileHegLeagueConfig.Side actorSide = config.sideFor(actorId);
        float presenceMult = actorSide != null ? actorSide.presenceDaysMult : 1f;
        // sec 3.2.3 asymmetry: the Hegemony's methodical ops sit longer, the League's are shorter.
        // Same stage, different numbers -- no forked code, ever.
        float presenceDays = Math.max(1f, config.planetaryPresenceDays * presenceMult);

        ExileHegLeagueSqueezeIntel raid = new ExileHegLeagueSqueezeIntel(
                system, actor, targetId, config.planetaryMaxShipSize,
                config.planetaryBountyPerFrigate, config.wearinessPerFpDestroyed,
                config.planetarySystemCooldownDays);
        raid.setLaunchFp(fp); // sec 14.15 victory-bonus size scaling
        if (!config.planetaryHiddenUntilArrival) {
            // The intel constructor hides the op; this is the only way to opt out of that.
            raid.setHidden(false);
        }

        // Gather at the jump point nearest the source.
        JumpPointAPI gather = null;
        float min = Float.MAX_VALUE;
        List points = source.getContainingLocation().getEntities(JumpPointAPI.class);
        for (int i = 0; i < points.size(); i++) {
            JumpPointAPI curr = (JumpPointAPI) points.get(i);
            float dist = Misc.getDistance(
                    source.getPrimaryEntity().getLocation(), curr.getLocation());
            if (dist < min) {
                min = dist;
                gather = curr;
            }
        }
        SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(
                actor, system.getCenter());
        if (gather == null || raidJump == null) return false;

        // Pass 2's assemble stage, reused as-is: the per-fleet FP cap and the PATROL_LARGE
        // suppression solve the same two problems here, and its isSourceKnown() override is
        // LOAD-BEARING (null pirate-base intel -> NPE in getArrowData; the 2026-08-27 crash).
        ExileHegLeagueInterdictionManager.ExileHegLeagueAssembleStage assemble =
                new ExileHegLeagueInterdictionManager.ExileHegLeagueAssembleStage(
                        raid, gather, config.planetaryMaxFleetFp);
        assemble.addSource(source);
        assemble.setSpawnFP(fp);
        assemble.setAbortFP(fp * config.planetaryAbortFraction);
        raid.addStage(assemble);

        TravelStage travel = new TravelStage(raid, gather, raidJump, false);
        travel.setAbortFP(fp * config.planetaryAbortFraction);
        raid.addStage(travel);

        ExileHegLeagueSqueezeStage action = new ExileHegLeagueSqueezeStage(
                raid, system, presenceDays, actorId, targetId, anchor.getId());
        action.setAbortFP(fp * config.planetaryAbortFraction);
        raid.addStage(action);
        // The base intel reads this field for isPresenceCompleted(); a squeeze stage IS an
        // interdiction stage, so the Pass-2 seam serves both without a second field.
        raid.setInterdictionStage(action);

        raid.addStage(new ReturnStage(raid));

        // Hidden until the cordon arrives -- the sibling convention (sec 3.4). The hiding comes
        // from the intel constructor's setHidden(true); the addIntel boolean is forceNoMessage
        // and only suppresses the notification.
        Global.getSector().getIntelManager().addIntel(raid, true);

        // *** THE MEASUREMENT LINE (sec 5 "instrument before tuning"). ***
        // Every FP number in this pass is a guess until this line has been read across a run. It
        // prints the op's FP, the measured live defense of the chosen system split into patrol
        // strength and the anchor's station strength (both counted by the gate since the
        // 2026-09-20 ruling), and the gate that admitted it. Tune
        // planetaryBlockade.spawnFpMin/Max and feasibilityMult FROM THIS, not from intuition --
        // the assaultFp:800 lesson.
        float enemy = WarSimScript.getEnemyStrength(actor, system);
        float station = ExileStationStrength.get(anchor.getFaction(), system,
                anchor.getPrimaryEntity());
        Global.getLogger(ExileHegLeagueSqueezeManager.class).info(
                "Exiles Heg-League SQUEEZE LAUNCHED: " + source.getName() + " -> "
                        + anchor.getName() + " in " + system.getNameWithLowercaseType()
                        + " | actor=" + actorId + " victim=" + targetId
                        + " | FP=" + (int) fp
                        + " defense=" + (int) (enemy + station)
                        + " (enemy=" + (int) enemy + " + station=" + (int) station + ")"
                        + " gate=" + (int) config.feasibilityGate(fp, source,
                                config.planetaryFeasibilityStrengthMult)
                        + " (" + config.describeGate(fp, source,
                                config.planetaryFeasibilityStrengthMult) + ")"
                        + " | maxShipSize=" + config.planetaryMaxShipSize
                        + " maxFleetFp=" + (int) config.planetaryMaxFleetFp
                        + " presenceDays=" + (int) presenceDays
                        + " | reason=" + reason
                        + " state=" + ExileHegLeagueDirector.getStateName(director.getState())
                        + " tension=" + director.getTension());

        director.debug("Heg-League " + reason + " (squeeze): " + actor.getDisplayName() + " -> "
                + anchor.getName() + " (" + (int) fp + " FP, " + (int) presenceDays + "d).");
        return true;
    }

    /**
     * "Sized by tension" (sec 3.2.2 state 3), the strike's formula verbatim: the base band is
     * rolled, then scaled by how far the meter has climbed from its floor toward its cap.
     *
     * <p>Flagged in D80.10 item 5: at the cap this produces a ~450-FP destroyer cordon, which is a
     * lot of hulls parked in one place. That is what measurement line 1 is for.
     */
    protected static float rollFp(ExileHegLeagueDirector director, ExileHegLeagueConfig config,
                                  Random random) {
        float fp = config.planetarySpawnFpMin
                + random.nextFloat() * (config.planetarySpawnFpMax - config.planetarySpawnFpMin);
        float span = config.tensionCap - config.tensionFloor;
        if (span > 0f && config.planetaryTensionFpScale > 0f) {
            float t = (director.getTension() - config.tensionFloor) / span;
            if (t < 0f) t = 0f;
            if (t > 1f) t = 1f;
            fp *= 1f + config.planetaryTensionFpScale * t;
        }
        return fp;
    }

    /**
     * The anchor pool: the author's whitelist, intersected live with everything the other ops
     * already respect, weighted by distance from the source.
     *
     * <p>Returns a MARKET, like the strike's picker and unlike interdiction's: a squeeze is aimed
     * at one colony, not at a system's approaches.
     */
    protected static MarketAPI pickAnchor(ExileHegLeagueConfig config, FactionAPI actor,
                                          String targetFactionId, MarketAPI source, float fp,
                                          Random random) {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);

        // Cross-director dogpile check (sec 2). Read-only shared STATE -- the reason the settled
        // architecture needs no central controller. Asked once per launch, never per frame.
        List busySystems = ExileLiveOps.getSystemsWithActiveRaids(null);

        // sec 14.14 Option A: STRENGTH-unit gate, converted with vanilla's
        // Misc.getAdjustedStrength(fp, source). Per source and budget only, so computed once per pass.
        boolean gateOn = config.planetaryFeasibilityStrengthMult > 0f;
        float gate = config.feasibilityGate(fp, source, config.planetaryFeasibilityStrengthMult);
        String gateHow = config.describeGate(fp, source, config.planetaryFeasibilityStrengthMult);

        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m.isHidden() || !m.isInEconomy()) continue;
            // R6b: THE WHITELIST, first and cheapest. An empty list means nowhere.
            if (!ExileHegLeagueGeography.isPlanetaryBlockadeWhitelisted(config, m)) continue;
            // LIVE ownership query (R6c) -- never a hardcoded list.
            if (!targetFactionId.equals(m.getFactionId())) continue;
            if (!m.getFaction().isHostileTo(actor)) continue;
            if (m.getPrimaryEntity() == null) continue;
            if (!(m.getContainingLocation() instanceof StarSystemAPI)) continue;

            StarSystemAPI system = (StarSystemAPI) m.getContainingLocation();

            // R6a: SYSTEM-level capital exemption, belt-and-braces over the whitelist (which
            // contains no capital market today, and must not start to by accident).
            if (ExileHegLeagueGeography.isExemptSystem(config, system)) continue;
            // Player colonies are code-exempt by the Exiles convention: no NPC director op is
            // ever pointed at a system the player holds a market in.
            if (!Misc.getMarketsInLocation(system, Factions.PLAYER).isEmpty()) continue;
            if (system.getMemoryWithoutUpdate().getBoolean(
                    ExileHegLeagueInterdictionManager.SYSTEM_COOLDOWN_KEY)) continue;
            if (busySystems.contains(system)) continue;
            if (hasLiveOpIn(system)) continue;

            // R6b strain markets, the refugee shield, the author allow-list and the
            // Battered/Emergency-Measures guard, all per market.
            if (!ExileHegLeagueGeography.isEligibleVictimMarket(config, m, targetFactionId)) {
                continue;
            }

            // COMMERCE-tier feasibility, own copy of the idiom (S4 -- no shared helper), and
            // STATION STRENGTH IS EXCLUDED (D80.2). getEnemyStrength sums LIVE patrol strength,
            // so it cannot be predicted offline -- hence the instrumentation. One line per
            // candidate MARKET, following the Pass-3 fix's shape, because two whitelist markets
            // can share a system and the tester needs to see both considered.
            // (Note: the "EXCLUDED" line above predates the 2026-09-20 ruling -- the gate below
            // INCLUDES the anchor's station, as the class javadoc says.)
            if (gateOn) {
                float enemy = WarSimScript.getEnemyStrength(actor, system);
                float station = ExileStationStrength.get(m.getFaction(), system,
                        m.getPrimaryEntity());
                float defense = enemy + station;
                if (defense > gate) {
                    Global.getLogger(ExileHegLeagueSqueezeManager.class).info(
                            "Exiles Heg-League squeeze feasibility: " + m.getName() + " in "
                                    + system.getBaseName() + " REJECTED (defense " + (int) defense + " = enemy "
                                    + (int) enemy + " + station " + (int) station
                                    + " > gate " + (int) gate + " = " + gateHow + "),"
                                    + " actor=" + actor.getId() + ", target=" + targetFactionId);
                    continue;
                }
                Global.getLogger(ExileHegLeagueSqueezeManager.class).info(
                        "Exiles Heg-League squeeze feasibility: " + m.getName() + " in "
                                + system.getBaseName() + " eligible (defense " + (int) defense + " = enemy "
                                + (int) enemy + " + station " + (int) station
                                + " <= gate " + (int) gate + " = " + gateHow + "),"
                                + " actor=" + actor.getId() + ", target=" + targetFactionId);
            }

            // R6c: closer is likelier, so ops concentrate where the powers' spaces meet without a
            // border ever being hardcoded.
            float distLY = Misc.getDistanceLY(
                    source.getPrimaryEntity().getLocationInHyperspace(),
                    system.getLocation());
            picker.add(m, 1f / (1f + distLY * config.distanceFalloff));
        }

        return (MarketAPI) picker.pick();
    }

    /**
     * True if one of THIS director's operations -- of any type -- is already live in the system.
     *
     * <p>A private copy of the sibling loop, for the same S4 reason the gates are private, and
     * with the same two scans: {@code ExileHegLeagueRaidIntel} covers interdictions, strikes AND
     * squeezes (the lookup returns subclasses), while blockades ride {@code BlockadeFGI} and need
     * their own.
     */
    protected static boolean hasLiveOpIn(StarSystemAPI system) {
        return ExileHegLeagueGeography.hasLiveOpIn(system); // delegate since 2026-11-08 (tier 3)
    }
}
