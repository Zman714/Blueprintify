package data.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import lunalib.lunaSettings.LunaSettings;

import java.util.*;

public class BlueprintifyModPlugin extends BaseModPlugin {

    static BlueprintifyModPlugin INSTANCE;
    private BlueprintifyOwnershipScript ownershipScript;

    private Set<String> storageWeapons  = new HashSet<>();
    private Set<String> storageFighters = new HashSet<>();

    @Override
    public void onGameLoad(boolean newGame) {
        if (Global.getSector() == null) return;
        INSTANCE = this;

        ownershipScript = new BlueprintifyOwnershipScript();
        ownershipScript.runCheck();
        Global.getSector().addTransientScript(ownershipScript);
        scanStorage();

        Set<String> marketFactionIds = getMarketFactionIds();
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        boolean fullMode = Boolean.TRUE.equals(LunaSettings.getBoolean("blueprintify", "blueprintify_fullMode"));

        List<String> ships   = tagOrphanedShips(marketFactionIds, memory, fullMode, null);
        List<String> weapons = tagOrphanedWeapons(marketFactionIds, fullMode, buildPlayerWeapons(), null);
        List<String> fighters = tagOrphanedFighters(marketFactionIds, fullMode, buildPlayerFighters(), null);

        updateIntel(ships, weapons, fighters,
                new ArrayList<BlueprintifyStatusIntel.ShipDebugEntry>(),
                new ArrayList<BlueprintifyStatusIntel.WeaponDebugEntry>(),
                new ArrayList<BlueprintifyStatusIntel.FighterDebugEntry>());
    }

    void computeDebugAndUpdate() {
        if (Global.getSector() == null) return;
        if (ownershipScript != null) ownershipScript.runCheck();

        Set<String> marketFactionIds = getMarketFactionIds();
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        boolean fullMode = Boolean.TRUE.equals(LunaSettings.getBoolean("blueprintify", "blueprintify_fullMode"));

        List<BlueprintifyStatusIntel.ShipDebugEntry>    shipDebug    = new ArrayList<>();
        List<BlueprintifyStatusIntel.WeaponDebugEntry>  weaponDebug  = new ArrayList<>();
        List<BlueprintifyStatusIntel.FighterDebugEntry> fighterDebug = new ArrayList<>();

        List<String> ships   = tagOrphanedShips(marketFactionIds, memory, fullMode, shipDebug);
        List<String> weapons = tagOrphanedWeapons(marketFactionIds, fullMode, buildPlayerWeapons(), weaponDebug);
        List<String> fighters = tagOrphanedFighters(marketFactionIds, fullMode, buildPlayerFighters(), fighterDebug);

        updateIntel(ships, weapons, fighters, shipDebug, weaponDebug, fighterDebug);
    }

