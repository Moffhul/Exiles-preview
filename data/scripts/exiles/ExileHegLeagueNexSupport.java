package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.FactionHostilityIntel;
import com.fs.starfarer.api.impl.campaign.intel.FactionHostilityManager;

/**
 * The Nexerelin premise-protection layers for the Heg-League war director
 * (docs/EXILES_HEG_LEAGUE_WAR.md sec 7.2-7.3; rulings R10 + R11).
 *
 * <p><b>This class contains ZERO Nexerelin types</b> and is safe on the startup script graph.
 * The one layer that genuinely needs a Nex API -- B2, the runtime max-relationship pin -- is
 * reached through {@link ExileHegLeagueNexHook}, whose only implementation is loaded by NAME
 * and only after {@link #isNexEnabled()} passes. Layers B3 and the conquest shields need no
 * Nexerelin classes at all: one re-asserts a vanilla relationship, the other writes market
 * memory keys that vanilla ignores.
 *
 * <p><b>Shipped stack (R11 rider): B2 + B3 + conquest shields.</b> B1 (the shipped merged
 * {@code hegemony.json} config pin) was DROPPED -- a static file cannot honour a runtime
 * switch, and B2 alone already trips {@code canCeasefire} false. Every layer is behind its own
 * player-facing switch in the config {@code nex{}} block and every layer is REVERSIBLE: turn a
 * switch off and the next game load removes what we installed.
 *
 * <p>Switches off means <i>graceful ride</i>, not sabotage: with {@code selfHealHostility}
 * off the director idles while the pair is at peace and resumes if war resumes. It never
 * fights a Nex peace the player has deliberately allowed.
 */
public class ExileHegLeagueNexSupport {

    public static final String NEX_MOD_ID = "nexerelin";

    /** Our modifier id inside Nex's max-relationship stat. Never rename: it is the removal key. */
    public static final String PIN_MODIFIER_ID = "exiles_hegleague";
    public static final String PIN_DESC = "Exiles: Hegemony-League war";

    /** Sector persistent flag guarding B2's ACCUMULATING modify call (test D4). */
    public static final String PIN_APPLIED_KEY = "$exiles_hl_nex_pin_applied";

    /** Nex's own key. Blocks NPC invasion/raid/sat-bomb/rebellion; player invasion still allowed. */
    public static final String NEX_NO_INVADE_KEY = "$nex_npc_no_invade";
    /** Our marker, so we only ever remove shields WE installed. */
    public static final String SHIELD_MARKER_KEY = "$exiles_hl_nex_shield";

    /** The class loaded by name. Referenced ONLY as this string -- see the class javadoc. */
    private static final String PIN_CLASS = "data.scripts.exiles.ExileHegLeagueNexPin";

    private ExileHegLeagueNexSupport() {
    }

