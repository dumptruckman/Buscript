package buscript;

import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

class DefaultEventExecutor implements EventExecutor {

    Buscript buscript;
    String script;

    DefaultEventExecutor(Buscript buscript, String script) {
        this.buscript = buscript;
        this.script = script;
    }

    @Override
    public void execute(Listener listener, Event event) throws EventException {
        buscript.getGlobalScope().put("event", buscript.getGlobalScope(), event);
        buscript.runScript(script, event.getEventName(), null);
    }
}
