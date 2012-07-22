package buscript;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Buscript implements Listener {

    public static final String NULL = "!!NULL";

    String target = null;

    Scriptable global;

    Plugin plugin;
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
        Context cx = Context.enter();
        try {
            global = cx.initStandardObjects();
        } finally {
            Context.exit();
        }
        addScriptMethods(new DefaultFunctions(this));
        setupPermissions();
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
                            if (keyObj.toString().equals("time")) {
                                try {
                                    script.put(keyObj.toString(), Long.valueOf(scriptMap.get(keyObj).toString()));
                                } catch (NumberFormatException e) {
                                    plugin.getLogger().warning("Script data error, time reset");
                                    script.put(keyObj.toString(), 0);
                                }
                            } else {
                                script.put(keyObj.toString(), scriptMap.get(keyObj));
                            }
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

    public String replaceName(String string) {
        if (string == null) {
            throw new IllegalArgumentException("string must not be null");
        }
        String target = this.target;
        if (target == null) {
            target = Buscript.NULL;
        }
        return string.replaceAll("%t", target);
    }

    public void addScriptMethod(String name, Method method, Scriptable obj) {
        FunctionObject scriptMethod = new FunctionObject(name,
                method, obj);
        global.put(name, global, scriptMethod);
    }

    public void addScriptMethods(String[] names, Scriptable obj) {
        for (Method method : obj.getClass().getDeclaredMethods()) {
            for (String name : names) {
                if (method.getName().equals(name)) {
                    addScriptMethod(name, method, obj);
                }
            }
        }
    }

    public void addScriptMethods(Scriptable obj) {
        for (Method method : obj.getClass().getDeclaredMethods()) {
            addScriptMethod(method.getName(), method, obj);
        }
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

    public void executeScript(File scriptFile) {
        executeScript(scriptFile, null, null);
    }

    public void executeScript(File scriptFile, Player exectuor) {
        executeScript(scriptFile, null, exectuor);
    }

    public void executeScript(File scriptFile, String target) {
        executeScript(scriptFile, target, null);
    }

    public void executeScript(File scriptFile, String target, Player exectuor) {
        this.target = target;
        runScript(scriptFile, exectuor, target == null ? NULL : target);
    }

    public void scheduleScript(File scriptFile, long delay) {
        scheduleScript(scriptFile, null, delay);
    }

    public void scheduleScript(File scriptFile, String target, long delay) {
        if (target == null) {
            target = NULL;
        }
        List<Map<String, Object>> playerScripts = delayedScripts.get(target);
        if (playerScripts == null) {
            playerScripts = new ArrayList<Map<String, Object>>();
            delayedScripts.put(target, playerScripts);
        }
        Map<String, Object> script = new HashMap<String, Object>(2);
        script.put("time", System.currentTimeMillis() + delay);
        script.put("file", scriptFile.toString());
        playerScripts.add(script);
        saveData();
    }

    private void runScript(File script, Player executor, String target) {
        Context cx = Context.enter();
        try {
            global.put("server", global, Bukkit.getServer());
            global.put("target", global, target);
            Reader reader = null;
            try{
                reader = new FileReader(script);
                cx.evaluateReader(global, reader, script.toString(), 1, null);
            } catch (Exception e) {
                plugin.getLogger().warning("Error running script: " + e.getMessage());
                if (executor != null) {
                    executor.sendMessage("Error running script: " + e.getMessage());
                }
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ignore) { }
                }

            }
        } finally {
            Context.exit();
        }
    }
}
