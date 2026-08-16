package top.craft_hello.tpa.objects;

import cn.handyplus.lib.adapter.HandyRunnable;
import cn.handyplus.lib.adapter.HandySchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import top.craft_hello.tpa.abstracts.Configuration;
import top.craft_hello.tpa.enums.PermissionType;
import top.craft_hello.tpa.exceptions.*;
import top.craft_hello.tpa.utils.SendMessageUtil;
import top.craft_hello.tpa.utils.SQLiteUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.isNull;
import static top.craft_hello.tpa.utils.LoadingConfigUtil.getConfig;

public class PlayerDataConfig {
    private static final Map<UUID, PlayerDataConfig> PLAYER_DATAS = new ConcurrentHashMap<>();
    private UUID playerUUID;
    private Player player;
    private String playerName;
    private String defaultHomeName;
    private boolean setlang;
    private final Map<String, Location> HOMES = new ConcurrentHashMap<>();
    private List<String> denyList = new ArrayList<>();
    private Location lastLocation;
    private Location logoutLocation;
    protected String languageStr;
    private HandyRunnable loadPlayerTimer;

    public PlayerDataConfig(UUID playerUUID){
        if (isNull(playerUUID)) return;
        this.playerUUID = playerUUID;
        if (PlayerDataConfig.containsPlayerData(playerUUID)) return;
        loadConfiguration();
        PLAYER_DATAS.put(playerUUID, this);
    }

    public PlayerDataConfig(String playerName){
        this(Bukkit.getOfflinePlayer(playerName).getUniqueId());
    }

    private void loadConfiguration(){
        Map<String, Object> data = SQLiteUtil.loadPlayerDataRow(playerUUID);
        if (isNull(data)) {
            playerName = getOfflinePlayerName();
            languageStr = getConfig().getDefaultLanguageStr();
            setlang = false;
            defaultHomeName = null;
            lastLocation = null;
            logoutLocation = null;
            denyList = new ArrayList<>();
            SQLiteUtil.insertOrUpdatePlayerData(playerUUID, playerName, languageStr, setlang, defaultHomeName, lastLocation, logoutLocation, denyList);
        } else {
            playerName = (String) data.get("player_name");
            if (isNull(playerName)) playerName = getOfflinePlayerName();
            languageStr = (String) data.get("language");
            if (isNull(languageStr)) languageStr = getConfig().getDefaultLanguageStr();
            setlang = (Boolean) data.get("setlang");
            defaultHomeName = (String) data.get("default_home");
            lastLocation = (Location) data.get("last_location");
            logoutLocation = (Location) data.get("logout_location");
            denyList = (List<String>) data.get("deny_list");
            if (isNull(denyList)) denyList = new ArrayList<>();
            HOMES.putAll(SQLiteUtil.loadHomes(playerUUID));
        }
        startLoadPlayerTimer();
    }

    private String getOfflinePlayerName(){
        try {
            String name = Bukkit.getOfflinePlayer(playerUUID).getName();
            return isNull(name) ? playerUUID.toString() : name;
        } catch (Exception ignored) {
            return playerUUID.toString();
        }
    }

    private void startLoadPlayerTimer(){
        if (!isNull(loadPlayerTimer)) loadPlayerTimer.cancel();
        loadPlayerTimer = new HandyRunnable() {
            long sec = 200;
            @Override
            public void run() {
                try {
                    player = Bukkit.getPlayer(playerUUID);
                    if (!isNull(player)){
                        this.cancel();
                    }
                    if (--sec < 0) {
                        this.cancel();
                    }
                } catch (Exception ignored) {
                    this.cancel();
                }
            }
        };
        HandySchedulerUtil.runTaskTimerAsynchronously(loadPlayerTimer, 0, 1);
    }

    private void save(){
        SQLiteUtil.insertOrUpdatePlayerData(playerUUID, playerName, languageStr, setlang, defaultHomeName, lastLocation, logoutLocation, denyList);
    }

    public void reloadConfiguration(){
        HOMES.clear();
        loadConfiguration();
    }

    public static void reloadAllPlayerData(){
        for (String playerUUID : getPlayerUUIDList()) PLAYER_DATAS.get(UUID.fromString(playerUUID)).reloadConfiguration();
    }

    public static void loadAllPlayerData() {
        Configuration.offUpdateConfiguration();
        for (UUID playerUUID : SQLiteUtil.getAllPlayerUUIDs()) {
            new PlayerDataConfig(playerUUID);
        }
    }

    public static PlayerDataConfig getPlayerData(UUID playerUUID) {
        if (!containsPlayerData(playerUUID)) new PlayerDataConfig(playerUUID);
        return PLAYER_DATAS.get(playerUUID);
    }

