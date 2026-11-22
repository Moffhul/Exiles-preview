package data.scripts.exiles;

import java.awt.Color;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * The GENERAL ENGAGEMENT telegraph -- PASS 5, from sec 3.2.4 A: the rare emergent super-state gets
 * "a named operation in the intel". This is that intel, and it is the only player-facing marker
 * that the war has briefly stopped being a border quarrel.
 *
 * <p><b>Two beats, one intel</b>, the same lifecycle as {@link ExileHegLeagueIncidentIntel} and
 * {@link ExileHegLeagueCeasefireIntel}. Beat 1 goes up when the director declares the General
 * Engagement at ESCALATION entry. Beat 2 lands when the extended window closes: sec 3.2.4 A rules
 * that a General Engagement <b>always</b> ends at the ceasefire table with elevated stand-down
 * odds -- mutual exhaustion, the doves seizing the crisis -- so the closing beat says exactly that,
 * and the ceasefire intel that opens a moment later carries the rest of the story.
 *
 * <p><b>The operation name is a PLACEHOLDER</b> and comes from
 * {@code intel.generalEngagementOperationName} in {@code director_config.json}. It is deliberately
 * NOT derived from which side is surging: a General Engagement is both sides committing at once,
 * and there is no single offender to name it after. See the sec 14.10 build record.
 *
 * <p><b>Bounded and self-expiring</b> (sec 3.3): two timers, both on this object. The normal one
 * counts {@code generalEngagementIntelDays} from the closing beat; the hard backstop counts
 * {@code generalEngagementDays + generalEngagementIntelDays} from posting, so an intel whose
 * director was disabled or lost still expires. Only primitives and Strings are saved.
 *
 * <p><b>All prose is PLACEHOLDER</b> (sec 11 item 2 / R12 -- an author deliverable).
 */
public class ExileHegLeagueEngagementIntel extends BaseIntelPlugin {

    /** The closing update marker. A String, for the {@code listInfoParam} reason on the siblings. */
    public static final String ENGAGEMENT_ENDED_UPDATE = "exiles_hl_engagement_ended";

    protected String factionAId;
    protected String factionBId;
    /** PLACEHOLDER operation name from config. */
    protected String operationName;
    /** The director's {@code escalationsReached} at posting -- the number in the title. */
    protected int number;
    /** The director's {@code cyclesCompleted} at posting -- log identity only. */
    protected int cycle;
    protected boolean engagementEnded = false;

    protected float intelDays;
    /** Counts down only once the closing beat has landed; &lt; 0 means "not started". */
    protected float daysUntilEnd = -1f;
    /** The orphan backstop: windowDays + intelDays from posting, always counting down. */
    protected float daysUntilBackstop;

    /**
     * @param windowDays the General Engagement window ({@code cycle.generalEngagementDays}), used
     *                   only to size the backstop.
     * @param intelDays  {@code intel.generalEngagementIntelDays}.
     */
    public ExileHegLeagueEngagementIntel(String factionAId, String factionBId,
                                         String operationName, int number, int cycle,
                                         float windowDays, float intelDays) {
        this.factionAId = factionAId;
        this.factionBId = factionBId;
        this.operationName = operationName != null && operationName.length() > 0
                ? operationName : "Operation PLACEHOLDER";
        this.number = number;
        this.cycle = cycle;
        this.intelDays = Math.max(1f, intelDays);
        this.daysUntilBackstop = Math.max(1f, windowDays) + this.intelDays;

        // MANDATORY, for the reason recorded on ExileHegLeagueIncidentIntel: the intel manager
        // displays intel, it does not advance it.
        Global.getSector().addScript(this);
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
    }

    protected static String displayName(String factionId) {
        FactionAPI faction = factionId != null
                ? Global.getSector().getFaction(factionId) : null;
        if (faction == null) return String.valueOf(factionId);
        return faction.getDisplayName();
    }

    public int getCycle() {
        return cycle;
    }

    public int getNumber() {
        return number;
    }

    public String getOperationName() {
        return operationName;
    }

    public boolean isEngagementEnded() {
        return engagementEnded;
    }

    /** Beat 2: the extended window has closed. Idempotent; a no-op once the intel is over. */
    public void markEngagementEnded() {
        if (engagementEnded || isEnding() || isEnded()) return;
        engagementEnded = true;
        daysUntilEnd = intelDays;
        sendUpdateIfPlayerHasIntel(ENGAGEMENT_ENDED_UPDATE, false);
    }

