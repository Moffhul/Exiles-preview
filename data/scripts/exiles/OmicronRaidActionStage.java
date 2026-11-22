package data.scripts.exiles;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.raid.PirateRaidActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Action stage for Omicron operations. Differs from the pirate version in two ways:
 * <ul>
 *   <li><b>Fixed objective budgets:</b> ops are SENT only at the Director's target factions.
 *       Their market objectives are chosen once at creation and persist for the life of the op.
 *       A positive legacy {@code collateralChance} setting lets hostile bystander colonies
 *       enter that draw; zero enforces strict target-faction discipline. Config-exempt factions
 *       (refugees) are never hit; player colonies are
 *       code-exempt — player-directed ops are reserved for a future quest-controlled crisis
 *       mechanism. Enforced in BOTH {@link #getTargets()} (which colonies fleets are sent at)
 *       and {@link #canRaid} (the check the raid AI makes before an opportunistic ground
 *       raid — vanilla accepts any hostile market there, so without that override the
 *       discipline never bound opportunistic raids at all).</li>
 *   <li><b>The raid effect is Omicron's colony disruption</b> (temporarily knock industries
 *       offline; never population, deciv-safe caps — verified: temporary setDisrupted cannot
 *       decivilize) instead of the pirate commodity raid. Applied via {@code performRaid}.
 *       ON SCREEN, the vanilla RaidAssignmentAI invokes it only for fleets that SURVIVE to reach
 *       the colony (the survival gate). OFF SCREEN, vanilla {@code autoresolve()} calls
 *       {@code performRaid(null, target)} on a strength comparison instead, so there it is a
 *       strength gate, not a survival gate (corrected 2026-10-06, bug sweep OMI-4).</li>
 * </ul>
 * Defender scrambles (MilitaryResponseScript) and the outmatched/autoresolve logic are
 * inherited from the vanilla stage unchanged.
 */
public class OmicronRaidActionStage extends PirateRaidActionStage {

    protected ArrayList targetFactionIds; // String; null only during the superclass constructor
    protected float collateralChance;
    protected ArrayList collateralExemptFactions; // String
    protected String primaryTargetId;
    protected int objectiveBudget;
    protected ArrayList objectiveMarketIds;       // String; rolled once and saved with the op
    protected int shortMinDays;
    protected int shortMaxDays;
    protected int longMinDays;
    protected int longMaxDays;
    protected float spaceportChance;
    protected float farmingChance;
    protected float otherChance;

    /** TUNE: recent-unrest stability points per bombardment (vanilla decay ~90d per point). */
    public static final int BOMBARDMENT_UNREST = 2;

    public OmicronRaidActionStage(RaidIntel raid, StarSystemAPI system, List targetFactionIds,
                                  OmicronDirectorConfig config, String primaryTargetId,
                                  int objectiveBudget) {
        super(raid, system);
        this.targetFactionIds = new ArrayList(targetFactionIds);
        this.collateralChance = config.collateralChance;
        this.collateralExemptFactions = new ArrayList(config.collateralExemptFactions);
        this.primaryTargetId = primaryTargetId;
        this.objectiveBudget = objectiveBudget < 1 ? 1 : objectiveBudget;
        this.objectiveMarketIds = chooseObjectives();
        // The super constructor computed this from the unfiltered vanilla pool (our fields
        // were null); redo it against the real target set. Always false while player
        // colonies are collateral-exempt, but written to survive that exemption changing.
        playerTargeted = false;
        List real = getTargets();
        for (int i = 0; i < real.size(); i++) {
            if (((MarketAPI) real.get(i)).isPlayerOwned()) {
                playerTargeted = true;
                break;
            }
        }
        this.shortMinDays = config.disruptShortMinDays;
        this.shortMaxDays = config.disruptShortMaxDays;
        this.longMinDays = config.disruptLongMinDays;
        this.longMaxDays = config.disruptLongMaxDays;
        this.spaceportChance = config.disruptSpaceportChance;
        this.farmingChance = config.disruptFarmingChance;
        this.otherChance = config.disruptOtherChance;
    }

