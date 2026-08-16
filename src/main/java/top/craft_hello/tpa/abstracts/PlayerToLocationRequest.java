package top.craft_hello.tpa.abstracts;


import cn.handyplus.lib.adapter.HandyRunnable;
import cn.handyplus.lib.adapter.HandySchedulerUtil;
import cn.handyplus.lib.adapter.PlayerSchedulerUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import top.craft_hello.tpa.utils.SendMessageUtil;
import top.craft_hello.tpa.enums.CommandType;
import top.craft_hello.tpa.enums.PermissionType;
import top.craft_hello.tpa.exceptions.*;
import top.craft_hello.tpa.objects.Config;
import top.craft_hello.tpa.objects.LanguageConfig;
import top.craft_hello.tpa.objects.PlayerDataConfig;
import top.craft_hello.tpa.utils.LoadingConfigUtil;


import static java.util.Objects.isNull;
import static top.craft_hello.tpa.utils.LoadingConfigUtil.getConfig;


public abstract class PlayerToLocationRequest extends Request {
    protected Player requestPlayer;
    protected String requestPlayerName;
    protected String targetName;
    protected CommandType commandType;


    public PlayerToLocationRequest(CommandSender requestObject, String[] args, CommandType commandType)  {
        this.commandType = commandType;
        checkError(requestObject, args);
        if (!getConfig().isEnableTeleportDelay(requestPlayer)) {
            teleport();
            return;
        }
        setCheckMoveTimer(requestPlayer.getLocation());
        setCountdownMessageTimer(requestPlayer, targetName);
        setTimer((delay < 0L ? 3000L : delay * 1000L));
        REQUEST_QUEUE.put(requestPlayer, this);
    }

    protected void checkError() {
        if (isNull(requestPlayer) || !requestPlayer.isOnline()) throw new ErrorTargetOfflineException(requestPlayer, "null");
    }

