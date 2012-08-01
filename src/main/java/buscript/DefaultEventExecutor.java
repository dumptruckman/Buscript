package buscript;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

class DefaultEventExecutor implements EventExecutor {

    Buscript buscript;
    String scriptFile;

    DefaultEventExecutor(Buscript buscript, String scriptFile) {
        this.buscript = buscript;
        this.scriptFile = scriptFile;
    }

    @Override
    public void execute(Listener listener, Event event) throws EventException {
        buscript.getGlobalScope().put("event", buscript.getGlobalScope(), event);
        buscript.executeScript(buscript.getCachedScript(scriptFile), event.getEventName(), null, null);
    }
}
