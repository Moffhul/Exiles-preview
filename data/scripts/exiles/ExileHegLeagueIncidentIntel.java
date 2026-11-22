package data.scripts.exiles;

import java.awt.Color;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * The INCIDENT TELEGRAPH -- PASS 3 of docs/EXILES_HEG_LEAGUE_WAR.md sec 3.2.2: an incident
 * "posts as rumour-then-confirmation intel (the Ashen Pass-7 telegraph pattern)".
 *
 * <p><b>Two beats, one intel.</b> The rumour goes up the moment the incident fires -- hedged,
 * unattributed, the sector's first confused reports. {@code confirmDays} later the same intel
 * sends an update carrying the confirmation: the victim's government has decided what happened and
 * who did it, which is what makes the RETALIATION that follows legible rather than arbitrary. The
 * intel then lingers for {@code intelDays} and ends itself.
 *
 * <p><b>Why this one is NOT hidden.</b> The sibling {@code setHidden(true)} pattern exists for
 * OPERATIONS, which the player is meant to discover by flying into them
 * ({@link ExileHegLeagueRaidIntel} keeps it, and so does the strike op). A telegraph is the exact
 * opposite instrument: it exists to be read. Ashen's Pass-7 telegraph is a campaign message for
 * the same reason. Source concealment is likewise absent by ruling (sec 3.4): Hegemony and League
 * space is public knowledge and there is nothing here to conceal.
 *
 * <p><b>Bounded and self-expiring</b> (sec 3.3): every timer is on this object, nothing is
 * registered elsewhere, and the intel removes itself. It holds only Strings, floats and booleans
 * -- the site is resolved to a NAME at construction and the market is never retained, so a market
 * that decivilises or changes hands cannot strand saved state.
 *
 * <p><b>All prose is PLACEHOLDER</b> and lives on {@link ExileHegLeagueIncident} (sec 11 item 2).
 */
public class ExileHegLeagueIncidentIntel extends BaseIntelPlugin {

    /**
     * The confirmation update marker. A String rather than vanilla's {@code new Object()} idiom
     * ({@code RaidIntel.ENTERED_SYSTEM_UPDATE}) because {@code listInfoParam} is a serialized field
     * on {@link BaseIntelPlugin} and a String is trivially safe there.
     */
    public static final String CONFIRMED_UPDATE = "exiles_hl_incident_confirmed";
    /**
     * The containment beat (sec 3.2.5, 2026-09-13): the cycle closed without ceasefire talks, and
     * the victim's formal protest is the last word. Same intel, third update -- no new object.
     */
    public static final String CONTAINED_UPDATE = "exiles_hl_incident_contained";

    protected String typeId;
    protected String title;
    protected String rumorText;
    protected String confirmationText;
    protected String shortDesc;
    /** Set by {@link #markContained()}; null on a save from before the amendment. */
    protected String containedText;
    protected String containedShortDesc;
    protected String offenderFactionId;
    protected String victimFactionId;
    /** Resolved once, at construction. May be null -- the prose degrades to "the border". */
    protected String siteName;

    protected float daysUntilConfirm;
    protected float daysUntilEnd;
    protected boolean confirmed = false;
    /** A boolean, so a pre-amendment save reads it as false -- the correct answer. */
    protected boolean contained = false;

