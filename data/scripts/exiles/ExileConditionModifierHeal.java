package data.scripts.exiles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;

/**
 * LOAD-TIME HEAL for stat modifiers orphaned by Exiles market conditions (2026-09-22).
 *
 * <p><b>Why it exists.</b> Until {@code ExileTimedCondition.isTransient()} returned false, the
 * engine rebuilt every Exiles timed condition's plugin EMPTY on load, so a post-load
 * {@code unapply()} could not find the commodities it had hit and the pre-load modifiers stayed on
 * the market for the life of the save (docs/EXILES_HEG_LEAGUE_WAR.md sec 14.12.3: 41 orphans across
 * three conditions in one save). Saves made before the fix carry those orphans; this strips them.
 *
 * <p><b>What it removes, and only that.</b> A modifier whose source id is
 * {@code <conditionId>_<uid>} where the condition id starts with {@code exile}, names a real
 * market-condition spec, and no condition instance with that exact source id is live on the
 * market. It looks at the three stats Exiles conditions modify: commodity availability (flat),
 * market accessibility (flat) and the player supply price (mult). Vanilla and other mods' ids are
 * never touched.
 *
 * <p><b>Cost.</b> Once per game load, ~90 economy markets x ~30 commodities of hash-map key scans;
 * no allocation in any per-frame path and nothing retained. Runs every load rather than once, so a
 * future orphan from a path nobody has found yet is caught the next time the game loads. Each
 * market is healed inside its own try/catch: a heal can never block a load.
 */
public class ExileConditionModifierHeal {

    public static final String EXILES_PREFIX = "exile";

    private ExileConditionModifierHeal() {
    }

    /** Sweep every economy market; log one line only when something was removed. */
    public static void run() {
        if (Global.getSector() == null || Global.getSector().getEconomy() == null) return;
        int removed = 0;
        int marketsTouched = 0;
        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market == null) continue;
            try {
                int here = healMarket(market);
                if (here > 0) {
                    removed += here;
                    marketsTouched++;
                }
            } catch (Exception ex) {
                Global.getLogger(ExileConditionModifierHeal.class).warn(
                        "Exiles condition heal skipped " + market.getId() + ": " + ex);
            }
        }
        if (removed > 0) {
            Global.getLogger(ExileConditionModifierHeal.class).info(
                    "Exiles condition heal: removed " + removed
                            + " orphaned modifier(s) on " + marketsTouched + " market(s)");
        }
    }

    /** @return the number of orphaned modifiers removed from this market. */
    protected static int healMarket(MarketAPI market) {
        Set live = new HashSet(); // String: idForPluginModifications of every live condition
        List conditions = market.getConditions();
        for (int i = 0; i < conditions.size(); i++) {
            MarketConditionAPI cond = (MarketConditionAPI) conditions.get(i);
            if (cond != null && cond.getIdForPluginModifications() != null) {
                live.add(cond.getIdForPluginModifications());
            }
        }

        int removed = 0;
        removed += healBonus(market.getAccessibilityMod(), live, market, "accessibility");

        List commodities = market.getCommoditiesCopy();
        for (int i = 0; i < commodities.size(); i++) {
            CommodityOnMarketAPI com = (CommodityOnMarketAPI) commodities.get(i);
            if (com == null) continue;
            removed += healStat(com.getAvailableStat(), live, market, com.getId() + " available");
            removed += healBonus(com.getPlayerSupplyPriceMod(), live, market,
                    com.getId() + " player price");
        }
        return removed;
    }

    /** Remove orphaned flat and mult modifiers from one stat. Keys are copied before removal. */
    protected static int healStat(MutableStat stat, Set live, MarketAPI market, String label) {
        if (stat == null) return 0;
        int removed = 0;

        List flatKeys = new ArrayList(stat.getFlatMods().keySet());
        for (int i = 0; i < flatKeys.size(); i++) {
            String source = (String) flatKeys.get(i);
            if (isOrphan(source, live)) {
                stat.unmodifyFlat(source);
                removed++;
                logOne(market, label, source);
            }
        }

        List multKeys = new ArrayList(stat.getMultMods().keySet());
        for (int i = 0; i < multKeys.size(); i++) {
            String source = (String) multKeys.get(i);
            if (isOrphan(source, live)) {
                stat.unmodifyMult(source);
                removed++;
                logOne(market, label, source);
            }
        }
        return removed;
    }

    /** The accessibility twin of {@link #healStat}: market accessibility is a {@code StatBonus},
     *  whose maps and removers carry different names but the same semantics. */
    protected static int healBonus(StatBonus bonus, Set live, MarketAPI market, String label) {
        if (bonus == null) return 0;
        int removed = 0;

        List flatKeys = new ArrayList(bonus.getFlatBonuses().keySet());
        for (int i = 0; i < flatKeys.size(); i++) {
            String source = (String) flatKeys.get(i);
            if (isOrphan(source, live)) {
                bonus.unmodifyFlat(source);
                removed++;
                logOne(market, label, source);
            }
        }

        List multKeys = new ArrayList(bonus.getMultBonuses().keySet());
        for (int i = 0; i < multKeys.size(); i++) {
            String source = (String) multKeys.get(i);
            if (isOrphan(source, live)) {
                bonus.unmodifyMult(source);
                removed++;
                logOne(market, label, source);
            }
        }
        return removed;
    }

    /**
     * An Exiles condition-instance id with no live instance behind it.
     *
     * <p>Three gates, cheapest first: the prefix, then a live-set lookup, then the spec lookup on
     * the part before the last underscore. A source without an underscore, or whose stem is not a
     * registered market condition, is never ours to remove.
     */
    protected static boolean isOrphan(String source, Set live) {
        if (source == null || !source.startsWith(EXILES_PREFIX)) return false;
        if (live.contains(source)) return false;
        int cut = source.lastIndexOf('_');
        if (cut <= 0 || cut >= source.length() - 1) return false;
        String conditionId = source.substring(0, cut);
        return Global.getSettings().getMarketConditionSpec(conditionId) != null;
    }

    protected static void logOne(MarketAPI market, String label, String source) {
        Global.getLogger(ExileConditionModifierHeal.class).info(
                "Exiles condition heal: " + market.getName() + " -- removed orphan " + source
                        + " from " + label);
    }
}
