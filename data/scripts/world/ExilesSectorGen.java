package data.scripts.world;

import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import data.scripts.world.systems.ExileXechas;

import static com.fs.starfarer.api.campaign.RepLevel.*;


public class ExilesSectorGen {

    /**
     * First pass -- runs in onNewGame(), BEFORE vanilla proc-gen. Only the player's home system
     * (Xechas) is placed here, so NGCSetStartingLocation resolves against a system that exists on
     * the proven early path. Faction relations are faction-level and set here too.
     */
    public void generateHomeSystems(SectorAPI sector) {
        new ExileXechas().generate(sector);
        applyFactionRelations(sector);
    }

    /**
     * Second pass -- runs in onNewGameAfterProcGen(), AFTER vanilla lays down its constellations,
     * so the custom systems dodge the constellation keep-out "spotlight" gap. Placement (cluster
     * jitter + proximity detector + optional constellation join) is driven by
     * data/config/exiles/system_placement.json via ExileSystemPlacement. Still before economy
     * load, so the market-bearing custom systems bind their markets normally.
     */
    public void generateDeferredSystems(SectorAPI sector) {
        new ExileSystemPlacement().generateDeferredSystems(sector);
    }

    private void setRel(SectorAPI sector, String factionIdOne, String factionIdTwo, RepLevel relationship) {
        if (sector.getFaction(factionIdOne) == null) return;
        if (sector.getFaction(factionIdTwo) == null) return;

        sector.getFaction(factionIdOne).setRelationship(factionIdTwo, relationship);
    }

