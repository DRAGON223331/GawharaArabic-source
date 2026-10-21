package net.gawhara.arabic;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GawharaArabic - بيصلّح العربي للاعبين Bedrock (تشكيل الحروف + الاتجاه).
 * مفيش مكتبات خارجية. Floodgate اختياري (بيتستخدم بالـ reflection لو موجود).
 */
public final class ArabicPlugin extends JavaPlugin implements Listener, TabExecutor {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    /** وضع اللاعب: AUTO حسب default-mode، ON دايمًا، OFF أبدًا. */
    enum Mode {
        AUTO, ON, OFF;

        static Mode parse(String s) {
            if (s == null) {
                return AUTO;
            }
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return AUTO;
            }
        }
    }

    // الإعدادات (volatile لأن الشات async)
    private volatile ArabicShaper.Options options = ArabicShaper.Options.DEFAULT;
    private volatile String defaultMode = "auto";
    private volatile boolean useFloodgate = true;
    private volatile boolean uuidCheck = true;
    private volatile String namePrefix = ".";
    private volatile boolean chatEnabled = true;
    private volatile boolean shapeNames = true;
    /** إعدادات الباكتات: الاتجاه auto عشان السطر كله (بريفكس إنجليزي + عربي) يترتب صح. */
    private volatile ArabicShaper.Options lineOptions = new ArabicShaper.Options(true, false, false);
    private volatile PacketSettings packets = new PacketSettings(false, true, true, true, true, true, true);
    private AutoCloseable packetHook;

    /** إيه اللي نصلّحه على مستوى الباكتات (لأي رسالة من أي بلجن). */
    record PacketSettings(boolean enabled, boolean systemChat, boolean disguisedChat, boolean actionBar,
                          boolean titles, boolean tabList, boolean windowTitles) {
    }

    private final Map<UUID, Mode> overrides = new ConcurrentHashMap<>();
    private File dataFile;

    // Floodgate (reflection)
    private Object floodgateApi;
    private Method floodgateIsPlayer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        loadAll();

        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("arabicfix");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
        getLogger().info("GawharaArabic شغال" + (floodgateApi != null ? " (Floodgate اتلقى)." : "."));
    }

    // ------------------------------------------------------------------ تحميل

    private void loadAll() {
        reloadConfig();
        var c = getConfig();
        defaultMode = c.getString("default-mode", "auto").toLowerCase(Locale.ROOT);
        useFloodgate = c.getBoolean("bedrock-detection.use-floodgate", true);
        uuidCheck = c.getBoolean("bedrock-detection.uuid-check", true);
        namePrefix = c.getString("bedrock-detection.name-prefix", ".");
        chatEnabled = c.getBoolean("chat.enabled", true);
        shapeNames = c.getBoolean("chat.shape-names", true);
        options = new ArabicShaper.Options(
                c.getBoolean("text.strip-diacritics", true),
                c.getBoolean("text.western-digits", false),
                !"auto".equalsIgnoreCase(c.getString("text.base-direction", "rtl")));

        lineOptions = new ArabicShaper.Options(
                options.stripDiacritics(), options.westernDigits(),
                "rtl".equalsIgnoreCase(c.getString("packets.base-direction", "auto")));
        packets = new PacketSettings(
                c.getBoolean("packets.enabled", true),
                c.getBoolean("packets.system-chat", true),
                c.getBoolean("packets.disguised-chat", true),
                c.getBoolean("packets.action-bar", true),
                c.getBoolean("packets.titles", true),
                c.getBoolean("packets.tab-list", true),
                c.getBoolean("packets.window-titles", true));

        initFloodgate();
        initPacketHook();

        overrides.clear();
        if (dataFile.exists()) {
            var y = YamlConfiguration.loadConfiguration(dataFile);
            var sec = y.getConfigurationSection("modes");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    try {
                        overrides.put(UUID.fromString(key), Mode.parse(sec.getString(key)));
                    } catch (IllegalArgumentException ignored) {
                        // UUID بايظ، نتجاهله
                    }
                }
            }
        }
    }

    private void initFloodgate() {
        floodgateApi = null;
        floodgateIsPlayer = null;
        if (!useFloodgate) {
            return;
        }
        try {
            Class<?> cls = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, getClassLoader());
            floodgateApi = cls.getMethod("getInstance").invoke(null);
            floodgateIsPlayer = cls.getMethod("isFloodgatePlayer", UUID.class);
        } catch (Throwable ignored) {
            floodgateApi = null; // Floodgate مش موجود، هنعتمد على الـ UUID/البريفكس
        }
    }

    private void initPacketHook() {
        closeHook();
        if (!packets.enabled()) {
            return;
        }
        if (!getServer().getPluginManager().isPluginEnabled("packetevents")) {
            getLogger().warning("PacketEvents مش مثبّت: رسايل البلجنات التانية (التاب/التايتل/system) مش هتتصلّح. "
                    + "نزّله من Modrinth وحطه في plugins/.");
            return;
        }
        try {
            packetHook = new PacketHook(this);
            getLogger().info("PacketEvents اتلقى: بصلّح الرسايل من كل البلجنات للاعبين Bedrock.");
        } catch (Throwable t) {
            getLogger().warning("مقدرتش أشغّل PacketEvents hook: " + t);
        }
    }

    private void closeHook() {
        if (packetHook != null) {
            try {
                packetHook.close();
            } catch (Exception ignored) {
                // مفيش حاجة نعملها
            }
            packetHook = null;
        }
    }

    @Override
    public void onDisable() {
        closeHook();
    }

    PacketSettings packetSettings() {
        return packets;
    }

    private void saveData() {
        var y = new YamlConfiguration();
        overrides.forEach((id, m) -> {
            if (m != Mode.AUTO) {
                y.set("modes." + id, m.name());
            }
        });
        try {
            y.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("مقدرتش أحفظ data.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ API عام (تقدر تستدعيه من بلجنات تانية)

    /** اللاعب Bedrock؟ */
    public boolean isBedrock(Player p) {
        UUID id = p.getUniqueId();
        if (floodgateApi != null && floodgateIsPlayer != null) {
            try {
                if ((boolean) floodgateIsPlayer.invoke(floodgateApi, id)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // نكمل بباقي الطرق
            }
        }
        if (uuidCheck && id.getMostSignificantBits() == 0L) {
            return true;
        }
        String prefix = namePrefix;
        return prefix != null && !prefix.isEmpty() && p.getName().startsWith(prefix);
    }

    /** هل لازم نصلّح النص للاعب ده؟ */
    public boolean shouldFix(Player p) {
        return switch (overrides.getOrDefault(p.getUniqueId(), Mode.AUTO)) {
            case ON -> true;
            case OFF -> false;
            case AUTO -> switch (defaultMode) {
                case "all" -> true;
                case "off" -> false;
                default -> isBedrock(p);
            };
        };
    }

    /** يصلّح نص عادي. */
    public String fix(String text) {
        return ArabicShaper.fix(text, options);
    }

    /** يصلّح Component (للشات): بيحافظ على الألوان والـ hover/click. */
    public Component shape(Component c) {
        return shapeWith(c, options);
    }

    /** يصلّح سطر كامل (رسالة نظام / أكشن بار / تاب...). الاتجاه بيتحدد من أول حرف قوي. */
    public Component shapeLine(Component c) {
        return shapeWith(c, lineOptions);
    }

    private record Piece(String text, Style style) {
    }

    private Component shapeWith(Component c, ArabicShaper.Options opt) {
        List<Piece> pieces = new ArrayList<>();
        if (flatten(c, Style.empty(), pieces)) {
            return rebuild(c, pieces, opt);
        }
        return shapeRecursive(c, opt); // فيه Translatable/Keybind.. مش هنقدر نسطّحه
    }

    /** يجمع كل النصوص بأستايلاتها الفعلية (بعد الوراثة). false لو فيه نوع Component مش نصي. */
    private boolean flatten(Component c, Style inherited, List<Piece> out) {
        if (!(c instanceof TextComponent t)) {
            return false;
        }
        Style eff = t.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        if (!t.content().isEmpty()) {
            out.add(new Piece(t.content(), eff));
        }
        for (Component kid : t.children()) {
            if (!flatten(kid, eff, out)) {
                return false;
            }
        }
        return true;
    }

    private Component rebuild(Component original, List<Piece> pieces, ArabicShaper.Options opt) {
        StringBuilder full = new StringBuilder();
        List<Style> perChar = new ArrayList<>();
        for (Piece p : pieces) {
            full.append(p.text());
            for (int i = 0; i < p.text().length(); i++) {
                perChar.add(p.style());
            }
        }
        String text = full.toString();
        if (!ArabicShaper.containsArabic(text) || ArabicShaper.containsShaped(text)) {
            return original; // مفيش عربي، أو اتصلّح قبل كده
        }
        ArabicShaper.Mapped m = ArabicShaper.fixMapped(text, opt);

        List<Component> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Style curStyle = null;
        for (int i = 0; i < m.text().length(); i++) {
            Style st = perChar.get(m.src()[i]);
            if (curStyle != null && !st.equals(curStyle)) {
                parts.add(Component.text(cur.toString(), curStyle));
                cur.setLength(0);
            }
            curStyle = st;
            cur.append(m.text().charAt(i));
        }
        if (curStyle != null && cur.length() > 0) {
            parts.add(Component.text(cur.toString(), curStyle));
        }
        return Component.empty().children(parts);
    }

    private Component shapeRecursive(Component c, ArabicShaper.Options opt) {
        if (c instanceof TextComponent t && ArabicShaper.containsArabic(t.content())) {
            c = t.content(ArabicShaper.fix(t.content(), opt));
        }
        if (c instanceof TranslatableComponent tc && !tc.arguments().isEmpty()) {
            List<ComponentLike> args = new ArrayList<>();
            for (TranslationArgument a : tc.arguments()) {
                if (a.value() instanceof Component v) {
                    args.add(shapeWith(v, opt));
                } else {
                    args.add(a);
                }
            }
            c = tc.arguments(args);
        }
        List<Component> kids = c.children();
        if (!kids.isEmpty()) {
            List<Component> out = new ArrayList<>(kids.size());
            for (Component k : kids) {
                out.add(shapeWith(k, opt));
            }
            c = c.children(out);
        }
        return c;
    }

    // ------------------------------------------------------------------ الشات

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!chatEnabled) {
            return;
        }
        ChatRenderer previous = event.renderer(); // نحافظ على فورمات البلجنات التانية (LuckPerms وغيره)
        event.renderer((source, name, message, viewer) -> {
            if (viewer instanceof Player p && shouldFix(p)) {
                Component n = shapeNames ? shape(name) : name;
                return previous.render(source, n, shape(message), viewer);
            }
            return previous.render(source, name, message, viewer);
        });
    }

    // ------------------------------------------------------------------ الأوامر

    private void reply(CommandSender to, String key, String... kv) {
        String body = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            body = body.replace("{" + kv[i] + "}", kv[i + 1]);
        }
        Component prefix = LEGACY.deserialize(getConfig().getString("messages.prefix", ""));
        Component msg = LEGACY.deserialize(body);
        if (to instanceof Player p && shouldFix(p)) {
            prefix = shape(prefix);
            msg = shape(msg);
        }
        to.sendMessage(prefix.append(msg));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("reload")) {
            if (!sender.hasPermission("gawhararabic.admin")) {
                reply(sender, "no-permission");
                return true;
            }
            loadAll();
            reply(sender, "reloaded");
            return true;
        }

        if (!(sender instanceof Player p)) {
            reply(sender, "players-only");
            return true;
        }

        switch (sub) {
            case "status" -> reply(p, "status",
                    "mode", overrides.getOrDefault(p.getUniqueId(), Mode.AUTO).name().toLowerCase(Locale.ROOT),
                    "platform", isBedrock(p) ? "Bedrock" : "Java",
                    "active", shouldFix(p) ? "yes" : "no");
            case "on", "off", "auto" -> {
                Mode m = Mode.parse(sub);
                if (m == Mode.AUTO) {
                    overrides.remove(p.getUniqueId());
                } else {
                    overrides.put(p.getUniqueId(), m);
                }
                saveData();
                reply(p, "mode-set", "mode", sub);
            }
            case "test" -> {
                String sample = "مرحبا بالعالم! هذا اختبار 123 Hello (تجربة) لا إله إلا الله";
                reply(p, "test-header");
                p.sendMessage(Component.text("[before] " + sample));
                p.sendMessage(Component.text("[after]  " + fix(sample)));
            }
            default -> reply(p, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> all = new ArrayList<>(List.of("status", "on", "off", "auto", "test"));
        if (sender.hasPermission("gawhararabic.admin")) {
            all.add("reload");
        }
        String start = args[0].toLowerCase(Locale.ROOT);
        all.removeIf(s -> !s.startsWith(start));
        return all;
    }
}
