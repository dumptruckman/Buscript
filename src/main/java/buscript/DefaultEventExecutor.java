package buscript;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.jetbrains.annotations.NotNull;

class DefaultEventExecutor implements EventExecutor {

    @NotNull
    final Buscript buscript;
    @NotNull
    final String scriptFile;

    DefaultEventExecutor(@NotNull final Buscript buscript, @NotNull final String scriptFile) {
        this.buscript = buscript;
        this.scriptFile = scriptFile;
    }

    @Override
    public void execute(final Listener listener, final Event event) throws EventException {
        buscript.getGlobalScope().put("event", buscript.getGlobalScope(), event);
        buscript.executeScript(scriptFile, event.getEventName(), null, null);
    }
}
