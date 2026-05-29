package data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.Color;
import java.util.*;

public class BlueprintifyStatusIntel extends BaseIntelPlugin {

    // Main tab buttons
    private static final String BTN_OVERVIEW = "bpfy_overview";
    private static final String BTN_DEBUG    = "bpfy_debug";

    // Debug sub-tab buttons
    private static final String BTN_DEBUG_SHIPS    = "bpfy_debug_ships";
    private static final String BTN_DEBUG_WEAPONS  = "bpfy_debug_weapons";
    private static final String BTN_DEBUG_FIGHTERS = "bpfy_debug_fighters";

    // Pagination buttons
    private static final String BTN_PREV = "bpfy_prev";
    private static final String BTN_NEXT = "bpfy_next";

    private static final int PAGE_SIZE             = 50;
    private static final int MAX_ENTRIES_PER_GROUP = 200;

    // -------------------------------------------------------------------------
    // Debug entry types
    // -------------------------------------------------------------------------

    public static class ShipDebugEntry {
        public final String hullId;
        public final String hullName;
        public final String filterReason;
        public final String manufacturer;
        public final String factionId;

        public ShipDebugEntry(String hullId, String hullName, String filterReason,
                              String manufacturer, String factionId) {
            this.hullId = hullId;
            this.hullName = (hullName != null && !hullName.isEmpty()) ? hullName : hullId;
            this.filterReason = filterReason;
            this.manufacturer = manufacturer != null ? manufacturer : "";
            this.factionId = factionId;
        }
    }

    public static class WeaponDebugEntry {
        public final String weaponId;
        public final String weaponName;
        public final String filterReason;
        public final String manufacturer;

        public WeaponDebugEntry(String weaponId, String weaponName, String filterReason, String manufacturer) {
            this.weaponId = weaponId;
            this.weaponName = (weaponName != null && !weaponName.isEmpty()) ? weaponName : weaponId;
            this.filterReason = filterReason;
            this.manufacturer = manufacturer != null ? manufacturer : "";
        }
    }

    public static class FighterDebugEntry {
        public final String wingId;
        public final String wingName;
        public final String filterReason;
        public final String manufacturer;

        public FighterDebugEntry(String wingId, String wingName, String filterReason, String manufacturer) {
            this.wingId = wingId;
            this.wingName = (wingName != null && !wingName.isEmpty()) ? wingName : wingId;
            this.filterReason = filterReason;
            this.manufacturer = manufacturer != null ? manufacturer : "";
        }
    }

    // -------------------------------------------------------------------------
    // Reason ordering / detail visibility
    // -------------------------------------------------------------------------

    private static final List<String> SHIP_REASON_ORDER = Arrays.asList(
        "INCLUDED",
        "Market faction known",
        "Requires acquisition (cleanup mode)",
        "Not acquired",
        "Tag: no_drop",
        "Tag: hide_in_codex",
        "Zero OP",
        "UNBOARDABLE (not entity-known)",
        "Blank/null name",
        "STATION hint",
        "MODULE hint",
        "UNDER_PARENT hint",
        "Fighter size",
        "D-hull"
    );

    private static final Set<String> SHIP_DETAIL_REASONS = new HashSet<>(Arrays.asList(
        "INCLUDED",
        "Market faction known",
        "Requires acquisition (cleanup mode)",
        "Not acquired",
        "Tag: no_drop",
        "Tag: hide_in_codex",
        "Zero OP",
        "UNBOARDABLE (not entity-known)"
    ));

    private static final List<String> WEAPON_REASON_ORDER = Arrays.asList(
        "INCLUDED",
        "Market faction known",
        "Entity known (cleanup mode)",
        "Not acquired",
        "Tag: no_drop",
        "Tag: no_bp_drop",
        "Blank name"
    );

    private static final Set<String> WEAPON_DETAIL_REASONS = new HashSet<>(Arrays.asList(
        "INCLUDED",
        "Market faction known",
        "Entity known (cleanup mode)",
        "Not acquired"
    ));

    private static final List<String> FIGHTER_REASON_ORDER = Arrays.asList(
        "INCLUDED",
        "Market faction known",
        "Entity known (cleanup mode)",
        "Not acquired",
        "Tag: no_drop",
        "Tag: no_bp_drop",
        "Blank name"
    );

