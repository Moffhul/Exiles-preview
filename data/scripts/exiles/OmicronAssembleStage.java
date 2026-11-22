package data.scripts.exiles;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.util.Misc;

/**
 * AssembleStage for a MARKETLESS source: the "market" handed to {@link #addSource} is a bare
 * {@code MarketAPI} object whose primary entity is the node's Nexus station fleet — never
 * added to the economy (the ratified escape hatch in EXILES_OMICRON_FACTION_ARCHITECTURE.md
 * §2a: a market OBJECT where the API requires one, without economy participation).
 *
 * <p>The only override is {@link #addRoutesAsNeeded}: identical to vanilla except the
 * {@code market.isInEconomy()} eligibility check is dropped (a bare market is never in the
 * economy). The {@code primaryEntity.isAlive()} check REMAINS — that is the kill-the-source
 * cutoff: destroy the Nexus and in-flight operations stop reinforcing, then fail.
 */
public class OmicronAssembleStage extends AssembleStage {

    public OmicronAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint) {
        super(raid, gatheringPoint);
    }

    /**
     * The Omicron home systems are SECRET (author decision 2026-07-03): never reveal the
     * source. This suppresses both the intel-map arrow from the origin system
     * (RaidIntel.getArrowData bails when the source is unknown) and the "assembling in the
     * X system" stage text, which becomes "assembling at an unknown location".
     */
    @Override
    public boolean isSourceKnown() {
        return false;
    }

    @Override
    protected void addRoutesAsNeeded(float amount) {
        if (spawnFP <= 0) return;

        float days = Misc.getDays(amount);

        interval.advance(days);
        if (!interval.intervalElapsed()) return;

        if (sources.isEmpty()) {
            status = RaidStageStatus.FAILURE;
            return;
        }

        MarketAPI market = (MarketAPI) sources.get(currSource);
        // Vanilla also requires market.isInEconomy(); dropped here — the bare Nexus-source
        // market is deliberately never in the economy. Nexus-alive is the real gate.
        if (market.getPrimaryEntity() == null || !market.getPrimaryEntity().isAlive()) {
            sources.remove(market);
            return;
        }

        currSource++;
        currSource %= sources.size();

        OptionalFleetData extra = new OptionalFleetData(market);

        String sid = intel.getRouteSourceId();
        RouteData route = RouteManager.getInstance().addRoute(
                sid, market, Long.valueOf(Misc.genRandomSeed()), extra, intel, null);

        extra.fleetType = pickNextType();
        float fp = getFP(extra.fleetType);

        extra.fp = Float.valueOf(fp);
        extra.strength = Float.valueOf(Misc.getAdjustedStrength(fp, market));

        float prepDays = 3f + 3f * (float) Math.random();
        float travelDays = RouteLocationCalculator.getTravelDays(market.getPrimaryEntity(), gatheringPoint);

        if (DebugFlags.RAID_DEBUG || DebugFlags.FAST_RAIDS) {
            prepDays *= 0.1f;
            travelDays *= 0.1f;
        }

        route.addSegment(new RouteSegment(prepDays, market.getPrimaryEntity(), PREP_STAGE));
        route.addSegment(new RouteSegment(travelDays, market.getPrimaryEntity(), gatheringPoint));
        route.addSegment(new RouteSegment(1000f, gatheringPoint, WAIT_STAGE));

        maxDays = Math.max(maxDays, prepDays + travelDays);
    }
}
