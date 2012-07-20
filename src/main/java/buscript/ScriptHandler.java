package buscript;

import buscript.util.TimeTools;
import buscript.util.fscript.FSException;
import buscript.util.fscript.FSFastExtension;
import buscript.util.fscript.FSFunctionExtension;
import buscript.util.fscript.FSUnsupportedException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;

public class ScriptHandler extends FSFastExtension {

    public static String replaceName(String string) throws FSException {
        if (string == null) {
            throw new IllegalArgumentException("string must not be null");
        }
        String target = Buscript.target;
        if (target == null) {
            target = Buscript.NULL;
        }
        return string.replaceAll("%p", target);
    }

    private Buscript buscript;

    ScriptHandler(Buscript buscript) {
        this.buscript = buscript;
        setupHandler();
    }

    private void setupHandler() {
        HasPermFunc hasPermFunc = new HasPermFunc(this);
        addFunctionExtension("hasPerm", hasPermFunc);
        addFunctionExtension("hasperm", hasPermFunc);

        CommandFunc commandFunc = new CommandFunc(this);
        addFunctionExtension("cmd", commandFunc);
        addFunctionExtension("command", commandFunc);

        OnlineFunc onlineFunc = new OnlineFunc(this);
        addFunctionExtension("online", onlineFunc);
        addFunctionExtension("isOnline", onlineFunc);
        addFunctionExtension("isonline", onlineFunc);

        BroadcastFunc broadcastFunc = new BroadcastFunc(this);
        addFunctionExtension("broadcast", broadcastFunc);

        MessageFunc messageFunc = new MessageFunc(this);
        addFunctionExtension("message", messageFunc);
        addFunctionExtension("msg", messageFunc);
        addFunctionExtension("tell", messageFunc);

        ExecuteFunc executeFunc = new ExecuteFunc(this);
        addFunctionExtension("execute", executeFunc);
        addFunctionExtension("exec", executeFunc);
        addFunctionExtension("excs", executeFunc);
        addFunctionExtension("run", executeFunc);

        DelayedExecuteFunc delayedExecuteFunc = new DelayedExecuteFunc(this);
        addFunctionExtension("execdelay", delayedExecuteFunc);
        addFunctionExtension("execDelay", delayedExecuteFunc);
        addFunctionExtension("runlater", delayedExecuteFunc);
        addFunctionExtension("runLater", delayedExecuteFunc);

        OnlinePlayersFunc onlinePlayersFunc = new OnlinePlayersFunc(this);
        addFunctionExtension("onlinePlayers", onlinePlayersFunc);
        addFunctionExtension("onlineplayers", onlinePlayersFunc);
        addFunctionExtension("playersOnline", onlinePlayersFunc);
        addFunctionExtension("playersonline", onlinePlayersFunc);
    }

    static class HasPermFunc implements FSFunctionExtension {
        ScriptHandler handler;
        HasPermFunc(ScriptHandler handler) {
            this.handler = handler;
        }
        @Override
        public Object callFunction(String name, ArrayList params) throws FSException {
            if (params.size() == 2) {
                Player player = Bukkit.getPlayerExact(replaceName(params.get(0).toString()));
                return player != null && player.hasPermission(params.get(1).toString());
            } else if (params.size() == 3) {
                if (handler.buscript.permissions != null) {
                    return handler.buscript.permissions.has(params.get(0).toString(), replaceName(params.get(1).toString()), params.get(2).toString());
                } else {
                    throw new FSException("Vault must be installed to use hasperm(world, player, perm)!");
                }
            } else {
                throw new FSUnsupportedException();
            }
        }
    }

