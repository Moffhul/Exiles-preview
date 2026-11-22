package data.scripts.exiles;

import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/**
 * Shared war-geography queries for the Hegemony-Persean League director
 * (docs/EXILES_HEG_LEAGUE_WAR.md sec 2, ruling R6).
 *
 * <p><b>Why this class exists, and why it is not a general "director helper" dumping ground.</b>
 * Pass 2 put these four predicates on {@link ExileHegLeagueInterdictionManager} because it was the
 * only caller. Pass 3 adds two more -- the strike manager picks its own targets, and the director
 * picks an incident SITE for the telegraph -- so the same bodies would otherwise be typed three
 * times. That is the measured bar the project sets for extraction (CLAUDE.md sec 6: "extract a
 * utility only when two callers would otherwise hold identical code -- measure first"), and it is
 * met here. It was NOT met for the feasibility gates, which stay private per op type: interdiction
 * excludes station strength and strikes include it, and those differences are design, not drift
 * (ruling S4, Ashen doc sec 14.6 -- do not merge them).
 *
 * <p><b>The bodies are moved, not rewritten.</b> {@link ExileHegLeagueInterdictionManager} keeps
 * its own protected methods as one-line delegates so every Pass-2 call site, and every behaviour
 * the 2026-08-27 live test signed off, is byte-identical.
 *
 * <p>Everything here queries ownership <b>live</b> (R6c). Nothing is ever a hardcoded target list:
 * core worlds change hands, especially under Nexerelin, and a market that changes hands must simply
 * leave the pool.
 */
public class ExileHegLeagueGeography {

    private ExileHegLeagueGeography() {
    }

    /**
     * R6a -- the SYSTEM-level capital exemption. Matched against both the system id and its base
     * name, case-insensitively: vanilla systems are created by display name and looked up by
     * lowercase id in starmap.json, and an exemption that silently failed to match would quietly
     * make a capital targetable. {@code ExileHegLeagueInterdictionManager.logGeographyResolution}
     * is the first-boot report that turns a typo into a log line rather than a playtest surprise.
     */
    public static boolean isExemptSystem(ExileHegLeagueConfig config, StarSystemAPI system) {
        if (system == null || config == null) return false;
        for (int i = 0; i < config.exemptSystems.size(); i++) {
            String id = (String) config.exemptSystems.get(i);
            if (id == null) continue;
            if (id.equalsIgnoreCase(system.getId())) return true;
            if (id.equalsIgnoreCase(system.getBaseName())) return true;
        }
        return false;
    }

    /**
     * R6b (Pass 4) -- is this system on the author's SYSTEM-blockade whitelist (sec 14.9.1)?
     *
     * <p>Same id-or-base-name, case-insensitive match as {@link #isExemptSystem}, for the same
     * reason: vanilla systems are created by display name and looked up by lowercase id, and an
     * entry that silently failed to match would quietly remove a war zone from the map. The
     * first-boot geography line reports OK/UNRESOLVED for every id.
     *
     * <p><b>ONE MERGED LIST</b> (D1, ruled): the whitelist names acceptable war zones, not owners.
     * Whether a given side may act there is decided by the live "does the victim own an eligible
     * market here" test, so an ownership flip under Nexerelin moves the system between the two
     * sides' pools with no config change.
     *
     * <p>An EMPTY list means "nowhere" -- no blockade may launch. That is deliberate and the
     * opposite of {@code allowedMarkets}: this list is a whitelist, not a narrowing knob.
     */
    public static boolean isBlockadeWhitelistedSystem(ExileHegLeagueConfig config,
                                                      StarSystemAPI system) {
        if (system == null || config == null) return false;
        for (int i = 0; i < config.blockadeWhitelistSystems.size(); i++) {
            String id = (String) config.blockadeWhitelistSystems.get(i);
            if (id == null) continue;
            if (id.equalsIgnoreCase(system.getId())) return true;
            if (id.equalsIgnoreCase(system.getBaseName())) return true;
        }
        return false;
    }

    /**
     * R6b (Pass 6) -- is this MARKET on the author's planetary-blockade whitelist (sec 14.9.1)?
     *
     * <p>The market twin of {@link #isBlockadeWhitelistedSystem}, and deliberately a separate
     * method rather than a parameterised one: the system list matches on id OR base name because
     * vanilla systems are created by display name, while market ids are exact strings with no
     * display-name fallback, so a name-insensitive match here would be a different (and wronger)
     * test. Both lists are reported OK/UNRESOLVED on the first-boot geography line.
     *
     * <p><b>ONE MERGED LIST</b>, as D1 ruled for the system whitelist and for the same reason: an
     * entry names an acceptable target colony, not an owner. Whether a given side may squeeze it
     * is decided by the live "does the offender own this market" test, so an ownership flip under
     * Nexerelin moves it between the two sides' pools with no config change.
     *
     * <p>An EMPTY list means "nowhere" -- no squeeze may launch. That is deliberate and the
     * opposite of {@code allowedMarkets}: this is a whitelist, not a narrowing knob.
     */
    public static boolean isPlanetaryBlockadeWhitelisted(ExileHegLeagueConfig config,
                                                         MarketAPI market) {
        if (config == null || market == null) return false;
        return config.planetaryBlockadeWhitelistMarkets.contains(market.getId());
    }

