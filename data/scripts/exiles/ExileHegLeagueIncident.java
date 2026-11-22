package data.scripts.exiles;

import java.util.ArrayList;

/**
 * One entry in the Hegemony-Persean League INCIDENT TABLE -- PASS 3 of
 * docs/EXILES_HEG_LEAGUE_WAR.md (sec 12 row 3; the table itself is sec 3.2.2 state 2).
 *
 * <p>An incident is the spark that starts a crisis cycle: it names an <b>offender</b> and a
 * <b>victim</b> (both FACTIONS -- no market is harmed by the incident itself), adds tension scaled
 * by its own severity, and posts as rumour-then-confirmation intel
 * ({@link ExileHegLeagueIncidentIntel}). The victim's answer is the RETALIATION state's punitive
 * op, which is where anything mechanical finally happens.
 *
 * <h2>The four shipped entries (sec 3.2.2's own examples, non-Midan)</h2>
 * <ul>
 * <li><b>border_clash</b> -- patrols traded fire on the frontier. The common, cheap one.</li>
 * <li><b>convoy_destroyed</b> -- a civilian convoy destroyed, with survivors testifying.</li>
 * <li><b>violent_inspection</b> -- an inspection stop escalated into shooting. Skewed toward the
 *     Hegemony as offender, because stop-and-search is the Hegemony's institutional habit.</li>
 * <li><b>civilian_casualties</b> -- a raid hit civilian infrastructure. The rare, expensive one.</li>
 * </ul>
 *
 * <h2>PASS 7 SEAM -- read this before adding an entry</h2>
 * The Midan false flag (sec 6.1, ruling R8) is an entry in <i>this</i> table and is deliberately
 * NOT built here. It needs a state gate (eligible only when de-escalation is winning), a long
 * cooldown and a per-campaign firing cap, which is exactly what {@link #isEligible} exists for:
 * subclass this class, override {@code isEligible}, add the instance to the table, and nothing
 * else in the incident pipeline has to change. The director already treats every entry
 * polymorphically -- it picks, reads {@code severity}, reads the offender bias, and posts the
 * intel from the strings this class returns.
 *
 * <h2>Text is PLACEHOLDER</h2>
 * Every player-facing string below is assistant-written placeholder pending sec 11 item 2 (the
 * author's player-facing text deliverable). It is written <b>faction-neutral and composed from
 * parameters</b>, because either side can be the offender in any entry -- the same sentence has to
 * read correctly in both directions. Nothing here is accepted canon.
 */
public class ExileHegLeagueIncident {

    public static final String BORDER_CLASH = "border_clash";
    public static final String CONVOY_DESTROYED = "convoy_destroyed";
    public static final String VIOLENT_INSPECTION = "violent_inspection";
    public static final String CIVILIAN_CASUALTIES = "civilian_casualties";

    /** Stable id; the config key and the saved reference. */
    public String id;
    public boolean enabled = true;
    /** Relative pick weight. 0 (or disabled) removes the entry from the table. */
    public float weight;
    /**
     * Multiplies {@code tension.gains.perIncident} (sec 3.2.1: incidents are "moderate; scaled by
     * incident severity"). The shipped values are centred near 1.0 on purpose -- the ratified gain
     * is 8, and this pass ships the ratified value rather than a rebalance dressed up as severity.
     */
    public float severity;
    /**
     * Chance this entry's offender is the Hegemony; -1 uses
     * {@code cycle.incidentOffenderHegemonyChance}. A soft bias, never a hard lock: a war where one
     * side can never be at fault reads as scripted.
     */
    public float hegemonyOffenderChance = -1f;

    public ExileHegLeagueIncident(String id, float weight, float severity) {
        this.id = id;
        this.weight = weight;
        this.severity = severity;
    }

    /**
     * <b>THE PASS-7 SEAM.</b> May this entry fire right now? The base rule is "if it is switched on
     * and carries weight"; the Midan false flag will override this with its tension-floor-days
     * gate, cooldown and per-campaign cap (sec 6.1).
     */
    public boolean isEligible(ExileHegLeagueDirector director, ExileHegLeagueConfig config) {
        return enabled && weight > 0f;
    }

    /** Does this entry involve Midan? Base entries never do; Pass 7's override returns true and
     *  the director then routes its tension through {@code reportMidanInterference()}. */
    public boolean isMidanFalseFlag() {
        return false;
    }

    // ------------------------------------------------------------------
    // Player-facing text -- ALL PLACEHOLDER (sec 11 item 2)
    // ------------------------------------------------------------------

    /** Intel headline. */
    public String getTitle() {
        if (CONVOY_DESTROYED.equals(id)) return "Convoy Destroyed";
        if (VIOLENT_INSPECTION.equals(id)) return "Inspection Turned Violent";
        if (CIVILIAN_CASUALTIES.equals(id)) return "Civilian Casualties";
        return "Border Clash";
    }

