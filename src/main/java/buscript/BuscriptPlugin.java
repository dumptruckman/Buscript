package buscript;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class BuscriptPlugin extends JavaPlugin {

    Buscript buscript;

    @Override
    public void onEnable() {
        buscript = new Buscript(this);
    }

    public Buscript getAPI() {
        return buscript;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            return false;
        }
        File scriptFile = new File(getAPI().getScriptFolder(), args[0]);
        if (!scriptFile.exists()) {
            sender.sendMessage("Script '" + scriptFile + "' does not exist!");
            return true;
        }
        if (args.length == 1) {
            getAPI().executeScript(scriptFile);
            return true;
        } else if (args.length == 2) {
            getAPI().executeScript(scriptFile, args[1]);
            return true;
        }
        return false;
    }
}
