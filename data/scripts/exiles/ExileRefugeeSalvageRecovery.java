package data.scripts.exiles;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial.ExtraSalvage;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.util.Misc;

/** Narrow NPC salvage adapter. Never runs player dialogs or applies player salvage bonuses. */
public class ExileRefugeeSalvageRecovery {
    public static final String CONSUMED = "$exiles_refugee_salvage_consumed";
    public static final String FAILED = "$exiles_refugee_salvage_failed";
    public static final String TRADE_PREFIX = "exile_refugee_salvage_delivery_";

    public static boolean eligible(SectorEntityToken entity) {
        if (entity == null || !entity.isAlive() || entity.isExpired() || entity.getContainingLocation() == null
                || entity.getMemoryWithoutUpdate().contains(CONSUMED)
                || entity.getMemoryWithoutUpdate().contains(FAILED)
                || entity.getMemoryWithoutUpdate().getBoolean(MemFlags.ENTITY_MISSION_IMPORTANT)
                || entity.hasTag(Tags.FADING_OUT_AND_EXPIRING)
                || entity.hasTag(Tags.MISSION_ITEM) || entity.hasTag(Tags.MISSION_LOCATION)
                || entity.hasTag(Tags.REPORT_IMPORTANT)) return false;
        Object special = Misc.getSalvageSpecial(entity);
        if (special != null && !(special instanceof ShipRecoverySpecialData)) return false;
        if (entity instanceof CampaignTerrainAPI) {
            CampaignTerrainAPI terrain = (CampaignTerrainAPI) entity;
            if (!(terrain.getPlugin() instanceof DebrisFieldTerrainPlugin)) return false;
            DebrisFieldTerrainPlugin debris = (DebrisFieldTerrainPlugin) terrain.getPlugin();
            return debris.getParams().source == DebrisFieldSource.BATTLE && debris.getParams().lastsDays <= 60
                    && !debris.isScavenged() && !debris.isFadingOut();
        }
        // Once initialized, the recovery list is authoritative: an empty list is a spent wreck.
        if (special instanceof ShipRecoverySpecialData && ((ShipRecoverySpecialData) special).ships.isEmpty()) return false;
        return entity.hasTag(Tags.SALVAGEABLE) && entity.hasTag(Tags.EXPIRES)
                && Entities.WRECK.equals(entity.getCustomEntityType())
                && entity.getCustomPlugin() instanceof DerelictShipEntityPlugin;
    }

    public static SectorEntityToken findTarget(LocationAPI location, CampaignFleetAPI fleet) {
        return findPreferredTarget(location, fleet == null ? null : fleet.getLocation());
    }

    public static SectorEntityToken findTarget(LocationAPI location, CampaignFleetAPI fleet,
                                                ExileRefugeeSalvageConfig cfg) {
        return findPreferredTarget(location, fleet == null ? null : fleet.getLocation(), cfg);
    }

    /** Null origin is a cheap existence check for dispatch; otherwise rank without rolling loot. */
    public static SectorEntityToken findPreferredTarget(LocationAPI location, Vector2f origin) {
        return findPreferredTarget(location, origin, null);
    }

