package buscript;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.javascript.Script;

import java.io.File;

class CachedScript {

    @NotNull
    private final Script script;

    @Nullable
    private final String previousScript;

    private final long previousFileModified;

    CachedScript(@NotNull final Script script, @NotNull final String scriptContents) {
        this.script = script;
        this.previousScript = scriptContents;
        this.previousFileModified = 0L;
    }

    CachedScript(@NotNull final Script script, @NotNull final File scriptFile) {
        this.script = script;
        this.previousFileModified = scriptFile.lastModified();
        this.previousScript = null;
    }

    public boolean isSame(@NotNull final String scriptContents) {
        return previousScript != null && previousScript.equals(scriptContents);
    }

    public boolean isSame(@NotNull final File scriptFile) {
        return previousFileModified == scriptFile.lastModified();
    }

    @NotNull
    public Script getScript() {
        return script;
    }
}
