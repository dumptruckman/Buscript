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
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;

import java.io.File;
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
    private Map<String, String> scriptCache = new HashMap<String, String>();

    private List<Map<String, Object>> delayedReplacements = null;

    private final List<StringReplacer> stringReplacers = new ArrayList<StringReplacer>();

    private Map<String, Object> metaData = new HashMap<String, Object>();

    boolean runTasks = true;
    Map<String, List<Map<String, Object>>> delayedScripts = new HashMap<String, List<Map<String, Object>>>();

    private static class TargetReplacer implements StringReplacer {

        private Buscript buscript;

        private TargetReplacer(Buscript buscript) {
            this.buscript = buscript;
        }

        @Override
        public String getRegexString() {
            return "%target%";
        }

        @Override
        public String getReplacement() {
            return buscript.getTarget();
        }

        @Override
        public String getGlobalVarName() {
            return "target";
        }
    }

    /**
     * Creates a new Buscript object, which is used to execute Javascript script files.
     * <p/>
     * This object is not thread-safe so a new one should be created for each thread.
     * <p/>
     * This constructor will automatically assign the variable name "plugin" to your plugin for script purposes.
     *
     * @param plugin The plugin implementing this library.
     */
    public Buscript(Plugin plugin) {
        this(plugin, "plugin");
    }

    /**
     * Creates a new Buscript object, which is used to execute Javascript script files.
     * <p/>This object is not thread-safe so a new one should be created for each thread.
     *
     * @param plugin The plugin implementing this library.
     * @param pluginScriptName The name of the variable the plugin will be referenced as in scripts.
     */
    public Buscript(Plugin plugin, String pluginScriptName) {
        this.plugin = plugin;
        registerStringReplacer(new TargetReplacer(this));
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
            global.put("server", global, plugin.getServer());
            global.put(pluginScriptName, global, plugin);
            global.put("metaData", global, metaData);
            global.put("NULL", global, NULL);
        } finally {
            Context.exit();
        }
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
     * Loops through all StringReplacers registered with this Buscript object and replaces their regex strings with
     * their replacement string and returns the result.  By default this includes a replacement of %t with the script's
     * current target.  This will also replace '&' with the appropriate color character.
     *
     * @param string The string to replace in.
     * @return The string that has had replacements for each registered StringReplacer.
     */
    public String stringReplace(String string) {
        if (string == null) {
            throw new IllegalArgumentException("string must not be null");
        }
        String result = string;
        if (delayedReplacements != null) {
            for (Map<String, Object> replacement : delayedReplacements) {
                Object regex = replacement.get("regex");
                Object replace = replacement.get("replace");
                if (regex != null) {
                    if (replace == null) {
                        replace = NULL;
                    }
                    result = result.replaceAll(regex.toString(), replace.toString());
                }
            }
        } else {
            for (StringReplacer r : stringReplacers) {
                String regex = r.getRegexString();
                if (regex == null) {
                    continue;
                }
                String replace = r.getReplacement();
                if (replace == null) {
                    replace = NULL;
                }
                result = result.replaceAll(regex, replace);
            }
        }
        result = ChatColor.translateAlternateColorCodes('&', result);
        return result;
    }

    /**
     * Adds a new {@link StringReplacer} to this Buscript instance which will allow built in global script functions
     * to replace strings as defined by the replacer.
     *
     * @param replacer the new StringReplacer to add.
     */
    public void registerStringReplacer(StringReplacer replacer) {
        Iterator<StringReplacer> it = stringReplacers.iterator();
        while (it.hasNext()) {
            StringReplacer r = it.next();
            if (r.getRegexString().equals(replacer.getRegexString())) {
                it.remove();
            }
        }
        stringReplacers.add(replacer);
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
            if (!method.getName().equals("getClassName")) {
                addScriptMethod(method.getName(), method, obj);
            }
        }
    }

    /**
     * Creates/sets a variable for use in the global scope.
     *
     * @param name The name of the variable which will be used in javascript as a "var".
     * @param object Value for the variable.
     */
    public void setScriptVariable(String name, Object object) {
        Context.enter();
        try {
            getGlobalScope().put(name, getGlobalScope(), object);
        } finally {
            Context.exit();
        }
    }

    /**
     * Obtains the value of a global scope variable.
     *
     * @param name The name of the javascript "var" to obtain.
     * @return The value of the global variable which will follow the same guidelines as
     * {@link Scriptable#get(String, org.mozilla.javascript.Scriptable)}.
     */
    public Object getScriptVariable(String name) {
        Context.enter();
        try {
            return getGlobalScope().get(name, getGlobalScope());
        } finally {
            Context.exit();
        }
    }

    /**
     * Obtains the value of a global scope variable that will be automatically casted to the type parameter.  The type
     * parameter may be limited by the confines of what
     * {@link Scriptable#get(String, org.mozilla.javascript.Scriptable)} returns.
     *
     * @param name The name of the javascript "var" to obtain.
     * @param type A class representing the type to cast the variable's value to.
     * @param <T> The type represented by the type parameter.
     * @return The value of of variable, automatically cast to the given type.  If unable to cast or value is null,
     * null will be returned.
     */
    public <T> T getScriptVariable(String name, Class<T> type) {
        try {
            return type.cast(getScriptVariable(name));
        } catch (ClassCastException e) {
            return null;
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Executes a javascript function.  The function must be declared in the scripting environment before this is
     * called.
     *
     * @param obj - Scriptable object for use as the 'this' object in javascript.
     * @param functionName The name of the javascript function.
     * @param args - Arguments for the script function.
     * @return The result of the function call.
     * @throws InvocationTargetException if the calling of the function resulted in an exception.
     * @throws FunctionNotFoundException if the named variable is not a function or its value is null.
     */
    public Object runScriptFunction(Scriptable obj, String functionName, Object... args)
            throws InvocationTargetException, FunctionNotFoundException {
        Object o = getScriptVariable(functionName);
        if (o.equals(Scriptable.NOT_FOUND)) {
            throw new FunctionNotFoundException("Variable '"+ functionName + "' not found!");
        } else if (o.equals(Context.getUndefinedValue())) {
            throw new FunctionNotFoundException("Variable '"+ functionName + "' is undefined!");
        }
        if(o instanceof Function){
            Function f = (Function)o;
            Context cx = Context.enter();
            try {
                return f.call(cx, getGlobalScope(), obj, args);
            } catch (Exception e) {
                throw new InvocationTargetException(e);
            } finally {
                Context.exit();
            }
        } else {
            throw new FunctionNotFoundException("'" + functionName + "' is not a valid function!");
        }
    }

    void executeDelayedScript(File scriptFile, List<Map<String, Object>> replacements, Map<String, Object> data) {
        if (data != null) {
            metaData = data;
        }
        delayedReplacements = replacements;
        executeScript(scriptFile, null, null);
        delayedReplacements = null;
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
     * Executes the given scriptFile with the given target.
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
        runScript(scriptFile, executor);
        this.target = null;
        metaData.clear();
    }

    /**
     * Executes the given script string (literal javascript) with no target.
     *
     * @param script The literal javascript to execute.
     * @param source The source of the script.  This can be anything except null.  It is what will show up if errors
     *               occur.
     */
    public void executeScript(String script, String source) {
        executeScript(script, source, null, null);
    }

    /**
     * Executes the given script string (literal javascript) with no target and messages the given executor if
     * anything goes wrong.
     *
     * @param script The literal javascript to execute.
     * @param source The source of the script.  This can be anything except null.  It is what will show up if errors
     *               occur.
     * @param executor the player to notify of errors.
     */
    public void executeScript(String script, String source, Player executor) {
        executeScript(script, source, null, executor);
    }

    /**
     * Executes the given script string (literal javascript) with the given target.
     *
     * @param script The literal javascript to execute.
     * @param source The source of the script.  This can be anything except null.  It is what will show up if errors
     *               occur.
     * @param target the target of the script which is used to replace the string '%t' and is added in the global scope
     *               as variable 'target'
     */
    public void executeScript(String script, String source, String target) {
        executeScript(script, source, target, null);
    }

    /**
     * Executes the given script string (literal javascript) with the specified target and messages the given executor
     * if anything goes wrong.
     *
     * @param script The literal javascript to execute.
     * @param source The source of the script.  This can be anything except null.  It is what will show up if errors
     *               occur.
     * @param target the target of the script which is used to replace the string '%t' and is added in the global scope
     *               as variable 'target'
     * @param executor the player to notify of errors.
     */
    public void executeScript(String script, String source, String target, Player executor) {
        this.target = target;
        runScript(script, source, executor);
        this.target = null;
        metaData.clear();
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

    void runScript(String script, String source, Player executor) {
        setup();
        Context cx = Context.enter();
        try {
            try{
                cx.evaluateString(getGlobalScope(), script, source, 1, null);
            } catch (Exception e) {
                getPlugin().getLogger().warning("Error running script: " + e.getMessage());
                if (executor != null) {
                    executor.sendMessage("Error running script: " + e.getMessage());
                }
            }
        } finally {
            Context.exit();
        }
    }

    void runScript(File script, Player executor) {
        setup();
        Context cx = Context.enter();
        try {
            Reader reader = null;
            try{

                reader = new FileReader(script);
                cx.evaluateReader(getGlobalScope(), reader, script.toString(), 1, null);
            } catch (Exception e) {
                getPlugin().getLogger().warning("Error running script: " + e.getMessage());
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

    private void setup() {
        Context cx = Context.enter();
        try {
            if (delayedReplacements != null) {
                for (Map<String, Object> replacement : delayedReplacements) {
                    Object var = replacement.get("var");
                    Object replace = replacement.get("replace");
                    if (var != null) {
                        if (replace == null) {
                            replace = NULL;
                        }
                        global.put(var.toString(), global, replace);
                    }
                }
            } else {
                for (StringReplacer r : stringReplacers) {
                    String var = r.getGlobalVarName();
                    String replace = r.getReplacement();
                    if (var != null) {
                        if (replace == null) {
                            replace = NULL;
                        }
                        global.put(var, global, replace);
                    }
                }
            }
            global.put("metaData", global, metaData);
        } finally {
            Context.exit();
        }
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

    void cacheScript(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                getPlugin().getLogger().warning(e.getMessage());
                return;
            }
        }

        scriptCache.put(fileName, FileTools.readFileAsString(file, plugin.getLogger()));
    }

    String getCachedScript(String fileName) {
        String cached = scriptCache.get(fileName);
        if (cached == null) {
            cacheScript(fileName);
            cached = scriptCache.get(fileName);
        }
        return cached != null ? cached : "";
    }

    /**
     * Clears scripts that have been cached so that they may be reloaded from the disk.  Scripts are typically cached
     * when set bound to an event.
     */
    public void clearScriptCache() {
        scriptCache.clear();
    }
}
