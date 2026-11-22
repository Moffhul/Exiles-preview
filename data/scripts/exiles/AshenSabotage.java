package data.scripts.exiles;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import data.scripts.marketconditions.ExileCrusadeStrike;

/**
 * Ashen Crusade sabotage -- PASS 2 of docs/EXILES_ASHEN_CRUSADE_WAR.md §5.
 *
 * <p>The Pather half of the Crusade's character, and the cheapest subsystem in the mod: no fleets,
 * no intel, no {@code RaidIntel}. A monthly dice roll and two effects. Held as a transient field
 * by {@link AshenWarDirector}, which owns the cadence state; this class owns the config and the
 * logic.
 *
 * <p><b>Two tiers.</b> ORDINARY sabotage (1/month in Phases 1-2, 2/month in Phase 3) knocks out
 * industrial capacity and lands {@link ExileCrusadeStrike}. SPECIAL sabotage (once, in the last
 * month of Phase 2) goes after one of four named installations, hits harder, and is the sole
 * trigger for the one-time Culann strike.
 *
 * <p><b>Odds direction (§5.1, clarified in implementation).</b> The spec's formula reads
 * {@code baseChance × Π(modifier per FUNCTIONAL defensive industry)}, but that cannot produce the
 * ratified numbers: the author's 25% for the outposts is the chance <i>with defenses intact</i>,
 * so multiplying it down by present defenses would undershoot. Implemented the other way round --
 * {@code baseChance} IS the fully-defended chance, and every <b>non-functional</b> defensive
 * industry multiplies it UP. Same intent, correct arithmetic, and it preserves the cross-system
 * interaction the spec wanted: an Omicron or Black Fleet strike that disrupts Culann's Military
 * Base opens a real Crusade sabotage window.
 *
 * <p><b>Silence.</b> Ordinary sabotage never messages the player (§3.4) -- the condition appearing
 * on the market is the only tell. The one exception is a successful SPECIAL sabotage against a
 * Culann target, which is the ratified §7.0 telegraph: Tri-Tachyon would publicise a strike on
 * Culann's Military Base, and it is the player's warning that the Culann strike is coming.
 */
public class AshenSabotage {

    /** Defensive industry categories consulted for the odds. Tag-based (with vanilla ids as
     *  belt-and-suspenders) so custom and upgraded industries classify correctly -- the
     *  OmicronRaidActionStage lesson: id-only checks dropped a garrison into the wrong bucket,
     *  and station tiers like {@code battlestation_high} never match a bare id constant. */
    public static final String[] DEFENSE_TAGS = new String[] {
            "grounddefenses", "patrol", "military", "command", "station" };

    /** Industry categories ordinary sabotage may disable (ruling D). Never population; never
     *  military -- knocking out defenses is the SPECIAL tier's job, and letting the ordinary tier
     *  do it would collapse the distinction. {@code commerce} is deliberately ABSENT: disrupting
     *  vanilla's TradeCenter removes the -3 stability it imposes, so sabotaging it would HELP the
     *  target (verified 2026-07-31, §5.2). */
    public static final String[] DEFAULT_INDUSTRY_TAGS = new String[] {
            "mining", "refining", "heavyindustry", "lightindustry", "farming", "spaceport" };

    // --- Config (transient by construction: this object is rebuilt each launch) ---
    protected boolean enabled = true;
    protected List targetMarketIds;          // String
    protected Map baseChance;                // String marketId -> Float
    protected float defenseDownMult = 2.0f;  // per NON-functional defensive industry
    protected float maxChance = 0.9f;
    protected float impossibleFloor = 0.01f;
    protected int attemptsPhase1 = 1;
    protected int attemptsPhase2 = 1;
    protected int attemptsPhase3 = 2;
    protected float cadenceDays = 30f;

    protected List industryTags;             // String
    protected int industriesDisabled = 3;
    protected float disableDays = 60f;
    protected float spaceportWeight = 0.35f;    // ruling L: not the default pick every month
    protected float spaceportDisableDays = 30f; // ruling L: shorter than the rest

    protected float strikeDays = 30f;
    protected int strikeCommodityCount = 3;
    protected int strikeMagnitude = 2;

    // Special tier
    protected List specialTargets;           // SpecialTarget
    protected float specialBaseChance = 0.25f;
    protected float specialMatureChance = 0.50f;
    protected float specialDisableDays = 90f;
    protected int specialUnrestPoints = 2;
    protected float culannWeightEarly = 0.5f;
    protected float culannWeightMature = 6f;
    protected float specialWindowDays = 30f;

