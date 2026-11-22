package data.scripts.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.JumpPointInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.util.Misc.Token;

/** Exiles-only start package and vanilla Galatia completion, with no vanilla start bonuses. */
public class ExileAddStartingScript extends BaseCommandPlugin {
    public static final int START_CREW = 100;
    public static final int START_MACHINERY = 10;
    public static final int START_SUPPLIES = 45;
    public static final int START_FUEL = 30;
    public static final int START_CREDITS = 20000;

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null) return false;
        CharacterCreationData data = (CharacterCreationData)
                memoryMap.get(MemKeys.LOCAL).get("$characterData");

        addStartingPackage(data);
        AddRemoveCommodity.addCommodityGainText(Commodities.CREW, START_CREW, dialog.getTextPanel());
        AddRemoveCommodity.addCommodityGainText(Commodities.HEAVY_MACHINERY, START_MACHINERY, dialog.getTextPanel());
        AddRemoveCommodity.addCommodityGainText(Commodities.SUPPLIES, START_SUPPLIES, dialog.getTextPanel());
        AddRemoveCommodity.addCommodityGainText(Commodities.FUEL, START_FUEL, dialog.getTextPanel());
        AddRemoveCommodity.addCreditsGainText(START_CREDITS, dialog.getTextPanel());

        // Like vanilla's NGCAddStandardStartingScript, this runs once the sector exists.
        // Do not rely on the CSV selection-time state for campaign-world changes.
        data.addScript(new Script() {
            public void run() {
                CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
                fleet.getFleetData().ensureHasFlagship();
                for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
                    member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
                }
                fleet.getFleetData().setSyncNeeded();

                completeGalatiaIfNeeded();
            }
        });
        return true;
    }

    public static void addStartingPackage(CharacterCreationData data) {
        data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.CREW, START_CREW);
        data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.HEAVY_MACHINERY, START_MACHINERY);
        data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.SUPPLIES, START_SUPPLIES);
        data.getStartingCargo().addItems(CargoItemType.RESOURCES, Commodities.FUEL, START_FUEL);
        data.getStartingCargo().getCredits().add(START_CREDITS);
    }

    /** The vanilla and Nex Exiles starts share this one-time Galatia normalization. */
    public static void completeGalatiaIfNeeded() {
        StarSystemAPI system = Global.getSector().getStarSystem("galatia");
        if (system == null) return; // possible in a Nex random-sector campaign
        PlanetAPI ancyra = (PlanetAPI) system.getEntityById("ancyra");
        SectorEntityToken inner = system.getEntityById("galatia_jump_point_alpha");
        SectorEntityToken fringe = system.getEntityById("galatia_jump_point_fringe");
        if (ancyra == null || ancyra.getMarket() == null || inner == null || fringe == null) return;
        boolean stillTutorial = system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
                || inner.getMemoryWithoutUpdate().getBoolean(JumpPointInteractionDialogPluginImpl.UNSTABLE_KEY)
                || fringe.getMemoryWithoutUpdate().getBoolean(JumpPointInteractionDialogPluginImpl.UNSTABLE_KEY);
        if (!stillTutorial) return;

        // Match vanilla's contact creation before Galatia completion, while withholding
        // its personal relationship bonuses.
        TutorialMissionIntel.createMainContact(ancyra);
        TutorialMissionIntel.getJangalaContact();

        // Vanilla's completion method restores Galatia's economy, jump points,
        // patrols, relay, local fleets, and starting abilities. Preserve the
        // Hegemony relationship across its tutorial-specific neutralization.
        float hegRel = Global.getSector().getFaction(Factions.HEGEMONY)
                .getRelToPlayer().getRel();
        TutorialMissionIntel.endGalatiaPortionOfMission(false, false);
        Global.getSector().getFaction(Factions.HEGEMONY)
                .getRelToPlayer().setRel(hegRel);
    }

    /** Called from onNewGameAfterTimePass, retaining Exiles' existing relation timing. */
    public static void applyStartingRelations() {
        // Deferred live-play values preserved from ExilesModPlugin; keep inactive during testing.
        // Global.getSector().getPlayerFaction().setRelationship(Factions.HEGEMONY, -1.0f);
        // Global.getSector().getPlayerFaction().setRelationship(Factions.INDEPENDENT, -0.80f);
        // Global.getSector().getPlayerFaction().setRelationship(Factions.LUDDIC_CHURCH, -0.70f);
        Global.getSector().getPlayerFaction().setRelationship(Factions.LUDDIC_PATH, -0.65f);
        // Global.getSector().getPlayerFaction().setRelationship(Factions.PERSEAN, -0.80f);
        Global.getSector().getPlayerFaction().setRelationship(Factions.PIRATES, 1.0f);
        // Global.getSector().getPlayerFaction().setRelationship(Factions.DIKTAT, -0.55f);
        // Global.getSector().getPlayerFaction().setRelationship(Factions.TRITACHYON, -0.60f);
    }
}