    private static final Set<String> FIGHTER_DETAIL_REASONS = new HashSet<>(Arrays.asList(
        "INCLUDED",
        "Market faction known",
        "Entity known (cleanup mode)",
        "Not acquired"
    ));

    // -------------------------------------------------------------------------
    // Live data
    // -------------------------------------------------------------------------

    private List<String> ships;
    private List<String> weapons;
    private List<String> fighters;
    private List<ShipDebugEntry>    shipDebug;
    private List<WeaponDebugEntry>  weaponDebug;
    private List<FighterDebugEntry> fighterDebug;

    // Pre-computed caches
    private List<ShipDebugEntry>    shipDetailEntries;
    private Map<String, Integer>    shipGroupCounts;

    private List<WeaponDebugEntry>  weaponDetailEntries;
    private Map<String, Integer>    weaponGroupCounts;

    private List<FighterDebugEntry> fighterDetailEntries;
    private Map<String, Integer>    fighterGroupCounts;

    // For ship faction coloring
    private List<FactionAPI> factionsByNameLen;

    // UI state
    private boolean showDebug  = false;
    private String  debugSubTab = "ships";
    private int     debugPage   = 0;

    // -------------------------------------------------------------------------
    // Construction / update
    // -------------------------------------------------------------------------

    public BlueprintifyStatusIntel(List<String> ships, List<String> weapons, List<String> fighters,
                                    List<ShipDebugEntry> shipDebug,
                                    List<WeaponDebugEntry> weaponDebug,
                                    List<FighterDebugEntry> fighterDebug) {
        update(ships, weapons, fighters, shipDebug, weaponDebug, fighterDebug);
    }

    public void update(List<String> ships, List<String> weapons, List<String> fighters,
                       List<ShipDebugEntry> shipDebug,
                       List<WeaponDebugEntry> weaponDebug,
                       List<FighterDebugEntry> fighterDebug) {
        this.ships       = new ArrayList<>(ships);
        this.weapons     = sorted(weapons);
        this.fighters    = sorted(fighters);
        this.shipDebug   = new ArrayList<>(shipDebug);
        this.weaponDebug = new ArrayList<>(weaponDebug);
        this.fighterDebug = new ArrayList<>(fighterDebug);
        rebuildShipDebugCache();
        rebuildWeaponDebugCache();
        rebuildFighterDebugCache();
    }

    // -------------------------------------------------------------------------
    // Cache builders
    // -------------------------------------------------------------------------

    private void rebuildShipDebugCache() {
        if (Global.getSector() == null) return;

        factionsByNameLen = new ArrayList<>(Global.getSector().getAllFactions());
        Collections.sort(factionsByNameLen, new Comparator<FactionAPI>() {
            public int compare(FactionAPI a, FactionAPI b) {
                return b.getDisplayName().length() - a.getDisplayName().length();
            }
        });

        shipGroupCounts = new HashMap<>();
        for (ShipDebugEntry e : shipDebug) {
            Integer n = shipGroupCounts.get(e.filterReason);
            shipGroupCounts.put(e.filterReason, n == null ? 1 : n + 1);
        }

        Map<String, List<ShipDebugEntry>> grouped = new LinkedHashMap<>();
        for (String reason : SHIP_REASON_ORDER) {
            if (SHIP_DETAIL_REASONS.contains(reason)) grouped.put(reason, new ArrayList<ShipDebugEntry>());
        }
        for (ShipDebugEntry e : shipDebug) {
            if (!SHIP_DETAIL_REASONS.contains(e.filterReason)) continue;
            List<ShipDebugEntry> g = grouped.get(e.filterReason);
            if (g == null) { g = new ArrayList<ShipDebugEntry>(); grouped.put(e.filterReason, g); }
            g.add(e);
        }

        final Comparator<ShipDebugEntry> order = new Comparator<ShipDebugEntry>() {
            public int compare(ShipDebugEntry a, ShipDebugEntry b) {
                int c = a.manufacturer.compareToIgnoreCase(b.manufacturer);
                return c != 0 ? c : a.hullName.compareToIgnoreCase(b.hullName);
            }
        };

        shipDetailEntries = new ArrayList<>();
        for (List<ShipDebugEntry> g : grouped.values()) {
            Collections.sort(g, order);
            shipDetailEntries.addAll(g.subList(0, Math.min(g.size(), MAX_ENTRIES_PER_GROUP)));
        }
    }

