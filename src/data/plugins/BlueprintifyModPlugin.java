package data.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import lunalib.lunaSettings.LunaSettings;

import java.util.*;

public class BlueprintifyModPlugin extends BaseModPlugin {

    static BlueprintifyModPlugin INSTANCE;
    private BlueprintifyOwnershipScript ownershipScript;

    private static final Map<String, String> ENCOUNTER_FLAGS = new HashMap<>();
    static {
        ENCOUNTER_FLAGS.put("threat",  "$player.encounteredThreat");
        ENCOUNTER_FLAGS.put("dweller", "$player.encounteredDweller");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        if (Global.getSector() == null) return;
        INSTANCE = this;

        Global.getSector().addListener(new BlueprintifyEncounterListener(false));
        ownershipScript = new BlueprintifyOwnershipScript();
        ownershipScript.runCheck();
        Global.getSector().addTransientScript(ownershipScript);

        // Game load: compute overview data only; debug data is built on demand.
        Set<String> marketFactionIds = getMarketFactionIds();
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        boolean fullMode = Boolean.TRUE.equals(LunaSettings.getBoolean("blueprintify", "blueprintify_fullMode"));

        List<String> ships   = tagOrphanedShips(marketFactionIds, memory, fullMode, null);
        List<String> weapons = tagOrphanedWeapons(marketFactionIds, memory, fullMode);
        List<String> fighters = tagOrphanedFighters(marketFactionIds, memory, fullMode);

        updateIntel(ships, weapons, fighters, new ArrayList<BlueprintifyStatusIntel.ShipDebugEntry>());
    }

    // Called once when the Debug tab is opened. Re-runs all tagging against the current
    // game state (faction known ships, player memory flags, etc.) so the debug view is fresh.
    void computeDebugAndUpdate() {
        if (Global.getSector() == null) return;
        if (ownershipScript != null) ownershipScript.runCheck();

        Set<String> marketFactionIds = getMarketFactionIds();
        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        boolean fullMode = Boolean.TRUE.equals(LunaSettings.getBoolean("blueprintify", "blueprintify_fullMode"));

        List<BlueprintifyStatusIntel.ShipDebugEntry> shipDebug = new ArrayList<>();
        List<String> ships   = tagOrphanedShips(marketFactionIds, memory, fullMode, shipDebug);
        List<String> weapons = tagOrphanedWeapons(marketFactionIds, memory, fullMode);
        List<String> fighters = tagOrphanedFighters(marketFactionIds, memory, fullMode);

        updateIntel(ships, weapons, fighters, shipDebug);
    }

