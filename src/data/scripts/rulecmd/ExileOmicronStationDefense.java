package data.scripts.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;

/**
 * Wakes an attacked station's Omicron complement immediately before MarketCMD constructs the
 * battle. A 350+ FP player force in Hybrasil also commits Culann's strategic detachment. This
 * command is deliberately self-contained in the early-loaded rule-command JAR: rules.csv is
 * resolved before Exiles' loose Janino scripts are available.
 */
public class ExileOmicronStationDefense extends BaseCommandPlugin {

    private static final String LOCAL_DEFENDER_TAG = "exiles_omicron_tt_local_defender";
    private static final String STRATEGIC_RESERVE_TAG = "exiles_omicron_tt_strategic_reserve";
    private static final String MARKET_ID_KEY = "$exiles_omicron_tt_market";
    private static final String ACTIVE_KEY = "$exiles_omicron_tt_active";
    private static final String THREAT_FACTION_KEY = "$exiles_omicron_tt_threat_faction";
    private static final String ALERT_KEY = "$exiles_omicron_tt_defense_alert";
    private static final String ALERT_FACTION_KEY = "$exiles_omicron_tt_alert_faction";
    private static final String RESERVE_ALERT_KEY = "$exiles_omicron_tt_reserve_alert";
    private static final String RESERVE_FACTION_KEY = "$exiles_omicron_tt_reserve_faction";
    private static final String RESERVE_READY_KEY = "$exiles_omicron_tt_reserve_ready";
    private static final String RESERVE_THRESHOLD_KEY = "$exiles_omicron_tt_reserve_threshold";

    private static final String WARNING =
            "An unusual number of targeting sensors are suddenly directed at your fleet. "
            + "Evidently, the station's visible defenses are not all that Tri-Tachyon has prepared.";

    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || dialog.getInteractionTarget() == null) return true;
        MarketAPI market = dialog.getInteractionTarget().getMarket();
        if (!isProtectedTriTachyonMarket(market)) return true;

        CampaignFleetAPI station = Misc.getStationFleet(market);
        CampaignFleetAPI defender = findDefender(market);
        if (station == null || defender == null) return true;

        String mode = params == null || params.isEmpty()
                ? "activate" : params.get(0).getString(memoryMap);
        if ("warn".equals(mode)) {
            dialog.getTextPanel().addPara(WARNING, Misc.getHighlightColor());
            return true;
        }
        if (!"activate".equals(mode)) return true;

        market.getMemoryWithoutUpdate().set(ALERT_KEY, true, 15f);
        market.getMemoryWithoutUpdate().set(ALERT_FACTION_KEY, Factions.PLAYER, 15f);

        activateAgainstPlayer(defender, station);

        CampaignFleetAPI reserve = findStrategicReserve(market);
        if (reserve != null && Global.getSector().getPlayerFleet() != null
                && Global.getSector().getPlayerFleet().getFleetPoints()
                >= reserve.getMemoryWithoutUpdate().getFloat(RESERVE_THRESHOLD_KEY)) {
            String homeId = reserve.getMemoryWithoutUpdate().getString(MARKET_ID_KEY);
            MarketAPI home = homeId == null ? null
                    : Global.getSector().getEconomy().getMarket(homeId);
            if (home != null) {
                home.getMemoryWithoutUpdate().set(RESERVE_ALERT_KEY, true, 15f);
                home.getMemoryWithoutUpdate().set(
                        RESERVE_FACTION_KEY, Factions.PLAYER, 15f);
                home.getMemoryWithoutUpdate().set(RESERVE_READY_KEY, true, 15f);
            }
            activateAgainstPlayer(reserve, station);
            Global.getLogger(ExileOmicronStationDefense.class).info(
                    "Omicron strategic detachment committed at " + market.getName());
        }

        Global.getLogger(ExileOmicronStationDefense.class).info(
                "Omicron direct station defense prepared at " + market.getName());
        return true;
    }

    private static boolean isProtectedTriTachyonMarket(MarketAPI market) {
        if (market == null || !Factions.TRITACHYON.equals(market.getFactionId())) return false;
        String id = market.getId();
        return "culann".equals(id) || "eochu_bres".equals(id)
                || "port_tse".equals(id) || "tibicena".equals(id);
    }

    private static CampaignFleetAPI findDefender(MarketAPI market) {
        if (market.getContainingLocation() == null) return null;
        List<CampaignFleetAPI> fleets = market.getContainingLocation().getFleets();
        for (CampaignFleetAPI fleet : fleets) {
            if (fleet == null || !fleet.isAlive() || !fleet.hasTag(LOCAL_DEFENDER_TAG)) continue;
            if (market.getId().equals(
                    fleet.getMemoryWithoutUpdate().getString(MARKET_ID_KEY))) return fleet;
        }
        return null;
    }

    private static CampaignFleetAPI findStrategicReserve(MarketAPI market) {
        if (market.getContainingLocation() == null) return null;
        List<CampaignFleetAPI> fleets = market.getContainingLocation().getFleets();
        for (CampaignFleetAPI fleet : fleets) {
            if (fleet == null || !fleet.isAlive()
                    || !fleet.hasTag(STRATEGIC_RESERVE_TAG)) continue;
            return fleet;
        }
        return null;
    }

    private static void activateAgainstPlayer(CampaignFleetAPI fleet,
                                              CampaignFleetAPI station) {
        fleet.clearAssignments();
        applyPlayerAttackRoe(fleet);
        fleet.getMemoryWithoutUpdate().set(ACTIVE_KEY, true);
        fleet.getMemoryWithoutUpdate().set(THREAT_FACTION_KEY, Factions.PLAYER);
        fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
        fleet.setHidden(false);
        fleet.setTransponderOn(false);
        fleet.setLocation(station.getLocation().x, station.getLocation().y);
        fleet.addAssignment(FleetAssignment.INTERCEPT, Global.getSector().getPlayerFleet(),
                1f, "intercepting designated hostile forces");
    }

    private static void applyPlayerAttackRoe(CampaignFleetAPI fleet) {
        MemoryAPI mem = fleet.getMemoryWithoutUpdate();
        mem.unset(MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF);
        mem.unset(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE);
        mem.unset(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE);
        mem.set(MemFlags.MEMORY_KEY_PATROL_ALLOW_TOFF, true);
        mem.set(MemFlags.NON_HOSTILE_OVERRIDES_MAKE_HOSTILE, true);
        mem.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true);
        mem.set(MemFlags.MEMORY_KEY_FLEET_DO_NOT_GET_SIDETRACKED, true);

        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            String id = faction.getId();
            Misc.makeHostileToFaction(fleet, id, false, -1f);
            Misc.makeNonHostileToFaction(fleet, id, false, -1f);
            if (Factions.PLAYER.equals(id)) {
                Misc.makeHostileToFaction(fleet, id, true, -1f);
            } else {
                Misc.makeNonHostileToFaction(fleet, id, true, -1f);
            }
        }
    }
}