    /** Restrict the action stage to its persisted objective list. (Called from the
     *  super constructor once, before our fields exist — fall back to vanilla behavior for
     *  that single call.) */
    @Override
    protected List getTargets() {
        List all = super.getTargets();
        if (targetFactionIds == null) return all;
        List result = new ArrayList();
        for (int i = 0; i < all.size(); i++) {
            MarketAPI m = (MarketAPI) all.get(i);
            // Old in-flight operations deserialize without an objective list. Keep those
            // safely restricted to their configured target factions; new operations use the
            // fixed persisted budget below.
            if (objectiveMarketIds == null ? (!m.isPlayerOwned()
                    && !ExileProtectedFactions.isProtected(m) && isPrimaryFactionTarget(m))
                    : objectiveMarketIds.contains(m.getId())) result.add(m);
        }
        return result;
    }

    private ArrayList chooseObjectives() {
        ArrayList chosen = new ArrayList();
        ArrayList candidates = new ArrayList();
        List all = super.getTargets();
        MarketAPI primary = null;
        for (int i = 0; i < all.size(); i++) {
            MarketAPI market = (MarketAPI) all.get(i);
            if (market == null || market.isPlayerOwned()) continue;
            if (primaryTargetId != null && primaryTargetId.equals(market.getId())) {
                primary = market;
                continue;
            }
            if (isPrimaryFactionTarget(market) || isCollateralEligible(market)) {
                candidates.add(market);
            }
        }
        if (primary == null) {
            for (int i = 0; i < candidates.size(); i++) {
                MarketAPI market = (MarketAPI) candidates.get(i);
                if (isPrimaryFactionTarget(market)) {
                    primary = market;
                    candidates.remove(i);
                    break;
                }
            }
        }
        if (primary != null) chosen.add(primary.getId());
        Random picker = new Random();
        while (chosen.size() < objectiveBudget && !candidates.isEmpty()) {
            int index = picker.nextInt(candidates.size());
            MarketAPI market = (MarketAPI) candidates.remove(index);
            chosen.add(market.getId());
        }
        return chosen;
    }

    /** Whether updateRoutes committed the op to at least one colony. False after the stage
     *  starts means every candidate's defenses exceed the raid's strength — the op is about
     *  to abort without acting, and announcing it would just be announce-then-instant-fail
     *  noise (playtest 2026-07-04, Eos Exodus). The intel reads this before revealing. */
    public boolean hasChosenTargets() {
        return targets != null && !targets.isEmpty();
    }

    protected boolean isPrimaryFactionTarget(MarketAPI market) {
        return targetFactionIds != null && targetFactionIds.contains(market.getFactionId());
    }

    /** Collateral rule: with the legacy switch enabled, hostile bystander colonies may enter
     *  the one-time objective-budget draw. Player and canon/config protected markets never do. */
    protected boolean isCollateralEligible(MarketAPI market) {
        if (collateralChance <= 0f) return false;
        if (market.isPlayerOwned()) return false; // code-exempt: see class javadoc
        if (ExileProtectedFactions.isProtected(market)) return false; // never a direct target (canon)
        if (collateralExemptFactions != null
                && collateralExemptFactions.contains(market.getFactionId())) return false;
        FactionAPI omicron = Global.getSector().getFaction(OmicronDirector.OMICRON_FACTION);
        if (omicron == null || market.getFaction() == null
                || !omicron.isHostileTo(market.getFaction())) return false;
        return true;
    }

