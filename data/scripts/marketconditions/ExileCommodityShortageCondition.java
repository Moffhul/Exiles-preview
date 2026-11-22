package data.scripts.marketconditions;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Shared "shipments are not arriving" behaviour: a fixed set of always-hit spacer essentials
 * plus the commodities this particular market most depends on importing, each taking a flat
 * availability penalty for the condition's lifetime (PASS 5a, §14.5).
 *
 * <p><b>Three verbatim copies existed</b> -- {@link ExileCorsairActivity},
 * {@link ExileCrusadeHarassment} and {@link ExileCrusadeStrike} each carried an identical
 * commodity picker, an identical apply/unapply pair, and an identical flipped-grid tooltip
 * block. That is the rule of three met by measurement rather than by argument, which is why
 * this layer exists and the accessibility penalty below does not.
 *
 * <p><b>Accessibility deliberately lives in the subclasses.</b> Corsair Activity and Crusade
 * Harassment both crater accessibility; Crusade Strike does not -- sabotage burns a warehouse,
 * it does not picket the approaches. Folding accessibility in here would have fitted two of the
 * three consumers and quietly changed the third, so the two that want it add two lines in their
 * own {@code apply()}/{@code unapply()} around a {@code super} call.
 *
 * <p>The hit list is chosen once, when the condition first applies, and held for its lifetime
 * so the market screen does not reshuffle under the player.
 */
public abstract class ExileCommodityShortageCondition extends ExileTimedCondition {

    /** Commodity ids taking the availability hit; picked on first apply, then held stable. */
    protected List hitCommodityIds = null; // String
    public int getHitFor(String commodityId) {
        return hitCommodityIds != null && hitCommodityIds.contains(commodityId) ? getHitUnits() : 0;
    }

    /** Display name used on the stat modifiers. */
    public abstract String getConditionName();

    /** How many import-dependent commodities to hit, on top of the always-hit list. */
    public abstract int getHitCount();

    /** Availability units removed from each hit commodity (vanilla trade-loss hits are -2). */
    public abstract int getHitUnits();

    /** Commodities hit on every market that trades in them at all. */
    protected abstract String[] getAlwaysHit();

    @Override
    public void apply(String id) {
        if (market == null) return;
        if (hitCommodityIds == null) {
            hitCommodityIds = pickImportDependentCommodities(market, getAlwaysHit(), getHitCount());
        }
        int units = getHitUnits();
        String name = getConditionName();
        for (int i = 0; i < hitCommodityIds.size(); i++) {
            CommodityOnMarketAPI com = market.getCommodityData((String) hitCommodityIds.get(i));
            if (com == null) continue;
            com.getAvailableStat().modifyFlat(id, -units, name);
        }
    }

    /**
     * STATELESS on purpose (2026-09-22). The remembered list is kept for the tooltip and to hold
     * the pick stable across reapplies, but removal must never depend on it: the plugin used to be
     * rebuilt empty on load, and a null list here is what left 41 orphaned modifiers in one save.
     * Walking every commodity and removing this instance's id is ~30 hash-map removals, each a
     * no-op where the id was never applied, and runs only on removal or a stability change.
     */
    @Override
    public void unapply(String id) {
        if (market == null) return;
        List all = market.getCommoditiesCopy();
        for (int i = 0; i < all.size(); i++) {
            CommodityOnMarketAPI com = (CommodityOnMarketAPI) all.get(i);
            if (com == null) continue;
            com.getAvailableStat().unmodifyFlat(id);
        }
    }

    /**
     * The always-hit essentials (any market that trades in them at all), then the commodities
     * this market most depends on shipping for: demanded above what it produces locally, ranked
     * by that shortfall. Crew and marines are excluded -- a raid-driven "crew shortage" reads
     * wrong on the market screen. Plain selection loops, Janino-style.
     */
    protected static List pickImportDependentCommodities(
            MarketAPI market, String[] alwaysHit, int count) {
        List result = new ArrayList(); // String ids

        if (alwaysHit != null) {
            for (int i = 0; i < alwaysHit.length; i++) {
                CommodityOnMarketAPI com = market.getCommodityData(alwaysHit[i]);
                if (com == null) continue;
                if (com.getMaxDemand() <= 0 && com.getMaxSupply() <= 0) continue; // truly no trade
                result.add(alwaysHit[i]);
            }
        }

        List candidates = new ArrayList(market.getCommoditiesCopy());
        for (int picks = 0; picks < count; picks++) {
            CommodityOnMarketAPI best = null;
            int bestShortfall = 0;
            for (int i = 0; i < candidates.size(); i++) {
                CommodityOnMarketAPI com = (CommodityOnMarketAPI) candidates.get(i);
                if (com == null || com.isNonEcon()) continue;
                String cid = com.getId();
                if (Commodities.CREW.equals(cid) || Commodities.MARINES.equals(cid)) continue;
                if (result.contains(cid)) continue;
                int shortfall = com.getMaxDemand() - com.getMaxSupply();
                if (com.getMaxDemand() <= 0 || shortfall <= 0) continue;
                if (best == null || shortfall > bestShortfall) {
                    best = com;
                    bestShortfall = shortfall;
                }
            }
            if (best == null) break;
            result.add(best.getId());
        }
        return result;
    }

    /**
     * The shared tooltip block: an intro line in the subclass's own voice, then the same
     * flipped grid vanilla's Trade Disruption tooltip uses -- one commodity per row, value
     * column. No-op when nothing is being hit.
     */
    protected void addShortageGrid(TooltipMakerAPI tooltip, float pad, String intro) {
        if (hitCommodityIds == null || hitCommodityIds.isEmpty()) return;

        tooltip.addPara(intro, pad);
        tooltip.beginGridFlipped(400f, 1, 30f, pad);
        int row = 0;
        for (int i = 0; i < hitCommodityIds.size(); i++) {
            String name;
            try {
                name = Global.getSettings()
                        .getCommoditySpec((String) hitCommodityIds.get(i)).getName();
            } catch (Exception e) {
                name = (String) hitCommodityIds.get(i);
            }
            tooltip.addToGrid(0, row++, name, "-" + getHitUnits(), Misc.getHighlightColor());
        }
        tooltip.addGrid(3f);
    }
}
