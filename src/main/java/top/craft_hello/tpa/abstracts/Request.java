package top.craft_hello.tpa.abstracts;

import cn.handyplus.lib.adapter.EntitySchedulerUtil;
import cn.handyplus.lib.adapter.HandyRunnable;
import cn.handyplus.lib.adapter.HandySchedulerUtil;
import cn.handyplus.lib.adapter.PlayerSchedulerUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import top.craft_hello.tpa.utils.SendMessageUtil;
import top.craft_hello.tpa.interfaces.RequestInterface;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.isNull;
import static top.craft_hello.tpa.utils.LoadingConfigUtil.getConfig;

public abstract class Request implements RequestInterface {
    protected static Random random = new Random();
    protected HandyRunnable timer;
    protected HandyRunnable countdownMessageTimer;
    protected HandyRunnable checkMoveTimer;
    protected HandyRunnable useCommandTimer;
    protected long delay;
    protected volatile Location location;
    protected final static Map<Player, Request> REQUEST_QUEUE = new ConcurrentHashMap<>();
    protected final static Map<Player, String> COMMAND_DELAY_QUEUE = new ConcurrentHashMap<>();


    protected void setCountdownMessageTimer(@NotNull Player player, @NotNull String target){
        HandyRunnable countdownMessageTimer = new HandyRunnable() {
            long sec = delay;
            @Override
            public void run() {
                try {
                    if (sec > 0){
                        SendMessageUtil.teleportCountdown(player, target, String.valueOf(sec));
                    }
                    if (getConfig().isEnableTitleMessage()){
                        SendMessageUtil.titleCountdownMessage(player, target, String.valueOf(sec));
                        if (getConfig().isEnableSound()) PlayerSchedulerUtil.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                    }
                    if (--sec < 0){
                        SendMessageUtil.titleCountdownOverMessage(player, target);
                        this.cancel();
                    }
                } catch (Exception ignored){
                    this.cancel();
                }
            }
        };
        this.countdownMessageTimer = countdownMessageTimer;
        HandySchedulerUtil.runTaskTimerAsynchronously(countdownMessageTimer, 0, 20);
    }

    protected void setCommandTimer(Player player, long commandDelay){
        if (commandDelay == 0L) return;
        HandyRunnable useCommandTimer = new HandyRunnable() {
            long sec = commandDelay;
            @Override
            public void run() {
                try {
                    COMMAND_DELAY_QUEUE.put(player, String.valueOf(--sec));
                    if (sec < 0){
                        COMMAND_DELAY_QUEUE.remove(player);
                        this.cancel();
                    }
                } catch (Exception ignored){
                    COMMAND_DELAY_QUEUE.remove(player);
                    this.cancel();
                }
            }
        };
        this.useCommandTimer = useCommandTimer;
        HandySchedulerUtil.runTaskTimerAsynchronously(useCommandTimer, 0, 20);
    }

    // 该请求是否与指定玩家有关（用于玩家退出/插件重载时的清理）
    protected boolean belongsTo(Player player) {
        return false;
    }

    // 取消该请求持有的全部计时器，避免玩家退出后仍在后台运行
    protected void cancelTimers() {
        if (!isNull(timer)) timer.cancel();
        if (!isNull(checkMoveTimer)) checkMoveTimer.cancel();
        if (!isNull(countdownMessageTimer)) countdownMessageTimer.cancel();
        if (!isNull(useCommandTimer)) useCommandTimer.cancel();
    }

    // 玩家退出时清理其所有待处理请求、命令冷却与相关计时器
    public static void removePlayerRequest(Player player) {
        COMMAND_DELAY_QUEUE.remove(player);
        for (Map.Entry<Player, Request> entry : new ArrayList<>(REQUEST_QUEUE.entrySet())) {
            Request request = entry.getValue();
            if (entry.getKey().equals(player) || request.belongsTo(player)) {
                REQUEST_QUEUE.remove(entry.getKey());
                request.cancelTimers();
            }
        }
    }

    public static void teleport(Player player, Location location) {
        HandySchedulerUtil.runTaskAsynchronously(() -> EntitySchedulerUtil.syncTeleport(player, location));
    }

    public static Map<Player, Request> getRequestQueue() {
        return REQUEST_QUEUE;
    }

    public static void clearRequestQueue() {
        Set<Player> players = REQUEST_QUEUE.keySet();
        for (Player player : players) REQUEST_QUEUE.remove(player);
    }

    public static void clearCommandDelayQueue() {
        Set<Player> players = COMMAND_DELAY_QUEUE.keySet();
        for (Player player : players) COMMAND_DELAY_QUEUE.remove(player);
    }
}