    private void rebuildWeaponDebugCache() {
        weaponGroupCounts = new HashMap<>();
        for (WeaponDebugEntry e : weaponDebug) {
            Integer n = weaponGroupCounts.get(e.filterReason);
            weaponGroupCounts.put(e.filterReason, n == null ? 1 : n + 1);
        }

        Map<String, List<WeaponDebugEntry>> grouped = new LinkedHashMap<>();
        for (String reason : WEAPON_REASON_ORDER) {
            if (WEAPON_DETAIL_REASONS.contains(reason)) grouped.put(reason, new ArrayList<WeaponDebugEntry>());
        }
        for (WeaponDebugEntry e : weaponDebug) {
            if (!WEAPON_DETAIL_REASONS.contains(e.filterReason)) continue;
            List<WeaponDebugEntry> g = grouped.get(e.filterReason);
            if (g == null) { g = new ArrayList<WeaponDebugEntry>(); grouped.put(e.filterReason, g); }
            g.add(e);
        }

        final Comparator<WeaponDebugEntry> order = new Comparator<WeaponDebugEntry>() {
            public int compare(WeaponDebugEntry a, WeaponDebugEntry b) {
                return a.weaponName.compareToIgnoreCase(b.weaponName);
            }
        };

        weaponDetailEntries = new ArrayList<>();
        for (List<WeaponDebugEntry> g : grouped.values()) {
            Collections.sort(g, order);
            weaponDetailEntries.addAll(g.subList(0, Math.min(g.size(), MAX_ENTRIES_PER_GROUP)));
        }
    }

    private void rebuildFighterDebugCache() {
        fighterGroupCounts = new HashMap<>();
        for (FighterDebugEntry e : fighterDebug) {
            Integer n = fighterGroupCounts.get(e.filterReason);
            fighterGroupCounts.put(e.filterReason, n == null ? 1 : n + 1);
        }

        Map<String, List<FighterDebugEntry>> grouped = new LinkedHashMap<>();
        for (String reason : FIGHTER_REASON_ORDER) {
            if (FIGHTER_DETAIL_REASONS.contains(reason)) grouped.put(reason, new ArrayList<FighterDebugEntry>());
        }
        for (FighterDebugEntry e : fighterDebug) {
            if (!FIGHTER_DETAIL_REASONS.contains(e.filterReason)) continue;
            List<FighterDebugEntry> g = grouped.get(e.filterReason);
            if (g == null) { g = new ArrayList<FighterDebugEntry>(); grouped.put(e.filterReason, g); }
            g.add(e);
        }

        final Comparator<FighterDebugEntry> order = new Comparator<FighterDebugEntry>() {
            public int compare(FighterDebugEntry a, FighterDebugEntry b) {
                return a.wingName.compareToIgnoreCase(b.wingName);
            }
        };

        fighterDetailEntries = new ArrayList<>();
        for (List<FighterDebugEntry> g : grouped.values()) {
            Collections.sort(g, order);
            fighterDetailEntries.addAll(g.subList(0, Math.min(g.size(), MAX_ENTRIES_PER_GROUP)));
        }
    }

    // -------------------------------------------------------------------------
    // Color lookup (ships only)
    // -------------------------------------------------------------------------

    private Color getWeaponEntryColor(WeaponDebugEntry e) {
        if (!e.manufacturer.isEmpty() && Global.getSettings().hasDesignTypeColor(e.manufacturer)) {
            return Global.getSettings().getDesignTypeColor(e.manufacturer);
        }
        return null;
    }

    private Color getFighterEntryColor(FighterDebugEntry e) {
        if (!e.manufacturer.isEmpty() && Global.getSettings().hasDesignTypeColor(e.manufacturer)) {
            return Global.getSettings().getDesignTypeColor(e.manufacturer);
        }
        return null;
    }