    private void applyFactionRelations(SectorAPI sector) {
 //       FactionAPI player = sector.getFaction(Factions.PLAYER);
//        FactionAPI hegemony = sector.getFaction(Factions.HEGEMONY);
//        FactionAPI tritachyon = sector.getFaction(Factions.TRITACHYON);
//        FactionAPI pirates = sector.getFaction(Factions.PIRATES);
//        FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);
//        FactionAPI church = sector.getFaction(Factions.LUDDIC_CHURCH);
//        FactionAPI path = sector.getFaction(Factions.LUDDIC_PATH);
//        FactionAPI kol = sector.getFaction(Factions.KOL);
//        FactionAPI diktat = sector.getFaction(Factions.DIKTAT);
//        FactionAPI persean = sector.getFaction(Factions.PERSEAN);
//        FactionAPI guard = sector.getFaction(Factions.LIONS_GUARD);

        setRel(sector,"exilemidan", Factions.HEGEMONY, VENGEFUL);
        setRel(sector,"exilemidan", Factions.INDEPENDENT, HOSTILE);
        setRel(sector,"exilemidan", Factions.KOL, HOSTILE);
        setRel(sector,"exilemidan", Factions.LIONS_GUARD, HOSTILE);
        setRel(sector,"exilemidan", Factions.LUDDIC_CHURCH, HOSTILE);
        setRel(sector,"exilemidan", Factions.LUDDIC_PATH, VENGEFUL);
        setRel(sector,"exilemidan", Factions.MERCENARY, HOSTILE);
        setRel(sector,"exilemidan", Factions.PERSEAN, HOSTILE);
        setRel(sector,"exilemidan", Factions.PIRATES, HOSTILE);
        setRel(sector,"exilemidan", Factions.PLAYER, INHOSPITABLE);
        setRel(sector,"exilemidan", Factions.REMNANTS, HOSTILE);
        setRel(sector,"exilemidan", Factions.SCAVENGERS, HOSTILE);
        setRel(sector,"exilemidan", Factions.DIKTAT, HOSTILE);
        // Sindrian Reformers (0.98.5a): a rival claimant to the Diktat that exists only during
        // vanilla's Askonia standoff. Sean 2026-11-14: Midan and Black Fleet HOSTILE (hostile to
        // both sides, so Exiles does not tilt the standoff); the other three explicitly NEUTRAL.
        setRel(sector,"exilemidan", Factions.DIKTAT_ACHILLES, HOSTILE);
        setRel(sector,"exilemidan", Factions.THREAT, VENGEFUL);
        setRel(sector,"exilemidan", Factions.DWELLER, VENGEFUL);
        setRel(sector,"exilemidan", Factions.TRITACHYON, VENGEFUL);
        setRel(sector,"exilemidan", "exileashencrusade", HOSTILE);
        setRel(sector,"exilemidan", "exileomicron", HOSTILE);
        setRel(sector,"exilemidan", "exilerefugee",INHOSPITABLE);
        // Directed proxy alliance, reciprocal by author ruling 2026-07-12. The Black Fleet
        // is a dangerous, compartmentalized partner rather than an openly integrated arm,
        // so FRIENDLY is the ceiling/floor instead of COOPERATIVE.
        setRel(sector,"exilemidan", "exileblackfleet", FRIENDLY);

        // Black Fleet (split 2026-07-07): Mourad's Midan-backed armada. Hostile to the whole
        // Core like ordinary pirates -- INCLUDING the vanilla pirates themselves (Kanta cannot
        // stand the rise of new pirate royalty; the friction is canon). FRIENDLY with Midan is
        // the alliance floor: visible cooperation without COOPERATIVE's open-books intimacy
        // (compartmentalization -- the rank-and-file only know a supplier cutout).
        setRel(sector,"exileblackfleet", "exilemidan", FRIENDLY);
        setRel(sector,"exileblackfleet", Factions.HEGEMONY, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.INDEPENDENT, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.KOL, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.LIONS_GUARD, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.LUDDIC_CHURCH, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.LUDDIC_PATH, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.MERCENARY, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.PERSEAN, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.PIRATES, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.PLAYER, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.REMNANTS, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.SCAVENGERS, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.DIKTAT, HOSTILE);
        setRel(sector,"exileblackfleet", Factions.DIKTAT_ACHILLES, HOSTILE); // Sean 2026-11-14
        setRel(sector,"exileblackfleet", Factions.THREAT, VENGEFUL);
        setRel(sector,"exileblackfleet", Factions.DWELLER, VENGEFUL);
        setRel(sector,"exileblackfleet", Factions.TRITACHYON, HOSTILE);
        setRel(sector,"exileblackfleet", "exileashencrusade", HOSTILE);
        setRel(sector,"exileblackfleet", "exileomicron", HOSTILE);
        setRel(sector,"exileblackfleet", "exilerefugee", INHOSPITABLE);

        setRel(sector,"exileashencrusade", Factions.HEGEMONY, NEUTRAL);
        setRel(sector,"exileashencrusade", Factions.INDEPENDENT, INHOSPITABLE);
        setRel(sector,"exileashencrusade", Factions.KOL, NEUTRAL);
        setRel(sector,"exileashencrusade", Factions.LIONS_GUARD, INHOSPITABLE);
        setRel(sector,"exileashencrusade", Factions.LUDDIC_CHURCH, INHOSPITABLE);
        setRel(sector,"exileashencrusade", Factions.LUDDIC_PATH, COOPERATIVE);
        setRel(sector,"exileashencrusade", Factions.MERCENARY, NEUTRAL);
        setRel(sector,"exileashencrusade", Factions.PERSEAN, HOSTILE);
        setRel(sector,"exileashencrusade", Factions.PIRATES, HOSTILE);
        setRel(sector,"exileashencrusade", Factions.PLAYER, SUSPICIOUS);
        setRel(sector,"exileashencrusade", Factions.REMNANTS, HOSTILE);
        setRel(sector,"exileashencrusade", Factions.SCAVENGERS, NEUTRAL);
        setRel(sector,"exileashencrusade", Factions.DIKTAT, INHOSPITABLE);
        setRel(sector,"exileashencrusade", Factions.DIKTAT_ACHILLES, NEUTRAL); // Sean 2026-11-14
        setRel(sector,"exileashencrusade", Factions.THREAT, HOSTILE);
        setRel(sector,"exileashencrusade", Factions.DWELLER, HOSTILE);
        setRel(sector,"exileashencrusade", Factions.TRITACHYON, VENGEFUL);
        setRel(sector,"exileashencrusade", "exileomicron", VENGEFUL);
        setRel(sector,"exileashencrusade", "exilerefugee", NEUTRAL);

        setRel(sector,"exileomicron", Factions.HEGEMONY, HOSTILE);
        setRel(sector,"exileomicron", Factions.INDEPENDENT, HOSTILE);
        setRel(sector,"exileomicron", Factions.KOL, VENGEFUL);
        setRel(sector,"exileomicron", Factions.LIONS_GUARD, INHOSPITABLE);
        setRel(sector,"exileomicron", Factions.LUDDIC_CHURCH, VENGEFUL);
        setRel(sector,"exileomicron", Factions.LUDDIC_PATH, VENGEFUL);
        setRel(sector,"exileomicron", Factions.MERCENARY, HOSTILE);
        setRel(sector,"exileomicron", Factions.PERSEAN, HOSTILE);
        setRel(sector,"exileomicron", Factions.PIRATES, HOSTILE);
        setRel(sector,"exileomicron", Factions.PLAYER, INHOSPITABLE); // "oddly cooperative": misclassifies the player as a non-target; not aggro on sight, but the player may choose to strike its assets (Phase-2 quest can flip this hostile)
        setRel(sector,"exileomicron", Factions.REMNANTS, FRIENDLY);
        setRel(sector,"exileomicron", Factions.SCAVENGERS, INHOSPITABLE);
        setRel(sector,"exileomicron", Factions.DIKTAT, INHOSPITABLE);
        setRel(sector,"exileomicron", Factions.DIKTAT_ACHILLES, NEUTRAL); // Sean 2026-11-14
        setRel(sector,"exileomicron", Factions.THREAT, HOSTILE);
        setRel(sector,"exileomicron", Factions.DWELLER, HOSTILE);
        setRel(sector,"exileomicron", Factions.TRITACHYON, FRIENDLY);
        setRel(sector,"exileomicron", "exilerefugee", INHOSPITABLE);

        setRel(sector,"exilerefugee", Factions.HEGEMONY, INHOSPITABLE);
        setRel(sector,"exilerefugee", Factions.INDEPENDENT, INHOSPITABLE);
        setRel(sector,"exilerefugee", Factions.KOL, NEUTRAL);
        setRel(sector,"exilerefugee", Factions.LIONS_GUARD, HOSTILE);
        setRel(sector,"exilerefugee", Factions.LUDDIC_CHURCH, INHOSPITABLE);
        setRel(sector,"exilerefugee", Factions.LUDDIC_PATH, INHOSPITABLE);
        setRel(sector,"exilerefugee", Factions.MERCENARY, NEUTRAL);
        setRel(sector,"exilerefugee", Factions.PERSEAN, INHOSPITABLE);
        setRel(sector,"exilerefugee", Factions.PIRATES, HOSTILE);
        setRel(sector,"exilerefugee", Factions.PLAYER, SUSPICIOUS);
        setRel(sector,"exilerefugee", Factions.REMNANTS, HOSTILE);
        setRel(sector,"exilerefugee", Factions.SCAVENGERS, SUSPICIOUS);
        setRel(sector,"exilerefugee", Factions.DIKTAT, INHOSPITABLE); // Sean 2026-10-07; Lion's Guard stays HOSTILE
        setRel(sector,"exilerefugee", Factions.DIKTAT_ACHILLES, NEUTRAL); // Sean 2026-11-14
        setRel(sector,"exilerefugee", Factions.THREAT, HOSTILE);
        setRel(sector,"exilerefugee", Factions.DWELLER, HOSTILE);
        setRel(sector,"exilerefugee", Factions.TRITACHYON, INHOSPITABLE);
     }
}
