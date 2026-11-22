package data.scripts.exiles;

import java.awt.Color;
import java.util.List;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import org.lwjgl.util.vector.Vector2f;

import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.TacticalModulePlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.util.Misc;

import data.scripts.exiles.OmicronRaidIntel.BountyResult;

/**
 * THE CONVOY AMBUSH -- a Black Fleet operation (Pass 7 step 7f; war doc sec 6.2 PASS 7 PROXY
 * DESIGN, ruled 2026-10-19, Sean's idea; build details ruled 2026-11-01).
 *
 * <p>Launched by {@link BlackFleetRaidManager} when a {@code CONVOY_AMBUSH} directive
 * ({@link ExileMidanDirective}) names a market and that market's relief convoy is in its system,
 * travelling or unloading. One small raider fleet (destroyer-capped, on the raid ramp) appears at
 * the jump point whose distance from the convoy is closest to {@code spawnPreferredDistance} --
 * since 2026-11-02 (Sean) 0, the jump point NEAREST the convoy, so the raiders follow it in and catch
 * it on the approach (the farthest, ruled first, never arrived in time; 5000 arrived mid-unload) --
 * and INTERCEPTs the convoy (vanilla: the target is always pursued, no give-up). An encounter strips that order, so
 * it is re-issued for the whole hunt window. When the convoy is dead or gone, or after
 * {@code huntDays}, it makes for the nearest jump point and despawns there; it never flies home
 * (ruled 2026-11-01).
 *
 * <p><b>The Black Fleet's own op, by the single-spawner rule:</b> only the Black Fleet director
 * spawns Black Fleet fleets, so its kill switch ({@code $exilebf_raids_enabled}), the strength ramp
 * and its anchors (no living anchor, no ambush) all apply. It does not know who wrote the directive.
 *
 * <p><b>Rulings 2026-11-01:</b> no intel; one campaign message, only if the player is in the system
 * when the raiders appear; the raided side pays the per-kill bounty when the player kills them (the
 * shared {@link ExileHegLeagueBattleRewards#payRaidedSideBounty}), announced a beat after the battle;
 * no Black Fleet prosperity. Attribution of a kill is the war side's business (and deferred).
 *
 * <p>Saved state: a named static class held by the manager, at most one at a time. It drops its
 * fleet references when it ends. The listener is attached to the raider fleet and dies with it.
 */
public class ExileBlackFleetAmbush implements FleetEventListener {

    public static final String AMBUSHER_TAG = "exilebf_ambusher";
    protected static final int HUNT = 0;
    protected static final int LEAVE = 1;
    /** See OmicronRaidIntel: the payout notice waits a beat for the post-battle message flood. */
    protected static final float BOUNTY_NOTICE_DELAY_DAYS = 0.2f;
    /** The credits icon the raid intels put on their payout notices (same file, same look). */
    protected static final String PAYOUT_ICON = "graphics/icons/intel/gain_credits.png";
    protected static boolean payoutIconLoaded = false;

    protected CampaignFleetAPI fleet;
    protected CampaignFleetAPI convoy;
    protected String marketName;
    protected String victimFactionId;
    protected float bountyPerFrigate;
    protected float huntDays;
    protected float leaveDays;
    protected int phase = HUNT;
    protected float phaseDays = 0f;
    protected boolean done = false;
    protected int pendingCredits = 0;
    protected float noticeDays = -1f;
    /** Queued with the credits (2026-11-04): the reputation change, the damage share and how many
     *  battles are aggregated -- the notice shows the same lines as a raid intel's payout update. */
    protected float pendingRepDelta = 0f;
    protected float pendingFraction = 1f;
    protected int pendingBattles = 0;
    /** Times the hunt order had to be re-issued (7f test fix), and when that was last logged. */
    protected int reorders = 0;
    protected float lastReorderLogDay = -999f;
    protected float lastHuntLogDay = -999f;
    protected static final String HUNT_ORDER = "exilebf_ambush_hunt";

