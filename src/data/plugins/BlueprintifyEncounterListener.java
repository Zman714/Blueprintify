package data.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.Set;

public class BlueprintifyEncounterListener extends BaseCampaignEventListener {

    public BlueprintifyEncounterListener(boolean permaRegister) {
        super(permaRegister);
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI fleet, BattleAPI battle) {
        if (!battle.isPlayerInvolved()) return;

        MemoryAPI memory = Global.getSector().getPlayerMemoryWithoutUpdate();
        Set<String> marketFactionIds = BlueprintifyModPlugin.getMarketFactionIds();
        Set<String> entityKnownHulls = BlueprintifyModPlugin.getEntityKnownShips(marketFactionIds);

        for (CampaignFleetAPI enemyFleet : battle.getNonPlayerSideSnapshot()) {
            if (enemyFleet.getFaction() != null
                    && !marketFactionIds.contains(enemyFleet.getFaction().getId())) {
                memory.set("$blueprintify_encountered_" + enemyFleet.getFaction().getId(), true);
            }
            for (FleetMemberAPI member : enemyFleet.getFleetData().getMembersListCopy()) {
                if (member.isFighterWing()) continue;
                String hullId = member.getHullId();
                if (hullId == null) continue;
                ShipHullSpecAPI spec = member.getHullSpec();
                if (spec == null) continue;
                if (entityKnownHulls.contains(hullId) || spec.hasTag("codex_unlockable")) {
                    memory.set("$blueprintify_seen_" + hullId, true);
                }
            }
        }
    }
}
