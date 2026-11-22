package data.scripts.exiles;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.util.Misc;

import data.scripts.marketconditions.ExileBusinessIsGood;
import data.scripts.marketconditions.ExileCorsairActivity;
import data.scripts.marketconditions.ExilePriceSpikes;
import data.scripts.marketconditions.ExileSacked;

/**
 * Black Fleet ASSAULT action stage (two-tier design ratified 2026-07-09): the rare, heavy
 * tier. Goes after PLANETS. On a surviving fleet reaching a market, in order:
 *
 *  1. The vanilla ground raid (super.performRaid -> doGenericRaid): Recent Unrest scaled by raid
 *     effectiveness, plus the RECENTLY_RAIDED flag. It steals nothing (corrected 2026-10-06, bug
 *     sweep BF-9; the Black Fleet's loot comes from its own sack logic).
 *  2. "Looted docks": short, deciv-safe industry disruption -- spaceport family (0.8 chance,
 *     20-35d) and heavy-industry-family (0.6 chance, 30-45d). NEVER population; military
 *     deliberately untouched (contrast with Omicron, which always hits defenses -- pirates
 *     loot infrastructure, machines dismantle capability). Caps well under the verified
 *     deciv-safety bounds (temporary disruption cannot decivilize).
 *  3. Loot provenance -> Business is Good at the SOURCE anchor: the top produced commodities
 *     of the sacked market (crew/marines excluded) ride back and get fenced -- 30-60 days of
 *     boosted Stockpiles output of THOSE goods. Multi-sack ops MERGE their loot lists
 *     (capped) — everything the op stole gets fenced.
 *
 * CORSAIR ACTIVITY is PRESENCE-based, not sack-based (author ruling 2026-07-11, replacing
 * the brief sack-triggered version): an assault armada strangles the system's shipping the
 * same way and on the same timetable a Raid does — the condition blankets every hostile
 * market in the system on day {@link BlackFleetCommerceRaidStage#CONDITION_APPLY_DAY} of the
 * in-system presence and LIFTS when the assault force withdraws (success, failure, or early
 * abort). Trade recovers when they leave; the sack's lasting damage is the unrest and the
 * looted docks.
 *
 * MULTI-SACK (v0.8 polish, author request 2026-07-11): the primary victim (chosen by the
 * manager at launch: size^2-weighted, feasibility- and deciv-guard-checked) is sacked FIRST —
 * until then, the whole stage machinery (routes, opportunistic raids, autoresolve) is locked
 * to it, exactly like the playtest-3 single-victim rework. But once the primary falls, the op
 * keeps going: any other hostile market in the system is fair game IF it is unsacked, not
 * battered (unrest deciv-guard), not paying Black Fleet protection, and the corsairs think
 * they can win — the vanilla strength comparison (current in-system faction strength vs
 * enemy strength + that market's station).
 * Autoresolve mirrors this with vanilla's own attrition model (str -= defense x 0.5 per
 * sack, weakest secondary first). maxDays extends after each sack so real fleets have time
 * to move on the next target; the op ends when nothing eligible remains or time runs out.
 *
 * Assaults are events, not ambience: a campaign message announces each sack.
 */
public class BlackFleetAssaultStage extends PirateRaidActionStage {

    protected boolean enoughMadeIt(List routes, List stragglers) {
        return ExileBlackFleetLogistics.strength(routes, stragglers) >= abortFP;
    }

    /** TUNE: looted-docks disruption. Deepened 2026-07-09 (playtest ruling: Assaults rarer but
     *  with more teeth and longer-lasting effects). Still comfortably deciv-safe. */
    public static final float INDUSTRY_CHANCE = 0.6f;
    public static final int INDUSTRY_MIN_DAYS = 30;
    public static final int INDUSTRY_MAX_DAYS = 45;
    /** TUNE: how many top commodities of each sacked market get fenced. */
    public static final int LOOT_COMMODITIES = 3;
    /** TUNE: days added to the op clock after a sack when follow-up targets remain. */
    public static final float SECONDARY_SACK_EXTRA_DAYS = 20f;

    protected String sourceStationId;
    /** The manager-designated PRIMARY victim (playtest-3 single-victim discipline): the stage
     *  machinery is restricted to it until it falls; multi-sack opens up after. */
    protected String victimMarketId;
    /** Markets this op has sacked (via our performRaid — both real-fleet and autoresolve
     *  paths). Null on intels from pre-multi-sack saves; lazily initialized. */
    protected List sackedMarketIds = new ArrayList(); // String

