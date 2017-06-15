/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package buscript;

import buscript.util.FileTools;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.RegisteredServiceProvider;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Buscript extends ScriptManager {

    private Plugin plugin;

    private Permission permissions;
    private Economy economy;
    private Chat chat;

    private File scriptFile;
    private FileConfiguration scriptConfig;

    boolean runTasks = true;
    Map<String, List<Map<String, Object>>> delayedScripts = new HashMap<String, List<Map<String, Object>>>();

    /**
     * Creates a new Buscript object, which is used to execute Javascript script files.
     * <p>This object is not thread-safe so a new one should be created for each thread.</p>
     * <p>This constructor will automatically assign the variable name "plugin" to your plugin for script purposes.</p>
     *
     * @param plugin The plugin implementing this library.
     */
    public Buscript(Plugin plugin) {
        this(plugin, "plugin");
    }

    /**
     * Creates a new Buscript object, which is used to execute Javascript script files.
     *
     * <p>This object is not thread-safe so a new one should be created for each thread.</p>
     *
     * @param plugin The plugin implementing this library.
     * @param pluginScriptName The name of the variable the plugin will be referenced as in scripts.
     */
    public Buscript(Plugin plugin, String pluginScriptName) {
        super(new File(plugin.getDataFolder(), "scripts"), plugin.getLogger());
        this.plugin = plugin;

        // Adds the current server instance as a script variable "server".
        setScriptVariable("server", plugin.getServer());
        setScriptVariable(pluginScriptName, plugin);

        // Adds all the default Buscript global methods.
        addScriptMethods(new DefaultFunctions(this));
        // Sets up permissions with vault.
        setupVault();
        plugin.getServer().getPluginManager().registerEvents(new VaultListener(this), plugin);
        // Initializes the delayed script data.
        initData();
        // Starts up a task to check for scripts that need to run at a specific time.
        ScriptTask scriptTask = new ScriptTask(this);
        scriptTask.start();
        // Registers events with bukkit.
        plugin.getServer().getPluginManager().registerEvents(new BuscriptListener(this), plugin);
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
                                    getPlugin().getLogger().warning("Script data error, time reset");
                                    script.put(keyObj.toString(), 0);
                                }
                            }/* else if (keyObj.toString().equals("replacements")) {
                                Object obj = scriptMap.get(keyObj);
                                System.out.println(obj);
                            }*/ else {
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
        if (getPlugin().getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Permission> permissionProvider = getPlugin().getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permissions = permissionProvider.getProvider();
        }
        RegisteredServiceProvider<Economy> economyProvider = getPlugin().getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
        RegisteredServiceProvider<Chat> chatProvider = getPlugin().getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
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
        setScriptVariable("permissions", permissions);
        setScriptVariable("chat", chat);
        setScriptVariable("economy", economy);
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
        List<Map<String, Object>> replacements = new ArrayList<Map<String, Object>>(stringReplacers.size());
        for (StringReplacer r : stringReplacers) {
            Map<String, Object> replacement = new HashMap<String, Object>(2);
            String regex = r.getRegexString();
            if (regex != null) {
                replacement.put("regex", regex);
            }
            String replace = r.getReplacement();
            if (replace != null) {
                replacement.put("replace", replace);
            }
            String var = r.getGlobalVarName();
            if (var != null) {
                replacement.put("var", var);
            }
            replacements.add(replacement);
        }
        script.put("replacements", replacements);
        script.put("metaData", new HashMap<String, Object>(metaData));
        playerScripts.add(script);
        saveData();
    }

    /**
     * This method will remove any scripts scheduled to be executed for the target.  Null is a valid target.
     *
     * @param target The target to remove scheduled scripts for.
     */
    public void clearScheduledScripts(String target) {
        delayedScripts.remove(target);
        saveData();
    }

    /**
     * Binds a script to a Bukkit event.  The script will be run when the event fires with a "event" variable available
     * in the script.  The script file will be loaded into a cache for optimal performance.
     *
     * @param eventClassName The fully realized class name of the Bukkit event.
     * @param priorityString The priority for the event: LOWEST, LOW, NORMAL, HIGH, HIGHEST, MONITOR.
     * @param scriptFile The file containing the script.
     */
    public void registerEventScript(String eventClassName, String priorityString, File scriptFile) {
        EventPriority priority = EventPriority.valueOf(priorityString.toUpperCase());
        if (priority == null) {
            getPlugin().getLogger().warning(priorityString + " is not a valid EventPriority!");
            return;
        }
        Class eventClass;
        try {
            eventClass = Class.forName(eventClassName);
        } catch (ClassNotFoundException e) {
            getPlugin().getLogger().warning(e.getMessage());
            return;
        }
        if (!Event.class.isAssignableFrom(eventClass)) {
            getPlugin().getLogger().warning("Class must extend " + Event.class);
            return;
        }
        Method method;
        try {
            method = eventClass.getDeclaredMethod("getHandlerList");
        } catch (NoSuchMethodException ignore) {
            getPlugin().getLogger().warning(eventClass.getName() + " cannot be listened for!");
            return;
        }
        if (method == null) {
            getPlugin().getLogger().warning(eventClass.getName() + " cannot be listened for!");
            return;
        }
        HandlerList handlerList = null;
        try {
            method.setAccessible(true);
            Object handlerListObj = method.invoke(null);
            if (handlerListObj == null || !(handlerListObj instanceof HandlerList)) {
                getPlugin().getLogger().warning(eventClass.getName() + " cannot be listened for!");
                return;
            }
            handlerList = (HandlerList) handlerListObj;
        } catch (IllegalAccessException ignore) {
            getPlugin().getLogger().warning(eventClass.getName() + " cannot be listened for!");
            return;
        } catch (InvocationTargetException ignore) {
            getPlugin().getLogger().warning(eventClass.getName() + " cannot be listened for!");
            return;
        }
        Listener listener = new DefaultListener();
        EventExecutor eventExecutor = new DefaultEventExecutor(this, scriptFile.toString());
        RegisteredListener registeredListener = new RegisteredListener(listener, eventExecutor, priority, getPlugin(), false);
        handlerList.register(registeredListener);
    }
}
