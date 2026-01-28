package xin.vanilla.aotake.util;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import xin.vanilla.aotake.AotakeSweep;
import xin.vanilla.aotake.data.KeyValue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = AotakeSweep.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TextureUtils {
    /**
     * 默认主题文件名
     */
    public static final String DEFAULT_THEME = "textures.png";
    /**
     * 内部主题文件夹路径
     */
    public static final String INTERNAL_THEME_DIR = "textures/gui/";

    private static final Logger LOGGER = LogManager.getLogger();

    public static ResourceLocation loadCustomTexture(String textureName) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        textureName = textureName.replaceAll("\\\\", "/");
        textureName = textureName.startsWith("./") ? textureName.substring(2) : textureName;
        ResourceLocation customTextureLocation = AotakeSweep.createIdentifier(TextureUtils.getSafeThemePath(textureName));
        if (!TextureUtils.isTextureAvailable(customTextureLocation)) {
            if (!textureName.startsWith(INTERNAL_THEME_DIR)) {
                customTextureLocation = AotakeSweep.createIdentifier(TextureUtils.getSafeThemePath(textureName + System.currentTimeMillis()));
                File textureFile = new File(textureName);
                // 检查文件是否存在
                if (!textureFile.exists()) {
                    LOGGER.warn("Texture file not found: {}", textureFile.getAbsolutePath());
                    customTextureLocation = AotakeSweep.createIdentifier(INTERNAL_THEME_DIR + DEFAULT_THEME);
                } else {
                    try (InputStream inputStream = Files.newInputStream(textureFile.toPath())) {
                        // 直接从InputStream创建NativeImage
                        NativeImage nativeImage = NativeImage.read(inputStream);
                        // 创建DynamicTexture并注册到TextureManager
                        DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
                        textureManager.register(customTextureLocation, dynamicTexture);
                    } catch (IOException e) {
                        LOGGER.warn("Failed to load texture: {}", textureFile.getAbsolutePath());
                        LOGGER.error(e);
                        customTextureLocation = AotakeSweep.createIdentifier(INTERNAL_THEME_DIR + DEFAULT_THEME);
                    }
                }
            }
        }
        return customTextureLocation;
    }

    public static String getSafeThemePath(String path) {
        return path.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
    }

    public static boolean isTextureAvailable(ResourceLocation resourceLocation) {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        DynamicTexture miss = MissingTextureAtlasSprite.getTexture();
        AbstractTexture texture = textureManager.getTexture(resourceLocation, miss);
        if (texture == miss) {
            return false;
        }
        // 确保纹理已经加载
        return texture.getId() != -1;
    }

    private static final Map<ResourceLocation, NativeImage> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, KeyValue<Integer, Integer>> TEXTURE_SIZE_CACHE = new HashMap<>();

    /**
     * 从资源中加载纹理并转换为NativeImage
     */
    public static NativeImage getTextureImage(ResourceLocation texture) {
        if (CACHE.containsKey(texture)) {
            return CACHE.get(texture);
        }
        try {
            Resource resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            try (InputStream inputStream = resource.getInputStream()) {
                NativeImage nativeImage = NativeImage.read(inputStream);
                CACHE.put(texture, nativeImage);
                return nativeImage;
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to load texture: {}", texture);
            return null;
        }
    }

    /**
     * 获取纹理的宽高
     */
    public static KeyValue<Integer, Integer> getTextureSize(ResourceLocation texture) {
        KeyValue<Integer, Integer> size = new KeyValue<>(0, 0);
        if (TEXTURE_SIZE_CACHE.containsKey(texture)) {
            size = TEXTURE_SIZE_CACHE.get(texture);
        } else {
            NativeImage textureImage = getTextureImage(texture);
            if (textureImage != null) {
                size.setKey(textureImage.getWidth()).setValue(textureImage.getHeight());
            }
            TEXTURE_SIZE_CACHE.put(texture, size);
        }
        return size;
    }

    public static void clearAll() {
        for (NativeImage img : CACHE.values()) {
            try {
                img.close();
            } catch (Exception ignored) {
            }
        }
        CACHE.clear();
        TEXTURE_SIZE_CACHE.clear();
    }

    @SubscribeEvent
    public static void resourceReloadEvent(TextureStitchEvent.Post event) {
        if (AotakeSweep.MODID.equals(event.getMap().location().getNamespace())) {
            clearAll();
            LOGGER.debug("Cleared texture cache");
        }
    }
}
