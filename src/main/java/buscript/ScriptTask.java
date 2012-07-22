/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package buscript;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class ScriptTask implements Runnable {

    private Buscript buscript;
    private Plugin plugin;

    ScriptTask(Buscript buscript) {
        this.plugin = buscript.getPlugin();
        this.buscript = buscript;
    }

    @Override
    public void run() {
        if (!buscript.runTasks) {
            return;
        }
        long time = System.currentTimeMillis();
        Iterator<Map.Entry<String, List<Map<String, Object>>>> allScriptsIt = buscript.delayedScripts.entrySet().iterator();
        while (allScriptsIt.hasNext()) {
            final Map.Entry<String, List<Map<String, Object>>> entry = allScriptsIt.next();
            boolean removed = false;
            Iterator<Map<String, Object>> scriptsIt = entry.getValue().iterator();
            while (scriptsIt.hasNext()) {
                final Map<String, Object> script = scriptsIt.next();
                if (script.get("time") != null) {
                    try {
                        long scriptTime = (Long) script.get("time");
                        if (time >= scriptTime) {
                            if (script.get("file") != null) {
                                final File scriptFile = new File(script.get("file").toString());
                                if (scriptFile.exists()) {
                                    if (entry.getKey().equals(Buscript.NULL)) {
                                        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                                            @Override
                                            public void run() {
                                                buscript.executeScript(scriptFile);
                                            }
                                        });
                                    } else {
                                        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                                            @Override
                                            public void run() {
                                                buscript.executeScript(scriptFile, entry.getKey());
                                            }
                                        });
                                    }
                                    scriptsIt.remove();
                                    removed = true;
                                } else {
                                    try {
                                        scriptFile.createNewFile();
                                    } catch (IOException ignore) { }
                                    if (scriptFile.exists()) {
                                        if (entry.getKey().equals(Buscript.NULL)) {
                                            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                                                @Override
                                                public void run() {
                                                    buscript.executeScript(scriptFile);
                                                }
                                            });
                                        } else {
                                            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                                                @Override
                                                public void run() {
                                                    buscript.executeScript(scriptFile, entry.getKey());
                                                }
                                            });
                                        }
                                        scriptsIt.remove();
                                        removed = true;
                                    } else {
                                        plugin.getLogger().warning("Missing script file: " + scriptFile);
                                        scriptsIt.remove();
                                        removed = true;
                                    }
                                }
                            } else {
                                plugin.getLogger().warning("Invalid delayed script entry");
                                scriptsIt.remove();
                                removed = true;
                            }
                        }
                    } catch (NumberFormatException ignore) {
                        plugin.getLogger().warning("Invalid delayed script entry");
                        scriptsIt.remove();
                        removed = true;
                    }
                } else {
                    plugin.getLogger().warning("Invalid delayed script entry");
                    scriptsIt.remove();
                    removed = true;
                }
            }
            if (removed) {
                buscript.saveData();
            }
        }
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 20L);
    }
}