package org.crebs.coreui.menu.kokonut;

import java.util.Map;
import java.util.WeakHashMap;
import org.bukkit.entity.Player;

/** Server-thread request identity; independent of the optional Mail plugin. */
public final class MailRequests {
    private static final Map<Player,Object> requests=new WeakHashMap<>();
    private MailRequests() { }
    public static Object begin(Player player) { Object ticket=new Object(); requests.put(player,ticket); return ticket; }
    public static boolean current(Player player,Object ticket) { return requests.get(player)==ticket; }
    public static void invalidate(Player player) { requests.remove(player); }
}
