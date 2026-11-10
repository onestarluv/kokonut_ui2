package org.crebs.coreui.menu.quest;

import org.bukkit.entity.Player;
import org.crebs.coreui.menu.quest.QuestCatalog.Quest;
import org.crebs.coreui.menu.quest.QuestCatalog.Status;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PROTOTYPE store: quest status changes (accept at an NPC, abandon, complete) per player, in memory only, so the
 * NPC screen and the journal agree until the server restarts. A real quest plugin replaces this.
 */
final class QuestProgress {
    private static final Map<UUID, Map<String, Status>> MOVED = new HashMap<>();

    private QuestProgress() {
    }

    /** Status lookup for one player; offline checks and the preview pass {@link #of(Map)} instead. */
    interface View {
        Status status(Quest quest);
    }

    static View of(Map<String, Status> moved) {
        return q -> moved.getOrDefault(q.id(), q.status());
    }

    static View of(Player player) {
        return of(MOVED.getOrDefault(player.getUniqueId(), Map.of()));
    }

    static void set(Player player, Quest quest, Status status) {
        MOVED.computeIfAbsent(player.getUniqueId(), id -> new HashMap<>()).put(quest.id(), status);
    }
}
