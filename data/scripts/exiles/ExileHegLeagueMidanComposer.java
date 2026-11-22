package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent.SkillPickPreference;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;

/**
 * MIDAN'S STRIKE FORCE COMPOSER -- Pass 7 step 7d-1 (build plan docs/EXILES_PASS7_BUILD_PLAN.md
 * sec 1.5a and the 7d split). Builds the Strike Force from an EXPLICIT variant list (never a
 * faction doctrine, never a fleet inflater), so no League market ever sells these ships and no
 * quality roll adds D-mods:
 * <ul>
 * <li><b>The main fleet:</b> the PLS Ajax (Conquest flagship) and the ruled roster, nine
 * level-5 officers on the listed hulls, and the Ajax's level-7 captain with his ruled officer and
 * admiral skills, who is the fleet commander.</li>
 * <li><b>An escort fleet:</b> an unnamed group from the escort roster with a few level-5
 * officers. Up to {@code escortCap} of them join the Ajax when the margin needs it.</li>
 * </ul>
 *
 * <p><b>7d-1 is LOG-ONLY:</b> fleets are composed in memory, never added to any location, measured
 * with vanilla's own {@code CampaignFleetAPI.getEffectiveStrength()} (officers, S-mods and CR
 * included -- the figure vanilla's war sim uses for live fleets), and then dropped. Nothing is
 * spawned and no reference is kept. 7d-2 will reuse {@link #composeMainFleet} and
 * {@link #composeEscortFleet} for the real launch.
 *
 * <p>Fails soft: a missing variant id is logged as a WARN and skipped, and a fleet with nothing in
 * it comes back null, so a bad config can never crash the director.
 */
public class ExileHegLeagueMidanComposer {

    /**
     * The Ajax's captain is ONE PERSON across every strike (ruled 2026-10-30): like the player's
     * own officers, he escapes when his ship is lost and commands the next rung's Ajax. Registered
     * in ImportantPeople under this stable id (the Mourad pattern) -- a Phase-2 handle.
     */
    public static final String AJAX_CAPTAIN_ID = "exile_midan_ajax_captain";

    /** The Ajax's fleet, flagged as the Persean League (the false flag). Never added to a location
     *  here. A MEASUREMENT copy: its captain is a throwaway with the same level and skills. */
    public static CampaignFleetAPI composeMainFleet(ExileHegLeagueConfig config, Random random) {
        return composeMainFleet(config, random, true, false);
    }

    /**
     * 7d-2: {@code withFlagship} false rebuilds the main fleet WITHOUT the Ajax, for a strike whose
     * Ajax has already been lost and whose route respawns (the player flew away and back). Its best
     * officered ship then commands, as in an escort -- the Ajax's captain has escaped and is out of
     * this strike.
     *
     * <p>{@code persistentCaptain} true (launched fleets only) puts the one registered captain on
     * the Ajax; false (the in-memory measurement) builds a throwaway, so measuring never re-points
     * the real captain's fleet reference at a fleet that is about to be dropped.
     */
    public static CampaignFleetAPI composeMainFleet(ExileHegLeagueConfig config, Random random,
                                                    boolean withFlagship, boolean persistentCaptain) {
        FactionAPI league = Global.getSector().getFaction(Factions.PERSEAN);
        if (league == null) return null;
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(
                Factions.PERSEAN, config.midanFleetName, true);

        if (!withFlagship) {
            List roster = config.midanRoster;
            for (int i = 0; i < roster.size(); i++) {
                addMember(fleet, (String) roster.get(i), random);
            }
            if (fleet.getFleetData().getNumMembers() <= 0) return null;
            assignOfficers(fleet, null, config.midanOfficerShips, config.midanOfficerLevel, random);
            commandFromBestOfficer(fleet);
            finish(fleet);
            return fleet;
        }

        FleetMemberAPI flagship = addMember(fleet, config.midanFlagshipVariant, random);
        if (flagship == null) {
            warn("flagship variant " + config.midanFlagshipVariant + " is missing; no main fleet");
            return null;
        }
        String prefix = league.getShipNamePrefix();
        flagship.setShipName((prefix != null && prefix.length() > 0 ? prefix + " " : "")
                + config.midanFlagshipName);
        // Sean, 2026-11-03: the fleet that carries the Ajax is "Strike Force Ajax", with no faction
        // prefix (the tooltip still shows the League flag). Escorts, and a main fleet rebuilt
        // after the Ajax is lost, keep the shared fleetName. Empty = the shared name here too.
        String flagshipFleetName = config.midanFlagshipFleetName;
        if (flagshipFleetName != null && flagshipFleetName.length() > 0) {
            fleet.setName(flagshipFleetName);
            fleet.setNoFactionInName(true);
        }

        List roster = config.midanRoster;
        for (int i = 0; i < roster.size(); i++) {
            addMember(fleet, (String) roster.get(i), random);
        }

        PersonAPI captain = persistentCaptain
                ? getOrCreateAjaxCaptain(league, config, random)
                : createAjaxCaptain(league, config, random);
        fleet.setCommander(captain);
        fleet.getFleetData().setFlagship(flagship);
        flagship.setCaptain(captain);

        assignOfficers(fleet, flagship, config.midanOfficerShips, config.midanOfficerLevel, random);
        finish(fleet);
        return fleet;
    }