    public static PlayerDataConfig getPlayerData(Player player) {
        UUID playerUUID = player.getUniqueId();
        PlayerDataConfig playerDataConfig = getPlayerData(playerUUID);
        if (!isNull(playerDataConfig)) playerDataConfig.player = player;
        return playerDataConfig;
    }

    public static PlayerDataConfig getPlayerData(OfflinePlayer offlinePlayer) {
        UUID playerUUID = offlinePlayer.getUniqueId();
        return getPlayerData(playerUUID);
    }

    public static PlayerDataConfig getPlayerData(String playerName) {
        UUID playerUUID = Bukkit.getPlayerUniqueId(playerName);
        if (isNull(playerUUID)) {
            new PlayerDataConfig(playerName);
            return PLAYER_DATAS.get(Bukkit.getOfflinePlayer(playerName).getUniqueId());
        }
        if (!containsPlayerData(playerUUID)) new PlayerDataConfig(playerName);
        return PLAYER_DATAS.get(playerUUID);
    }

    public static List<String> getPlayerUUIDList(){
        List<String> playerUUIDList = new ArrayList<>();
        for (Map.Entry<UUID, PlayerDataConfig> langMap : PLAYER_DATAS.entrySet()) playerUUIDList.add(langMap.getKey().toString());
        return playerUUIDList;
    }

    public static boolean containsPlayerData(UUID playerUUID) {
        return PLAYER_DATAS.containsKey(playerUUID);
    }

    public static void removePlayerData(UUID playerUUID) {
        PlayerDataConfig playerDataConfig = PLAYER_DATAS.remove(playerUUID);
        if (!isNull(playerDataConfig)) playerDataConfig.cancelLoadPlayerTimer();
    }

    private void cancelLoadPlayerTimer() {
        if (!isNull(loadPlayerTimer)) loadPlayerTimer.cancel();
    }

    public static void removePlayerData(Player player) {
        UUID playerUUID = player.getUniqueId();
        removePlayerData(playerUUID);
    }

    public static void removePlayerData(OfflinePlayer offlinePlayer) {
        UUID playerUUID = offlinePlayer.getUniqueId();
        removePlayerData(playerUUID);
    }

    public void updatePlayerName(String playerName) {
        if (isNull(playerName) || playerName.equals(this.playerName)) return;
        this.playerName = playerName;
        save();
    }

    public String getLanguageStr() {
        return languageStr;
    }

    public boolean containsDefaultHome(){
        return !isNull(defaultHomeName);
    }

    public String getDefaultHomeName() {
        return defaultHomeName;
    }

    public boolean equalsDefaultHomeName(String homeName) {
        return !isNull(defaultHomeName) && defaultHomeName.equalsIgnoreCase(homeName);
    }

    public boolean containsHomeLocation(String homeName) {
        return HOMES.containsKey(homeName);
    }

    public PermissionType getPermissionType(Player player) {
        if (player.hasPermission("tpa.admin")) return PermissionType.ADMIN;
        if (player.hasPermission("tpa.mvp++")) return PermissionType.MVP_PLUS_PLUS;
        if (player.hasPermission("tpa.mvp+")) return PermissionType.MVP_PLUS;
        if (player.hasPermission("tpa.mvp")) return PermissionType.MVP;
        if (player.hasPermission("tpa.vip+")) return PermissionType.VIP_PLUS;
        if (player.hasPermission("tpa.vip")) return PermissionType.VIP;
        return PermissionType.DEFAULT;
    }

    public void checkHomeAmountIsMax()  {
        Player onlinePlayer = player;
        if (isNull(onlinePlayer) || !onlinePlayer.isOnline()) onlinePlayer = Bukkit.getPlayer(playerUUID);
        if (isNull(onlinePlayer) || !onlinePlayer.isOnline()) throw new ErrorTargetOfflineException(null, "null");
        PermissionType permissionType = getPermissionType(onlinePlayer);
        int homeAmount = HOMES.size();
        int maxHomeAmount = getConfig().getHomeAmountMax(permissionType);
        if (maxHomeAmount < 1) return;
        if (homeAmount >= maxHomeAmount) throw new HomeMaxLimitErrorException(onlinePlayer, maxHomeAmount);
    }

    public Location getHomeLocation()  {
        if (isNull(defaultHomeName)) throw new ErrorNoDefaultHomeException(player);
        return getHomeLocation(defaultHomeName);
    }

    public Location getHomeLocation(String homeName)  {
        if (!containsHomeLocation(homeName)) throw new ErrorHomeNotFoundException(player, homeName);
        return HOMES.get(homeName);
    }

    public boolean containsLastLocation() {
        return !isNull(lastLocation);
    }

    public Location getLastLocation()  {
        if (!containsLastLocation()) throw new ErrorLastLocationMissingException(player);
        return lastLocation;
    }

