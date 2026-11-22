package data.scripts.exiles;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.HasslePlayerScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.util.Misc;

/**
 * Hegemony-Persean League PLAYER STOPS -- PASS 8a-1 of docs/EXILES_PASS8_PLAN.md (sec 11).
 *
 * <p><b>What this is.</b> A stateless helper. While a war op is in its action window, the op's
 * own stage tick calls {@link #refresh} and this class marks the op's live fleets so that
 * VANILLA's {@code HasslePlayerScript} will send one of them after the player. The dialogue is
 * rules.csv (ids {@code exileHLStop*}), vanilla rule commands only. Nothing here pursues, hails or
 * talks; vanilla does all three.
 *
 * <p><b>Why the marks expire.</b> Every key is written with a {@link #FLAG_DAYS} expiry and
 * renewed by the tick, exactly as vanilla's {@code PerseanLeagueBlockade} does. A mark therefore
 * cannot outlive its fleet or its op by more than two days, with no clean-up code (Ashen Ruling F,
 * mitigation 1).
 *
 * <p><b>Hostile players get no dialogue.</b> {@code HasslePlayerScript} skips any fleet hostile
 * to the player, so an Exile-start player (League HOSTILE, Hegemony VENGEFUL) is simply attacked,
 * as before, until a relation is repaired to INHOSPITABLE or better. Ruled acceptable 2026-11-16.
 *
 * <p><b>Config.</b> The statics below are pushed from {@code exilehegleague.playerInterception}
 * by {@link #configure} when the director loads its config -- the same arrangement the stages use
 * for their own tunables, because a saved stage holds no config reference.
 */
public class ExileHegLeaguePlayerStop {

    /** Quest/console override of {@code playerInterception.enabled} (sibling convention). */
    public static final String ENABLED_KEY = "$exiles_hl_interception_enabled";

    /** Vanilla hassle types. One per op kind, so each has its own sector-wide cooldown. */
    public static final String TYPE_INSPECT = "exile_hl_inspect";
    public static final String TYPE_CORDON = "exile_hl_cordon_stop";

    /** The dialogue's two voices: an unsanctioned shakedown, or an official fee. */
    public static final String KIND_RAIDER = "raider";
    public static final String KIND_OFFICIAL = "official";

    // --- fleet memory, written here, read by rules.csv ---
    public static final String STOP_KEY = "$exiles_hl_stop";
    public static final String KIND_KEY = "$exiles_hl_stopKind";
    public static final String TOLL_KEY = "$exiles_hl_toll";
    public static final String TOLL_HALF_KEY = "$exiles_hl_tollHalf";
    public static final String TOLL_DOUBLE_KEY = "$exiles_hl_tollDouble";
    public static final String SUPPLIES_KEY = "$exiles_hl_supplies";
    public static final String SUPPLIES_HALF_KEY = "$exiles_hl_suppliesHalf";
    public static final String SUPPLIES_DOUBLE_KEY = "$exiles_hl_suppliesDouble";
    public static final String LIE_CHANCE_KEY = "$exiles_hl_lieChance";
    public static final String CR_WEIGHT_KEY = "$exiles_hl_crWeight";
    public static final String REP_LIE_KEY = "$exiles_hl_repLie";
    public static final String REP_FACE_DOWN_KEY = "$exiles_hl_repFaceDown";
    public static final String REP_FLOOR_KEY = "$exiles_hl_repFloor";
    public static final String LOW_IMPACT_DAYS_KEY = "$exiles_hl_lowImpactDays";
    public static final String COMMISSIONED_KEY = "$exiles_hl_commissioned";
    /** Suffix for the display copy of a credit figure ("4,000"). */
    public static final String DGS = "DGS";

    // --- fleet memory, written by rules.csv, read back here for the log ---
    public static final String OUTCOME_KEY = "$exiles_hl_stopOutcome";
    public static final String PAID_KEY = "$exiles_hl_stopPaid";

    // --- FACTION memory, read by rules.csv only. Nothing in v1 sets either except the test pins. ---
    /** A quest-granted status (Sean, 2026-11-16: "a good quest lever"). */
    public static final String OFFICIAL_BUSINESS_KEY = "$exiles_hl_officialBusiness";
    /** Bought authorization codes. Deliberately NOT {@code hl}-scoped: the idea reaches beyond
     *  these encounters and a later system should not have to rename the flag. */
    public static final String AUTH_CODES_KEY = "$exiles_authCodes";

    public static float FLAG_DAYS = 2f;

