package data.scripts.exiles;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;

/**
 * PHASE 4 -- THE CULANN STRIKE (docs/EXILES_ASHEN_CRUSADE_WAR.md §7, Pass 7).
 *
 * <p>The operation the whole war clock has been counting down to. Everything the player watched in
 * two Phase-3 rehearsals, at full scale and against the real objective: crack Culann, wreck
 * Tri-Tachyon's industrial heart, and take the Corrupted Nanoforge home to Elpis.
 *
 * <p><b>Extends the rehearsal deliberately.</b> §6.4's fiction is that Phase 3 <i>is</i> a
 * rehearsal for this -- so the targeting, the single-target-once machinery, the sticky-success fix
 * and the outcome-reporting seam are all inherited rather than re-typed. What differs is the
 * outcome, the naming, and the absence of a feasibility gate.
 *
 * <p><b>NO {@code canBeat()} GATE (Sean, 2026-08-13).</b> The rehearsal's tactical check is
 * overridden away entirely. With it, an under-specced strike would mill around Culann and time out
 * -- the once-per-campaign climax as a silent non-event, which is the worst outcome available here.
 * Without it, an under-specced strike is <i>repelled</i>, which is a designed result with a
 * preparation floor, a retry window and good fiction behind it. <b>Caveat (bug sweep ASH-6,
 * 2026-10-06):</b> "repelled" happens only where the fleets are spawned, i.e. near the player.
 * Off-screen, abstract routes take no losses and the window-end autoresolve calls this
 * always-true {@code canBeat()}, so an unwatched strike always lands -- consistent with the
 * design target ("wins comfortably if the player does not intervene"). Verified safe: vanilla
 * {@code PirateRaidActionStage.canRaid()} carries no strength check of its own, only a
 * recently-raided flag and hostility, so removing ours leaves no hidden gate underneath.
 *
 * <p><b>Bombardment is VISUAL ONLY</b> (ruling B, §7.4): {@code addBombardVisual}, never
 * {@code doBombardment}. No market-size loss, no pollution. Saturation bombing left this director
 * and became end-of-quest-chain content.
 */
public class AshenCulannStrikeStage extends AshenRehearsalStage {

    /** Per-industry offline durations, §3.2a ruling 3. Pushed in from config by the launcher. */
    public static int heavyBatteriesDays = 30;
    public static int spaceportDays = 60;
    public static int refiningDays = 180;
    public static int orbitalWorksDays = 360;
    public static int militaryBaseDays = 360;

    /** Elpis -- the Providence station that receives the forge. Resolved via ENTITY, never by a
     *  hardcoded market id (CLAUDE.md §7: the econ loader assigns its own market ids). */
    public static final String ELPIS_ENTITY_ID = "exileashen_command_bastion";
    public static final String ASHEN_ORBITAL_WORKS = "exile_ashen_orbitalworks";

    public AshenCulannStrikeStage(RaidIntel raid, StarSystemAPI target, String targetMarketId,
                                  int unrestPoints, float maxDays) {
        super(raid, target, targetMarketId, 0, unrestPoints, maxDays);
    }

    /**
     * Inherits the rehearsal's {@code updateRoutes()} arrival fix (2026-09-19, docs sec 14.11),
     * which matters MORE here than for a rehearsal: this stage's {@link #canBeat} is
     * unconditionally true because the strike is designed to have no gate, yet vanilla's own
     * arrival strength check ran underneath it and could clear the target and zero the window --
     * repelling the climactic strike on the tick it arrived. The restored window keeps the armada
     * on station; only the log label is changed here.
     */
    @Override
    protected String arrivalLogLabel() {
        return "Ashen Culann strike";
    }

    /** Fired once, when the armadas are actually in Hybrasil rather than when they launched. */
    protected boolean arrivalAnnounced = false;

