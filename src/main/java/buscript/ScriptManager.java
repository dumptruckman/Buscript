package buscript;

import buscript.util.FileTools;
import org.bukkit.ChatColor;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ScriptManager {

    private final Logger logger;

    public static final String NULL = "!!NULL";

    private ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("nashorn");
    private Invocable invocable = (Invocable) scriptEngine;
    private ScriptContext defaultContext = scriptEngine.getContext();
    private Bindings engineBindings = defaultContext.getBindings(ScriptContext.ENGINE_SCOPE);

    Object globalObject;
    Object objectConstructor;

    private String target = null;

    private File scriptFolder;

    private Map<String, String> scriptCache = new HashMap<String, String>();

    private List<Map<String, Object>> delayedReplacements = null;

    protected final List<StringReplacer> stringReplacers = new ArrayList<StringReplacer>();

    protected Map<String, Object> metaData = new HashMap<String, Object>();

    private static class TargetReplacer implements StringReplacer {

        private ScriptManager scriptManager;

        private TargetReplacer(ScriptManager scriptManager) {
            this.scriptManager = scriptManager;
        }

        @Override
        public String getRegexString() {
            return "%target%";
        }

        @Override
        public String getReplacement() {
            return scriptManager.getTarget();
        }

        @Override
        public String getGlobalVarName() {
            return "target";
        }
    }

    protected ScriptManager(File scriptFolder, Logger logger) {
        this.logger = logger;
        registerStringReplacer(new TargetReplacer(this));
        // Create script folder in plugin's directory.
        this.scriptFolder = scriptFolder;
        if (!getScriptFolder().exists()) {
            getScriptFolder().mkdirs();
        }
        // Initialize the context with a global object.
        try {
            globalObject = scriptEngine.eval("this");
            objectConstructor = scriptEngine.eval("Object");
        } catch (ScriptException e) {
            throw new RuntimeException(e);
        }

        // Adds the current server instance as a script variable "server".
        engineBindings.put("metaData", metaData);
        engineBindings.put("NULL", NULL);
    }

    protected Logger getLogger() {
        return logger;
    }

    /**
     * Retrieves the global scope object for this Buscript execution environment. Equivalent to the global this object
     * in JS.
     *
     * @return The global scope object for this Buscript execution environment.
     */
    public Object getGlobalScope() {
        return globalObject;
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
     * current target.  This will also replace '&amp;' with the appropriate color character.
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
     * Adds all methods from the given obj to the global scope.
     * Methods intended to be added should all have unique names or you may have conflicts.
     *
     * @param obj The object whose methods should be added.
     */
    public void addScriptMethods(Object obj) {
        try {
            invocable.invokeMethod(objectConstructor, "bindProperties", globalObject, obj);
        } catch (ScriptException | NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates/sets a variable for use in the global scope.
     *
     * @param name The name of the variable which will be used in javascript as a "var".
     * @param object Value for the variable.
     */
    public void setScriptVariable(String name, Object object) {
        engineBindings.put(name, object);
    }

    /**
     * Obtains the value of a global scope variable.
     *
     * @param name The name of the javascript "var" to obtain.
     * @return The value of the global variable of the given name.
     */
    public Object getScriptVariable(String name) {
        return engineBindings.get(name);
    }

    /**
     * Obtains the value of a global scope variable that will be automatically casted to the type parameter.
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
     * @param obj - "Scope" object for use as the 'this' object in javascript.
     * @param functionName The name of the javascript function.
     * @param args - Arguments for the script function.
     * @return The result of the function call.
     * @throws ScriptException if the calling of the function resulted in an exception.
     * @throws FunctionNotFoundException if the named variable is not a function or its value is null.
     */
    public Object runScriptFunction(Object obj, String functionName, Object... args)
            throws FunctionNotFoundException, ScriptException {
        Object o = getScriptVariable(functionName);
        try {
            return invocable.invokeMethod(obj, functionName, args);
        } catch (NoSuchMethodException e) {
            throw new FunctionNotFoundException("'" + functionName + "' is not a valid function!");
        }
    }

    Object executeDelayedScript(File scriptFile, List<Map<String, Object>> replacements, Map<String, Object> data) {
        if (data != null) {
            metaData = data;
        }
        delayedReplacements = replacements;
        Object res = executeScript(scriptFile, null, null);
        delayedReplacements = null;
        return res;
    }

    /**
     * Executes the given scriptFile with no target.
     *
     * @param scriptFile The file to execute.
     */
    public Object executeScript(File scriptFile) {
        return executeScript(scriptFile, null, null);
    }

    /**
     * Executes the given scriptFile with no target and messages the given executor if anything goes wrong.
     *
     * @param scriptFile the file to execute.
     * @param executor the executor to notify of errors.
     */
    public Object executeScript(File scriptFile, ScriptExecutor executor) {
        return executeScript(scriptFile, null, executor);
    }

    /**
     * Executes the given scriptFile with the given target.
     *
     * @param scriptFile the file to execute.
     * @param target the target of the script which is used to replace the string '%t' and is added in the global scope
     *               as variable 'target'
     */
    public Object executeScript(File scriptFile, String target) {
        return executeScript(scriptFile, target, null);
    }

    /**
     * Executes the given scriptFile with the specified target and messages the given executor if anything goes wrong.
     *
     * @param scriptFile the file to execute.
     * @param target the target of the script which is used to replace the string '%t' and is added in the global scope
     *               as variable 'target'
     * @param executor the executor to notify of errors.
     */
    public Object executeScript(File scriptFile, String target, ScriptExecutor executor) {
        this.target = target;
        Object res = runScript(scriptFile, executor);
        this.target = null;
        metaData.clear();
        return res;
    }

    /**
     * Executes the given script string (literal javascript) with no target.
     *
     * @param script The literal javascript to execute.
     * @param source The source of the script.  This can be anything except null.  It is what will show up if errors
     *               occur.
     */
    public Object executeScript(String script, String source) {
        return executeScript(script, source, null, null);
    }

    /**
     * Executes the given script string (literal javascript) with no target and messages the given executor if
     * anything goes wrong.
     *
     * @param script The literal javascript to execute.
     * @param source The source of the script.  This can be anything except null.  It is what will show up if errors
     *               occur.
     * @param executor the executor to notify of errors.
     */
    public Object executeScript(String script, String source, ScriptExecutor executor) {
        return executeScript(script, source, null, executor);
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
    public Object executeScript(String script, String source, String target) {
        return executeScript(script, source, target, null);
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
     * @param executor the executor to notify of errors.
     */
    public Object executeScript(String script, String source, String target, ScriptExecutor executor) {
        this.target = target;
        Object res = runScript(script, executor);
        this.target = null;
        metaData.clear();
        return res;
    }

    Object runScript(String script, ScriptExecutor executor) {
        setup();
        try {
            return scriptEngine.eval(script);
        } catch (ScriptException e) {
            getLogger().warning("Error running script: " + e.getMessage());
            if (executor != null) {
                executor.sendMessage("Error running script: " + e.getMessage());
            }
            return null;
        }
    }

    Object runScript(File script, ScriptExecutor executor) {
        setup();
        try (Reader reader = new FileReader(script)){
            return scriptEngine.eval(reader);
        } catch (ScriptException | IOException e) {
            getLogger().warning("Error running script: " + e.getMessage());
            if (executor != null) {
                executor.sendMessage("Error running script: " + e.getMessage());
            }
            return null;
        }
    }

    private void setup() {
        if (delayedReplacements != null) {
            for (Map<String, Object> replacement : delayedReplacements) {
                Object var = replacement.get("var");
                Object replace = replacement.get("replace");
                if (var != null) {
                    if (replace == null) {
                        replace = NULL;
                    }
                    setScriptVariable(var.toString(), replace);
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
                    setScriptVariable(var, replace);
                }
            }
        }
        setScriptVariable("metaData", metaData);
    }

    void cacheScript(String fileName) {
        File file = new File(fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                getLogger().warning(e.getMessage());
                return;
            }
        }

        scriptCache.put(fileName, FileTools.readFileAsString(file, getLogger()));
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
