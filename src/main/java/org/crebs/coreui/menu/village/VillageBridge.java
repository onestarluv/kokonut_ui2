package org.crebs.coreui.menu.village;

import kr.gomsv.kokonutCore.api.village.VillageInfo;
import kr.gomsv.kokonutCore.api.village.VillageInvite;
import kr.gomsv.kokonutCore.api.village.VillageRole;
import kr.gomsv.kokonutCore.api.village.VillageService;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.crebs.coreui.menu.village.VillageMenu.Backend;
import org.crebs.coreui.menu.village.VillageMenu.Reply;
import org.crebs.coreui.menu.village.VillageStore.Invite;
import org.crebs.coreui.menu.village.VillageStore.Member;
import org.crebs.coreui.menu.village.VillageStore.Overview;
import org.crebs.coreui.menu.village.VillageStore.Role;
import org.crebs.coreui.menu.village.VillageStore.Village;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Kokonut-Core's {@link VillageService} as the screens' {@link Backend}. The only village class that names Core types:
 * load it only after confirming Kokonut-Core is enabled, and treat a {@link LinkageError} as "Core too old".
 */
final class VillageBridge implements Backend {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.systemDefault());
    private final Server server;
    private final VillageService service;

    private VillageBridge(Server server, VillageService service) {
        this.server = server;
        this.service = service;
    }

    /** Null when Core has no village service registered. */
    static Backend find(Server server) {
        VillageService service = server.getServicesManager().load(VillageService.class);
        return service == null ? null : new VillageBridge(server, service);
    }

    @Override
    public void load(Player p, Consumer<Overview> done) {
        service.overview(p, o -> {
            if (o.failed()) { done.accept(null); return; }
            done.accept(new Overview(o.village() == null ? null : village(o.village()),
                    o.invites().stream().map(VillageBridge::invite).toList(), service.createCost(), service.maxMembers(0)));
        });
    }

    private Village village(VillageInfo v) {
        var members = v.members().stream().map(m -> new Member(m.id(), m.nickname(), m.name(), role(m.role()),
                day(m.joinedAt()), lastSeen(m.id(), m.lastSeen()), m.intro())).toList();
        String home = v.home() == null ? "-" : v.home().describe();
        return new Village(v.id(), v.name(), v.grade(), members, v.maxMembers(), v.chunks(), kr.gomsv.kokonutCore.api.village.VillageRules.MAX_CHUNKS,
                service.chunkCost((int) v.chunks() + 1), home, day(v.createdAt()), v.icon(),
                v.notice(), v.noticeBy() == null ? "" : v.noticeBy(), day(v.noticeAt()));
    }

    private static Invite invite(VillageInvite i) {
        return new Invite(i.villageId(), i.villageName(), i.icon(), i.grade(), i.members(), i.maxMembers(), i.inviterName(), day(i.invitedAt()));
    }

    private static Role role(VillageRole role) {
        return Role.valueOf(role.name());
    }

    private static String day(Instant at) {
        return at == null ? "-" : DAY.format(at);
    }

    /** "접속 중" when online on this server; otherwise how long ago the member was last seen. */
    private String lastSeen(UUID id, Instant at) {
        if (server.getPlayer(id) != null) return "접속 중";
        if (at == null) return "-";
        Duration ago = Duration.between(at, Instant.now());
        if (ago.toMinutes() < 5) return "방금 전";
        if (ago.toHours() < 1) return ago.toMinutes() + "분 전";
        if (ago.toDays() < 1) return ago.toHours() + "시간 전";
        if (ago.toDays() < 2) return "어제";
        if (ago.toDays() < 30) return ago.toDays() + "일 전";
        return day(at);
    }

    private static Consumer<VillageService.Result> reply(Consumer<Reply> done) {
        return r -> done.accept(new Reply(r.success(), r.message()));
    }

    @Override public void create(Player p, String name, Consumer<Reply> done) { service.create(p, name, reply(done)); }
    @Override public void invite(Player p, String target, Consumer<Reply> done) { service.invite(p, target, reply(done)); }
    @Override public void accept(Player p, long village, Consumer<Reply> done) { service.accept(p, village, reply(done)); }
    @Override public void decline(Player p, long village, Consumer<Reply> done) { service.decline(p, village, reply(done)); }
    @Override public void leave(Player p, Consumer<Reply> done) { service.leave(p, reply(done)); }
    @Override public void kick(Player p, UUID target, Consumer<Reply> done) { service.kick(p, target, reply(done)); }
    @Override public void setRole(Player p, UUID target, Role role, Consumer<Reply> done) {
        service.setRole(p, target, VillageRole.valueOf(role.name()), reply(done));
    }
    @Override public void setIcon(Player p, String icon, Consumer<Reply> done) { service.setIcon(p, icon, reply(done)); }
    @Override public void setNotice(Player p, String text, Consumer<Reply> done) { service.setNotice(p, text, reply(done)); }
    @Override public void setIntro(Player p, String text, Consumer<Reply> done) { service.setIntro(p, text, reply(done)); }
    @Override public void home(Player p, Consumer<Reply> done) { service.teleportHome(p, reply(done)); }
    @Override public void claimChunk(Player p, Consumer<Reply> done) { service.claimChunk(p, reply(done)); }
}
