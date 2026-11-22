package data.scripts.exiles;

import java.util.List;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;

/** Shared, local-only danger policy for Refugee salvage dispatch and active expeditions. */
public final class ExileRefugeeSalvageThreats {
    private ExileRefugeeSalvageThreats() {}

    /** Hostile markets reserve their immediate patrol/station space without poisoning the system. */
    public static boolean isTargetSafe(SectorEntityToken target, ExileRefugeeSalvageConfig cfg) {
        if (target == null || target.getContainingLocation() == null || Global.getSector() == null
                || Global.getSector().getEconomy() == null) return false;
        List<MarketAPI> markets = Global.getSector().getEconomy().getMarkets(target.getContainingLocation());
        if (markets == null) return true;
        for (MarketAPI market : markets) {
            if (!hostileMarket(market)) continue;
            SectorEntityToken primary = market.getPrimaryEntity();
            if (primary != null && primary.getContainingLocation() == target.getContainingLocation()
                    && Misc.getDistance(primary, target) <= cfg.hostileMarketSafetyRadius) return false;
        }
        return true;
    }

    /**
     * The LAUNCH screen: the system's live fleet list, once a response is otherwise ready.
     *
     * <p>Rewritten 2026-09-21 (Sean's ruling after the Isirah read). The old screen was
     * system-wide for every hostile fleet, and a pirate market's roaming patrols, scouts and
     * convoys held Isirah deferred for months at a time. Two classes now:
     * <ul>
     * <li><b>Mobile threats</b> -- raiders, war fleets, and any hostile fleet not sourced from a
     *     market in this system -- still defer a launch wherever they are. That is the "active
     *     raid" rule and it is unchanged.</li>
     * <li><b>Local defense</b> -- a hostile fleet sourced from a market in this system, whatever
     *     its type (patrol, scout, convoy) -- defers a launch only within
     *     {@code launchThreatRadius} of the chosen wreck. Refugees will brave a system they know
     *     hostile patrols are in; the in-flight visible-threat check is what makes them run when
     *     spotted.</li>
     * </ul>
     * {@code localDefenseExemptAtLaunch} false restores the old system-wide screen.
     */
    public static CampaignFleetAPI findLaunchThreat(LocationAPI location, SectorEntityToken target,
                                                     float expeditionStrength,
                                                     ExileRefugeeSalvageConfig cfg) {
        if (location == null) return null;
        List<CampaignFleetAPI> fleets = location.getFleets();
        if (fleets == null) return null;
        // REF-3 (Sean, 2026-10-06): compare STRENGTH with strength, as the in-flight screen does.
        // Hostile fleets are measured with getEffectiveStrength(); the old floor was raw FP
        // (combatFp x mult), k-times too low, so small hostiles deferred launches.
        float minimumStrength = getLaunchDangerFloor(expeditionStrength, cfg);
        // The MEMORY_KEY_MAKE_NON_AGGRESSIVE skips here and in findThreat() are KEPT deliberately
        // (bug sweep REF-1, 2026-10-06): the engine ignores the flag, but Omicron uses it as a
        // marker on its dormant sleeper/defence fleets (OmicronDefenseFleetManager,
        // OmicronTriTachyonDefenseManager), which hold station and do not hunt. Dropping the skip
        // would make salvagers defer for those sleepers in Tri-Tachyon systems.
        for (CampaignFleetAPI other : fleets) {
            if (other == null || !other.isAlive() || other.getContainingLocation() != location
                    || other.isStationMode()
                    || other.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE)
                    || !hostileToRefugees(other, null)
                    || other.getEffectiveStrength() < minimumStrength) continue;
            if (cfg.localDefenseExemptAtLaunch && isLocalDefense(other)
                    && target != null && target.getContainingLocation() == location
                    && Misc.getDistance(other, target) > cfg.launchThreatRadius) continue;
            return other;
        }
        return null;
    }

    /**
     * REF-3: the launch screen's danger floor in STRENGTH units -- an expedition's MEASURED
     * effective strength (the last one dispatched, the same number the in-flight screen uses)
     * x {@code danger.strengthMult}. Until one has been measured this session it falls back to
     * the old raw-FP floor. The dispatch log line carries the measured value.
     */
    public static float getLaunchDangerFloor(float expeditionStrength, ExileRefugeeSalvageConfig cfg) {
        float strength = expeditionStrength > 0f ? expeditionStrength : cfg.combatFp;
        return strength * cfg.dangerStrengthMult;
    }

    /** Sourced from a market in its own system, and neither a raider nor a war fleet. */
    public static boolean isLocalDefense(CampaignFleetAPI fleet) {
        if (fleet == null) return false;
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_RAIDER)
                || fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_WAR_FLEET)) return false;
        MarketAPI source = Misc.getSourceMarket(fleet);
        SectorEntityToken primary = source == null ? null : source.getPrimaryEntity();
        return primary != null && primary.getContainingLocation() == fleet.getContainingLocation();
    }

    /** Active fleets retain the old visible-only check, with the same local-defense exclusions. */
    public static CampaignFleetAPI findVisibleThreat(CampaignFleetAPI refugee,
                                                      ExileRefugeeSalvageConfig cfg) {
        if (refugee == null || refugee.getContainingLocation() == null) return null;
        return findThreat(Misc.getVisibleFleets(refugee, false), refugee.getContainingLocation(),
                refugee, refugee, refugee.getEffectiveStrength() * cfg.dangerStrengthMult, cfg);
    }

    protected static CampaignFleetAPI findThreat(List<CampaignFleetAPI> fleets, LocationAPI location,
                                                   CampaignFleetAPI refugee, SectorEntityToken reference,
                                                   float minimumStrength, ExileRefugeeSalvageConfig cfg) {
        if (fleets == null) return null;
        for (CampaignFleetAPI other : fleets) {
            if (other == null || other == refugee || !other.isAlive()
                    || other.getContainingLocation() != location || other.isStationMode()
                    || other.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_MAKE_NON_AGGRESSIVE)
                    || !hostileToRefugees(other, refugee)
                    || other.getEffectiveStrength() < minimumStrength) continue;
            if (isContainedHomePatrol(other, reference, cfg)) continue;
            return other;
        }
        return null;
    }

    protected static boolean hostileToRefugees(CampaignFleetAPI other, CampaignFleetAPI refugee) {
        if (refugee != null) return other.isHostileTo(refugee);
        return other.getFaction() != null
                && other.getFaction().isHostileTo(ExileRefugeeSalvageExpeditionManager.FACTION);
    }

    protected static boolean hostileMarket(MarketAPI market) {
        return market != null && market.getFaction() != null
                && market.getFaction().isHostileTo(ExileRefugeeSalvageExpeditionManager.FACTION);
    }

    /**
     * Vanilla pirate patrols deliberately omit $isPatrol, so use fleet type as a fallback.
     * Raiders never receive this exemption. A nominally home-bound patrol that closes on the
     * operation is also a threat, even while still inside its market's defense bubble.
     */
    protected static boolean isContainedHomePatrol(CampaignFleetAPI fleet, SectorEntityToken reference,
                                                    ExileRefugeeSalvageConfig cfg) {
        if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_RAIDER)) return false;
        String type = Misc.getFleetType(fleet);
        boolean patrol = fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)
                || FleetTypes.PATROL_SMALL.equals(type) || FleetTypes.PATROL_MEDIUM.equals(type)
                || FleetTypes.PATROL_LARGE.equals(type);
        if (!patrol) return false;
        MarketAPI source = Misc.getSourceMarket(fleet);
        SectorEntityToken primary = source == null ? null : source.getPrimaryEntity();
        if (primary == null || primary.getContainingLocation() != fleet.getContainingLocation()
                || source.getFactionId() == null || fleet.getFaction() == null
                || !source.getFactionId().equals(fleet.getFaction().getId())
                || Misc.getDistance(fleet, primary) > cfg.hostileMarketSafetyRadius) return false;
        return reference == null || reference.getContainingLocation() != fleet.getContainingLocation()
                || Misc.getDistance(fleet, reference) > cfg.hostileMarketSafetyRadius;
    }
}