    @Override
    protected void advanceImpl(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);

        if (engagementEnded) {
            daysUntilEnd -= days;
        }
        daysUntilBackstop -= days;

        boolean expired = (engagementEnded && daysUntilEnd <= 0f) || daysUntilBackstop <= 0f;
        if (expired && !isEnding() && !isEnded()) {
            endAfterDelay();
        }
    }

    // ------------------------------------------------------------------
    // Presentation -- ALL PLACEHOLDER PROSE (sec 11 item 2 / R12)
    // ------------------------------------------------------------------

    @Override
    public String getName() {
        String title = "General Engagement #" + number;
        if (isEnding() || isEnded()) return title + " (Over)";
        if (isSendingUpdate() && ENGAGEMENT_ENDED_UPDATE.equals(getListInfoParam())) {
            return title + " - Broken Off";
        }
        if (engagementEnded) return title + " (Broken Off)";
        return title;
    }

    public String getSortString() {
        return "General Engagement";
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    /** PLACEHOLDER. The opening beat. */
    protected String getDeclaredText() {
        String a = displayName(factionAId);
        String b = displayName(factionBId);
        return "Border skirmishing between " + a + " and " + b + " has given way to a general"
                + " engagement. Both sides have committed heavy formations at once, and the"
                + " fighting is being reported under the operational designation "
                + operationName + ".";
    }

    /** PLACEHOLDER. The closing beat -- sec 3.2.4 A's forced return to the table. */
    protected String getEndedText() {
        String a = displayName(factionAId);
        String b = displayName(factionBId);
        return "The general engagement has broken off. Neither " + a + " nor " + b
                + " can sustain losses at this rate, and the doves on both sides have forced"
                + " the fleets back to the table.";
    }

    /** PLACEHOLDER. One-line summary for the intel bullets. */
    protected String getShortDesc() {
        if (engagementEnded) return "Broken off; both sides forced back to the table";
        return "Both sides have committed heavy formations";
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color tc = getBulletColorForMode(mode);
        float initPad = 3f;
        if (mode == ListInfoMode.IN_DESC) initPad = 10f;

        FactionAPI a = factionAId != null ? Global.getSector().getFaction(factionAId) : null;
        FactionAPI b = factionBId != null ? Global.getSector().getFaction(factionBId) : null;

        bullet(info);
        if (a != null) {
            info.addPara(a.getDisplayName(), initPad, tc, a.getBaseUIColor(), a.getDisplayName());
            initPad = 0f;
        }
        if (b != null) {
            info.addPara(b.getDisplayName(), initPad, tc, b.getBaseUIColor(), b.getDisplayName());
            initPad = 0f;
        }
        info.addPara(operationName, initPad, tc, Misc.getHighlightColor(), operationName);
        info.addPara(getShortDesc(), tc, 0f);
        unindent(info);
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(getName(), getTitleColor(mode), 0f);
        addBulletPoints(info, mode);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;

        FactionAPI a = factionAId != null ? Global.getSector().getFaction(factionAId) : null;
        FactionAPI b = factionBId != null ? Global.getSector().getFaction(factionBId) : null;
        if (a != null && b != null) {
            info.addImages(width, 128, opad, opad, a.getCrest(), b.getCrest());
        }

        info.addPara(getDeclaredText(), opad);
        if (engagementEnded) {
            info.addPara(getEndedText(), opad);
        } else {
            info.addPara("The fighting is continuing.", Misc.getGrayColor(), opad);
        }
        info.addPara(getShortDesc(), opad);
    }

    /**
     * The one place a vanilla intel sprite fits better than a crest: core {@code settings.json}
     * binds the intel key {@code "hostilities"} to {@code graphics/icons/intel/war.png}, which is
     * vanilla's own icon for factions going to war. No new art and no new registration.
     */
    @Override
    public String getIcon() {
        String icon = Global.getSettings().getSpriteName("intel", "hostilities");
        if (icon != null) return icon;
        FactionAPI a = factionAId != null ? Global.getSector().getFaction(factionAId) : null;
        if (a != null) return a.getCrest();
        return super.getIcon();
    }

    /** NEUTRAL, for the same reason as the ceasefire intel: both sides are in this one equally. */
    @Override
    public FactionAPI getFactionForUIColors() {
        return super.getFactionForUIColors();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MILITARY);
        if (factionAId != null) tags.add(factionAId);
        if (factionBId != null) tags.add(factionBId);
        return tags;
    }
}
