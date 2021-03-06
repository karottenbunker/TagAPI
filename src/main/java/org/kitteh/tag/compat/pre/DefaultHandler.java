package org.kitteh.tag.compat.pre;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.kitteh.tag.api.PacketHandler;
import org.kitteh.tag.api.TagHandler;
import org.kitteh.tag.api.Packet;
import org.kitteh.tag.api.TagAPIException;

import net.minecraft.server.NetworkManager;
import net.minecraft.server.Packet20NamedEntitySpawn;

public class DefaultHandler extends PacketHandler {

    private Field syncField;
    private Field highField;

    public DefaultHandler(TagHandler handler) {
        super(handler);
        try {
            this.syncField = NetworkManager.class.getDeclaredField("h");
            this.syncField.setAccessible(true);
            this.highField = NetworkManager.class.getDeclaredField("highPriorityQueue");
            this.highField.setAccessible(true);
            net.minecraft.server.EntityPlayer.class.getDeclaredField("netServerHandler");
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.SEVERE, "TagAPI is only compatible with 1.4 and beyond.", e);
            this.plugin.getServer().getPluginManager().disablePlugin(this.plugin);
            return;
        }
    }

    @Override
    public void hookPlayer(Player player) {
        try {
            this.listSwap(player, Collections.synchronizedList(new ArrayLizt<Object>(player)), false);
        } catch (final Exception e) {
            new TagAPIException("[TagAPI] Failed to inject into networkmanager for " + player.getName(), e).printStackTrace();
        }
    }

    @Override
    public void releasePlayer(Player player) {
        try {
            this.listSwap(player, Collections.synchronizedList(new ArrayList<Object>()), true);
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to restore " + player.getName() + ". Could be a problem.", e);
        }
    }

    @Override
    public void shutdown() {
        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            if (player != null) {
                this.releasePlayer(player);
            }
        }
    }

    private void listSwap(Player player, List<Object> list, boolean onlyIfOldIsHacked) throws IllegalArgumentException, IllegalAccessException {
        final NetworkManager nm = (NetworkManager) ((CraftPlayer) player).getHandle().netServerHandler.networkManager;
        final List<?> old = (List<?>) this.highField.get(nm);
        if (onlyIfOldIsHacked) {
            if (!(old instanceof ArrayLizt)) {
                return;
            }
        }
        synchronized (this.syncField.get(nm)) {
            for (final Object object : old) {
                list.add(object);
            }
            this.highField.set(nm, list);
        }
    }

    @Override
    protected void handlePacketAdd(Object o, Player owner) {
        if (o instanceof Packet20NamedEntitySpawn) {
            try {
                final Packet20NamedEntitySpawn packet = ((Packet20NamedEntitySpawn) o);
                final Packet p = new Packet(packet.b, packet.a);
                this.handler.packet(p, owner);
                packet.b = p.tag;
            } catch (final Exception e) {
                // Just in case!
            }
        }
    }
}
