/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package buscript;

import buscript.util.TimeTools;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.javascript.ScriptableObject;

import java.io.File;

class DefaultFunctions extends ScriptableObject {

    @NotNull
    private final Buscript buscript;

    DefaultFunctions(@NotNull final Buscript buscript) {
        this.buscript = buscript;
    }

    @Override
    @NotNull
    public String getClassName() {
        return "DefaultFunctions";
    }

    public void broadcast(@NotNull final String message) {
        buscript.getPlugin().getServer().broadcastMessage(message);
    }

    public void broadcastPerm(@NotNull final String message, @NotNull final String permission) {
        buscript.getPlugin().getServer().broadcast(message, permission);
    }

    public void command(@NotNull final String command) {
        buscript.getPlugin().getServer().dispatchCommand(buscript.getPlugin().getServer().getConsoleSender(), command);
    }

    public void commandSpoof(@NotNull final String name, @NotNull final String command) {
        Player player = buscript.getPlugin().getServer().getPlayerExact(name);
        if (player != null) {
            buscript.getPlugin().getServer().dispatchCommand(player, command);
        }
    }

    public void message(@NotNull final String name, @NotNull final String message) {
        Player player = buscript.getPlugin().getServer().getPlayerExact(name);
        if (player != null) {
            player.sendMessage(message);
        }
    }

    public boolean hasPerm(@NotNull final String name, @NotNull final String permission) {
        Player player = buscript.getPlugin().getServer().getPlayerExact(name);
        return player != null && player.hasPermission(permission);
    }

    public boolean hasPermOffline(@NotNull final String world, @NotNull final String player, @NotNull final String permission) {
        final Permission permissions = buscript.getPermissions();
        if (permissions != null) {
            return permissions.has(world, player, permission);
        } else {
            throw new IllegalStateException("Vault must be installed to use hasPermOffline(world, player, perm)!");
        }
    }

    public void addPerm(@NotNull final String world, @NotNull final String player, @NotNull final String permission) {
        final Permission permissions = buscript.getPermissions();
        if (permissions != null) {
            permissions.playerAdd(world, player, permission);
        } else {
            throw new IllegalStateException("Vault must be installed to use addPerm(world, player, perm)!");
        }
    }

    public void removePerm(@NotNull final String world, @NotNull final String player, @NotNull final String permission) {
        final Permission permissions = buscript.getPermissions();
        if (permissions != null) {
            permissions.playerRemove(world, player, permission);
        } else {
            throw new IllegalStateException("Vault must be installed to use removePerm(world, player, perm)!");
        }
    }

    public boolean hasMoney(@NotNull final String player, @NotNull final Double money) {
        final Economy economy = buscript.getEconomy();
        if (economy != null) {
            return economy.has(player, money);
        } else {
            throw new IllegalStateException("Vault must be installed to use hasMoney(player, money)!");
        }
    }

    public boolean addMoney(@NotNull final String player, @NotNull final Double money) {
        final Economy economy = buscript.getEconomy();
        if (economy != null) {
            return economy.depositPlayer(player, money).transactionSuccess();
        } else {
            throw new IllegalStateException("Vault must be installed to use addMoney(player, money)!");
        }
    }

    public boolean removeMoney(@NotNull final String player, @NotNull final Double money) {
        final Economy economy = buscript.getEconomy();
        if (economy != null) {
            return economy.withdrawPlayer(player, money).transactionSuccess();
        } else {
            throw new IllegalStateException("Vault must be installed to use removeMoney(player, money)!");
        }
    }

    public boolean isOnline(@NotNull final String name) {
        return buscript.getPlugin().getServer().getPlayerExact(name) != null;
    }

    public void run(@NotNull final String script) {
        buscript.executeScript(new File(buscript.getScriptFolder(), script));
    }

    public void runTarget(@NotNull final String script, final String target) {
        buscript.executeScript(new File(buscript.getScriptFolder(), script), target);
    }

    public void runLater(@NotNull final String script, @NotNull final String delay) {
        long d = TimeTools.fromShortForm(delay);
        buscript.scheduleScript(new File(buscript.getScriptFolder(), script), d * 1000);
    }

    public void runLaterTarget(@NotNull final String script, @NotNull final String delay, final String target) {
        long d = TimeTools.fromShortForm(delay);
        buscript.scheduleScript(new File(buscript.getScriptFolder(), script), target, d * 1000);
    }

    public void clearScripts(final String target) {
        buscript.clearScheduledScripts(target);
    }

    public void registerEvent(@NotNull final String event, @NotNull final String priority, @NotNull final String script) {
        buscript.registerEventScript(event, priority, new File(buscript.getScriptFolder(), script));
    }
}