    protected void checkError(CommandSender requestObject, String[] args)  {
        Config config = LoadingConfigUtil.getConfig();
        String command;
        PlayerDataConfig playerDataConfig;
        switch (commandType) {
            case WARP:
                command = "warp";
                if (!(requestObject instanceof Player)) throw new ErrorConsoleRestrictedException(requestObject);
                requestPlayer = (Player) requestObject;
                requestPlayerName = requestPlayer.getName();
                this.delay = LoadingConfigUtil.getConfig().getTeleportDelay(requestPlayer);
                if (!config.isEnableCommand(commandType)) throw new ErrorCommandDisabledException(requestPlayer);
                if (!config.hasPermission(requestPlayer, PermissionType.WARP)) throw new ErrorPermissionDeniedException(requestPlayer);
                if (COMMAND_DELAY_QUEUE.containsKey(requestPlayer)) throw new ErrorCommandCooldownException(requestPlayer, COMMAND_DELAY_QUEUE.get(requestPlayer));
                if (REQUEST_QUEUE.containsKey(requestPlayer)) throw new ErrorRequestPendingException(requestPlayer);
                if (config.isDisableWorld(commandType, requestPlayer.getWorld())) throw new ErrorWorldDisabledException(requestPlayer);
                if (args.length > 1) throw new ErrorSyntaxWarpException(requestPlayer, command);
                targetName = args[args.length - 1];
                if (!LoadingConfigUtil.getWarpConfig().containsWarpLocation(targetName)) throw new ErrorWarpNotFoundException(requestPlayer, targetName);
                location = LoadingConfigUtil.getWarpConfig().getWarpLocation(requestPlayer, targetName);
                break;
            case HOME:
                command = "home";
                if (!(requestObject instanceof Player)) throw new ErrorConsoleRestrictedException(requestObject);
                requestPlayer = (Player) requestObject;
                requestPlayerName = requestPlayer.getName();
                this.delay = LoadingConfigUtil.getConfig().getTeleportDelay(requestPlayer);
                if (!config.isEnableCommand(commandType)) throw new ErrorCommandDisabledException(requestPlayer);
                if (!config.hasPermission(requestPlayer, PermissionType.HOME)) throw new ErrorPermissionDeniedException(requestPlayer);
                if (COMMAND_DELAY_QUEUE.containsKey(requestPlayer)) throw new ErrorCommandCooldownException(requestPlayer, COMMAND_DELAY_QUEUE.get(requestPlayer));
                if (REQUEST_QUEUE.containsKey(requestPlayer)) throw new ErrorRequestPendingException(requestPlayer);
                if (config.isDisableWorld(commandType, requestPlayer.getWorld())) throw new ErrorWorldDisabledException(requestPlayer);
                if (args.length > 1) throw new ErrorSyntaxHomeException(requestPlayer, command);
                playerDataConfig = PlayerDataConfig.getPlayerData(requestPlayer);
                if (args.length == 0){
                    location = playerDataConfig.getHomeLocation();
                    targetName = playerDataConfig.getDefaultHomeName();
                    break;
                }
                targetName = args[args.length - 1];
                location = PlayerDataConfig.getPlayerData(requestPlayer).getHomeLocation(targetName);
                break;
            case SPAWN:
                if (!(requestObject instanceof Player)) throw new ErrorConsoleRestrictedException(requestObject);
                requestPlayer = (Player) requestObject;
                requestPlayerName = requestPlayer.getName();
                this.delay = LoadingConfigUtil.getConfig().getTeleportDelay(requestPlayer);
                if (!config.isEnableCommand(commandType)) throw new ErrorCommandDisabledException(requestPlayer);
                if (!config.hasPermission(requestPlayer, PermissionType.SPAWN)) throw new ErrorPermissionDeniedException(requestPlayer);
                if (COMMAND_DELAY_QUEUE.containsKey(requestPlayer)) throw new ErrorCommandCooldownException(requestPlayer, COMMAND_DELAY_QUEUE.get(requestPlayer));
                if (REQUEST_QUEUE.containsKey(requestPlayer)) throw new ErrorRequestPendingException(requestPlayer);
                if (config.isDisableWorld(commandType, requestPlayer.getWorld())) throw new ErrorWorldDisabledException(requestPlayer);
                if (!LoadingConfigUtil.getSpawnConfig().containsSpawnLocation()) throw new ErrorSpawnNotSetException(requestPlayer);
                location = LoadingConfigUtil.getSpawnConfig().getSpawnLocation(requestPlayer);
                targetName = "spawn_name";
                break;
            case BACK:
                if (!(requestObject instanceof Player)) throw new ErrorConsoleRestrictedException(requestObject);
                requestPlayer = (Player) requestObject;
                requestPlayerName = requestPlayer.getName();
                this.delay = LoadingConfigUtil.getConfig().getTeleportDelay(requestPlayer);
                if (!config.isEnableCommand(commandType)) throw new ErrorCommandDisabledException(requestPlayer);
                if (!config.hasPermission(requestPlayer, PermissionType.BACK)) throw new ErrorPermissionDeniedException(requestPlayer);
                if (COMMAND_DELAY_QUEUE.containsKey(requestPlayer)) throw new ErrorCommandCooldownException(requestPlayer, COMMAND_DELAY_QUEUE.get(requestPlayer));
                if (REQUEST_QUEUE.containsKey(requestPlayer)) throw new ErrorRequestPendingException(requestPlayer);
                if (config.isDisableWorld(commandType, requestPlayer.getWorld())) throw new ErrorWorldDisabledException(requestPlayer);
                location = PlayerDataConfig.getPlayerData(requestPlayer).getLastLocation();
                targetName = "last_location";
                break;
            case RTP:
                random.setSeed(System.currentTimeMillis());
                if (!(requestObject instanceof Player)) throw new ErrorConsoleRestrictedException(requestObject);
                requestPlayer = ((Player) requestObject);
                requestPlayerName = requestPlayer.getName();
                this.delay = LoadingConfigUtil.getConfig().getTeleportDelay(requestPlayer);
                if (!config.isEnableCommand(commandType)) throw new ErrorCommandDisabledException(requestPlayer);
                if (!config.hasPermission(requestPlayer, PermissionType.RTP)) throw new ErrorPermissionDeniedException(requestPlayer);
                if (COMMAND_DELAY_QUEUE.containsKey(requestPlayer)) throw new ErrorCommandCooldownException(requestPlayer, COMMAND_DELAY_QUEUE.get(requestPlayer));
                if (REQUEST_QUEUE.containsKey(requestPlayer)) throw new ErrorRequestPendingException(requestPlayer);
                targetName = "rtp_name";
                World world = requestPlayer.getWorld();
                if (config.isDisableWorld(commandType, world)) throw new ErrorWorldDisabledException(requestPlayer);
                SendMessageUtil.generateRandomLocationMessage(requestPlayer);
                if (config.isEnableTitleMessage()) {
                    SendMessageUtil.titleGenerateRandomLocationMessage(requestPlayer);
                    if (config.isEnableSound()) PlayerSchedulerUtil.playSound(requestPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                }
                Location playerLocation = requestPlayer.getLocation();
                if (HandySchedulerUtil.isFolia()) {
                    // Folia/Canvas: getHighestBlockYAt/getBlockAt read the chunk synchronously
                    // and must execute on the region thread owning the target chunk. Load the
                    // chunk asynchronously and read the height inside the (region-thread)
                    // callback. The RTP rtpTimer in teleport() polls `location` until it is
                    // non-null, so resolving it asynchronously is safe.
                    generateRtpLocationAsync(world, playerLocation, config);
                } else {
                    // Paper/Bukkit: 同步生成随机传送点
                    for (int i = 0; i < RTP_MAX_ATTEMPTS; i++) {
                        location = tryRandomRtpLocation(world, playerLocation, config);
                        if (!isNull(location)) break;
                    }
                    if (isNull(location)) {
                        // 无安全位置时直接提示失败，终止本次随机传送
                        throw new RtpFailedException(requestPlayer);
                    }
                }
                break;
            default:
                throw new ErrorRuntimeException(requestObject, "在 objects.PlayerToLocationRequest : 35行，请联系开发者（https://github.com/WarSkyGod/TPA/issues）");
        }
    }

    /**
     * Folia/Canvas 兼容：异步加载目标区块并在区块所在 region 线程读取高度。
     * 不能同步调用 getHighestBlockYAt/getBlockAt（会抛 "Cannot retrieve chunk asynchronously"）。
     * 通过 getChunkAtAsync 异步获取区块，在回调（区块所在 region 线程）里读取最高方块
     * 高度并判断是否为实心方块。若不是则重新随机坐标并重试，最多尝试 {@value #RTP_MAX_ATTEMPTS} 次。
     * 成功后写入 {@link #location}，由 RTP 的 rtpTimer 轮询触发传送（见 teleport()）。
     */
    private static final int RTP_MAX_ATTEMPTS = 64;
    private volatile boolean rtpGenerationActive;

    private void generateRtpLocationAsync(World world, Location origin, Config config) {
        rtpGenerationActive = true;
        generateRtpLocationAsync(world, origin, config, 0);
    }

    private void generateRtpLocationAsync(final World world, final Location origin, final Config config, final int attempt) {
        if (!rtpGenerationActive || !requestPlayer.isOnline()) {
            rtpGenerationActive = false;
            return;
        }
        if (attempt >= RTP_MAX_ATTEMPTS) {
            // 超过最大尝试次数仍无合适位置：保持 location 为 null，
            // rtpTimer 会在超时后抛出 RtpFailedException 通知玩家。
            rtpGenerationActive = false;
            return;
        }
        int limitX = config.getRtpLimitX();
        int limitZ = config.getRtpLimitZ();
        final double x;
        final double z;
        switch (world.getEnvironment()) {
            case NETHER:
                x = random.nextDouble(origin.getX() - limitX, origin.getX() + limitX);
                z = random.nextDouble(origin.getZ() - limitZ, origin.getZ() + limitZ);
                break;
            case THE_END:
                x = random.nextDouble(-100, 100);
                z = random.nextDouble(-100, 100);
                break;
            default:
                x = random.nextDouble(origin.getX() - limitX, origin.getX() + limitX);
                z = random.nextDouble(origin.getZ() - limitZ, origin.getZ() + limitZ);
                break;
        }
        // 先规避世界边界，确保读取与加载的是同一个区块
        final double[] clamped = clampToBorder(world, x, z);
        final int blockX = (int) Math.floor(clamped[0]);
        final int blockZ = (int) Math.floor(clamped[1]);
        // getChunkAtAsync 接收区块坐标（block >> 4），回调在拥有该区块的 region 线程执行
        Consumer<Chunk> onChunkLoaded = new Consumer<Chunk>() {
            @Override
            public void accept(Chunk chunk) {
                if (!rtpGenerationActive || !requestPlayer.isOnline()) {
                    rtpGenerationActive = false;
                    return;
                }
                try {
                    Location safe = findSafeRtpLocationAt(world, clamped[0], clamped[1], blockX, blockZ);
                    if (!isNull(safe)) {
                        location = safe;
                        rtpGenerationActive = false;
                    } else {
                        // 重新随机一个坐标并异步重试
                        generateRtpLocationAsync(world, origin, config, attempt + 1);
                    }
                } catch (Throwable ignored) {
                    generateRtpLocationAsync(world, origin, config, attempt + 1);
                }
            }
        };
        world.getChunkAtAsync(blockX >> 4, blockZ >> 4, onChunkLoaded);
    }

    // 生成一个随机坐标，并返回安全落地位置；无合适位置返回 null
    private static Location tryRandomRtpLocation(World world, Location origin, Config config) {
        int limitX = config.getRtpLimitX();
        int limitZ = config.getRtpLimitZ();
        double x;
        double z;
        switch (world.getEnvironment()) {
            case NETHER:
                x = random.nextDouble(origin.getX() - limitX, origin.getX() + limitX);
                z = random.nextDouble(origin.getZ() - limitZ, origin.getZ() + limitZ);
                break;
            case THE_END:
                x = random.nextDouble(-100, 100);
                z = random.nextDouble(-100, 100);
                break;
            default:
                x = random.nextDouble(origin.getX() - limitX, origin.getX() + limitX);
                z = random.nextDouble(origin.getZ() - limitZ, origin.getZ() + limitZ);
                break;
        }
        return findSafeRtpLocation(world, x, z);
    }

    // 规避世界边界：将坐标钳制在世界边界内侧
    private static double[] clampToBorder(World world, double x, double z) {
        WorldBorder border = world.getWorldBorder();
        double radius = border.getSize() / 2.0;
        Location center = border.getCenter();
        double minX = center.getX() - radius;
        double maxX = center.getX() + radius;
        double minZ = center.getZ() - radius;
        double maxZ = center.getZ() + radius;
        if (x < minX + 1) x = minX + 1;
        else if (x > maxX - 1) x = maxX - 1;
        if (z < minZ + 1) z = minZ + 1;
        else if (z > maxZ - 1) z = maxZ - 1;
        return new double[]{x, z};
    }

    // 根据坐标查找安全落地位置：实心地面、避免液体与卡墙窒息
    private static Location findSafeRtpLocation(World world, double x, double z) {
        double[] clamped = clampToBorder(world, x, z);
        return findSafeRtpLocationAt(world, clamped[0], clamped[1],
                (int) Math.floor(clamped[0]), (int) Math.floor(clamped[1]));
    }

    private static Location findSafeRtpLocationAt(World world, double x, double z, int blockX, int blockZ) {
        int y;
        if (world.getEnvironment() == World.Environment.NETHER) {
            y = findNetherFloorY(world, blockX, blockZ);
        } else {
            y = findOverworldFloorY(world, blockX, blockZ);
        }
        if (y < 0) return null;
        return new Location(world, blockX + 0.5, y + 1.0, blockZ + 0.5);
    }

    // 主世界/末地：从最高方块向下寻找实心地面，避免液体与卡墙窒息
    private static int findOverworldFloorY(World world, int blockX, int blockZ) {
        int maxHeight = world.getMaxHeight();
        int minY = world.getMinHeight();
        int y = world.getHighestBlockYAt(blockX, blockZ, HeightMap.WORLD_SURFACE);
        if (y < minY || y > maxHeight - 1) return -1;
        // 最高处是水/岩浆则直接放弃（避免下沉/烧死）
        Material top = world.getBlockAt(blockX, y, blockZ).getType();
        if (top == Material.WATER || top == Material.LAVA) return -1;
        // 向下寻找实心地面（越过草丛、树叶等非实心方块）
        while (y > minY) {
            if (world.getBlockAt(blockX, y, blockZ).getType().isSolid()) break;
            y--;
        }
        if (y <= minY || y + 2 > maxHeight - 1) return -1;
        Material ground = world.getBlockAt(blockX, y, blockZ).getType();
        // 地面不能是水/岩浆
        if (ground == Material.WATER || ground == Material.LAVA) return -1;
        // 脚与头的位置不能有实心方块，防止卡墙窒息
        Material feet = world.getBlockAt(blockX, y + 1, blockZ).getType();
        Material head = world.getBlockAt(blockX, y + 2, blockZ).getType();
        if (feet.isSolid() || head.isSolid()) return -1;
        // 脚的位置不能是水/岩浆
        if (feet == Material.WATER || feet == Material.LAVA) return -1;
        return y;
    }

    // 地狱：最高点是基岩天花板，必须找到天花板下方的地面，避免异常传送至基岩顶
    private static int findNetherFloorY(World world, int blockX, int blockZ) {
        int maxHeight = world.getMaxHeight();
        int minY = world.getMinHeight();
        int top = world.getHighestBlockYAt(blockX, blockZ, HeightMap.WORLD_SURFACE);
        if (top < minY || top > maxHeight - 1) return -1;
        // 从最高点向下扫描，找到基岩天花板的底部（第一个非实心方块的位置）
        int ceilingBottom = top;
        while (ceilingBottom > minY && world.getBlockAt(blockX, ceilingBottom, blockZ).getType().isSolid()) {
            ceilingBottom--;
        }
        // 从天花板下方开始向下寻找"实心地面 + 上方3格非实心（脚/头/头部空间）"的位置
        for (int yy = ceilingBottom; yy >= minY + 1; yy--) {
            if (yy + 3 > maxHeight - 1) continue;
            Material ground = world.getBlockAt(blockX, yy, blockZ).getType();
            if (!ground.isSolid() || ground == Material.LAVA || ground == Material.WATER) continue;
            Material b1 = world.getBlockAt(blockX, yy + 1, blockZ).getType();
            Material b2 = world.getBlockAt(blockX, yy + 2, blockZ).getType();
            Material b3 = world.getBlockAt(blockX, yy + 3, blockZ).getType();
            if (b1.isSolid() || b2.isSolid() || b3.isSolid()) continue;
            if (b1 == Material.LAVA || b1 == Material.WATER) continue;
            return yy;
        }
        return -1;
    }

    protected void setTimer(long delay){
        HandyRunnable timer = new HandyRunnable() {
            @Override
            public void run() {
                try {
                    // 执行逻辑
                    teleport();
                } catch (Exception ignored){
                    REQUEST_QUEUE.remove(requestPlayer);
                    rtpGenerationActive = false;
                    this.cancel();
                }
            }
        };
        this.timer = timer;
        HandySchedulerUtil.runTaskLaterAsynchronously(timer, delay / 50L);
    }

    protected void isMove(@NotNull Location lastLocation){
        if (requestPlayer.getLocation().getX() != lastLocation.getX() || requestPlayer.getLocation().getY() != lastLocation.getY() || requestPlayer.getLocation().getZ() != lastLocation.getZ()){
            REQUEST_QUEUE.remove(requestPlayer);
            rtpGenerationActive = false;
            timer.cancel();
            checkMoveTimer.cancel();
            countdownMessageTimer.cancel();
            if (LoadingConfigUtil.getConfig().isEnableTitleMessage()){
                LanguageConfig language = LanguageConfig.getLanguage(requestPlayer);
                String title = language.getFormatMessage("teleport.canceled.self");
                if (LoadingConfigUtil.getConfig().isEnableSound()) PlayerSchedulerUtil.playSound(requestPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                requestPlayer.sendTitle(title, "");
            }
            SendMessageUtil.move(requestPlayer, requestPlayer);
        }
    }

    protected void setCheckMoveTimer(@NotNull Location lastLocation){
        HandyRunnable checkMoveTimer = new HandyRunnable() {
            long sec = delay * 20;
            @Override
            public void run() {
                try {
                    // 执行逻辑
                    isMove(lastLocation);
                    if (--sec < 0){
                        this.cancel();
                    }
                } catch (Exception ignored) {
                    this.cancel();
                }
            }
        };
        this.checkMoveTimer = checkMoveTimer;
        HandySchedulerUtil.runTaskTimerAsynchronously(checkMoveTimer, 0, 1);
    }

    protected void teleport()  {
        if (getConfig().isEnableTeleportDelay(requestPlayer)) {
            REQUEST_QUEUE.remove(requestPlayer);
            checkMoveTimer.cancel();
            if (!isNull(countdownMessageTimer)) countdownMessageTimer.cancel();
        }
        checkError();
        switch (commandType){
            case WARP:
                if (getConfig().isEnableTitleMessage()) {
                    SendMessageUtil.titleCountdownOverMessage(requestPlayer, targetName);
                    if (getConfig().isEnableSound()) PlayerSchedulerUtil.playSound(requestPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                }
                SendMessageUtil.tpToWarpMessage(requestPlayer, targetName);
                if (!getConfig().isNonTpaOrTphereDisableCheck() && getConfig().isEnableCommandDelay(requestPlayer))
                    setCommandTimer(requestPlayer, getConfig().getCommandDelay(requestPlayer));
                break;
            case HOME:
                if (getConfig().isEnableTitleMessage()) {
                    SendMessageUtil.titleCountdownOverMessage(requestPlayer, targetName);
                    if (getConfig().isEnableSound()) PlayerSchedulerUtil.playSound(requestPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                }
                SendMessageUtil.tpToHomeMessage(requestPlayer, targetName);
                if (!getConfig().isNonTpaOrTphereDisableCheck() && getConfig().isEnableCommandDelay(requestPlayer))
                    setCommandTimer(requestPlayer, getConfig().getCommandDelay(requestPlayer));
                break;
            case SPAWN:
                if (getConfig().isEnableTitleMessage()) {
                    Bukkit.getConsoleSender().sendMessage(targetName);
                    SendMessageUtil.titleCountdownOverMessage(requestPlayer, targetName);
                    if (getConfig().isEnableSound()) PlayerSchedulerUtil.playSound(requestPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                }
                SendMessageUtil.backSpawnSuccessMessage(requestPlayer);
                if (!getConfig().isNonTpaOrTphereDisableCheck() && getConfig().isEnableCommandDelay(requestPlayer))
                    setCommandTimer(requestPlayer, getConfig().getCommandDelay(requestPlayer));
                break;
            case BACK:
                if (getConfig().isEnableTitleMessage()) {
                    SendMessageUtil.titleCountdownOverMessage(requestPlayer, targetName);
                    if (getConfig().isEnableSound()) PlayerSchedulerUtil.playSound(requestPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                }
                SendMessageUtil.backLastLocationSuccessMessage(requestPlayer);
                if (!getConfig().isNonTpaOrTphereDisableCheck() && getConfig().isEnableCommandDelay(requestPlayer))
                    setCommandTimer(requestPlayer, getConfig().getCommandDelay(requestPlayer));
                break;
            case RTP:
                HandyRunnable rtpTimer = new HandyRunnable() {
                    long sec = 200;
                    @Override
                    public void run() {
                        try {
                            if (!isNull(location)){
                                rtpGenerationActive = false;
                                teleport(requestPlayer, location);
                                if (getConfig().isEnableTitleMessage()) {
                                    SendMessageUtil.titleCountdownOverMessage(requestPlayer, targetName);
                                    if (getConfig().isEnableSound()) PlayerSchedulerUtil.playSound(requestPlayer, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                                }
                                SendMessageUtil.rtpSuccessMessage(requestPlayer);
                                this.cancel();
                            }
                            if (--sec < 0) {
                                rtpGenerationActive = false;
                                throw new RtpFailedException(requestPlayer);
                            }
                        } catch (Exception ignored) {
                            rtpGenerationActive = false;
                            this.cancel();
                        }
                    }
                };
                HandySchedulerUtil.runTaskTimerAsynchronously(rtpTimer, 0, 1);
                if (!getConfig().isNonTpaOrTphereDisableCheck() && getConfig().isEnableCommandDelay(requestPlayer))
                    setCommandTimer(requestPlayer, getConfig().getCommandDelay(requestPlayer));
                return;
        }
        teleport(requestPlayer, location);
    }

    @Override
    public void tpaccept()  {
        throw new ErrorNoPendingRequestException(requestPlayer);
    }

    @Override
    protected boolean belongsTo(Player player) {
        return requestPlayer.equals(player);
    }

    @Override
    public void tpdeny()  {
        throw new ErrorNoPendingRequestException(requestPlayer);
    }

}