    /**
     * Builds and places the raiders. @return the op, or null if no fleet could be made or the
     * system has no jump point (logged either way).
     */
    public static ExileBlackFleetAmbush launch(MarketAPI source, MarketAPI target,
                                               CampaignFleetAPI convoy, float fp, int maxShipSize,
                                               float bountyPerFrigate, float huntDays,
                                               float leaveDays, float spawnPreferredDistance,
                                               float tankerFp) {
        // 7f test fix (2026-11-01): the FARTHEST jump point was 15-17k units out -- ~10 days, the
        // whole unload -- so the raiders could not arrive in time. Now: the jump point whose
        // distance from the convoy is CLOSEST to spawnPreferredDistance (far enough to be seen
        // coming, near enough to arrive). A plain minimum fails in Tyle: Wuya Jump drifts 1.3-8.3k
        // from Cibola, and below the minimum the only other choice is the 17k fringe.
        LocationAPI loc = target.getContainingLocation();
        JumpPointAPI spawnAt = null;
        float best = -1f;
        float bestError = Float.MAX_VALUE;
        // MEASUREMENT (Sean 2026-11-03): every candidate, so the launch line shows the choice. On
        // 2026-11-03 "nearest" picked Syrinx Passage at 16726 for a convoy Sean believes came in by the
        // far jump point -- which, if it were in this list, should have been far nearer.
        StringBuffer candidates = new StringBuffer();
        List points = loc.getEntities(JumpPointAPI.class);
        for (int i = 0; i < points.size(); i++) {
            JumpPointAPI jp = (JumpPointAPI) points.get(i);
            float d = Misc.getDistance(jp.getLocation(), convoy.getLocation());
            if (candidates.length() > 0) candidates.append(", ");
            candidates.append(jp.getName()).append(" ").append((int) d);
            float error = Math.abs(d - spawnPreferredDistance);
            if (error < bestError) {
                bestError = error;
                best = d;
                spawnAt = jp;
            }
        }
        if (spawnAt == null) {
            log("Black Fleet AMBUSH NOT LAUNCHED: " + target.getName() + "'s system has no jump point");
            return null;
        }

        FleetParamsV3 params = new FleetParamsV3(
                source,
                source.getLocationInHyperspace(),
                BlackFleetRaidManager.BF_FACTION,
                null,
                FleetTypes.RAIDER,
                fp,
                // Sean, 2026-11-04: a small fuel ship (vanilla's tankerSmall role is the Dram), because
                // every Black Fleet strike comes from outside the Core. Config ambush.tankerFp; 0 = none.
                0f, Math.max(0f, tankerFp), 0f, 0f, 0f, 0f);
        params.random = new Random();
        params.maxShipSize = maxShipSize;
        params.ignoreMarketFleetSizeMult = Boolean.TRUE;
        CampaignFleetAPI fleet = FleetFactoryV3.createFleet(params);
        if (fleet == null || fleet.isEmpty()) {
            log("Black Fleet AMBUSH NOT LAUNCHED: fleet creation failed (FP " + (int) fp + ")");
            return null;
        }
        // MEASUREMENT (Sean, 2026-11-04): what the factory actually rolled, so the hunters' look can
        // be judged from the log (the destroyer bucket weights the Buffalo Mk.II heavily).
        StringBuffer hulls = new StringBuffer();
        List members = fleet.getFleetData().getMembersListCopy();
        for (int i = 0; i < members.size(); i++) {
            FleetMemberAPI m = (FleetMemberAPI) members.get(i);
            if (hulls.length() > 0) hulls.append(", ");
            hulls.append(m.getHullSpec().getHullName()).append(" ").append((int) m.getFleetPointCost());
        }
        log("Black Fleet AMBUSH COMPOSITION: " + target.getName() + " | " + members.size()
                + " ship(s), " + fleet.getFleetPoints() + " FP, burn "
                + (int) fleet.getFleetData().getMinBurnLevel() + " | " + hulls);
        // Starsector prepends the faction name: "Black Fleet Raiders" (PLACEHOLDER name).
        fleet.setName("Raiders");
        fleet.addTag(AMBUSHER_TAG);
        fleet.setTransponderOn(false);
        // No $isRaider: vanilla's colony-action check reads it, and these fleets hunt one convoy.

        loc.addEntity(fleet);
        fleet.setLocation(spawnAt.getLocation().x, spawnAt.getLocation().y);

        ExileBlackFleetAmbush op = new ExileBlackFleetAmbush();
        op.fleet = fleet;
        op.convoy = convoy;
        op.marketName = target.getName();
        op.victimFactionId = convoy.getFaction() != null ? convoy.getFaction().getId() : null;
        op.bountyPerFrigate = bountyPerFrigate;
        op.huntDays = Math.max(1f, huntDays);
        op.leaveDays = Math.max(1f, leaveDays);
        fleet.addEventListener(op);
        op.orderHunt(op.huntDays);

        boolean playerHere = Global.getSector().getPlayerFleet() != null
                && Global.getSector().getPlayerFleet().getContainingLocation() == loc;
        if (playerHere) {
            // PLACEHOLDER text (Sean writes it).
            Global.getSector().getCampaignUI().addMessage(
                    "Black Fleet raiders have dropped out of " + spawnAt.getName()
                            + ", running down the relief convoy bound for " + target.getName() + ".",
                    Misc.getNegativeHighlightColor());
        }
        log("Black Fleet AMBUSH LAUNCHED: " + source.getName() + " -> " + target.getName()
                + " relief convoy (" + op.victimFactionId + ") | FP " + fleet.getFleetPoints()
                + " (asked " + (int) fp + ") | at " + spawnAt.getName() + ", "
                + (int) best + " from the convoy | player in system: " + playerHere);
        log("Black Fleet AMBUSH SPAWN CHOICE: " + target.getName() + " | convoy "
                + (int) Misc.getDistance(convoy.getLocation(), target.getPrimaryEntity().getLocation())
                + " from its destination, " + (convoy.isInHyperspaceTransition() ? "MID-JUMP" : "in normal space")
                + " | jump points by distance from the convoy: " + candidates
                + " | preferred distance " + (int) spawnPreferredDistance);
        return op;
    }

