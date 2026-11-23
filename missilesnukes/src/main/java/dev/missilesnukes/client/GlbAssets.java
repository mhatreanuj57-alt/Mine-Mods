package dev.missilesnukes.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.missilesnukes.common.MissilesNukes;
import dev.missilesnukes.glb.Gltf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/** Loads the original .glb files from the mod's assets and turns their embedded PNG images into GPU textures. */
public final class GlbAssets {
    public static final ResourceLocation WHITE = MissilesNukes.id("glb/white");
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static boolean whiteReady;

    private GlbAssets() {}

    public static Gltf load(String fileName) {
        ResourceLocation id = MissilesNukes.id("models/glb/" + fileName);
        try {
            Resource res = Minecraft.getInstance().getResourceManager().getResourceOrThrow(id);
            try (InputStream in = res.open()) {
                return Gltf.parse(in.readAllBytes());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load model " + id, e);
        }
    }

    public static ResourceLocation white() {
        if (!whiteReady) {
            NativeImage img = new NativeImage(1, 1, false);
            img.setPixelRGBA(0, 0, 0xFFFFFFFF);
            Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(img));
            whiteReady = true;
        }
        return WHITE;
    }

    /** Texture for an embedded image of a model; created on first use and cached until the next resource reload. */
    public static ResourceLocation texture(String modelKey, Gltf gltf, int imageIndex) {
        String key = modelKey + "/" + imageIndex;
        ResourceLocation cached = TEXTURES.get(key);
        if (cached != null) return cached;
        ResourceLocation loc = MissilesNukes.id("glb/" + key);
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(gltf.images.get(imageIndex)));
            DynamicTexture tex = new DynamicTexture(img);
            tex.setFilter(img.getWidth() > 64, false);   // smooth for the big PBR textures, crisp for the pixel-art effect textures
            Minecraft.getInstance().getTextureManager().register(loc, tex);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot decode image " + key, e);
        }
        TEXTURES.put(key, loc);
        return loc;
    }

    /** Called on resource reload: textures are released and rebuilt lazily. */
    public static void clear() {
        for (ResourceLocation loc : TEXTURES.values()) Minecraft.getInstance().getTextureManager().release(loc);
        TEXTURES.clear();
        if (whiteReady) { Minecraft.getInstance().getTextureManager().release(WHITE); whiteReady = false; }
    }
}
