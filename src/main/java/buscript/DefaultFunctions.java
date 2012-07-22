package buscript;

import buscript.util.TimeTools;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.mozilla.javascript.ScriptableObject;

import java.io.File;

class DefaultFunctions extends ScriptableObject {

    private Buscript buscript;

    DefaultFunctions(Buscript buscript) {
        this.buscript = buscript;
    }

    @Override
    public String getClassName() {
        return "Buscript";
    }

    public void broadcast(String message) {
        Bukkit.broadcastMessage(buscript.replaceName(message));
    }

    public void broadcastPerm(String message, String permission) {
        Bukkit.broadcast(buscript.replaceName(message), permission);
    }

    public void command(String command) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), buscript.replaceName(command));
    }

    public void commandFake(String name, String command) {
        Player player = Bukkit.getPlayerExact(buscript.replaceName(name));
        if (player != null) {
            Bukkit.dispatchCommand(player, buscript.replaceName(command));
        }
    }

    public void message(String name, String message) {
        Player player = Bukkit.getPlayerExact(buscript.replaceName(name));
        if (player != null) {
            player.sendMessage(buscript.replaceName(message));
        }
    }

    public boolean hasPerm(String name, String permission) {
        Player player = Bukkit.getPlayerExact(buscript.replaceName(name));
        return player != null && player.hasPermission(permission);
    }

    public boolean hasPermOffline(String world, String name, String permission) {
        if (buscript.getPermissions() != null) {
            return buscript.getPermissions().has(world, buscript.replaceName(name), permission);
        } else {
            throw new IllegalStateException("Vault must be installed to use hasperm(world, player, perm)!");
        }
    }

    public boolean isOnline(String name) {
        return Bukkit.getPlayerExact(name) != null;
    }

    public void run(String script) {
        buscript.executeScript(new File(buscript.getScriptFolder(), buscript.replaceName(script)));
    }

    public void runTarget(String script, String target) {
        buscript.executeScript(new File(buscript.getScriptFolder(), buscript.replaceName(script)),
                buscript.replaceName(target));
    }

    public void runLater(String script, String delay) {
        long d = TimeTools.fromShortForm(delay);
        buscript.scheduleScript(new File(buscript.getScriptFolder(), buscript.replaceName(script)), d * 1000);
    }

    public void runLaterTarget(String script, String delay, String target) {
        long d = TimeTools.fromShortForm(delay);
        buscript.scheduleScript(new File(buscript.getScriptFolder(), buscript.replaceName(script)),
                buscript.replaceName(target), d * 1000);
    }
}
