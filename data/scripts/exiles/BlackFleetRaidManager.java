package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidAssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.ReturnStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.TravelStage;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/**
 * Black Fleet raid launcher (M6 of the split build, 2026-07-07). Classic pirate commodity
 * raids from the two anchor stations against Core factions, on a config cadence.
 *
 * DELIBERATELY MINIMAL — this is NOT the Phase-1 Director generalization. The Omicron
 * Director's node/asset/escalation architecture is Omicron-specific; refactoring it into the
 * multi-faction "Sector Destabilization Director" is scheduled Phase-1 work (CLAUDE.md §6).
 * This manager reuses the PURE VANILLA raid pipeline instead (RaidIntel + PirateRaid stages,
 * cribbed from PirateBaseIntel.startRaid) so the eventual Director refactor can absorb it as
 * config, not code. Vanilla owns fleet lifecycle, intel entries, defender scrambles, and the
 * actual ground-raid effect (vanilla doGenericRaid: Recent Unrest + RECENTLY_RAIDED, no theft --
 * pirate flavor, unlike Omicron's bombardment; corrected 2026-10-06, BF-9).
 *
 * Emergent behaviors (no special code):
 *  - Raid fleets spawn from the anchor MARKETS, so they inherit the Mysterious Stockpiles
 *    quality bonus while any anchor lives, and lose it when both die.
 *  - No living anchor market = no source = raids stop. Composes with ExileBlackFleetLifecycle
 *    (Mourad relocation, temp-base spigot) without coordination.
 *
 * Config: data/config/exiles/director_config.json -> "exileblackfleet" (reloaded every game
 * launch via transient fields, so tuning edits land on restart). Quest override:
 * $exilebf_raids_enabled (Boolean memory flag) beats the config default.
 *
 * Third tier, the CONVOY AMBUSH (Pass 7 step 7f): acts on Midan's CONVOY_AMBUSH directive
 * (ExileMidanDirective, a sector-memory key -- this class never learns who wrote it) with one
 * small raider fleet per directive; see ExileBlackFleetAmbush. Same kill switch, ramp and anchors.
 *
 * Player colonies are code-exempt: systems containing player markets are never targeted
 * (vanilla PirateBaseIntel convention + this mod's Omicron convention — player-directed
 * pressure is reserved for future quest-controlled crisis content).
 */
public class BlackFleetRaidManager implements EveryFrameScript {

    public static final String BF_FACTION = "exileblackfleet";
    // Econ-JSON refactor 2026-07-08: anchors resolved via station entities (see lifecycle).
    public static final String SALLEE_STATION = "exilebf_sallee_station";
    public static final String LUNDY_STATION = "exilebf_lundy_station";
    public static final String CONFIG_PATH = "data/config/exiles/director_config.json";
    public static final String CONFIG_KEY = "exileblackfleet";
    public static final String RAIDS_ENABLED_KEY = "$exilebf_raids_enabled";
    /** Set on a target SYSTEM's memory at op launch (with expiry); pickTarget skips flagged
     *  systems. Anti-hotspot: no simultaneous ops on one system, no immediate re-raids. */
    public static final String SYSTEM_COOLDOWN_KEY = "$exilebf_op_cooldown";
    /** Victim-weight multiplier for markets carrying the extortion grudge flag (spec #6).
     *  Playtest-5 fix (2026-07-11): also applied at SYSTEM selection — the original
     *  victim-only weighting was inert, since a grudged market's system first had to win
     *  the plain proximity roll before the grudge ever got a vote. */
    public static final float GRUDGE_WEIGHT_MULT = 10f;
    /** Feasibility-gate relaxation for grudge targets (playtest-5): revenge is worth
     *  losses, so the defense threshold stretches by this factor at system AND victim
     *  selection. True fortress systems (e.g. Askonia) can still exceed even the relaxed
     *  gate — some grudges go unavenged, and expire (see extortion manager GRUDGE_DAYS). */
    public static final float GRUDGE_FEASIBILITY_RELAX = 2f;

    /** Assemble stage with an UNDISCLOSED source (playtest fix 2026-07-08: showing the source
     *  printed the anchor stations' locations on the raid intel — free recon the player should
     *  have to earn by actually finding the anchorages). Named static class (NOT anonymous) so
     *  XStream serialization stays clean; class name kept from the known-source version. */
    public static class KnownSourceAssembleStage extends PirateRaidAssembleStage {
        protected boolean enoughMadeIt(List routes, List stragglers) {
            return ExileBlackFleetLogistics.strength(routes, stragglers) >= abortFP;
        }
        public KnownSourceAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint) {
            super(raid, gatheringPoint, null);
        }

