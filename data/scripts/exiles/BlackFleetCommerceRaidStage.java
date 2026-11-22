package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import data.scripts.marketconditions.ExileCorsairActivity;

/**
 * Black Fleet COMMERCE RAID action stage, v2 -- PRESENCE-ONLY (playtest-3 rework, 2026-07-09).
 * Fleets enter the target system and hunt its shipping lanes for {@code presenceDays}; they
 * NEVER approach or touch a planet ({@link #canRaid} always false, so the raid AI never
 * issues a colony action). If the corsairs maintain presence for the full duration without
 * being driven below the abort threshold, every hostile market in the system receives the
 * timed Corsair Activity condition (accessibility strangulation) and the op succeeds.
 *
 * v1 reused the vanilla ground-raid targeting, which sent fleets AT planets and triggered
 * pillage-looking behavior (author: raids should be a hostile presence in the system, not
 * planet attacks -- planet attacks are the Assault tier's job).
 */
public class BlackFleetCommerceRaidStage extends PirateRaidActionStage {

    protected boolean enoughMadeIt(List routes, List stragglers) {
        return ExileBlackFleetLogistics.strength(routes, stragglers) >= abortFP;
    }

    /** Day of the presence at which Corsair Activity lands (author ruling 2026-07-09):
     *  the strangulation starts once the corsairs have had a few days to work the lanes. */
    public static final float CONDITION_APPLY_DAY = 5f;

    protected float presenceDays;
    protected boolean conditionsApplied = false;
    protected boolean conditionsRemoved = false;
    protected List affectedMarketIds = new ArrayList(); // String

    public BlackFleetCommerceRaidStage(RaidIntel raid, StarSystemAPI target, float presenceDays) {
        super(raid, target);
        this.presenceDays = presenceDays;
        this.maxDays = presenceDays + 10f; // slack; success normally fires at presenceDays
    }

    /** NEVER: commerce raiders do not touch planets. This also prevents the raid AI from
     *  issuing any colony-approach orders -- fleets just follow their loiter route. */
    @Override
    public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
        return false;
    }

    /** No ground-raid steps, no per-colony military-response staging: just send every route
     *  into the system to loiter (the vanilla patrol/fleet AI handles any fighting that finds
     *  them). Overrides the vanilla version wholesale -- no steps are ever created. */
    @Override
    protected void updateRoutes() {
        resetRoutes();

        List routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
        for (int i = 0; i < routes.size(); i++) {
            RouteData route = (RouteData) routes.get(i);
            route.addSegment(new RouteManager.RouteSegment(presenceDays + 5f, system.getCenter()));
        }
    }

    /** Lifecycle (2026-07-09 ruling): Corsair Activity lands on day CONDITION_APPLY_DAY of the
     *  presence and is REMOVED when the corsairs withdraw — whether that's the planned end of
     *  the {@code presenceDays} window (success) or an early withdrawal because the op fell
     *  below its abort threshold (abortIfNeededBasedOnFP; abortFraction 0.5 = "drops below
     *  50%, they withdraw"). Trade recovers the moment they leave. */
    @Override
    protected void updateStatus() {
        abortIfNeededBasedOnFP(true); // early withdrawal below 50% FP -> status = FAILURE

        if (status == RaidStageStatus.ONGOING) {
            if (elapsed >= CONDITION_APPLY_DAY && !conditionsApplied) {
                applyConditions();
            }
            if (elapsed >= presenceDays) {
                status = RaidStageStatus.SUCCESS; // presence complete; withdrawal follows
            }
        }

        if (status != RaidStageStatus.ONGOING && conditionsApplied && !conditionsRemoved) {
            removeConditions();
        }
    }

    protected void applyConditions() {
        conditionsApplied = true;
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market.isHidden()) continue;
            if (market.isPlayerOwned()) continue; // code-exempt convention
            if (!market.getFaction().isHostileTo(intel.getFaction())) continue;
            // Extortion coordination (spec #5): a market paying the racket is spared.
            if (data.scripts.marketconditions.ExilePriceSpikes.isSpiked(market)) continue;
            ExileCorsairActivity.applyTo(market);
            ExileUnrestGuard.reportExilesPressure(market,
                    ExileEmergencyMeasuresConfig.pressureDays(
                            ExileCorsairActivity.DURATION_DAYS, 0));
            affectedMarketIds.add(market.getId());
        }
        Global.getLogger(BlackFleetCommerceRaidStage.class).info(
                "Black Fleet commerce raid: Corsair Activity applied to "
                        + affectedMarketIds.size() + " market(s) in " + system.getBaseName());
    }

    protected void removeConditions() {
        conditionsRemoved = true;
        for (int i = 0; i < affectedMarketIds.size(); i++) {
            MarketAPI market = Global.getSector().getEconomy()
                    .getMarket((String) affectedMarketIds.get(i));
            ExileCorsairActivity.removeFrom(market);
        }
        Global.getLogger(BlackFleetCommerceRaidStage.class).info(
                "Black Fleet commerce raid: corsairs withdrew from " + system.getBaseName()
                        + " (" + status + "), Corsair Activity lifted from "
                        + affectedMarketIds.size() + " market(s)");
    }

    /** Never invoked (canRaid is false); kept as a harmless safety net. */
    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        ExileCorsairActivity.applyTo(market);
        ExileUnrestGuard.reportExilesPressure(market,
                ExileEmergencyMeasuresConfig.pressureDays(
                        ExileCorsairActivity.DURATION_DAYS, 0));
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        float opad = 10f;

        if (status == RaidStageStatus.FAILURE) {
            info.addPara("The corsairs have been driven out of the "
                    + intel.getSystem().getNameWithLowercaseType() + ". The raid is over.", opad);
        } else if (status == RaidStageStatus.SUCCESS) {
            info.addPara("The corsairs have picked the shipping lanes of the "
                    + intel.getSystem().getNameWithLowercaseType()
                    + " clean. Trade into the system's ports has been strangled.", opad);
        } else if (curr == index) {
            info.addPara("Corsairs are hunting shipping in the "
                    + intel.getSystem().getNameWithLowercaseType() + ".", opad);
        }
    }

    // --- Corsair flavor for fleet action text ---

    @Override
    public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
        return "raiding shipping near " + market.getName();
    }

    @Override
    public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
        return "moving to raid shipping near " + market.getName();
    }

    @Override
    public String getRaidInSystemText(CampaignFleetAPI fleet) {
        return "hunting shipping";
    }

    @Override
    public String getRaidDefaultText(CampaignFleetAPI fleet) {
        return "hunting shipping";
    }
}