    public boolean isDone() {
        return done;
    }

    /** Called by the manager every quarter day or so. */
    public void advance(float days) {
        if (done) return;
        flushNotice(days);

        if (fleet == null || !fleet.isAlive() || fleet.isEmpty()) {
            end("the raiders are gone (" + (phase == HUNT ? "destroyed hunting" : "left") + ")");
            return;
        }
        phaseDays += days;

        if (phase == HUNT) {
            String why = null;
            if (convoy == null || !convoy.isAlive() || convoy.isEmpty()) {
                why = "the convoy is dead or gone";
            } else if (convoy.getContainingLocation() != fleet.getContainingLocation()) {
                why = "the convoy left the system";
            } else if (phaseDays >= huntDays) {
                why = "the hunt timed out after " + (int) huntDays + "d";
            }
            if (why != null) {
                beginLeave(why);
                return;
            }
            pointAtConvoy();
            if (phaseDays - lastHuntLogDay >= 1f) {
                lastHuntLogDay = phaseDays;
                logHunt();
            }
            // 7f test fix (2026-11-01): vanilla removes a front INTERCEPT when another fleet
            // interacts with this one (a patrol encounter), and an engine reaction can push its own
            // order in front. Both test ambushes lost the order in 3-4 days with no losses and went
            // home. The hunt stands for the whole window: re-issue it, out of battle.
            FleetAssignmentDataAPI curr = fleet.getCurrentAssignment();
            if (fleet.getBattle() == null && (curr == null || !HUNT_ORDER.equals(curr.getCustom()))) {
                reorders++;
                if (phaseDays - lastReorderLogDay >= 1f) {
                    lastReorderLogDay = phaseDays;
                    log("Black Fleet AMBUSH RE-ORDERED: " + marketName + " -- the hunt order was "
                            + (curr == null ? "gone" : "displaced by " + curr.getAssignment())
                            + " | day " + (int) phaseDays + " of " + (int) huntDays
                            + " | " + reorders + " re-order(s) | "
                            + (int) Misc.getDistance(fleet.getLocation(), convoy.getLocation())
                            + " from the convoy");
                }
                orderHunt(huntDays - phaseDays);
            }
            return;
        }

        // LEAVE: the GO_TO_LOCATION_AND_DESPAWN order removes the fleet at the jump point. A fleet
        // still here long after is despawned, but never in front of the player.
        if (phaseDays >= leaveDays && !fleet.isVisibleToPlayerFleet()) {
            fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
            end("the raiders were removed after " + (int) leaveDays + "d of leaving");
        }
    }

