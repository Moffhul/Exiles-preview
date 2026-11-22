package data.scripts.industries;

import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;

/**
 * Inert "broken" placeholder industry -- backs BOTH exile_broken_siphon and exile_broken_processor (one class,
 * several IDs). Produces and demands nothing: defunct Domain-era infrastructure on the Yma refugee Siphon Station
 * that the refugees can't yet operate. A Phase 2 questline (the settlement's engineer) brings each one online by
 * swapping it for its working counterpart:
 *   exile_broken_siphon    -> exile_makeshift_gas_mining   (3 volatiles)
 *   exile_broken_processor -> exile_juryrigged_processor   (3 fuel from 3 volatiles)
 * The swap is a scripted market.removeIndustry(brokenId) + market.addIndustry(workingId) -- NOT the vanilla
 * player-driven upgrade path (this is an NPC colony). NPC-only; starts as the only "industries" on the colony.
 */
public class ExileBrokenStructure extends BaseIndustry {

    @Override
    public void apply() {
        super.apply(true);
        // Intentionally inert: produces nothing, demands nothing. Awaits a quest to bring it online.
    }

    @Override
    public void unapply() {
        super.unapply();
    }

    @Override
    public boolean isAvailableToBuild() {
        return false;
    }

    @Override
    public String getUnavailableReason() {
        return "Defunct -- requires repair.";
    }

    @Override
    public boolean showWhenUnavailable() {
        return false;
    }
}