    static class CommandFunc implements FSFunctionExtension {
        ScriptHandler handler;
        CommandFunc(ScriptHandler handler) {
            this.handler = handler;
        }
        @Override
        public Object callFunction(String name, ArrayList params) throws FSException {
            if (params.size() == 1) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replaceName(params.get(0).toString()));
            } else if (params.size() == 2) {
                Player player = Bukkit.getPlayerExact(replaceName(params.get(0).toString()));
                if (player != null) {
                    Bukkit.dispatchCommand(player,replaceName(params.get(0).toString()));
                }
            } else {
                throw new FSUnsupportedException();
            }
            return null;
        }
    }

    static class OnlineFunc implements FSFunctionExtension {
        ScriptHandler handler;
        OnlineFunc(ScriptHandler handler) {
            this.handler = handler;
        }
        @Override
        public Object callFunction(String name, ArrayList params) throws FSException {
            if (name.equalsIgnoreCase("online") && params.size() == 1) {
                return Bukkit.getPlayerExact(replaceName(params.get(0).toString())) != null;
            } else {
                throw new FSUnsupportedException();
            }
        }
    }

    static class BroadcastFunc implements FSFunctionExtension {
        ScriptHandler handler;
        BroadcastFunc(ScriptHandler handler) {
            this.handler = handler;
        }
        @Override
        public Object callFunction(String name, ArrayList params) throws FSException {
            if (params.size() == 1) {
                Bukkit.broadcastMessage(replaceName(params.get(0).toString()));
            } else if (params.size() == 2) {
                Bukkit.broadcast(replaceName(params.get(0).toString()), params.get(1).toString());
            } else {
                throw new FSUnsupportedException();
            }
            return null;
        }
    }

    static class MessageFunc implements FSFunctionExtension {
        ScriptHandler handler;
        MessageFunc(ScriptHandler handler) {
            this.handler = handler;
        }
        @Override
        public Object callFunction(String name, ArrayList params) throws FSException {
            if (params.size() == 2) {
                Player player = Bukkit.getPlayerExact(replaceName(params.get(0).toString()));
                if (player != null) {
                    player.sendMessage(params.get(1).toString());
                }
            } else {
                throw new FSUnsupportedException();
            }
            return null;
        }
    }

    static class ExecuteFunc implements FSFunctionExtension {
        ScriptHandler handler;
        ExecuteFunc(ScriptHandler handler) {
            this.handler = handler;
        }
        @Override
        public Object callFunction(String name, ArrayList params) throws FSException {
            if (params.size() == 1) {
                handler.buscript.executeScript(
                        new File(handler.buscript.getScriptFolder(), replaceName(params.get(0).toString())));
            } else if (params.size() == 2) {
                handler.buscript.executeScript(
                        new File(handler.buscript.getScriptFolder(), replaceName(params.get(0).toString())),
                        replaceName(params.get(1).toString()));
            } else {
                throw new FSUnsupportedException();
            }
            return null;
        }
    }

    static class DelayedExecuteFunc implements FSFunctionExtension {
        ScriptHandler handler;
        DelayedExecuteFunc(ScriptHandler handler) {
            this.handler = handler;
        }
        @Override
        public Object callFunction(String name, ArrayList params) throws FSException {
            if (params.size() == 2) {
                try {
                    long delay = TimeTools.fromShortForm(params.get(1).toString());
                    handler.buscript.scheduleScript(
                            new File(handler.buscript.getScriptFolder(), replaceName(params.get(0).toString())),
                            delay * 1000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (params.size() == 3) {
                try {
                    long delay = TimeTools.fromShortForm(params.get(1).toString());
                    handler.buscript.scheduleScript(
                            new File(handler.buscript.getScriptFolder(), replaceName(params.get(0).toString())),
                            delay * 1000, replaceName(params.get(2).toString()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                throw new FSUnsupportedException();
            }
            return null;
        }
    }

    static class OnlinePlayersFunc implements FSFunctionExtension {
        ScriptHandler handler;
        OnlinePlayersFunc(ScriptHandler handler) {
            this.handler = handler;
        }
        @Override
        public Object callFunction(String name, ArrayList params) throws FSException {
            if (params.size() == 0) {
                Player[] players = Bukkit.getOnlinePlayers();
                String[] names = new String[players.length];
                for (int i = 0; i < players.length; i++) {
                    names[i] = players[i].getName();
                }
                return names;
            } else {
                throw new FSUnsupportedException();
            }
        }
    }
}
