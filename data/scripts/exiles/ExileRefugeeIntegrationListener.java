package data.scripts.exiles;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonySizeChangeListener;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import data.scripts.industries.ExileRefugeeProcessingCenter;
import data.scripts.industries.ExileSecurityCenter;
import data.scripts.marketconditions.ExileRefugeeInflux;
import data.scripts.marketconditions.ExileRefugeeStrain;

/**
 * Event-driven refugee dynamics on colony GROWTH. Fired by the game's ColonySizeChangeListener (via
 * ListenerUtil.reportColonySizeChanged), so it triggers exactly on a size change with no dependence on an
 * industry's advance() running -- which is why the earlier in-industry size polling failed.
 *
 *  - A functional Refugee Processing Center integrates the population as the colony grows: removes
 *    decivilized_subpop and exile_refugee_strain.
 *  - An unmanaged refugee influx festers: if a growing colony carries exile_refugee_influx but has built NO
 *    control structure (Processing Center or Security Centers), exile_refugee_strain is applied. (Dormant
 *    until exile_refugee_influx reaches player colonies via the future refugee-trust system.)
 */
public class ExileRefugeeIntegrationListener implements ColonySizeChangeListener {

    @Override
    public void reportColonySizeChanged(MarketAPI market, int prevSize) {
        if (market == null) return;
        if (market.getSize() <= prevSize) return;   // only on growth

        boolean hasProcessing = isFunctional(market.getIndustry(ExileRefugeeProcessingCenter.INDUSTRY_ID));
        boolean hasSecurity = isFunctional(market.getIndustry(ExileSecurityCenter.INDUSTRY_ID));

        // Processing Center integrates the population on growth.
        if (hasProcessing) {
            if (market.hasCondition(Conditions.DECIVILIZED_SUBPOP)) {
                market.removeCondition(Conditions.DECIVILIZED_SUBPOP);
            }
            if (market.hasCondition(ExileRefugeeStrain.CONDITION_ID)) {
                market.removeCondition(ExileRefugeeStrain.CONDITION_ID);
            }
        }

        // Unmanaged influx festers into strain when the colony grows with no control structure built.
        if (market.hasCondition(ExileRefugeeInflux.CONDITION_ID)
                && !market.hasCondition(ExileRefugeeStrain.CONDITION_ID)
                && !hasProcessing && !hasSecurity) {
            market.addCondition(ExileRefugeeStrain.CONDITION_ID);
        }
    }

    private boolean isFunctional(Industry ind) {
        return ind != null && ind.isFunctional();
    }
}
