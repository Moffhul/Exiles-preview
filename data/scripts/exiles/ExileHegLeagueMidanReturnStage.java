package data.scripts.exiles;

import java.util.List;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

/**
 * Vanilla's return stage, sent to the anchor system's hyperspace jump point, where the fleets
 * despawn (Pass 7 step 7d-2; ruled 2026-10-20). Fallback noted in the plan for the retune: if the
 * stealthed fleet is too hard to follow, despawn when it leaves the target system instead.
 */
public class ExileHegLeagueMidanReturnStage extends ReturnStage {

    protected SectorEntityToken returnPoint;

    public ExileHegLeagueMidanReturnStage(RaidIntel raid, SectorEntityToken returnPoint) {
        super(raid);
        this.returnPoint = returnPoint;
    }

    @Override
    public void giveReturnOrdersToStragglers(List stragglers) {
        ExileHegLeagueMidanStrikeIntel.sendHome(this, stragglers, returnPoint);
    }

    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        if (curr >= index) {
            info.addPara("The strike force has withdrawn into hyperspace.", 10f);
        }
    }
}
