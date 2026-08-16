package top.craft_hello.tpa.tabcompleters;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.craft_hello.tpa.enums.PermissionType;
import top.craft_hello.tpa.objects.Config;
import top.craft_hello.tpa.objects.LanguageConfig;
import top.craft_hello.tpa.utils.LoadingConfigUtil;

import java.util.ArrayList;
import java.util.List;

public class TpaTabCompleter implements TabCompleter {
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        List<String> list = new ArrayList<>();
        LanguageConfig language = LanguageConfig.getLanguage(sender);
        Config config = LoadingConfigUtil.getConfig();
        if (args.length == 0 || (args.length == 1 && "spawn".equalsIgnoreCase(args[args.length - 1]))){
            return list;
        }

        if (args.length == 1){
            if (sender instanceof Player){
                List<String> playerList = new ArrayList<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!sender.equals(player)){
                        list.add(player.getName());
                        playerList.add(player.getName());
                    }
                }

                if (playerList.isEmpty()) list.add(language.getMessage("not_online_players"));
                if (config.hasPermission(sender, PermissionType.RELOAD)) list.add("reload");
                if (config.hasPermission(sender, PermissionType.ADMIN)) list.add("migrate");
                list.add("blacklist");
                list.add("setlang");
            }
            return list;
        }

        if (args.length == 2 && "blacklist".equalsIgnoreCase(args[args.length - 2])){
            list.add("add");
            list.add("remove");
            list.add("clear");
            return list;
        }

        if (args.length == 3 && "blacklist".equalsIgnoreCase(args[args.length - 3])
                && ("add".equalsIgnoreCase(args[1]) || "remove".equalsIgnoreCase(args[1]))){
            if (sender instanceof Player){
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!sender.equals(player)){
                        list.add(player.getName());
                    }
                }
            }
            return list;
        }

        if (args.length == 2 && "setlang".equalsIgnoreCase(args[args.length - 2])){
            list.add("clear");
            list.addAll(LanguageConfig.getLanguageTextList());
            return list;
        }
        return list;
    }
}