    /** With tuning supplied, targets inside hostile-market defense space are excluded. */
    public static SectorEntityToken findPreferredTarget(LocationAPI location, Vector2f origin,
                                                         ExileRefugeeSalvageConfig cfg) {
        if (location == null || location.isHyperspace()) return null;
        SectorEntityToken best = null;
        float bestScore = -1;
        float bestDistance = Float.MAX_VALUE;
        List<SectorEntityToken> candidates = new ArrayList<SectorEntityToken>();
        candidates.addAll(location.getEntitiesWithTag(Tags.SALVAGEABLE));
        candidates.addAll(location.getTerrainCopy());
        for (SectorEntityToken entity : candidates) {
            if (!eligible(entity) || (cfg != null
                    && !ExileRefugeeSalvageThreats.isTargetSafe(entity, cfg))) continue;
            if (origin == null) return entity;
            float distance = Misc.getDistance(origin, entity.getLocation());
            float score = targetValue(entity) / (1f + distance / 2000f);
            if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                best = entity; bestScore = score; bestDistance = distance;
            }
        }
        return best;
    }

    /** Largest remaining hull sets the opportunity value; ordinary debris has weight 1. */
    protected static float targetValue(SectorEntityToken entity) {
        float value = 1f;
        Object special = Misc.getSalvageSpecial(entity);
        if (special instanceof ShipRecoverySpecialData) {
            ShipRecoverySpecialData data = (ShipRecoverySpecialData) special;
            for (PerShipData ship : data.ships) value = Math.max(value, shipValue(ship));
            return value;
        }
        if (!(entity instanceof CampaignTerrainAPI) && entity.getCustomPlugin() instanceof DerelictShipEntityPlugin) {
            DerelictShipEntityPlugin plugin = (DerelictShipEntityPlugin) entity.getCustomPlugin();
            if (plugin.getData() != null) value = Math.max(value, shipValue(plugin.getData().ship));
        }
        return value;
    }

    protected static float shipValue(PerShipData ship) {
        if (ship == null) return 1f;
        try {
            ShipVariantAPI variant = ship.getVariant();
            if (variant == null || variant.getHullSpec() == null) return 1f;
            HullSize size = variant.getHullSpec().getHullSize();
            if (size == HullSize.CAPITAL_SHIP) return 16f;
            if (size == HullSize.CRUISER) return 8f;
            if (size == HullSize.DESTROYER) return 4f;
            if (size == HullSize.FRIGATE) return 2f;
            return 1f;
        } catch (RuntimeException ex) {
            // A malformed third-party variant must not stop every fleet's target selection.
            // Collection already logs and temporarily excludes targets it cannot process.
            return 1f;
        }
    }

    public static void collect(SectorEntityToken entity, CampaignFleetAPI fleet, CargoAPI manifest) {
        if (!eligible(entity)) return;
        // Build the complete result first. A bad variant cannot partially consume a battlefield.
        Random random = Misc.getRandom(entity.getMemoryWithoutUpdate().getLong(MemFlags.SALVAGE_SEED), 100);
        CargoAPI result = Global.getFactory().createCargo(false);
        ShipRecoverySpecialData data = ShipRecoverySpecial.getSpecialData(entity, "", false, false);
        if (entity instanceof CampaignTerrainAPI) {
            // Vanilla ScavengeAbility populates the shared debris spec from THESE same entity drops.
            // Pass local copies directly; never mutate a shared salvage spec or add drops twice.
            result.addAll(SalvageEntity.generateSalvage(random, 1, 1, 1, 1,
                    new ArrayList<DropData>(entity.getDropValue()), new ArrayList<DropData>(entity.getDropRandom())));
            if (data != null) for (PerShipData ship : data.ships) scrap(result, ship, random);
        } else {
            if (data != null) {
                for (PerShipData ship : data.ships) scrap(result, ship, random);
            } else {
                scrap(result, ((DerelictShipEntityPlugin) entity.getCustomPlugin()).getData().ship, random);
            }
        }
        ExtraSalvage extra = BaseSalvageSpecial.getExtraSalvage(entity);
        if (extra != null) result.addAll(extra.cargo);
        entity.getMemoryWithoutUpdate().set(CONSUMED, true);
        if (data != null) data.ships.clear();
        BaseSalvageSpecial.clearExtraSalvage(entity.getMemoryWithoutUpdate());
        entity.getDropValue().clear();
        entity.getDropRandom().clear();
        if (entity instanceof CampaignTerrainAPI) {
            ((DebrisFieldTerrainPlugin) ((CampaignTerrainAPI) entity).getPlugin()).setScavenged(true);
        } else {
            entity.removeTag(Tags.SALVAGEABLE);
            entity.setExpired(true);
        }
        for (CargoStackAPI stack : result.getStacksCopy()) {
            if (!allowed(stack)) continue;
            float capacity = Math.max(0, fleet.getCargo().getMaxCapacity() - fleet.getCargo().getSpaceUsed());
            float amount = stack.getSize();
            if (stack.isCommodityStack() && Commodities.FUEL.equals(stack.getCommodityId())) {
                amount = Math.min(amount, Math.max(0, fleet.getCargo().getMaxFuel() - fleet.getCargo().getFuel()));
            } else if (stack.isPersonnelStack()) {
                amount = Math.min(amount, Math.max(0, fleet.getCargo().getMaxPersonnel() - fleet.getCargo().getTotalPersonnel()));
            } else if (stack.getCargoSpacePerUnit() > 0) {
                amount = Math.min(amount, capacity / stack.getCargoSpacePerUnit());
            }
            amount = (float) Math.floor(amount);
            if (amount <= 0) continue;
            fleet.getCargo().addItems(stack.getType(), stack.getData(), amount);
            manifest.addItems(stack.getType(), stack.getData(), amount);
        }
        Global.getLogger(ExileRefugeeSalvageRecovery.class).info("Refugee fleet salvaged " + entity.getId());
    }

    public static boolean allowed(CargoStackAPI stack) {
        if (stack.isWeaponStack()) return true;
        if (!stack.isCommodityStack()) return false;
        String id = stack.getCommodityId();
        // Explicit material list: custom cores may be commodity stacks too.
        return Commodities.SUPPLIES.equals(id) || Commodities.FUEL.equals(id)
                || Commodities.METALS.equals(id) || Commodities.RARE_METALS.equals(id)
                || Commodities.HEAVY_MACHINERY.equals(id) || Commodities.ORE.equals(id)
                || Commodities.RARE_ORE.equals(id) || Commodities.VOLATILES.equals(id)
                || Commodities.ORGANICS.equals(id) || Commodities.FOOD.equals(id)
                || Commodities.DOMESTIC_GOODS.equals(id) || Commodities.LUXURY_GOODS.equals(id)
                || Commodities.HAND_WEAPONS.equals(id) || Commodities.DRUGS.equals(id)
                || Commodities.ORGANS.equals(id) || Commodities.CREW.equals(id) || Commodities.MARINES.equals(id);
    }

    /** Only condition and weapon retention are needed for scrapping; no recoverable hull is persisted. */
    public static class ExileScrapPreparation extends ShipRecoverySpecial {
        public ExileScrapPreparation(Random random) { this.random = random; }
        public void prepareMember(FleetMemberAPI member, PerShipData ship) {
            member.getStatus().setHullFraction(getHullForCondition(ship.condition));
            if (ship.pruneWeapons) {
                float retain = getFighterWeaponRetainProb(ship.condition);
                FleetEncounterContext.prepareShipForRecovery(member, false, false, false, retain, retain, random);
            }
        }
    }
    protected static void scrap(CargoAPI cargo, PerShipData ship, Random random) {
        if (ship == null || ship.getVariant() == null) throw new IllegalArgumentException("Missing Refugee salvage variant");
        ShipVariantAPI original = ship.getVariant();
        ShipVariantAPI variant;
        try { variant = (ShipVariantAPI) original.clone(); }
        catch (Exception ex) { throw new IllegalStateException("Could not copy salvage variant", ex); }
        FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
        new ExileScrapPreparation(random).prepareMember(member, ship);
        cargo.addCommodity(Commodities.SUPPLIES, member.getRepairTracker().getSuppliesFromScuttling());
        cargo.addCommodity(Commodities.FUEL, member.getRepairTracker().getFuelFromScuttling());
        cargo.addCommodity(Commodities.HEAVY_MACHINERY, member.getRepairTracker().getHeavyMachineryFromScuttling());
        for (String slot : member.getVariant().getNonBuiltInWeaponSlots()) {
            String weapon = member.getVariant().getWeaponId(slot);
            if (weapon != null) cargo.addWeapons(weapon, 1);
        }
    }

    public static void deliver(MarketAPI home, CampaignFleetAPI fleet, CargoAPI manifest, ExileRefugeeSalvageConfig cfg) {
        SubmarketAPI open = home.getSubmarket(Submarkets.SUBMARKET_OPEN);
        // Refresh BEFORE injecting weapons: do not erase today's delivery on the player's next click.
        if (open != null) open.getPlugin().updateCargoPrePlayerInteraction();
        for (CargoStackAPI stack : manifest.getStacksCopy()) {
            float remaining = Math.min(stack.getSize(), fleet.getCargo().getQuantity(stack.getType(), stack.getData()));
            if (remaining <= 0 || !allowed(stack)) continue;
            if (stack.isWeaponStack()) {
                if (open != null) open.getCargo().addItems(stack.getType(), stack.getData(), remaining);
            } else {
                String id = stack.getCommodityId();
                float existing = 0;
                for (StatMod mod : home.getCommodityData(id).getTradeModPlus().getFlatMods().values()) {
                    if (mod.getSource().startsWith(TRADE_PREFIX)) existing += Math.max(0, mod.getValue());
                }
                float amount = Math.min(remaining * cfg.commodityDeliveryFraction, Math.max(0, cfg.commodityBonusCap - existing));
                if (amount >= 1) home.getCommodityData(id).addTradeModPlus(
                        TRADE_PREFIX + Misc.genUID(), (float) Math.floor(amount), cfg.commodityBonusDays);
            }
            fleet.getCargo().removeItems(stack.getType(), stack.getData(), remaining);
        }
        manifest.clear();
        Global.getLogger(ExileRefugeeSalvageRecovery.class).info("Refugee salvage delivered to " + home.getName());
    }
}
