package data.scripts.exiles;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import data.scripts.marketconditions.ExileEmergencyMeasures;

/** Event-only attribution hook: player hostile acts cancel dormant eligibility, never active aid. */
public class ExileEmergencyMeasuresPlayerActionListener implements ColonyPlayerHostileActListener {

    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(
            InteractionDialogAPI dialog, MarketAPI market, TempData actionData, CargoAPI cargo) {
        ExileEmergencyMeasures.reportPlayerHostileAction(market);
    }

    @Override
    public void reportRaidToDisruptFinished(
            InteractionDialogAPI dialog, MarketAPI market, TempData actionData, Industry industry) {
        ExileEmergencyMeasures.reportPlayerHostileAction(market);
    }

    @Override
    public void reportTacticalBombardmentFinished(
            InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {
        ExileEmergencyMeasures.reportPlayerHostileAction(market);
    }

    @Override
    public void reportSaturationBombardmentFinished(
            InteractionDialogAPI dialog, MarketAPI market, TempData actionData) {
        ExileEmergencyMeasures.reportPlayerHostileAction(market);
    }
}
