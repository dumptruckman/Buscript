/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package buscript;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Buscript {

    @NotNull // Wow. That's a bit contradictory, huh?
    public static final String NULL = "!!NULL";

    @NotNull
    private final Plugin plugin;
    @NotNull
    private final File scriptFolder;
    @NotNull
    private final Scriptable globalScope;
    @NotNull
    private final Map<String, CachedScript> scriptCache = new HashMap<String, CachedScript>();

    @NotNull
    final Map<String, List<Map<String, Object>>> delayedScripts = new HashMap<String, List<Map<String, Object>>>();

    @NotNull
    private Map<String, Object> metaData = new HashMap<String, Object>();
    @NotNull
    private final File scriptFile;
    @NotNull
    private FileConfiguration scriptConfig;

    @Nullable
    private Permission permissions;
    @Nullable
    private Economy economy;
    @Nullable
    private Chat chat;

    boolean runTasks = true;

    /**
     * Creates a new Buscript object, which is used to execute Javascript script files.
     * <p/>
     * This object is not thread-safe so a new one should be created for each thread.
     * <p/>
     * This constructor will automatically assign the variable name "plugin" to your plugin for script purposes.
     *
     * @param plugin The plugin implementing this library.
     */
    public Buscript(@NotNull final Plugin plugin) {
        this(plugin, "plugin");
    }

    /**
     * Creates a new Buscript object, which is used to execute Javascript script files.
     * <p/>This object is not thread-safe so a new one should be created for each thread.
     *
     * @param plugin The plugin implementing this library.
     * @param pluginScriptName The name of the variable the plugin will be referenced as in scripts.
     */
    public Buscript(@NotNull final Plugin plugin, @NotNull final String pluginScriptName) {
        this(plugin, pluginScriptName, new File(plugin.getDataFolder(), "scripts"));
    }

    /**
     * Creates a new Buscript object, which is used to execute Javascript script files.
     * <p/>
     * This object is not thread-safe so a new one should be created for each thread.
     *
     * @param plugin The plugin implementing this library.
     * @param pluginScriptName The name of the variable the plugin will be referenced as in scripts.
     * @param scriptFolder The folder to store scripts in.
     */
    public Buscript(@NotNull final Plugin plugin, @NotNull final String pluginScriptName, @NotNull final File scriptFolder) {
        this.plugin = plugin;
        // Create script folder in plugin's directory.
        this.scriptFolder = scriptFolder;
        if (!getScriptFolder().exists()) {
            getScriptFolder().mkdirs();
        }
        // Initialize the context with a global object.
        final Context cx = Context.enter();
        try {
            globalScope = cx.initStandardObjects();
            // Adds the current server instance as a script variable "server".
            globalScope.put("server", globalScope, plugin.getServer());
            globalScope.put(pluginScriptName, globalScope, plugin);
            globalScope.put("metaData", globalScope, metaData);
            globalScope.put("NULL", globalScope, NULL);
        } finally {
            Context.exit();
        }

        // Adds all the default Buscript global methods.
        addScriptMethods(new DefaultFunctions(this));
        // Sets up permissions with vault.
        setupVault();
        plugin.getServer().getPluginManager().registerEvents(new VaultListener(this), plugin);

        // Initializes the delayed script data.
        scriptFile = new File(getScriptFolder(), "scripts.bin");
        initData();
        // Starts up a task to check for scripts that need to run at a specific time.
        ScriptTask scriptTask = new ScriptTask(this);
        scriptTask.start();
        // Registers events with bukkit.
        plugin.getServer().getPluginManager().registerEvents(new BuscriptListener(this), plugin);
    }

    @NotNull
    public File getScriptFolder() {
        return scriptFolder;
    }

    void setupVault() {
        if (getPlugin().getServer().getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        final RegisteredServiceProvider<Permission> permissionProvider = getPlugin().getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permissions = permissionProvider.getProvider();
        }
        final RegisteredServiceProvider<Economy> economyProvider = getPlugin().getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (economyProvider != null) {
            economy = economyProvider.getProvider();
        }
        final RegisteredServiceProvider<Chat> chatProvider = getPlugin().getServer().getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
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
        final Scriptable global = getGlobalScope();
        try {
            global.put("permissions", global, permissions);
            global.put("chat", global, chat);
            global.put("economy", global, economy);
        } finally {
            Context.exit();
        }
    }

    /**
     * Retrieves the plugin that is implementing this library.
     *
     * @return The plugin implementing the Buscript library.
     */
    @NotNull
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * Retrieves the global scope object for this Buscript execution environment.
     *
     * @return The global scope object for this Buscript execution environment.
     */
    @NotNull
    protected Scriptable getGlobalScope() {
        return globalScope;
    }

/*
    protected Scriptable createScriptScope() {
        final Context cx = Context.enter();
        try {
            final Scriptable newScope = cx.newObject(getGlobalScope());
            newScope.setParentScope(getGlobalScope());
            return newScope;
        } finally {
            Context.exit();
        }
    }
    */

    /**
     * Gets the Vault permission API if enabled.
     *
     * @return the Vault permission API or null if not enabled.
     */
    @Nullable
    public Permission getPermissions() {
        return permissions;
    }

    /**
     * Gets the Vault economy API if enabled.
     *
     * @return the Vault economy API or null if not enabled.
     */
    @Nullable
    public Economy getEconomy() {
        return economy;
    }

    /**
     * Gets the Vault chat API if enabled.
     *
     * @return the Vault chat API or null if not enabled.
     */
    @Nullable
    public Chat getChat() {
        return chat;
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
    public void addScriptMethod(@NotNull final String name, @NotNull final Method method, @NotNull final Scriptable obj) {
        FunctionObject scriptMethod = new FunctionObject(name, method, obj);
        getGlobalScope().put(name, getGlobalScope(), scriptMethod);
    }

    /**
     * Adds all methods from the given obj to the global scope that match the names given in names.
     *
     *
     * @param names The names of methods to add.
     * @param obj The object containing these methods.
     */
    public void addScriptMethods(@NotNull final String[] names, @NotNull final Scriptable obj) {
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
     * <p/>
     * Methods intended to be added should all have unique names or you may have conflicts.
     *
     * @param obj The object whose methods should be added.
     */
    public void addScriptMethods(@NotNull final Scriptable obj) {
        for (Method method : obj.getClass().getDeclaredMethods()) {
            if (!method.getName().equals("getClassName")) {
                addScriptMethod(method.getName(), method, obj);
            }
        }
    }

    /**
     * Creates/sets a variable for use in the scope provided.
     *
     * @param scope The scope in which the variable should be declared.
     * @param name The name of the variable which will be used in javascript as a "var".
     * @param object Value for the variable.
     */
    public void setScriptVariable(@NotNull final Scriptable scope, @NotNull final String name, @Nullable final Object object) {
        Context.enter();
        try {
            scope.put(name, scope, object);
        } finally {
            Context.exit();
        }
    }

    /**
     * Creates/sets a variable for use in the global scope.
     *
     * @param name The name of the variable which will be used in javascript as a "var".
     * @param object Value for the variable.
     */
    public void setScriptVariable(@NotNull final String name, @Nullable final Object object) {
        setScriptVariable(getGlobalScope(), name, object);
    }

    /**
     * Obtains the value of a variable in the scope specified.
     *
     * @param scope The scope in which the variable is declared.
     * @param name The name of the javascript "var" to obtain.
     * @return The value of the global variable which will follow the same guidelines as
     * {@link Scriptable#get(String, org.mozilla.javascript.Scriptable)}.
     */
    public Object getScriptVariable(@NotNull final Scriptable scope, @NotNull final String name) {
        Context.enter();
        try {
            return scope.get(name, scope);
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
    public Object getScriptVariable(@NotNull final String name) {
        return getScriptVariable(getGlobalScope(), name);
    }

    /**
     * Obtains the value of a variable from the specified scope that will be automatically casted to the type parameter.
     * <p/>
     * The type parameter may be limited by the confines of what {@link Scriptable#get(String, org.mozilla.javascript.Scriptable)} returns.
     *
     * @param scope The scope in which the variable is declared.
     * @param name The name of the javascript "var" to obtain.
     * @param type A class representing the type to cast the variable's value to.
     * @param <T> The type represented by the type parameter.
     * @return The value of of variable, automatically cast to the given type.  If unable to cast or value is null,
     * null will be returned.
     */
    public <T> T getScriptVariable(@NotNull final Scriptable scope, @NotNull final String name, @NotNull final Class<T> type) {
        try {
            return type.cast(getScriptVariable(scope, name));
        } catch (ClassCastException e) {
            return null;
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Obtains the value of a global scope variable that will be automatically casted to the type parameter.
     * <p/>
     * The type parameter may be limited by the confines of what {@link Scriptable#get(String, org.mozilla.javascript.Scriptable)} returns.
     *
     * @param name The name of the javascript "var" to obtain.
     * @param type A class representing the type to cast the variable's value to.
     * @param <T> The type represented by the type parameter.
     * @return The value of of variable, automatically cast to the given type.  If unable to cast or value is null,
     * null will be returned.
     */
    public <T> T getScriptVariable(@NotNull final String name, @NotNull final Class<T> type) {
        return getScriptVariable(getGlobalScope(), name, type);
    }

    /**
     * Executes a javascript function.  The function must be declared in the scripting environment before this is
     * called.
     *
     * @param scope The scope in which the function is declared.
     * @param obj Scriptable object for use as the 'this' object in javascript.
     * @param functionName The name of the javascript function.
     * @param args Arguments for the script function.
     * @return The result of the function call.
     * @throws InvocationTargetException if the calling of the function resulted in an exception.
     * @throws FunctionNotFoundException if the named variable is not a function or its value is null.
     */
    public Object runScriptFunction(@NotNull final Scriptable scope, @NotNull final Scriptable obj, @NotNull final String functionName, final Object... args)
            throws InvocationTargetException, FunctionNotFoundException {
        final Object o = getScriptVariable(scope, functionName);
        if (o.equals(Scriptable.NOT_FOUND)) {
            throw new FunctionNotFoundException("Variable '"+ functionName + "' not found!");
        } else if (o.equals(Context.getUndefinedValue())) {
            throw new FunctionNotFoundException("Variable '"+ functionName + "' is undefined!");
        }
        if(o instanceof Function){
            final Function f = (Function)o;
            final Context cx = Context.enter();
            try {
                return f.call(cx, scope, obj, args);
            } catch (Exception e) {
                throw new InvocationTargetException(e);
            } finally {
                Context.exit();
            }
        } else {
            throw new FunctionNotFoundException("'" + functionName + "' is not a valid function!");
        }
    }

    CachedScript cacheScript(@NotNull final String scriptName, @NotNull final String scriptContents) {
        Context cx = Context.enter();
        try {
            CachedScript cachedScript = scriptCache.get(scriptName);
            if (cachedScript != null && cachedScript.isSame(scriptContents)) {
                // script is already cached
                return cachedScript;
            }
            final Script script = cx.compileString(scriptContents, scriptName, 1, null);
            cachedScript = new CachedScript(script, scriptContents);
            scriptCache.put(scriptName, cachedScript);
            return cachedScript;
        } finally {
            Context.exit();
        }
    }

    @Nullable
    CachedScript cacheScript(@NotNull final File scriptFile) {
        Context cx = Context.enter();
        try {
            CachedScript cachedScript = scriptCache.get(scriptFile.getName());
            if (cachedScript != null && cachedScript.isSame(scriptFile)) {
                // script is already cached
                return cachedScript;
            }
            Reader reader = null;
            try{
                reader = new FileReader(scriptFile);
                final Script script = cx.compileReader(reader, scriptFile.toString(), 1, null);
                cachedScript = new CachedScript(script, scriptFile);
                scriptCache.put(scriptFile.getName(), cachedScript);
                return cachedScript;
            } catch (IOException e) {
                getPlugin().getLogger().warning("Error compiling script: " + e.getMessage());
                return null;
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

    public void removeCachedScript(@NotNull final String scriptName) {
        this.scriptCache.remove(scriptName);
    }

    public void clearScriptCache() {
        this.scriptCache.clear();
    }

    void executeDelayedScript(File scriptFile, Map<String, Object> data) {
        if (data != null) {
            metaData = data;
        }
        executeScript(scriptFile, null, null);
    }

    /**
     * Executes the given scriptFile with no target.
     *
     * @param scriptFile The file to execute.
     */
    public void executeScript(@NotNull final File scriptFile) {
        executeScript(scriptFile, null, null);
    }

    /**
     * Executes the given scriptFile with no target and messages the given executor if anything goes wrong.
     *
     * @param scriptFile the file to execute.
     * @param executor the player to notify of errors.
     */
    public void executeScript(@NotNull final File scriptFile, @Nullable final Player executor) {
        executeScript(scriptFile, null, executor);
    }

    /**
     * Executes the given scriptFile with the given target.
     *
     * @param scriptFile the file to execute.
     * @param target the target of the script which is used to replace the string '%t' and is added in the global scope
     *               as variable 'target'
     */
    public void executeScript(@NotNull final File scriptFile, @Nullable final String target) {
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
    public void executeScript(@NotNull final File scriptFile, @Nullable final String target, @Nullable final Player executor) {
        runScript(scriptFile, target, executor);
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
        runScript(script, source, target, executor);
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
        script.put("target", target);
        script.put("time", System.currentTimeMillis() + delay);
        script.put("file", scriptFile.toString());
        script.put("metaData", new HashMap<String, Object>(metaData));
        playerScripts.add(script);
        saveData();
    }

    /**
     * This method will remove any scripts scheduled to be executed for the target.  Null is a valid target.
     *
     * @param target The target to remove scheduled scripts for.
     */
    public void clearScheduledScripts(@Nullable final String target) {
        delayedScripts.remove(target);
        saveData();
    }

    void runScript(@NotNull final String scriptName, @NotNull final String scriptContents, @Nullable final String target, @Nullable final Player executor) {
        CachedScript cachedScript = cacheScript(scriptName, scriptContents);
        setup(getGlobalScope(), target);
        Context cx = Context.enter();
        try {
            try{
                cachedScript.getScript().exec(cx, getGlobalScope());
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

    void runScript(@NotNull final File scriptFile, @Nullable final String target, @Nullable final Player executor) {
        CachedScript cachedScript = cacheScript(scriptFile);
        if (cachedScript == null) {
            getPlugin().getLogger().warning("Error running script, file could not be read!");
            if (executor != null) {
                executor.sendMessage("Error running script, file could not be read!");
            }
            return;
        }
        setup(getGlobalScope(), target);
        Context cx = Context.enter();
        try {
            try{
                cachedScript.getScript().exec(cx, getGlobalScope());
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

    private void setup(@NotNull final Scriptable scope, @Nullable final String target) {
        Context.enter();
        try {
            scope.put("metaData", scope, metaData);
            scope.put("target", scope, target);
        } finally {
            Context.exit();
        }
    }

    private void initData() {
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

    void saveData() {
        scriptConfig.set("scripts", delayedScripts);
        try {
            scriptConfig.save(scriptFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save script data: " + e.getMessage());
        }
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
