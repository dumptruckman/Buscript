/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package buscript;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class ScriptTask implements Runnable {

    private Buscript buscript;
    private Plugin plugin;
    private int id = -1;

    ScriptTask(Buscript buscript) {
        this.plugin = buscript.getPlugin();
        this.buscript = buscript;
    }

    void start() {
        id = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this, 20L, 20L);
    }

    void kill() {
        plugin.getServer().getScheduler().cancelTask(id);
    }

    @Override
    public void run() {
        if (!buscript.runTasks) {
            kill();
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
                                    try {
                                        final List<Map<String, Object>> replacements = (List<Map<String, Object>>) script.get("replacements");
                                        final Map<String, Object> metaData = (Map<String, Object>) script.get("metaData");
                                        buscript.getPlugin().getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                                            @Override
                                            public void run() {
                                                buscript.executeDelayedScript(scriptFile, replacements, metaData);
                                            }
                                        });
                                        scriptsIt.remove();
                                        removed = true;
                                    } catch (ClassCastException e) {
                                        plugin.getLogger().warning("Invalid delayed script entry");
                                        scriptsIt.remove();
                                        removed = true;
                                    }
                                } else {
                                    try {
                                        scriptFile.createNewFile();
                                    } catch (IOException ignore) { }
                                    if (scriptFile.exists()) {
                                        try {
                                            final List<Map<String, Object>> replacements = (List<Map<String, Object>>) script.get("replacements");
                                            final Map<String, Object> metaData = (Map<String, Object>) script.get("metaData");
                                            buscript.getPlugin().getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
                                                @Override
                                                public void run() {
                                                    buscript.executeDelayedScript(scriptFile, replacements, metaData);
                                                }
                                            });
                                            scriptsIt.remove();
                                            removed = true;
                                        } catch (ClassCastException e) {
                                            scriptsIt.remove();
                                            removed = true;
                                            System.out.println("could not cast");
                                        }
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
    }


}