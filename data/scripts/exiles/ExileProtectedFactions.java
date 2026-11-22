package data.scripts.exiles;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * Factions that NPC faction operations must NEVER directly target.
 *
 * <p>AUTHOR RULING 2026-07-29 (canon, not a tuning knob): <b>the Refugee faction is never a direct
 * target of any NPC faction.</b> Each faction has its own reason, so this is not a blanket plot
 * shield:
 * <ul>
 *   <li><b>Omicron</b> — the refugees are so weak they never rise to the network's notice.</li>
 *   <li><b>Black Fleet</b> — they are too poor to be worth the effort.</li>
 *   <li><b>Ashen Crusade</b> — actively recruiting from them, so it will not raid them.</li>
 * </ul>
 *
 * <p>This lives in code rather than config because it is settled canon: a config list implies the
 * rule is tunable, and an operator clearing that list should not be able to expose the refugees.
 * Per-director config exemptions (e.g. Omicron's {@code operations.collateralExempt}) still exist
 * for factions that are exempt by BALANCE rather than by canon; this check runs first and is
 * unconditional.
 *
 * <p>Because the check is evaluated at targeting time rather than baked into an operation when it
 * launches, it also corrects operations already in flight on an existing save.
 *
 * <p>Fixes review finding 3 (2026-07-28): Black Fleet assault multi-sack accepted any hostile
 * market in the system as a follow-up target, so a refugee enclave sharing a system with an
 * Independent primary (Corvus, Mairaath) was a legal secondary sack.
 *
 * @see data.scripts.exiles.BlackFleetAssaultStage
 * @see data.scripts.exiles.OmicronRaidActionStage
 */
public class ExileProtectedFactions {

    public static final String REFUGEES = "exilerefugee";

    /** True if NPC faction operations must not directly target this faction. */
    public static boolean isProtected(String factionId) {
        return REFUGEES.equals(factionId);
    }

    /** Convenience overload; null-safe for markets without a faction. */
    public static boolean isProtected(MarketAPI market) {
        if (market == null) return false;
        return isProtected(market.getFactionId());
    }

    private ExileProtectedFactions() {
    }
}
