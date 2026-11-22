package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import data.scripts.marketconditions.ExileHegLeagueInterdiction;
import data.scripts.marketconditions.ExileHegLeaguePlanetaryBlockade;

/**
 * Hegemony-Persean League PLANETARY BLOCKADE action stage, "the squeeze" -- PASS 6 of
 * docs/EXILES_HEG_LEAGUE_WAR.md sec 4.4. <b>The fourth port of the presence-raid pattern</b>
 * ({@code BlackFleetCommerceRaidStage} -&gt; {@link AshenInterdictionStage} -&gt;
 * {@link ExileHegLeagueInterdictionStage} -&gt; here), and the first that is a SUBCLASS of a
 * sibling rather than a fresh copy: the parent was already parameterised on exactly the axes this
 * op needs (sec 14.11.1 item 1).
 *
 * <p><b>Three things differ from the parent, and nothing else.</b>
 * <ol>
 * <li>{@link #getLoiterToken()} returns the anchor market's primary entity UNCONDITIONALLY. A
 *     squeeze is defined by the cordon sitting over one colony; the parent's
 *     {@code loiterOnFocusMarket} switch is an interdiction tuning knob and is deliberately not
 *     consulted here.</li>
 * <li>{@link #applyConditions()} / {@link #removeConditions()} stamp
 *     {@code exile_hl_planetary_blockade} on THE ONE ANCHOR MARKET, rather than on every market
 *     the victim owns in the system (D80.10 item 3: "planetary" beside "system" means one
 *     colony).</li>
 * <li>{@link #getConditionApplyDay()} reads this op's own config knob, so the two presence ops can
 *     be tuned apart. See the parent's accessor for why that is a method and not a static.</li>
 * </ol>
 * Everything else -- {@link #canRaid} (always false: the cordon never touches a planet),
 * {@code updateRoutes()}, {@code updateStatus()}, {@code suppressStationTargeting()},
 * {@code isPresenceCompleted()} and the FP abort -- is <b>inherited untouched</b>.
 *
 * <p><b>What the fleets actually do -- RULED by Sean, 2026-09-20 (late), REVERSING D80.2.</b>
 * The design-doc sentence "attack station and fleets in orbit" is LITERAL here: the cordon holds
 * station over the anchor world, engages hostile fleets that come to it, and is free to engage
 * the orbital station too. Sean's reasoning: the Ashen blockade suppressed station attacks
 * because the Crusade had to hold its main strength for later and its blockade escorted the
 * Hussar station assault; the League/Hegemony squeeze has no such constraint. So
 * this class reads its OWN static ({@link #SUPPRESS_STATION_ATTACKS}, shipped FALSE) instead of the
 * parent's -- interdiction keeps its suppression untouched. "The station" is the ANCHOR's only:
 * every other station in the system stays on the do-not-attack list ({@link #isStationProtected},
 * fix of 2026-10-04 after the Tigra City kill). Consequences carried into the launcher: the feasibility gate now
 * INCLUDES the anchor's station strength (the strike gate, not interdiction's), and the size cap
 * and FP band moved to the strike's values (cruisers, 250-400) because a destroyer cordon cannot
 * crack a size-6 station. They still never make a ground action. Beating the station puts it
 * offline for vanilla's repair window, which is the squeeze in mechanics as well as prose.
 *
 * <p><b>The guard-failure log line.</b> Overlap with {@code exile_hl_interdiction} or a
 * Heg-League {@code Blockaded} is prevented at LAUNCH -- every manager in this director consults
 * {@code hasLiveOpIn(system)} and {@link ExileLiveOps}, and a squeeze intel is an
 * {@link ExileHegLeagueRaidIntel} so all of those scans already see it. Importing the Black
 * Fleet's "stronger shortage wins" rule would therefore be dead code guarding an unreachable
 * state. What ships instead is a permanent WARN if the anchor is nonetheless found already
 * carrying one of them: that is a guard failure and the tester needs to see it, not have it
 * silently smoothed over.
 *
 * <p><b>Both directions.</b> Like every op in this war, the same class serves Hegemony cordons
 * over League worlds and League cordons over Hegemony worlds; the faction ids are constructor
 * parameters and every string is composed from them.
 */
public class ExileHegLeagueSqueezeStage extends ExileHegLeagueInterdictionStage {

    /** Day of the presence at which the cordon starts turning traffic away. This op's OWN knob
     *  ({@code planetaryBlockade.conditionApplyDay}), pushed from config; the parent's static is
     *  interdiction's and is left alone. */
    public static float SQUEEZE_CONDITION_APPLY_DAY = 5f;
    /** Own flag, NOT the parent's static: the squeeze fights the station (ruling 2026-09-20), the
     *  interdiction never does. Pushed from {@code planetaryBlockade.suppressStationAttacks}. */
    public static boolean SUPPRESS_STATION_ATTACKS = false;

