package data.scripts.exiles;

/**
 * The incident a landed or foiled Midan rung becomes (Pass 7 step 7d-2; war doc sec 6.1, LANDING
 * IN THE WAR CYCLE): "a League strike on a Hegemony world". It is the Pass-7 entry the incident
 * table was built to take (see {@link ExileHegLeagueIncident}'s seam note), but it is <b>never
 * rolled</b>: {@link #isEligible} is always false, and the director fires it directly from
 * {@code landMidanRung}. Offender is always the League, victim the Hegemony.
 *
 * <p>Text is PLACEHOLDER (assistant-written), and deliberately keeps the false flag intact: the
 * Hegemony blames the League, the League denies it, and nobody names Midan.
 */
public class ExileHegLeagueMidanIncident extends ExileHegLeagueIncident {

    public static final String ID = "midan_strike";

    public ExileHegLeagueMidanIncident() {
        super(ID, 0f, 1f);
        hegemonyOffenderChance = 0f;
    }

    @Override
    public boolean isEligible(ExileHegLeagueDirector director, ExileHegLeagueConfig config) {
        return false;
    }

    @Override
    public boolean isMidanFalseFlag() {
        return true;
    }

    @Override
    public String getTitle() {
        return "Strike on a Hegemony World";
    }

    @Override
    public String getRumorText(String offender, String victim, String site) {
        String where = site == null ? "a " + victim + " world" : site;
        return "Reports are coming in of an attack on " + where + " by a well-armed fleet flying "
                + offender + " colours.";
    }

    @Override
    public String getConfirmationText(String offender, String victim, String site) {
        String where = site == null ? "the attack" : "the attack on " + site;
        return victim + " has confirmed " + where + " and holds " + offender + " responsible. "
                + offender + " denies that any such operation was ordered.";
    }

    @Override
    public String getShortDesc(String offender, String victim) {
        return offender + " blamed; " + offender + " denies it";
    }

    @Override
    public String getContainedText(String offender, String victim, String site) {
        String where = site == null ? "the attack" : "the attack on " + site;
        return victim + " has lodged a formal protest over " + where + ". " + offender
                + " still denies any part in it, and neither government has taken the matter"
                + " further.";
    }
}