    /**
     * Announce the capitals, and measure the fight we are about to have.
     *
     * <p><b>Why arrival and not launch.</b> This is the beat Sean cares most about, and at launch
     * the armadas are still an assemble and a transit away -- an announcement then would be a
     * warning, not an entrance. Arrival is also the only moment the message is guaranteed to line
     * up with what the player can see, since launch order is precise but arrival order is blurred
     * by assemble and travel jitter (the rehearsal trace measured fleets trickling in 4 -> 7 -> 9).
     *
     * <p><b>The strength line is MEASUREMENT ONLY and must never become a gate.</b> The whole
     * reason the assault FP is 800 rather than §7.1's 500-700 is that §7.1 was set before Hybrasil
     * could be measured -- and 800 is itself an extrapolation of a ratio observed at a fifth of
     * this scale. This prints the real numbers on the first live strike so the band gets tuned from
     * data instead of from a fourth round of reasoning. It is deliberately NOT the temporary
     * scaffolding the rehearsal carried: one line, once per campaign, at the moment it matters.
     */
    protected void announceArrival() {
        if (arrivalAnnounced || system == null) return;

        FactionAPI ashen = intel.getFaction();

        // THIS OP's fleets, not any Ashen fleet in the system (corrected 2026-08-16). With all
        // three waves now launching together, "any Ashen fleet" would let a sweep destroyer fire
        // an announcement about capital ships -- the sweep and the blockade arrive alongside the
        // armadas rather than days ahead of them. getRoutes() is the same accessor nameFleets()
        // uses, and routes spawn their fleets lazily, so this simply stays false until one is up.
        boolean armadaHere = false;
        List routeList = getRoutes();
        if (routeList != null) {
            for (int i = 0; i < routeList.size(); i++) {
                RouteData route = (RouteData) routeList.get(i);
                CampaignFleetAPI fleet = route.getActiveFleet();
                if (fleet != null && fleet.getContainingLocation() == system) {
                    armadaHere = true;
                    break;
                }
            }
        }
        if (!armadaHere) return;

        // Total Ashen presence is still worth measuring -- it is what canBeat() would have seen,
        // and the count is the fastest way to tell whether the waves actually concentrated.
        int ashenFleets = 0;
        List fleets = system.getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            if (fleet == null) continue;
            if (fleet.getFaction() == ashen) ashenFleets++;
        }

        arrivalAnnounced = true;

        Global.getSector().getCampaignUI().addMessage(
                "Ashen Crusade capital ships have entered the Hybrasil system.", Color.ORANGE);

