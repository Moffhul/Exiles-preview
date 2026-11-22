package data.scripts.marketconditions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Refugee Influx -- mainly a PLAYER-FACING condition: refugees migrate to the player's colonies as
 * they earn the refugees' trust. Both effects scale off the player's relation with the refugees and
 * re-sample on a slow cadence (REFRESH_DAYS), so the situation drifts as the relationship changes.
 *
 *   - Stability penalty = 5% of the relation score (floored), minimum 1 and maximum 5.
 *   - Population growth  = twice the separate 10% relation scale (may go negative -- colony size never
 *                          drops, refugees just thin out).
 *
 * Refugee Processing Center reads getRefugeeStabilityPenalty() to preserve its current net -1
 * mitigation. Security Centers now grants a generic stability bonus instead. Future hook: this condition
 * should be removable as the sector's refugee crisis is resolved.
 */
public class ExileRefugeeInflux extends BaseMarketConditionPlugin implements MarketImmigrationModifier {

    public static final String CONDITION_NAME = "Refugee influx";
    public static final String CONDITION_ID = "exile_refugee_influx";
    public static final String REFUGEE_FACTION = "exilerefugee";

    /** Effects re-sample the player's refugee relation on this cadence, in days. TUNE (30 or 60). */
    public static final float REFRESH_DAYS = 30f;

    // These fields are SAVED only because isTransient() below returns false (bug sweep REF-6,
    // 2026-10-06). Before that the engine stripped them on save, so the old "existing-save
    // compatibility" note on scaleBase was wrong. The name scaleBase is kept as-is.
    private String modId = null;
    // Owns population growth only.
    private int scaleBase = 0;         // 10% of the relation score, floored; -10..10. Can be negative.
    private int stabilityPenalty = 1;  // 5% of positive relation, floored; always clamped to 1..5.
    private float sinceRefresh = 0f;

    /**
     * Population-growth scale: 10% of the player's relation score with the refugees, rounded down
     * (relation -100..100 -> -10..10). Kept separate so stability can use its slower scale.
     */
    /**
     * Persist the plugin (bug sweep REF-6; the ExileTimedCondition fix of 2026-09-22). Vanilla's
     * default is transient, which strips modId and the refresh clock on save, so after a load the
     * 30-day refresh did nothing until the next apply(). Dormant today (nothing applies this
     * condition yet); fixed before the refugee-trust quests start using it.
     */
    @Override
    public boolean isTransient() {
        return false;
    }

    public static int getRefugeeRelationScale() {
        if (Global.getSector() == null) return 0;
        float rel = Global.getSector().getPlayerFaction().getRelationship(REFUGEE_FACTION);
        return (int) Math.floor(rel * 10f);
    }

    /** One stability point per 20 positive relation, floored and clamped to 1..5. */
    public static int getRefugeeStabilityPenalty() {
        if (Global.getSector() == null) return 1;
        float rel = Global.getSector().getPlayerFaction().getRelationship(REFUGEE_FACTION);
        int penalty = (int) Math.floor(rel * 5f);
        return Math.max(1, Math.min(5, penalty));
    }

    public int getStabilityPenalty() {
        // The clamp also protects an existing save whose newly added field initially deserializes as zero.
        return Math.max(1, Math.min(5, stabilityPenalty));
    }

    /** Twice the unchanged 10% growth scale; may be negative (size never drops). */
    public int getGrowthBonus() {
        return 2 * scaleBase;
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        modId = id;

        scaleBase = getRefugeeRelationScale();
        stabilityPenalty = getRefugeeStabilityPenalty();
        sinceRefresh = 0f;

        market.addTransientImmigrationModifier(this);
        market.getStability().modifyFlat(id, -getStabilityPenalty(), CONDITION_NAME);
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;

        market.removeTransientImmigrationModifier(this);
        market.getStability().unmodify(id);
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);
        if (market == null || modId == null) return;

        sinceRefresh += Global.getSector().getClock().convertToDays(amount);
        if (sinceRefresh < REFRESH_DAYS) return;
        sinceRefresh = 0f;

        // Re-sample relation; modifyFlat with the same id overwrites the existing stability mod.
        scaleBase = getRefugeeRelationScale();
        stabilityPenalty = getRefugeeStabilityPenalty();
        market.getStability().modifyFlat(modId, -getStabilityPenalty(), CONDITION_NAME);
    }

    @Override
    public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
        int bonus = getGrowthBonus();

        // Track arriving refugees as "poor" population (flavor) only when they are actually arriving.
        if (bonus > 0) {
            incoming.add(Factions.POOR, bonus);
        }

        // The actual growth lever. Negative bonus slows/stops immigration; it does not shrink the colony.
        incoming.getWeight().modifyFlat(getModId(), bonus, getConditionNameForTooltip());
    }

    protected String getConditionNameForTooltip() {
        return Misc.ucFirst(condition.getName().toLowerCase());
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);

        float opad = 10f;

        tooltip.addPara(
               "Their numbers reflect how much the refugees trust you -- more arrivals mean "
              + "faster growth, but greater strain on order and local resources.",
                opad
        );

        tooltip.addPara(
                "%s stability",
                opad,
                Misc.getHighlightColor(),
                "-" + getStabilityPenalty()
        );

        int growth = getGrowthBonus();
        tooltip.addPara(
                "%s population growth",
                opad,
                Misc.getHighlightColor(),
                (growth >= 0 ? "+" : "") + growth
        );
    }
}