    /** One unnamed escort group, same false flag. Its first officer commands it. */
    public static CampaignFleetAPI composeEscortFleet(ExileHegLeagueConfig config, Random random) {
        FactionAPI league = Global.getSector().getFaction(Factions.PERSEAN);
        if (league == null) return null;
        CampaignFleetAPI fleet = Global.getFactory().createEmptyFleet(
                Factions.PERSEAN, config.midanFleetName, true);
        List roster = config.midanEscortRoster;
        for (int i = 0; i < roster.size(); i++) {
            addMember(fleet, (String) roster.get(i), random);
        }
        if (fleet.getFleetData().getNumMembers() <= 0) return null;

        assignOfficers(fleet, null, config.midanEscortOfficerShips, config.midanEscortOfficerLevel, random);
        commandFromBestOfficer(fleet);
        finish(fleet);
        return fleet;
    }

    /** The fleet's commander: the captain of its most expensive officered ship. */
    protected static void commandFromBestOfficer(CampaignFleetAPI fleet) {
        FleetMemberAPI best = null;
        List members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) members.get(i);
            if (m.getCaptain() == null || m.getCaptain().isDefault()) continue;
            if (best == null || m.getFleetPointCost() > best.getFleetPointCost()) best = m;
        }
        if (best != null) {
            fleet.setCommander(best.getCaptain());
            fleet.getFleetData().setFlagship(best);
        }
    }

    /** Vanilla's live-fleet strength (Misc.getMemberStrength summed: CR, hull, quality, S-mods, captain). */
    public static float measure(CampaignFleetAPI fleet) {
        if (fleet == null) return 0f;
        return fleet.getEffectiveStrength();
    }

    /** Total fleet points, for the log. */
    public static int fleetPoints(CampaignFleetAPI fleet) {
        if (fleet == null) return 0;
        return fleet.getFleetPoints();
    }

    public static int shipCount(CampaignFleetAPI fleet) {
        if (fleet == null) return 0;
        return fleet.getFleetData().getNumMembers();
    }

    /**
     * How many escorts the main fleet needs to reach {@code required}: 0..cap, or -1 when even the
     * cap is not enough (the rung waits).
     */
    public static int escortsNeeded(float required, float mainStrength, float escortStrength, int cap) {
        for (int n = 0; n <= cap; n++) {
            if (mainStrength + n * escortStrength >= required) return n;
        }
        return -1;
    }

    // ------------------------------------------------------------------

    protected static FleetMemberAPI addMember(CampaignFleetAPI fleet, String variantId, Random random) {
        if (variantId == null || !Global.getSettings().doesVariantExist(variantId)) {
            warn("variant " + variantId + " does not exist; ship skipped");
            return null;
        }
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
        member.setShipName(fleet.getFleetData().pickShipName(member, random));
        fleet.getFleetData().addFleetMember(member);
        return member;
    }

    /** The one registered Ajax captain, created on first use (the first launch). */
    public static PersonAPI getOrCreateAjaxCaptain(FactionAPI league, ExileHegLeagueConfig config,
                                                   Random random) {
        PersonAPI person = Global.getSector().getImportantPeople().getPerson(AJAX_CAPTAIN_ID);
        if (person != null) return person;
        person = createAjaxCaptain(league, config, random);
        person.setId(AJAX_CAPTAIN_ID);
        Global.getSector().getImportantPeople().addPerson(person);
        Global.getLogger(ExileHegLeagueMidanComposer.class).info(
                "Exiles Midan AJAX CAPTAIN created: " + person.getNameString() + " (id "
                        + AJAX_CAPTAIN_ID + ", level " + person.getStats().getLevel() + ")");
        return person;
    }

    /** The Ajax's captain: unnamed, strictly military, the ruled level and skills (plan sec 1.5a). */
    protected static PersonAPI createAjaxCaptain(FactionAPI league, ExileHegLeagueConfig config,
                                                 Random random) {
        PersonAPI person = league.createRandomPerson(random);
        person.setRankId(Ranks.SPACE_ADMIRAL);
        person.setPostId(Ranks.POST_FLEET_COMMANDER);
        person.setPersonality(Personalities.STEADY); // ruled 2026-10-29: suits the Conquest's broadsides
        MutableCharacterStatsAPI stats = person.getStats();
        stats.setLevel(config.midanCaptainLevel);
        for (int i = 0; i < config.midanCaptainSkillIds.size(); i++) {
            String id = (String) config.midanCaptainSkillIds.get(i);
            int level = ((Integer) config.midanCaptainSkillLevels.get(i)).intValue();
            stats.setSkillLevel(id, level);
        }
        for (int i = 0; i < config.midanAdmiralSkills.size(); i++) {
            stats.setSkillLevel((String) config.midanAdmiralSkills.get(i), 1);
        }
        return person;
    }

    /**
     * One vanilla-generated officer per listed ship (matching the variant id, first unofficered
     * copy wins), exactly as FleetFactoryV3 does it: skills picked to suit the hull, elite skills
     * at vanilla's default count, TIMID promoted to CAUTIOUS.
     */
    protected static void assignOfficers(CampaignFleetAPI fleet, FleetMemberAPI skip, List officerShips,
                                         int level, Random random) {
        List members = fleet.getFleetData().getMembersListCopy();
        List used = new ArrayList();
        for (int i = 0; i < officerShips.size(); i++) {
            String want = (String) officerShips.get(i);
            FleetMemberAPI target = null;
            for (int j = 0; j < members.size(); j++) {
                FleetMemberAPI m = (FleetMemberAPI) members.get(j);
                if (m == skip || used.contains(m)) continue;
                if (want.equals(m.getVariant().getHullVariantId())) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                warn("no unofficered " + want + " in the fleet; officer skipped");
                continue;
            }
            used.add(target);
            SkillPickPreference pref = FleetFactoryV3.getSkillPrefForShip(target);
            PersonAPI officer = OfficerManagerEvent.createOfficer(fleet.getFaction(), level, pref,
                    false, fleet, true, true, -1, random);
            if (Personalities.TIMID.equals(officer.getPersonalityAPI().getId())) {
                officer.setPersonality(Personalities.CAUTIOUS);
            }
            target.setCaptain(officer);
        }
    }

    /** Full CR on every ship and a fresh sync, so the strength vanilla reports is the launch-day fleet. */
    protected static void finish(CampaignFleetAPI fleet) {
        fleet.forceSync();
        List members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) members.get(i);
            m.getRepairTracker().setCR(m.getRepairTracker().getMaxCR());
        }
        fleet.getFleetData().setSyncNeeded();
        fleet.getFleetData().syncIfNeeded();
    }

    protected static void warn(String line) {
        Global.getLogger(ExileHegLeagueMidanComposer.class).warn("Exiles Midan composer: " + line);
    }
}