    /**
     * The unconfirmed first beat. Deliberately hedged -- at this point nobody has established what
     * happened, which is the whole point of a rumour-then-confirmation pair.
     *
     * @param site a market name, or null when nothing nameable resolved.
     */
    public String getRumorText(String offender, String victim, String site) {
        String where = site == null ? "the border" : ("the approaches to " + site);
        if (CONVOY_DESTROYED.equals(id)) {
            return "Traffic control reports a " + victim + " convoy overdue and unaccounted for near "
                    + where + ". Unverified transmissions describe an attack by warships that did"
                    + " not answer hails.";
        }
        if (VIOLENT_INSPECTION.equals(id)) {
            return "Conflicting accounts are circulating of a stop-and-search near " + where
                    + " that ended in gunfire. " + offender + " channels are not commenting.";
        }
        if (CIVILIAN_CASUALTIES.equals(id)) {
            return "Refugee traffic out of " + where + " is carrying claims of civilian dead after"
                    + " a raid on outlying habitats. Casualty figures vary wildly by source.";
        }
        return "Patrol elements are reported to have exchanged fire near " + where
                + ". Neither command has confirmed losses.";
    }

    /**
     * The confirmation beat: what the victim's government has now decided happened, and therefore
     * what its hawks will be demanding an answer for.
     */
    public String getConfirmationText(String offender, String victim, String site) {
        String where = site == null ? "the border" : site;
        if (CONVOY_DESTROYED.equals(id)) {
            return victim + " authorities have confirmed the loss of the convoy near " + where
                    + " and released survivor testimony naming " + offender + " warships. "
                    + offender + " denies the account.";
        }
        if (VIOLENT_INSPECTION.equals(id)) {
            return victim + " has confirmed that a " + offender + " inspection party opened fire"
                    + " during a boarding near " + where + ", and has demanded the crew's release.";
        }
        if (CIVILIAN_CASUALTIES.equals(id)) {
            return victim + " has confirmed civilian dead at " + where + " and formally attributed"
                    + " the strike to " + offender + ". " + offender + " calls the figures"
                    + " fabricated.";
        }
        return victim + " has confirmed an engagement with " + offender + " forces near " + where
                + " and lodged a formal protest. Both sides claim they were fired on first.";
    }

    /** One-line summary for intel bullets. */
    public String getShortDesc(String offender, String victim) {
        return offender + " blamed; " + victim + " is preparing a response";
    }

    /**
     * The containment beat (sec 3.2.5, 2026-09-13): the cycle closed without ceasefire talks.
     * The victim's protest is filed, the border playbook has absorbed the incident, and nothing
     * more happens -- which is most cycles, by design. PLACEHOLDER prose (sec 11 item 2).
     */
    public String getContainedText(String offender, String victim, String site) {
        String where = site == null ? "the border" : site;
        if (CONVOY_DESTROYED.equals(id)) {
            return victim + " has lodged a formal protest over the convoy lost near " + where
                    + " and demanded compensation. " + offender + " has not acknowledged the"
                    + " demand, and neither government has taken the matter further.";
        }
        if (VIOLENT_INSPECTION.equals(id)) {
            return "The detained crew have been quietly returned. " + victim + " has lodged a"
                    + " formal protest over the boarding near " + where + "; " + offender
                    + " has reissued its inspection orders. Both governments consider the matter"
                    + " closed.";
        }
        if (CIVILIAN_CASUALTIES.equals(id)) {
            return victim + " has lodged a formal protest over the civilian dead at " + where
                    + ". " + offender + " continues to dispute the figures. Quiet diplomacy has"
                    + " kept the matter off the assembly floor, for now.";
        }
        return victim + " has lodged a formal protest with " + offender + " over the engagement"
                + " near " + where + ". Both commands have reissued their standing orders on the"
                + " border, and neither government has taken the matter further.";
    }

    /** One-line summary once contained. */
    public String getContainedShortDesc(String offender, String victim) {
        return "Formal protest lodged; " + victim + " considers the matter closed";
    }

    // ------------------------------------------------------------------
    // The table
    // ------------------------------------------------------------------

    /**
     * The shipped non-Midan table. Weights and severities are TUNING and are read back from
     * {@code director_config.json}; this is the code default so a missing or broken config block
     * degrades to shipped behaviour rather than to an empty table.
     *
     * <p><b>Weighted-average severity is ~1.03</b> ((3x0.75 + 2x1.25 + 2x1.0 + 1x1.5) / 8), so the
     * average incident is worth the ratified {@code perIncident} gain of 8 and this pass changes
     * no ratified number. See the sec 14.4 build log for the arithmetic on whether that can reach
     * {@code hawkThreshold}.
     *
     * @return an ArrayList of ExileHegLeagueIncident.
     */
    public static ArrayList defaultTable() {
        ArrayList table = new ArrayList();
        table.add(new ExileHegLeagueIncident(BORDER_CLASH, 3f, 0.75f));
        table.add(new ExileHegLeagueIncident(CONVOY_DESTROYED, 2f, 1.25f));
        ExileHegLeagueIncident inspection =
                new ExileHegLeagueIncident(VIOLENT_INSPECTION, 2f, 1f);
        // Soft bias, not a lock (see the field javadoc): stop-and-search is the Hegemony's habit,
        // and vanilla already gives it a whole inspection intel of its own -- but the League runs
        // customs too, so one time in four it is the League's boarding party that panics.
        inspection.hegemonyOffenderChance = 0.75f;
        table.add(inspection);
        table.add(new ExileHegLeagueIncident(CIVILIAN_CASUALTIES, 1f, 1.5f));
        return table;
    }

    /** The entry with this id, or null. */
    public static ExileHegLeagueIncident find(ArrayList table, String id) {
        if (table == null || id == null) return null;
        for (int i = 0; i < table.size(); i++) {
            ExileHegLeagueIncident entry = (ExileHegLeagueIncident) table.get(i);
            if (entry != null && id.equals(entry.id)) return entry;
        }
        return null;
    }
}
