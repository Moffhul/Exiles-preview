package data.scripts.exiles;

import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.NGCAddStandardStartingScript;
import data.scripts.rulecmd.ExileAddStartingScript;

import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;

/**
 * The Exiles hard start under Nexerelin (docs/EXILES_NEXERELIN_COMPAT.md §3.1 BLOCKER fix):
 * Nex replaces the vanilla new-game flow, so the rules.csv {@code ngcExileStart} path never
 * fires there — this class + data/config/exerelin/customStarts.json is the Nex-native port.
 * Mirrors {@code ngcExilePicked} exactly: wolf_exile + hound_exile, 100 crew / 10 machinery /
 * 45 supplies / 30 fuel, 20000 credits, +1 character point, Normal difficulty, Xechas start
 * point. Starting officers/colonies/patches ride the ModPlugin hooks and need no port.
 *
 * <p>v1 simplifications (deliberate): Normal difficulty only (no Easy branch) and no prologue
 * story pages — the vanilla rules path keeps both for non-Nex games.
 *
 * <p>JANINO NOTE: this class imports Nexerelin classes and is ONLY loaded when Nexerelin
 * itself instantiates it (lazy per-class compile) — verify once that game start works with
 * Nex disabled, per the audit doc.
 */
public class ExilesNexStart extends exerelin.campaign.customstart.CustomStart {

    public void execute(InteractionDialogAPI dialog, Map memoryMap) {
        MemoryAPI local = (MemoryAPI) memoryMap.get(MemKeys.LOCAL);
        CharacterCreationData data = (CharacterCreationData) local.get("$characterData");

        data.addStartingFleetMember("wolf_exile_Starting", FleetMemberType.SHIP);
        data.addStartingFleetMember("hound_exile_Starting", FleetMemberType.SHIP);

        ExileAddStartingScript.addStartingPackage(data);
        data.getPerson().getStats().addPoints(1);

        // Same start point the vanilla rules path uses (NGCSetStartingLocation Xechas).
        data.setStartingLocationName("Xechas");
        data.getStartingCoordinates().set(-2500f, -3000f);

        data.setDifficulty("normal");
        ExerelinSetupData.getInstance().easyMode = false;
        ExerelinSetupData.getInstance().freeStart = true;
        PlayerFactionStore.setPlayerFactionIdNGC(Factions.PLAYER);

        data.addScriptBeforeTimePass(new Script() {
            public void run() {
                Global.getSector().getMemoryWithoutUpdate().set("$exilesNexStart", true);
                Global.getSector().getMemoryWithoutUpdate().set("$exiles_starting_fines_eligible", true);
            }
        });

        // Display fleet + gain text (SpacerStart pattern).
        FleetMemberAPI wolf = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP, "wolf_exile_Starting");
        FleetMemberAPI hound = Global.getFactory().createFleetMember(
                FleetMemberType.SHIP, "hound_exile_Starting");
        CampaignFleetAPI tempFleet = FleetFactoryV3.createEmptyFleet(
                PlayerFactionStore.getPlayerFactionIdNGC(), FleetTypes.PATROL_SMALL, null);
        tempFleet.getFleetData().addFleetMember(wolf);
        tempFleet.getFleetData().addFleetMember(hound);
        tempFleet.getFleetData().setFlagship(wolf);
        wolf.setCaptain(data.getPerson());
        tempFleet.getFleetData().setSyncNeeded();
        tempFleet.getFleetData().syncIfNeeded();
        tempFleet.forceSync();

        AddRemoveCommodity.addFleetMemberGainText(wolf.getVariant(), dialog.getTextPanel());
        AddRemoveCommodity.addFleetMemberGainText(hound.getVariant(), dialog.getTextPanel());
        AddRemoveCommodity.addCommodityGainText(Commodities.CREW, ExileAddStartingScript.START_CREW, dialog.getTextPanel());
        AddRemoveCommodity.addCommodityGainText(Commodities.HEAVY_MACHINERY, ExileAddStartingScript.START_MACHINERY, dialog.getTextPanel());
        AddRemoveCommodity.addCommodityGainText(Commodities.SUPPLIES, ExileAddStartingScript.START_SUPPLIES, dialog.getTextPanel());
        AddRemoveCommodity.addCommodityGainText(Commodities.FUEL, ExileAddStartingScript.START_FUEL, dialog.getTextPanel());
        AddRemoveCommodity.addCreditsGainText(ExileAddStartingScript.START_CREDITS, dialog.getTextPanel());

        data.addScript(new Script() {
            public void run() {
                CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
                NGCAddStandardStartingScript.adjustStartingHulls(fleet);
                fleet.getFleetData().ensureHasFlagship();
                for (int i = 0; i < fleet.getFleetData().getMembersListCopy().size(); i++) {
                    FleetMemberAPI member = (FleetMemberAPI)
                            fleet.getFleetData().getMembersListCopy().get(i);
                    member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
                }
                fleet.getFleetData().setSyncNeeded();
                ExileAddStartingScript.completeGalatiaIfNeeded();
            }
        });

        dialog.getVisualPanel().showFleetInfo("Your fleet", tempFleet, null, null);
        dialog.getOptionPanel().addOption("Done", "nex_NGCDone");
        dialog.getOptionPanel().addOption("Back", "nex_NGCStartBack");
        dialog.getOptionPanel().setShortcut("nex_NGCStartBack",
                Keyboard.KEY_ESCAPE, false, false, false, false);
    }
}
