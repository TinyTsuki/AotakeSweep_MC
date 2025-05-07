package xin.vanilla.aotake.util;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import lombok.NonNull;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandSource;
import net.minecraft.command.arguments.BlockStateParser;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.IPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.Util;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.config.ServerConfig;
import xin.vanilla.aotake.data.player.PlayerSweepDataCapability;

import javax.annotation.Nullable;

public class AotakeUtils {

    private static final Logger LOGGER = LogManager.getLogger();

    // region 消息相关

    /**
     * 广播消息
     *
     * @param player  发送者
     * @param message 消息
     */
    public static void broadcastMessage(ServerPlayerEntity player, Component message) {
        player.server.getPlayerList().broadcastMessage(new TranslationTextComponent("chat.type.announcement", player.getDisplayName(), message.toChatComponent()), ChatType.SYSTEM, Util.NIL_UUID);
    }

    /**
     * 广播消息
     *
     * @param server  发送者
     * @param message 消息
     */
    public static void broadcastMessage(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastMessage(new TranslationTextComponent("chat.type.announcement", "Server", message.toChatComponent()), ChatType.SYSTEM, Util.NIL_UUID);
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(ServerPlayerEntity player, Component message) {
        player.sendMessage(message.toChatComponent(AotakeUtils.getPlayerLanguage(player)), player.getUUID());
    }

    /**
     * 发送消息
     *
     * @param player  玩家
     * @param message 消息
     */
    public static void sendMessage(ServerPlayerEntity player, String message) {
        player.sendMessage(Component.literal(message).toChatComponent(), player.getUUID());
    }

    /**
     * 发送翻译消息
     *
     * @param player 玩家
     * @param key    翻译键
     * @param args   参数
     */
    public static void sendTranslatableMessage(ServerPlayerEntity player, String key, Object... args) {
        player.sendMessage(Component.translatable(key, args).setLanguageCode(AotakeUtils.getPlayerLanguage(player)).toChatComponent(), player.getUUID());
    }

    /**
     * 发送翻译消息
     *
     * @param source  指令来源
     * @param success 是否成功
     * @param key     翻译键
     * @param args    参数
     */
    public static void sendTranslatableMessage(CommandSource source, boolean success, String key, Object... args) {
        if (source.getEntity() != null && source.getEntity() instanceof ServerPlayerEntity) {
            try {
                sendTranslatableMessage(source.getPlayerOrException(), key, args);
            } catch (CommandSyntaxException ignored) {
            }
        } else if (success) {
            source.sendSuccess(Component.translatable(key, args).setLanguageCode(ServerConfig.DEFAULT_LANGUAGE.get()).toChatComponent(), false);
        } else {
            source.sendFailure(Component.translatable(key, args).setLanguageCode(ServerConfig.DEFAULT_LANGUAGE.get()).toChatComponent());
        }
    }

    /**
     * 广播数据包至所有玩家
     *
     * @param packet 数据包
     */
    public static void broadcastPacket(IPacket<?> packet) {
        AotakeSweep.getServerInstance().getPlayerList().getPlayers().forEach(player -> player.connection.send(packet));
    }

    // endregion 消息相关


    // region 玩家语言相关

    public static String getPlayerLanguage(ServerPlayerEntity player) {
        try {
            return PlayerSweepDataCapability.getData(player).getValidLanguage(player);
        } catch (IllegalArgumentException i) {
            return ServerConfig.DEFAULT_LANGUAGE.get();
        }
    }

    public static String getValidLanguage(@Nullable PlayerEntity player, @Nullable String language) {
        String result;
        if (StringUtils.isNullOrEmptyEx(language) || "client".equalsIgnoreCase(language)) {
            if (player instanceof ServerPlayerEntity) {
                result = AotakeUtils.getServerPlayerLanguage((ServerPlayerEntity) player);
            } else {
                result = AotakeUtils.getClientLanguage();
            }
        } else if ("server".equalsIgnoreCase(language)) {
            result = ServerConfig.DEFAULT_LANGUAGE.get();
        } else {
            result = language;
        }
        return result;
    }

    public static String getServerPlayerLanguage(ServerPlayerEntity player) {
        return player.getLanguage();
    }

    /**
     * 复制玩家语言设置
     *
     * @param originalPlayer 原始玩家
     * @param targetPlayer   目标玩家
     */
    public static void clonePlayerLanguage(ServerPlayerEntity originalPlayer, ServerPlayerEntity targetPlayer) {
        FieldUtils.setPrivateFieldValue(ServerPlayerEntity.class, targetPlayer, FieldUtils.getPlayerLanguageFieldName(originalPlayer), getServerPlayerLanguage(originalPlayer));
    }

    public static String getClientLanguage() {
        return Minecraft.getInstance().getLanguageManager().getSelected().getCode();
    }

    // endregion 玩家语言相关


    // region 杂项

    /**
     * 播放音效
     *
     * @param player 玩家
     * @param sound  音效
     * @param volume 音量
     * @param pitch  音调
     */
    public static void playSound(ServerPlayerEntity player, ResourceLocation sound, float volume, float pitch) {
        SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(sound);
        if (soundEvent != null) {
            player.playNotifySound(soundEvent, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    /**
     * 序列化方块默认状态
     */
    public static String serializeBlockState(Block block) {
        return serializeBlockState(block.defaultBlockState());
    }

    /**
     * 序列化方块状态
     */
    public static String serializeBlockState(BlockState blockState) {
        return BlockStateParser.serialize(blockState);
    }

    /**
     * 反序列化方块状态
     */
    public static BlockState deserializeBlockState(String block) {
        try {
            return new BlockStateParser(new StringReader(block), false).parse(true).getState();
        } catch (Exception e) {
            LOGGER.error("Invalid unsafe block: {}", block, e);
            return null;
        }
    }

    /**
     * 获取方块注册ID
     */
    @NonNull
    public static String getBlockRegistryName(BlockState blockState) {
        return getBlockRegistryName(blockState.getBlock());
    }

    /**
     * 获取方块注册ID
     */
    @NonNull
    public static String getBlockRegistryName(Block block) {
        ResourceLocation location = block.getRegistryName();
        return location == null ? "" : location.toString();
    }

    // endregion 杂项
}
