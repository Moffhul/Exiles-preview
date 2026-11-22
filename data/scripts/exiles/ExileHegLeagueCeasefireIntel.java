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
 * The CEASEFIRE TELEGRAPH -- PASS 5 of docs/EXILES_HEG_LEAGUE_WAR.md sec 3.4: "ceasefire talks
 * convened; ceasefire announced" post as intel, while internal meter movements never do.
 *
 * <p><b>Two beats, one intel</b> -- deliberately the same shape as
 * {@link ExileHegLeagueIncidentIntel}, which is the ratified precedent (sec 3.2.5 ruling 4: one
 * story from spark to resolution, zero board clutter). Beat 1 goes up the moment the director
 * enters {@code STATE_POLITICAL_INTERVENTION} and says <i>why</i> the doves convened: after an
 * ESCALATION, after a General Engagement, or pre-emptively from the warning band -- the three-way
 * RETALIATION exit of sec 3.2.5. Beat 2 carries the announced result once
 * {@code resolveIntervention()} has rolled it: the ceasefire holds in full, holds in part, or
 * collapses.
 *
 * <p><b>VOCABULARY (sec 3.2.5 ruling 3, RULED by Sean 2026-09-13).</b> What the doves broker is a
 * <b>TEMPORARY CEASEFIRE</b>. It is never a "summit" and never "peace" -- top-level peace talks are
 * reserved Phase-2 quest content (the secret envoy). Do not soften this prose toward peace.
 *
 * <p><b>Bounded and self-expiring</b> (sec 3.3). The intel owns every timer and removes itself.
 * It runs TWO timers on purpose: the normal one counts {@code ceasefireIntelDays} from the outcome
 * beat, and a hard backstop counts {@code stateDays + ceasefireIntelDays} from posting, so an intel
 * whose director was disabled, idled or lost mid-cycle still expires instead of sitting on the
 * board forever. Only primitives and Strings are saved.
 *
 * <p><b>All prose below is PLACEHOLDER</b> (sec 11 item 2, ruling R12 -- the player-facing text is
 * an author deliverable). It is written faction-neutral and composed from parameters, because the
 * two belligerents are symmetric at the table.
 */
public class ExileHegLeagueCeasefireIntel extends BaseIntelPlugin {

    /**
     * The outcome update marker. A String rather than vanilla's {@code new Object()} idiom, for the
     * reason spelled out on {@link ExileHegLeagueIncidentIntel#CONFIRMED_UPDATE}: {@code
     * listInfoParam} is a serialized field on {@link BaseIntelPlugin}.
     */
    public static final String CEASEFIRE_OUTCOME_UPDATE = "exiles_hl_ceasefire_outcome";

    /** Why the doves convened -- the three-way branch of sec 3.2.5. */
    public static final int REASON_ESCALATION = 1;
    public static final int REASON_GENERAL_ENGAGEMENT = 2;
    public static final int REASON_PREEMPTIVE = 3;

    protected String factionAId;
    protected String factionBId;
    /** One of the REASON_* constants; 0 on a malformed call, which reads as the escalation line. */
    protected int reason;
    /** The director's {@code cyclesCompleted} at posting -- log/debug identity only. */
    protected int cycle;
    /** {@link ExileHegLeagueDirector}'s {@code OUTCOME_*}; OUTCOME_NONE until beat 2. */
    protected int outcome = ExileHegLeagueDirector.OUTCOME_NONE;
    protected boolean outcomeAnnounced = false;

    /** Days from the outcome beat to expiry. Saved so the timer survives a load. */
    protected float intelDays;
    /** Counts down only once the outcome has landed; &lt; 0 means "not started". */
    protected float daysUntilEnd = -1f;
    /** The orphan backstop: stateDays + intelDays from posting, always counting down. */
    protected float daysUntilBackstop;