    public boolean containsLogoutLocation() {
        return !isNull(logoutLocation);
    }

    public Location getLogoutLocation(Player executor)  {
        if (!containsLogoutLocation()) throw new ErrorLogoutLocationMissingException(executor);
        return logoutLocation;
    }

    public boolean isDeny(String playerUUID) {
        return denyList.contains(playerUUID);
    }

    public void checkIsNoDeny(String playerUUID, Player executor)  {
        if (!isDeny(playerUUID)) throw new ErrorNotBlacklistedException(executor);
    }

    public List<String> getDenyList(CommandSender sender)  {
        if (denyList.isEmpty()) throw new ErrorNotBlockedException(sender);
        return denyList;
    }

    public void addDeny(String playerUUID) {
        if (isDeny(playerUUID)) return;
        denyList.add(playerUUID);
        save();
    }

    public void delDeny(String playerUUID) {
        if (!isDeny(playerUUID)) return;
        denyList.remove(playerUUID);
        save();
    }

    public void clearDenyList() {
        if (denyList.isEmpty()) return;
        denyList.clear();
        save();
    }

    public boolean equalsLanguageStr(String languageStr) {
        if (isNull(languageStr) || isNull(this.languageStr)) return false;
        return this.languageStr.equalsIgnoreCase(languageStr);
    }

    public boolean isSetlang(){
        return setlang;
    }

    public void setSetlang(boolean setlang) {
        this.setlang = setlang;
        save();
    }

    public void setLanguage(String languageStr) {
        if (isNull(languageStr) || equalsLanguageStr(languageStr)) return;
        this.languageStr = Configuration.formatLangStr(languageStr);
        save();
        SendMessageUtil.setLangCommandSuccess(player, this.languageStr);
    }

    public void setDefaultHomeName(String homeName, boolean force) {
        if (!force){
            if (isNull(homeName) || !containsHomeLocation(homeName)) throw new ErrorHomeNotFoundException(player, homeName);
            if (homeName.equalsIgnoreCase(defaultHomeName)) throw new ErrorDefaultHomeAlreadySetException(player, homeName);
        }
        defaultHomeName = homeName;
        save();
        SendMessageUtil.setDefaultHomeSuccess(player, homeName);
    }

    public void setHomeLocation(Location location)  {
        String defaultHomeName = "default";
        if (containsDefaultHome()) defaultHomeName = this.defaultHomeName;
        if (!containsHomeLocation(defaultHomeName)) {
            checkHomeAmountIsMax();
            setDefaultHomeName(defaultHomeName, true);
        }
        HOMES.put(defaultHomeName, location);
        SQLiteUtil.insertOrUpdateHome(playerUUID, defaultHomeName, location);
        save();
        SendMessageUtil.setHomeSuccess(player, defaultHomeName);
    }

    public void setHomeLocation(String homeName, Location location)  {
        if (isNull(homeName) || isNull(location)) return;
        if (!containsHomeLocation(homeName)) checkHomeAmountIsMax();
        if (isNull(defaultHomeName)) {
            defaultHomeName = homeName;
            setDefaultHomeName(defaultHomeName, true);
        }
        HOMES.put(homeName, location);
        SQLiteUtil.insertOrUpdateHome(playerUUID, homeName, location);
        save();
        SendMessageUtil.setHomeSuccess(player, homeName);
    }

    public void delHomeLocation(String homeName)  {
        if (containsHomeLocation(homeName)) {
            HOMES.remove(homeName);
            defaultHomeName = null;
            SQLiteUtil.deleteHome(playerUUID, homeName);
            if (HOMES.isEmpty()) {
                save();
            } else {
                for (Map.Entry<String, Location> homeMap : HOMES.entrySet()) {
                    if (isNull(defaultHomeName)) {
                        defaultHomeName = homeMap.getKey();
                        break;
                    }
                }
                save();
            }
            SendMessageUtil.delHomeSuccess(player, homeName);
            return;
        }
        throw new ErrorHomeNotFoundException(player, homeName);
    }

    public void setLastLocation(Location location) {
        if (isNull(location)) return;
        lastLocation = location;
        save();
    }

    public void setLogoutLocation(Location location) {
        if (isNull(location)) return;
        logoutLocation = location;
        save();
    }

    public List<String> getHomeNameList(CommandSender sender)  {
        if (HOMES.isEmpty()) throw new ErrorNoHomesSetException(sender);
        List<String> homeNameList = new ArrayList<>();
        for (Map.Entry<String, Location> homeMap : HOMES.entrySet()){
            homeNameList.add(homeMap.getKey());
        }
        return homeNameList;
    }

    public List<String> getHomeNameList()  {
        return getHomeNameList(player);
    }
}
