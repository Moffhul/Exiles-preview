package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.GenericPluginManagerAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.BaseGenerateFleetOfficersPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.GenerateFleetOfficersPlugin.GenerateFleetOfficersPickData;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/**
 * Omicron-only fleet officer generator, registered through the same generic-plugin seam
 * used by vanilla Remnant and Omega fleets. It never handles another faction.
 */
public class ExileOmicronFleetOfficerGenerator extends BaseGenerateFleetOfficersPlugin {

    /** Grade III is reserved for the capital flagship of a genuinely major fleet. */
    public static final float GRADE_III_MIN_FLEET_FP = 150f;

    public int getHandlingPriority(Object params) {
        if (!(params instanceof GenerateFleetOfficersPickData)) return -1;
        GenerateFleetOfficersPickData data = (GenerateFleetOfficersPickData) params;
        if (data.fleet == null || data.fleet.getFaction() == null) return -1;
        if (!OmicronDirector.OMICRON_FACTION.equals(data.fleet.getFaction().getId())) return -1;
        if (data.params != null && !data.params.withOfficers) return -1;
        return GenericPluginManagerAPI.MOD_SUBSET;
    }

    public void addCommanderAndOfficers(CampaignFleetAPI fleet, FleetParamsV3 params, Random random) {
        if (random == null) random = new Random();

        List<FleetMemberAPI> eligible = new ArrayList<FleetMemberAPI>();
        float fleetFP = 0f;
        List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI member = (FleetMemberAPI) members.get(i);
            fleetFP += member.getFleetPointCost();
            if (member.isFighterWing() || member.isCivilian()) continue;
            if (!Misc.isAutomated(member)) continue;
            if (member.getCaptain() != null && !member.getCaptain().isDefault()) continue;
            eligible.add(member);
        }
        if (eligible.isEmpty()) return;

        float fpPerCore = Global.getSettings().getFloat("baseFPPerAICore");
        float numberMult = params == null ? 1f : params.officerNumberMult;
        int numberBonus = params == null ? 0 : params.officerNumberBonus;
        int numCores = (int) (fleetFP / fpPerCore * numberMult) + numberBonus;
        if (numCores < 1) numCores = 1;
        if (numCores > eligible.size()) numCores = eligible.size();

        FleetMemberAPI gradeIIIFlagship = pickGradeIIIFlagship(eligible, fleetFP, random);

        WeightedRandomPicker<FleetMemberAPI> shipPicker = new WeightedRandomPicker<FleetMemberAPI>(random);
        for (int i = 0; i < eligible.size(); i++) {
            FleetMemberAPI member = (FleetMemberAPI) eligible.get(i);
            if (member == gradeIIIFlagship) continue;
            shipPicker.add(member, Math.max(1f, member.getFleetPointCost()));
        }

        List<FleetMemberAPI> assigned = new ArrayList<FleetMemberAPI>();
        boolean integrate = params == null || !params.doNotIntegrateAICores;

        if (gradeIIIFlagship != null
                && assignCore(gradeIIIFlagship, ExileOmicronCoreIds.GRADE_III, fleet, integrate, random)) {
            assigned.add(gradeIIIFlagship);
        }

        while (assigned.size() < numCores && !shipPicker.isEmpty()) {
            FleetMemberAPI member = (FleetMemberAPI) shipPicker.pickAndRemove();
            String coreId = pickCoreForShip(member, fleetFP, random);
            if (assignCore(member, coreId, fleet, integrate, random)) {
                assigned.add(member);
            }
        }
        if (assigned.isEmpty()) return;

        FleetMemberAPI flagship = null;
        if (gradeIIIFlagship != null && assigned.contains(gradeIIIFlagship)) {
            flagship = gradeIIIFlagship;
        } else {
            WeightedRandomPicker<FleetMemberAPI> commanderPicker =
                    new WeightedRandomPicker<FleetMemberAPI>(random);
            for (int i = 0; i < assigned.size(); i++) {
                FleetMemberAPI member = (FleetMemberAPI) assigned.get(i);
                float gradeWeight = gradeWeight(member.getCaptain().getAICoreId());
                commanderPicker.add(member, Math.max(1f, member.getFleetPointCost()) * gradeWeight);
            }
            flagship = (FleetMemberAPI) commanderPicker.pick();
        }

        PersonAPI commander = flagship.getCaptain();
        commander.setRankId(Ranks.SPACE_COMMANDER);
        commander.setPostId(Ranks.POST_FLEET_COMMANDER);
        fleet.setCommander(commander);
        fleet.getFleetData().setFlagship(flagship);

        int commanderSkills = 0;
        if (fleetFP > 75f) commanderSkills++;
        if (fleetFP > 150f) commanderSkills++;
        if (params != null && params.noCommanderSkills != null && params.noCommanderSkills) {
            commanderSkills = 0;
        }
        RemnantOfficerGeneratorPlugin.addCommanderSkills(
                commander, fleet, params, commanderSkills, random);
    }

    private static FleetMemberAPI pickGradeIIIFlagship(List<FleetMemberAPI> eligible,
                                                        float fleetFP, Random random) {
        if (fleetFP <= GRADE_III_MIN_FLEET_FP) return null;

        WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<FleetMemberAPI>(random);
        for (int i = 0; i < eligible.size(); i++) {
            FleetMemberAPI member = (FleetMemberAPI) eligible.get(i);
            if (member.isCapital()) {
                picker.add(member, Math.max(1f, member.getFleetPointCost()));
            }
        }
        if (picker.isEmpty()) return null;
        return (FleetMemberAPI) picker.pick();
    }

    private static boolean assignCore(FleetMemberAPI member, String coreId,
                                      CampaignFleetAPI fleet, boolean integrate, Random random) {
        AICoreOfficerPlugin plugin = Misc.getAICoreOfficerPlugin(coreId);
        if (plugin == null) {
            Global.getLogger(ExileOmicronFleetOfficerGenerator.class).warn(
                    "No AI officer plugin for " + coreId + "; Omicron ship left unofficered.");
            return false;
        }

        PersonAPI officer = plugin.createPerson(coreId, fleet.getFaction().getId(), random);
        member.setCaptain(officer);
        if (integrate) {
            RemnantOfficerGeneratorPlugin.integrateAndAdaptCoreForAIFleet(member);
        }
        return true;
    }

    private static String pickCoreForShip(FleetMemberAPI member, float fleetFP, Random random) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
        if (member.isCapital()) {
            picker.add(ExileOmicronCoreIds.GRADE_II, 1f);
        } else if (member.isCruiser()) {
            picker.add(ExileOmicronCoreIds.GRADE_II, 4f);
            picker.add(ExileOmicronCoreIds.GRADE_I, 0.5f);
        } else if (member.isDestroyer()) {
            picker.add(ExileOmicronCoreIds.GRADE_II, fleetFP > 75f ? 3f : 1f);
            picker.add(ExileOmicronCoreIds.GRADE_I, 3f);
        } else {
            picker.add(ExileOmicronCoreIds.GRADE_I, 5f);
            if (fleetFP > 75f) picker.add(ExileOmicronCoreIds.GRADE_II, 0.5f);
        }
        return (String) picker.pick();
    }

    private static float gradeWeight(String coreId) {
        if (ExileOmicronCoreIds.GRADE_III.equals(coreId)) return 4f;
        if (ExileOmicronCoreIds.GRADE_II.equals(coreId)) return 2f;
        return 1f;
    }
}