    /** The check RaidAssignmentAI consults before a fleet ground-raids a nearby market.
     *  Vanilla accepts ANY hostile market here, independent of getTargets — without this
     *  override, target discipline never applied to opportunistic raids on colonies close
     *  to a fleet's path. */
    @Override
    public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (!super.canRaid(fleet, market)) return false;
        if (objectiveMarketIds == null) return !market.isPlayerOwned()
                && !ExileProtectedFactions.isProtected(market)
                && isPrimaryFactionTarget(market);
        return objectiveMarketIds.contains(market.getId());
    }

    /** The Omicron raid effect: temporary industry disruption, survival-gated by the caller. */
    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        int disrupted = disruptColony(market);

        ExileUnrestGuard.reportExilesPressure(market,
                ExileEmergencyMeasuresConfig.pressureDays(0f, BOMBARDMENT_UNREST));

        // Recent unrest from the bombardment (author request 2026-07-09): population centers
        // don't take orbital fire calmly. Vanilla decaying-stability mechanism (~90d/point).
        // UNREST STACKS UNCAPPED across repeat attacks (playtest-4: Gilead driven to a
        // year-deep stability hole by Omicron + Black Fleet both) — battered markets still
        // get bombarded (disruption is temporary, deciv-safe) but take no FURTHER unrest,
        // unless the player disables the guard in settings.json (ExileUnrestGuard).
        if (!ExileUnrestGuard.isBattered(market)) {
            com.fs.starfarer.api.impl.campaign.econ.RecentUnrest.get(market)
                    .add(BOMBARDMENT_UNREST, "Omicron bombardment");
        } else {
            Global.getLogger(OmicronRaidActionStage.class).info(
                    "Unrest deciv-guard: skipped bombardment unrest at " + market.getName());
        }

        // The vanilla bombardment surface-explosion overlay (same one tactical AND saturation
        // use — there's only one at the map level). Self-guarding: no-op unless the player is
        // in-system, self-terminates after a few seconds.
        MarketCMD.addBombardVisual(market.getPrimaryEntity());

        // Record actual damage (not mere raiding) on the intel for its end-state summary.
        if (disrupted > 0 && intel instanceof OmicronRaidIntel) {
            ((OmicronRaidIntel) intel).addDamagedColony(market.getName());
        }

        // The success-detection flag the stage machinery reads (vanilla sets it in doGenericRaid).
        Misc.setFlagWithReason(market.getMemoryWithoutUpdate(), MemFlags.RECENTLY_RAIDED,
                intel.getFaction().getId(), true, 30f);

        Global.getSector().getCampaignUI().addMessage(
                "Omicron forces have bombarded " + market.getName()
                        + (disrupted > 0 ? ", knocking " + disrupted + " industries offline." : "."),
                Color.RED);
        Global.getLogger(OmicronRaidActionStage.class).info(
                "Omicron performRaid: " + market.getName() + " (" + market.getFactionId()
                        + "), disrupted=" + disrupted + ", byFleet=" + (fleet != null));
    }

    /** Temporarily knock industries offline. Never population; deciv-safe short caps. */
    protected int disruptColony(MarketAPI market) {
        if (market == null || !market.isInEconomy()) return 0;
        Random random = new Random();
        List industries = new ArrayList(market.getIndustries());
        int disrupted = 0;
        for (int i = 0; i < industries.size(); i++) {
            Industry ind = (Industry) industries.get(i);
            if (ind == null) continue;
            String id = ind.getId();
            if (Industries.POPULATION.equals(id)) continue; // never touch population

            float dur;
            if (isMilitaryIndustry(ind)) {
                dur = rollDays(random, shortMinDays, shortMaxDays); // always hit defenses
            } else if (isFarmingIndustry(ind)) {
                if (random.nextFloat() >= farmingChance) continue;
                dur = rollDays(random, shortMinDays, shortMaxDays); // <=30d: keep food away from deciv
            } else if (isSpaceportIndustry(ind)) {
                if (random.nextFloat() >= spaceportChance) continue;
                dur = rollDays(random, shortMinDays, shortMaxDays);
            } else {
                if (random.nextFloat() >= otherChance) continue;
                dur = rollDays(random, longMinDays, longMaxDays);   // 30-60d
            }
            // Max semantics (fixed 2026-10-10): the one-argument form OVERWRITES, so a short Omicron
            // roll could shorten a longer disruption already running. Every other director keeps
            // the longer of the two; so does this one now.
            ind.setDisrupted(dur, true);
            disrupted++;
        }
        return disrupted;
    }

    private static float rollDays(Random random, int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    // Category checks are TAG-based (with the vanilla ids as belt-and-suspenders) so CUSTOM
    // industries classify correctly — playtest 2026-07-05: the id-only checks dropped the
    // Knights of Ludd garrison (a military installation) into the 50% "other" bucket, so it
    // survived bombardments that leveled the vanilla defenses next door. Tag conventions per
    // industries.csv: military/command/patrol/grounddefenses, farming, spaceport.

    private static boolean isMilitaryIndustry(Industry ind) {
        String id = ind.getId();
        if (Industries.GROUNDDEFENSES.equals(id) || Industries.HEAVYBATTERIES.equals(id)
                || Industries.PATROLHQ.equals(id) || Industries.MILITARYBASE.equals(id)
                || Industries.HIGHCOMMAND.equals(id)) return true;
        return ind.getSpec().hasTag("military") || ind.getSpec().hasTag("command")
                || ind.getSpec().hasTag("patrol") || ind.getSpec().hasTag("grounddefenses");
    }

    private static boolean isFarmingIndustry(Industry ind) {
        String id = ind.getId();
        if (Industries.FARMING.equals(id) || Industries.AQUACULTURE.equals(id)) return true;
        return ind.getSpec().hasTag("farming");
    }

    private static boolean isSpaceportIndustry(Industry ind) {
        String id = ind.getId();
        if (Industries.SPACEPORT.equals(id) || Industries.MEGAPORT.equals(id)) return true;
        return ind.getSpec().hasTag("spaceport");
    }

    /** Vanilla text with the raid/bombard reword (author 2026-07-05: Omicron are spaceships,
     *  they don't land and grab things — "raided" stays only in the OP name "Omicron Raid").
     *  Body is otherwise a faithful copy of PirateRaidActionStage.showStageInfo, raw-List
     *  Janino style; the ongoing/failure branches keep vanilla phrasing (they describe the
     *  operation, not colony damage). */
    @Override
    public void showStageInfo(TooltipMakerAPI info) {
        int curr = intel.getCurrentStage();
        int index = intel.getStageIndex(this);
        float opad = 10f;

        if (status == RaidIntel.RaidStageStatus.FAILURE) {
            info.addPara("The raiding forces have been defeated by the defenders of the "
                    + intel.getSystem().getNameWithLowercaseType() + ". The raid is now over.", opad);
        } else if (status == RaidIntel.RaidStageStatus.SUCCESS) {
            List raided = new ArrayList();
            for (int i = 0; i < targets.size(); i++) {
                MarketAPI market = (MarketAPI) targets.get(i);
                if (!market.getFaction().isHostileTo(intel.getFaction())) continue;
                if (Misc.flagHasReason(market.getMemoryWithoutUpdate(),
                        MemFlags.RECENTLY_RAIDED, intel.getFaction().getId())) {
                    raided.add(market);
                }
            }
            if (!raided.isEmpty()) {
                info.addPara("The Omicron forces have successfully bombarded the following colonies:", opad);
                float initPad = opad;
                for (int i = 0; i < raided.size(); i++) {
                    BaseIntelPlugin.addMarketToList(info, (MarketAPI) raided.get(i), initPad,
                            Misc.getTextColor());
                    initPad = 0f;
                }
            }
        } else if (curr == index) {
            info.addPara("The raiding forces are currently operating in the "
                    + intel.getSystem().getNameWithLowercaseType() + ".", opad);
        }
    }

    // --- Machine flavor for fleet action text ---

    @Override
    public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
        return "dismantling infrastructure on " + market.getName();
    }

    @Override
    public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
        return "descending on " + market.getName();
    }
}