    /**
     * The single colony this cordon is parked over. Saved.
     *
     * <p>Kept as its own field rather than relying on the inherited {@code focusMarketId}: the two
     * happen to carry the same value today, but the parent's is documented as "the market named in
     * the intel", a display concern, and the day someone changes what interdiction names would
     * otherwise silently move which market a squeeze squeezes.
     */
    protected String anchorMarketId;

    public ExileHegLeagueSqueezeStage(RaidIntel raid, StarSystemAPI target, float presenceDays,
                                      String actorFactionId, String victimFactionId,
                                      String anchorMarketId) {
        // loiterOnFocusMarket true for the parent's benefit, though getLoiterToken() below no
        // longer consults it -- so a future parent refactor that starts reading the flag again
        // cannot quietly move the cordon to the system centre.
        super(raid, target, presenceDays, actorFactionId, victimFactionId, anchorMarketId, true);
        this.anchorMarketId = anchorMarketId;
    }

    public String getAnchorMarketId() {
        return anchorMarketId;
    }

    /** This op's own apply day. See the parent's accessor for why this is a method. */
    @Override
    protected float getConditionApplyDay() {
        return SQUEEZE_CONDITION_APPLY_DAY;
    }

    /**
     * ALWAYS on for the squeeze (2026-10-04 fix). The ruling "the squeeze fights the station" means
     * the ANCHOR's station -- the only one the launch gate and the arrival line count. Before this
     * fix, {@link #SUPPRESS_STATION_ATTACKS} false switched protection off for every station in the
     * system, and since vanilla's tally ignores stations the cordon charged whatever it could
     * reach: the Sphinx squeeze (anchor stationless) destroyed Tigra City's station ~Dec 8 c208,
     * an unbudgeted fight against a market it was never aimed at. Which stations are protected is
     * now decided per market in {@link #isStationProtected}.
     */
    @Override
    protected boolean isStationSuppressionEnabled() {
        return true;
    }

    /** Every station except the anchor's; the anchor's too when {@link #SUPPRESS_STATION_ATTACKS}
     *  is true (the hands-off cordon kept for comparison). */
    @Override
    protected boolean isStationProtected(MarketAPI market) {
        if (SUPPRESS_STATION_ATTACKS) return true;
        return market == null || !market.getId().equals(anchorMarketId);
    }

    /** The anchor market's orbit, always. Falls back to the system centre on exactly the parent's
     *  guards, so a market that decivilises, leaves the economy or somehow moves system mid-op
     *  cannot strand the routes. */
    @Override
    protected SectorEntityToken getLoiterToken() {
        MarketAPI anchor = getAnchorMarket();
        if (anchor == null || anchor.getPrimaryEntity() == null) return system.getCenter();
        if (anchor.getPrimaryEntity().getContainingLocation() != system) return system.getCenter();
        return anchor.getPrimaryEntity();
    }

    protected MarketAPI getAnchorMarket() {
        if (anchorMarketId == null) return null;
        return Global.getSector().getEconomy().getMarket(anchorMarketId);
    }

    /** The anchor's station counts against the cordon on arrival, exactly as the launch gate and
     *  the strike stage count it. */
    @Override
    protected float getArrivalStationStrength() {
        MarketAPI anchor = getAnchorMarket();
        if (anchor == null || anchor.getPrimaryEntity() == null || system == null) return 0f;
        return ExileStationStrength.get(anchor.getFaction(), system,
                anchor.getPrimaryEntity());
    }

    @Override
    protected String getOpNoun() {
        return "squeeze";
    }

    /** PASS 8a-1: the cordon has its own stop type, so its cooldown is separate from the raiders'. */
    @Override
    protected String getStopType() {
        return ExileHegLeaguePlayerStop.TYPE_CORDON;
    }

    /** OFF for the squeeze: the 2026-11-18 ruling was interdiction-only. A cordon holds one
     *  market's orbit and fights that market's station by design, so "keep clear of objectives
     *  under a station" is not obviously right for it and has not been measured. */
    @Override
    protected boolean isObjectiveKeepOutEnabled() {
        return false;
    }

    /** A cordon charges an official fee; it does not run a shakedown (ruled 2026-11-16). */
    @Override
    protected String getStopKind() {
        return ExileHegLeaguePlayerStop.KIND_OFFICIAL;
    }

