package com.aermini.regexfilter;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AerRegexFilter extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private Pattern nameRegex;
    private Pattern chatRegex;
    private Pattern nameAllowPattern;
    private final Map<Pattern, String> chatReplacements = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("aerregexfilter") != null) {
            getCommand("aerregexfilter").setExecutor(new FilterCommand(this));
        }
        getLogger().info("AerRegexFilter 加载成功 | by. AerMini");
    }

    public void loadConfig() {
        reloadConfig();
        config = getConfig();
        nameRegex = compile(config.getString("name-regex"));
        chatRegex = compile(config.getString("chat-regex"));
        nameAllowPattern = compile(config.getString("name.allowregex", ".*"));
        chatReplacements.clear();

        ConfigurationSection replaceSection = config.getConfigurationSection("chat.replace");
        if (replaceSection != null) {
            for (String key : replaceSection.getKeys(false)) {
                Pattern p = compile(replaceSection.getString(key));
                if (p != null) chatReplacements.put(p, key);
            }
        }
        if (config.getBoolean("debug", true)) getLogger().severe("arf config loaded");
    }

    private Pattern compile(String regex) {
        if (regex == null || regex.isEmpty()) return null;
        String sanitized = regex.replaceAll("\\r\\n|\\r|\\n", "").trim();
        return Pattern.compile(sanitized);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (config.getBoolean("debug", true)) {
            getLogger().severe(String.format("onChat: message->%s", event.getMessage()));
        }
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        String message = event.getMessage();
        String original = message;

        if (config.getBoolean("regexfilter.chat", true)) {
            for (Map.Entry<Pattern, String> entry : chatReplacements.entrySet()) {
                message = entry.getKey().matcher(message).replaceAll(entry.getValue());
            }
            if (chatRegex != null) {
                message = maskByCharacter(message, chatRegex, config.getString("chat.filter", "*"));
            }
        }

        // 修改消息（Spigot 1.21 会自动在这里剥离玩家签名并正常广播修改后的消息）
        if (!original.equals(message)) event.setMessage(message);

        if (config.getBoolean("debug", true)) getLogger().severe("onChat: newmsg->"+message);
    }

    private String maskByCharacter(String text, Pattern pattern, String filter) {
        Matcher m = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String match = m.group();
            StringBuilder replacement = new StringBuilder();
            for (int i = 0; i < match.length(); i++) {
                replacement.append(filter);
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!config.getBoolean("regexfilter.name", true)) return;
        String name = event.getName();
        boolean invalid = (getVisualLength(name) > config.getInt("name.maxlenth", 16)) ||
                (!nameAllowPattern.matcher(name).matches()) ||
                (nameRegex != null && nameRegex.matcher(name).find());

        if (config.getBoolean("debug", true)) {
            getLogger().severe(String.format("onPreLogin: %s, %s, %s",
                    (getVisualLength(name) > config.getInt("name.maxlenth", 16)),
                    (!nameAllowPattern.matcher(name).matches()),
                    (nameRegex != null && nameRegex.matcher(name).find())));
        }

        if (invalid && config.getBoolean("name.forbidden-kick", true)) {
            List<String> messages = config.getStringList("name.kick-message");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(ChatColor.translateAlternateColorCodes('&', messages.get(i)));
                if (i < messages.size() - 1) sb.append("\n");
            }
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, sb.toString());
        }
    }

    private int getVisualLength(String str) {
        int len = 0;
        for (char c : str.toCharArray()) {
            if (c >= '\u4e00' && c <= '\u9fa5') len += 2;
            else len += 1;
        }
        return len;
    }

    private static class FilterCommand implements CommandExecutor {
        private final AerRegexFilter plugin;

        public FilterCommand(AerRegexFilter plugin) {
            this.plugin = plugin;
        }

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("aerregexfilter.admin")) {
                    sender.sendMessage(ChatColor.RED + "你没有执行此命令的权限。");
                    return true;
                }
                plugin.loadConfig();
                sender.sendMessage(ChatColor.GREEN + "AerRegexFilter -> 配置文件已重载");
                return true;
            }
            sender.sendMessage("§e§lAerRegexFilter §r§7for §b§lYanYuTing §8| §r§fby. §dAerMini");
            return true;
        }
    }
}