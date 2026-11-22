package data.scripts.exiles;

import java.util.ArrayList;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PersonImportance;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import org.lwjgl.util.vector.Vector2f;
import java.util.Random;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.impl.campaign.procgen.DefenderDataOverride;
import com.fs.starfarer.api.util.Misc;
import data.scripts.world.ExilesSectorGen;
import data.scripts.rulecmd.ExileAddStartingScript;
import com.fs.starfarer.api.campaign.SpecialItemData;

public class ExilesModPlugin extends BaseModPlugin {

    /*
     =========================
     Persistent keys
     =========================
    */
    private static final String START_SETUP_DONE = "$exiles_start_setup_done";
    private static final String NACHIKETA_PATCH_DONE = "$exiles_nachiketa_patch_done";
    private static final String GILEAD_PATCH_DONE = "$exiles_gilead_patch_done_v2";
    private static final String TARTESSUS_PATCH_DONE = "$exiles_tartessus_patch_done";
    private static final String ASHER_PATCH_DONE = "$exiles_asher_patch_done_v2";
    private static final String HYBRASIL_MIRRORS_DONE = "$exiles_hybrasil_mirrors_done_v1";
    private static final String JANGALA_PATCH_DONE = "$exiles_jangala_patch_done";
    private static final String HESPERUS_PATCH_DONE = "$exiles_hesperus_patch_done";
    private static final String KAZERON_PATCH_DONE = "$exiles_kazeron_patch_done";
    private static final String KAPTEYN_PATCH_DONE = "$exiles_kapteyn_patch_done";
    private static final String MAXIOS_PATCH_DONE = "$exiles_maxios_patch_done";
    private static final String SPHINX_PATCH_DONE = "$exiles_sphinx_patch_done";
    private static final String VOLTURN_PATCH_DONE = "$exiles_volturn_patch_done";
    private static final String CUNNINGHAM_SETUP_DONE = "$exiles_cunningham_setup_done";
    private static final String XECHAS_COLONIES_DONE = "$exiles_xechas_colonies_done";
    private static final String REFUGEE_STATIONS_DONE = "$exiles_refugee_stations_done_v2";
    private static final String REFUGEE_STRAIN_DONE = "$exiles_refugee_strain_done";
    private static final String BLACKFLEET_SETUP_DONE = "$exilebf_setup_done";

    /*
     =========================
     New game lifecycle hooks
     =========================
    */

    @Override
    public void onNewGame() {
        registerOmicronCorePlugins();
        generateSector();
     }

    @Override
    public void onNewGameAfterProcGen() {
        // Deferred custom systems: generated here (AFTER vanilla proc-gen) so they dodge the
        // constellation keep-out "spotlight" gap, but still BEFORE economy load so the market
        // systems bind. Placement is config-driven (data/config/exiles/system_placement.json).
        // Xechas is the sole exception -- it stays in onNewGame() as the player-start anchor.
        new ExilesSectorGen().generateDeferredSystems(Global.getSector());
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        applyEconomyPatches();
        ExileMidanEconomyNetwork.startNewGame();
        ensureEmergencyMeasuresListener();

        Global.getLogger(ExilesModPlugin.class).info("DEBUG: onNewGameAfterEconomyLoad reached");

        // Phase-1 Omicron Director (persistent, saved with the game). Added once here on a new game;
        // the hasScript guard in onGameLoad keeps it from being duplicated on reload.
        Global.getSector().addScript(new OmicronDirector());
        Global.getLogger(ExilesModPlugin.class).info("DEBUG: OmicronDirector added");

        // Tri-Tachyon protection mandate: four station-only complements, Culann's large
        // strategic detachment, and travel-time-dependent mature-Nexus fallback responses.
        OmicronTriTachyonDefenseManager.ensure();

        // Black Fleet anchor watcher (Mourad relocation + temp-base spigot close). Persistent;
        // the hasScript guard in onGameLoad keeps it from duplicating on reload.
        Global.getSector().addScript(new ExileBlackFleetLifecycle());

        // Black Fleet raid launcher (vanilla pirate-raid pipeline from the anchors; config
        // cadence + start-delay grace). Persistent; guarded in onGameLoad.
        Global.getSector().addScript(new BlackFleetRaidManager());

        // Black Fleet extortion racket (disguised collectors -> Price Spikes; spec 2026-07-09,
        // built 2026-07-11). Persistent; guarded in onGameLoad.
        Global.getSector().addScript(new ExileBlackFleetExtortionManager());

        // Phase-1 refugee convoys: bounded civilian fleets (maxActiveConvoys), temporary Refugee
        // Encampments on arrival, possible early departure ordered by local spaceport control.
        Global.getSector().addScript(new ExileRefugeeConvoyManager());

        // Atmospheric Refugee salvage traffic: directly spawned expeditions, scanned daily and
        // capped per system and globally (comments corrected 2026-10-06, bug sweep REF-10).
        Global.getSector().addScript(new ExileRefugeeSalvageExpeditionManager());

        // Ashen Crusade war clock (Pass 1 of docs/EXILES_ASHEN_CRUSADE_WAR.md). Runs the
        // four-phase state machine and the war score, and drives sabotage (Pass 2).
        // Persistent; guarded in onGameLoad.
        Global.getSector().addScript(new AshenWarDirector());

        // Ashen relief convoys (Pass 3): Phase-1 aid runs to the refugee enclaves, which are
        // also the Crusade's recruiting grounds. The only organic source of war score.
        Global.getSector().addScript(new AshenReliefManager());

        // Ashen interdiction raids (Pass 4): Phase-2/3 presence ops against Tri-Tachyon
        // systems. Persistent; guarded in onGameLoad.
        Global.getSector().addScript(new AshenInterdictionManager());

        // Hegemony-Persean League war director (Pass 1 of docs/EXILES_HEG_LEAGUE_WAR.md).
        // The FOURTH SIBLING on the vanilla RaidIntel pipeline -- it shares utilities with the
        // Ashen/BF/Omicron directors and knows nothing about them. It runs the Tension meter and
        // the six-state crisis cycle, and owns the switch-gated, reversible Nexerelin premise
        // pins (spec sec 7.2, R10/R11), which it applies once per load. Persistent; guarded in
        // onGameLoad.
        Global.getSector().addScript(new ExileHegLeagueDirector());

        // Heg-League interdiction raids (Pass 2): presence ops during the director's TENSION and
        // ESCALATION states. It READS the director's state and owns the config nowhere -- the
        // director is the single config holder. Persistent; guarded in onGameLoad.
        Global.getSector().addScript(new ExileHegLeagueInterdictionManager());

        // Heg-League surgical strikes (Pass 3): the RETALIATION state's ONE punitive op against
        // the cycle's offender, plus ESCALATION counter-strikes. Like the interdiction manager it
        // READS the director's state and owns no config of its own. Persistent; guarded in
        // onGameLoad.
        Global.getSector().addScript(new ExileHegLeagueStrikeManager());

        // Heg-League system blockades (Pass 4): the ESCALATION state's signature op -- a whole
        // system's jump points held for weeks, against the author's whitelist of acceptable war
        // zones. Like its two siblings it READS the director's state and owns no config of its
        // own. Persistent; guarded in onGameLoad.
        Global.getSector().addScript(new ExileHegLeagueBlockadeManager());

        // Heg-League recovery convoys (Pass 6b): the victim's answer to a landed surgical strike
        // -- a civilian relief run that drips the extended disruption back down to the
        // design-normal figure. It is the half of surgicalStrike.extendedDisruptMult that makes
        // raising it above 1.0 legitimate. Like its siblings it READS the director's config and
        // owns none of its own. The SQUEEZE manager (Pass 6a) is deliberately NOT registered: it
        // is stateless plumbing called by the strike manager's retaliation path, not a script.
        // Persistent; guarded in onGameLoad.
        Global.getSector().addScript(new ExileHegLeagueRecoveryManager());
    }

