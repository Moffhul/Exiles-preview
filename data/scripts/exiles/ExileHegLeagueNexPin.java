package data.scripts.exiles;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;

import exerelin.campaign.DiplomacyManager;

/**
 * Layer B2 of the Heg-League premise pins (docs/EXILES_HEG_LEAGUE_WAR.md sec 7.2): the runtime
 * Nexerelin maximum-relationship pin that keeps the Hegemony and the Persean League below the
 * ceasefire threshold no matter what Nex diplomacy rolls.
 *
 * <p><b>THIS CLASS IMPORTS NEXERELIN AND MUST NEVER BE REFERENCED BY NAME FROM ANY OTHER
 * EXILES CLASS.</b> It is loaded only by {@link ExileHegLeagueNexSupport} via
 * {@code getScriptClassLoader().loadClass(...)} on a string, and only after
 * {@code isModEnabled("nexerelin")} passes. Janino compiles loose scripts lazily and per class,
 * so with Nexerelin absent this file is never compiled and never loaded. (Same arrangement as
 * {@code ExilesNexStart}, which Nex itself instantiates by name.)
 *
 * <p>Verified against Nexerelin 0.12.1d sources:
 * <ul>
 *   <li>{@code DiplomacyManager.getManager()} is static; {@code modifyMaxRelationshipMod} and
 *       {@code getMaxRelationshipMod} are instance methods (the spec wrote the former as a
 *       static call -- corrected here).</li>
 *   <li>{@code modifyMaxRelationshipMod} re-adds the modifier's current value, i.e. it
 *       ACCUMULATES. The caller's persistent once-flag is what makes save/load/save idempotent
 *       (test D4).</li>
 *   <li>{@code getMaxRelationship} takes {@code min(mod1, mod2) + baseMax} and clamps at -1, so
 *       pinning ONE direction is enough to hold {@code canCeasefire} false.</li>
 * </ul>
 */
public class ExileHegLeagueNexPin implements ExileHegLeagueNexHook {

    public boolean applyPin(String modifierId, float mod, String factionId,
                            String otherFactionId, String desc) {
        try {
            DiplomacyManager manager = DiplomacyManager.getManager();
            if (manager == null) return false;
            manager.modifyMaxRelationshipMod(modifierId, mod, factionId, otherFactionId, desc);
            return true;
        } catch (Throwable t) {
            Global.getLogger(ExileHegLeagueNexPin.class).warn(
                    "Exiles Heg-League: Nexerelin relation pin could not be applied", t);
            return false;
        }
    }

    public boolean removePin(String modifierId, String factionId, String otherFactionId) {
        try {
            DiplomacyManager manager = DiplomacyManager.getManager();
            if (manager == null) return false;
            MutableStat stat = manager.getMaxRelationshipMod(factionId, otherFactionId);
            // No stat means nothing of ours is installed -- removal has already succeeded.
            if (stat == null) return true;
            stat.unmodifyFlat(modifierId);
            return true;
        } catch (Throwable t) {
            Global.getLogger(ExileHegLeagueNexPin.class).warn(
                    "Exiles Heg-League: Nexerelin relation pin could not be removed", t);
            return false;
        }
    }

    public float readPin(String factionId, String otherFactionId) {
        try {
            DiplomacyManager manager = DiplomacyManager.getManager();
            if (manager == null) return 0f;
            return manager.getMaxRelationshipModValue(factionId, otherFactionId);
        } catch (Throwable t) {
            return 0f;
        }
    }
}
