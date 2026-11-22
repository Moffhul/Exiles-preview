package data.scripts.rulecmd;

import java.util.List;
import java.util.Map;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc.Token;

/** Carry the selected Exile start into the newly created sector, before mod initialization. */
public class ExileStartingFines extends BaseCommandPlugin {
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        CharacterCreationData data = (CharacterCreationData)
                memoryMap.get(MemKeys.LOCAL).get("$characterData");
        data.addScriptBeforeTimePass(new Script() {
            public void run() {
                Global.getSector().getMemoryWithoutUpdate().set("$exiles_starting_fines_eligible", true);
                Global.getLogger(ExileStartingFines.class).info("Exiles starting fines: campaign marked eligible");
            }
        });
        return true;
    }
}
