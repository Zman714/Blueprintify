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

    private static final String BTN_OVERVIEW = "bpfy_overview";
    private static final String BTN_DEBUG    = "bpfy_debug";
    private static final String BTN_PREV     = "bpfy_prev";
    private static final String BTN_NEXT     = "bpfy_next";

    private static final int PAGE_SIZE            = 50;
    private static final int MAX_ENTRIES_PER_GROUP = 200;

    public static class ShipDebugEntry {
        public final String hullId;
        public final String hullName;
        public final String filterReason;
        public final String manufacturer;
        public final String factionId; // faction that knows this hull (null if unregistered)

        public ShipDebugEntry(String hullId, String hullName, String filterReason,
                              String manufacturer, String factionId) {
            this.hullId = hullId;
            this.hullName = (hullName != null && !hullName.isEmpty()) ? hullName : hullId;
            this.filterReason = filterReason;
            this.manufacturer = manufacturer != null ? manufacturer : "";
            this.factionId = factionId;
        }
    }

    private static final List<String> REASON_ORDER = Arrays.asList(
        "INCLUDED",
        "Market faction known",
        "Requires acquisition (cleanup mode)",
        "Requires acquisition (not seen)",
        "Encounter locked",
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

    private static final Set<String> DETAIL_REASONS = new HashSet<>(Arrays.asList(
        "INCLUDED",
        "Market faction known",
        "Requires acquisition (cleanup mode)",
        "Requires acquisition (not seen)",
        "Encounter locked",
        "Tag: no_drop",
        "Tag: hide_in_codex",
        "Zero OP",
        "UNBOARDABLE (not entity-known)"
    ));

    // Live data
    private List<String> ships;
    private List<String> weapons;
    private List<String> fighters;
    private List<ShipDebugEntry> shipDebug;

    // Pre-computed debug cache — built once in update(), read on every render
    private List<ShipDebugEntry> detailEntries;  // sorted, capped, flattened detail entries
    private Map<String, Integer> groupCounts;     // reason → total count (for headings)

    // UI state (persists across panel rebuilds)
    private boolean showDebug = false;
    private int debugPage     = 0;

    public BlueprintifyStatusIntel(List<String> ships, List<String> weapons, List<String> fighters,
                                    List<ShipDebugEntry> shipDebug) {
        update(ships, weapons, fighters, shipDebug);
    }

    public void update(List<String> ships, List<String> weapons, List<String> fighters,
                       List<ShipDebugEntry> shipDebug) {
        this.ships = new ArrayList<>(ships);
        this.weapons = sorted(weapons);
        this.fighters = sorted(fighters);
        this.shipDebug = new ArrayList<>(shipDebug);
        rebuildDebugCache();
    }

    private void rebuildDebugCache() {
        if (Global.getSector() == null) return;

        // Count total entries per reason (used for group headings).
        groupCounts = new HashMap<>();
        for (ShipDebugEntry e : shipDebug) {
            Integer n = groupCounts.get(e.filterReason);
            groupCounts.put(e.filterReason, n == null ? 1 : n + 1);
        }

        // Build detail entries: group → sort by manufacturer then name → cap → flatten in REASON_ORDER.
        Map<String, List<ShipDebugEntry>> grouped = new LinkedHashMap<>();
        for (String reason : REASON_ORDER) {
            if (DETAIL_REASONS.contains(reason)) grouped.put(reason, new ArrayList<ShipDebugEntry>());
        }
        for (ShipDebugEntry e : shipDebug) {
            if (!DETAIL_REASONS.contains(e.filterReason)) continue;
            List<ShipDebugEntry> g = grouped.get(e.filterReason);
            if (g == null) { g = new ArrayList<ShipDebugEntry>(); grouped.put(e.filterReason, g); }
            g.add(e);
        }

        final Comparator<ShipDebugEntry> entryOrder = new Comparator<ShipDebugEntry>() {
            public int compare(ShipDebugEntry a, ShipDebugEntry b) {
                int c = a.manufacturer.compareToIgnoreCase(b.manufacturer);
                if (c != 0) return c;
                return a.hullName.compareToIgnoreCase(b.hullName);
            }
        };

        detailEntries = new ArrayList<>();
        for (List<ShipDebugEntry> g : grouped.values()) {
            Collections.sort(g, entryOrder);
            detailEntries.addAll(g.subList(0, Math.min(g.size(), MAX_ENTRIES_PER_GROUP)));
        }
    }

    private Color getEntryColor(ShipDebugEntry e) {
        if (!e.manufacturer.isEmpty() && Global.getSettings().hasDesignTypeColor(e.manufacturer)) {
            return Global.getSettings().getDesignTypeColor(e.manufacturer);
        }
        if (e.factionId != null && !e.factionId.isEmpty() && Global.getSector() != null) {
            FactionAPI f = Global.getSector().getFaction(e.factionId);
            if (f != null) return f.getBaseUIColor();
        }
        return null;
    }

    private static List<String> sorted(List<String> in) {
        List<String> out = new ArrayList<>(in);
        Collections.sort(out);
        return out;
    }

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

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (BTN_DEBUG.equals(buttonId)) {
            showDebug = true;
            debugPage = 0;
            // Recompute against current game state — runs once per Debug press.
            if (BlueprintifyModPlugin.INSTANCE != null) {
                BlueprintifyModPlugin.INSTANCE.computeDebugAndUpdate();
            }
        } else if (BTN_OVERVIEW.equals(buttonId)) {
            showDebug = false;
            debugPage = 0;
        } else if (BTN_PREV.equals(buttonId)) {
            debugPage = Math.max(0, debugPage - 1);
        } else if (BTN_NEXT.equals(buttonId)) {
            debugPage++;
        }
        ui.recreateIntelUI();
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float opad = 10f;
        float pad  = 3f;
        float tabH = 30f;

        // Tab bar
        TooltipMakerAPI tabBar = panel.createUIElement(width, tabH, false);
        if (showDebug) {
            tabBar.addButton("Overview", BTN_OVERVIEW, 100f, 22f, 0f);
            tabBar.addButton("Debug", BTN_DEBUG,
                    Misc.getBrightPlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.C2_MENU, 100f, 22f, 10f);
        } else {
            tabBar.addButton("Overview", BTN_OVERVIEW,
                    Misc.getBrightPlayerColor(), Misc.getDarkPlayerColor(),
                    Alignment.MID, CutStyle.C2_MENU, 100f, 22f, 0f);
            tabBar.addButton("Debug", BTN_DEBUG, 100f, 22f, 10f);
        }
        panel.addUIElement(tabBar).inTL(0, 0);

        // Scrollable content
        TooltipMakerAPI content = panel.createUIElement(width, height - tabH - 4f, true);
        if (showDebug) {
            addShipDebugSection(content, opad, pad);
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

    private void addShipDebugSection(TooltipMakerAPI info, float opad, float pad) {
        if (detailEntries == null) {
            info.addPara("Debug data not available.", Misc.getGrayColor(), opad);
            return;
        }

        info.addSectionHeading(
            "Ship Filter Report  (" + shipDebug.size() + " hulls examined)",
            Alignment.LMID, opad
        );

        // Count-only groups — always shown above the paginated detail, cheap to render
        for (String reason : REASON_ORDER) {
            if (DETAIL_REASONS.contains(reason)) continue;
            int count = groupCounts.containsKey(reason) ? groupCounts.get(reason) : 0;
            if (count == 0) continue;
            info.addSectionHeading(reason + "  (" + count + ")  —  not listed", Alignment.LMID, pad);
        }

        // Paginated detail entries
        int total      = detailEntries.size();
        int totalPages = total == 0 ? 1 : (total + PAGE_SIZE - 1) / PAGE_SIZE;
        int page       = Math.max(0, Math.min(debugPage, totalPages - 1));
        int start      = page * PAGE_SIZE;
        int end        = Math.min(start + PAGE_SIZE, total);

        info.addSectionHeading(
            "Details  —  page " + (page + 1) + " of " + totalPages,
            Alignment.LMID, opad
        );
        addPageNav(info, page, totalPages, pad);

        if (total == 0) {
            info.addPara("No detail entries.", Misc.getGrayColor(), pad);
            return;
        }

        // If this page opens mid-group, re-show the heading with "(continued)" for context
        String lastReason = null;
        if (start > 0) {
            String startReason = detailEntries.get(start).filterReason;
            if (startReason.equals(detailEntries.get(start - 1).filterReason)) {
                int count = groupCounts.containsKey(startReason) ? groupCounts.get(startReason) : 0;
                info.addSectionHeading(startReason + " (" + count + ")  —  continued", Alignment.LMID, opad);
                lastReason = startReason;
            }
        }

        for (int i = start; i < end; i++) {
            ShipDebugEntry e = detailEntries.get(i);

            if (!e.filterReason.equals(lastReason)) {
                lastReason = e.filterReason;
                int count  = groupCounts.containsKey(lastReason) ? groupCounts.get(lastReason) : 0;
                boolean capped = count > MAX_ENTRIES_PER_GROUP;
                String label = lastReason + " (" + count + ")"
                        + (capped ? "  —  showing first " + MAX_ENTRIES_PER_GROUP : "");
                info.addSectionHeading(label, Alignment.LMID, opad);
            }

            Color entryColor = getEntryColor(e);
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

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Personal");
        return tags;
    }
}
