/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package buscript;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

public class BuscriptListener implements Listener {

    private Buscript buscript;

    public BuscriptListener(Buscript buscript) {
        this.buscript = buscript;
    }

    /**
     * This handler listens for Vault to be enabled so that it's api can be hooked even if the plugin implementing
     * Buscript does not depend on Vault.
     *
     * @param event event thrown when a plugin is enabled.
     */
    @EventHandler
    public void pluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("Vault")) {
            buscript.setupVault();
        }
    }

    /**
     * Listens for the plugin implementing Buscript to be disabled so that Buscript can be shut down properly.
     * Also listens for vault to be disabled so that buscript can unhook it's API.
     *
     * @param event event thrown when a plugin is disabled.
     */
    @EventHandler
    public void pluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(buscript.getPlugin())) {
            buscript.runTasks = false;
            buscript.saveData();
        }
        if (event.getPlugin().getName().equals("Vault")) {
            buscript.disableVault();
        }
    }
}
