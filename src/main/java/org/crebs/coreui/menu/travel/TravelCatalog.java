package org.crebs.coreui.menu.travel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Data the server travel screen draws: the server categories (전체 · 서버 · 거주 · 야생) and the servers in
 * each. Travelling only switches server (the player lands wherever that server puts arriving players).
 * <p>Which proxy server belongs to which category, and its display name and description, are fixed in
 * {@link #CATEGORIES} (the proxy does not know them). Whether a server is up, its players and max players, and the
 * player's own server come from the proxy at run time (Kokonut-velocity → Kokonut-Core {@code NetworkServers}).
 * Every server listed here always shows; one the proxy does not have (yet) is {@link Status#NOT_READY}.</p>
 *
 * @param current proxy name of the server the player is on, or null while unknown
 */
public record TravelCatalog(List<Category> categories, String current) {

    /** How a server is doing. Only an {@link #open} one can be travelled to. */
    public enum Status {
        SMOOTH("원활", true, "smooth"),
        BUSY("혼잡", true, "busy"),
        FULL("포화", false, "full"),
        DOWN("점검 중", false, "down"),
        /** Listed here but not registered on the proxy (velocity.toml) yet. */
        NOT_READY("준비 중", false, "down"),
        /** No report from the proxy yet; travel is still allowed (the proxy refuses it if the server is down). */
        UNKNOWN("확인 중", true, "down");

        public final String label;
        public final boolean open;
        private final String dot;

        Status(String label, boolean open, String dot) {
            this.label = label;
            this.open = open;
            this.dot = dot;
        }

        /** The coloured dot beside the label: green, yellow, red, grey. */
        public String dot() {
            return "travel/ui/dot_" + dot;
        }
    }

    /** What the proxy reported about one server. {@code maxPlayers} is 0 when unknown. */
    public record Live(boolean online, int players, int maxPlayers) {}

    /**
     * One backend server.
     *
     * @param id          the proxy's server name (velocity.toml)
     * @param description up to two lines (shown under the list for the chosen server, and in the row's tooltip)
     * @param players     players on it (0 while unknown)
     * @param maxPlayers  the server's max players, or 0 when unknown
     */
    public record Server(String id, String name, List<String> description, Status status, int players, int maxPlayers) {
        /** "12/100", "12명" without a max, "…" while unknown, "-" when down. */
        public String count() {
            if (status == Status.UNKNOWN) return "…";
            if (status == Status.DOWN || status == Status.NOT_READY) return "-";
            return maxPlayers > 0 ? players + "/" + maxPlayers : players + "명";
        }

        /** The status a live report gives: down, full at the max, busy from 80 % of it. */
        static Status status(Live live) {
            if (live == null) return Status.UNKNOWN;
            if (!live.online()) return Status.DOWN;
            if (live.maxPlayers() <= 0) return Status.SMOOTH;
            if (live.players() >= live.maxPlayers()) return Status.FULL;
            return live.players() * 5 >= live.maxPlayers() * 4 ? Status.BUSY : Status.SMOOTH;
        }
    }

    /**
     * A kind of server; its servers are channels of the same place. It may have none (shown as 준비 중).
     *
     * @param icon        vanilla item key
     * @param description one line (shown in the button's tooltip)
     */
    public record Category(String id, String name, String icon, String description, List<Server> servers) {
        public int online() {
            return servers.stream().mapToInt(Server::players).sum();
        }

        public Server server(String id) {
            return servers.stream().filter(s -> s.id().equals(id)).findFirst().orElse(null);
        }
    }

    public Category category(String id) {
        return categories.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    /** The category that has the server {@code server}, or null. */
    public Category categoryOf(String server) {
        if (server == null) return null;
        return categories.stream().filter(c -> c.server(server) != null).findFirst().orElse(null);
    }

    public int online() {
        // 전체 repeats the other categories' servers, so count those only.
        return categories.stream().filter(c -> !c.id().equals(ALL)).mapToInt(Category::online).sum();
    }

    /** One server of {@link #CATEGORIES}: proxy name, display name, description. */
    public record Definition(String id, String name, List<String> description) {}

    /** One category of {@link #CATEGORIES}. */
    public record Group(String id, String name, String icon, String description, List<Definition> servers) {}

    /**
     * The network as it is now.
     *
     * @param live     the proxy's report: proxy name → state
     * @param reported whether the proxy has reported at all; then servers missing from {@code live} are not on the
     *                 proxy ({@link Status#NOT_READY}), before that every server shows as {@link Status#UNKNOWN}
     * @param current  the player's own server, or null
     */
    public static TravelCatalog of(Map<String, Live> live, boolean reported, String current) {
        List<Category> categories = new ArrayList<>();
        for (Group group : CATEGORIES) {
            List<Server> servers = new ArrayList<>();
            for (Definition d : group.servers()) {
                Live state = live.get(d.id());
                Status status = reported && state == null ? Status.NOT_READY : Server.status(state);
                servers.add(new Server(d.id(), d.name(), d.description(), status,
                        state == null ? 0 : state.players(), state == null ? 0 : state.maxPlayers()));
            }
            categories.add(new Category(group.id(), group.name(), group.icon(), group.description(), List.copyOf(servers)));
        }
        // 전체: every server of the other categories, in their order, as the first tab.
        List<Server> every = categories.stream().flatMap(c -> c.servers().stream()).toList();
        categories.addFirst(new Category(ALL, "전체", "map", "모든 서버", every));
        return new TravelCatalog(List.copyOf(categories), current);
    }

    private static Definition home(int channel) {
        return new Definition("home" + channel, "거주 " + channel + "채널",
                List.of("집과 마을 영토가 있는 곳이에요.", "내 마을 홈은 /마을 에서 갈 수 있어요."));
    }

    private static Definition wild(int channel) {
        return new Definition("wild" + channel, "야생 " + channel + "채널",
                List.of("자원을 캐고 사냥하는 넓은 땅이에요.", "채널마다 지형이 달라요."));
    }

    /** Id of the 전체 tab that {@link #of} puts first: every server of {@link #CATEGORIES}. */
    public static final String ALL = "all";

    /**
     * The network's categories and servers (전체 is added by {@link #of}). Server ids must match the server names in
     * velocity.toml.
     */
    public static final List<Group> CATEGORIES = List.of(
            new Group("server", "서버", "compass", "로비와 스폰", List.of(
                    new Definition("lobby", "로비", List.of("처음 접속하면 도착하는 곳이에요.", "서버 안내를 볼 수 있어요.")),
                    new Definition("spawn", "스폰", List.of("모험가들이 모이는 중심 마을이에요.", "상점과 우체국, 게시판이 있어요.")))),
            new Group("home", "거주", "oak_door", "집과 마을 영토가 있는 곳", List.of(home(1), home(2), home(3), home(4), home(5))),
            new Group("wild", "야생", "iron_axe", "자원을 캐고 사냥하는 곳", List.of(wild(1), wild(2), wild(3), wild(4))));
}