    /**
     * MEASUREMENT (7f retest, 2026-11-01): the raiders spawned 5977 from an unloading convoy and
     * were still not on it 8+ days later. Once a day while hunting: how far, whether vanilla's
     * tactical module has them FLEEING and from what ({@code $ai_fleeingFrom}), what it is chasing,
     * and how far the player is -- so the next run says why instead of us guessing.
     */
    protected void logHunt() {
        String fleeing = "no";
        String chasing = "-";
        if (fleet.getAI() instanceof ModularFleetAIAPI) {
            TacticalModulePlugin tm = ((ModularFleetAIAPI) fleet.getAI()).getTacticalModule();
            if (tm != null) {
                if (tm.isFleeing()) {
                    Object from = fleet.getMemoryWithoutUpdate().get("$ai_fleeingFrom");
                    fleeing = "YES" + (from instanceof CampaignFleetAPI
                            ? " from " + ((CampaignFleetAPI) from).getNameWithFaction()
                            + (((CampaignFleetAPI) from).isPlayerFleet() ? " (the player)" : "")
                            : "");
                }
                SectorEntityToken t = tm.getTarget();
                chasing = t == null ? "nothing" : (t == convoy ? "the convoy" : t.getName());
            }
        }
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        String playerDist = player != null && player.getContainingLocation() == fleet.getContainingLocation()
                ? (int) Misc.getDistance(player.getLocation(), fleet.getLocation()) + "" : "not in system";
        log("Black Fleet AMBUSH HUNT: " + marketName + " day " + (int) phaseDays + " | "
                + (int) Misc.getDistance(fleet.getLocation(), convoy.getLocation())
                + " from the convoy (they "
                + (convoy.getVisibilityLevelTo(fleet) == SectorEntityToken.VisibilityLevel.NONE
                        ? "cannot see it" : "see it") + ")"
                + " | fleeing: " + fleeing + " | chasing: " + chasing
                + " | player " + playerDist + " | speed " + (int) fleet.getVelocity().length());
    }

    /**
     * 7f retest fix (2026-11-01): TELL THE RAIDERS WHERE THE CONVOY IS. Decompiled
     * {@code TacticalModule}: an intercepting fleet that cannot SEE its target searches around
     * {@code $ai_lastSeenTargetLoc}, else {@code $ai_placeToLook}, else a random point within 3000 of
     * ITSELF -- and then keeps renewing that point. Spawned 6000 out, beyond sensor range, the raiders
     * circled their own jump point for 8+ days until the departing convoy flew into them. Midan's
     * tip-off is the lore for knowing; vanilla's own intercept fleets do the same
     * ({@code TransmitterTrapSpecial}, {@code DelayedFleetEncounter} set PLACE_TO_LOOK_FOR_TARGET).
     * Refreshed every tick, so an orbiting or moving convoy is followed.
     */
    protected void pointAtConvoy() {
        if (fleet == null || convoy == null) return;
        Vector2f where = new Vector2f(convoy.getLocation());
        fleet.getMemoryWithoutUpdate().set(FleetAIFlags.PLACE_TO_LOOK_FOR_TARGET, where, 1f);
        fleet.getMemoryWithoutUpdate().set(FleetAIFlags.LAST_SEEN_TARGET_LOC, new Vector2f(where), 0.5f);
    }

    /** The hunt: an INTERCEPT on the convoy at the FRONT of the queue, marked as ours. */
    protected void orderHunt(float days) {
        pointAtConvoy();
        fleet.addAssignmentAtStart(FleetAssignment.INTERCEPT, convoy, Math.max(0.5f, days),
                "hunting the relief convoy bound for " + marketName, null);
        FleetAssignmentDataAPI added = fleet.getCurrentAssignment();
        if (added != null) added.setCustom(HUNT_ORDER);
    }