    // --- config (exilehegleague.playerInterception), pushed by configure() ---
    public static boolean enabled = true;
    public static float tollPerFleetPoint = 200f;
    public static float tollFloor = 2000f;
    public static float tollCap = 50000f;
    public static float tollMultHegemony = 1f;
    public static float tollMultPersean = 1f;
    public static float negotiateMult = 0.5f;
    public static float caughtLieMult = 2f;
    public static float creditsPerSupply = 150f;
    public static float lieBaseChance = 0.5f;
    public static float lieRepShift = 0.5f;
    public static float lieMinChance = 0.2f;
    public static float lieMaxChance = 0.8f;
    public static float repCaughtLie = 2f;
    public static float repFacedDown = 3f;
    public static String repFloor = "INHOSPITABLE";
    public static float inspectionWeightInspect = 0.2f;
    public static float inspectionWeightCordon = 0.3f;
    public static float raiderLowRepImpactDays = 2f;

    /** {@code testing.stopDiagnosticsDays}: days between STOP DIAG lines per op; 0 = off
     *  (production). See {@link #logDiagnostics}. */
    public static float diagnosticsDays = 0f;
    /** Fleets written out per STOP DIAG line; any more are only counted. */
    public static int DIAG_MAX_FLEETS = 8;

    public static void configure(ExileHegLeagueConfig c) {
        if (c == null) return;
        diagnosticsDays = c.testStopDiagnosticsDays;
        enabled = c.stopEnabled;
        tollPerFleetPoint = c.stopTollPerFleetPoint;
        tollFloor = c.stopTollFloor;
        tollCap = c.stopTollCap;
        tollMultHegemony = c.stopTollMultHegemony;
        tollMultPersean = c.stopTollMultPersean;
        negotiateMult = c.stopNegotiateMult;
        caughtLieMult = c.stopCaughtLieMult;
        creditsPerSupply = c.stopCreditsPerSupply;
        lieBaseChance = c.stopLieBaseChance;
        lieRepShift = c.stopLieRepShift;
        lieMinChance = c.stopLieMinChance;
        lieMaxChance = c.stopLieMaxChance;
        repCaughtLie = c.stopRepCaughtLie;
        repFacedDown = c.stopRepFacedDown;
        repFloor = c.stopRepFloor;
        inspectionWeightInspect = c.stopInspectionWeightInspect;
        inspectionWeightCordon = c.stopInspectionWeightCordon;
        raiderLowRepImpactDays = c.stopRaiderLowRepImpactDays;
    }

    public static boolean isEnabled() {
        return ExileHegLeagueDirector.switchEnabled(ENABLED_KEY, enabled);
    }

    /**
     * Log any finished stop on this op's fleets, then renew the marks if the player is here.
     *
     * <p>Called from the op's stage tick while the stage is ONGOING, and only then: the return
     * leg is never marked. The outcome pass is one memory lookup per live fleet; the marking pass
     * runs only while the player is in the op's system, so an op the player never visits costs
     * nothing beyond the route walk.
     *
     * @param markNow false = outcome pass only (the caller's refresh interval has not elapsed)
     * @param logMarks true = log one STOP MARKS line if any fleet is marked (the caller asks for
     *                 this once per op per game load, so the test read can tell "never marked"
     *                 from "marked but never caught the player")
     * @return true if at least one fleet was marked on this call
     */
    public static boolean refresh(RaidIntel intel, StarSystemAPI system, String stopType,
                                  String kind, String actorFactionId, boolean markNow,
                                  boolean logMarks) {
        if (intel == null || system == null || actorFactionId == null) return false;

        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        boolean mark = markNow && player != null && player.getContainingLocation() == system
                && isEnabled();

        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        if (routes == null || routes.isEmpty()) return false;

        int marked = 0;
        boolean figuresReady = false;
        int toll = 0;
        int tollHalf = 0;
        int tollDouble = 0;
        float lieChance = 0f;
        boolean commissioned = false;

        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null || !fleet.isAlive()) continue;

            collectOutcome(fleet, stopType, system, actorFactionId);

            if (!mark) continue;
            if (fleet.isHostileTo(player)) continue; // vanilla would skip it anyway

            if (!figuresReady) {
                figuresReady = true;
                toll = computeToll(player, actorFactionId);
                tollHalf = roundCredits(toll * negotiateMult);
                tollDouble = roundCredits(toll * caughtLieMult);
                lieChance = computeLieChance(actorFactionId);
                commissioned = actorFactionId.equals(Misc.getCommissionFactionId());
            }

