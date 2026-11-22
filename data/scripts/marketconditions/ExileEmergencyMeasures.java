package data.scripts.marketconditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.exiles.ExileEmergencyMeasuresConfig;

/**
 * Emergency Measures -- a visible, finite recovery response that can only be armed by an explicit
 * Exiles-origin pressure report. The same condition plugin owns its hidden 60-day lead-in and its
 * visible active lifecycle, so there is no sector-wide market scan.
 *
 * The reserve is snapshotted once on activation: enough to raise raw underlying Stability to 1,
 * capped at +5. It never increases or extends later. Commodity shortages reduce the applied reserve
 * through the worst one-unit deficit, and later damage can overwhelm it.
 */
public class ExileEmergencyMeasures extends BaseMarketConditionPlugin
        implements EconomyTickListener, TooltipMakerAPI.TooltipCreator {

    public static final String CONDITION_ID = "exile_emergency_measures";
    public static final String CONDITION_NAME = "Emergency Measures";

    public static final String PRESSURE_KEY = "$exiles_emergency_measures_pressure";
    public static final String ACTIVE_KEY = "$exiles_emergency_measures_active";
    public static final String COOLDOWN_KEY = "$exiles_emergency_measures_cooldown";
    public static final String PLAYER_CAUSED_KEY = "$exiles_emergency_measures_player_caused";

    private static final String FINANCE_NODE_ID = "exile_emergency_measures";

    protected boolean active = false;
    protected int storedReserve = 0;
    protected float zeroStabilityDays = 0f;
    protected float activeDays = 0f;
    protected float recoveryDays = 0f;
    protected float sinceEffectRefresh = 0f;

    /** Called only from narrow Exiles damage seams, never from vanilla or player actions. */
    public static void reportExilesPressure(MarketAPI market, float pressureDays) {
        if (market == null || !market.isInEconomy()) return;
        if (isActive(market)) return;
        if (market.getMemoryWithoutUpdate().getBoolean(PLAYER_CAUSED_KEY)) return;

        float duration = Math.max(ExileEmergencyMeasuresConfig.MIN_EXILES_PRESSURE_DAYS, pressureDays);
        market.getMemoryWithoutUpdate().set(PRESSURE_KEY, true, duration);
        if (!market.hasCondition(CONDITION_ID)) {
            market.addCondition(CONDITION_ID);
        }
    }

    /** Player raids/bombardments disqualify only a dormant lead-in; active responses are untouched. */
    public static void reportPlayerHostileAction(MarketAPI market) {
        if (market == null || isActive(market)) return;

        market.getMemoryWithoutUpdate().set(
                PLAYER_CAUSED_KEY, true, ExileEmergencyMeasuresConfig.PLAYER_CAUSED_BLOCK_DAYS);
        market.getMemoryWithoutUpdate().unset(PRESSURE_KEY);
        if (market.hasCondition(CONDITION_ID)) {
            market.removeCondition(CONDITION_ID);
        }
    }

    public static boolean isActive(MarketAPI market) {
        return market != null && market.hasCondition(CONDITION_ID)
                && market.getMemoryWithoutUpdate().getBoolean(ACTIVE_KEY);
    }

    @Override
    public void apply(String id) {
        if (market == null) return;

        if (active || market.getMemoryWithoutUpdate().getBoolean(ACTIVE_KEY)) {
            active = true;
            applyActiveEffects(id);
        }
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        // Vanilla may call unapply/apply as a reapplication pair while the condition remains.
        // Lifecycle state and cooldown therefore belong to removeCondition(), not this method.
        clearActiveEffects(id);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null) return;

        float days = Global.getSector().getClock().convertToDays(amount);
        if (days <= 0f) return;

        if (!market.isInEconomy()) {
            removeCondition(isActiveState());
            return;
        }

        if (!isActiveState()) {
            advanceLeadIn(days);
            return;
        }

        advanceActive(days);
    }

    protected void advanceLeadIn(float days) {
        if (!market.getMemoryWithoutUpdate().getBoolean(PRESSURE_KEY)) {
            removeCondition(false);
            return;
        }

        if (market.getMemoryWithoutUpdate().getBoolean(COOLDOWN_KEY)
                || market.getMemoryWithoutUpdate().getBoolean(PLAYER_CAUSED_KEY)) {
            zeroStabilityDays = 0f;
            return;
        }

        if (market.getStabilityValue() <= 0f) {
            zeroStabilityDays += days;
        } else {
            zeroStabilityDays = 0f;
        }

        if (zeroStabilityDays >= ExileEmergencyMeasuresConfig.ZERO_STABILITY_TRIGGER_DAYS) {
            activate();
        }
    }

    protected void activate() {
        if (isActiveState()) return;

        // Counter first, then reapply vanilla Recent Unrest so the snapshot reflects the relief.
        RecentUnrest unrest = RecentUnrest.get(market, false);
        if (unrest != null && unrest.getPenalty() > 0) {
            unrest.counter(1, CONDITION_NAME);
            market.reapplyCondition(Conditions.RECENT_UNREST);
        }

        float underlying = market.getStability().getModifiedValue();
        int needed = (int) Math.ceil(1f - underlying);
        storedReserve = Math.max(0, Math.min(
                ExileEmergencyMeasuresConfig.STABILITY_RESERVE_CAP, needed));

        active = true;
        activeDays = 0f;
        recoveryDays = 0f;
        sinceEffectRefresh = 0f;
        // Timed defensively so an externally removed/decivilized condition cannot leave an immortal
        // guard marker. The condition itself always ends sooner and never refreshes this duration.
        market.getMemoryWithoutUpdate().set(
                ACTIVE_KEY, true, ExileEmergencyMeasuresConfig.MAX_ACTIVE_DAYS + 5f);
        market.getMemoryWithoutUpdate().unset(PRESSURE_KEY);
        applyActiveEffects(getModId());

        Global.getLogger(ExileEmergencyMeasures.class).info(
                "Emergency Measures activated at " + market.getName()
                        + ": underlying=" + underlying + ", reserve=" + storedReserve);
    }

    protected void advanceActive(float days) {
        activeDays += days;
        sinceEffectRefresh += days;

        float underlying = getUnderlyingStability();
        if (underlying >= ExileEmergencyMeasuresConfig.RECOVERY_UNDERLYING_STABILITY) {
            recoveryDays += days;
        } else {
            recoveryDays = 0f;
        }

        if (recoveryDays >= ExileEmergencyMeasuresConfig.RECOVERY_HOLD_DAYS) {
            removeCondition(true);
            return;
        }
        if (activeDays >= ExileEmergencyMeasuresConfig.MAX_ACTIVE_DAYS) {
            removeCondition(true);
            return;
        }

        if (sinceEffectRefresh >= ExileEmergencyMeasuresConfig.EFFECT_REFRESH_DAYS) {
            sinceEffectRefresh = 0f;
            applyActiveEffects(getModId());
        }
    }

    protected void removeCondition(boolean completedActivation) {
        if (market == null) return;

        if (completedActivation) {
            market.getMemoryWithoutUpdate().set(
                    COOLDOWN_KEY, true, ExileEmergencyMeasuresConfig.COOLDOWN_DAYS);
        }
        market.getMemoryWithoutUpdate().unset(PRESSURE_KEY);
        market.getMemoryWithoutUpdate().unset(ACTIVE_KEY);
        clearActiveEffects(getModId());
        active = false;

        if (market.hasCondition(CONDITION_ID)) {
            market.removeCondition(CONDITION_ID);
        }
    }

    protected void applyActiveEffects(String id) {
        applyDemand(Commodities.MARINES, ExileEmergencyMeasuresConfig.MARINES_DEMAND, id);
        applyDemand(Commodities.HAND_WEAPONS, ExileEmergencyMeasuresConfig.HAND_WEAPONS_DEMAND, id);
        applyDemand(Commodities.SUPPLIES, ExileEmergencyMeasuresConfig.SUPPLIES_DEMAND, id);

        int appliedReserve = getSupplyLimitedReserve();
        market.getStability().modifyFlat(id, appliedReserve, CONDITION_NAME);

        clearNpcPriceModifiers(id);
        if (!market.isPlayerOwned()) {
            for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
                com.getPlayerSupplyPriceMod().modifyMult(
                        id, ExileEmergencyMeasuresConfig.NPC_PLAYER_BUY_PRICE_MULT, CONDITION_NAME);
            }
            removeFinanceListener();
        } else {
            ensureFinanceListener();
        }
    }

    protected void clearActiveEffects(String id) {
        market.getStability().unmodify(id);
        clearDemand(Commodities.MARINES, id);
        clearDemand(Commodities.HAND_WEAPONS, id);
        clearDemand(Commodities.SUPPLIES, id);
        clearNpcPriceModifiers(id);
        removeFinanceListener();
    }

    protected void applyDemand(String commodityId, int quantity, String id) {
        CommodityOnMarketAPI com = market.getCommodityData(commodityId);
        if (com != null && com.getDemand() != null) {
            com.getDemand().getDemand().modifyFlat(id, quantity, CONDITION_NAME);
        }
    }

    protected void clearDemand(String commodityId, String id) {
        CommodityOnMarketAPI com = market.getCommodityData(commodityId);
        if (com != null && com.getDemand() != null) {
            com.getDemand().getDemand().unmodify(id);
        }
    }

    /** Vanilla-style worst-deficit behavior: the one-unit shortage reduces the reserve by one. */
    protected int getSupplyLimitedReserve() {
        int worstDeficit = 0;
        worstDeficit = Math.max(worstDeficit,
                getDeficit(Commodities.MARINES, ExileEmergencyMeasuresConfig.MARINES_DEMAND));
        worstDeficit = Math.max(worstDeficit,
                getDeficit(Commodities.HAND_WEAPONS, ExileEmergencyMeasuresConfig.HAND_WEAPONS_DEMAND));
        worstDeficit = Math.max(worstDeficit,
                getDeficit(Commodities.SUPPLIES, ExileEmergencyMeasuresConfig.SUPPLIES_DEMAND));
        return Math.max(0, storedReserve - worstDeficit);
    }

    protected int getDeficit(String commodityId, int demand) {
        CommodityOnMarketAPI com = market.getCommodityData(commodityId);
        if (com == null) return demand;
        return Math.max(0, demand - com.getAvailable());
    }

    protected void clearNpcPriceModifiers(String id) {
        for (CommodityOnMarketAPI com : market.getCommoditiesCopy()) {
            com.getPlayerSupplyPriceMod().unmodify(id);
        }
    }

    protected float getAppliedStabilityBonus() {
        StatMod mod = market.getStability().getFlatStatMod(getModId());
        return mod == null ? 0f : mod.value;
    }

    protected float getUnderlyingStability() {
        return market.getStability().getModifiedValue() - getAppliedStabilityBonus();
    }

    protected boolean isActiveState() {
        return active || market.getMemoryWithoutUpdate().getBoolean(ACTIVE_KEY);
    }

    protected void ensureFinanceListener() {
        if (Global.getSector() == null) return;
        if (!Global.getSector().getListenerManager().hasListener(this)) {
            Global.getSector().getListenerManager().addListener(this, true);
        }
    }

    protected void removeFinanceListener() {
        if (Global.getSector() != null
                && Global.getSector().getListenerManager().hasListener(this)) {
            Global.getSector().getListenerManager().removeListener(this);
        }
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        if (!isActiveState() || !market.isPlayerOwned()) return;

        int iterations = (int) Global.getSettings().getFloat("economyIterPerMonth");
        if (iterations < 1) iterations = 1;
        float monthlyCost = (float) getMonthlyPlayerCost();

        MonthlyReport report = SharedData.getData().getCurrentReport();
        FDNode marketNode = report.getMarketNode(market);
        FDNode node = report.getNode(marketNode, FINANCE_NODE_ID);
        node.name = CONDITION_NAME;
        node.icon = Global.getSettings().getSpriteName("income_report", "generic_expense");
        node.upkeep += monthlyCost / iterations;
        node.custom = market;
        node.mapEntity = market.getPrimaryEntity();
        node.tooltipCreator = this;
        node.tooltipParam = market;
    }

    @Override
    public void reportEconomyMonthEnd() {
    }

    protected int getMonthlyPlayerCost() {
        return market.getSize() * ExileEmergencyMeasuresConfig.PLAYER_MONTHLY_COST_PER_MARKET_SIZE;
    }

    @Override
    public boolean showIcon() {
        return market != null && isActiveState();
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        float opad = 10f;

        int applied = getSupplyLimitedReserve();
        tooltip.addPara("%s Stability from a stored emergency reserve of %s.", opad,
                Misc.getHighlightColor(), "+" + applied, "+" + storedReserve);
        tooltip.addPara("Demand: %s Marines, %s Hand Weapons, and %s Supplies. A shortage reduces the "
                        + "applied reserve but can never increase the stored reserve.", opad,
                Misc.getHighlightColor(),
                "+" + ExileEmergencyMeasuresConfig.MARINES_DEMAND,
                "+" + ExileEmergencyMeasuresConfig.HAND_WEAPONS_DEMAND,
                "+" + ExileEmergencyMeasuresConfig.SUPPLIES_DEMAND);

        if (market.isPlayerOwned()) {
            tooltip.addPara("Monthly emergency appropriation: %s.", opad,
                    Misc.getHighlightColor(), Misc.getDGSCredits(getMonthlyPlayerCost()));
        } else {
            tooltip.addPara("The player pays %s more when purchasing commodities here.", opad,
                    Misc.getHighlightColor(), "+50%");
        }

        int remaining = Math.max(0,
                (int) Math.ceil(ExileEmergencyMeasuresConfig.MAX_ACTIVE_DAYS - activeDays));
        tooltip.addPara("The response can remain in force for at most %s more days and will end early "
                        + "after underlying Stability remains at 3 or higher for 30 days.", opad,
                Misc.getHighlightColor(), "" + remaining);
    }

    @Override
    public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
        tooltip.addPara("Emergency Measures appropriation for %s: %s per month while active. The "
                        + "monthly report accrues the expense across economy ticks, so partial months are prorated.",
                0f, Misc.getHighlightColor(), market.getName(), Misc.getDGSCredits(getMonthlyPlayerCost()));
    }

    @Override
    public float getTooltipWidth(Object tooltipParam) {
        return 450f;
    }

    @Override
    public boolean isTooltipExpandable(Object tooltipParam) {
        return false;
    }
}