        @Override
        public boolean isSourceKnown() {
            return false;
        }
    }

    // --- Saved state ---
    protected long startTs = 0L;
    // No active-op lists: live ops are derived from the intel manager (see countActive).
    /** Pass 7 step 7f: the one live convoy ambush (no intel, so it is held here), or null. */
    protected ExileBlackFleetAmbush ambush = null;
    /** The last directive acted on (its whole string, serial included): one ambush per directive. */
    protected String lastAmbushDirective = null;

    // --- Transient config (reloaded each launch; tuning edits land on restart) ---
    // Two-tier design (ratified 2026-07-09): frequent light COMMERCE raids (shipping; Corsair
    // Activity condition) + rare heavy ASSAULTS (planets; sack + loot + Business is Good).
    protected transient boolean configLoaded = false;
    protected transient boolean enabledDefault = true;
    /** COMMERCE-tier feasibility (playtest-4 split, 2026-07-11): commerce raids hunt
     *  shipping and never fight ground battles, so they don't need to out-muscle a
     *  battlestation — looser gate + station strength excluded (see pickTarget). This is
     *  also the target-spread fix: the old shared 1.25x gate at early-ramp FP left only a
     *  handful of soft systems (Arcadia, Galatia) in the candidate pool. */
    protected transient float feasibilityMult = 0.95f; // STRENGTH units since BF-2
    /** ASSAULT-tier feasibility: assaults DO fight ground battles — full defense estimate
     *  (enemy strength + stations). STRENGTH units since BF-2 (was 1.25 x raw FP). */
    protected transient float assaultFeasibilityMult = 0.70f;
    /** Distance-weight falloff per LY (playtest-4: 0.25 made the eastern fringe dominate
     *  permanently; 0.10 keeps closer-is-likelier while letting deep-Core ops happen). */
    protected transient float distanceFalloff = 0.10f;
    /** Credits the RAIDED faction pays per frigate-equivalent corsair killed (Omicron
     *  bounty port, playtest-4); 0 disables. */
    protected transient float bountyPerFrigate = 3000f;
    protected transient List targetFactions = null; // String

    // Commerce-raid tier
    protected transient IntervalUtil interval = null;
    protected transient float intervalDays = 45f;
    protected transient int spawnFpMin = 150;
    protected transient int spawnFpMax = 250;
    protected transient int maxActive = 2;
    protected transient float startDelayDays = 90f;
    protected transient float abortFraction = 0.5f;
    protected transient float presenceDays = 45f; // days corsairs hunt shipping in-system
    protected transient int raidMaxShipSize = 2;    // destroyers (author ruling 2026-07-09)

    // Assault tier
    protected transient IntervalUtil assaultInterval = null;
    protected transient float assaultIntervalDays = 120f;
    protected transient int assaultFpMin = 350;
    protected transient int assaultFpMax = 500;
    protected transient int assaultMaxActive = 1;
    protected transient float assaultStartDelayDays = 180f;
    protected transient int assaultMaxShipSize = 3; // cruisers; set 4 in config when capitals should join

    // Time-ramp (ratified 2026-07-08, rise-from-ashes lore): FP multiplier climbs from
    // rampStartMult to 1.0 over rampDays. Mirrors the Omicron escalation-clock pattern.
    protected transient float rampDays = 365f;
    protected transient float rampStartMult = 0.6f;

    // Anti-hotspot cooldown, days a system stays untargetable after an op launches at it.
    protected transient float systemCooldownDays = 60f;

    // Convoy-ambush tier (Pass 7 step 7f): acts on Midan's CONVOY_AMBUSH directive.
    protected transient boolean ambushEnabled = true;
    protected transient int ambushFpMin = 40;
    protected transient int ambushFpMax = 60;
    protected transient int ambushMaxShipSize = 2;   // destroyer cap, like the raids (ruled 2026-10-19)
    protected transient float ambushHuntDays = 20f;
    protected transient float ambushLeaveDays = 15f;
    protected transient float ambushSpawnPreferredDistance = 0f; // 0 = the jump point nearest the convoy (2026-11-02)
    protected transient float ambushTankerFp = 3f;     // a Dram (Sean, 2026-11-04); 0 = no fuel ship
    protected transient float ambushTickDays = 0f;

    public boolean isDone() {
        return false;
    }

    public boolean runWhilePaused() {
        return false;
    }

    public void advance(float amount) {
        if (startTs == 0L) {
            startTs = Global.getSector().getClock().getTimestamp();
        }
        if (!configLoaded) {
            loadConfig();
        }
        float days = Global.getSector().getClock().convertToDays(amount);
        float elapsed = Global.getSector().getClock().getElapsedDaysSince(startTs);

        // Commerce-raid tier: frequent, light, hunts shipping.
        interval.advance(days);
        if (interval.intervalElapsed() && raidsEnabled() && elapsed >= startDelayDays) {
            if (countActive(false) < maxActive) {
                tryLaunch(false, spawnFpMin, spawnFpMax);
            }
        }

        // Assault tier: rare, heavy, sacks planets.
        assaultInterval.advance(days);
        if (assaultInterval.intervalElapsed() && raidsEnabled() && elapsed >= assaultStartDelayDays) {
            if (countActive(true) < assaultMaxActive) {
                tryLaunch(true, assaultFpMin, assaultFpMax);
            }
        }

        advanceAmbush(days);
    }

    // ------------------------------------------------------------------
    // Convoy-ambush tier (Pass 7 step 7f; war doc sec 6.2, ruled 2026-10-19 and 2026-11-01)
    // ------------------------------------------------------------------

    /**
     * Every quarter day: runs the live ambush, or else looks for a {@code CONVOY_AMBUSH} directive it
     * has not acted on whose convoy is now in its destination's system. One ambush at a time and one
     * per directive. The directive's author is unknown here: this reads a sector-memory key and
     * nothing else of the war director.
     *
     * <p>Quarter-day, not daily, since the 2026-11-02 ruling (Sean): the raiders follow the convoy
     * in through the jump point nearest it, so they must appear right BEHIND it -- a daily check let
     * it reach orbit first. The extra cost is one memory read per tick, plus one system's fleet list
     * only while a directive waits for its convoy.
     */
    protected void advanceAmbush(float days) {
        ambushTickDays += days;
        if (ambushTickDays < 0.25f) return;
        float step = ambushTickDays;
        ambushTickDays = 0f;

        if (ambush != null) {
            ambush.advance(step);
            if (ambush.isDone()) ambush = null;
            return;
        }

        if (!ambushEnabled || !raidsEnabled()) return;

        String directive = ExileMidanDirective.getActive();
        if (directive == null || directive.equals(lastAmbushDirective)) return;
        if (!ExileMidanDirective.TYPE_CONVOY_AMBUSH.equals(ExileMidanDirective.getType(directive))) return;
        MarketAPI target = Global.getSector().getEconomy().getMarket(
                ExileMidanDirective.getMarketId(directive));
        if (target == null || target.getContainingLocation() == null) return;

        CampaignFleetAPI convoy = findConvoy(target);
        if (convoy == null) return; // not here yet: check again tomorrow

        lastAmbushDirective = directive;
        MarketAPI source = pickSource();
        if (source == null) {
            Global.getLogger(BlackFleetRaidManager.class).info(
                    "Black Fleet AMBUSH NOT LAUNCHED: " + target.getName()
                            + " -- no source (both anchors dead or unusable)");
            return;
        }
        float fp = (ambushFpMin + new Random().nextFloat() * (float) (ambushFpMax - ambushFpMin))
                * getRampMult();
        ambush = ExileBlackFleetAmbush.launch(source, target, convoy, fp, ambushMaxShipSize,
                bountyPerFrigate, ambushHuntDays, ambushLeaveDays, ambushSpawnPreferredDistance,
                ambushTankerFp);
    }

    /** The live relief convoy bound for this market, if it is in the market's system. */
    protected CampaignFleetAPI findConvoy(MarketAPI target) {
        List fleets = target.getContainingLocation().getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI f = (CampaignFleetAPI) fleets.get(i);
            if (!f.isAlive() || f.isEmpty()) continue;
            if (!f.getMemoryWithoutUpdate().getBoolean(ExileMidanDirective.CONVOY_FLAG)) continue;
            if (target.getId().equals(f.getMemoryWithoutUpdate().getString(
                    ExileMidanDirective.RELIEF_DEST_KEY))) {
                return f;
            }
        }
        return null;
    }

    /** Roll a ramped budget, pick a source and a beatable target, launch the op. Budget rolls
     *  BEFORE target selection: the feasibility gate needs to know what this op can plausibly
     *  beat (playtest fix 2026-07-08: budget-blind picks suicided into defended systems). */
    protected void tryLaunch(boolean assault, int fpMin, int fpMax) {
        String tier = assault ? "assault" : "commerce raid";
        MarketAPI source = pickSource();
        if (source == null) {
            // BF-10a (bug sweep, 2026-10-06): failed attempts are now visible in the log.
            Global.getLogger(BlackFleetRaidManager.class).info(
                    "Black Fleet " + tier + " attempt: no source (both anchors dead or unusable)");
            return; // both anchors dead -> ops stop, emergently
        }

        float fp = fpMin + new Random().nextFloat() * (float) (fpMax - fpMin);
        fp *= getRampMult();

        StarSystemAPI target = pickTarget(source, fp, assault);
        if (target == null) {
            Global.getLogger(BlackFleetRaidManager.class).info(
                    "Black Fleet " + tier + " attempt: no eligible target (FP=" + (int) fp
                            + ") -- every system filtered (cooldown, player colony, or feasibility)");
            return;
        }

        launchOp(source, target, fp, assault);
    }

    /** BF-2: an op's FP in WarSimScript STRENGTH units (vanilla's conversion, which folds in the
     *  source market's fleet quality and doctrine). No source = raw FP. */
    protected float getOpStrength(MarketAPI source, float fp) {
        if (source == null || fp <= 0f) return fp;
        return Misc.getAdjustedStrength(fp, source);
    }

    /** Rise-from-ashes strength ramp: rampStartMult -> 1.0 over rampDays (clamped). */
    protected float getRampMult() {
        if (rampDays <= 0f) return 1f;
        float progress = Global.getSector().getClock().getElapsedDaysSince(startTs) / rampDays;
        if (progress < 0f) progress = 0f;
        if (progress > 1f) progress = 1f;
        return rampStartMult + progress * (1f - rampStartMult);
    }

    /** Config default, overridable at runtime by quests via the memory flag. */
    protected boolean raidsEnabled() {
        Object flag = Global.getSector().getMemoryWithoutUpdate().get(RAIDS_ENABLED_KEY);
        if (flag instanceof Boolean) {
            return ((Boolean) flag).booleanValue();
        }
        return enabledDefault;
    }

    /** Live ops of one tier, derived from the intel manager (the OmicronDirector pattern) rather
     *  than from a bookkeeping list this class maintains. Nothing to prune, nothing to keep in
     *  sync, and the count stays correct while raidsEnabled() is false — the old activeRaids /
     *  activeAssaults lists were pruned only inside the enable-gated branch, so a quest disabling
     *  raids would have retained ended intel and stalled the extortion outcome signal (review
     *  item 6, fixed 2026-07-29; the signal now self-posts in BlackFleetRaidIntel.notifyEnded). */
    protected int countActive(boolean assault) {
        int n = 0;
        List ops = Global.getSector().getIntelManager().getIntel(BlackFleetRaidIntel.class);
        for (int i = 0; i < ops.size(); i++) {
            BlackFleetRaidIntel op = (BlackFleetRaidIntel) ops.get(i);
            if (op.isEnded() || op.isEnding()) continue;
            if (op.isAssault() != assault) continue;
            n++;
        }
        return n;
    }

    /** A living anchor market, at random if both live (spread the raid origins). */
    protected MarketAPI pickSource() {
        List alive = new ArrayList();
        MarketAPI sallee = getAnchorMarket(SALLEE_STATION);
        MarketAPI lundy = getAnchorMarket(LUNDY_STATION);
        if (sallee != null && sallee.isInEconomy() && BF_FACTION.equals(sallee.getFactionId())) alive.add(sallee);
        if (lundy != null && lundy.isInEconomy() && BF_FACTION.equals(lundy.getFactionId())) alive.add(lundy);
        if (alive.isEmpty()) return null;
        return (MarketAPI) alive.get(new Random().nextInt(alive.size()));
    }

    /** The one market an Assault will sack: hostile target-faction markets in the system,
     *  individually beatable by this op's budget, weighted by size^2 (vanilla's own victim
     *  weighting). Battered markets (unrest deciv-guard, playtest-4) are skipped — repeat
     *  sacks were stacking Recent Unrest toward decivilization on vanilla worlds. Null if
     *  nothing qualifies. */
    protected MarketAPI pickVictim(StarSystemAPI system, float opStrength) {
        FactionAPI bf = Global.getSector().getFaction(BF_FACTION);
        if (bf == null) return null;

        float enemyStr = WarSimScript.getEnemyStrength(bf, system);
        WeightedRandomPicker picker = new WeightedRandomPicker();

        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m.isHidden() || m.isPlayerOwned()) continue;
            if (!targetFactions.contains(m.getFactionId())) continue;
            if (!m.getFaction().isHostileTo(bf)) continue;
            if (ExileUnrestGuard.isBattered(m)) continue;
            // Anti-repeat (ratified 2026-07-11): a world still scarred from the last sack
            // is off the menu — 60d, outlasting vanilla's 30d RECENTLY_RAIDED flag.
            if (data.scripts.marketconditions.ExileSacked.isSacked(m)) continue;
            // Extortion coordination (spec #5): the Black Fleet does not burn what pays it.
            if (data.scripts.marketconditions.ExilePriceSpikes.isSpiked(m)) continue;

            boolean grudged = m.getMemoryWithoutUpdate()
                    .getBoolean(ExileBlackFleetExtortionManager.GRUDGE_KEY);

            if (assaultFeasibilityMult > 0f) {
                float defense = enemyStr
                        + ExileStationStrength.get(m.getFaction(), system, m.getPrimaryEntity());
                // Grudge relaxation (playtest-5): revenge is worth losses. Without this, a
                // grudged market behind a real garrison was unreachable forever.
                float gate = opStrength * assaultFeasibilityMult
                        * (grudged ? GRUDGE_FEASIBILITY_RELAX : 1f);
                if (defense > gate) continue;
            }

            float weight = (float) (m.getSize() * m.getSize());
            // Silent grudge (spec #6): the player broke this market's racket — the fleet
            // remembers. Heavy weighting; the (relaxed) feasibility gate above still applies.
            if (grudged) {
                weight *= GRUDGE_WEIGHT_MULT;
            }
            picker.add(m, weight);
        }
        return (MarketAPI) picker.pick();
    }

    /** Bounty payer for a commerce raid: the biggest hostile market in the target system —
     *  the same market the vanilla raid-intel bullets display as "Target". Null disables the
     *  bounty for this op (no hostile market — shouldn't happen past pickTarget). */
    protected String pickPayingFactionId(StarSystemAPI system, FactionAPI bf) {
        int max = 0;
        String result = null;
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m.isHidden() || m.isPlayerOwned()) continue;
            if (!m.getFaction().isHostileTo(bf)) continue;
            if (m.getSize() > max) {
                max = m.getSize();
                result = m.getFactionId();
            }
        }
        return result;
    }

    /** Anchor market via its station entity (markets are econ-JSON-declared; ids are the loader's). */
    protected MarketAPI getAnchorMarket(String stationEntityId) {
        SectorEntityToken station = Global.getSector().getEntityById(stationEntityId);
        if (station == null) return null;
        return station.getMarket();
    }

    /** System containing a hostile target-faction market that this op can plausibly take on;
     *  closer = likelier (distanceFalloff per LY — flattened in playtest-4: 0.25 made the
     *  eastern fringe near the anchors dominate permanently). Systems containing ANY player
     *  market are excluded (code-exempt convention).
     *
     *  Feasibility is PER-TIER (playtest-4 target-spread fix): ASSAULTS fight ground battles,
     *  so their estimate is the full WarSimScript enemy strength + hostile stations at the
     *  tight assaultFeasibilityMult; COMMERCE raids just loiter and hunt shipping — stations
     *  sit in orbit and don't chase freighter-hunters, so stations are EXCLUDED and the mult
     *  is loose. Together these roughly double the commerce candidate pool at early-ramp FP.
     *
     *  Assaults also skip systems where every sackable market is battered (unrest
     *  deciv-guard) — pickVictim would find nothing and the launch would fizzle. */
    protected StarSystemAPI pickTarget(MarketAPI source, float fp, boolean assault) {
        FactionAPI bf = Global.getSector().getFaction(BF_FACTION);
        if (bf == null || targetFactions == null) return null;

        float mult = assault ? assaultFeasibilityMult : feasibilityMult;

        // UNITS (BF-2, 2026-10-06): both gates compare STRENGTH with strength, as the Heg-League
        // gates do (doc sec 14.14). WarSimScript defense is effective strength; the op's FP is
        // converted with Misc.getAdjustedStrength(fp, source). k = strength per FP, logged once
        // per pass (per source and budget); pickVictim() gets the same converted strength.
        float opStrength = getOpStrength(source, fp);
        if (source != null && fp > 0f) {
            float k = opStrength / fp;
            Global.getLogger(BlackFleetRaidManager.class).info(
                    "Exiles units [Black Fleet " + (assault ? "assault" : "commerce raid")
                            + "]: source=" + source.getName()
                            + " fp=" + (int) fp + " adjustedStrength=" + (int) opStrength
                            + " k=" + (Math.round(k * 100f) / 100f)
                            + " feasibilityMult=" + mult
                            + (mult > 0f ? " requiredOdds=" + (Math.round(1f / mult * 100f) / 100f)
                                    + ":1" : " (gate disabled)"));
        }

        // BF-12 (Sean, 2026-10-06): respect the shared live-op state like every other director.
        // A system already under a Heg-League, Ashen or Omicron op (or a blockade) is off the
        // menu -- dogpiles are the Maxios decivilization path. Own ops are excluded here because
        // the Black Fleet's own anti-hotspot cooldown already covers them.
        List busySystems = ExileLiveOps.getSystemsWithActiveRaids(BF_FACTION);

        WeightedRandomPicker picker = new WeightedRandomPicker();
        List seen = new ArrayList(); // StarSystemAPI, to avoid double-adding

        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m.isHidden()) continue;
            if (!targetFactions.contains(m.getFactionId())) continue;
            if (!m.getFaction().isHostileTo(bf)) continue;
            if (!(m.getContainingLocation() instanceof StarSystemAPI)) continue;

            StarSystemAPI system = (StarSystemAPI) m.getContainingLocation();
            if (seen.contains(system)) continue;
            seen.add(system);

            if (!Misc.getMarketsInLocation(system, Factions.PLAYER).isEmpty()) continue;

            // Anti-hotspot: skip systems with an active/recent Black Fleet op (flag set at
            // launch, expires after systemCooldownDays).
            if (system.getMemoryWithoutUpdate().getBoolean(SYSTEM_COOLDOWN_KEY)) continue;
            if (busySystems.contains(system)) continue; // BF-12

            List inSystem = Misc.getMarketsInLocation(system);

            // Grudge pull (playtest-5 fix, 2026-07-11): the original grudge weighting lived
            // only in pickVictim, so a grudged market's SYSTEM first had to win the plain
            // proximity roll — observed inert in playtest (3 assaults, 0 at grudge systems).
            // Now a system containing a grudged, still-eligible target-faction market pulls
            // BOTH tiers (assaults sack it; commerce raids strangle it — the revenge vector
            // that still works when the market hides behind a fortress).
            boolean grudgePull = false;
            for (int k = 0; k < inSystem.size(); k++) {
                MarketAPI sm = (MarketAPI) inSystem.get(k);
                if (sm.isHidden() || sm.isPlayerOwned()) continue;
                if (!targetFactions.contains(sm.getFactionId())) continue;
                if (!sm.getFaction().isHostileTo(bf)) continue;
                if (!sm.getMemoryWithoutUpdate()
                        .getBoolean(ExileBlackFleetExtortionManager.GRUDGE_KEY)) continue;
                if (assault && (ExileUnrestGuard.isBattered(sm)
                        || data.scripts.marketconditions.ExileSacked.isSacked(sm))) continue;
                grudgePull = true;
                break;
            }

            if (assault) {
                boolean anySackable = false;
                for (int k = 0; k < inSystem.size(); k++) {
                    MarketAPI sm = (MarketAPI) inSystem.get(k);
                    if (sm.isHidden() || sm.isPlayerOwned()) continue;
                    if (!targetFactions.contains(sm.getFactionId())) continue;
                    if (!sm.getFaction().isHostileTo(bf)) continue;
                    if (ExileUnrestGuard.isBattered(sm)) continue;
                    anySackable = true;
                    break;
                }
                if (!anySackable) continue;
            }

            if (mult > 0f) {
                float defense = WarSimScript.getEnemyStrength(bf, system);
                if (assault) {
                    for (int k = 0; k < inSystem.size(); k++) {
                        MarketAPI sm = (MarketAPI) inSystem.get(k);
                        if (!sm.getFaction().isHostileTo(bf)) continue;
                        defense += ExileStationStrength.get(sm.getFaction(), system, sm.getPrimaryEntity());
                    }
                }
                // Grudge relaxation: revenge is worth losses (a repelled revenge assault is
                // its own story — abortIfNeededBasedOnFP withdraws them below 50% FP).
                float gate = opStrength * mult * (grudgePull ? GRUDGE_FEASIBILITY_RELAX : 1f);
                if (defense > gate) continue; // op can't take it on; pick softer prey
            }

            float distLY = Misc.getDistanceLY(
                    source.getPrimaryEntity().getLocationInHyperspace(),
                    system.getLocation());
            float weight = 1f / (1f + distLY * distanceFalloff);
            if (grudgePull) {
                weight *= GRUDGE_WEIGHT_MULT;
            }
            picker.add(system, weight);
        }

        return (StarSystemAPI) picker.pick();
    }

    /** The vanilla pirate raid, verbatim structure (PirateBaseIntel.startRaid), with the
     *  action stage swapped per tier: commerce raids strangle trade, Assaults sack planets. */
    protected void launchOp(MarketAPI source, StarSystemAPI target, float fp, boolean assault) {
        FactionAPI bf = Global.getSector().getFaction(BF_FACTION);
        if (bf == null) return;

        // Assault victim picked BEFORE intel creation (playtest-4 reorder): its faction is
        // who pays the bounty, and a no-victim fizzle no longer burns the system cooldown.
        MarketAPI victim = null;
        if (assault) {
            victim = pickVictim(target, getOpStrength(source, fp));
            if (victim == null) {
                Global.getLogger(BlackFleetRaidManager.class).info(
                        "Black Fleet assault attempt: no beatable victim market in "
                                + target.getBaseName() + " (FP=" + (int) fp + ")");
                return;
            }
        }
        // Who pays for dead corsairs: the assault victim's faction, or (commerce — the whole
        // system's shipping is hunted) the biggest hostile market's, matching the "Target"
        // line the vanilla raid intel displays.
        String payingFactionId = victim != null
                ? victim.getFactionId() : pickPayingFactionId(target, bf);

        RaidIntel raid = new BlackFleetRaidIntel(target, bf, assault,
                assault ? assaultMaxShipSize : raidMaxShipSize,
                payingFactionId, bountyPerFrigate);

        // Anti-hotspot (playtest fix 2026-07-09: three concurrent ops piled onto Arcadia):
        // flag the target system at launch; pickTarget skips flagged systems until it expires.
        target.getMemoryWithoutUpdate().set(SYSTEM_COOLDOWN_KEY, true, systemCooldownDays);

        // Gather at the jump point nearest the anchor.
        JumpPointAPI gather = null;
        float min = Float.MAX_VALUE;
        List points = source.getContainingLocation().getEntities(JumpPointAPI.class);
        for (int i = 0; i < points.size(); i++) {
            JumpPointAPI curr = (JumpPointAPI) points.get(i);
            float dist = Misc.getDistance(source.getPrimaryEntity().getLocation(), curr.getLocation());
            if (dist < min) {
                min = dist;
                gather = curr;
            }
        }

        SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(bf, target.getCenter());
        if (gather == null || raidJump == null) return;

        KnownSourceAssembleStage assemble = new KnownSourceAssembleStage(raid, gather);
        assemble.addSource(source);
        assemble.setSpawnFP(fp);
        assemble.setAbortFP(fp * abortFraction);
        raid.addStage(assemble);

        TravelStage travel = new ExileBlackFleetLogistics.Travel(raid, gather, raidJump);
        travel.setAbortFP(fp * abortFraction);
        raid.addStage(travel);

        PirateRaidActionStage action;
        if (assault) {
            // Single-victim rework (playtest-3): one Assault = one designated market
            // (victim picked up top, before intel creation).
            String sourceStationId = source.getPrimaryEntity() != null
                    ? source.getPrimaryEntity().getId() : null;
            action = new BlackFleetAssaultStage(raid, target, sourceStationId, victim.getId());
        } else {
            action = new BlackFleetCommerceRaidStage(raid, target, presenceDays);
        }
        action.setAbortFP(fp * abortFraction);
        raid.addStage(action);

        raid.addStage(assault ? new ExileBlackFleetReturnStage((BlackFleetRaidIntel) raid) : new ReturnStage(raid));

        // Always a quiet add: ops launch HIDDEN (v0.8 polish — no post until the raid
        // begins); BlackFleetRaidIntel reveals itself on arrival or player battle contact.
        Global.getSector().getIntelManager().addIntel(raid, true);

        Global.getLogger(BlackFleetRaidManager.class).info(
                "Black Fleet " + (assault ? "ASSAULT" : "commerce raid") + " launched: "
                        + source.getName() + " -> " + target.getNameWithLowercaseType()
                        + ", FP=" + (int) fp + " (ramp x" + getRampMult() + ")");
    }

    protected void loadConfig() {
        configLoaded = true;
        targetFactions = new ArrayList();
        targetFactions.add("hegemony");
        targetFactions.add("persean"); // vanilla faction id; "persean_league" is only the file name
        targetFactions.add("tritachyon");
        targetFactions.add("sindrian_diktat");
        targetFactions.add("luddic_church");
        targetFactions.add("independent");

        try {
            JSONObject root = Global.getSettings().loadJSON(CONFIG_PATH);
            JSONObject f = root.optJSONObject(CONFIG_KEY);
            if (f != null) {
                JSONObject raid = f.optJSONObject("raid");
                if (raid != null) {
                    intervalDays = (float) raid.optDouble("intervalDays", intervalDays);
                    spawnFpMin = raid.optInt("spawnFpMin", spawnFpMin);
                    spawnFpMax = raid.optInt("spawnFpMax", spawnFpMax);
                    maxActive = raid.optInt("maxActive", maxActive);
                    startDelayDays = (float) raid.optDouble("startDelayDays", startDelayDays);
                    abortFraction = (float) raid.optDouble("abortFraction", abortFraction);
                    enabledDefault = raid.optBoolean("enabled", enabledDefault);
                    feasibilityMult = (float) raid.optDouble("feasibilityMult", feasibilityMult);
                    presenceDays = (float) raid.optDouble("presenceDays", presenceDays);
                    raidMaxShipSize = raid.optInt("maxShipSize", raidMaxShipSize);
                }
                JSONObject assault = f.optJSONObject("assault");
                if (assault != null) {
                    assaultIntervalDays = (float) assault.optDouble("intervalDays", assaultIntervalDays);
                    assaultFpMin = assault.optInt("spawnFpMin", assaultFpMin);
                    assaultFpMax = assault.optInt("spawnFpMax", assaultFpMax);
                    assaultMaxActive = assault.optInt("maxActive", assaultMaxActive);
                    assaultStartDelayDays = (float) assault.optDouble("startDelayDays", assaultStartDelayDays);
                    assaultMaxShipSize = assault.optInt("maxShipSize", assaultMaxShipSize);
                    assaultFeasibilityMult = (float) assault.optDouble("feasibilityMult", assaultFeasibilityMult);
                }
                distanceFalloff = (float) f.optDouble("distanceFalloff", distanceFalloff);
                bountyPerFrigate = (float) f.optDouble("bountyPerFrigate", bountyPerFrigate);
                JSONObject ramp = f.optJSONObject("ramp");
                if (ramp != null) {
                    rampDays = (float) ramp.optDouble("rampDays", rampDays);
                    rampStartMult = (float) ramp.optDouble("startMult", rampStartMult);
                }
                systemCooldownDays = (float) f.optDouble("systemCooldownDays", systemCooldownDays);
                JSONObject ambushCfg = f.optJSONObject("ambush");
                if (ambushCfg != null) {
                    ambushEnabled = ambushCfg.optBoolean("enabled", ambushEnabled);
                    ambushFpMin = ambushCfg.optInt("fpMin", ambushFpMin);
                    ambushFpMax = Math.max(ambushFpMin, ambushCfg.optInt("fpMax", ambushFpMax));
                    ambushMaxShipSize = ambushCfg.optInt("maxShipSize", ambushMaxShipSize);
                    ambushHuntDays = (float) ambushCfg.optDouble("huntDays", ambushHuntDays);
                    ambushLeaveDays = (float) ambushCfg.optDouble("leaveDays", ambushLeaveDays);
                    ambushSpawnPreferredDistance = (float) ambushCfg.optDouble(
                            "spawnPreferredDistance", ambushSpawnPreferredDistance);
                    ambushTankerFp = (float) ambushCfg.optDouble("tankerFp", ambushTankerFp);
                }
                JSONArray targets = f.optJSONArray("targetFactions");
                if (targets != null && targets.length() > 0) {
                    targetFactions = new ArrayList();
                    for (int i = 0; i < targets.length(); i++) {
                        targetFactions.add(targets.getString(i));
                    }
                }
            }
        } catch (Exception e) {
            Global.getLogger(BlackFleetRaidManager.class).warn(
                    "Black Fleet raid config load failed; using code defaults", e);
        }

        // BUG FIX 2026-07-08: the configured cadence is the actual launch cadence (a hardcoded
        // ~10-day check used to override it).
        interval = new IntervalUtil(intervalDays * 0.75f, intervalDays * 1.25f);
        assaultInterval = new IntervalUtil(assaultIntervalDays * 0.75f, assaultIntervalDays * 1.25f);
    }
}