            MemoryAPI mem = fleet.getMemoryWithoutUpdate();
            mem.set(MemFlags.WILL_HASSLE_PLAYER, true, FLAG_DAYS);
            mem.set(MemFlags.HASSLE_TYPE, stopType, FLAG_DAYS);
            mem.set(STOP_KEY, true, FLAG_DAYS);
            mem.set(KIND_KEY, kind, FLAG_DAYS);
            setCredits(mem, TOLL_KEY, toll);
            setCredits(mem, TOLL_HALF_KEY, tollHalf);
            setCredits(mem, TOLL_DOUBLE_KEY, tollDouble);
            mem.set(SUPPLIES_KEY, supplies(toll), FLAG_DAYS);
            mem.set(SUPPLIES_HALF_KEY, supplies(tollHalf), FLAG_DAYS);
            mem.set(SUPPLIES_DOUBLE_KEY, supplies(tollDouble), FLAG_DAYS);
            mem.set(LIE_CHANCE_KEY, lieChance, FLAG_DAYS);
            mem.set(CR_WEIGHT_KEY, TYPE_CORDON.equals(stopType)
                    ? inspectionWeightCordon : inspectionWeightInspect, FLAG_DAYS);
            // Negative on purpose: the rule passes these straight to AdjustRep, and a "-$var"
            // token is only understood by AddRemoveCommodity.
            mem.set(REP_LIE_KEY, -Math.abs(repCaughtLie), FLAG_DAYS);
            mem.set(REP_FACE_DOWN_KEY, -Math.abs(repFacedDown), FLAG_DAYS);
            mem.set(REP_FLOOR_KEY, repFloor, FLAG_DAYS);
            mem.set(LOW_IMPACT_DAYS_KEY, raiderLowRepImpactDays, FLAG_DAYS);
            if (commissioned) {
                mem.set(COMMISSIONED_KEY, true, FLAG_DAYS);
            } else {
                mem.unset(COMMISSIONED_KEY);
            }
            marked++;
        }

        if (marked > 0 && logMarks) {
            Global.getLogger(ExileHegLeaguePlayerStop.class).info(
                    "Exiles Heg-League STOP MARKS: "
                            + (TYPE_CORDON.equals(stopType) ? "cordon" : "inspect")
                            + " " + system.getBaseName()
                            + " actor=" + actorFactionId
                            + " fleets=" + marked
                            + " toll=" + toll
                            + " lieChance=" + (Math.round(lieChance * 100f) / 100f)
                            + " commissioned=" + commissioned);
        }
        return marked > 0;
    }

    /** THE MEASUREMENT LINE. The rules write the option the player took; this logs it once. */
    protected static void collectOutcome(CampaignFleetAPI fleet, String stopType,
                                         StarSystemAPI system, String actorFactionId) {
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        if (!mem.contains(OUTCOME_KEY)) return;
        String outcome = mem.getString(OUTCOME_KEY);
        int paid = 0;
        if (mem.contains(PAID_KEY)) {
            // Written by a rule as a copy of another variable, so its stored type is the rule
            // engine's business; a bad read must never cost the log line.
            try {
                paid = (int) mem.getFloat(PAID_KEY);
            } catch (Exception e) {
                paid = -1;
            }
        }
        mem.unset(OUTCOME_KEY);
        mem.unset(PAID_KEY);
        Global.getLogger(ExileHegLeaguePlayerStop.class).info(
                "Exiles Heg-League STOP: " + (TYPE_CORDON.equals(stopType) ? "cordon" : "inspect")
                        + " " + system.getBaseName()
                        + " actor=" + actorFactionId
                        + " outcome=" + outcome
                        // A payment in kind records units of supplies, not credits (2026-11-22:
                        // the first one in play logged "toll=0" because nothing was recorded).
                        + ("paid_supplies".equals(outcome) ? " supplies=" : " toll=") + paid);
    }

    /**
     * STOP DIAG -- a TEST measurement (2026-11-17), off unless {@code testing.stopDiagnosticsDays}
     * is above 0. One line per op per interval, and only while the player and at least one of the
     * op's live fleets are in the op's system.
     *
     * <p><b>Why it exists.</b> In the first 8a-1 run six League raiders, marked to stop the
     * player, flew past him at Arcadia straight at the Citadel and were destroyed in four days.
     * The log had nothing on vanilla's side of a stop, nor on what a raider was doing. This line
     * records, per fleet, its current order, what its AI is chasing, its distance to the focus
     * market and to the player, whether it can see the player, and the flags vanilla's
     * {@code HasslePlayerScript} filters on -- so "never marked", "marked but busy chasing
     * something", "sent after the player and ignored it" and "out of range" can be told apart.
     *
     * <p>Called from the intel, not the stage, so it also covers the approach and the wait at
     * the jump point, when no fleet is marked yet. Read-only: it changes nothing on any fleet.
     *
     * @param opLabel "interdiction" or "squeeze"
     * @param phase   "approach", "action" or "return"
     */
    public static void logDiagnostics(RaidIntel intel, StarSystemAPI system, String opLabel,
                                      String phase, String focusMarketId) {
        if (intel == null || system == null) return;
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null || player.getContainingLocation() != system) return;

        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        if (routes == null || routes.isEmpty()) return;

        SectorEntityToken focus = null;
        CampaignFleetAPI station = null;
        if (focusMarketId != null) {
            MarketAPI market = Global.getSector().getEconomy().getMarket(focusMarketId);
            if (market != null) {
                focus = market.getPrimaryEntity();
                station = Misc.getStationFleet(market);
            }
        }

        StringBuilder fleets = new StringBuilder();
        int here = 0;
        int elsewhere = 0;
        int listed = 0;
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (fleet == null || !fleet.isAlive()) continue;
            if (fleet.getContainingLocation() != system) {
                elsewhere++;
                continue;
            }
            here++;
            if (listed >= DIAG_MAX_FLEETS) continue;
            listed++;
            appendFleetDiagnostics(fleets, fleet, player, focus, station);
        }
        if (here == 0) return;

        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        Global.getLogger(ExileHegLeaguePlayerStop.class).info(
                "Exiles Heg-League STOP DIAG: " + opLabel + " " + system.getBaseName()
                        + " phase=" + phase
                        + " routes=" + routes.size() + " here=" + here + " elsewhere=" + elsewhere
                        + " cooldown[inspect=" + cooldownText(global, TYPE_INSPECT)
                        + " cordon=" + cooldownText(global, TYPE_CORDON) + "]"
                        + " transponder=" + (player.isTransponderOn() ? "on" : "off")
                        + " focus=" + (focus != null ? focus.getName() : "-")
                        + fleets.toString());
    }

    /** One fleet's part of the STOP DIAG line. */
    protected static void appendFleetDiagnostics(StringBuilder sb, CampaignFleetAPI fleet,
                                                 CampaignFleetAPI player, SectorEntityToken focus,
                                                 CampaignFleetAPI station) {
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        sb.append(" || ").append(fleet.getName()).append(" ").append(fleet.getFleetPoints())
                .append("fp");

        FleetAssignmentDataAPI order = fleet.getCurrentAssignment();
        sb.append(" order=");
        if (order == null) {
            sb.append("none");
        } else {
            sb.append(String.valueOf(order.getAssignment())).append(">")
                    .append(diagName(order.getTarget(), player));
        }

        SectorEntityToken chasing = null;
        boolean fleeing = false;
        boolean stationOffLimits = false;
        if (fleet.getAI() instanceof ModularFleetAIAPI) {
            ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
            if (ai.getTacticalModule() != null) {
                chasing = ai.getTacticalModule().getTarget();
            }
            // The fleet AI's own answer, not the tactical module's: this is the exact call
            // vanilla's HasslePlayerScript filter makes (corrected 2026-11-18).
            fleeing = ai.isFleeing();
            if (station != null && ai.getStrategicModule() != null) {
                stationOffLimits = ai.getStrategicModule().getDoNotAttack().contains(station);
            }
        }
        sb.append(" chasing=").append(diagName(chasing, player));
        sb.append(" contact=").append(diagName(fleet.getInteractionTarget(), player));
        if (focus != null && focus.getContainingLocation() == fleet.getContainingLocation()) {
            sb.append(" dFocus=").append(
                    (int) Misc.getDistance(fleet.getLocation(), focus.getLocation()));
        }
        sb.append(" dPlayer=").append(
                (int) Misc.getDistance(fleet.getLocation(), player.getLocation()));
        // Same call, same direction, as vanilla's HasslePlayerScript filter.
        sb.append(" seesPlayer=").append(String.valueOf(player.getVisibilityLevelTo(fleet)));

        sb.append(" flags=");
        int before = sb.length();
        if (mem.getBoolean(MemFlags.WILL_HASSLE_PLAYER)) sb.append("marked,");
        if (mem.getBoolean(MemFlags.MEMORY_KEY_PURSUE_PLAYER)) sb.append("pursuingPlayer,");
        if (mem.getBoolean(MemFlags.FLEET_BUSY)) sb.append("busy,");
        if (mem.getBoolean(MemFlags.FLEET_SPECIAL_ACTION)) sb.append("specialAction,");
        if (mem.getBoolean(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED)) {
            sb.append("noSidetrack,");
        }
        if (fleet.getBattle() != null) sb.append("inBattle,");
        if (fleeing) sb.append("fleeing,");
        if (fleet.isHostileTo(player)) sb.append("hostileToPlayer,");
        if (stationOffLimits) sb.append("stationOffLimits,");
        if (sb.length() == before) {
            sb.append("none");
        } else {
            sb.setLength(sb.length() - 1);
        }
    }

    /** A short name for whatever a fleet is ordered to, chasing or touching. */
    protected static String diagName(SectorEntityToken entity, CampaignFleetAPI player) {
        if (entity == null) return "-";
        if (entity == player) return "PLAYER";
        if (entity instanceof CampaignFleetAPI) {
            CampaignFleetAPI other = (CampaignFleetAPI) entity;
            String faction = other.getFaction() != null ? other.getFaction().getId() : "?";
            if (other.isStationMode()) return "STATION(" + faction + ")";
            return other.getName() + "(" + faction + " " + other.getFleetPoints() + "fp)";
        }
        String name = entity.getName();
        return name != null ? name : "unnamed";
    }

    /** Days left on vanilla's sector-wide cooldown for one stop type, or "none". A cooldown is
     *  only ever started by a successful hassle roll, so "none" all through a watched op means
     *  vanilla never sent a fleet after the player. */
    protected static String cooldownText(MemoryAPI global, String stopType) {
        String key = HasslePlayerScript.HASSLE_TIMEOUT_KEY + "_" + stopType;
        if (!global.contains(key)) return "none";
        return (Math.round(global.getExpire(key) * 10f) / 10f) + "d";
    }

    /** Player fleet points x rate x the side's multiplier, clamped (default D5: vanilla's Pather
     *  tithe rate). */
    protected static int computeToll(CampaignFleetAPI player, String actorFactionId) {
        float mult = ExileHegLeagueConfig.HEGEMONY_ID.equals(actorFactionId)
                ? tollMultHegemony : tollMultPersean;
        float toll = player.getFleetPoints() * tollPerFleetPoint * mult;
        if (toll < tollFloor) toll = tollFloor;
        if (tollCap > 0f && toll > tollCap) toll = tollCap;
        return roundCredits(toll);
    }

    /** base + standing x shift, clamped. Standing runs -1..1, so INHOSPITABLE is about 35%,
     *  neutral 50% and WELCOMING about 65% at the shipped values. */
    protected static float computeLieChance(String actorFactionId) {
        FactionAPI actor = Global.getSector().getFaction(actorFactionId);
        float rel = actor != null ? actor.getRelToPlayer().getRel() : 0f;
        float chance = lieBaseChance + rel * lieRepShift;
        if (chance < lieMinChance) chance = lieMinChance;
        if (chance > lieMaxChance) chance = lieMaxChance;
        return chance;
    }

    protected static int roundCredits(float value) {
        return Math.round(value / 100f) * 100;
    }

    protected static int supplies(int credits) {
        if (creditsPerSupply <= 0f) return 0;
        return (int) Math.ceil(credits / creditsPerSupply);
    }

    protected static void setCredits(MemoryAPI mem, String key, int credits) {
        mem.set(key, credits, FLAG_DAYS);
        mem.set(key + DGS, Misc.getWithDGS(credits), FLAG_DAYS);
    }

    /**
     * TEST PINS ({@code testing.grantAuthCodes} / {@code testing.grantOfficialBusiness}): set the
     * faction flag for 30 days. Called once per game load by the director. Exists because the
     * flags have no setter in v1 and Console Commands may not be available on 0.98.5a.
     */
    public static void applyTestGrants(ExileHegLeagueConfig c) {
        if (c == null) return;
        grant(c.testGrantAuthCodes, AUTH_CODES_KEY);
        grant(c.testGrantOfficialBusiness, OFFICIAL_BUSINESS_KEY);
        if (c.testStopDiagnosticsDays > 0f) {
            Global.getLogger(ExileHegLeaguePlayerStop.class).info(
                    "Exiles Heg-League TEST PIN: stopDiagnosticsDays " + c.testStopDiagnosticsDays
                            + " -- STOP DIAG lines are on");
        }
    }

    protected static void grant(String factionId, String key) {
        if (factionId == null || factionId.length() == 0) return;
        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (faction == null) {
            Global.getLogger(ExileHegLeaguePlayerStop.class).warn(
                    "Exiles Heg-League TEST PIN: no faction \"" + factionId + "\" for " + key);
            return;
        }
        faction.getMemoryWithoutUpdate().set(key, true, 30f);
        Global.getLogger(ExileHegLeaguePlayerStop.class).info(
                "Exiles Heg-League TEST PIN: " + key + " set on " + factionId + " for 30 days");
    }
}
