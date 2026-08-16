package top.craft_hello.tpa.objects;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import top.craft_hello.tpa.exceptions.ErrorSpawnNotSetException;
import top.craft_hello.tpa.interfaces.ConfigurationInterface;
import top.craft_hello.tpa.utils.SQLiteUtil;

import static java.util.Objects.isNull;

public class SpawnConfig implements ConfigurationInterface {
    private static volatile SpawnConfig instance;
    private Location location;

    private SpawnConfig() {
        loadConfiguration();
    }

    public static SpawnConfig getInstance() {
        if (isNull(instance)) synchronized (SpawnConfig.class) { if (isNull(instance)) instance = new SpawnConfig(); }
        return instance;
    }

    private void loadConfiguration() {
        location = SQLiteUtil.loadSpawn();
    }

    @Override
    public void reloadConfiguration() {
        loadConfiguration();
    }

    public boolean containsSpawnLocation() {
        return !isNull(location);
    }

    public Location getSpawnLocation(CommandSender sender)  {
        if (!containsSpawnLocation()) throw new ErrorSpawnNotSetException(sender);
        return location;
    }

    public void setSpawnLocation(Location location) {
        if (isNull(location)) return;
        this.location = location;
        SQLiteUtil.insertOrUpdateSpawn(location);
    }

    public void delSpawnLocation() {
        if (containsSpawnLocation()) {
            location = null;
            SQLiteUtil.deleteSpawn();
        }
    }
}