    @Override
    public void onNewGameAfterTimePass() {
        runPostTimePassSetup();
        ExileBlackFleetProsperity.ensure();
        ExileStartingDebt.initialize();
    }

    /*
     =========================
     Save/load lifecycle hooks
     =========================
    */

    @Override
    public void onGameLoad(boolean newGame) {
        // The network starts only in new campaigns. A saved manager means this campaign
        // already opted in; repair its market membership immediately after loading.
        if (Global.getSector().hasScript(ExileMidanEconomyNetwork.class)) {
            ExileMidanEconomyNetwork.syncMarkets();
        }
        ExileBlackFleetProsperity.ensure();
        ExileStartingDebt.restore();
        registerOmicronCorePlugins();
        applyLoadSafeFixes();
        // 2026-09-22: strip stat modifiers orphaned by pre-fix transient condition plugins
        // (sec 14.12.3 of the Heg-League doc). Cheap, once per load, logs only when it acts.
        ExileConditionModifierHeal.run();
        postStoryAnnouncementTestIfPinned();
        // Persistent Director restores itself from the save; only re-add if missing (avoids duplicates).
        if (!Global.getSector().hasScript(OmicronDirector.class)) {
            Global.getSector().addScript(new OmicronDirector());
        }

        // Black Fleet lifecycle: restores from save; re-add if missing (save-compatible on
        // existing games). Also re-asserts the closed temp-base spigot after each launch.
        if (!Global.getSector().hasScript(ExileBlackFleetLifecycle.class)) {
            Global.getSector().addScript(new ExileBlackFleetLifecycle());
        }

        // Black Fleet raid launcher: restores from save; re-add if missing.
        if (!Global.getSector().hasScript(BlackFleetRaidManager.class)) {
            Global.getSector().addScript(new BlackFleetRaidManager());
        }

        // Black Fleet extortion racket: restores from save; re-add if missing
        // (save-compatible on existing games — starts fresh at the config cadence).
        if (!Global.getSector().hasScript(ExileBlackFleetExtortionManager.class)) {
            Global.getSector().addScript(new ExileBlackFleetExtortionManager());
        }

        // Refugee convoy manager: restores from save; re-add if missing so the prototype
        // is also testable in an existing development campaign.
        if (!Global.getSector().hasScript(ExileRefugeeConvoyManager.class)) {
            Global.getSector().addScript(new ExileRefugeeConvoyManager());
        }

        // Refugee salvage expeditions: restores from save; re-add if missing so version one is
        // immediately testable in an existing development campaign.
        if (!Global.getSector().hasScript(ExileRefugeeSalvageExpeditionManager.class)) {
            Global.getSector().addScript(new ExileRefugeeSalvageExpeditionManager());
        }

        // Ashen war clock: restores from save; re-add if missing so it is testable in an
        // existing development campaign. Config is transient, so timeScale and cadence
        // edits land on restart.
        if (!Global.getSector().hasScript(AshenWarDirector.class)) {
            Global.getSector().addScript(new AshenWarDirector());
        }

        // Ashen relief convoys: restore from save; re-add if missing.
        if (!Global.getSector().hasScript(AshenReliefManager.class)) {
            Global.getSector().addScript(new AshenReliefManager());
        }

        // Ashen interdiction raids: restore from save; re-add if missing.
        if (!Global.getSector().hasScript(AshenInterdictionManager.class)) {
            Global.getSector().addScript(new AshenInterdictionManager());
        }

        // Heg-League war director: restores from save; re-add if missing so Pass 1 is testable
        // in an existing development campaign. Its config is transient, so timeScale, cadence
        // and the nex{} player switches are re-read on every restart -- which is also when the
        // Nexerelin layers are applied or REMOVED (switch off + our key present -> removed).
        if (!Global.getSector().hasScript(ExileHegLeagueDirector.class)) {
            Global.getSector().addScript(new ExileHegLeagueDirector());
        }

        // Heg-League interdiction raids (Pass 2): restore from save; re-add if missing, so an
        // existing Pass-1 development campaign picks the manager up without a new game.
        if (!Global.getSector().hasScript(ExileHegLeagueInterdictionManager.class)) {
            Global.getSector().addScript(new ExileHegLeagueInterdictionManager());
        }

        // Heg-League surgical strikes (Pass 3): restore from save; re-add if missing, so an
        // existing Pass-1/2 development campaign picks the manager up without a new game.
        if (!Global.getSector().hasScript(ExileHegLeagueStrikeManager.class)) {
            Global.getSector().addScript(new ExileHegLeagueStrikeManager());
        }

        // Heg-League system blockades (Pass 4): restore from save; re-add if missing, so an
        // existing Pass-1/2/3 development campaign picks the manager up without a new game.
        if (!Global.getSector().hasScript(ExileHegLeagueBlockadeManager.class)) {
            Global.getSector().addScript(new ExileHegLeagueBlockadeManager());
        }

        // Heg-League recovery convoys (Pass 6b): restore from save; re-add if missing, so an
        // existing Pass-1/2/3/4/5 development campaign picks the manager up without a new game.
        // It simply starts with an empty ledger -- wounds struck before the upgrade get no
        // relief, which is correct and needs no migration (decision D80.9).
        if (!Global.getSector().hasScript(ExileHegLeagueRecoveryManager.class)) {
            Global.getSector().addScript(new ExileHegLeagueRecoveryManager());
        }

        // Heg-League incident telegraph heal (2026-08-28 live-test fix): builds before the fix
        // posted the intel WITHOUT adding it as a script, so its timers never ticked -- no
        // confirmation, no expiry. New intel adds itself in its constructor and that script
        // reference persists in the save; this sweep only re-attaches stranded instances from
        // saves made with the bugged build. Idempotent: skips anything already in the list.
        for (Object o : Global.getSector().getIntelManager()
                .getIntel(ExileHegLeagueIncidentIntel.class)) {
            ExileHegLeagueIncidentIntel incident = (ExileHegLeagueIncidentIntel) o;
            if (!incident.isEnded() && !Global.getSector().getScripts().contains(incident)) {
                Global.getSector().addScript(incident);
            }
        }

        // Heg-League Pass-5 intel heal: the same defensive sweep as the incident telegraph above.
        // Both classes add themselves as scripts in their constructors and that reference persists
        // in the save, so this should never find anything -- it exists so a stranded beat intel
        // (an interrupted save, a future posting path that forgets the line) still ticks and still
        // expires rather than sitting on the intel board forever. Idempotent.
        for (Object o : Global.getSector().getIntelManager()
                .getIntel(ExileHegLeagueCeasefireIntel.class)) {
            ExileHegLeagueCeasefireIntel ceasefire = (ExileHegLeagueCeasefireIntel) o;
            if (!ceasefire.isEnded() && !Global.getSector().getScripts().contains(ceasefire)) {
                Global.getSector().addScript(ceasefire);
            }
        }
        for (Object o : Global.getSector().getIntelManager()
                .getIntel(ExileHegLeagueEngagementIntel.class)) {
            ExileHegLeagueEngagementIntel engagement = (ExileHegLeagueEngagementIntel) o;
            if (!engagement.isEnded() && !Global.getSector().getScripts().contains(engagement)) {
                Global.getSector().addScript(engagement);
            }
        }

        // Home-defense gauntlet: attach per-station defender managers to tagged Omicron
        // facilities missing one (save-compatible on existing games) and re-apply config
        // tiers to managers restored from the save, so tuning edits land on restart.
        OmicronDefenseFleetManager.ensureManagers(OmicronDirectorConfig.load());

        // Save-compatible: adopts any existing dormant complements or adds the manager when
        // this feature is introduced into an older campaign.
        OmicronTriTachyonDefenseManager.ensure();

        // Removes decivilized_subpop when a Processing-Center colony grows (event-driven, no polling).
        if (!Global.getSector().getListenerManager().hasListenerOfClass(ExileRefugeeIntegrationListener.class)) {
            Global.getSector().getListenerManager().addListener(new ExileRefugeeIntegrationListener(), true);
        }
        ensureEmergencyMeasuresListener();

        applyRefugeeColonyMusic();
        applyArrayDefenderOverrides();
        applyRefugeeConquestShield();

    }