    /**
     * @param factionAId one belligerent (the Hegemony, by convention at the call site).
     * @param factionBId the other belligerent.
     * @param reason     one of the REASON_* constants.
     * @param cycle      the director's cycle counter, for the log line only.
     * @param stateDays  the POLITICAL_INTERVENTION dwell, used only to size the backstop.
     * @param intelDays  {@code intel.ceasefireIntelDays}.
     */
    public ExileHegLeagueCeasefireIntel(String factionAId, String factionBId, int reason,
                                        int cycle, float stateDays, float intelDays) {
        this.factionAId = factionAId;
        this.factionBId = factionBId;
        this.reason = reason;
        this.cycle = cycle;
        this.intelDays = Math.max(1f, intelDays);
        this.daysUntilBackstop = Math.max(1f, stateDays) + this.intelDays;

        // MANDATORY -- the intel manager DISPLAYS intel, it does not advance it. Without this
        // line advanceImpl() never runs: no outcome beat, no expiry. Live-test finding of
        // 2026-08-28, recorded on ExileHegLeagueIncidentIntel. Removed again in notifyEnded().
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

    public int getReason() {
        return reason;
    }

    public int getCycle() {
        return cycle;
    }

    public int getOutcome() {
        return outcome;
    }

    public boolean isOutcomeAnnounced() {
        return outcomeAnnounced;
    }

    /**
     * Beat 2: the director has rolled the outcome. Idempotent, and a no-op on an intel that is
     * already over -- the same discipline as
     * {@link ExileHegLeagueIncidentIntel#markContained()}.
     */
    public void markOutcome(int outcome) {
        if (outcomeAnnounced || isEnding() || isEnded()) return;
        outcomeAnnounced = true;
        this.outcome = outcome;
        daysUntilEnd = intelDays;
        sendUpdateIfPlayerHasIntel(CEASEFIRE_OUTCOME_UPDATE, false);
    }

    @Override
    protected void advanceImpl(float amount) {
        float days = Global.getSector().getClock().convertToDays(amount);

        if (outcomeAnnounced) {
            daysUntilEnd -= days;
        }
        daysUntilBackstop -= days;

        boolean expired = (outcomeAnnounced && daysUntilEnd <= 0f) || daysUntilBackstop <= 0f;
        if (expired && !isEnding() && !isEnded()) {
            endAfterDelay();
        }
    }

    // ------------------------------------------------------------------
    // Presentation -- ALL PLACEHOLDER PROSE (sec 11 item 2 / R12)
    // ------------------------------------------------------------------

    @Override
    public String getName() {
        if (isEnding() || isEnded()) return getTitleForState() + " (Over)";
        if (isSendingUpdate() && CEASEFIRE_OUTCOME_UPDATE.equals(getListInfoParam())) {
            return getTitleForState() + " - Announced";
        }
        return getTitleForState();
    }

    protected String getTitleForState() {
        if (!outcomeAnnounced) return "Ceasefire Talks";
        if (outcome == ExileHegLeagueDirector.OUTCOME_STAND_DOWN) return "Temporary Ceasefire";
        if (outcome == ExileHegLeagueDirector.OUTCOME_PARTIAL) return "Partial Ceasefire";
        if (outcome == ExileHegLeagueDirector.OUTCOME_COLLAPSE) return "Ceasefire Talks Collapsed";
        return "Ceasefire Talks";
    }

    public String getSortString() {
        return "Ceasefire";
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    /** PLACEHOLDER. Why the doves convened -- the sec 3.2.5 three-way branch, in prose. */
    protected String getConvenedText() {
        String a = displayName(factionAId);
        String b = displayName(factionBId);
        if (reason == REASON_GENERAL_ENGAGEMENT) {
            return "With fleets committed across the border and neither side able to sustain the"
                    + " tempo, back-channel contacts between " + a + " and " + b + " have convened"
                    + " talks on a temporary ceasefire.";
        }
        if (reason == REASON_PREEMPTIVE) {
            return "Before the hardliners could force the issue, moderates on both sides have"
                    + " convened talks between " + a + " and " + b + " on a temporary ceasefire"
                    + " along the border.";
        }
        return "After the latest round of open operations, " + a + " and " + b + " have convened"
                + " talks on a temporary ceasefire.";
    }

    /** PLACEHOLDER. The announced result; every line names both belligerents. */
    protected String getOutcomeText() {
        String a = displayName(factionAId);
        String b = displayName(factionBId);
        if (outcome == ExileHegLeagueDirector.OUTCOME_STAND_DOWN) {
            return a + " and " + b + " have announced a temporary ceasefire. Both sides are"
                    + " standing down their forward elements; nobody is calling it a settlement.";
        }
        if (outcome == ExileHegLeagueDirector.OUTCOME_PARTIAL) {
            return a + " and " + b + " have announced a temporary ceasefire that holds only in"
                    + " part. Some commitments were made, several disputes were left open, and"
                    + " neither side has recalled everything it deployed.";
        }
        if (outcome == ExileHegLeagueDirector.OUTCOME_COLLAPSE) {
            return "The ceasefire talks between " + a + " and " + b + " have collapsed. Each"
                    + " delegation blames the other, and both have gone home with nothing agreed.";
        }
        return "No result has been announced from the talks between " + a + " and " + b + ".";
    }

    /** PLACEHOLDER. One-line summary for the intel bullets. */
    protected String getShortDesc() {
        if (!outcomeAnnounced) return "Talks are under way; no result announced";
        if (outcome == ExileHegLeagueDirector.OUTCOME_STAND_DOWN) {
            return "Ceasefire holds in full, for now";
        }
        if (outcome == ExileHegLeagueDirector.OUTCOME_PARTIAL) {
            return "Ceasefire holds in part";
        }
        if (outcome == ExileHegLeagueDirector.OUTCOME_COLLAPSE) {
            return "Talks collapsed; nothing agreed";
        }
        return "Talks are under way; no result announced";
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
        info.addPara(getShortDesc(), tc, initPad);
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

        info.addPara(getConvenedText(), opad);
        if (outcomeAnnounced) {
            info.addPara(getOutcomeText(), opad);
        } else {
            info.addPara("Nothing has been announced by either delegation.",
                    Misc.getGrayColor(), opad);
        }
        info.addPara(getShortDesc(), opad);
    }

    /**
     * Matches {@link ExileHegLeagueIncidentIntel}: a crest rather than a vanilla intel sprite.
     * {@code graphics/icons/intel/peace.png} exists in the vanilla install but is NOT bound to any
     * key in core {@code settings.json}, so there is no registered sprite name to ask for and
     * inventing one would be adding art plumbing. The crest is the shipped sibling pattern.
     */
    @Override
    public String getIcon() {
        FactionAPI a = factionAId != null ? Global.getSector().getFaction(factionAId) : null;
        if (a != null) return a.getCrest();
        return super.getIcon();
    }

    /**
     * NEUTRAL on purpose. The incident telegraph colours itself with the VICTIM's faction because
     * an incident has one; a ceasefire table has two equal parties and colouring it as one of them
     * would read as that side's announcement.
     */
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
