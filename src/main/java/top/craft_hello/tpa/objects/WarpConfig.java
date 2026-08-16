package top.craft_hello.tpa.objects;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import top.craft_hello.tpa.exceptions.ErrorWarpNotFoundException;
import top.craft_hello.tpa.interfaces.ConfigurationInterface;
import top.craft_hello.tpa.utils.SQLiteUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.isNull;

public class WarpConfig implements ConfigurationInterface {
    private static volatile WarpConfig instance;
    private static final Map<String, Location> LOCATIONS = new ConcurrentHashMap<>();

    private WarpConfig() {
        loadConfiguration();
    }

    public static WarpConfig getInstance() {
        if (isNull(instance)) synchronized (WarpConfig.class) { if (isNull(instance)) instance = new WarpConfig(); }
        return instance;
    }

    private void loadConfiguration() {
        LOCATIONS.putAll(SQLiteUtil.loadAllWarps());
    }

    @Override
    public void reloadConfiguration() {
        LOCATIONS.clear();
        loadConfiguration();
    }

    public boolean containsWarpLocation(String warpName) {
        return LOCATIONS.containsKey(warpName);
    }

    public List<String> getWarpNameList() {
        List<String> locationNameList = new ArrayList<>();
        for (Map.Entry<String, Location> locationMap : LOCATIONS.entrySet()){
            locationNameList.add(locationMap.getKey());
        }
        return locationNameList;
    }

    public Location getWarpLocation(CommandSender sender, String warpName)  {
        if (!containsWarpLocation(warpName)) throw new ErrorWarpNotFoundException(sender, warpName);
        return LOCATIONS.get(warpName);
    }

    public void setWarpLocation(String warpName, Location location) {
        if (isNull(location)) return;
        LOCATIONS.put(warpName, location);
        SQLiteUtil.insertOrUpdateWarp(warpName, location);
    }

    public void delWarpLocation(String warpName) {
        if (containsWarpLocation(warpName)) {
            LOCATIONS.remove(warpName);
            SQLiteUtil.deleteWarp(warpName);
        }
    }
}
