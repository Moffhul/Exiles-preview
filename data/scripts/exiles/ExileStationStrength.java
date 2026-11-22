package data.scripts.exiles;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.List;

/**
 * Station strength that survives a save load. Drop-in replacement for vanilla's
 * {@code WarSimScript.getStationStrength(faction, system, from)}: same filters (station mode,
 * same faction, within 3x battle-join range of {@code from}), same inputs, same units.
 *
 * <p><b>Why it exists (found 2026-10-26, decompiled FleetData/CampaignFleet, 0.98a-RC8):</b>
 * vanilla sums {@code fleet.getEffectiveStrength()}, which returns a cached total that is not saved.
 * After a load, {@code getFlagship()} / {@code getSortedMembers()} run a member-lists-only sync
 * that clears the "needs sync" flag without recomputing that total, so it stays 0. Moving fleets
 * resync constantly; a station never changes, so it reads 0 until the player visits its system.
 * In the Pass 7d-1 test Sphinx's battlestation read 240 on a fresh game and 0 for 16 months after
 * a load. Every director gate that adds station strength saw stationed targets as undefended.
 *
 * <p>The fix sums the engine's own per-member formula ({@code Misc.getMemberStrength(member,
 * true, true, true)}, exactly what the full sync sums). Per-member strength IS reset on load, so
 * this is correct without touching the fleet's state. This is shared MEASUREMENT only; every
 * caller keeps its own decision about whether and how station strength counts.
 *
 * <p>If a future Starsector changes vanilla's filters, update {@link #get} to match.
 */
public class ExileStationStrength {

    /** Same contract as {@code WarSimScript.getStationStrength}; correct after a save load. */
    public static float get(FactionAPI faction, StarSystemAPI system, SectorEntityToken from) {
        if (faction == null || system == null || from == null) return 0f;
        float maxDist = Misc.getBattleJoinRange() * 3f;
        float strength = 0f;
        List fleets = system.getFleets();
        for (int i = 0; i < fleets.size(); i++) {
            CampaignFleetAPI fleet = (CampaignFleetAPI) fleets.get(i);
            if (fleet == null || !fleet.isStationMode()) continue;
            if (fleet.getFaction() != faction) continue;
            if (Misc.getDistance(from, fleet) >= maxDist) continue;
            strength += memberSum(fleet);
        }
        return strength;
    }

    /** The engine's full-sync total for one fleet, recomputed instead of read from the cache. */
    public static float memberSum(CampaignFleetAPI fleet) {
        if (fleet == null) return 0f;
        FleetDataAPI data = fleet.getFleetData();
        if (data == null) return 0f;
        List members = data.getMembersListCopy();
        float sum = 0f;
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI member = (FleetMemberAPI) members.get(i);
            if (member == null) continue;
            sum += Misc.getMemberStrength(member, true, true, true);
        }
        return sum;
    }
}