    // Presence-based Corsair Activity lifecycle — identical to the commerce tier's.
    protected boolean conditionsApplied = false;
    protected boolean conditionsRemoved = false;
    protected List affectedMarketIds = new ArrayList(); // String
    /** BF-4: stage-elapsed day of the last Corsair Activity refresh (0 on older saves). */
    protected float conditionsRefreshedAt = 0f;
    /** BF-4: refresh cadence while ONGOING -- well inside the condition's 45-day self-timer, so
     *  a long assault (20-45 days + 20 per secondary sack) can never outlive it. */
    public static final float CONDITION_REFRESH_DAYS = 15f;

    public BlackFleetAssaultStage(RaidIntel raid, StarSystemAPI target, String sourceStationId,
                                  String victimMarketId) {
        super(raid, target);
        this.sourceStationId = sourceStationId;
        this.victimMarketId = victimMarketId;
    }

    protected boolean primarySacked() {
        if (sackedMarketIds == null) sackedMarketIds = new ArrayList(); // pre-multi-sack save
        return victimMarketId != null && sackedMarketIds.contains(victimMarketId);
    }

    /** Remaining eligible targets, PRIMARY FIRST. Before the primary falls: just the primary
     *  (single-victim discipline). After: every unsacked hostile market that isn't hidden,
     *  player-owned, protected canon (ExileProtectedFactions — refugees), or battered (unrest
     *  deciv-guard). Feasibility deliberately NOT checked
     *  here — this runs every status tick, and strength math belongs at decision points
     *  (canRaid / autoresolve; CLAUDE.md per-frame discipline). (Called once from the super
     *  constructor before our fields exist — fall back to vanilla for that call.) */
    @Override
    protected List getTargets() {
        List all = super.getTargets();
        if (victimMarketId == null) return all;
        if (sackedMarketIds == null) sackedMarketIds = new ArrayList();

        boolean primaryDown = primarySacked();
        List result = new ArrayList();
        for (int i = 0; i < all.size(); i++) {
            MarketAPI m = (MarketAPI) all.get(i);
            if (sackedMarketIds.contains(m.getId())) continue;
            // Price Spikes is the racket's coordination flag. Check before the primary branch
            // as well: a market that pays after launch is still protected when the fleet arrives.
            if (ExilePriceSpikes.isSpiked(m)) continue;
            if (victimMarketId.equals(m.getId())) {
                result.add(0, m); // primary always heads the list
                continue;
            }
            if (!primaryDown) continue; // locked to the primary until it falls
            if (m.isHidden() || m.isPlayerOwned()) continue;
            if (ExileProtectedFactions.isProtected(m)) continue; // never a direct target (canon)
            if (ExileUnrestGuard.isBattered(m)) continue;
            if (ExileSacked.isSacked(m)) continue; // still scarred from a previous op
            result.add(m);
        }
        return result;
    }

    /** Raid-AI gate: the primary until it falls; after that, follow-up targets only if the
     *  corsairs think they can win (vanilla strength comparison). */
    @Override
    public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (victimMarketId == null) return super.canRaid(fleet, market);
        if (sackedMarketIds == null) sackedMarketIds = new ArrayList();
        if (ExilePriceSpikes.isSpiked(market)) return false;

