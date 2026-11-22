package data.scripts.exiles;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.AICoreOfficerPlugin;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;

/** Makes the three custom commodities genuine, player-installable AI officers. */
public class ExileOmicronCampaignPlugin extends BaseCampaignPlugin {

    public String getId() {
        return ExileOmicronCoreIds.CAMPAIGN_PLUGIN_ID;
    }

    public boolean isTransient() {
        return true;
    }

    public PluginPick<AICoreOfficerPlugin> pickAICoreOfficerPlugin(String commodityId) {
        if (!ExileOmicronCoreIds.isOmicronCore(commodityId)) return null;
        return new PluginPick<AICoreOfficerPlugin>(
                new ExileOmicronAICoreOfficerPlugin(), PickPriority.MOD_SPECIFIC);
    }
}
