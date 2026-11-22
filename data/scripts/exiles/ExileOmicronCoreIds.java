package data.scripts.exiles;

/** Stable identifiers and shared constants for the Omicron tactical-core system. */
public final class ExileOmicronCoreIds {

    public static final String GRADE_I = "exiles_omicron_core_1";
    public static final String GRADE_II = "exiles_omicron_core_2";
    public static final String GRADE_III = "exiles_omicron_core_3";

    public static final String CAMPAIGN_PLUGIN_ID = "exiles_omicron_core_campaign_plugin";

    private ExileOmicronCoreIds() {
    }

    public static boolean isOmicronCore(String commodityId) {
        return GRADE_I.equals(commodityId)
                || GRADE_II.equals(commodityId)
                || GRADE_III.equals(commodityId);
    }
}
