package data.plugins;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.codex.CodexDataV2;
import com.fs.starfarer.api.impl.codex.CodexEntryPlugin;

import java.util.List;
import java.util.Set;

public class BlueprintifyOwnershipScript implements EveryFrameScript {

    private static final float CHECK_INTERVAL = 60f;
    private float elapsed = 0f;
    private boolean wasPaused = false;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        boolean isPaused = (amount == 0f);

        if (isPaused) {
            if (!wasPaused) {
                wasPaused = true;
                runCheck();
            }
            return;
        }

        wasPaused = false;
        elapsed += amount;
        if (elapsed < CHECK_INTERVAL) return;
        elapsed = 0f;
        runCheck();
    }

    void runCheck() {
        if (Global.getSector() == null) return;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        Set<String> marketFactionIds = BlueprintifyModPlugin.getMarketFactionIds();
        Set<String> entityKnownHulls = BlueprintifyModPlugin.getEntityKnownShips(marketFactionIds);

        checkMembers(fleet.getFleetData().getMembersListCopy(), memory);

        FleetDataAPI mothballed = fleet.getCargo().getMothballedShips();
        if (mothballed != null) {
            checkMembers(mothballed.getMembersListCopy(), memory);
        }

        checkCodex(memory, entityKnownHulls);
    }

    private void checkMembers(List<FleetMemberAPI> members, MemoryAPI memory) {
        for (FleetMemberAPI member : members) {
            if (member.isFighterWing()) continue;
            ShipHullSpecAPI spec = member.getHullSpec();
            if (spec == null || spec.isDHull()) continue;
            memory.set("$blueprintify_seen_" + spec.getHullId(), true);
        }
    }

    private void checkCodex(MemoryAPI memory, Set<String> entityKnownHulls) {
        if (CodexDataV2.ENTRIES == null) return;
        for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
            if (!spec.isBaseHull()) continue;
            String hullId = spec.getHullId();
            if (!spec.hasTag("codex_unlockable") && !entityKnownHulls.contains(hullId)) continue;
            CodexEntryPlugin entry = CodexDataV2.ENTRIES.get(hullId);
            if (entry != null && entry.isVisible()) {
                memory.set("$blueprintify_seen_" + hullId, true);
            }
        }
    }
}
