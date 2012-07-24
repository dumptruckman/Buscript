package buscript;

/**
 * Represents a regex string, usually something simple like "%t" to be replaced by another string.
 */
public interface StringReplacer {

    /**
     * A regex string representing what should be replaced.
     *
     * @return a regex string representing what should be replaced.
     */
    String getRegexString();

    /**
     * The string to replace with.  This should probably return null after a script has been run.
     *
     * @return the string to replace with.
     */
    String getReplacement();

    /**
     * If this does not return null, Buscript will create a global variable in the scripting environment with this
     * as a name and {@link #getReplacement()} as a value.
     *
     * @return the name for a global script variable or null to not use this feature.
     */
    String getGlobalVarName();
}
