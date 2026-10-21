package net.gawhara.arabic;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisguisedChat;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerListHeaderAndFooter;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import org.bukkit.entity.Player;

/**
 * بيعترض الباكتات الخارجة للاعبين Bedrock وبيصلّح العربي في أي رسالة، من أي بلجن.
 * الكلاس ده بيتحمّل بس لو PacketEvents موجود.
 */
final class PacketHook extends PacketListenerAbstract implements AutoCloseable {

    private final ArabicPlugin plugin;

    PacketHook(ArabicPlugin plugin) {
        super(PacketListenerPriority.HIGHEST); // آخر واحد، عشان نشوف تعديلات البلجنات التانية
        this.plugin = plugin;
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void close() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Object who = event.getPlayer();
        if (!(who instanceof Player player) || !plugin.shouldFix(player)) {
            return; // لسه في اللوجن أو لاعب Java
        }
        ArabicPlugin.PacketSettings cfg = plugin.packetSettings();
        PacketTypeCommon type = event.getPacketType();

        if (cfg.systemChat() && type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            var w = new WrapperPlayServerSystemChatMessage(event);
            w.setMessage(plugin.shapeLine(w.getMessage()));
            event.markForReEncode(true);
        } else if (cfg.disguisedChat() && type == PacketType.Play.Server.DISGUISED_CHAT) {
            var w = new WrapperPlayServerDisguisedChat(event);
            w.setMessage(plugin.shapeLine(w.getMessage()));
            event.markForReEncode(true);
        } else if (cfg.actionBar() && type == PacketType.Play.Server.ACTION_BAR) {
            var w = new WrapperPlayServerActionBar(event);
            w.setActionBarText(plugin.shapeLine(w.getActionBarText()));
            event.markForReEncode(true);
        } else if (cfg.titles() && type == PacketType.Play.Server.SET_TITLE_TEXT) {
            var w = new WrapperPlayServerSetTitleText(event);
            w.setTitle(plugin.shapeLine(w.getTitle()));
            event.markForReEncode(true);
        } else if (cfg.titles() && type == PacketType.Play.Server.SET_TITLE_SUBTITLE) {
            var w = new WrapperPlayServerSetTitleSubtitle(event);
            w.setSubtitle(plugin.shapeLine(w.getSubtitle()));
            event.markForReEncode(true);
        } else if (cfg.tabList() && type == PacketType.Play.Server.PLAYER_LIST_HEADER_AND_FOOTER) {
            var w = new WrapperPlayServerPlayerListHeaderAndFooter(event);
            w.setHeader(plugin.shapeLine(w.getHeader()));
            w.setFooter(plugin.shapeLine(w.getFooter()));
            event.markForReEncode(true);
        } else if (cfg.windowTitles() && type == PacketType.Play.Server.OPEN_WINDOW) {
            var w = new WrapperPlayServerOpenWindow(event);
            w.setTitle(plugin.shapeLine(w.getTitle()));
            event.markForReEncode(true);
        }
    }
}