    /**
     * D2 (Pass 4) -- may this market take the lighter COLLATERAL condition?
     *
     * <p>Asked of every market in a blockaded system that the victim does NOT own. The victim's
     * own markets take vanilla {@code Blockaded} at full tiered force and never reach this test.
     *
     * <p>Every clause is a standing Exiles guard rather than a blockade-specific rule: the
     * refugee canon shield, the R6b strain markets, player colonies (which cannot occur here
     * anyway, since a player market takes the whole system off the target map one level up), and
     * the Battered/Emergency-Measures guard every director consults. None of them matches inside
     * the five whitelisted systems today; they exist so a later whitelist edit cannot quietly
     * squeeze a fragile world.
     *
     * <p><b>Deliberately NOT applied:</b> {@code allowedMarkets}. That is a target-SELECTION knob
     * (the Pass-2 precedent in {@code isExcludedMarket}), and a bystander market is not a target.
     */
    public static boolean isBlockadeCollateralMarket(ExileHegLeagueConfig config, MarketAPI m) {
        if (config == null || m == null) return false;
        if (m.isHidden() || !m.isInEconomy()) return false;
        if (m.isPlayerOwned()) return false;
        if (ExileProtectedFactions.isProtected(m)) return false;
        if (config.excludedMarkets.contains(m.getId())) return false;
        if (ExileUnrestGuard.isBattered(m)) return false;
        return true;
    }

    /**
     * May this op legally squeeze this market? Every clause is a spec ruling, not a heuristic.
     *
     * <p>NOTE: this deliberately does NOT include the {@link #isExemptSystem} test. Pass 2 applies
     * the capital exemption at SYSTEM level, one level up, before it ever looks at markets, and
     * moving it in here would change the shipped call order. Callers that start from a market
     * rather than a system must ask both (see {@link #pickAnyEligibleMarket}).
     */
    public static boolean isEligibleVictimMarket(ExileHegLeagueConfig config, MarketAPI m,
                                                 String victimFactionId) {
        if (m == null || m.isHidden() || !m.isInEconomy() || m.isPlayerOwned()) return false;
        if (!victimFactionId.equals(m.getFactionId())) return false;
        // Canon, not tuning: the refugee faction is never a direct target of any NPC op.
        if (ExileProtectedFactions.isProtected(m)) return false;
        // R6b: the five refugee strain markets are never direct victims of ANY op.
        if (config.excludedMarkets.contains(m.getId())) return false;
        // Author allow-list (R6c). Empty = everything non-exempt is fair game.
        if (!config.allowedMarkets.isEmpty() && !config.allowedMarkets.contains(m.getId())) {
            return false;
        }
        // Always through the guard: a market already on its knees (Recent Unrest cap, or live
        // Emergency Measures) is off the menu for every Exiles director.
        if (ExileUnrestGuard.isBattered(m)) return false;
        return true;
    }

    /** At least one market in the system that an op may legally squeeze. */
    public static boolean hasEligibleVictimMarket(ExileHegLeagueConfig config, StarSystemAPI system,
                                                  String victimFactionId) {
        List inSystem = Misc.getMarketsInLocation(system);
        for (int i = 0; i < inSystem.size(); i++) {
            MarketAPI m = (MarketAPI) inSystem.get(i);
            if (isEligibleVictimMarket(config, m, victimFactionId)) return true;
        }
        return false;
    }

