package org.crebs.coreui.menu.kokonut;

import kr.gomsv.kokonutCore.api.network.NetworkServers;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.crebs.coreui.menu.travel.TravelCatalog;

import java.util.HashMap;
import java.util.Map;

/**
 * Kokonut-Core's {@link NetworkServers} (fed by Kokonut-velocity) behind the travel screen. Only load this class
 * after confirming Kokonut-Core is enabled.
 */
public final class NetworkBridge {
    private NetworkBridge() {
    }

    /** The network as the proxy last reported it; null when Core has no {@link NetworkServers} (older Core). */
    public static TravelCatalog catalog(Server server) {
        NetworkServers network = server.getServicesManager().load(NetworkServers.class);
        if (network == null) return null;
        Map<String, TravelCatalog.Live> live = new HashMap<>();
        for (NetworkServers.ServerStatus s : network.servers()) {
            live.put(s.name(), new TravelCatalog.Live(s.online(), s.players(), s.maxPlayers()));
        }
        return TravelCatalog.of(live, network.reported(), network.currentServer().orElse(null));
    }

    /** Asks the proxy to move {@code player}; false when Core has no {@link NetworkServers}. */
    public static boolean travel(Server server, Player player, String target) {
        NetworkServers network = server.getServicesManager().load(NetworkServers.class);
        if (network == null) return false;
        network.travel(player, target);
        return true;
    }
}