    /**
     * The ANCHOR MARKET ONLY.
     *
     * <p>Every legality clause is re-checked here rather than trusted from launch time, exactly as
     * the parent does and for the same reason: an op outlives its own target selection by weeks,
     * and a market can go Battered, change hands or fall under the refugee shield while the cordon
     * is in transit.
     */
    @Override
    protected void applyConditions() {
        conditionsApplied = true;
        MarketAPI anchor = getAnchorMarket();
        if (anchor == null || anchor.isHidden() || !anchor.isInEconomy()
                || anchor.isPlayerOwned()
                || ExileProtectedFactions.isProtected(anchor)
                || victimFactionId == null || !victimFactionId.equals(anchor.getFactionId())
                || ExileUnrestGuard.isBattered(anchor)
                || ExileHegLeagueInterdictionManager.isExcludedMarket(anchorMarketId)) {
            Global.getLogger(ExileHegLeagueSqueezeStage.class).info(
                    "Exiles Heg-League squeeze: anchor " + anchorMarketId
                            + " is no longer a legal target on arrival; no condition applied"
                            + " (actor=" + actorFactionId + ", victim=" + victimFactionId + ")");
            return;
        }

        // GUARD FAILURE, not a tuning note (D80.4). Overlap is prevented at launch by the
        // cross-op scans; if it happens anyway, say so loudly and then proceed -- both conditions
        // have independent lifecycles and independent ids, so nothing breaks, but somebody needs
        // to go and find out how the scans were bypassed.
        if (anchor.hasCondition(ExileHegLeagueInterdiction.CONDITION_ID)
                || anchor.hasCondition(Conditions.BLOCKADED)) {
            Global.getLogger(ExileHegLeagueSqueezeStage.class).warn(
                    "Exiles Heg-League squeeze GUARD FAILURE: " + anchor.getName()
                            + " already carries "
                            + (anchor.hasCondition(ExileHegLeagueInterdiction.CONDITION_ID)
                                    ? ExileHegLeagueInterdiction.CONDITION_ID : "")
                            + (anchor.hasCondition(Conditions.BLOCKADED)
                                    ? " " + Conditions.BLOCKADED : "")
                            + " at squeeze apply time -- the launch-time overlap guards should"
                            + " have made this unreachable. Applying anyway; investigate.");
        }

        ExileHegLeaguePlanetaryBlockade.applyTo(anchor, actorFactionId);
        ExileUnrestGuard.reportExilesPressure(anchor,
                ExileEmergencyMeasuresConfig.pressureDays(
                        ExileHegLeaguePlanetaryBlockade.DURATION_DAYS, 0));
        affectedMarketIds.add(anchor.getId());

        Global.getLogger(ExileHegLeagueSqueezeStage.class).info(
                "Exiles Heg-League squeeze: condition applied to " + anchor.getName()
                        + " (" + shortName(victimFactionId) + ") in " + system.getBaseName()
                        + " by " + shortName(actorFactionId));
    }

    @Override
    protected void removeConditions() {
        conditionsRemoved = true;
        for (int i = 0; i < affectedMarketIds.size(); i++) {
            MarketAPI market = Global.getSector().getEconomy()
                    .getMarket((String) affectedMarketIds.get(i));
            ExileHegLeaguePlanetaryBlockade.removeFrom(market);
        }
        Global.getLogger(ExileHegLeagueSqueezeStage.class).info(
                "Exiles Heg-League squeeze: cordon withdrew from " + system.getBaseName()
                        + " (" + status + "), condition lifted from " + affectedMarketIds.size()
                        + " market(s)");
    }

    /** Never invoked ({@link #canRaid} is false); the parent's safety net would stamp the WRONG
     *  condition, so it is overridden to a no-op rather than inherited. */
    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
    }

    // ------------------------------------------------------------------
    // Text -- PLACEHOLDER voice, faction-neutral (R12, sec 11 item 2)
    // ------------------------------------------------------------------

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        float opad = 10f;
        String actor = shortName(actorFactionId);
        String victim = shortName(victimFactionId);
        MarketAPI anchor = getAnchorMarket();
        String anchorName = anchor != null ? anchor.getName() : "the colony";

        if (status == RaidStageStatus.FAILURE) {
            info.addPara(actor + " warships have been driven off " + anchorName
                    + ". The blockade is over.", opad);
        } else if (status == RaidStageStatus.SUCCESS) {
            info.addPara(actor + " warships held station over " + anchorName
                    + " for weeks. Nothing lifted from the surface without their leave, and "
                    + victim + " has been made to feel it.", opad);
        } else if (curr == index) {
            info.addPara(actor + " warships are blockading " + anchorName + ", turning away "
                    + victim + " traffic.", opad);
        }
    }

    @Override
    public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
        return "blockading " + market.getName();
    }

    @Override
    public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
        return "moving to blockade " + market.getName();
    }

    @Override
    public String getRaidInSystemText(CampaignFleetAPI fleet) {
        return "enforcing a planetary blockade";
    }

    @Override
    public String getRaidDefaultText(CampaignFleetAPI fleet) {
        return "enforcing a planetary blockade";
    }
}
