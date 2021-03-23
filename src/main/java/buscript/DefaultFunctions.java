/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package buscript;

import buscript.util.TimeTools;
import org.bukkit.entity.Player;

import java.io.File;

public class DefaultFunctions {

    private Buscript buscript;

    DefaultFunctions(Buscript buscript) {
        this.buscript = buscript;
    }

    public void broadcast(String message) {
        buscript.getPlugin().getServer().broadcastMessage(buscript.stringReplace(message));
    }

    public void broadcastPerm(String message, String permission) {
        buscript.getPlugin().getServer().broadcast(buscript.stringReplace(message), permission);
    }

    public void command(String command) {
        buscript.getPlugin().getServer().dispatchCommand(buscript.getPlugin().getServer().getConsoleSender(), buscript.stringReplace(command));
    }

    public void commandSpoof(String name, String command) {
        Player player = buscript.getPlugin().getServer().getPlayerExact(buscript.stringReplace(name));
        if (player != null) {
            buscript.getPlugin().getServer().dispatchCommand(player, buscript.stringReplace(command));
        }
    }

    public void message(String name, String message) {
        Player player = buscript.getPlugin().getServer().getPlayerExact(buscript.stringReplace(name));
        if (player != null) {
            player.sendMessage(buscript.stringReplace(message));
        }
    }

    public boolean hasPerm(String name, String permission) {
        Player player = buscript.getPlugin().getServer().getPlayerExact(buscript.stringReplace(name));
        return player != null && player.hasPermission(permission);
    }

    public boolean hasPermOffline(String world, String player, String permission) {
        if (buscript.getPermissions() != null) {
            return buscript.getPermissions().has(world, buscript.stringReplace(player), permission);
        } else {
            throw new IllegalStateException("Vault must be installed to use hasPermOffline(world, player, perm)!");
        }
    }

    public void addPerm(String world, String player, String permission) {
        if (buscript.getPermissions() != null) {
            buscript.getPermissions().playerAdd(world, buscript.stringReplace(player), permission);
        } else {
            throw new IllegalStateException("Vault must be installed to use addPerm(world, player, perm)!");
        }
    }

    public void removePerm(String world, String player, String permission) {
        if (buscript.getPermissions() != null) {
            buscript.getPermissions().playerRemove(world, buscript.stringReplace(player), permission);
        } else {
            throw new IllegalStateException("Vault must be installed to use removePerm(world, player, perm)!");
        }
    }

    public boolean hasMoney(String player, Double money) {
        if (buscript.getEconomy() != null) {
            return buscript.getEconomy().has(buscript.stringReplace(player), money);
        } else {
            throw new IllegalStateException("Vault must be installed to use hasMoney(player, money)!");
        }
    }

    public boolean addMoney(String player, Double money) {
        if (buscript.getEconomy() != null) {
            return buscript.getEconomy().depositPlayer(buscript.stringReplace(player), money).transactionSuccess();
        } else {
            throw new IllegalStateException("Vault must be installed to use addMoney(player, money)!");
        }
    }

    public boolean removeMoney(String player, Double money) {
        if (buscript.getEconomy() != null) {
            return buscript.getEconomy().withdrawPlayer(buscript.stringReplace(player), money).transactionSuccess();
        } else {
            throw new IllegalStateException("Vault must be installed to use removeMoney(player, money)!");
        }
    }

    public boolean isOnline(String name) {
        return buscript.getPlugin().getServer().getPlayerExact(buscript.stringReplace(name)) != null;
    }

    public void run(String script) {
        buscript.executeScript(new File(buscript.getScriptFolder(), buscript.stringReplace(script)));
    }

    public void runTarget(String script, String target) {
        buscript.executeScript(new File(buscript.getScriptFolder(), buscript.stringReplace(script)),
                buscript.stringReplace(target));
    }

    public void runLater(String script, String delay) {
        long d = TimeTools.fromShortForm(delay);
        buscript.scheduleScript(new File(buscript.getScriptFolder(), buscript.stringReplace(script)), d * 1000);
    }

    public void runLaterTarget(String script, String delay, String target) {
        long d = TimeTools.fromShortForm(delay);
        buscript.scheduleScript(new File(buscript.getScriptFolder(), buscript.stringReplace(script)),
                buscript.stringReplace(target), d * 1000);
    }

    public void clearScripts(String target) {
        buscript.clearScheduledScripts(target);
    }

    public String stringReplace(String string) {
        return buscript.stringReplace(string);
    }

    public void registerEvent(String event, String priority, String script) {
        buscript.registerEventScript(event, priority, new File(buscript.getScriptFolder(), script));
    }
}
