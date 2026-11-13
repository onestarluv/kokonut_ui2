package org.crebs.coreui.menu.skill;

import org.crebs.coreui.menu.skill.SkillCatalog.Branch;
import org.crebs.coreui.menu.skill.SkillCatalog.LevelReward;
import org.crebs.coreui.menu.skill.SkillCatalog.Node;
import org.crebs.coreui.menu.skill.SkillCatalog.Skill;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PROTOTYPE: each player's 채광 progress, kept in server memory (gone after a restart). Everyone starts from the
 * same sample: Lv. 46, six learned nodes, two claimed rewards.
 */
final class SkillStore {
    enum Status { LEARNED, OPEN, LOCKED }

    /** Immutable snapshot; {@code learned} holds node ids, {@code claimed} the levels of claimed rewards. */
    record Progress(int level, long xp, long xpNeed, Set<String> learned, Set<Integer> claimed) {
        Progress {
            learned = Set.copyOf(learned);
            claimed = Set.copyOf(claimed);
        }

        boolean has(String id) {
            return SkillCatalog.HUB.equals(id) || learned.contains(id);
        }

        int earned(Skill skill) {
            return level / skill.levelsPerPoint();
        }

        int spent(Skill skill) {
            return skill.nodes().stream().filter(n -> learned.contains(n.id())).mapToInt(Node::cost).sum();
        }

        /** Points left to invest. */
        int points(Skill skill) {
            return earned(skill) - spent(skill);
        }

        /** OPEN: every requirement is learned and the level is reached (points are checked by {@link #canInvest}). */
        Status status(Node n) {
            if (learned.contains(n.id())) return Status.LEARNED;
            return level >= n.level() && n.requires().stream().allMatch(this::has) ? Status.OPEN : Status.LOCKED;
        }

        boolean canInvest(Skill skill, Node n) {
            return status(n) == Status.OPEN && points(skill) >= n.cost();
        }

        boolean claimable(LevelReward r) {
            return level >= r.level() && !claimed.contains(r.level());
        }

        /** Points invested in each branch. */
        Map<Branch, Integer> branchSpent(Skill skill) {
            Map<Branch, Integer> out = new LinkedHashMap<>();
            for (Branch b : Branch.values()) out.put(b, 0);
            for (Node n : skill.nodes()) if (learned.contains(n.id())) out.merge(n.branch(), n.cost(), Integer::sum);
            return out;
        }
    }

    private static final Map<UUID, Progress> PLAYERS = new ConcurrentHashMap<>();

    private SkillStore() {
    }

    static Progress sample() {
        return new Progress(46, 1840, 2600, Set.of("sense", "geology", "hawkeye", "arm", "focus", "whet"), Set.of(10, 20));
    }

    static Progress of(UUID player) {
        return PLAYERS.computeIfAbsent(player, id -> sample());
    }

    /** Learns {@code node} when it is open and affordable; false otherwise. */
    static boolean learn(UUID player, Skill skill, Node node) {
        Progress p = of(player);
        if (!p.canInvest(skill, node)) return false;
        Set<String> learned = new HashSet<>(p.learned());
        learned.add(node.id());
        PLAYERS.put(player, new Progress(p.level(), p.xp(), p.xpNeed(), learned, p.claimed()));
        return true;
    }

    /** Forgets every node; returns the points given back. */
    static int reset(UUID player, Skill skill) {
        Progress p = of(player);
        int back = p.spent(skill);
        PLAYERS.put(player, new Progress(p.level(), p.xp(), p.xpNeed(), Set.of(), p.claimed()));
        return back;
    }

    /** Marks the reward as claimed when the level is reached and it was not claimed before. */
    static boolean claim(UUID player, LevelReward reward) {
        Progress p = of(player);
        if (!p.claimable(reward)) return false;
        Set<Integer> claimed = new HashSet<>(p.claimed());
        claimed.add(reward.level());
        PLAYERS.put(player, new Progress(p.level(), p.xp(), p.xpNeed(), p.learned(), claimed));
        return true;
    }
}
