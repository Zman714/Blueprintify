package data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class BlueprintifyStatusIntel extends BaseIntelPlugin {

    private List<String> ships;    // hull IDs
    private List<String> weapons;  // weapon names
    private List<String> fighters; // fighter names

    public BlueprintifyStatusIntel(List<String> ships, List<String> weapons, List<String> fighters) {
        update(ships, weapons, fighters);
    }

    public void update(List<String> ships, List<String> weapons, List<String> fighters) {
        this.ships = new ArrayList<>(ships);
        this.weapons = sorted(weapons);
        this.fighters = sorted(fighters);
    }

    private static List<String> sorted(List<String> in) {
        List<String> out = new ArrayList<>(in);
        Collections.sort(out);
        return out;
    }

    @Override
    protected String getName() {
        return "Blueprintify";
    }

    @Override
    public String getIcon() {
        return "graphics/icons/intel/codex_update.png";
    }

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
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        float opad = 10f;
        float pad = 3f;
        float headerHeight = 48f;

        TooltipMakerAPI header = panel.createUIElement(width, headerHeight, false);
        header.addPara(
            "The following blueprints have been added to normal salvage drop pools by Blueprintify. "
            + "They can appear in derelicts, ruins, and other salvageable sites.",
            opad
        );
        panel.addUIElement(header).inTL(0, 0);

        TooltipMakerAPI content = panel.createUIElement(width, height - headerHeight - 24f, true);
        addShipSection(content, ships, opad, pad);
        addSection(content, "Weapons (" + weapons.size() + ")", weapons, opad, pad);
        addSection(content, "Fighters (" + fighters.size() + ")", fighters, opad, pad);
        panel.addUIElement(content).belowLeft(header, 0);
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
            int rows = (members.size() + cols - 1) / cols;
            info.addShipList(cols, rows, 58f, Misc.getTextColor(), members, opad);
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

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("Personal");
        return tags;
    }
}