    /** Event-only player attribution for Emergency Measures; transient and idempotent on every load. */
    private void ensureEmergencyMeasuresListener() {
        if (!Global.getSector().getListenerManager().hasListenerOfClass(
                ExileEmergencyMeasuresPlayerActionListener.class)) {
            Global.getSector().getListenerManager().addListener(
                    new ExileEmergencyMeasuresPlayerActionListener(), true);
        }
    }

    /**
     * Both registrations use vanilla extension points and are transient: no per-frame script,
     * no serialized plugin instance, and no effect on non-Omicron fleets. Re-register the
     * campaign picker each load; guard the generic fleet generator against duplicates.
     */
    private void registerOmicronCorePlugins() {
        Global.getSector().unregisterPlugin(ExileOmicronCoreIds.CAMPAIGN_PLUGIN_ID);
        Global.getSector().registerPlugin(new ExileOmicronCampaignPlugin());

        if (!Global.getSector().getGenericPlugins().hasPlugin(ExileOmicronFleetOfficerGenerator.class)) {
            Global.getSector().getGenericPlugins().addPlugin(
                    new ExileOmicronFleetOfficerGenerator(), true);
        }
    }

    /** Array slice v2 (author rescope 2026-07-06, Domain survey-ship pattern): the Sensor/
     *  Comm buoys get DIALOG-LAYER drone defenders — no campaign fleets. The defender data
     *  lives on the entity (DefenderDataOverride, staged by vanilla SalvageGenFromSeed);
     *  mod rules.csv rows gate the objective dialog behind the fight and reopen it for ~30
     *  days after a victory ($defenderFleetDefeated re-set with expiry). Idempotent, every
     *  load: also back-tags the buoys for existing saves. Strength from config defense
     *  sensor/comm tiers (fpMin/fpMax, author: 50-70). */
    private void applyArrayDefenderOverrides() {
        OmicronDirectorConfig config = OmicronDirectorConfig.load();
        // Back-tag Prime's buoys by entity id (pre-spread saves lack the tags); the new
        // cluster systems tag their arrays at generation.
        backTagArray("exileomicron_sensor_array", OmicronDirectorConfig.ARRAY_TAG_SENSOR);
        backTagArray("exileomicron_comm_array", OmicronDirectorConfig.ARRAY_TAG_COMM);
        // Spread build: apply the defender override to EVERY tagged array, sector-wide —
        // Prime, Cipher, Vigil (and any future system) all ride the same rules.csv rows.
        java.util.List systems = Global.getSector().getStarSystems();
        for (int i = 0; i < systems.size(); i++) {
            com.fs.starfarer.api.campaign.StarSystemAPI system =
                    (com.fs.starfarer.api.campaign.StarSystemAPI) systems.get(i);
            java.util.List tagged = system.getEntitiesWithTag(OmicronDirectorConfig.ARRAY_TAG);
            for (int j = 0; j < tagged.size(); j++) {
                SectorEntityToken e = (SectorEntityToken) tagged.get(j);
                if (e.hasTag(OmicronDirectorConfig.ARRAY_TAG_SENSOR)) {
                    applyArrayDefender(e, OmicronDirectorConfig.ARRAY_TAG_SENSOR, config.defenseSensor);
                } else if (e.hasTag(OmicronDirectorConfig.ARRAY_TAG_COMM)) {
                    applyArrayDefender(e, OmicronDirectorConfig.ARRAY_TAG_COMM, config.defenseComm);
                }
            }
        }
    }

    private void backTagArray(String entityId, String specificTag) {
        SectorEntityToken e = Global.getSector().getEntityById(entityId);
        if (e == null) return;
        if (!e.hasTag(OmicronDirectorConfig.ARRAY_TAG)) e.addTag(OmicronDirectorConfig.ARRAY_TAG);
        if (!e.hasTag(specificTag)) e.addTag(specificTag);
    }

    private void applyArrayDefender(SectorEntityToken e, String specificTag,
                                    OmicronDirectorConfig.DefenseTier tier) {
        if (e == null || tier == null) return;
        if (!e.hasTag(OmicronDirectorConfig.ARRAY_TAG)) e.addTag(OmicronDirectorConfig.ARRAY_TAG);
        if (!e.hasTag(specificTag)) e.addTag(specificTag);
        // The buoy entity types have no salvage-gen spec; borrow technology_cache (remnant
        // defenders, defQuality 1) so SalvageGenFromSeed has sane defaults — faction, prob,
        // and strength all come from OUR override anyway.
        e.getMemoryWithoutUpdate().set(
                com.fs.starfarer.api.impl.campaign.ids.MemFlags.SALVAGE_SPEC_ID_OVERRIDE,
                "technology_cache");
        if (!e.getMemoryWithoutUpdate().contains(
                com.fs.starfarer.api.impl.campaign.ids.MemFlags.SALVAGE_SEED)) {
            e.getMemoryWithoutUpdate().set(
                    com.fs.starfarer.api.impl.campaign.ids.MemFlags.SALVAGE_SEED,
                    Misc.genRandomSeed());
        }
        // maxDefenderSize 2 (destroyer cap, author 2026-07-06): the screen must read as a
        // screen of small craft regardless of the strength budget — 50-70 bought a cruiser.
        Misc.setDefenderOverride(e, new DefenderDataOverride(
                OmicronDirector.OMICRON_FACTION, 1f, tier.fpMin, tier.fpMax, 2));
    }

    /** Nexerelin conquest shield (author ruling 2026-07-06: REFUGEE COLONIES ONLY — Midan/
     *  Ashen homes deliberately left conquerable as sector chaos). Nex honors the
     *  $nex_uninvadable market memory key; unplayable factions never counter-invade or
     *  respawn, so unshielded refugee colonies would be silently ground out of the campaign
     *  — and refugee elimination kills the Phase-2 trust questline. Zero Nex dependency:
     *  vanilla ignores unknown memory keys. Idempotent, every load. */
    private void applyRefugeeConquestShield() {
        String[] ids = new String[] {
                "corvus_abandoned_station", "mairaath_abandoned_station1",
                "yma_abandoned_station", "maxios"
        };
        for (int i = 0; i < ids.length; i++) {
            MarketAPI market = Global.getSector().getEconomy().getMarket(ids[i]);
            if (market != null) {
                market.getMemoryWithoutUpdate().set("$nex_uninvadable", true);
            }
        }
    }

