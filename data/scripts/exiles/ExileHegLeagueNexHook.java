package data.scripts.exiles;

/**
 * The Nexerelin-touching half of the Heg-League premise pins (docs/EXILES_HEG_LEAGUE_WAR.md
 * sec 7.2, layer B2), behind an interface that mentions <b>no</b> Nexerelin type.
 *
 * <p><b>Why an interface at all.</b> Janino resolves referenced types when it compiles the
 * REFERRING class, so a direct call to a class that imports {@code exerelin.*} would drag
 * that import into the director's own compile and break game start whenever Nexerelin is
 * absent -- the exact failure CLAUDE.md sec 7 and the compat audit warn about. The only
 * implementation, {@link ExileHegLeagueNexPin}, is therefore loaded by NAME (a string, never a
 * compile-time reference) through the script classloader, which is the same lazy path Nex
 * itself uses to instantiate {@code ExilesNexStart}. No Nexerelin class is reachable from the
 * startup script graph.
 *
 * <p>Every method returns a success flag rather than throwing, so a Nexerelin version whose
 * API has moved degrades to the B3 self-heal backstop with a log line instead of crashing.
 */
public interface ExileHegLeagueNexHook {

    /**
     * Adds a flat modifier to the Nex maximum-relationship stat for the ordered pair.
     *
     * <p>Nex's {@code modifyMaxRelationshipMod} ACCUMULATES (it re-adds the current value of
     * the same modifier id), which is why the caller guards this with a persistent once-flag.
     *
     * @return true if the call reached Nexerelin.
     */
    boolean applyPin(String modifierId, float mod, String factionId,
                     String otherFactionId, String desc);

    /** Removes our modifier id from the pair's stat (the reversibility half of R11). */
    boolean removePin(String modifierId, String factionId, String otherFactionId);

    /** Current total modifier value for the pair, for logging and the D4 idempotency test. */
    float readPin(String factionId, String otherFactionId);
}