        MarketAPI target = targetMarketId == null
                ? null : Global.getSector().getEconomy().getMarket(targetMarketId);
        float str = WarSimScript.getFactionStrength(ashen, system);
        float enemy = WarSimScript.getEnemyStrength(ashen, system);
        float station = target == null ? 0f : ExileStationStrength.get(
                target.getFaction(), system, target.getPrimaryEntity());
        Global.getLogger(AshenCulannStrikeStage.class).info(
                "CULANN STRIKE ARRIVAL: crusade=" + (int) str
                        + " vs defense=" + (int) (enemy + station)
                        + " (enemy=" + (int) enemy + " + station=" + (int) station + ")"
                        + " [ashenFleetsInSystem=" + ashenFleets + "]"
                        + " -- MEASUREMENT ONLY, no gate. Tune assaultFp from this.");
    }

    @Override
    protected void updateStatus() {
        if (status == RaidStageStatus.ONGOING) announceArrival();
        super.updateStatus();
    }

    // STATION SCOPE (bug sweep ASH-5, ruled by Sean 2026-10-06): the armadas came for Culann.
    // The inherited suppressNonTargetStations() keeps them off every OTHER market station in
    // Hybrasil (Eochu Bres' star fortress, the independent and pirate stations), which no gate
    // counts and which are not the objective. Culann's own station stays fair game.

    /**
     * No feasibility gate. The Crusade has prepared for years; it commits.
     *
     * <p>This is the same reasoning Sean applied to rehearsal <i>launch</i> in 2026-08-09 -- fixed
     * target, deliberate operation -- taken one step further, because Phase 4 is more deliberate
     * than a rehearsal rather than less.
     */
    @Override
    protected boolean canBeat(MarketAPI market) {
        return true;
    }

    /**
     * The engine names these, not us.
     *
     * <p>The two assault fleets are meant to read as <b>"Ashen Crusade Armada"</b>, composed by the
     * engine from Ashen's {@code fleetTypeNames}. That requires {@code HiddenSourceAssembleStage}'s
     * {@code allowLargeType} to be on -- the Armada suppression added for 45 FP interdiction
     * raiders would otherwise have this fleet arrive as a "Fury". The screening armada is a
     * separate operation and carries its own explicit name.
     */
    @Override
    protected void nameFleets() {
        // Intentionally empty -- see javadoc.
    }

    /**
     * Feed the Phase-4 branch rather than the preparation meter.
     *
     * <p>Runs from {@code AshenRaidIntel.notifyEnded()}, the seam every path reaches -- the lesson
     * from 2026-08-09, where reporting from {@code finish()} silently dropped two outcomes in
     * three. A repelled strike must report, because the floor reset is what paces the retry.
     */
    @Override
    public void reportOutcome(AshenWarDirector director) {
        if (hasLanded()) {
            director.notifyPhase4Succeeded();
        } else {
            director.notifyPhase4Failed();
        }
    }

    @Override
    public String getOutcomeSummary() {
        return (targetMarketId == null ? "?" : targetMarketId)
                + ": CULANN STRIKE " + (rehearsalDone ? "SUCCEEDED" : "REPELLED")
                + " after " + (int) elapsed + "d of " + (int) maxDays + "d";
    }

    /**
     * The strike itself: five industries down on differentiated timers, unrest across Hybrasil,
     * the bombardment animation, and the forge.
     */
    @Override
    public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
        if (rehearsalDone) return;
        if (targetMarketId == null || !targetMarketId.equals(market.getId())) return;

        rehearsalDone = true;

        // 1. Wreck the industry. §3.2a ruling 3: differentiated durations, and Heavy Batteries
        //    recovers FASTEST of the five on purpose -- "keep Culann wounded not dying", so it does
        //    not spend a year as a free target for the Black Fleet, pirates and the player. Net
        //    shape: hard shell back within a month, reach gone for a year.
        //    The orbital battlestation is deliberately NOT in this list: a station beaten in combat
        //    is already offline 30-60 days by vanilla's own rules (Sean, 2026-08-13), so the effect
        //    happens for free and stays consistent with every other station in the game.
        disruptFor(market, Industries.HEAVYBATTERIES, heavyBatteriesDays);
        disruptFor(market, spaceportIdOf(market), spaceportDays);
        disruptFor(market, Industries.REFINING, refiningDays);
        disruptFor(market, Industries.ORBITALWORKS, orbitalWorksDays);
        disruptFor(market, Industries.MILITARYBASE, militaryBaseDays);

        // 2. Unrest on BOTH Hybrasil markets (§3.2a, ratified 2026-08-09) -- cracking Tri-Tachyon's
        //    industrial heart shakes the whole system. Guard-routed, which matters more here than
        //    anywhere else in the spec: Hybrasil is already double-teamed by Omicron ops and Eochu
        //    Bres takes this with no compensating event of its own.
        MarketAPI sister = sisterMarket(market);
        ExileUnrestGuard.reportExilesPressure(market,
                ExileEmergencyMeasuresConfig.pressureDays(
                        Math.max(orbitalWorksDays, militaryBaseDays), unrestPoints));
        ExileUnrestGuard.reportExilesPressure(sister,
                ExileEmergencyMeasuresConfig.pressureDays(0f, unrestPoints));
        applyUnrest(market);
        applyUnrest(sister);

        // 3. The animation. VISUAL ONLY -- no doBombardment, no size loss, no pollution (ruling B).
        //    Self-guarding: a no-op unless the player is in-system to see it.
        SectorEntityToken entity = market.getPrimaryEntity();
        if (entity != null) MarketCMD.addBombardVisual(entity);

        // 4. The objective.
        boolean tookForge = stealNanoforge(market);

        // 5. Stand down. They came for the forge, they have it, and the war aim is served by
        //    getting it home -- not by trading capital ships with a Reserve that respawns.
        AshenStandDownScript.begin(system, intel.getFaction().getId());

        Global.getSector().getCampaignUI().addMessage(
                "The Ashen Crusade has broken Culann. Tri-Tachyon's yards are burning"
                        + (tookForge
                                ? ", and the Corrupted Nanoforge is gone."
                                : ", but the vault they came for was already empty."),
                Color.ORANGE);
        Global.getLogger(AshenCulannStrikeStage.class).info(
                "ASHEN CULANN STRIKE SUCCEEDED at " + market.getName()
                        + " -- heavybatteries " + heavyBatteriesDays
                        + "d, spaceport " + spaceportDays
                        + "d, refining " + refiningDays
                        + "d, orbitalworks " + orbitalWorksDays
                        + "d, militarybase " + militaryBaseDays
                        + "d; nanoforge " + (tookForge ? "STOLEN" : "ABSENT"));
    }

    /** Vanilla max-semantics, per industry. Never additive -- that is the drift-to-permanent path. */
    protected void disruptFor(MarketAPI market, String industryId, int days) {
        if (industryId == null || days <= 0) return;
        Industry industry = market.getIndustry(industryId);
        if (industry == null) return;
        industry.setDisrupted((float) days, true);
    }

    /** Culann ships {@code spaceport}, but take the upgrade if a game has one. */
    protected String spaceportIdOf(MarketAPI market) {
        if (market.hasIndustry(Industries.MEGAPORT)) return Industries.MEGAPORT;
        if (market.hasIndustry(Industries.SPACEPORT)) return Industries.SPACEPORT;
        return null;
    }

    protected void applyUnrest(MarketAPI market) {
        if (market == null) return;
        if (ExileUnrestGuard.isBattered(market)) {
            Global.getLogger(AshenCulannStrikeStage.class).info(
                    "unrest deciv-guard: skipped Culann-strike unrest at " + market.getName());
            return;
        }
        RecentUnrest.get(market).add(unrestPoints, "Ashen Crusade assault");
    }

    /** The other Tri-Tachyon market in the system -- Eochu Bres, resolved rather than hardcoded. */
    protected MarketAPI sisterMarket(MarketAPI struck) {
        if (system == null) return null;
        java.util.List markets = Misc.getMarketsInLocation(system, struck.getFactionId());
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (m != null && !m.getId().equals(struck.getId())) return m;
        }
        return null;
    }

    /**
     * Move the Corrupted Nanoforge from Culann's Orbital Works to Elpis (ruling K: STOLEN).
     *
     * <p><b>If it is not there, the strike still happened</b> (§3.2a ruling 2): the Crusade sailed
     * on years-old intelligence and does not learn the vault is empty until Culann is cracked. The
     * knockout above is not an alternative to the theft -- both happen.
     *
     * <p>Verified in §7.5: {@code setSpecialItem(null)} unapplies the old effect cleanly, Exiles
     * whitelists {@code exile_ashen_orbitalworks} on both nanoforge rows of its own
     * {@code special_items.csv}, and the effect path has no requirements to fail. Sean confirmed
     * the install in game on 2026-07-31.
     *
     * @return true if a forge was actually taken.
     */
    protected boolean stealNanoforge(MarketAPI culann) {
        Industry works = culann.getIndustry(Industries.ORBITALWORKS);
        if (works == null) return false;

        SpecialItemData special = works.getSpecialItem();
        if (special == null || !Items.CORRUPTED_NANOFORGE.equals(special.getId())) return false;

        MarketAPI elpis = findElpis();
        if (elpis == null) {
            Global.getLogger(AshenCulannStrikeStage.class).warn(
                    "Culann strike: nanoforge present but Elpis was not resolvable; leaving it.");
            return false;
        }
        Industry ashenWorks = elpis.getIndustry(ASHEN_ORBITAL_WORKS);
        if (ashenWorks == null) {
            Global.getLogger(AshenCulannStrikeStage.class).warn(
                    "Culann strike: Elpis has no " + ASHEN_ORBITAL_WORKS + "; leaving the forge.");
            return false;
        }

        works.setSpecialItem(null);
        ashenWorks.setSpecialItem(new SpecialItemData(Items.CORRUPTED_NANOFORGE, null));
        Global.getLogger(AshenCulannStrikeStage.class).info(
                "Corrupted Nanoforge moved: Culann orbitalworks -> " + elpis.getName()
                        + " " + ASHEN_ORBITAL_WORKS);
        return true;
    }

    /**
     * Elpis, resolved through its ENTITY.
     *
     * <p>CLAUDE.md §7 is explicit about this: the econ loader binds markets to entities via the
     * {@code "entities"} list and assigns its own market ids, so scripts must go through
     * {@code entity.getMarket()} rather than guessing a market id that the loader owns.
     */
    protected MarketAPI findElpis() {
        SectorEntityToken elpis = Global.getSector().getEntityById(ELPIS_ENTITY_ID);
        if (elpis == null) return null;
        return elpis.getMarket();
    }
}
