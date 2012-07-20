package buscript;

import buscript.util.fscript.FSException;
import buscript.util.fscript.FScript;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Buscript implements Listener {

    public static final String NULL = "!!NULL";
    static String target = null;

    Plugin plugin;
    ScriptHandler scriptHandler;
    Permission permissions;
    boolean runTasks;
    File scriptFolder;
    File scriptFile;
    FileConfiguration scriptConfig;

    Map<String, List<Map<String, Object>>> delayedScripts = new HashMap<String, List<Map<String, Object>>>();

    public Buscript(Plugin plugin) {
        this.plugin = plugin;
        scriptFolder = new File(plugin.getDataFolder(), "scripts");
        if (!getScriptFolder().exists()) {
            getScriptFolder().mkdirs();
        }
        setupPermissions();
        scriptHandler = new ScriptHandler(this);
        scriptFile = new File(getScriptFolder(), "scripts.bin");
        scriptConfig = YamlConfiguration.loadConfiguration(scriptFile);
        ConfigurationSection scripts = scriptConfig.getConfigurationSection("scripts");
        if (scripts != null) {
            for (String player : scripts.getKeys(false)) {
                List<Map<String, Object>> playerScripts = new ArrayList<Map<String, Object>>();
                delayedScripts.put(player, playerScripts);
                for (Object scriptObj : scripts.getList(player)) {
                    if (scriptObj instanceof Map) {
                        Map scriptMap = (Map) scriptObj;
                        Map<String, Object> script = new HashMap<String, Object>(2);
                        for (Object keyObj : scriptMap.keySet()) {
                            script.put(keyObj.toString(), scriptMap.get(keyObj));
                        }
                        playerScripts.add(script);
                    }
                }
            }
        }
        runTasks = true;
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new ScriptTask(this), 20L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void pluginEnable(PluginEnableEvent event) {
        if (event.getPlugin().getName().equals("Vault")) {
            setupPermissions();
        }
    }

    @EventHandler
    public void pluginDisable(PluginDisableEvent event) {
        if (event.getPlugin().equals(plugin)) {
            runTasks = false;
            saveData();
        }
    }

    void saveData() {
        scriptConfig.set("scripts", delayedScripts);
        try {
            scriptConfig.save(scriptFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save script data: " + e.getMessage());
        }
    }

    boolean setupPermissions() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Permission> permissionProvider = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permissions = permissionProvider.getProvider();
        }
        return (permissions != null);
    }

    public File getScriptFolder() {
        return scriptFolder;
    }

    public FScript getFScript() {
        FScript fScript = new FScript();
        fScript.registerExtension(scriptHandler);
        return fScript;
    }

    public void executeScript(File scriptFile) {
        executeScript(scriptFile, null);
    }

    public void executeScript(File scriptFile, String player) {
        target = player;
        runScript(scriptFile);
    }

    public void scheduleScript(File scriptFile, long delay) {
        scheduleScript(scriptFile, delay, null);
    }

    public void scheduleScript(File scriptFile, long delay, String player) {
        if (player == null) {
            player = NULL;
        }
        List<Map<String, Object>> playerScripts = delayedScripts.get(player);
        if (playerScripts == null) {
            playerScripts = new ArrayList<Map<String, Object>>();
            delayedScripts.put(player, playerScripts);
        }

        Map<String, Object> script = new HashMap<String, Object>(2);
        script.put("time", System.currentTimeMillis() + delay);
        script.put("file", scriptFile.toString());
        playerScripts.add(script);
        saveData();
    }

    public ScriptHandler getScriptHandler() {
        return scriptHandler;
    }

    private void runScript(File script) {
        Reader reader = null;
        FScript fScript = getFScript();
        try{
            reader = new FileReader(script);
            fScript.load(reader);
            fScript.run();
        } catch (IOException e){
            plugin.getLogger().warning("Read error: " + e.getMessage());
        } catch (FSException e) {
            plugin.getLogger().warning("Error parsing script: " + e.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) { }
            }
        }
    }
}