    /** Per-colony docking-music flavor (author ruling 2026-07-06): Corvus platform = Luddic
     *  hymns — the main refuge of the stronger adherents of the old faith (deliberately
     *  rhymes with Ashen: two branches of the same broken tree); Mairaath + Yma enclaves =
     *  the drifting salvage theme (their scrapper economy set to music); Maxios = faction
     *  default, no override. Uses the vanilla $musicSetId override (market + primary entity
     *  memory, the same key vanilla missions/TT Black Site set). Idempotent, runs every
     *  load so it reaches existing saves and future tuning edits land on restart. May be
     *  replaced by custom AI-generated music later. */
    private void applyRefugeeColonyMusic() {
        setMarketMusic("corvus_abandoned_station", "music_luddite_market_neutral");
        setMarketMusic("mairaath_abandoned_station1", "music_survey_and_scavenge");
        setMarketMusic("yma_abandoned_station", "music_survey_and_scavenge");
        // "maxios" stays on the faction default on purpose.
    }

    private void setMarketMusic(String marketId, String musicSetId) {
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) return;
        market.getMemoryWithoutUpdate().set(
                com.fs.starfarer.api.impl.MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, musicSetId);
        if (market.getPrimaryEntity() != null) {
            market.getPrimaryEntity().getMemoryWithoutUpdate().set(
                    com.fs.starfarer.api.impl.MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, musicSetId);
        }
    }

    /*
     =========================
     Sector generation
     =========================
    */

    private void generateSector() {
        new ExilesSectorGen().generateHomeSystems(Global.getSector());
    }


    /*
     =========================
     Economy/world patches
     =========================
    */

    private void applyEconomyPatches() {
        applyNachiketaChangesOnce();
        applyGileadChangesOnce();
        applyTartessusChangesOnce();
        applyAsherChangesOnce();
        applyHybrasilMirrorDamageOnce();
        applyJangalaChangesOnce();
        applyHesperusChangesOnce();
        applyKazeronChangesOnce();
        applyKapteynChangesOnce();
        applyMaxiosChangesOnce();
        applySphinxChangesOnce();
        applyVolturnChangesOnce();
        applyCunninghamSetupOnce();
        applyRefugeeStationSquattersOnce();
        applyRefugeeStrainColoniesOnce();
        applyBlackFleetSetupOnce();
        setupXechasStartingColoniesOnce();
    }

    // Post-econ-load Black Fleet setup (split build 2026-07-07; econ-JSON refactor 2026-07-08:
    // the anchor MARKETS are declared vanilla-style in data/campaign/econ/sallee.json +
    // lundy.json, so Mourad can only be seated once the economy exists).
    private void applyBlackFleetSetupOnce() {
        if (Global.getSector() == null) return;
        if (Global.getSector().getPersistentData().containsKey(BLACKFLEET_SETUP_DONE)) return;

        MarketAPI sallee = findMarketByPrimaryEntityId("exilebf_sallee_station");
        if (sallee == null) return; // pre-split save: no anchors, nothing to seat Mourad on

        // Stable person id — LOAD-BEARING for Phase-2 (Tapper-reveal + recruitment arcs).
        // HIDDEN NPC (author ruling 2026-07-08, Arroyo-pattern): Mourad exists in the
        // ImportantPeople registry from game start but is NOT seated in any comm directory or
        // admin post — the player cannot meet him until a quest surfaces him. The stations'
        // public faces are the auto-generated Warboss/Portboss/Quartermaster. His portrait is
        // registered in data/config/settings.json (graphics/characters) so it renders when a
        // quest does surface him. $exilebf_mourad_at tracks which anchor he is "at" for the
        // lifecycle script and future quests.
        PersonAPI mourad = Global.getFactory().createPerson();
        mourad.setId("exile_mourad");
        mourad.setFaction("exileblackfleet");
        mourad.setGender(FullName.Gender.MALE);
        mourad.setName(new FullName("Mourad", "", FullName.Gender.MALE));
        mourad.setRankId(Ranks.SPACE_ADMIRAL);
        mourad.setPostId(Ranks.POST_STATION_COMMANDER); // renders as "Station King" via faction posts
        mourad.setPortraitSprite(Global.getSettings().getSpriteName("characters", "exile_mourad"));
        Global.getSector().getImportantPeople().addPerson(mourad);
        Global.getSector().getMemoryWithoutUpdate().set("$exilebf_mourad_at", "sallee");

        Global.getSector().getPersistentData().put(BLACKFLEET_SETUP_DONE, true);
    }


    private void applyNachiketaChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(NACHIKETA_PATCH_DONE)) {
            return;
        }

        MarketAPI nachiketaMarket = findMarketByPrimaryEntityId("nachiketa");
        if (nachiketaMarket == null) return;

        nachiketaMarket.setFreePort(true);
        nachiketaMarket.setSize(4);

        if (nachiketaMarket.hasCondition("population_5")) {
            nachiketaMarket.removeCondition("population_5");
        }

        if (!nachiketaMarket.hasCondition("population_4")) {
            nachiketaMarket.addCondition("population_4");
        }

        if (!nachiketaMarket.hasCondition("decivilized_subpop")) {
            nachiketaMarket.addCondition("decivilized_subpop");
        }

        if (!nachiketaMarket.hasCondition("irradiated")) {
            nachiketaMarket.addCondition("irradiated");
        }

        Industry fuel = nachiketaMarket.getIndustry("fuelprod");
        if (fuel != null) {
            fuel.setSpecialItem(null);
        }

        if (nachiketaMarket.hasIndustry("battlestation_mid")) {
            nachiketaMarket.removeIndustry("battlestation_mid", null, false);
        }

        if (!nachiketaMarket.hasIndustry("orbitalstation")) {
            nachiketaMarket.addIndustry("orbitalstation");
        }

        nachiketaMarket.reapplyConditions();
        nachiketaMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(NACHIKETA_PATCH_DONE, true);
    }

    private void applyGileadChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(GILEAD_PATCH_DONE)) {
            return;
        }


        MarketAPI gileadMarket = findMarketByPrimaryEntityId("gilead");
        if (gileadMarket == null) return;

        gileadMarket.setSize(6);

        if (gileadMarket.hasCondition("population_7")) {
            gileadMarket.removeCondition("population_7");
        }

        if (!gileadMarket.hasCondition("population_6")) {
            gileadMarket.addCondition("population_6");
        }

        if (!gileadMarket.hasCondition("exile_luddic_unrest")) {
            gileadMarket.addCondition("exile_luddic_unrest");
        }

        if (!gileadMarket.hasCondition("pollution")) {
            gileadMarket.addCondition("pollution");
        }

        if (gileadMarket.hasCondition("mild_climate")) {
            gileadMarket.removeCondition("mild_climate");
        }

        if (gileadMarket.hasCondition("farmland_rich")) {
            gileadMarket.removeCondition("farmland_rich");
        }

        if (!gileadMarket.hasCondition("exile_corrupted_soil")) {
            gileadMarket.addCondition("exile_corrupted_soil");
        }


        if (gileadMarket.hasIndustry("patrolhq")) {
            gileadMarket.removeIndustry("patrolhq", null, false);
        }

        if (!gileadMarket.hasIndustry("exile_kol_garrison")) {
            gileadMarket.addIndustry("exile_kol_garrison");
        }

        if (gileadMarket.hasIndustry("farming")) {
            gileadMarket.removeIndustry("farming", null, false);
        }

        if (!gileadMarket.hasIndustry("exile_gilead_farming")) {
            gileadMarket.addIndustry("exile_gilead_farming");
        }

        if (gileadMarket.hasIndustry("starfortress")) {
            gileadMarket.removeIndustry("starfortress", null, false);
        }
        if (gileadMarket.hasIndustry("orbitalstation")) {
            gileadMarket.removeIndustry("orbitalstation", null, false);
        }

        if (!gileadMarket.hasIndustry("battlestation")) {
            gileadMarket.addIndustry("battlestation");
        }

        gileadMarket.reapplyConditions();
        gileadMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(GILEAD_PATCH_DONE, true);
    }

    private void applyTartessusChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(TARTESSUS_PATCH_DONE)) {
            return;
        }

        MarketAPI tartessusMarket = findMarketByPrimaryEntityId("tartessus");
        if (tartessusMarket == null) return;

        if (!tartessusMarket.hasCondition("exile_luddic_unrest")) {
            tartessusMarket.addCondition("exile_luddic_unrest");
        }
        if (!tartessusMarket.hasCondition("exile_refugee_strain")) {
            tartessusMarket.addCondition("exile_refugee_strain");
        }

        tartessusMarket.reapplyConditions();
        tartessusMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(TARTESSUS_PATCH_DONE, true);
    }

    private void applyAsherChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(ASHER_PATCH_DONE)) {
            return;
        }

        MarketAPI asherMarket = findMarketByPrimaryEntityId("asher");
        if (asherMarket == null) return;

        if (!asherMarket.hasCondition("exile_luddic_unrest")) {
            asherMarket.addCondition("exile_luddic_unrest");
        }

        if (!asherMarket.hasIndustry("patrolhq")) {
            asherMarket.addIndustry("patrolhq");
        }

        if (asherMarket.hasIndustry("orbitalstation")) {
            asherMarket.removeIndustry("orbitalstation", null, false);
        }

        if (!asherMarket.hasIndustry("battlestation")) {
            asherMarket.addIndustry("battlestation");
        }

        asherMarket.reapplyConditions();
        asherMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(ASHER_PATCH_DONE, true);
    }

    /**
     * PASS 6d -- the scar of the repulsed Knights of Gilead assault on Hybrasil, ~Cycle 204
     * (docs/EXILES_ASHEN_CRUSADE_WAR.md 6.4b and 14.10).
     *
     * <p>The Crusade already tried the direct approach against Tri-Tachyon's capital and was thrown
     * back, though not before doing lasting damage. That failure is <i>why</i> the war clock is a
     * patient countdown rather than another charge: it is the reason the Crusade now rehearses.
     * This makes it visible from the first time the player flies through Hybrasil.
     *
     * <p><b>Author ruling 2026-08-10.</b> Of Eochu Bres's three stellar mirrors: one is destroyed
     * outright and replaced with a small, poor debris field; one is knocked off its station -- moved
     * outward and no longer pointing at the planet; one is left untouched. All remain Tri-Tachyon:
     * they held the field, and the surviving array is still theirs.
     *
     * <p><b>Both halves are load-bearing.</b> Removing the {@code solar_array} condition without
     * touching the mirrors is invisible -- three intact mirrors and an unexplained farming penalty.
     * Wrecking the mirrors without removing the condition is the reverse: visible damage with no
     * consequence, and a bonus still flowing from scenery that is gone.
     *
     * <p>Economic effect [VERIFIED]: {@code SolarArray} is a market CONDITION, not an industry, and
     * carries {@code FARMING_BONUS = 2} flat plus suppression of {@code HOT}/{@code POOR_LIGHT}. So
     * this is a real cut to Tri-Tachyon food production on a size-6 world that is already
     * {@code farmland_poor} -- which FEEDS the planned "general food shortage" feature (CLAUDE.md
     * 6) rather than fighting it.
     */
    private void applyHybrasilMirrorDamageOnce() {
        if (Global.getSector() == null) return;
        if (Global.getSector().getPersistentData().containsKey(HYBRASIL_MIRRORS_DONE)) return;

        MarketAPI eochuBres = findMarketByPrimaryEntityId("eochu_bres");
        if (eochuBres == null) return;

        StarSystemAPI hybrasil = null;
        if (eochuBres.getPrimaryEntity() != null
                && eochuBres.getPrimaryEntity().getContainingLocation() instanceof StarSystemAPI) {
            hybrasil = (StarSystemAPI) eochuBres.getPrimaryEntity().getContainingLocation();
        }
        if (hybrasil == null) return;

        // 1. The array no longer works. Condition, not industry -- see the javadoc.
        if (eochuBres.hasCondition("solar_array")) {
            eochuBres.removeCondition("solar_array");
            eochuBres.reapplyConditions();
        }

        SectorEntityToken planet = hybrasil.getEntityById("eochu_bres");
        SectorEntityToken destroyed = hybrasil.getEntityById("eochu_bres_mirror3");
        SectorEntityToken displaced = hybrasil.getEntityById("eochu_bres_mirror2");

        // 2. One mirror knocked off station. Vanilla puts all three on
        //    setCircularOrbitPointingDown, which keeps a mirror permanently facing the planet --
        //    that IS the working configuration. Switching to plain setCircularOrbit means it holds
        //    its own facing instead of tracking, so it visibly no longer aims anywhere useful, and
        //    the wider, slower orbit reads as something that was knocked outward and left.
        if (displaced != null && planet != null) {
            displaced.setCircularOrbit(planet, 155f, 340f, 70f);

            // Its own description, so the wreck can say what happened to it.
            //
            // [VERIFIED 2026-08-11] setCustomDescriptionId is per-ENTITY (SectorEntityToken), not
            // per-type: vanilla's generator merely sets every mirror to the same id, and consumers
            // read Global.getSettings().getDescription(entity.getCustomDescriptionId(), CUSTOM).
            // So no new custom entity type is needed to give this one unique text -- one setter and
            // one descriptions.csv row.
            //
            // *** The text is a PLACEHOLDER awaiting Sean's prose *** -- it is player-facing, and
            // this site explains the whole war clock. The INTACT mirror deliberately keeps vanilla's
            // stellar_mirror text: nothing happened to it.
            displaced.setCustomDescriptionId("exiles_eochu_bres_wrecked_mirror");
        }

        // 3. One mirror gone, with a small poor debris field where it was.
        //    Deliberately thin: salvage is free loot, and CLAUDE.md pillar 2 is that the player
        //    starts behind. This is scenery that explains itself, not a reward.
        if (destroyed != null) {
            Vector2f where = new Vector2f(destroyed.getLocation());
            hybrasil.removeEntity(destroyed);

            DebrisFieldParams params = new DebrisFieldParams(
                    120f,        // field radius; vanilla warns to stay under 1000 for performance
                    0.35f,       // visual density -- sparse, a few tumbling panels
                    1000000f,    // effectively permanent
                    0f);         // no glowing pieces: this wreck is two years cold
            params.source = DebrisFieldSource.MIXED;
            params.baseSalvageXP = 0;
            params.defenderProb = 0f;

            SectorEntityToken debris = Misc.addDebrisField(hybrasil, params, new Random());
            if (debris != null) {
                debris.setSensorProfile(null);
                debris.setDiscoverable(null);
                debris.setId("exiles_eochu_bres_mirror_wreck");
                if (planet != null) {
                    debris.setCircularOrbit(planet, 240f, 220f, 40f); // the dead mirror's station
                } else {
                    debris.getLocation().set(where);
                }
            }
        }

        Global.getSector().getPersistentData().put(HYBRASIL_MIRRORS_DONE, true);
        Global.getLogger(ExilesModPlugin.class).info(
                "Exiles: Hybrasil mirror damage applied -- solar_array removed from Eochu Bres, "
                        + "mirror3 destroyed, mirror2 displaced, mirror1 intact.");
    }

    private void applyJangalaChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(JANGALA_PATCH_DONE)) {
            return;
        }

        MarketAPI jangalaMarket = findMarketByPrimaryEntityId("jangala");
        if (jangalaMarket == null) return;

        if (!jangalaMarket.hasCondition("exile_luddic_unrest")) {
            jangalaMarket.addCondition("exile_luddic_unrest");
        }
        if (!jangalaMarket.hasCondition("exile_refugee_strain")) {
            jangalaMarket.addCondition("exile_refugee_strain");
        }
        if (!jangalaMarket.hasIndustry("exile_security_center")) {
            jangalaMarket.addIndustry("exile_security_center");
        }

        jangalaMarket.reapplyConditions();
        jangalaMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(JANGALA_PATCH_DONE, true);
    }

    private void applyHesperusChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(HESPERUS_PATCH_DONE)) {
            return;
        }

        MarketAPI hesperusMarket = findMarketByPrimaryEntityId("hesperus");
        if (hesperusMarket == null) return;

        if (hesperusMarket.hasIndustry("militarybase")) {
            hesperusMarket.removeIndustry("militarybase", null, false);
        }
        if (hesperusMarket.hasIndustry("orbitalstation")) {
            hesperusMarket.removeIndustry("orbitalstation", null, false);
        }
        if (!hesperusMarket.hasIndustry("battlestation")) {
            hesperusMarket.addIndustry("battlestation");
        }
        if (!hesperusMarket.hasIndustry("exile_kol_garrison")) {
            hesperusMarket.addIndustry("exile_kol_garrison");
        }

        hesperusMarket.reapplyConditions();
        hesperusMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(HESPERUS_PATCH_DONE, true);
    }

    private void applyKazeronChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(KAZERON_PATCH_DONE)) {
            return;
        }

        MarketAPI kazeronMarket = findMarketByPrimaryEntityId("kazeron");
        if (kazeronMarket == null) return;

        Industry orbitalworks = kazeronMarket.getIndustry("orbitalworks");

        if (orbitalworks != null) {
            orbitalworks.setSpecialItem(
                    new SpecialItemData("corrupted_nanoforge", null)
            );
        }

        kazeronMarket.reapplyConditions();
        kazeronMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(KAZERON_PATCH_DONE, true);
    }

    private void applyKapteynChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(KAPTEYN_PATCH_DONE)) {
            return;
        }

        MarketAPI kapteynMarket = findMarketByPrimaryEntityId("station_kapteyn");
        if (kapteynMarket == null) return;

        Industry orbitalworks = kapteynMarket.getIndustry("orbitalworks");

        if (orbitalworks != null) {
            orbitalworks.setSpecialItem(
                    new SpecialItemData("corrupted_nanoforge", null)
            );
        }

        if (kapteynMarket.hasIndustry("orbitalstation")) {
            kapteynMarket.removeIndustry("orbitalstation", null, false);
        }

        if (!kapteynMarket.hasIndustry("battlestation")) {
            kapteynMarket.addIndustry("battlestation");
        }

        kapteynMarket.reapplyConditions();
        kapteynMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(KAPTEYN_PATCH_DONE, true);
    }

    private void applyMaxiosChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(MAXIOS_PATCH_DONE)) {
            return;
        }

        MarketAPI maxiosMarket = findMarketByPrimaryEntityId("new_maxios");
        if (maxiosMarket == null) return;

        Industry orbitalworks = maxiosMarket.getIndustry("orbitalworks");

        if (orbitalworks != null) {
            orbitalworks.setSpecialItem(
                    new SpecialItemData("corrupted_nanoforge", null)
            );
        }

        if (maxiosMarket.hasIndustry("orbitalstation")) {
            maxiosMarket.removeIndustry("orbitalstation", null, false);
        }

        if (!maxiosMarket.hasIndustry("battlestation")) {
            maxiosMarket.addIndustry("battlestation");
        }

        maxiosMarket.reapplyConditions();
        maxiosMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(MAXIOS_PATCH_DONE, true);
    }

    // Pass 7 step 7a (war doc sec 6.1 rung 2, ruled 2026-10-11): after Nachiketa, Sphinx is the
    // Hegemony's sensitive fuel asset, and a decade of fortification gave it a low-tech battlestation.
    // Vanilla Sphinx has no station, so there is nothing to remove. Midan's rung-2 strike must break
    // this station; the League's squeeze and strike gates now count it too.
    private void applySphinxChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(SPHINX_PATCH_DONE)) {
            return;
        }

        MarketAPI sphinxMarket = findMarketByPrimaryEntityId("sphinx");
        if (sphinxMarket == null) return;

        if (!sphinxMarket.hasIndustry("orbitalstation")
                && !sphinxMarket.hasIndustry("orbitalstation_mid")
                && !sphinxMarket.hasIndustry("orbitalstation_high")
                && !sphinxMarket.hasIndustry("battlestation")
                && !sphinxMarket.hasIndustry("battlestation_mid")
                && !sphinxMarket.hasIndustry("battlestation_high")
                && !sphinxMarket.hasIndustry("starfortress")
                && !sphinxMarket.hasIndustry("starfortress_mid")
                && !sphinxMarket.hasIndustry("starfortress_high")) {
            sphinxMarket.addIndustry("battlestation");
        }

        sphinxMarket.reapplyConditions();
        sphinxMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(SPHINX_PATCH_DONE, true);
    }

    // Pass 7 step 7a (war doc sec 6.1 rung 3, ruled 2026-10-14): the Red Tide, a quiet, permanent
    // food cut on Volturn (Askonia). Masked by Eventide's exports until Midan's rung-3 strike; then it
    // is what makes the Sector's food shortage last. Visible in Volturn's condition list, never
    // announced. The size of the cut lives in ExileRedTide.FOOD_PENALTY.
    private void applyVolturnChangesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(VOLTURN_PATCH_DONE)) {
            return;
        }

        MarketAPI volturnMarket = findMarketByPrimaryEntityId("volturn");
        if (volturnMarket == null) return;

        if (!volturnMarket.hasCondition("exile_red_tide")) {
            volturnMarket.addCondition("exile_red_tide");
        }

        volturnMarket.reapplyConditions();
        volturnMarket.reapplyIndustries();

        Global.getSector().getPersistentData().put(VOLTURN_PATCH_DONE, true);
    }

    // Pass 7 step 7b (war doc sec 6.1, ruled 2026-10-19; details 2026-10-24): Admiral Cunningham,
    // of Eventide's old families, who commands the Hegemony's war against the League
    // (docs/EXILES_LORE_HISTORY.md sec 4). Stable person id -- LOAD-BEARING for Phase 2 (the Kazeron
    // raid, the talks at Eventide, the Meridian cover-up all point at him). HIDDEN, Mourad pattern:
    // in the ImportantPeople registry from game start, but in no comm directory or admin post, so the
    // player cannot meet him until a quest surfaces him. His first public appearance is the vow after
    // Midan's rung-3 strike on Eventide. Rank Admiral (Sean: vanilla's Orcus Rao, stationed next door
    // in Valhalla, is an Admiral too, so nobody asks why Rao doesn't pull rank). First name left blank
    // on purpose. Portrait is a placeholder registered in data/config/settings.json.
    private void applyCunninghamSetupOnce() {
        if (Global.getSector() == null) return;
        if (Global.getSector().getPersistentData().containsKey(CUNNINGHAM_SETUP_DONE)) return;
        if (Global.getSector().getImportantPeople().getPerson("exile_cunningham") != null) {
            Global.getSector().getPersistentData().put(CUNNINGHAM_SETUP_DONE, true);
            return;
        }

        PersonAPI cunningham = Global.getFactory().createPerson();
        cunningham.setId("exile_cunningham");
        cunningham.setFaction(Factions.HEGEMONY);
        cunningham.setGender(FullName.Gender.MALE);
        cunningham.setName(new FullName("", "Cunningham", FullName.Gender.MALE));
        cunningham.setRankId(Ranks.SPACE_ADMIRAL);
        cunningham.setPostId(Ranks.POST_FLEET_COMMANDER);
        cunningham.setPortraitSprite(Global.getSettings().getSpriteName("characters", "exile_cunningham"));
        Global.getSector().getImportantPeople().addPerson(cunningham);

        Global.getSector().getPersistentData().put(CUNNINGHAM_SETUP_DONE, true);
    }

    private void applyRefugeeStationSquattersOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(REFUGEE_STATIONS_DONE)) {
            return;
        }

        // All four refugee colonies now have their MARKETS declared via econ-JSON appends to the vanilla
        // system files (corvus / mayasura / yma / magec.json), each with "faction":"exilerefugee" baked in
        // at econ-load — so they are refugee-owned BEFORE any plugin hook runs, in time for Nexerelin's
        // faction census (reinitLiveFactions). This script only finalizes each physical token (faction +
        // name) and strips the free starter storage. See docs/EXILES_ECONOMY_MAP.md §9.
        convertRefugeeColony("corvus_abandoned_station", "Asharu Refugee Platform");
        convertRefugeeColony("mairaath_abandoned_station1", "Mairaath Refugee Enclave");
        convertRefugeeColony("yma_abandoned_station", "Yma Refugee Enclave");
        convertRefugeeColony("maxios", "Maxios");

        Global.getSector().getPersistentData().put(REFUGEE_STATIONS_DONE, true);
    }

    // The NPC colonies near the refugee enclaves carry Refugee Strain + Security Centers. These are LIVE
    // vanilla markets (asharu / mairaath / port_tse / salamanca / new_maxios), so they CANNOT be declared in
    // our econ JSON -- a same-path append creates a SECOND, conflicting market on the entity and breaks it
    // (empty economy, no admin officer, stability 0, dead condition tooltips). Instead we patch the existing
    // vanilla market here, post-econ-load. (new_maxios is also patched by applyMaxiosChangesOnce; both target
    // the same real market by entity id, so they compose.)
    private void applyRefugeeStrainColoniesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(REFUGEE_STRAIN_DONE)) {
            return;
        }

        String[] strainEntities = new String[] {
                "asharu", "mairaath", "port_tse", "salamanca", "new_maxios"
        };

        for (int i = 0; i < strainEntities.length; i++) {
            MarketAPI market = findMarketByPrimaryEntityId(strainEntities[i]);
            if (market == null) continue;

            if (!market.hasCondition("exile_refugee_strain")) {
                market.addCondition("exile_refugee_strain");
            }
            if (!market.hasIndustry("exile_security_center")) {
                market.addIndustry("exile_security_center");
            }

            market.reapplyConditions();
            market.reapplyIndustries();
        }

        Global.getSector().getPersistentData().put(REFUGEE_STRAIN_DONE, true);
    }

    // Finalizes a refugee colony whose MARKET was declared via econ-JSON append (faction baked in at
    // econ-load). Runs post-econ-load: sets the physical token's faction/name and strips the free starter
    // storage + Hermes. Proven pattern recovered from Exiles_backup_5_30_26.
    private void convertRefugeeColony(String entityId, String displayName) {
        SectorEntityToken entity = Global.getSector().getEntityById(entityId);
        if (entity == null) return;

        entity.setName(displayName);
        entity.setFaction("exilerefugee");

        MarketAPI market = entity.getMarket();
        if (market != null) {
            market.setFactionId("exilerefugee");

            SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            if (storage != null) {
                storage.getCargo().clear();                           // removes the free Hermes
                market.removeSubmarket(Submarkets.SUBMARKET_STORAGE);  // removes free starter storage
            }
        }
    }

    private void setupXechasStartingColoniesOnce() {
        if (Global.getSector() == null) return;

        if (Global.getSector().getPersistentData().containsKey(XECHAS_COLONIES_DONE)) {
            return;
        }

        Global.getSector().getPersistentData().put(XECHAS_COLONIES_DONE, true);
        setupXechasStartingColonies();
    }

    /*
     =========================
     Post-start setup
     =========================
    */

    private void runPostTimePassSetup() {
        if (Global.getSector() == null) return;

        // ExileStartingFines (or ExilesNexStart) marks only the selected Exile start
        // before the opening time pass. Vanilla Wolf and other starts must keep their
        // own starting relationships and officers.
        if (!Global.getSector().getMemoryWithoutUpdate()
                .getBoolean("$exiles_starting_fines_eligible")) return;

        if (Global.getSector().getPersistentData().containsKey(START_SETUP_DONE)) {
            return;
        }

        Global.getSector().getPersistentData().put(START_SETUP_DONE, true);

        ExileAddStartingScript.applyStartingRelations();
        addStartingOfficers();
//		showPlayerFactionSetupIfNeeded();
    }

    /*
     =========================
     Load-safe fixes and scripts
     =========================
    */

    private void applyLoadSafeFixes() {
        applyNachiketaChangesOnce();
        applyRefugeeStationSquattersOnce();
        applyRefugeeStrainColoniesOnce();
    }

    /*
     =========================
     Starting faction setup
     =========================
    */

    private void addStartingOfficers() {
        addOfficer(
                "Horatio",
                "Khuttie",
                FullName.Gender.MALE,
                "graphics/portraits/characters/khuttie.png",
                Personalities.AGGRESSIVE,
                Skills.ORDNANCE_EXPERTISE
        );

        addOfficer(
                "Danielle",
                "Faraday",
                FullName.Gender.FEMALE,
                "graphics/portraits/characters/faraday.png",
                Personalities.STEADY,
                Skills.GUNNERY_IMPLANTS
        );

        addOfficer(
                "Zhang",
                "Allat",
                FullName.Gender.MALE,
                "graphics/portraits/characters/zhang.png",
                Personalities.STEADY,
                Skills.HELMSMANSHIP
        );
    }

    private void addOfficer(String firstName,
                            String lastName,
                            FullName.Gender gender,
                            String portraitPath,
                            String personality,
                            String skill1) {

        PersonAPI person = Global.getFactory().createPerson();
        person.setFaction(Factions.PLAYER);
        person.setGender(gender);
        person.setName(new FullName(firstName, lastName, gender));
        person.setPortraitSprite(portraitPath);
        person.setPersonality(personality);
        person.setRankId(Ranks.SPACE_LIEUTENANT);
        person.setPostId(Ranks.POST_OFFICER);

        MutableCharacterStatsAPI stats = person.getStats();
        stats.setLevel(1);
        stats.setSkillLevel(skill1, 1);

        Global.getSector().getPlayerFleet().getFleetData().addOfficer(person);
    }

    /*
     =========================
     Helpers
     =========================
    */

    // Pass 7 step 7b test pin (director_config.json: exilehegleague.testing.postStoryAnnouncement).
    // Posts one sample story announcement on every load while the pin is true, so the shared
    // announcement intel and Cunningham's portrait registration can be checked without waiting for
    // a rung. The text is test scaffolding, not story. Inert (one config read) when false.
    private void postStoryAnnouncementTestIfPinned() {
        ExileHegLeagueConfig cfg = ExileHegLeagueConfig.load();
        if (cfg == null || !cfg.testPostStoryAnnouncement) return;
        ExileStoryAnnouncementIntel.post(
                "TEST: Story Announcement",
                "This is a test of the shared story-announcement intel (Pass 7 step 7b)."
                        + "\n\nIt should show Admiral Cunningham's placeholder portrait, sit under"
                        + " the Story tag, and end itself after 30 days.",
                "Test pin: exilehegleague.testing.postStoryAnnouncement",
                Factions.HEGEMONY, "exile_cunningham", 30f);
    }

    private MarketAPI findMarketByPrimaryEntityId(String entityId) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            SectorEntityToken primary = market.getPrimaryEntity();
            if (primary != null && entityId.equals(primary.getId())) {
                return market;
            }
        }
        return null;
    }
	
	/*
	 =========================
	 Starting faction setup
	 =========================
	*/

    private void createPlayerStartingColony(PlanetAPI planet,
                                            String marketId,
                                            String marketName,
                                            int size,
                                            String[] conditions,
                                            String[] industries) {
        if (planet == null) return;

        MarketAPI existing = planet.getMarket();
        if (existing != null) {
            Global.getSector().getEconomy().removeMarket(existing);
            planet.setMarket(null);
        }

        MarketAPI market = Global.getFactory().createMarket(marketId, marketName, size);
        market.setPrimaryEntity(planet);
        market.setFactionId(Factions.PLAYER);
        market.setPlayerOwned(true);
        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);

        planet.setMarket(market);
        planet.setFaction(Factions.PLAYER);

    /*
    Clear any existing conditions so only your chosen ones remain
    for (MarketConditionAPI cond : new ArrayList<MarketConditionAPI>(market.getConditions())) {
        market.removeCondition(cond.getId());
    }
*/

        for (String condition : conditions) {
            if (!market.hasCondition(condition)) {
                market.addCondition(condition);
            }
        }

        market.setSurveyLevel(MarketAPI.SurveyLevel.FULL);

        for (MarketConditionAPI condition : market.getConditions()) {
            condition.setSurveyed(true);
        }

        market.reapplyConditions();

        for (String industry : industries) {
            if (!market.hasIndustry(industry)) {
                market.addIndustry(industry);

                // If it's a spaceport, set it to under construction   Tested: Does not work, may not be needed.
                if (Industries.SPACEPORT.equals(industry)) {
                    Industry ind = market.getIndustry(industry);
                    if (ind != null) {
                        ind.startBuilding();
                    }
                }
            }
        }