    protected Random random = new Random();

    /** One special-sabotage target: a named industry on a named market (§5.4). */
    public static class SpecialTarget {
        public String marketId;
        public String industryId;
        public boolean triggersPhase4;

        public SpecialTarget(String marketId, String industryId, boolean triggersPhase4) {
            this.marketId = marketId;
            this.industryId = industryId;
            this.triggersPhase4 = triggersPhase4;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public float getCadenceDays() {
        return cadenceDays;
    }

    public float getSpecialWindowDays() {
        return specialWindowDays;
    }

    public int getAttemptsPerMonth(int phase) {
        if (phase == AshenWarDirector.PHASE_BUILDUP) return attemptsPhase1;
        if (phase == AshenWarDirector.PHASE_INTERDICTION) return attemptsPhase2;
        if (phase == AshenWarDirector.PHASE_BLOCKADE) return attemptsPhase3;
        return 0; // Phase 4: the fleets are doing the talking
    }

    // ------------------------------------------------------------------
    // Ordinary sabotage (§5.2)
    // ------------------------------------------------------------------

    /**
     * One ordinary attempt. Picks a target weighted by its own success chance (an operative-poor
     * faction plays the odds), rolls, and on success applies the effects.
     *
     * <p>Ruling J1: targets below {@code impossibleFloor} are skipped, and if EVERY target is
     * below it the attempt is abandoned entirely rather than rerolled onto the least-bad option --
     * the Crusade husbanding scarce operatives.
     */
    public void attemptOrdinary(AshenWarDirector director) {
        if (!enabled) return;

        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        for (int i = 0; i < targetMarketIds.size(); i++) {
            MarketAPI market = getMarket((String) targetMarketIds.get(i));
            if (market == null) continue;
            float chance = getOrdinaryChance(market);
            if (chance < impossibleFloor) continue;
            picker.add(market, chance);
        }

        MarketAPI target = (MarketAPI) picker.pick();
        if (target == null) {
            log("ordinary sabotage: no viable target this month (all below the floor)");
            return;
        }

        float chance = getOrdinaryChance(target);
        boolean success = director.isSabotageAlwaysSucceeds() || random.nextFloat() < chance;
        log("ordinary sabotage vs " + target.getName() + ": chance="
                + fmt(chance) + " -> " + (success ? "SUCCESS" : "failed"));
        // Silent to the player either way (§3.4); the debug channel is opt-in.
        director.debug("Ashen sabotage vs " + target.getName() + " (" + fmt(chance) + "): "
                + (success ? "SUCCESS" : "failed"));

        if (!success) return;

        ExileCrusadeStrike.applyTo(target);
        int disrupted = disableIndustries(target, industriesDisabled, false);
        ExileUnrestGuard.reportExilesPressure(target,
                ExileEmergencyMeasuresConfig.pressureDays(
                        Math.max(disableDays, spaceportDisableDays), 0));
        log("ordinary sabotage hit " + target.getName() + ": Crusade Strike + "
                + disrupted + " industries offline");
    }

    /**
     * Fully-defended base chance, multiplied UP for each defensive industry that is currently
     * NON-functional. See the class javadoc for why the direction is inverted relative to the
     * spec text. Disrupted defenses are exactly the window other Exiles directors open.
     */
    public float getOrdinaryChance(MarketAPI market) {
        Float base = (Float) baseChance.get(market.getId());
        if (base == null) return 0f;

        float chance = base.floatValue();
        List industries = new ArrayList(market.getIndustries());
        for (int i = 0; i < industries.size(); i++) {
            Industry ind = (Industry) industries.get(i);
            if (ind == null || !isDefensive(ind)) continue;
            if (!ind.isFunctional()) {
                chance *= defenseDownMult;
            }
        }
        if (chance > maxChance) chance = maxChance;
        return chance;
    }

    // ------------------------------------------------------------------
    // Special sabotage (§5.4)
    // ------------------------------------------------------------------

    /**
     * The once-per-Phase-2 attempt against a named installation. Target selection is weighted, and
     * the Culann pair's weight climbs with war score (ruling O1) -- the Crusade probes Eochu Bres
     * while weak and goes for the war-winning target only when it believes it can land the blow.
     * The success chance escalates with war score on the same principle (§5.5).
     */
    public void attemptSpecial(AshenWarDirector director) {
        if (!enabled) return;

        float score = clamp01(director.getWarScore());
        float culannWeight = culannWeightEarly + (culannWeightMature - culannWeightEarly) * score;

        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        for (int i = 0; i < specialTargets.size(); i++) {
            SpecialTarget target = (SpecialTarget) specialTargets.get(i);
            MarketAPI market = getMarket(target.marketId);
            if (market == null) continue;
            Industry ind = market.getIndustry(target.industryId);
            // A target already knocked out is not worth an operative's life.
            if (ind == null || !ind.isFunctional()) continue;
            picker.add(target, target.triggersPhase4 ? culannWeight : 1f);
        }

        SpecialTarget chosen = (SpecialTarget) picker.pick();
        if (chosen == null) {
            log("special sabotage: no functional target available this cycle");
            return;
        }

        MarketAPI market = getMarket(chosen.marketId);
        Industry industry = market.getIndustry(chosen.industryId);
        float chance = specialBaseChance + (specialMatureChance - specialBaseChance) * score;
        boolean success = director.isSabotageAlwaysSucceeds() || random.nextFloat() < chance;

        log("SPECIAL sabotage vs " + market.getName() + " / " + chosen.industryId
                + ": chance=" + fmt(chance) + " (war score " + fmt(score) + ") -> "
                + (success ? "SUCCESS" : "failed"));
        director.debug("Ashen SPECIAL sabotage vs " + market.getName() + " "
                + chosen.industryId + " (" + fmt(chance) + "): "
                + (success ? "SUCCESS" : "failed"));

        if (!success) return;

        extendDisruption(industry, specialDisableDays);
        ExileCrusadeStrike.applyTo(market);
        ExileUnrestGuard.reportExilesPressure(market,
                ExileEmergencyMeasuresConfig.pressureDays(
                        specialDisableDays, specialUnrestPoints));

        // Recent Unrest routed through the deciv guard, exactly as Omicron bombardment does:
        // unrest stacks uncapped and decays only ~1 point per 90 days, and Hybrasil is already
        // double-teamed by Omicron ops. This is the Gilead lesson from playtest-4.
        if (!ExileUnrestGuard.isBattered(market)) {
            RecentUnrest.get(market).add(specialUnrestPoints, "Sabotage");
        } else {
            log("unrest deciv-guard: skipped special-sabotage unrest at " + market.getName());
        }

        if (chosen.triggersPhase4) {
            // §3.2a, ruling 2 (2026-08-13): this NO LONGER queues the Culann strike. A Culann
            // success is now an accumulating reconnaissance step feeding the preparation meter,
            // which replaced a single coin flip with a series. The config key still reads
            // "triggersPhase4" for backwards compatibility with anyone's tuned file -- it now
            // means "counts as preparation against Culann". Renaming it needs Sean's sign-off.
            director.reportSpecialSabotage();

            // NOT the §7.0 telegraph any more -- that moved to AshenWarDirector, onto the
            // preparation crossing, because tying the campaign's one warning to a dice roll is
            // exactly what the reframe removed. This is ambient signal: the player hears that the
            // Crusade is working Culann over, without being promised an imminent offensive.
            Global.getSector().getCampaignUI().addMessage(
                    "Reports from Hybrasil: saboteurs have crippled the " + industryName(industry)
                            + " on " + market.getName()
                            + ". Tri-Tachyon blames the Ashen Crusade.",
                    Color.ORANGE);
        }
    }

    // ------------------------------------------------------------------
    // Effects
    // ------------------------------------------------------------------

    /** Disable up to {@code count} functional, eligible industries. Returns how many were hit. */
    protected int disableIndustries(MarketAPI market, int count, boolean includeSpaceport) {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        List industries = new ArrayList(market.getIndustries());
        for (int i = 0; i < industries.size(); i++) {
            Industry ind = (Industry) industries.get(i);
            if (ind == null || !ind.isFunctional()) continue;
            if (Industries.POPULATION.equals(ind.getId())) continue; // never
            if (isDefensive(ind)) continue;                          // special tier's job
            if (!isEligible(ind)) continue;
            // Ruling L: spaceport/megaport disruption craters accessibility, vanilla's biggest
            // economic lever. Down-weighted so it is not the default pick every month.
            picker.add(ind, isSpaceport(ind) ? spaceportWeight : 1f);
        }

        int hit = 0;
        for (int i = 0; i < count; i++) {
            Industry ind = (Industry) picker.pickAndRemove();
            if (ind == null) break;
            extendDisruption(ind, isSpaceport(ind) ? spaceportDisableDays : disableDays);
            hit++;
        }
        return hit;
    }

    /**
     * Take the longer of the existing disruption and this one, never the sum.
     *
     * Both callers pick only from functional industries, and vanilla
     * {@code BaseIndustry.isFunctional()} opens with {@code if (isDisrupted()) return false;} —
     * so in practice this always receives an undisrupted industry and the distinction is moot
     * today. It is written defensively anyway: the previous form added the durations, which is
     * safe only as an accident of a filter two methods away. Loosen that filter, or call this
     * from somewhere new, and additive stacking drifts an industry toward permanent shutdown —
     * the decivilization path Exiles guards against everywhere else.
     *
     * {@code setDisrupted(days, true)} is vanilla's own max-semantics overload
     * ({@code dur = Math.max(memory.getExpire(key), dur)}), so this is the engine's behaviour,
     * not arithmetic of ours.
     */
    protected static void extendDisruption(Industry industry, float days) {
        if (industry == null) return;
        industry.setDisrupted(days, true);
    }

    protected boolean isEligible(Industry industry) {
        for (int i = 0; i < industryTags.size(); i++) {
            if (industry.getSpec().hasTag((String) industryTags.get(i))) return true;
        }
        // Belt-and-suspenders for ids whose spec tags do not carry the category.
        String id = industry.getId();
        return Industries.MINING.equals(id) || Industries.REFINING.equals(id)
                || Industries.HEAVYINDUSTRY.equals(id) || Industries.ORBITALWORKS.equals(id)
                || Industries.LIGHTINDUSTRY.equals(id) || Industries.FARMING.equals(id)
                || Industries.AQUACULTURE.equals(id) || isSpaceport(industry);
    }

    protected static boolean isSpaceport(Industry industry) {
        String id = industry.getId();
        if (Industries.SPACEPORT.equals(id) || Industries.MEGAPORT.equals(id)) return true;
        return industry.getSpec().hasTag("spaceport");
    }

    protected static boolean isDefensive(Industry industry) {
        for (int i = 0; i < DEFENSE_TAGS.length; i++) {
            if (industry.getSpec().hasTag(DEFENSE_TAGS[i])) return true;
        }
        String id = industry.getId();
        return Industries.GROUNDDEFENSES.equals(id) || Industries.HEAVYBATTERIES.equals(id)
                || Industries.PATROLHQ.equals(id) || Industries.MILITARYBASE.equals(id)
                || Industries.HIGHCOMMAND.equals(id);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    protected static MarketAPI getMarket(String id) {
        if (Global.getSector() == null) return null;
        MarketAPI market = Global.getSector().getEconomy().getMarket(id);
        if (market == null || !market.isInEconomy()) return null;
        return market;
    }

    protected static String industryName(Industry industry) {
        if (industry == null) return "installation";
        try {
            return industry.getCurrentName();
        } catch (Exception e) {
            return industry.getId();
        }
    }

    protected static float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }

    protected static String fmt(float value) {
        return "" + (Math.round(value * 100f) / 100f);
    }

    protected static void log(String text) {
        Global.getLogger(AshenSabotage.class).info("Ashen " + text);
    }

    // ------------------------------------------------------------------
    // Config
    // ------------------------------------------------------------------

    /** @param timeScale the director's §9.1 multiplier, applied to every duration here too. */
    public void loadConfig(JSONObject factionCfg, float timeScale) {
        applyDefaults();

        try {
            JSONObject cfg = factionCfg == null ? null : factionCfg.optJSONObject("sabotage");
            if (cfg != null) {
                enabled = cfg.optBoolean("enabled", enabled);
                defenseDownMult = (float) cfg.optDouble("defenseDownMult", defenseDownMult);
                maxChance = (float) cfg.optDouble("maxChance", maxChance);
                impossibleFloor = (float) cfg.optDouble("impossibleFloor", impossibleFloor);
                cadenceDays = (float) cfg.optDouble("cadenceDays", cadenceDays);
                attemptsPhase1 = cfg.optInt("attemptsPerMonthPhase1", attemptsPhase1);
                attemptsPhase2 = cfg.optInt("attemptsPerMonthPhase2", attemptsPhase2);
                attemptsPhase3 = cfg.optInt("attemptsPerMonthPhase3", attemptsPhase3);
                industriesDisabled = cfg.optInt("industriesDisabled", industriesDisabled);
                disableDays = (float) cfg.optDouble("disableDays", disableDays);
                spaceportWeight = (float) cfg.optDouble("spaceportWeight", spaceportWeight);
                spaceportDisableDays = (float) cfg.optDouble(
                        "spaceportDisableDays", spaceportDisableDays);
                strikeDays = (float) cfg.optDouble("strikeDays", strikeDays);
                strikeCommodityCount = cfg.optInt("strikeCommodityCount", strikeCommodityCount);
                strikeMagnitude = cfg.optInt("strikeMagnitude", strikeMagnitude);

                JSONObject chances = cfg.optJSONObject("baseChance");
                if (chances != null) {
                    baseChance = new HashMap();
                    targetMarketIds = new ArrayList();
                    java.util.Iterator keys = chances.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        baseChance.put(key, new Float((float) chances.optDouble(key, 0d)));
                        targetMarketIds.add(key);
                    }
                }

                JSONArray tags = cfg.optJSONArray("industryTags");
                if (tags != null && tags.length() > 0) {
                    industryTags = new ArrayList();
                    for (int i = 0; i < tags.length(); i++) {
                        industryTags.add(tags.getString(i));
                    }
                }

                JSONObject special = cfg.optJSONObject("special");
                if (special != null) {
                    specialBaseChance = (float) special.optDouble(
                            "baseChance", specialBaseChance);
                    specialMatureChance = (float) special.optDouble(
                            "matureChance", specialMatureChance);
                    specialDisableDays = (float) special.optDouble(
                            "disableDays", specialDisableDays);
                    specialUnrestPoints = special.optInt("unrestPoints", specialUnrestPoints);
                    culannWeightEarly = (float) special.optDouble(
                            "culannWeightEarly", culannWeightEarly);
                    culannWeightMature = (float) special.optDouble(
                            "culannWeightMature", culannWeightMature);
                    specialWindowDays = (float) special.optDouble(
                            "windowDays", specialWindowDays);

                    JSONArray targets = special.optJSONArray("targets");
                    if (targets != null && targets.length() > 0) {
                        specialTargets = new ArrayList();
                        for (int i = 0; i < targets.length(); i++) {
                            JSONObject entry = targets.getJSONObject(i);
                            specialTargets.add(new SpecialTarget(
                                    entry.getString("market"),
                                    entry.getString("industry"),
                                    entry.optBoolean("triggersPhase4", false)));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Global.getLogger(AshenSabotage.class).warn(
                    "Ashen sabotage config load failed; using code defaults", e);
        }

        if (timeScale > 0f && timeScale != 1f) {
            cadenceDays *= timeScale;
            disableDays *= timeScale;
            spaceportDisableDays *= timeScale;
            specialDisableDays *= timeScale;
            strikeDays *= timeScale;
            specialWindowDays *= timeScale;
        }

        // The condition reads its numbers from static fields so the tooltip and the timer agree
        // with config without threading a reference through the econ layer.
        ExileCrusadeStrike.DURATION_DAYS = strikeDays;
        ExileCrusadeStrike.HIT_COUNT = strikeCommodityCount;
        ExileCrusadeStrike.HIT_UNITS = strikeMagnitude;
    }

    /** Safe defaults matching the ratified spec, used if config is missing or malformed. */
    protected void applyDefaults() {
        baseChance = new HashMap();
        baseChance.put("culann", new Float(0.02f));
        baseChance.put("eochu_bres", new Float(0.02f));
        baseChance.put("tibicena", new Float(0.25f));
        baseChance.put("port_tse", new Float(0.25f));

        targetMarketIds = new ArrayList();
        targetMarketIds.add("culann");
        targetMarketIds.add("eochu_bres");
        targetMarketIds.add("tibicena");
        targetMarketIds.add("port_tse");

        industryTags = new ArrayList();
        for (int i = 0; i < DEFAULT_INDUSTRY_TAGS.length; i++) {
            industryTags.add(DEFAULT_INDUSTRY_TAGS[i]);
        }

        specialTargets = new ArrayList();
        specialTargets.add(new SpecialTarget("eochu_bres", Industries.MEGAPORT, false));
        specialTargets.add(new SpecialTarget("eochu_bres", Industries.FARMING, false));
        specialTargets.add(new SpecialTarget("culann", Industries.ORBITALWORKS, true));
        specialTargets.add(new SpecialTarget("culann", Industries.MILITARYBASE, true));
    }
}
