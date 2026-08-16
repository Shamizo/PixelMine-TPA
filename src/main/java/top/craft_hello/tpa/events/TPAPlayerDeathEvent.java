package top.craft_hello.tpa.events;

import cn.handyplus.lib.adapter.HandySchedulerUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import top.craft_hello.tpa.enums.CommandType;
import top.craft_hello.tpa.enums.PermissionType;
import top.craft_hello.tpa.objects.Config;
import top.craft_hello.tpa.objects.PlayerDataConfig;
import top.craft_hello.tpa.utils.LoadingConfigUtil;
import top.craft_hello.tpa.utils.SendMessageUtil;

public class TPAPlayerDeathEvent implements Listener {
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent playerDeathEvent){
        Player player = playerDeathEvent.getEntity();
        Location lastLocation = player.getLocation();
        World deathWorld = player.getWorld();
        HandySchedulerUtil.runTaskAsynchronously(() -> {
            PlayerDataConfig.getPlayerData(player).setLastLocation(lastLocation);
            sendReturnByDeathMessage(player, deathWorld);
        });
    }

    private void sendReturnByDeathMessage(Player player, World deathWorld){
        if (!player.isOnline()) return;
        Config config = LoadingConfigUtil.getConfig();
        if (!config.isEnableCommand(CommandType.BACK)) return;
        if (!config.isBackDeathMessage()) return;
        if (!config.hasPermission(player, PermissionType.BACK)) return;
        if (config.isDisableWorld(CommandType.BACK, deathWorld)) return;
        SendMessageUtil.returnByDeathMessage(player);
    }
}
