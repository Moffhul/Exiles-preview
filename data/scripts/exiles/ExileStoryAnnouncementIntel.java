package data.scripts.exiles;

import java.awt.Color;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * A one-shot STORY ANNOUNCEMENT -- Pass 7 step 7b (docs/EXILES_PASS7_BUILD_PLAN.md sec 2.F).
 *
 * <p><b>One generic class, several uses</b> (CLAUDE.md: build reusable components generically from
 * the start). Callers so far: Admiral Cunningham's vow after a successful rung 3 (7e-2, via
 * {@link #postAfter}). The Diktat embargo notices it was also built for were removed 2026-11-01.
 * Nothing here knows which story it is telling: the caller supplies the title, the body, the
 * one-line summary, and who is speaking (a faction crest, or a person's portrait).
 *
 * <p><b>Behaviour:</b> posted with the standard vanilla notification, filed under the Intel
 * window's <b>Story</b> tag, visible for a fixed number of days, then ends itself. It is
 * self-advancing (the intel manager DISPLAYS intel, it does not advance it -- CLAUDE.md gotcha):
 * the constructor adds it as a script and {@link #notifyEnded()} removes it. Only Strings and
 * floats are saved.
 *
 * <p><b>The prose is the caller's, and is author text</b> (Sean writes the final versions). Keep
 * each caller's text in one place; this class never invents story text of its own beyond the
 * generic "(Over)" suffix.
 */
public class ExileStoryAnnouncementIntel extends BaseIntelPlugin {

    protected String title;
    /** Paragraphs separated by a blank line ("\n\n"). */
    protected String body;
    /** One line for the intel list bullets. */
    protected String shortDesc;
    /** Whose crest and colours the announcement wears; may be null. */
    protected String factionId;
    /** An ImportantPeople id whose portrait is shown instead of the crest; may be null. */
    protected String personId;
    /** Days left on the board. Saved, so the timer survives a load. */
    protected float daysRemaining;

    /**
     * Creates and posts an announcement in one call. Returns the intel (already on the board).
     *
     * @param days how long it stays on the board; clamped to at least 1.
     */
    public static ExileStoryAnnouncementIntel post(String title, String body, String shortDesc,
                                                   String factionId, String personId, float days) {
        ExileStoryAnnouncementIntel intel = new ExileStoryAnnouncementIntel(
                title, body, shortDesc, factionId, personId, days);
        Global.getSector().getIntelManager().addIntel(intel, false);
        Global.getLogger(ExileStoryAnnouncementIntel.class).info(
                "Exiles story announcement posted: \"" + title + "\" (" + (int) intel.daysRemaining
                        + " days, faction " + factionId + ", person " + personId + ")");
        return intel;
    }

    /**
     * Posts the announcement after {@code delayDays} (7e-2: Cunningham's vow, ~5 days after a landed
     * rung 3). A tiny saved script holds the text until then and removes itself; nothing is posted
     * early, and a load in between keeps the countdown.
     */
    public static void postAfter(float delayDays, String title, String body, String shortDesc,
                                 String factionId, String personId, float days) {
        if (delayDays <= 0f) {
            post(title, body, shortDesc, factionId, personId, days);
            return;
        }
        Global.getSector().addScript(
                new DelayedPost(delayDays, title, body, shortDesc, factionId, personId, days));
        Global.getLogger(ExileStoryAnnouncementIntel.class).info(
                "Exiles story announcement scheduled: \"" + title + "\" in " + delayDays + " days");
    }

    /** The countdown behind {@link #postAfter}. Named static class: it is saved. */
    public static class DelayedPost implements com.fs.starfarer.api.EveryFrameScript {
        protected float daysLeft;
        protected String title;
        protected String body;
        protected String shortDesc;
        protected String factionId;
        protected String personId;
        protected float days;
        protected boolean done = false;

        public DelayedPost(float delayDays, String title, String body, String shortDesc,
                           String factionId, String personId, float days) {
            this.daysLeft = delayDays;
            this.title = title;
            this.body = body;
            this.shortDesc = shortDesc;
            this.factionId = factionId;
            this.personId = personId;
            this.days = days;
        }

        public boolean isDone() {
            return done;
        }

        public boolean runWhilePaused() {
            return false;
        }

        public void advance(float amount) {
            if (done) return;
            daysLeft -= Global.getSector().getClock().convertToDays(amount);
            if (daysLeft > 0f) return;
            done = true;
            post(title, body, shortDesc, factionId, personId, days);
        }
    }

    public ExileStoryAnnouncementIntel(String title, String body, String shortDesc,
                                       String factionId, String personId, float days) {
        this.title = title;
        this.body = body;
        this.shortDesc = shortDesc;
        this.factionId = factionId;
        this.personId = personId;
        this.daysRemaining = Math.max(1f, days);

        // MANDATORY -- see the class javadoc. Removed again in notifyEnded().
        Global.getSector().addScript(this);
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
    }

    @Override
    protected void advanceImpl(float amount) {
        if (isEnding() || isEnded()) return;
        daysRemaining -= Global.getSector().getClock().convertToDays(amount);
        if (daysRemaining <= 0f) {
            endAfterDelay();
        }
    }

    protected FactionAPI getFaction() {
        if (factionId == null) return null;
        return Global.getSector().getFaction(factionId);
    }

    protected PersonAPI getPerson() {
        if (personId == null) return null;
        return Global.getSector().getImportantPeople().getPerson(personId);
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @Override
    public String getName() {
        String t = title != null ? title : "Announcement";
        if (isEnding() || isEnded()) return t + " (Over)";
        return t;
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    public String getSortString() {
        return "Story";
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        if (shortDesc == null || shortDesc.length() == 0) return;
        Color tc = getBulletColorForMode(mode);
        float initPad = 3f;
        if (mode == ListInfoMode.IN_DESC) initPad = 10f;
        bullet(info);
        info.addPara(shortDesc, tc, initPad);
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

        PersonAPI person = getPerson();
        FactionAPI faction = getFaction();
        if (person != null && person.getPortraitSprite() != null) {
            info.addImage(person.getPortraitSprite(), 128, opad);
        } else if (faction != null) {
            info.addImage(faction.getCrest(), 128, opad);
        }

        if (body != null) {
            String[] paras = body.split("\n\n");
            for (int i = 0; i < paras.length; i++) {
                if (paras[i].trim().length() == 0) continue;
                info.addPara(paras[i].trim(), opad);
            }
        }
        if (isEnding() || isEnded()) {
            info.addPara("This announcement is no longer current.", Misc.getGrayColor(), opad);
        }
    }

    @Override
    public String getIcon() {
        PersonAPI person = getPerson();
        if (person != null && person.getPortraitSprite() != null) return person.getPortraitSprite();
        FactionAPI faction = getFaction();
        if (faction != null) return faction.getCrest();
        return super.getIcon();
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        FactionAPI faction = getFaction();
        if (faction != null) return faction;
        return super.getFactionForUIColors();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_STORY);
        if (factionId != null) tags.add(factionId);
        return tags;
    }
}