    private void scanStorage() {
        storageWeapons  = new HashSet<>();
        storageFighters = new HashSet<>();
        if (Global.getSector() == null) return;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (!market.isPlayerOwned()) continue;
            SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
            if (storage == null) continue;
            CargoAPI cargo = storage.getCargoNullOk();
            if (cargo == null) continue;
            for (CargoAPI.CargoItemQuantity<String> item : cargo.getWeapons()) {
                storageWeapons.add(item.getItem());
            }
            for (CargoAPI.CargoItemQuantity<String> item : cargo.getFighters()) {
                storageFighters.add(item.getItem());
            }
        }
    }

    private Set<String> buildPlayerWeapons() {
        Set<String> has = new HashSet<>();
        if (Global.getSector() == null) return has;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return has;

        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        if (playerFaction != null) has.addAll(playerFaction.getKnownWeapons());

        for (CargoAPI.CargoItemQuantity<String> item : fleet.getCargo().getWeapons()) {
            has.add(item.getItem());
        }
        addFittedWeapons(fleet.getFleetData().getMembersListCopy(), has);

        FleetDataAPI mothballed = fleet.getCargo().getMothballedShips();
        if (mothballed != null) addFittedWeapons(mothballed.getMembersListCopy(), has);

        has.addAll(storageWeapons);
        return has;
    }

    private Set<String> buildPlayerFighters() {
        Set<String> has = new HashSet<>();
        if (Global.getSector() == null) return has;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return has;

        FactionAPI playerFaction = Global.getSector().getPlayerFaction();
        if (playerFaction != null) has.addAll(playerFaction.getKnownFighters());

        for (CargoAPI.CargoItemQuantity<String> item : fleet.getCargo().getFighters()) {
            has.add(item.getItem());
        }
        addFittedFighters(fleet.getFleetData().getMembersListCopy(), has);

        FleetDataAPI mothballed = fleet.getCargo().getMothballedShips();
        if (mothballed != null) addFittedFighters(mothballed.getMembersListCopy(), has);

        has.addAll(storageFighters);
        return has;
    }

    private static void addFittedWeapons(List<FleetMemberAPI> members, Set<String> out) {
        for (FleetMemberAPI member : members) {
            if (member.isFighterWing() || member.getVariant() == null) continue;
            for (String slot : member.getVariant().getFittedWeaponSlots()) {
                String weaponId = member.getVariant().getWeaponId(slot);
                if (weaponId != null) out.add(weaponId);
            }
        }
    }

    private static void addFittedFighters(List<FleetMemberAPI> members, Set<String> out) {
        for (FleetMemberAPI member : members) {
            if (member.isFighterWing() || member.getVariant() == null) continue;
            for (String wingId : member.getVariant().getFittedWings()) {
                if (wingId != null) out.add(wingId);
            }
        }
    }

    protected List<String> tagOrphanedShips(Set<String> marketFactionIds, MemoryAPI memory, boolean fullMode,
                                             List<BlueprintifyStatusIntel.ShipDebugEntry> debugOut) {
        Set<String> known = getKnownShips(marketFactionIds);
        Set<String> entityKnown = getEntityKnownShips(marketFactionIds);

        final Map<String, String> hullFaction;
        if (debugOut != null) {
            hullFaction = new HashMap<String, String>();
            for (FactionAPI f : Global.getSector().getAllFactions()) {
                if (marketFactionIds.contains(f.getId())) {
                    for (String hullId : f.getKnownShips()) hullFaction.put(hullId, f.getId());
                }
            }
            for (FactionAPI f : Global.getSector().getAllFactions()) {
                if (!marketFactionIds.contains(f.getId())) {
                    for (String hullId : f.getKnownShips()) {
                        if (!hullFaction.containsKey(hullId)) hullFaction.put(hullId, f.getId());
                    }
                }
            }
        } else {
            hullFaction = null;
        }

        List<String> tagged = new ArrayList<>();
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            String id   = spec.getHullId();
            String name = spec.getHullName();
            String mfr  = spec.getManufacturer();
            String fid  = hullFaction != null ? hullFaction.get(id) : null;

            if (spec.isDHull()) { debugShip(debugOut, id, name, "D-hull", mfr, fid); continue; }
            if (spec.getHullSize() == ShipAPI.HullSize.FIGHTER) { debugShip(debugOut, id, name, "Fighter size", mfr, fid); continue; }
            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)) { debugShip(debugOut, id, name, "STATION hint", mfr, fid); continue; }
            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.MODULE)) { debugShip(debugOut, id, name, "MODULE hint", mfr, fid); continue; }
            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNDER_PARENT)) { debugShip(debugOut, id, name, "UNDER_PARENT hint", mfr, fid); continue; }
            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE)
                    && !entityKnown.contains(id)) {
                debugShip(debugOut, id, name, "UNBOARDABLE (not entity-known)", mfr, fid);
                continue;
            }
            if (spec.hasTag("no_drop")) { debugShip(debugOut, id, name, "Tag: no_drop", mfr, fid); continue; }
            if (spec.hasTag("hide_in_codex")) { debugShip(debugOut, id, name, "Tag: hide_in_codex", mfr, fid); continue; }
            if (name == null || name.replace("?", "").trim().isEmpty()) { debugShip(debugOut, id, name, "Blank/null name", mfr, fid); continue; }
            if (spec.getOrdnancePoints(null) == 0) { debugShip(debugOut, id, name, "Zero OP", mfr, fid); continue; }
            if (known.contains(id)) { debugShip(debugOut, id, name, "Market faction known", mfr, fid); continue; }

            boolean requiresAcquisition = entityKnown.contains(id)
                    || spec.hasTag("no_bp_drop")
                    || spec.hasTag("no_dealer")
                    || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX)
                    || spec.hasTag("codex_unlockable")
                    || spec.hasTag("limited_tooltip_if_locked");

            if (requiresAcquisition && !fullMode) {
                debugShip(debugOut, id, name, "Requires acquisition (cleanup mode)", mfr, fid);
                continue;
            }
            if (fullMode && !memory.getBoolean("$blueprintify_seen_" + id)) {
                debugShip(debugOut, id, name, "Not acquired", mfr, fid);
                continue;
            }

            spec.addTag("rare_bp");
            debugShip(debugOut, id, name, "INCLUDED", mfr, fid);
            tagged.add(id);
        }
        return tagged;
    }

    protected List<String> tagOrphanedWeapons(Set<String> marketFactionIds, boolean fullMode,
                                               Set<String> playerHasWeapons,
                                               List<BlueprintifyStatusIntel.WeaponDebugEntry> debugOut) {
        Set<String> known = getKnownWeapons(marketFactionIds);
        Set<String> entityKnown = getEntityKnownWeapons(marketFactionIds);

        List<String> tagged = new ArrayList<>();
        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            String id   = spec.getWeaponId();
            String name = spec.getWeaponName();
            String mfr  = spec.getManufacturer();

            if (spec.hasTag("no_drop"))    { debugWeapon(debugOut, id, name, "Tag: no_drop",    mfr); continue; }
            if (spec.hasTag("no_bp_drop")) { debugWeapon(debugOut, id, name, "Tag: no_bp_drop", mfr); continue; }
            if (name == null || name.replace("?", "").trim().isEmpty()) { debugWeapon(debugOut, id, name, "Blank name", mfr); continue; }
            if (known.contains(id))        { debugWeapon(debugOut, id, name, "Market faction known", mfr); continue; }
            if (entityKnown.contains(id) && !fullMode) { debugWeapon(debugOut, id, name, "Entity known (cleanup mode)", mfr); continue; }
            if (fullMode && !playerHasWeapons.contains(id)) { debugWeapon(debugOut, id, name, "Not acquired", mfr); continue; }

            spec.addTag("rare_bp");
            debugWeapon(debugOut, id, name, "INCLUDED", mfr);
            tagged.add(name);
        }
        return tagged;
    }

    protected List<String> tagOrphanedFighters(Set<String> marketFactionIds, boolean fullMode,
                                                Set<String> playerHasFighters,
                                                List<BlueprintifyStatusIntel.FighterDebugEntry> debugOut) {
        Set<String> known = getKnownFighters(marketFactionIds);
        Set<String> entityKnown = getEntityKnownFighters(marketFactionIds);

        List<String> tagged = new ArrayList<>();
        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            String id   = spec.getId();
            String name = spec.getWingName();
            String mfr  = (spec.getVariant() != null && spec.getVariant().getHullSpec() != null)
                          ? spec.getVariant().getHullSpec().getManufacturer() : null;

            if (spec.hasTag("no_drop"))    { debugFighter(debugOut, id, name, "Tag: no_drop",    mfr); continue; }
            if (spec.hasTag("no_bp_drop")) { debugFighter(debugOut, id, name, "Tag: no_bp_drop", mfr); continue; }
            if (name == null || name.replace("?", "").trim().isEmpty()) { debugFighter(debugOut, id, name, "Blank name", mfr); continue; }
            if (known.contains(id))        { debugFighter(debugOut, id, name, "Market faction known", mfr); continue; }
            if (entityKnown.contains(id) && !fullMode) { debugFighter(debugOut, id, name, "Entity known (cleanup mode)", mfr); continue; }
            if (fullMode && !playerHasFighters.contains(id)) { debugFighter(debugOut, id, name, "Not acquired", mfr); continue; }

            spec.addTag("rare_bp");
            debugFighter(debugOut, id, name, "INCLUDED", mfr);
            tagged.add(name);
        }
        return tagged;
    }

    private static void debugShip(List<BlueprintifyStatusIntel.ShipDebugEntry> out,
                                   String id, String name, String reason, String mfr, String fid) {
        if (out != null) out.add(new BlueprintifyStatusIntel.ShipDebugEntry(id, name, reason, mfr, fid));
    }

    private static void debugWeapon(List<BlueprintifyStatusIntel.WeaponDebugEntry> out,
                                     String id, String name, String reason, String mfr) {
        if (out != null) out.add(new BlueprintifyStatusIntel.WeaponDebugEntry(id, name, reason, mfr));
    }

    private static void debugFighter(List<BlueprintifyStatusIntel.FighterDebugEntry> out,
                                      String id, String name, String reason, String mfr) {
        if (out != null) out.add(new BlueprintifyStatusIntel.FighterDebugEntry(id, name, reason, mfr));
    }

    static Set<String> getMarketFactionIds() {
        Set<String> ids = new HashSet<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            ids.add(market.getFactionId());
        }
        return ids;
    }

    private static Set<String> getKnownShips(Set<String> marketFactionIds) {
        Set<String> known = new HashSet<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (marketFactionIds.contains(f.getId())) known.addAll(f.getKnownShips());
        }
        return known;
    }

    private static Set<String> getKnownWeapons(Set<String> marketFactionIds) {
        Set<String> known = new HashSet<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (marketFactionIds.contains(f.getId())) known.addAll(f.getKnownWeapons());
        }
        return known;
    }

    private static Set<String> getKnownFighters(Set<String> marketFactionIds) {
        Set<String> known = new HashSet<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (marketFactionIds.contains(f.getId())) known.addAll(f.getKnownFighters());
        }
        return known;
    }

    static Set<String> getEntityKnownShips(Set<String> marketFactionIds) {
        Set<String> known = new HashSet<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (!marketFactionIds.contains(f.getId())) known.addAll(f.getKnownShips());
        }
        return known;
    }

    private static Set<String> getEntityKnownWeapons(Set<String> marketFactionIds) {
        Set<String> known = new HashSet<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (!marketFactionIds.contains(f.getId())) known.addAll(f.getKnownWeapons());
        }
        return known;
    }

    private static Set<String> getEntityKnownFighters(Set<String> marketFactionIds) {
        Set<String> known = new HashSet<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (!marketFactionIds.contains(f.getId())) known.addAll(f.getKnownFighters());
        }
        return known;
    }

    private void updateIntel(List<String> ships, List<String> weapons, List<String> fighters,
                              List<BlueprintifyStatusIntel.ShipDebugEntry> shipDebug,
                              List<BlueprintifyStatusIntel.WeaponDebugEntry> weaponDebug,
                              List<BlueprintifyStatusIntel.FighterDebugEntry> fighterDebug) {
        List<IntelInfoPlugin> existing = Global.getSector().getIntelManager().getIntel(BlueprintifyStatusIntel.class);
        if (!existing.isEmpty()) {
            ((BlueprintifyStatusIntel) existing.get(0)).update(ships, weapons, fighters, shipDebug, weaponDebug, fighterDebug);
        } else {
            Global.getSector().getIntelManager().addIntel(
                    new BlueprintifyStatusIntel(ships, weapons, fighters, shipDebug, weaponDebug, fighterDebug), false);
        }
    }
}
