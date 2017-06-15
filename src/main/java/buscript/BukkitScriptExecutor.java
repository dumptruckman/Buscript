package buscript;

import org.bukkit.entity.Player;

class BukkitScriptExecutor implements ScriptExecutor {

    private final Player player;

    public BukkitScriptExecutor(Player player) {
        this.player = player;
    }

    @Override
    public void sendMessage(String message) {
        player.sendMessage(message);
    }
}
