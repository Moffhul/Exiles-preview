package data.scripts.exiles;

import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * "I am a blockade, and this is the system I am holding" -- the one thing the shared layer needs
 * to know about any Exiles blockade (PASS 4 of docs/EXILES_HEG_LEAGUE_WAR.md, sec 14.9.2).
 *
 * <p><b>Why an interface rather than a common base class.</b> Blockades ride vanilla's
 * {@code BlockadeFGI}, which is not in the {@code RaidIntel} hierarchy {@link ExileLiveOps}
 * already scans, and each faction's blockade subclasses it directly. {@link ExileLiveOps} has to
 * ask "where is this blockade" without importing any director's class -- dependencies in this
 * project point INTO the shared utilities and never back out (CLAUDE.md sec 6, the settled
 * architecture). One method on an interface buys exactly that and nothing else.
 *
 * <p>Implemented by {@code AshenBlockade} (which already had the method, unchanged) and
 * {@code ExileHegLeagueBlockade}. A future Black Fleet or Omicron blockade implements it and is
 * picked up by the helper with no further change.
 */
public interface ExileBlockadeIntel {

    /** The system whose jump points this blockade is holding, or null if it has none. */
    StarSystemAPI getBlockadedSystem();
}
