/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package buscript;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Buscript {

    public static final String NULL = "!!NULL";

    private String target = null;
    private Plugin plugin;
    private Scriptable global;
    private Permission permissions;
    private Economy economy;
    private Chat chat;
    private File scriptFolder;
    private File scriptFile;
    private FileConfiguration scriptConfig;

    boolean runTasks;
    Map<String, List<Map<String, Object>>> delayedScripts = new HashMap<String, List<Map<String, Object>>>();

    /**
     * Creates a new Buscript object, which is used to execute Javascript script files.  This object is not thread-safe
     * so a new one should be created for each thread.
     *
     * @param plugin The plugin implementing this library.
     */
    public Buscript(Plugin plugin) {
        this.plugin = plugin;
        // Create script folder in plugin's directory.
        scriptFolder = new File(plugin.getDataFolder(), "scripts");
        if (!getScriptFolder().exists()) {
            getScriptFolder().mkdirs();
        }
        // Initialize the context with a global object.
        Context cx = Context.enter();
        try {
            global = cx.initStandardObjects();
            // Adds the current server instance as a script variable "server".
            global.put("server", global, Bukkit.getServer());
            global.put("plugin", global, plugin);
        } finally {
            Context.exit();
        }
        // Adds all the default Buscript global methods.
        addScriptMethods(new DefaultFunctions(this));
        // Sets up permissions with vault.
        setupVault();
        // Initializes the delayed script data.
        initData();
        // Starts up a task to check for scripts that need to run at a specific time.
        runTasks = true;
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new ScriptTask(this), 20L);
        // Registers events with bukkit.
        Bukkit.getPluginManager().registerEvents(new BuscriptListener(this), plugin);
    }

    private void initData() {
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
    }

    void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Permission> permissionProvider = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permissions = permissionProvider.getProvider();
        }
        RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
        RegisteredServiceProvider<Chat> chatProvider = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
        if (chatProvider != null) {
            chat = chatProvider.getProvider();
        }
        updateVaultInGlobalScope();
    }

    void disableVault() {
        permissions = null;
        economy = null;
        chat = null;
        updateVaultInGlobalScope();
    }

    private void updateVaultInGlobalScope() {
        // Add vault to the script's global scope as variables.
        Context.enter();
        try {
            global.put("permissions", global, permissions);
            global.put("chat", global, chat);
            global.put("economy", global, economy);
        } finally {
            Context.exit();
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

    /**
     * Retrieves the plugin that is implementing this library.
     *
     * @return The plugin implementing the Buscript library.
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Retrieves the global scope object for this Buscript execution environment.
     *
     * @return The global scope object for this Buscript execution environment.
     */
    public Scriptable getGlobalScope() {
        return global;
    }

    /**
     * Gets the Vault permission API if enabled.
     *
     * @return the Vault permission API or null if not enabled.
     */
    public Permission getPermissions() {
        return permissions;
    }

    /**
     * Gets the Vault economy API if enabled.
     *
     * @return the Vault economy API or null if not enabled.
     */
    public Economy getEconomy() {
        return economy;
    }

    /**
     * Gets the Vault chat API if enabled.
     *
     * @return the Vault chat API or null if not enabled.
     */
    public Chat getChat() {
        return chat;
    }

    /**
     * Gets the current script target.  This may return null if the script is not set to execute on a target.
     * This only updates immediately preceding a script's execution.
     *
     * @return The current script target or null.
     */
    public String getTarget() {
        return target;
    }

    /**
     * Returns the folder that all scripts will launch from when defined to launch from within a script.
     *
     * @return The folder that all scripts will launch from when defined to launch from within a script.
     */
    public File getScriptFolder() {
        return scriptFolder;
    }

    /**
     * Allows the folder that all scripts will launch from when defined to launch from within a script to be
     * changed.
     *
     * @param folder The new folder that said scripts should launch from.
     */
    public void setScriptFolder(File folder) {
        if (!folder.isDirectory()) {
            throw new IllegalArgumentException("folder must be a directory!");
        }
        this.scriptFolder = folder;
    }

    /**
     * Replaces all instance of %t in a string with the scripts current target {@link #getTarget()}.  If no target has
     * been defined for the current script, %t will be replaced with !!NULL.
     *
     * @param string The string to replace in.
     * @return The string that has had replacements for %t.
     */
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

    /**
     * Adds a global method to the script environment.  The arguments for the method must match the specifications of
     * {@link FunctionObject#FunctionObject(String, java.lang.reflect.Member, org.mozilla.javascript.Scriptable)}
     * Adding a method with the same name as an existing method may result in conflicts.
     *
     * @param name the name as the method should be in the script environment.
     * @param method the java method to be linked.
     * @param obj the Scriptable object that must contain the method.
     */
    public void addScriptMethod(String name, Method method, Scriptable obj) {
        FunctionObject scriptMethod = new FunctionObject(name,
                method, obj);
        global.put(name, global, scriptMethod);
    }

    /**
     * Adds all methods from the given obj to the global scope that match the names given in names.
     *
     *
     * @param names The names of methods to add.
     * @param obj The object containing these methods.
     */
    public void addScriptMethods(String[] names, Scriptable obj) {
        for (Method method : obj.getClass().getDeclaredMethods()) {
            for (String name : names) {
                if (method.getName().equals(name)) {
                    addScriptMethod(name, method, obj);
                }
            }
        }
    }

    /**
     * Adds all methods from the given obj to the global scope.
     * Methods intended to be added should all have unique names or you may have conflicts.
     *
     * @param obj The object whose methods should be added.
     */
    public void addScriptMethods(Scriptable obj) {
        for (Method method : obj.getClass().getDeclaredMethods()) {
            addScriptMethod(method.getName(), method, obj);
        }
    }

    /**
     * Executes the given scriptFile with no target.
     *
     * @param scriptFile The file to execute.
     */
    public void executeScript(File scriptFile) {
        executeScript(scriptFile, null, null);
    }

    /**
     * Executes the given scriptFile with no target and messages the given executor if anything goes wrong.
     *
     * @param scriptFile the file to execute.
     * @param executor the player to notify of errors.
     */
    public void executeScript(File scriptFile, Player executor) {
        executeScript(scriptFile, null, executor);
    }

    /**
     *
     *
     * @param scriptFile the file to execute.
     * @param target the target of the script which is used to replace the string '%t' and is added in the global scope
     *               as variable 'target'
     */
    public void executeScript(File scriptFile, String target) {
        executeScript(scriptFile, target, null);
    }

    /**
     * Executes the given scriptFile with the specified target and messages the given executor if anything goes wrong.
     *
     * @param scriptFile the file to execute.
     * @param target the target of the script which is used to replace the string '%t' and is added in the global scope
     *               as variable 'target'
     * @param executor the player to notify of errors.
     */
    public void executeScript(File scriptFile, String target, Player executor) {
        this.target = target;
        runScript(scriptFile, executor, target == null ? NULL : target);
    }

    /**
     * Schedules a script to be run at a later time as specified by delay with no specified target.
     *
     * @param scriptFile the file to execute.
     * @param delay the delay for the script in milliseconds.
     */
    public void scheduleScript(File scriptFile, long delay) {
        scheduleScript(scriptFile, null, delay);
    }

    /**
     * Schedules a script to be run at a later time as specified by delay with the specified target.
     *
     * @param scriptFile the file to execute.
     * @param target the target of the script which is used to replace the string '%t' and is added in the global scope
     *               as variable 'target'
     * @param delay the delay for the script in milliseconds.
     */
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