// The player colony has no available storage without this
        if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) == null) {
            market.addSubmarket(Submarkets.SUBMARKET_STORAGE);
        }

// Add the Local Resources option. The Colony Doesn't Function Correctly Without an Open Market, this is the default when you found a colony in-game	
        if (market.getSubmarket(Submarkets.LOCAL_RESOURCES) == null) {
            market.addSubmarket(Submarkets.LOCAL_RESOURCES);
        }

        // This is necessary to prevent the player from having to pay 5,000mc to unlock storage on their own colony
        SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
        if (storage != null && storage.getPlugin() instanceof StoragePlugin) {
            ((StoragePlugin) storage.getPlugin()).setPlayerPaidToUnlock(true);
        }

        market.setAdmin(Global.getSector().getPlayerPerson());

        Global.getSector().getEconomy().addMarket(market, true);

        market.reapplyConditions();
        market.reapplyIndustries();
    }

    private void showPlayerFactionSetupIfNeeded() {
        if (!Misc.isPlayerFactionSetUp()) {
            Global.getSector().getCampaignUI().showPlayerFactionConfigDialog();
        }
    }

    // This is the only way I could find on my own that will create a player-owned colony that functions. Will only function correctly if the correct submarkets are added (see lines 389 - 403)
    private void setupXechasStartingColonies() {
        PlanetAPI thar = (PlanetAPI) Global.getSector().getEntityById("thar");
        PlanetAPI daria = (PlanetAPI) Global.getSector().getEntityById("daria");
        if (thar != null) {
            createPlayerStartingColony(
                    thar,
                    "thar_market",
                    "Thar",
                    1, // Yes, I know the default is 3, I wanted small starter colonies.
                    new String[]{
                            Conditions.POPULATION_1,
                            Conditions.HOT,
                            Conditions.ORE_RICH,
                            Conditions.RARE_ORE_ABUNDANT,
                            Conditions.ORGANICS_TRACE,
                            Conditions.RUINS_SCATTERED
                    },
                    new String[]{
                            Industries.POPULATION,
                            Industries.SPACEPORT // Note this adds fully completed spaceports, not ones under construction
                    }
            );
        }

        if (daria != null) {
            createPlayerStartingColony(
                    daria,
                    "daria_market",
                    "Daria",
                    1,
                    new String[]{
                            Conditions.POPULATION_1,
                            Conditions.DECIVILIZED_SUBPOP,
                            Conditions.INIMICAL_BIOSPHERE,
                            Conditions.HABITABLE,
                            Conditions.ORE_MODERATE,
                            Conditions.ORGANICS_ABUNDANT,
                            Conditions.RUINS_EXTENSIVE,
                            "exile_aggressive_megafauna",
                            "exile_fecund_soil"
                    },
                    new String[]{
                            Industries.POPULATION,
                            Industries.SPACEPORT,
                            "exile_farming_1"
                    }
            );
        }
    }

/*
    Helper code /*section
*/

    private void setRel(String fromFactionId, String toFactionId, float value) {
        FactionAPI fromFaction = Global.getSector().getFaction(fromFactionId);
        FactionAPI exilemidan = Global.getSector().getFaction("exilemidan");

        if (fromFaction == null) {
            Global.getLogger(ExilesModPlugin.class).error(
                    "Missing faction while setting relation: " + fromFactionId
            );
            return;
        }
    }
        /*
        End Helper Section
         */

}