    private Color getShipEntryColor(ShipDebugEntry e) {
        if (!e.manufacturer.isEmpty() && Global.getSettings().hasDesignTypeColor(e.manufacturer)) {
            return Global.getSettings().getDesignTypeColor(e.manufacturer);
        }
        if (e.factionId != null && !e.factionId.isEmpty() && Global.getSector() != null) {
            FactionAPI f = Global.getSector().getFaction(e.factionId);
            if (f != null) return f.getBaseUIColor();
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // BaseIntelPlugin overrides
    // -------------------------------------------------------------------------

    @Override protected String getName() { return "Blueprintify"; }
    @Override public String getIcon() { return "graphics/icons/intel/codex_update.png"; }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, IntelInfoPlugin.ListInfoMode mode) {
        info.addPara(
            "%s ships, %s weapons, %s fighters added to blueprint drop pools.",
            3f, Misc.getHighlightColor(),
            String.valueOf(ships.size()),
            String.valueOf(weapons.size()),
            String.valueOf(fighters.size())
        );
    }

    @Override public boolean hasLargeDescription() { return true; }
    @Override public boolean hasSmallDescription() { return false; }
    @Override public boolean isEnding() { return false; }
    @Override public boolean isEnded() { return false; }

    // -------------------------------------------------------------------------
    // Button handling
    // -------------------------------------------------------------------------

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (BTN_DEBUG.equals(buttonId)) {
            showDebug = true;
            debugPage = 0;
        } else if (BTN_OVERVIEW.equals(buttonId)) {
            showDebug = false;
            debugPage = 0;
        } else if (BTN_DEBUG_SHIPS.equals(buttonId)) {
            debugSubTab = "ships";
            debugPage = 0;
        } else if (BTN_DEBUG_WEAPONS.equals(buttonId)) {
            debugSubTab = "weapons";
            debugPage = 0;
        } else if (BTN_DEBUG_FIGHTERS.equals(buttonId)) {
            debugSubTab = "fighters";
            debugPage = 0;
        } else if (BTN_PREV.equals(buttonId)) {
            debugPage = Math.max(0, debugPage - 1);
        } else if (BTN_NEXT.equals(buttonId)) {
            debugPage++;
        }
        ui.recreateIntelUI();
    }

    // -------------------------------------------------------------------------
    // Panel construction
    // -------------------------------------------------------------------------

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        if (BlueprintifyModPlugin.INSTANCE != null) {
            BlueprintifyModPlugin.INSTANCE.computeDebugAndUpdate();
        }

        float opad = 10f;
        float pad  = 3f;
        float tabH = 30f;
        float bw   = 100f;
        float bh   = 22f;
        float bgap = 10f;
        float by   = (tabH - bh) / 2f;

        // Tab bar as a CustomPanel so buttons can be positioned horizontally
        CustomPanelAPI tabBar = panel.createCustomPanel(width, tabH, null);
        float x = 0f;

        TooltipMakerAPI e;

        // Overview
        e = tabBar.createUIElement(bw, bh, false);
        if (!showDebug) {
            e.addButton("Overview", BTN_OVERVIEW,
                    Misc.getBrightPlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.C2_MENU, bw, bh, 0f);
        } else {
            e.addButton("Overview", BTN_OVERVIEW, bw, bh, 0f);
        }
        tabBar.addUIElement(e).inTL(x, by);
        x += bw + bgap;

        // Debug
        e = tabBar.createUIElement(bw, bh, false);
        if (showDebug) {
            e.addButton("Debug", BTN_DEBUG,
                    Misc.getBrightPlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.C2_MENU, bw, bh, 0f);
        } else {
            e.addButton("Debug", BTN_DEBUG, bw, bh, 0f);
        }
        tabBar.addUIElement(e).inTL(x, by);
        x += bw + bgap;

