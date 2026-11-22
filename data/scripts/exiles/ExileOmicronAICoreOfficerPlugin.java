package data.scripts.exiles;

import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.BaseAICoreOfficerPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Skills;

/**
 * Vanilla-style person factory for all three Omicron tactical-core commodities.
 * Each commodity deterministically maps to one portrait, level/cost, and skill package.
 * This keeps cargo installation predictable for the player.
 */
public class ExileOmicronAICoreOfficerPlugin extends BaseAICoreOfficerPluginImpl {

    public PersonAPI createPerson(String aiCoreId, String factionId, Random random) {
        PersonAPI person = Global.getFactory().createPerson();
        person.setFaction(factionId);
        person.setAICoreId(aiCoreId);

        CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(aiCoreId);
        person.setName(new FullName(spec.getName(), "", FullName.Gender.ANY));

        person.getStats().setSkipRefresh(true);

        if (ExileOmicronCoreIds.GRADE_III.equals(aiCoreId)) {
            person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "exile_omicron_3a"));
            person.getStats().setLevel(7);
            addEliteSkill(person, Skills.HELMSMANSHIP);
            addEliteSkill(person, Skills.TARGET_ANALYSIS);
            addEliteSkill(person, Skills.COMBAT_ENDURANCE);
            addEliteSkill(person, Skills.FIELD_MODULATION);
            addEliteSkill(person, Skills.GUNNERY_IMPLANTS);
            addEliteSkill(person, Skills.SYSTEMS_EXPERTISE);
            addEliteSkill(person, Skills.ENERGY_WEAPON_MASTERY);
            person.getMemoryWithoutUpdate().set(AICoreOfficerPlugin.AUTOMATED_POINTS_MULT, 4f);
        } else if (ExileOmicronCoreIds.GRADE_II.equals(aiCoreId)) {
            person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "exile_omicron_2a"));
            person.getStats().setLevel(5);
            addEliteSkill(person, Skills.HELMSMANSHIP);
            addEliteSkill(person, Skills.TARGET_ANALYSIS);
            addEliteSkill(person, Skills.COMBAT_ENDURANCE);
            addEliteSkill(person, Skills.FIELD_MODULATION);
            addEliteSkill(person, Skills.GUNNERY_IMPLANTS);
            person.getMemoryWithoutUpdate().set(AICoreOfficerPlugin.AUTOMATED_POINTS_MULT, 3f);
        } else {
            person.setPortraitSprite(Global.getSettings().getSpriteName("characters", "exile_omicron_1a"));
            person.getStats().setLevel(3);
            addEliteSkill(person, Skills.HELMSMANSHIP);
            addEliteSkill(person, Skills.TARGET_ANALYSIS);
            addEliteSkill(person, Skills.COMBAT_ENDURANCE);
            person.getMemoryWithoutUpdate().set(AICoreOfficerPlugin.AUTOMATED_POINTS_MULT, 2f);
        }

        person.setPersonality(Personalities.RECKLESS);
        person.setRankId(Ranks.SPACE_CAPTAIN);
        person.setPostId(null);

        person.getStats().setSkipRefresh(false);
        person.getStats().refreshCharacterStatsEffects();
        return person;
    }

    private static void addEliteSkill(PersonAPI person, String skillId) {
        person.getStats().setSkillLevel(skillId, 2f);
    }
}
