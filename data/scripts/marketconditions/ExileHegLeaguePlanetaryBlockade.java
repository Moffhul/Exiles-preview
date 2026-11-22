package data.scripts.marketconditions;

import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Hegemony-Persean League PLANETARY BLOCKADE effect, "the squeeze" -- PASS 6 of
 * docs/EXILES_HEG_LEAGUE_WAR.md sec 4.4 / sec 8. Applied by
 * {@code data.scripts.exiles.ExileHegLeagueSqueezeStage} to the ONE anchor market a cordon is
 * parked over; lifted the tick the cordon withdraws or is driven off.
 *
 * <p><b>One market, not a system.</b> That is the whole distinction between this and the two
 * conditions beside it (sec 14.11, D80.10 item 3): {@link ExileHegLeagueInterdiction} lands on
 * every market the victim owns in the system, vanilla {@code Blockaded} plus
 * {@link ExileHegLeagueBlockadeCollateral} land on everything in the system, and this one lands on
 * the single colony the warships are sitting over. "Planetary" beside "system" means exactly that.
 *
 * <p><b>Its own id, never a shared one</b> (ruling S5, inherited from Ashen ruling E.c): separate
 * ids keep the lifecycles independent and let each tooltip name its own culprit.
 *
 * <p><b>Two effects</b> (sec 4.4, D80.4):
 * <ul>
 * <li>{@code -ACCESSIBILITY_PENALTY} flat on the market's accessibility. Accessibility IS vanilla's
 *     import/export throttle, and -0.5 is the ratified figure the three sibling presence conditions
 *     already use -- -0.2 proved too weak to visibly choke imports in the Black Fleet playtest.</li>
 * <li>{@code PLAYER_BUY_PRICE_MULT} on what the PLAYER pays for every commodity bought here. The
 *     mechanism is {@link CommodityOnMarketAPI#getPlayerSupplyPriceMod()} -- <b>a per-commodity
 *     method, not a market-level one</b>; sec 4.4 and sec 8 both named it on {@code MarketAPI},
 *     which does not have it (D80.10 item 1, docs corrected in this pass). Player price mods never
 *     touch the NPC economy sim, so this is purely what the cordon costs the player at the docks.
 *     Player SELL prices are deliberately untouched, following {@link ExilePriceSpikes}.</li>
 * </ul>
 *
 * <p><b>It holds NO reference to the intel.</b> Like the blockade collateral condition, and for the
 * same reason: a condition that reaches back into a live operation is impossible to reason about
 * once that operation is gone. The stage lifts this one on withdrawal; if the stage is somehow lost
 * -- console-removed intel, a corrupted op -- {@link #getDurationDays()} expires it at
 * {@link #DURATION_DAYS} and both mods unwind (Ashen Ruling F discipline). A dead operation can
 * never leave a permanent penalty behind.
 *
 * <p><b>Either belligerent can cause it</b>, so the acting faction is recorded on the market's
 * memory under {@link #ACTOR_KEY} -- the {@link ExileHegLeagueInterdiction#ACTOR_KEY} pattern --
 * and the tooltip names it from there, degrading to neutral wording rather than lying when the key
 * is gone.
 *
 * <p><b>Display name and description are PLACEHOLDERS</b> (R12: player-facing text is an
 * outstanding author deliverable, sec 11 item 2). They are deliberately faction-neutral so they
 * read correctly whichever side is doing the squeezing.
 *
 * <p>Tunables are static and overwritten from config at load by
 * {@code ExileHegLeagueSqueezeManager.applyConditionTunables()}, so the whole condition is
 * player-adjustable without touching Java.
 */
public class ExileHegLeaguePlanetaryBlockade extends ExileTimedCondition {

    public static final String CONDITION_ID = "exile_hl_planetary_blockade";
    /** PLACEHOLDER -- author deliverable (sec 11 item 2). */
    public static final String CONDITION_NAME = "Planetary Blockade";

    /** Market memory: which faction's warships are responsible. Expires on its own so a condition
     *  removed by any path cannot leave a stale attribution behind. */
    public static final String ACTOR_KEY = "$exiles_hl_squeeze_actor";

    /** Accessibility penalty while the cordon holds. Matches the ratified -50% the sibling
     *  presence conditions use. */
    public static float ACCESSIBILITY_PENALTY = 0.5f;
    /** Multiplier on every commodity's PLAYER BUY price while the cordon holds. 1.0 disables. */
    public static float PLAYER_BUY_PRICE_MULT = 1.4f;
    /**
     * HARD SELF-EXPIRY BACKSTOP. The stage removes this on withdrawal or abort, so in normal
     * operation the timer never gets close: {@code presenceDays x 1.25 + 30} at the shipped
     * numbers. This is the orphan guard, not the usual path.
     */
    public static float DURATION_DAYS = 80f;

    /**
     * Apply to a market (or refresh its timer), recording which side's warships are responsible.
     *
     * @param actorFactionId the SQUEEZING faction; may be null, in which case the tooltip stays
     *        neutral.
     */
    public static void applyTo(MarketAPI market, String actorFactionId) {
        if (market == null || !market.isInEconomy()) return;
        if (actorFactionId != null) {
            // Outlives the condition by a margin so a refresh near the end of the window cannot
            // drop the attribution, but still expires on its own if the market is somehow left
            // holding the condition through a path that never calls removeFrom().
            market.getMemoryWithoutUpdate().set(
                    ACTOR_KEY, actorFactionId, DURATION_DAYS + 30f);
        }
        applyOrRefresh(market, CONDITION_ID);
    }

    /** Remove from a market if present -- the cordon has withdrawn. */
    public static void removeFrom(MarketAPI market) {
        if (market == null) return;
        market.getMemoryWithoutUpdate().unset(ACTOR_KEY);
        removeIfPresent(market, CONDITION_ID);
    }

    public static boolean isSqueezed(MarketAPI market) {
        return market != null && market.hasCondition(CONDITION_ID);
    }

    /** The faction whose warships are responsible, or null if unrecorded. */
    protected FactionAPI getActorFaction() {
        if (market == null) return null;
        Object id = market.getMemoryWithoutUpdate().get(ACTOR_KEY);
        if (!(id instanceof String)) return null;
        return Global.getSector().getFaction((String) id);
    }

    public String getConditionName() {
        return CONDITION_NAME;
    }

    public float getDurationDays() {
        return DURATION_DAYS;
    }

    @Override
    public void apply(String id) {
        if (market == null) return;
        market.getAccessibilityMod().modifyFlat(id, -ACCESSIBILITY_PENALTY, CONDITION_NAME);
        if (PLAYER_BUY_PRICE_MULT != 1f) {
            // Per COMMODITY, not per market: getPlayerSupplyPriceMod lives on
            // CommodityOnMarketAPI. The classic indexed loop rather than an enhanced for --
            // CLAUDE.md sec 7's Janino discipline, with the cast written out.
            List commodities = market.getCommoditiesCopy();
            for (int i = 0; i < commodities.size(); i++) {
                CommodityOnMarketAPI com = (CommodityOnMarketAPI) commodities.get(i);
                if (com == null) continue;
                com.getPlayerSupplyPriceMod().modifyMult(id, PLAYER_BUY_PRICE_MULT,
                        CONDITION_NAME);
            }
        }
    }

    @Override
    public void unapply(String id) {
        if (market == null) return;
        market.getAccessibilityMod().unmodifyFlat(id);
        // Unmodify unconditionally, whatever the multiplier currently reads: a config edit
        // between apply and unapply must not be able to strand a price mod on a market.
        List commodities = market.getCommoditiesCopy();
        for (int i = 0; i < commodities.size(); i++) {
            CommodityOnMarketAPI com = (CommodityOnMarketAPI) commodities.get(i);
            if (com == null) continue;
            com.getPlayerSupplyPriceMod().unmodify(id);
        }
    }

    @Override
    protected void createTooltipAfterDescription(TooltipMakerAPI tooltip, boolean expanded) {
        super.createTooltipAfterDescription(tooltip, expanded);
        float opad = 10f;

        FactionAPI actor = getActorFaction();
        if (actor != null) {
            tooltip.addPara("%s warships are holding station over this world.", opad,
                    actor.getBaseUIColor(), actor.getDisplayName());
        } else {
            tooltip.addPara("Warships are holding station over this world.", opad);
        }

        tooltip.addPara("%s accessibility", opad, Misc.getHighlightColor(),
                "-" + (int) Math.round(ACCESSIBILITY_PENALTY * 100f) + "%");
        if (PLAYER_BUY_PRICE_MULT != 1f) {
            tooltip.addPara("%s prices for goods bought here", 0f, Misc.getHighlightColor(),
                    "+" + (int) Math.round((PLAYER_BUY_PRICE_MULT - 1f) * 100f) + "%");
        }
    }
}
