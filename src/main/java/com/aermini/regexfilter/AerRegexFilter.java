package com.aermini.regexfilter;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AerRegexFilter extends Plugin implements Listener {
    private Configuration config;
    private Pattern nameRegex;
    private Pattern chatRegex;
    private Pattern nameAllowPattern;
    private final Map<Pattern, String> chatReplacements = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerCommand(this, new FilterCommand(this));
        getLogger().info("AerRegexFilter 加载成功 | by. AerMini");
    }

    public void loadConfig() {
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(new File(getDataFolder(), "config.yml"));
            nameRegex = compile(config.getString("name-regex"));
            chatRegex = compile(config.getString("chat-regex"));
            nameAllowPattern = compile(config.getString("name.allowregex", ".*"));
            chatReplacements.clear();
            Configuration replaceSection = config.getSection("chat.replace");
            if (replaceSection != null) {
                for (String key : replaceSection.getKeys()) {
                    Pattern p = compile(replaceSection.getString(key));
                    if (p != null) chatReplacements.put(p, key);
                }
            }
        } catch (IOException e) {
            getLogger().severe("无法加载配置: " + e.getMessage());
        }
    }

    private Pattern compile(String regex) {
        if (regex == null || regex.isEmpty()) return null;
        String sanitized = regex.replaceAll("\\r\\n|\\r|\\n", "").trim();
        return Pattern.compile(sanitized);
    }

    private boolean isServerBlacklisted(ProxiedPlayer player, String category, String listName) {
        if (player == null || player.getServer() == null) return false;
        String serverName = player.getServer().getInfo().getName();
        List<String> blacklist = config.getStringList(category + "." + listName);
        return blacklist != null && blacklist.contains(serverName);
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (event.isCancelled() || !(event.getSender() instanceof ProxiedPlayer)) return;
        if (event.isCommand()) return;
        ProxiedPlayer player = (ProxiedPlayer) event.getSender();
        String message = event.getMessage();
        String original = message;
        if (config.getBoolean("regexfilter.chat", true)) {
            if (!isServerBlacklisted(player, "chat", "replace-blacklist")) {
                for (Map.Entry<Pattern, String> entry : chatReplacements.entrySet()) {
                    message = entry.getKey().matcher(message).replaceAll(entry.getValue());
                }
            }
            if (!isServerBlacklisted(player, "chat", "blacklist") && chatRegex != null) {
                message = maskByCharacter(message, chatRegex, config.getString("chat.filter", "*"));
            }
        }
        if (!original.equals(message)) event.setMessage(message);
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
    public void onPreLogin(PreLoginEvent event) {
        if (!config.getBoolean("regexfilter.name", true)) return;
        String name = event.getConnection().getName();
        boolean isLengthInvalid = getVisualLength(name) > config.getInt("name.maxlenth", 16);
        boolean isAllowedInvalid = !nameAllowPattern.matcher(name).matches();
        boolean isRegexMatched = false;
        String offendingWord = "none";
        String filteredName = name;
        if (nameRegex != null) {
            java.util.regex.Matcher m = nameRegex.matcher(name);
            if (m.find()) {
                isRegexMatched = true;
                offendingWord = m.group();
                filteredName = m.replaceAll("*");}}
        boolean invalid = isLengthInvalid || isAllowedInvalid || isRegexMatched;
        if (config.getBoolean("debug", true)) getLogger().severe(String.format("onPreLogin player->%s | lenth->%s(val->%s), allowed->%s, regexmatch->%s(offending->%s), filtered->%s",
                name, isLengthInvalid, getVisualLength(name), isAllowedInvalid, isRegexMatched, offendingWord, filteredName));
        if (invalid && config.getBoolean("name.forbidden-kick", true)) {
            List<String> messages = config.getStringList("name.kick-message");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < messages.size(); i++) {
                sb.append(ChatColor.translateAlternateColorCodes('&', messages.get(i)));
                if (i < messages.size() - 1) sb.append("\n");
            }
            event.setCancelled(true);
            event.setCancelReason(new TextComponent(sb.toString()));
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

    private void saveDefaultConfig() {
        if (!getDataFolder().exists()) getDataFolder().mkdir();
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            try (InputStream in = getResourceAsStream("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private static class FilterCommand extends Command {
        private final AerRegexFilter plugin;
        public FilterCommand(AerRegexFilter plugin) {
            super("aerregexfilter", "aerregexfilter.admin", "agf");
            this.plugin = plugin;
        }
        @Override
        public void execute(CommandSender sender, String[] args) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                plugin.loadConfig();
                sender.sendMessage(new TextComponent(ChatColor.GREEN + "AerRegexFilter -> 配置文件已重载"));
                return;
            }
            sender.sendMessage(new TextComponent("§e§lAerRegexFilter §r§7for §b§lYanYuTing §8| §r§fby. §dAerMini"));
        }
    }
}