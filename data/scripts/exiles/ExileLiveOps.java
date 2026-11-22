package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;

/**
 * Shared queries over live staged operations (PASS 5b survivor, built with Pass 6a).
 *
 * <p><b>Scope is deliberately one method.</b> Pass 5b was scoped around "four near-duplicates of
 * the live-op intel scan" and the investigation found seven sites in three families (doc §14.6):
 * "how many of my ops are live" (three sites, each with a *different* filter), "is one of my ops
 * live in this system" (two sites, different return types), and this one — "which systems have any
 * live raid" — which is the only near-verbatim pair. The other two families keep their own loops:
 * with no lambdas under Janino, parameterizing their filters costs a filter interface plus
 * anonymous inner classes at each call site, which is more code than it saves.
 *
 * <p>Do not add speculative helpers here. A query earns a place in this class when it has two
 * callers that would otherwise hold identical code, not when it might one day be useful.
 *
 * <p><b>Still one PUBLIC method</b> after Pass 4 of the Heg-League war: that pass widened what the
 * existing question answers (blockades count now, D8) rather than adding a second question. The
 * new private helper exists only because the two pipelines need two loops.
 */
public class ExileLiveOps {

    /**
     * Systems hosting a live staged operation -- vanilla raids plus Black Fleet, Omicron and Ashen
     * interdiction raids, since all of them ride the same {@link RaidIntel} pipeline, <b>and, since
     * Pass 4 of the Heg-League war (D8), any live {@link ExileBlockadeIntel}</b>, which rides
     * vanilla's separate {@code BlockadeFGI} pipeline instead.
     *
     * <p>Callers use this to keep civilian traffic out of a war zone, so it is a launch-time
     * question and must never be asked per frame.
     *
     * <p><b>On the "live" test:</b> {@code isFailed()} is redundant alongside
     * {@code isEnded() || isEnding()} -- vanilla's {@code RaidIntel} calls {@code endAfterDelay()}
     * on every path that sets {@code failStage}, so a failed raid is always already ending. It is
     * kept because both original copies had it and removing it would be a behaviour change dressed
     * up as a cleanup. (This redundancy was briefly mistaken for an accreted inconsistency; see
     * §14.6.)
     *
     * @param excludeFactionId faction whose own operations do not count, or null to count every
     *        faction's. The Ashen relief manager passes its own id: the Crusade knows where its own
     *        fleets are and will still run aid convoys into systems it is raiding (§4.1c). That
     *        exclusion is load-bearing -- the Crusade raids Magec and Mayasura during Phases 2-3,
     *        which is where two of the four enclaves sit, so counting Ashen raids would silently
     *        cancel off-phase relief for them.
     * @return a list of {@link StarSystemAPI}, never null
     */
    public static List getSystemsWithActiveRaids(String excludeFactionId) {
        List result = new ArrayList(); // StarSystemAPI
        List raids = Global.getSector().getIntelManager().getIntel(RaidIntel.class);
        for (int i = 0; i < raids.size(); i++) {
            RaidIntel raid = (RaidIntel) raids.get(i);
            if (raid == null || raid.isEnded() || raid.isEnding() || raid.isFailed()) continue;
            if (excludeFactionId != null && raid.getFaction() != null
                    && excludeFactionId.equals(raid.getFaction().getId())) continue;
            if (raid.getSystem() != null && !result.contains(raid.getSystem())) {
                result.add(raid.getSystem());
            }
        }
        addBlockadedSystems(result, excludeFactionId);
        return result;
    }

    /**
     * Blockades, added to the same answer (PASS 4, decision D8 -- RULED by Sean 2026-09-20).
     *
     * <p><b>Why the helper was extended rather than left alone.</b> The original recommendation
     * was to leave it: a blockade is not a raid, and no HL manager could see a new target through
     * it. The collateral design surfaced the real consumer -- refugee CONVOYS pick their
     * destinations through this method, and a standalone Heg-League blockade is exactly the kind
     * of thing a convoy would otherwise sail into and then sit in for ten days being turned away
     * at the gates. Lore: refugees avoid blockaded systems. The Ashen blockade was already
     * covered in practice, because it launches beside a companion raid that this method saw.
     *
     * <p><b>The scan is on {@code FleetGroupIntel.class}, filtered by {@code instanceof}.</b>
     * Whether {@code IntelManagerAPI.getIntel(Class)} matches interfaces is an engine detail the
     * plan deliberately does not depend on: scanning the concrete vanilla base class and testing
     * the interface is correct either way.
     *
     * <p>The live test is the FGI one ({@code isAborted()} has no {@code RaidIntel} counterpart),
     * and {@code excludeFactionId} works the same as above -- a faction's own blockade does not
     * keep its own traffic out.
     */
    protected static void addBlockadedSystems(List result, String excludeFactionId) {
        List intel = Global.getSector().getIntelManager().getIntel(FleetGroupIntel.class);
        for (int i = 0; i < intel.size(); i++) {
            Object candidate = intel.get(i);
            if (!(candidate instanceof ExileBlockadeIntel)) continue;
            FleetGroupIntel fgi = (FleetGroupIntel) candidate;
            if (fgi.isEnded() || fgi.isEnding() || fgi.isAborted() || fgi.isFailed()) continue;
            if (excludeFactionId != null && fgi.getFaction() != null
                    && excludeFactionId.equals(fgi.getFaction().getId())) continue;
            StarSystemAPI system = ((ExileBlockadeIntel) candidate).getBlockadedSystem();
            if (system != null && !result.contains(system)) {
                result.add(system);
            }
        }
    }

    private ExileLiveOps() {
    }
}