    protected void beginLeave(String why) {
        phase = LEAVE;
        phaseDays = 0f;
        convoy = null;
        fleet.clearAssignments();
        SectorEntityToken exit = null;
        float min = Float.MAX_VALUE;
        List points = fleet.getContainingLocation().getEntities(JumpPointAPI.class);
        for (int i = 0; i < points.size(); i++) {
            JumpPointAPI jp = (JumpPointAPI) points.get(i);
            float d = Misc.getDistance(jp.getLocation(), fleet.getLocation());
            if (d < min) {
                min = d;
                exit = jp;
            }
        }
        if (exit == null) {
            fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, null);
            end("no jump point to leave by");
            return;
        }
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, exit, leaveDays + 5f,
                "leaving with the spoils");
        log("Black Fleet AMBUSH LEAVING: " + marketName + " -- " + why + " | FP left "
                + fleet.getFleetPoints());
    }

    protected void end(String why) {
        if (pendingCredits > 0) {
            noticeDays = 0f;
            flushNotice(0f);
        }
        done = true;
        fleet = null;
        convoy = null;
        log("Black Fleet AMBUSH ENDED: " + marketName + " -- " + why + " | hunt re-orders: "
                + reorders);
    }

    /** The deferred bounty notice (credits are paid at once; the message waits a beat). */
    protected void flushNotice(float days) {
        if (pendingCredits <= 0 || noticeDays < 0f) return;
        noticeDays -= days;
        if (noticeDays > 0f) return;
        FactionAPI payer = victimFactionId != null ? Global.getSector().getFaction(victimFactionId) : null;
        String credits = Misc.getDGSCredits(pendingCredits);
        String payerName = payer != null ? payer.getDisplayNameWithArticle() : "the convoy's government";
        Color h = Misc.getHighlightColor();

        // 2026-11-04 (Sean's 7f-2 test): a bare campaign message carried no icon, so it did not
        // read as a bounty payment. The ambush has no intel by ruling, so the notice is a vanilla
        // MessageIntel dressed like a raid intel's payout update: credits icon, "received" line,
        // damage share, reputation line.
        MessageIntel intel = new MessageIntel();
        intel.addLine("Bounty from " + payerName + " for Black Fleet raiders destroyed", Misc.getTextColor());
        intel.addLine(BaseIntelPlugin.BULLET + "%s received", Misc.getTextColor(), new String[] {credits}, h);
        int share = Math.round(pendingFraction * 100f);
        if (pendingBattles == 1 && share < 100) {
            intel.addLine(BaseIntelPlugin.BULLET + "%s share based on damage dealt", Misc.getTextColor(),
                    new String[] {share + "%"}, h);
        }
        int repInt = Math.round(pendingRepDelta * 100f);
        if (payer != null && repInt != 0) {
            String deltaString = repInt > 0 ? "improved by " + repInt : "reduced by " + (-repInt);
            Color deltaColor = repInt > 0 ? Misc.getPositiveHighlightColor() : Misc.getNegativeHighlightColor();
            intel.addLine(BaseIntelPlugin.BULLET + "Relationship with " + payer.getDisplayName() + " " + deltaString,
                    Misc.getTextColor(), new String[] {payer.getDisplayName(), deltaString},
                    payer.getBaseUIColor(), deltaColor);
        }
        intel.setIcon(payoutIcon(payer));
        Global.getSector().getCampaignUI().addMessage(intel);

        pendingCredits = 0;
        pendingRepDelta = 0f;
        pendingFraction = 1f;
        pendingBattles = 0;
        noticeDays = -1f;
    }

    /** The credits icon, loaded on first use like the raid intels do; the payer's crest if it fails. */
    protected static String payoutIcon(FactionAPI payer) {
        if (!payoutIconLoaded) {
            try {
                Global.getSettings().loadTexture(PAYOUT_ICON);
                payoutIconLoaded = true;
            } catch (Exception e) {
                Global.getLogger(ExileBlackFleetAmbush.class).warn(
                        "Could not load payout icon " + PAYOUT_ICON + " -- using crest", e);
                return payer != null ? payer.getCrest() : null;
            }
        }
        return PAYOUT_ICON;
    }

    public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner,
                                     BattleAPI battle) {
        if (fleet == null || fleet != this.fleet || battle == null) return;
        if (!battle.isPlayerInvolved()) return;
        BountyResult result = ExileHegLeagueBattleRewards.payRaidedSideBounty(fleet, battle,
                BlackFleetRaidManager.BF_FACTION, victimFactionId, bountyPerFrigate,
                ExileBlackFleetAmbush.class);
        if (result == null) return;
        pendingCredits += result.payment;
        if (result.rep != null) pendingRepDelta += result.rep.delta;
        pendingFraction = result.fraction;
        pendingBattles++;
        noticeDays = BOUNTY_NOTICE_DELAY_DAYS;
    }

    public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason,
                                               Object param) {
        // advance() notices on its next call.
    }

    protected static void log(String line) {
        Global.getLogger(ExileBlackFleetAmbush.class).info(line);
    }
}