    public static boolean isNexEnabled() {
        try {
            return Global.getSettings().getModManager().isModEnabled(NEX_MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Layer B2, apply-or-remove. Call once per game load, after config is loaded.
     *
     * @return a one-line summary of what changed, or null if nothing did.
     */
    public static String syncRelationPin(ExileHegLeagueConfig config) {
        if (config == null) return null;
        boolean applied = isPinRecorded();

        if (!isNexEnabled()) {
            // Nexerelin was removed from the mod list after the pin was written. The stat lives
            // in Nex's own faction memory and went with it, so just forget our flag.
            if (applied) {
                Global.getSector().getPersistentData().remove(PIN_APPLIED_KEY);
                return "Nex relation pin flag cleared (Nexerelin is not enabled).";
            }
            return null;
        }

        ExileHegLeagueNexHook hook = loadHook();
        if (hook == null) return "Nex relation pin UNAVAILABLE (hook could not be loaded).";

        if (config.nexRelationPin && !applied) {
            boolean ok = hook.applyPin(PIN_MODIFIER_ID, config.nexPinValue,
                    ExileHegLeagueConfig.HEGEMONY_ID, ExileHegLeagueConfig.PERSEAN_ID, PIN_DESC);
            if (!ok) return "Nex relation pin FAILED to apply.";
            Global.getSector().getPersistentData().put(PIN_APPLIED_KEY, Boolean.TRUE);
            return "Nex relation pin applied (" + config.nexPinValue + "; total now "
                    + hook.readPin(ExileHegLeagueConfig.HEGEMONY_ID,
                            ExileHegLeagueConfig.PERSEAN_ID) + ").";
        }

        if (!config.nexRelationPin && applied) {
            boolean ok = hook.removePin(PIN_MODIFIER_ID,
                    ExileHegLeagueConfig.HEGEMONY_ID, ExileHegLeagueConfig.PERSEAN_ID);
            if (!ok) return "Nex relation pin FAILED to remove.";
            Global.getSector().getPersistentData().remove(PIN_APPLIED_KEY);
            return "Nex relation pin removed (player option off).";
        }

        return null; // already in the requested state -- the D4 idempotency path
    }

    private static boolean isPinRecorded() {
        Object flag = Global.getSector().getPersistentData().get(PIN_APPLIED_KEY);
        return flag instanceof Boolean && ((Boolean) flag).booleanValue();
    }

    /**
     * Loads the Nexerelin-touching implementation by name through the script classloader --
     * the same lazy path Nex uses for {@code ExilesNexStart}. Only ever called when Nexerelin
     * is enabled, so Janino never compiles that source in a non-Nex install.
     */
    private static ExileHegLeagueNexHook loadHook() {
        try {
            ClassLoader loader = Global.getSettings().getScriptClassLoader();
            Class cls = loader.loadClass(PIN_CLASS);
            Object instance = cls.newInstance();
            if (instance instanceof ExileHegLeagueNexHook) {
                return (ExileHegLeagueNexHook) instance;
            }
            return null;
        } catch (Throwable t) {
            Global.getLogger(ExileHegLeagueNexSupport.class).warn(
                    "Exiles Heg-League: could not load " + PIN_CLASS, t);
            return null;
        }
    }

    /**
     * R11 conquest shields, apply-or-remove. Call once per game load.
     *
     * <p>Zero Nexerelin dependency by construction: vanilla ignores unknown market memory keys,
     * so this is written unconditionally rather than gated on the mod being present -- exactly
     * the {@code applyRefugeeConquestShield()} idiom. Our own marker key rides alongside Nex's
     * so a later removal touches only shields Exiles installed, never another mod's.
     *
     * <p>Markets that fall off the configured list are unshielded on the next load, so editing
     * the list is as reversible as flipping the switch.
     *
     * @return a one-line summary, or null if nothing changed.
     */
    public static String syncConquestShields(ExileHegLeagueConfig config) {
        if (config == null || Global.getSector() == null) return null;

        int added = 0;
        int removed = 0;

        // Resolve the configured ids to live markets FIRST, and remember the ids those markets
        // actually carry. The econ loader assigns its own market ids (CLAUDE.md sec 7), so
        // comparing the sweep below against the raw config strings would let a market be
        // shielded on one line and un-shielded on the next -- a thrash, once per load, forever.
        ArrayList wantedIds = new ArrayList(); // String
        if (config.nexConquestShields) {
            for (int i = 0; i < config.nexShieldMarkets.size(); i++) {
                String id = (String) config.nexShieldMarkets.get(i);
                MarketAPI market = Global.getSector().getEconomy().getMarket(id);
                if (market == null) {
                    Global.getLogger(ExileHegLeagueNexSupport.class).warn(
                            "Exiles Heg-League: conquest-shield market \"" + id
                                    + "\" not found; skipped.");
                    continue;
                }
                wantedIds.add(market.getId());
                if (market.getMemoryWithoutUpdate().contains(SHIELD_MARKER_KEY)) continue;
                market.getMemoryWithoutUpdate().set(NEX_NO_INVADE_KEY, true);
                market.getMemoryWithoutUpdate().set(SHIELD_MARKER_KEY, true);
                added++;
            }
        }

        // Sweep for stale shields: switch off, or the market left the configured list. Only
        // shields carrying OUR marker are ever removed -- never another mod's or Nex's own.
        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (!market.getMemoryWithoutUpdate().contains(SHIELD_MARKER_KEY)) continue;
            if (wantedIds.contains(market.getId())) continue;
            market.getMemoryWithoutUpdate().unset(NEX_NO_INVADE_KEY);
            market.getMemoryWithoutUpdate().unset(SHIELD_MARKER_KEY);
            removed++;
        }

        if (added == 0 && removed == 0) return null;
        return "Nex conquest shields: " + added + " applied, " + removed + " removed.";
    }

    /**
     * Layer B3, the self-heal backstop. Cheap enough to run on the director's ordinary
     * interval; it reacts rather than prevents, which is why it is the backstop and not the
     * primary. Works identically with Nexerelin absent -- it touches nothing but vanilla
     * relationships.
     *
     * @return true if hostility had to be re-asserted this tick.
     */
    public static boolean selfHealHostility(ExileHegLeagueConfig config) {
        if (config == null || !config.nexSelfHealHostility) return false;
        FactionAPI hegemony = Global.getSector().getFaction(ExileHegLeagueConfig.HEGEMONY_ID);
        FactionAPI persean = Global.getSector().getFaction(ExileHegLeagueConfig.PERSEAN_ID);
        if (hegemony == null || persean == null) return false;
        if (hegemony.isHostileTo(persean)) return false;

        hegemony.setRelationship(ExileHegLeagueConfig.PERSEAN_ID, RepLevel.HOSTILE);
        Global.getLogger(ExileHegLeagueNexSupport.class).info(
                "Exiles Heg-League B3 self-heal: Hegemony/Persean League were no longer "
                        + "hostile; hostility re-asserted.");
        return true;
    }

    /**
     * Retires vanilla's game-start Hegemony/League {@link FactionHostilityIntel} (ruled
     * 2026-10-25, PASS7 plan sec 1.5a). Vanilla seeds the pair's hostility as a TIMED flare-up:
     * after 180 days it rolls a 25% end every 30-90 days, restores the starting relationship
     * and tells the player "hostilities ended" -- a false peace notice in a campaign whose war
     * never ends (seen in the 7c test, Aug 14 c206). Ending the intel immediately skips its
     * endHostilties() path entirely, so the relationship stays HOSTILE and nothing is left to
     * roll a peace. Tied to the B3 switch: with selfHealHostility off the director rides any
     * peace, so vanilla's flare-up is left to run its normal course.
     *
     * @return true if a live vanilla intel was retired this call.
     */
    public static boolean retireVanillaHostilityIntel(ExileHegLeagueConfig config) {
        if (config == null || !config.nexSelfHealHostility) return false;
        FactionAPI hegemony = Global.getSector().getFaction(ExileHegLeagueConfig.HEGEMONY_ID);
        FactionAPI persean = Global.getSector().getFaction(ExileHegLeagueConfig.PERSEAN_ID);
        if (hegemony == null || persean == null) return false;
        FactionHostilityManager manager = FactionHostilityManager.getInstance();
        if (manager == null) return false;
        FactionHostilityIntel intel = manager.getHostilties(hegemony, persean);
        if (intel == null || intel.isEnding() || intel.isEnded()) return false;

        intel.endImmediately();
        Global.getLogger(ExileHegLeagueNexSupport.class).info(
                "Exiles Heg-League: vanilla Hegemony/League hostility intel retired "
                        + "(relationship left hostile; no false 'hostilities ended' notice).");
        return true;
    }

    /** True while the pair is mutually hostile -- the director's premise (sec 3.3). */
    public static boolean isPairHostile() {
        FactionAPI hegemony = Global.getSector().getFaction(ExileHegLeagueConfig.HEGEMONY_ID);
        FactionAPI persean = Global.getSector().getFaction(ExileHegLeagueConfig.PERSEAN_ID);
        if (hegemony == null || persean == null) return false;
        return hegemony.isHostileTo(persean);
    }

    /**
     * True while the named faction still holds at least one market. Queried live every cycle
     * per R6c -- ownership can flip under Nexerelin and nothing here may be hardcoded.
     */
    public static boolean holdsAnyMarket(String factionId) {
        if (Global.getSector() == null) return false;
        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (factionId.equals(market.getFactionId())) return true;
        }
        return false;
    }
}
