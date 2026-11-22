package data.scripts.rulecmd;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.AICores;

/**
 * Vanilla AI-core contact turn-in with grade-specific Omicron reputation values.
 *
 * <p>This class is intentionally self-contained in the precompiled rule-command JAR. Rules are
 * loaded before Starsector compiles loose scripts, so it must not reference Exiles' loose-script
 * classes. All non-Omicron cores delegate to vanilla's {@link AICores#getBaseRepValue(String)}.
 */
public class ExileAICores extends AICores {

    private static final String EXECUTOR_CORE = "exiles_omicron_core_1";
    private static final String CONTROL_CORE = "exiles_omicron_core_2";
    private static final String COMMAND_CORE = "exiles_omicron_core_3";

    private static final float EXECUTOR_REP = 2f;
    private static final float CONTROL_REP = 3f;
    private static final float COMMAND_REP = 5f;

    @Override
    protected float computeCoreReputationValue(CargoAPI cargo) {
        float rep = 0f;
        for (CargoStackAPI stack : cargo.getStacksCopy()) {
            CommoditySpecAPI spec = stack.getResourceIfResource();
            if (spec != null && Commodities.AI_CORES.equals(spec.getDemandClass())) {
                rep += getExilesBaseRepValue(spec.getId()) * stack.getSize();
            }
        }
        rep *= repMult;
        return rep;
    }

    public static float getExilesBaseRepValue(String coreType) {
        if (EXECUTOR_CORE.equals(coreType)) return EXECUTOR_REP;
        if (CONTROL_CORE.equals(coreType)) return CONTROL_REP;
        if (COMMAND_CORE.equals(coreType)) return COMMAND_REP;
        return AICores.getBaseRepValue(coreType);
    }
}