    /**
     * A market of the acting side with a functional spaceport -- ops stage from somewhere real,
     * weighted by market size.
     *
     * <p><b>Exempt systems are valid SOURCES</b> (R6a is a targeting rule, not a basing rule), so
     * Chicomoztoc and Kazeron can and should send fleets.
     */
    public static MarketAPI pickSource(String actorFactionId, Random random) {
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market.isHidden() || !market.isInEconomy()) continue;
            if (!actorFactionId.equals(market.getFactionId())) continue;
            if (market.getPrimaryEntity() == null) continue;
            Industry spaceport = Misc.getSpaceport(market);
            if (spaceport == null || !spaceport.isFunctional()) continue;
            picker.add(market, (float) market.getSize());
        }
        return (MarketAPI) picker.pick();
    }

    /**
     * The NEAREST plausible same-faction port to a given market, distance-weighted (Pass 6,
     * D80.5 item 5).
     *
     * <p><b>Why not {@link #pickSource}.</b> That one is SIZE-weighted, which is right for a war
     * fleet -- a big industrial world musters a bigger squadron -- and wrong for relief, which
     * would sail a food convoy across the sector from Chicomoztoc to answer a wound two jumps from
     * a size-4 port. This one weights {@code 1 / (1 + distLY * distanceFalloff)}, the same falloff
     * the target pickers use, so aid comes from nearby.
     *
     * <p>Same functional-spaceport requirement: a convoy has to load somewhere real. The target's
     * OWN market is excluded -- a colony does not send itself relief.
     *
     * <p>Allocates a market list, so it must only ever be called on a slow path -- once per convoy
     * launch, never per tick (CLAUDE.md sec 7).
     *
     * @return a market, or null if the faction has no reachable functional port.
     */
    public static MarketAPI pickSourceNear(ExileHegLeagueConfig config, String actorFactionId,
                                           MarketAPI target, Random random) {
        if (actorFactionId == null || target == null) return null;
        float falloff = config != null ? config.distanceFalloff : 0.10f;
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI market = (MarketAPI) markets.get(i);
            if (market.isHidden() || !market.isInEconomy()) continue;
            if (!actorFactionId.equals(market.getFactionId())) continue;
            if (market.getPrimaryEntity() == null) continue;
            if (market == target) continue;
            Industry spaceport = Misc.getSpaceport(market);
            if (spaceport == null || !spaceport.isFunctional()) continue;
            float distLY = Misc.getDistanceLY(
                    market.getPrimaryEntity().getLocationInHyperspace(),
                    target.getPrimaryEntity() != null
                            ? target.getPrimaryEntity().getLocationInHyperspace()
                            : market.getPrimaryEntity().getLocationInHyperspace());
            picker.add(market, 1f / (1f + distLY * falloff));
        }
        return (MarketAPI) picker.pick();
    }

    /**
     * One market of {@code factionId} that any op would be allowed to touch, size-weighted.
     *
     * <p><b>Pass 3 uses this for FLAVOUR ONLY</b> -- naming a plausible place in the incident
     * telegraph (sec 3.2.2). It runs every guard anyway, because "the massacre happened at
     * Mairaath" would violate R6b's spirit even though no mechanical effect follows the name. It
     * additionally applies {@link #isExemptSystem}, which {@link #isEligibleVictimMarket} leaves to
     * its caller.
     *
     * <p>Allocates a market list, so it must only ever be called on a slow path -- once per
     * incident, never per tick (CLAUDE.md sec 7).
     *
     * @return a market, or null if the faction has nothing nameable.
     */
    public static MarketAPI pickAnyEligibleMarket(ExileHegLeagueConfig config, String factionId,
                                                  Random random) {
        if (config == null || factionId == null) return null;
        WeightedRandomPicker picker = new WeightedRandomPicker(random);
        List markets = Global.getSector().getEconomy().getMarketsCopy();
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (!isEligibleVictimMarket(config, m, factionId)) continue;
            if (!(m.getContainingLocation() instanceof StarSystemAPI)) continue;
            if (isExemptSystem(config, (StarSystemAPI) m.getContainingLocation())) continue;
            picker.add(m, (float) m.getSize());
        }
        return (MarketAPI) picker.pick();
    }

    /**
     * True while this director already has a live operation in the system -- any
     * {@link ExileHegLeagueRaidIntel} (interdiction, strike or squeeze: {@code getIntel(Class)}
     * returns subclasses, so one scan covers all three) or a {@link ExileHegLeagueBlockade}, which
     * rides vanilla's {@code BlockadeFGI} and needs its own scan. Paired everywhere with
     * {@code ExileLiveOps.getSystemsWithActiveRaids} (the cross-director dogpile check); this is
     * the war's own. One copy since the 2026-11-08 complexity review (tier 3); it was four,
     * byte-identical.
     */
    public static boolean hasLiveOpIn(StarSystemAPI system) {
        List ops = Global.getSector().getIntelManager().getIntel(ExileHegLeagueRaidIntel.class);
        for (int i = 0; i < ops.size(); i++) {
            ExileHegLeagueRaidIntel op = (ExileHegLeagueRaidIntel) ops.get(i);
            if (op.isEnded() || op.isEnding()) continue;
            if (op.getSystem() == system) return true;
        }
        List blockades = Global.getSector().getIntelManager()
                .getIntel(ExileHegLeagueBlockade.class);
        for (int i = 0; i < blockades.size(); i++) {
            ExileHegLeagueBlockade op = (ExileHegLeagueBlockade) blockades.get(i);
            if (op.isEnded() || op.isEnding() || op.isAborted() || op.isFailed()) continue;
            if (op.getBlockadedSystem() == system) return true;
        }
        return false;
    }

    /**
     * The biggest eligible victim market in the system: the one an interdiction or blockade
     * names on its intel and patrol text. It does NOT decide where fleets sit. One copy since the
     * 2026-11-08 complexity review (tier 3); it was two, identical.
     */
    public static MarketAPI pickFocusMarket(ExileHegLeagueConfig config, StarSystemAPI system,
                                            String victimFactionId) {
        MarketAPI best = null;
        int max = -1;
        List markets = Misc.getMarketsInLocation(system);
        for (int i = 0; i < markets.size(); i++) {
            MarketAPI m = (MarketAPI) markets.get(i);
            if (!isEligibleVictimMarket(config, m, victimFactionId)) continue;
            if (m.getSize() > max) {
                max = m.getSize();
                best = m;
            }
        }
        return best;
    }
}