    public ExileHegLeagueIncidentIntel(ExileHegLeagueIncident type, String offenderFactionId,
                                       String victimFactionId, MarketAPI site,
                                       float confirmDays, float intelDays) {
        this.typeId = type != null ? type.id : null;
        this.offenderFactionId = offenderFactionId;
        this.victimFactionId = victimFactionId;
        this.siteName = site != null ? site.getName() : null;

        String offender = displayName(offenderFactionId);
        String victim = displayName(victimFactionId);

        if (type != null) {
            this.title = type.getTitle();
            this.rumorText = type.getRumorText(offender, victim, siteName);
            this.confirmationText = type.getConfirmationText(offender, victim, siteName);
            this.shortDesc = type.getShortDesc(offender, victim);
            this.containedText = type.getContainedText(offender, victim, siteName);
            this.containedShortDesc = type.getContainedShortDesc(offender, victim);
        } else {
            // Only reachable with the incident table switched off, which is the Pass-1 fallback.
            this.title = "Border Incident";
            this.rumorText = "Unverified reports describe a violent encounter between " + offender
                    + " and " + victim + " forces.";
            this.confirmationText = victim + " has confirmed the incident and blamed " + offender
                    + ".";
            this.shortDesc = offender + " blamed; " + victim + " is preparing a response";
            this.containedText = victim + " has lodged a formal protest with " + offender
                    + ". Neither government has taken the matter further.";
            this.containedShortDesc = "Formal protest lodged; the matter is considered closed";
        }

        this.daysUntilConfirm = Math.max(0f, confirmDays);
        this.daysUntilEnd = Math.max(1f, intelDays);

        // The intel manager DISPLAYS intel; it does not advance it. Vanilla timed intel is
        // ticked either by an owning manager script (SystemBountyManager -> SystemBountyIntel)
        // or by adding itself as a sector script (RaidIntel.init()). Without this line
        // advanceImpl() never runs: no confirmation update, no expiry (live-test finding,
        // 2026-08-28). Removed again in notifyEnded(), the RaidIntel pattern.
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

    public String getTypeId() {
        return typeId;
    }

    public String getOffenderFactionId() {
        return offenderFactionId;
    }

    public String getVictimFactionId() {
        return victimFactionId;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean isContained() {
        return contained;
    }

    /**
     * sec 3.2.5: the director's CONTAINED exit. The closing beat of the same telegraph -- the
     * victim's protest is filed and nothing more happens. Confirmation is forced if it has not
     * landed yet (it always has: confirmDays is shorter than any retaliation lag), and the intel
     * then ends on its normal timer. Idempotent, and a no-op on an intel that is already over.
     */
    public void markContained() {
        if (contained || isEnding() || isEnded()) return;
        contained = true;
        if (!confirmed) {
            confirmed = true;
            daysUntilConfirm = 0f;
        }
        if (containedText == null) {
            // Intel created before the amendment: degrade to the generic line.
            containedText = displayName(victimFactionId) + " has lodged a formal protest with "
                    + displayName(offenderFactionId)
                    + ". Neither government has taken the matter further.";
            containedShortDesc = "Formal protest lodged; the matter is considered closed";
        }
        sendUpdateIfPlayerHasIntel(CONTAINED_UPDATE, false);
    }

    @Override
    protected void advanceImpl(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);

        if (!confirmed) {
            daysUntilConfirm -= days;
            if (daysUntilConfirm <= 0f) {
                confirmed = true;
                sendUpdateIfPlayerHasIntel(CONFIRMED_UPDATE, false);
            }
        }

        daysUntilEnd -= days;
        if (daysUntilEnd <= 0f && !isEnding() && !isEnded()) {
            endAfterDelay();
        }
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @Override
    public String getName() {
        if (isEnding() || isEnded()) return title + " (Over)";
        if (isSendingUpdate() && CONTAINED_UPDATE.equals(getListInfoParam())) {
            return title + " - Contained";
        }
        if (isSendingUpdate() && CONFIRMED_UPDATE.equals(getListInfoParam())) {
            return title + " - Confirmed";
        }
        if (contained) return title + " (Contained)";
        if (!confirmed) return title + " (Unconfirmed)";
        return title;
    }

    public String getSortString() {
        return "Incident";
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color tc = getBulletColorForMode(mode);
        float initPad = 3f;
        if (mode == ListInfoMode.IN_DESC) initPad = 10f;

        FactionAPI offender = offenderFactionId != null
                ? Global.getSector().getFaction(offenderFactionId) : null;
        FactionAPI victim = victimFactionId != null
                ? Global.getSector().getFaction(victimFactionId) : null;

        bullet(info);
        if (offender != null) {
            info.addPara(offender.getDisplayName(), initPad, tc,
                    offender.getBaseUIColor(), offender.getDisplayName());
            initPad = 0f;
        }
        if (victim != null) {
            info.addPara(victim.getDisplayName(), initPad, tc,
                    victim.getBaseUIColor(), victim.getDisplayName());
            initPad = 0f;
        }
        if (siteName != null) {
            info.addPara(siteName, initPad, tc, Misc.getHighlightColor(), siteName);
            initPad = 0f;
        }
        info.addPara(contained ? "Contained" : (confirmed ? "Confirmed" : "Unconfirmed"),
                tc, initPad);
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

        FactionAPI offender = offenderFactionId != null
                ? Global.getSector().getFaction(offenderFactionId) : null;
        FactionAPI victim = victimFactionId != null
                ? Global.getSector().getFaction(victimFactionId) : null;
        if (offender != null && victim != null) {
            info.addImages(width, 128, opad, opad, offender.getCrest(), victim.getCrest());
        }

        info.addPara(rumorText, opad);
        if (confirmed) {
            info.addPara(confirmationText, opad);
        } else {
            info.addPara("Nothing has been confirmed by either government.",
                    Misc.getGrayColor(), opad);
        }
        if (contained && containedText != null) {
            info.addPara(containedText, opad);
            info.addPara(containedShortDesc != null ? containedShortDesc : shortDesc, opad);
        } else {
            info.addPara(shortDesc, opad);
        }
    }

    @Override
    public String getIcon() {
        FactionAPI offender = offenderFactionId != null
                ? Global.getSector().getFaction(offenderFactionId) : null;
        if (offender != null) return offender.getCrest();
        return super.getIcon();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        if (victimFactionId != null) {
            FactionAPI victim = Global.getSector().getFaction(victimFactionId);
            if (victim != null) return victim;
        }
        return super.getFactionForUIColors();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_MILITARY);
        if (offenderFactionId != null) tags.add(offenderFactionId);
        if (victimFactionId != null) tags.add(victimFactionId);
        return tags;
    }
}