    protected List<String> tagOrphanedShips(Set<String> marketFactionIds, MemoryAPI memory, boolean fullMode,
                                             List<BlueprintifyStatusIntel.ShipDebugEntry> debugOut) {
        Set<String> known = getKnownShips(marketFactionIds);
        Set<String> entityKnown = getEntityKnownShips(marketFactionIds);

        // Build hull → faction ID map so debug entries carry an authoritative faction for coloring.
        // Market factions take priority over entity factions (written first, entity only fills gaps).
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
            String id  = spec.getHullId();
            String name = spec.getHullName();
            String mfr  = spec.getManufacturer();
            String fid  = hullFaction != null ? hullFaction.get(id) : null;
            if (spec.isDHull()) { debug(debugOut, id, name, "D-hull", mfr, fid); continue; }
            if (spec.getHullSize() == ShipAPI.HullSize.FIGHTER) { debug(debugOut, id, name, "Fighter size", mfr, fid); continue; }
            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)) { debug(debugOut, id, name, "STATION hint", mfr, fid); continue; }
            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.MODULE)) { debug(debugOut, id, name, "MODULE hint", mfr, fid); continue; }
            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNDER_PARENT)) { debug(debugOut, id, name, "UNDER_PARENT hint", mfr, fid); continue; }
            if (spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE)
                    && !entityKnown.contains(id)) {
                debug(debugOut, id, name, "UNBOARDABLE (not entity-known)", mfr, fid);
                continue;
            }
            if (spec.hasTag("no_drop")) { debug(debugOut, id, name, "Tag: no_drop", mfr, fid); continue; }
            if (spec.hasTag("hide_in_codex")) { debug(debugOut, id, name, "Tag: hide_in_codex", mfr, fid); continue; }
            if (name == null || name.replace("?", "").trim().isEmpty()) { debug(debugOut, id, name, "Blank/null name", mfr, fid); continue; }
            if (spec.getOrdnancePoints(null) == 0) { debug(debugOut, id, name, "Zero OP", mfr, fid); continue; }
            if (known.contains(id)) { debug(debugOut, id, name, "Market faction known", mfr, fid); continue; }
            boolean requiresAcquisition = entityKnown.contains(id)
                    || spec.hasTag("no_bp_drop")
                    || spec.hasTag("no_dealer")
                    || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX)
                    || spec.hasTag("codex_unlockable");
            if (requiresAcquisition) {
                if (!fullMode) { debug(debugOut, id, name, "Requires acquisition (cleanup mode)", mfr, fid); continue; }
                if (!memory.getBoolean("$blueprintify_seen_" + id)) { debug(debugOut, id, name, "Requires acquisition (not seen)", mfr, fid); continue; }
            } else if (spec.hasTag("limited_tooltip_if_locked") && !isEncounterUnlocked(spec.getTags(), memory)) {
                debug(debugOut, id, name, "Encounter locked", mfr, fid);
                continue;
            }
            spec.addTag("rare_bp");
            debug(debugOut, id, name, "INCLUDED", mfr, fid);
            tagged.add(id);
        }
        return tagged;
    }

    private static void debug(List<BlueprintifyStatusIntel.ShipDebugEntry> debugOut,
                               String hullId, String hullName, String reason,
                               String manufacturer, String factionId) {
        if (debugOut != null) {
            debugOut.add(new BlueprintifyStatusIntel.ShipDebugEntry(
                    hullId, hullName, reason, manufacturer, factionId));
        }
    }

    protected List<String> tagOrphanedWeapons(Set<String> marketFactionIds, MemoryAPI memory, boolean fullMode) {
        Set<String> known = getKnownWeapons(marketFactionIds);
        Map<String, Set<String>> entityFactionMap = getEntityFactionWeaponMap(marketFactionIds);
        Set<String> entityKnown = new HashSet<>();
        Set<String> entityEncountered = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : entityFactionMap.entrySet()) {
            entityKnown.addAll(entry.getValue());
            if (memory.getBoolean("$blueprintify_encountered_" + entry.getKey())) {
                entityEncountered.addAll(entry.getValue());
            }
        }
        List<String> tagged = new ArrayList<>();
        for (WeaponSpecAPI spec : Global.getSettings().getAllWeaponSpecs()) {
            if (spec.hasTag("no_drop") || spec.hasTag("no_bp_drop")) continue;
            String name = spec.getWeaponName();
            if (name == null || name.replace("?", "").trim().isEmpty()) continue;
            if (known.contains(spec.getWeaponId())) continue;
            if (entityKnown.contains(spec.getWeaponId())) {
                if (!fullMode) continue;
                if (!entityEncountered.contains(spec.getWeaponId())) continue;
            } else if (spec.hasTag("limited_tooltip_if_locked") && !isEncounterUnlocked(spec.getTags(), memory)) {
                continue;
            }
            spec.addTag("rare_bp");
            tagged.add(spec.getWeaponName());
        }
        return tagged;
    }

    protected List<String> tagOrphanedFighters(Set<String> marketFactionIds, MemoryAPI memory, boolean fullMode) {
        Set<String> known = getKnownFighters(marketFactionIds);
        Map<String, Set<String>> entityFactionMap = getEntityFactionFighterMap(marketFactionIds);
        Set<String> entityKnown = new HashSet<>();
        Set<String> entityEncountered = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : entityFactionMap.entrySet()) {
            entityKnown.addAll(entry.getValue());
            if (memory.getBoolean("$blueprintify_encountered_" + entry.getKey())) {
                entityEncountered.addAll(entry.getValue());
            }
        }
        List<String> tagged = new ArrayList<>();
        for (FighterWingSpecAPI spec : Global.getSettings().getAllFighterWingSpecs()) {
            if (spec.hasTag("no_drop") || spec.hasTag("no_bp_drop")) continue;
            String name = spec.getWingName();
            if (name == null || name.replace("?", "").trim().isEmpty()) continue;
            if (known.contains(spec.getId())) continue;
            if (entityKnown.contains(spec.getId())) {
                if (!fullMode) continue;
                if (!entityEncountered.contains(spec.getId())) continue;
            } else if (spec.hasTag("limited_tooltip_if_locked") && !isEncounterUnlocked(spec.getTags(), memory)) {
                continue;
            }
            spec.addTag("rare_bp");
            tagged.add(spec.getWingName());
        }
        return tagged;
    }

    private boolean isEncounterUnlocked(Set<String> tags, MemoryAPI memory) {
        for (Map.Entry<String, String> entry : ENCOUNTER_FLAGS.entrySet()) {
            if (tags.contains(entry.getKey())) {
                return memory.getBoolean(entry.getValue());
            }
        }
        return false;
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

    private static Map<String, Set<String>> getEntityFactionWeaponMap(Set<String> marketFactionIds) {
        Map<String, Set<String>> result = new HashMap<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (!marketFactionIds.contains(f.getId()) && !f.getKnownWeapons().isEmpty()) {
                result.put(f.getId(), new HashSet<>(f.getKnownWeapons()));
            }
        }
        return result;
    }

    private static Map<String, Set<String>> getEntityFactionFighterMap(Set<String> marketFactionIds) {
        Map<String, Set<String>> result = new HashMap<>();
        for (FactionAPI f : Global.getSector().getAllFactions()) {
            if (!marketFactionIds.contains(f.getId()) && !f.getKnownFighters().isEmpty()) {
                result.put(f.getId(), new HashSet<>(f.getKnownFighters()));
            }
        }
        return result;
    }

    private void updateIntel(List<String> ships, List<String> weapons, List<String> fighters,
                              List<BlueprintifyStatusIntel.ShipDebugEntry> shipDebug) {
        List<IntelInfoPlugin> existing = Global.getSector().getIntelManager().getIntel(BlueprintifyStatusIntel.class);
        if (!existing.isEmpty()) {
            ((BlueprintifyStatusIntel) existing.get(0)).update(ships, weapons, fighters, shipDebug);
        } else {
            Global.getSector().getIntelManager().addIntel(
                    new BlueprintifyStatusIntel(ships, weapons, fighters, shipDebug), false);
        }
    }
}