        if (showDebug) {
            x += 20f; // visual gap between main tabs and sub-tabs

            String[] labels = {"Ships",          "Weapons",          "Fighters"         };
            String[] ids    = {BTN_DEBUG_SHIPS,   BTN_DEBUG_WEAPONS,  BTN_DEBUG_FIGHTERS };
            String[] keys   = {"ships",           "weapons",          "fighters"         };

            for (int i = 0; i < 3; i++) {
                e = tabBar.createUIElement(bw, bh, false);
                if (keys[i].equals(debugSubTab)) {
                    e.addButton(labels[i], ids[i],
                            Misc.getBrightPlayerColor(), Misc.getDarkPlayerColor(),
                            Alignment.MID, CutStyle.C2_MENU, bw, bh, 0f);
                } else {
                    e.addButton(labels[i], ids[i], bw, bh, 0f);
                }
                tabBar.addUIElement(e).inTL(x, by);
                x += bw + bgap;
            }
        }

        panel.addComponent(tabBar).inTL(0, 0);

        TooltipMakerAPI content = panel.createUIElement(width, height - tabH - 12f, true);
        if (showDebug) {
            if ("weapons".equals(debugSubTab)) {
                addWeaponDebugSection(content, opad, pad);
            } else if ("fighters".equals(debugSubTab)) {
                addFighterDebugSection(content, opad, pad);
            } else {
                addShipDebugSection(content, opad, pad);
            }
        } else {
            content.addPara(
                "The following blueprints have been added to normal salvage drop pools by Blueprintify. "
                + "They can appear in derelicts, ruins, and other salvageable sites.",
                opad
            );
            addShipSection(content, ships, opad, pad);
            addSection(content, "Weapons (" + weapons.size() + ")", weapons, opad, pad);
            addSection(content, "Fighters (" + fighters.size() + ")", fighters, opad, pad);
        }
        panel.addUIElement(content).belowLeft(tabBar, 4f);
    }

    // -------------------------------------------------------------------------
    // Overview sections
    // -------------------------------------------------------------------------

    private void addShipSection(TooltipMakerAPI info, List<String> hullIds, float opad, float pad) {
        List<FleetMemberAPI> members = new ArrayList<>();
        for (String id : hullIds) {
            String variantId = id + "_Hull";
            if (Global.getSettings().getVariant(variantId) != null) {
                members.add(Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId));
            }
        }
        Collections.sort(members, new Comparator<FleetMemberAPI>() {
            public int compare(FleetMemberAPI a, FleetMemberAPI b) {
                return a.getHullSpec().getHullName().compareTo(b.getHullSpec().getHullName());
            }
        });
        info.addSectionHeading("Ships (" + members.size() + ")", Alignment.LMID, opad);
        if (members.isEmpty()) {
            info.addPara("None", Misc.getGrayColor(), pad);
        } else {
            int cols = 7;
            info.addShipList(cols, (members.size() + cols - 1) / cols, 58f, Misc.getTextColor(), members, opad);
        }
    }

    private void addSection(TooltipMakerAPI info, String heading, List<String> names, float opad, float pad) {
        info.addSectionHeading(heading, Alignment.LMID, opad);
        if (names.isEmpty()) {
            info.addPara("None", Misc.getGrayColor(), pad);
        } else {
            for (String name : names) {
                bullet(info);
                info.addPara(name, pad);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Debug sections
    // -------------------------------------------------------------------------

    private void addShipDebugSection(TooltipMakerAPI info, float opad, float pad) {
        if (shipDetailEntries == null) {
            info.addPara("Debug data not available.", Misc.getGrayColor(), opad);
            return;
        }

        info.addSectionHeading(
            "Ship Filter Report  (" + shipDebug.size() + " hulls examined)",
            Alignment.LMID, opad
        );

        for (String reason : SHIP_REASON_ORDER) {
            if (SHIP_DETAIL_REASONS.contains(reason)) continue;
            int count = shipGroupCounts.containsKey(reason) ? shipGroupCounts.get(reason) : 0;
            if (count == 0) continue;
            info.addSectionHeading(reason + "  (" + count + ")  —  not listed", Alignment.LMID, pad);
        }

        int total      = shipDetailEntries.size();
        int totalPages = total == 0 ? 1 : (total + PAGE_SIZE - 1) / PAGE_SIZE;
        int page       = Math.max(0, Math.min(debugPage, totalPages - 1));
        int start      = page * PAGE_SIZE;
        int end        = Math.min(start + PAGE_SIZE, total);

        info.addSectionHeading("Details  —  page " + (page + 1) + " of " + totalPages, Alignment.LMID, opad);
        addPageNav(info, page, totalPages, pad);

        if (total == 0) { info.addPara("No detail entries.", Misc.getGrayColor(), pad); return; }

        String lastReason = null;
        if (start > 0) {
            String startReason = shipDetailEntries.get(start).filterReason;
            if (startReason.equals(shipDetailEntries.get(start - 1).filterReason)) {
                int count = shipGroupCounts.containsKey(startReason) ? shipGroupCounts.get(startReason) : 0;
                info.addSectionHeading(startReason + " (" + count + ")  —  continued", Alignment.LMID, opad);
                lastReason = startReason;
            }
        }

        for (int i = start; i < end; i++) {
            ShipDebugEntry e = shipDetailEntries.get(i);
            if (!e.filterReason.equals(lastReason)) {
                lastReason = e.filterReason;
                int count  = shipGroupCounts.containsKey(lastReason) ? shipGroupCounts.get(lastReason) : 0;
                boolean capped = count > MAX_ENTRIES_PER_GROUP;
                info.addSectionHeading(
                    lastReason + " (" + count + ")" + (capped ? "  —  showing first " + MAX_ENTRIES_PER_GROUP : ""),
                    Alignment.LMID, opad
                );
            }
            Color entryColor = getShipEntryColor(e);
            String line = e.hullName + "  [" + e.hullId + "]";
            if (!e.manufacturer.isEmpty()) line += "  —  " + e.manufacturer;
            if (entryColor != null) {
                info.addPara(line, pad, entryColor, e.manufacturer);
            } else {
                info.addPara(line, Misc.getGrayColor(), pad);
            }
        }

        addPageNav(info, page, totalPages, opad);
    }

    private void addWeaponDebugSection(TooltipMakerAPI info, float opad, float pad) {
        if (weaponDetailEntries == null) {
            info.addPara("Debug data not available.", Misc.getGrayColor(), opad);
            return;
        }

        info.addSectionHeading(
            "Weapon Filter Report  (" + weaponDebug.size() + " weapons examined)",
            Alignment.LMID, opad
        );

        for (String reason : WEAPON_REASON_ORDER) {
            if (WEAPON_DETAIL_REASONS.contains(reason)) continue;
            int count = weaponGroupCounts.containsKey(reason) ? weaponGroupCounts.get(reason) : 0;
            if (count == 0) continue;
            info.addSectionHeading(reason + "  (" + count + ")  —  not listed", Alignment.LMID, pad);
        }

        int total      = weaponDetailEntries.size();
        int totalPages = total == 0 ? 1 : (total + PAGE_SIZE - 1) / PAGE_SIZE;
        int page       = Math.max(0, Math.min(debugPage, totalPages - 1));
        int start      = page * PAGE_SIZE;
        int end        = Math.min(start + PAGE_SIZE, total);

        info.addSectionHeading("Details  —  page " + (page + 1) + " of " + totalPages, Alignment.LMID, opad);
        addPageNav(info, page, totalPages, pad);

        if (total == 0) { info.addPara("No detail entries.", Misc.getGrayColor(), pad); return; }

        String lastReason = null;
        if (start > 0) {
            String startReason = weaponDetailEntries.get(start).filterReason;
            if (startReason.equals(weaponDetailEntries.get(start - 1).filterReason)) {
                int count = weaponGroupCounts.containsKey(startReason) ? weaponGroupCounts.get(startReason) : 0;
                info.addSectionHeading(startReason + " (" + count + ")  —  continued", Alignment.LMID, opad);
                lastReason = startReason;
            }
        }

        for (int i = start; i < end; i++) {
            WeaponDebugEntry e = weaponDetailEntries.get(i);
            if (!e.filterReason.equals(lastReason)) {
                lastReason = e.filterReason;
                int count  = weaponGroupCounts.containsKey(lastReason) ? weaponGroupCounts.get(lastReason) : 0;
                boolean capped = count > MAX_ENTRIES_PER_GROUP;
                info.addSectionHeading(
                    lastReason + " (" + count + ")" + (capped ? "  —  showing first " + MAX_ENTRIES_PER_GROUP : ""),
                    Alignment.LMID, opad
                );
            }
            Color wColor = getWeaponEntryColor(e);
            String wLine = e.weaponName + "  [" + e.weaponId + "]";
            if (!e.manufacturer.isEmpty()) wLine += "  —  " + e.manufacturer;
            if (wColor != null) {
                info.addPara(wLine, pad, wColor, e.manufacturer);
            } else {
                info.addPara(wLine, Misc.getGrayColor(), pad);
            }
        }

        addPageNav(info, page, totalPages, opad);
    }

    private void addFighterDebugSection(TooltipMakerAPI info, float opad, float pad) {
        if (fighterDetailEntries == null) {
            info.addPara("Debug data not available.", Misc.getGrayColor(), opad);
            return;
        }

        info.addSectionHeading(
            "Fighter Filter Report  (" + fighterDebug.size() + " wings examined)",
            Alignment.LMID, opad
        );

        for (String reason : FIGHTER_REASON_ORDER) {
            if (FIGHTER_DETAIL_REASONS.contains(reason)) continue;
            int count = fighterGroupCounts.containsKey(reason) ? fighterGroupCounts.get(reason) : 0;
            if (count == 0) continue;
            info.addSectionHeading(reason + "  (" + count + ")  —  not listed", Alignment.LMID, pad);
        }

        int total      = fighterDetailEntries.size();
        int totalPages = total == 0 ? 1 : (total + PAGE_SIZE - 1) / PAGE_SIZE;
        int page       = Math.max(0, Math.min(debugPage, totalPages - 1));
        int start      = page * PAGE_SIZE;
        int end        = Math.min(start + PAGE_SIZE, total);

        info.addSectionHeading("Details  —  page " + (page + 1) + " of " + totalPages, Alignment.LMID, opad);
        addPageNav(info, page, totalPages, pad);

        if (total == 0) { info.addPara("No detail entries.", Misc.getGrayColor(), pad); return; }

        String lastReason = null;
        if (start > 0) {
            String startReason = fighterDetailEntries.get(start).filterReason;
            if (startReason.equals(fighterDetailEntries.get(start - 1).filterReason)) {
                int count = fighterGroupCounts.containsKey(startReason) ? fighterGroupCounts.get(startReason) : 0;
                info.addSectionHeading(startReason + " (" + count + ")  —  continued", Alignment.LMID, opad);
                lastReason = startReason;
            }
        }

        for (int i = start; i < end; i++) {
            FighterDebugEntry e = fighterDetailEntries.get(i);
            if (!e.filterReason.equals(lastReason)) {
                lastReason = e.filterReason;
                int count  = fighterGroupCounts.containsKey(lastReason) ? fighterGroupCounts.get(lastReason) : 0;
                boolean capped = count > MAX_ENTRIES_PER_GROUP;
                info.addSectionHeading(
                    lastReason + " (" + count + ")" + (capped ? "  —  showing first " + MAX_ENTRIES_PER_GROUP : ""),
                    Alignment.LMID, opad
                );
            }
            Color fColor = getFighterEntryColor(e);
            String fLine = e.wingName + "  [" + e.wingId + "]";
            if (!e.manufacturer.isEmpty()) fLine += "  —  " + e.manufacturer;
            if (fColor != null) {
                info.addPara(fLine, pad, fColor, e.manufacturer);
            } else {
                info.addPara(fLine, Misc.getGrayColor(), pad);
            }
        }

        addPageNav(info, page, totalPages, opad);
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private void addPageNav(TooltipMakerAPI info, int page, int totalPages, float pad) {
        if (totalPages <= 1) return;
        if (page > 0) {
            info.addButton("< Previous", BTN_PREV,
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.C2_MENU, 110f, 20f, 0f);
        }
        if (page < totalPages - 1) {
            info.addButton("Next >", BTN_NEXT,
                    Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.C2_MENU, 110f, 20f, 10f);
        }
    }

    private static List<String> sorted(List<String> in) {
        List<String> out = new ArrayList<>(in);
        Collections.sort(out);
        return out;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Personal");
        return tags;
    }
}
