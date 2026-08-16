package top.craft_hello.tpa.commands;

import cn.handyplus.lib.adapter.HandySchedulerUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import top.craft_hello.tpa.utils.ErrorCheckUtil;

import java.util.Arrays;



public class Tpa implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender executor, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {


        // 判断是否是重新加载命令
        if (args.length == 1 && "reload".equalsIgnoreCase(args[args.length - 1])){
            ErrorCheckUtil.executeCommand(executor, args, "reload");
            return true;
        }

        // 判断是否是数据迁移命令
        if (args.length == 1 && "migrate".equalsIgnoreCase(args[args.length - 1])){
            ErrorCheckUtil.executeCommand(executor, args, "migrate");
            return true;
        }

        // 判断是否是设置显示语言命令
        if (args.length == 2 && "setlang".equalsIgnoreCase(args[args.length - 2])){
            ErrorCheckUtil.executeCommand(executor, args, "setlang");
            return true;
        }

        // 判断是否是黑名单管理命令
        if (args.length >= 1 && "blacklist".equalsIgnoreCase(args[0])){
            ErrorCheckUtil.executeCommand(executor, Arrays.copyOfRange(args, 1, args.length), "blacklist");
            return true;
        }

        ErrorCheckUtil.executeCommand(executor, args, command.getName());
        return true;
    }
}