        if (!primarySacked()) {
            if (!victimMarketId.equals(market.getId())) return false;
            return super.canRaid(fleet, market);
        }
        if (sackedMarketIds.contains(market.getId())) return false;
        if (market.isHidden() || market.isPlayerOwned()) return false;
        if (ExileProtectedFactions.isProtected(market)) return false; // never a direct target (canon)
        if (ExileUnrestGuard.isBattered(market)) return false;
        if (ExileSacked.isSacked(market)) return false; // scarred from a previous op
        if (!canBeat(market)) return false;
        return super.canRaid(fleet, market);
    }

    /** "Thinks it can win": vanilla's own comparison — current in-system faction strength vs
     *  system enemy strength + this market's station. */
    protected boolean canBeat(MarketAPI market) {
        FactionAPI bf = intel.getFaction();
        float str = WarSimScript.getFactionStrength(bf, system);
        float defense = WarSimScript.getEnemyStrength(bf, system)
                + ExileStationStrength.get(market.getFaction(), system, market.getPrimaryEntity());
        return str > defense;
    }

    /** Vanilla shape (PirateRaidActionStage.updateStatus), success/failure keyed to our sack
     *  tracking instead of RECENTLY_RAIDED flag sweeps: over when nothing eligible remains or
     *  the clock runs out — SUCCESS iff we sacked anything. Wrapped in the presence-based
     *  Corsair Activity lifecycle: blanket the system on the shared timetable while ONGOING,
     *  lift the moment the op stops being ONGOING for any reason. */
    @Override
    protected void updateStatus() {
        abortIfNeededBasedOnFP(true);
        if (sackedMarketIds == null) sackedMarketIds = new ArrayList();

        if (status == RaidStageStatus.ONGOING) {
            // Same timetable as a Raid (author ruling 2026-07-11): a few days after the
            // armada arrives in-system, shipping starts going missing everywhere.
            if (elapsed >= BlackFleetCommerceRaidStage.CONDITION_APPLY_DAY && !conditionsApplied) {
                applyConditions();
            } else if (conditionsApplied && !conditionsRemoved
                    && elapsed - conditionsRefreshedAt >= CONDITION_REFRESH_DAYS) {
                refreshConditions();
            }

            if (getTargets().isEmpty()) {
                finish(!sackedMarketIds.isEmpty());
            } else if (steps.isEmpty()) {
                // THE WINDOW-END FIX (Heg-League doc sec 14.14.11; ruled by Sean 2026-09-28 for
                // the Black Fleet too). This used to autoresolve only when the player was OUT of
                // spawn range and otherwise finish() on whatever the live fleets had already sacked,
                // with no strength check -- the outcome depended on where the player was at one
                // tick. Both branches now resolve through autoresolve(): its attrition model runs
                // on live WarSimScript strengths when fleets are spawned, and it skips anything the
                // live fleets already sacked. The window LENGTH is untouched (the separate ruling
                // that the assault keeps vanilla's maxDays stands).
                if (elapsed > maxDays) {
                    boolean inSpawnRange = RouteManager.isPlayerInSpawnRange(system.getCenter());
                    Global.getLogger(BlackFleetAssaultStage.class).info(
                            "Black Fleet assault WINDOW END at " + system.getBaseName()
                                    + ": strength "
                                    + (int) WarSimScript.getFactionStrength(intel.getFaction(), system)
                                    + " vs enemy "
                                    + (int) WarSimScript.getEnemyStrength(intel.getFaction(), system)
                                    + " (+ stations per target) | sacked so far "
                                    + sackedMarketIds.size() + " | player "
                                    + (inSpawnRange ? "IN" : "out of") + " spawn range | "
                                    + (int) elapsed + "d of " + (int) maxDays + "d");
                    autoresolve();
                }
            }
        }

        // Withdrawal — planned, driven out, or aborted — lifts the blockade, same as a Raid.
        if (status != RaidStageStatus.ONGOING && conditionsApplied && !conditionsRemoved) {
            removeConditions();
        }
    }

    protected void finish(boolean success) {
        status = success ? RaidStageStatus.SUCCESS : RaidStageStatus.FAILURE;
        removeMilScripts();
        if (!success) {
            giveReturnOrdersToStragglers(getRoutes());
        }
    }

    /** Player-out-of-range resolution, vanilla attrition model (str -= defense x 0.5 per
     *  sack): the primary first — if the op can't beat IT, the whole assault fails, no
     *  skipping ahead to soft targets — then remaining secondaries weakest-defense first
     *  until the math stops working. */
    @Override
    protected void autoresolve() {
        FactionAPI bf = intel.getFaction();
        float str = WarSimScript.getFactionStrength(bf, system);
        float enemyStr = WarSimScript.getEnemyStrength(bf, system);
        if (sackedMarketIds == null) sackedMarketIds = new ArrayList();

        for (int guard = 0; guard < 10; guard++) {
            List targets = getTargets();
            if (targets.isEmpty()) break;

            MarketAPI pick;
            if (!primarySacked()) {
                pick = (MarketAPI) targets.get(0); // getTargets puts the primary at index 0
            } else {
                pick = null;
                float pickDef = Float.MAX_VALUE;
                for (int i = 0; i < targets.size(); i++) {
                    MarketAPI m = (MarketAPI) targets.get(i);
                    float def = enemyStr + ExileStationStrength.get(
                            m.getFaction(), system, m.getPrimaryEntity());
                    if (def < pickDef) {
                        pick = m;
                        pickDef = def;
                    }
                }
            }
            if (pick == null) break;

            float defense = enemyStr + ExileStationStrength.get(
                    pick.getFaction(), system, pick.getPrimaryEntity());
            if (defense >= str) {
                if (sackedMarketIds.isEmpty()) ((BlackFleetRaidIntel)intel).markProsperityDefeat();
                break;
            }

            Industry station = Misc.getStationIndustry(pick);
            if (station != null) {
                OrbitalStation.disrupt(station);
            }
            performRaid(null, pick);
            str -= defense * 0.5f;
        }

        finish(!sackedMarketIds.isEmpty());
    }

    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (sackedMarketIds == null) sackedMarketIds = new ArrayList();
        // Final invariant guard for cached real-fleet targets and all autoresolve callers.
        if (ExilePriceSpikes.isSpiked(market)) return;
        if (ExileSacked.isSacked(market) || sackedMarketIds.contains(market.getId())) return;

        // 1. Vanilla ground raid: recent_unrest and RECENTLY_RAIDED; explicit looting follows.
        super.performRaid(fleet, market);
        ExileUnrestGuard.reportExilesPressure(
                market, ExileEmergencyMeasuresConfig.BLACK_FLEET_ASSAULT_PRESSURE_DAYS);
        if (!sackedMarketIds.contains(market.getId())) {
            sackedMarketIds.add(market.getId());
        }

        // Grudge settled (extortion spec #6): the sack IS the revenge.
        market.getMemoryWithoutUpdate().unset(ExileBlackFleetExtortionManager.GRUDGE_KEY);

        // (Corsair Activity is presence-based — see updateStatus — not part of the sack.)

        // 2. The scar (ratified 2026-07-11): guaranteed lasting consequence — trade limps,
        //    ground defenses stripped (chaos ripple: everyone else smells blood), 60d
        //    anti-repeat marker. No stability effect by design.
        ExileSacked.applyTo(market);

        // 3. Looted docks (deciv-safe short disruption; never population, never military).
        List loot = topProducedCommodities(market, LOOT_COMMODITIES);
        lootDocks(market);

        // 4. The loot goes home to be fenced — merged with anything this op already stole.
        MarketAPI anchor = getSourceAnchor();
        ((BlackFleetRaidIntel)intel).recordProsperityLoot(loot);

        // 5. Follow-up targets remain? Extend the op clock so fleets have time to reach one.
        if (!getTargets().isEmpty()) {
            maxDays = Math.max(maxDays, elapsed + SECONDARY_SACK_EXTRA_DAYS);
        }

        Global.getSector().getCampaignUI().addMessage(
                "Black Fleet forces have sacked " + market.getName() + "!",
                Color.RED);
        Global.getLogger(BlackFleetAssaultStage.class).info(
                "Black Fleet assault: sacked " + market.getName() + " (" + market.getFactionId()
                        + "), loot=" + loot + ", returningTo="
                        + (anchor != null ? anchor.getName() : "none")
                        + ", sackedSoFar=" + sackedMarketIds.size()
                        + ", byFleet=" + (fleet != null));
    }

    /** Corsair Activity on every hostile market in the assaulted system (refreshes timers
     *  where it already sits). Same exemptions and tracking as the commerce tier. */
    protected void applyConditions() {
        conditionsApplied = true;
        conditionsRefreshedAt = elapsed;
        if (affectedMarketIds == null) affectedMarketIds = new ArrayList(); // pre-polish save
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m.isHidden()) continue;
            if (m.isPlayerOwned()) continue; // code-exempt convention
            if (!m.getFaction().isHostileTo(intel.getFaction())) continue;
            // Extortion coordination (spec #5): a market paying the racket is spared.
            if (data.scripts.marketconditions.ExilePriceSpikes.isSpiked(m)) continue;
            ExileCorsairActivity.applyTo(m);
            ExileUnrestGuard.reportExilesPressure(m,
                    ExileEmergencyMeasuresConfig.pressureDays(
                            ExileCorsairActivity.DURATION_DAYS, 0));
            affectedMarketIds.add(m.getId());
        }
        Global.getLogger(BlackFleetAssaultStage.class).info(
                "Black Fleet assault: Corsair Activity applied to " + affectedMarketIds.size()
                        + " market(s) in " + system.getBaseName());
    }

    /** BF-4 (Sean, 2026-10-06): Corsair Activity is presence-scoped -- it lifts when the corsairs
     *  withdraw -- but its own timer is a fixed 45 days, and an assault window can run longer.
     *  Re-arm the timer on the markets this op covers while it is still ONGOING. A market that
     *  started paying the racket since is left to time out (the apply-time exemption). */
    protected void refreshConditions() {
        conditionsRefreshedAt = elapsed;
        if (affectedMarketIds == null) return; // pre-polish save; nothing tracked
        for (int i = 0; i < affectedMarketIds.size(); i++) {
            MarketAPI market = Global.getSector().getEconomy()
                    .getMarket((String) affectedMarketIds.get(i));
            if (market == null || ExilePriceSpikes.isSpiked(market)) continue;
            ExileCorsairActivity.applyTo(market);
            ExileUnrestGuard.reportExilesPressure(market,
                    ExileEmergencyMeasuresConfig.pressureDays(
                            ExileCorsairActivity.DURATION_DAYS, 0));
        }
    }

    protected void removeConditions() {
        conditionsRemoved = true;
        if (affectedMarketIds == null) return; // pre-polish save; nothing tracked
        for (int i = 0; i < affectedMarketIds.size(); i++) {
            MarketAPI market = Global.getSector().getEconomy()
                    .getMarket((String) affectedMarketIds.get(i));
            ExileCorsairActivity.removeFrom(market);
        }
        Global.getLogger(BlackFleetAssaultStage.class).info(
                "Black Fleet assault: force withdrew from " + system.getBaseName()
                        + " (" + status + "), Corsair Activity lifted from "
                        + affectedMarketIds.size() + " market(s)");
    }

    /** Short disruption of the port and industry -- the docks got stripped, not shelled. */
    protected void lootDocks(MarketAPI market) {
        Random random = new Random();
        List industries = new ArrayList(market.getIndustries());
        for (int i = 0; i < industries.size(); i++) {
            Industry ind = (Industry) industries.get(i);
            if (ind == null) continue;
            String id = ind.getId();
            if (Industries.POPULATION.equals(id)) continue; // never

            boolean spaceportFamily = Industries.SPACEPORT.equals(id)
                    || Industries.MEGAPORT.equals(id) || ind.getSpec().hasTag("spaceport");
            boolean industryFamily = ind.getSpec().hasTag("heavyindustry")
                    || ind.getSpec().hasTag("industrial");

            if (spaceportFamily) {
                float days = ExileBlackFleetProsperity.get().config().portDays;
                ind.setDisrupted(Math.max(ind.getDisruptedDays(), days));
            } else if (industryFamily) {
                if (random.nextFloat() < INDUSTRY_CHANCE) {
                    ind.setDisrupted(Math.max(ind.getDisruptedDays(), INDUSTRY_MIN_DAYS
                            + random.nextInt(INDUSTRY_MAX_DAYS - INDUSTRY_MIN_DAYS + 1)));
                }
            }
        }
    }

    /** Top produced commodities of the sacked market by max supply (crew/marines excluded --
     *  boosting "people" output at the fence reads wrong). Plain selection loop, Janino-style. */
    protected List topProducedCommodities(MarketAPI market, int count) {
        List candidates = new ArrayList(market.getCommoditiesCopy());
        List result = new ArrayList(); // String ids
        for (int picks = 0; picks < count; picks++) {
            CommodityOnMarketAPI best = null;
            for (int i = 0; i < candidates.size(); i++) {
                CommodityOnMarketAPI com = (CommodityOnMarketAPI) candidates.get(i);
                if (com == null || com.isNonEcon() || com.getMaxSupply() <= 0) continue;
                String id = com.getId();
                if (Commodities.CREW.equals(id) || Commodities.MARINES.equals(id)) continue;
                if (result.contains(id)) continue;
                if (best == null || com.getMaxSupply() > best.getMaxSupply()) {
                    best = com;
                }
            }
            if (best == null) break;
            result.add(best.getId());
        }
        return result;
    }

    protected MarketAPI getSourceAnchor() {
        if (sourceStationId == null) return null;
        SectorEntityToken station = Global.getSector().getEntityById(sourceStationId);
        if (station == null) return null;
        MarketAPI market = station.getMarket();
        if (market == null || !market.isInEconomy()) return null;
        return market;
    }

    // --- Corsair flavor for fleet action text ---

    @Override
    public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
        return "sacking " + market.getName();
    }

    @Override
    public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
        return "descending on " + market.getName();
    }
}
