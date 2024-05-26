/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package cientistavuador.newrenderingpipeline.newrendering;

import cientistavuador.newrenderingpipeline.Main;
import cientistavuador.newrenderingpipeline.util.ObjectCleaner;
import cientistavuador.newrenderingpipeline.util.StringUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.lwjgl.opengl.ARBTextureCompressionBPTC;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.KHRDebug;

/**
 *
 * @author Cien
 */
public class NLightmaps {

    public static final NLightmaps NULL_LIGHTMAPS = new NLightmaps(
            "null lightmaps",
            new String[]{"none"},
            new float[]{0f, 0f, 0f},
            1, 1,
            0
    );

    private final String name;
    private final String[] names;
    private final float[] lightmaps;
    private final int width;
    private final int height;
    private final int margin;

    private final Map<String, Integer> nameMap = new HashMap<>();
    private final float[] intensities;

    static class WrappedLightmap {

        int texture = 0;
    }

    private final WrappedLightmap lightmapTexture = new WrappedLightmap();

    public NLightmaps(String name, String[] names, float[] lightmaps, int width, int height, int margin) {
        if (name == null) {
            name = "Unnamed";
        }
        Objects.requireNonNull(names, "Names is null");
        Objects.requireNonNull(lightmaps, "Lightmaps is null");
        if (width < 0) {
            throw new IllegalArgumentException("Width is negative");
        }
        if (height < 0) {
            throw new IllegalArgumentException("Height is negative");
        }
        if (margin < 0) {
            throw new IllegalArgumentException("Margin is negative");
        }

        int requiredPixels = width * height * names.length * 3;
        if (lightmaps.length != requiredPixels) {
            throw new IllegalArgumentException("Invalid lightmaps size! required " + requiredPixels + ", found " + lightmaps.length);
        }

        Set<String> nameSet = new HashSet<>();
        for (int i = 0; i < lightmaps.length; i++) {
            if (names[i] == null) {
                throw new IllegalArgumentException("Lightmap at index " + i + " has a null name");
            }
            if (nameSet.contains(names[i])) {
                throw new IllegalArgumentException("Duplicate lightmap name: " + names[i]);
            }
            nameSet.add(names[i]);
        }

        this.name = name;
        this.names = names;
        this.lightmaps = lightmaps;
        this.width = width;
        this.height = height;
        this.margin = margin;
        for (int i = 0; i < this.lightmaps.length; i++) {
            this.nameMap.put(this.names[i], i);
        }
        this.intensities = new float[this.lightmaps.length];
        for (int i = 0; i < this.intensities.length; i++) {
            this.intensities[i] = 1f;
        }

        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedLightmap wrapped = this.lightmapTexture;
        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                int tex = wrapped.texture;
                if (tex != 0) {
                    glDeleteTextures(tex);
                    wrapped.texture = 0;
                }
            });
        });
    }

    public String getName() {
        return name;
    }

    public String getName(int index) {
        return this.names[index];
    }

    public float[] getLightmaps() {
        return lightmaps;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMargin() {
        return margin;
    }
    
    public float getIntensity(int index) {
        return this.intensities[index];
    }

    public void setIntensity(int index, float intensity) {
        this.intensities[index] = intensity;
    }

    public int getNumberOfLightmaps() {
        return this.names.length;
    }

    public int indexOf(String name) {
        Integer i = this.nameMap.get(name);
        if (i == null) {
            return -1;
        }
        return i;
    }

    public void manualFree() {
        final WrappedLightmap wrapped = this.lightmapTexture;
        int tex = wrapped.texture;
        if (tex != 0) {
            glDeleteTextures(tex);
            wrapped.texture = 0;
        }
    }

    private void validate() {
        if (this.lightmapTexture.texture != 0) {
            return;
        }

        int internalFormat = GL_R11F_G11F_B10F;
        if (Main.isSupported(4, 2)) {
            internalFormat = GL42C.GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT;
        }
        if (GL.getCapabilities().GL_ARB_texture_compression_bptc) {
            internalFormat = ARBTextureCompressionBPTC.GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT_ARB;
        }

        int maxLod = (int) Math.abs(
                Math.log(this.margin) / Math.log(2.0)
        );
        
        int lightmap = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, lightmap);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, internalFormat, this.width, this.height, this.names.length, 0, GL_RGB, GL_FLOAT, this.lightmaps);

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glTexParameterf(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_LOD, maxLod);
        glGenerateMipmap(GL_TEXTURE_2D_ARRAY);

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

        if (GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(GL_TEXTURE, lightmap,
                    StringUtils.truncateStringTo255Bytes("lightmap_" + lightmap + "_" + this.name)
            );
        }

        this.lightmapTexture.texture = lightmap;
    }

    public int lightmap() {
        validate();
        return this.lightmapTexture.texture;
    }

}
