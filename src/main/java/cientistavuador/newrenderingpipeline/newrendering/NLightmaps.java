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
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.lwjgl.opengl.ARBTextureCompressionBPTC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.KHRDebug;

/**
 *
 * @author Cien
 */
public class NLightmaps {

    private static final AtomicLong textureIds = new AtomicLong();

    public static final NLightmaps NULL_LIGHTMAPS = new NLightmaps(
            "Empty/Null Lightmaps", new String[]{"None"}, 0,
            new float[]{0f, 0f, 0f}, 1, 1,
            new Vector3f[]{new Vector3f(0f, 0f, 0f)}, new byte[]{0, 0, 0}, 1, 1,
            new byte[]{0, 0, 0, 1}, 1, 1
    );

    private final String name;
    private final String[] names;
    private final int margin;

    private final float[] lightmaps;
    private final int width;
    private final int height;

    private final Vector3f[] indirectIntensities;
    private final byte[] indirectLightmaps;
    private final int indirectWidth;
    private final int indirectHeight;

    private final byte[] colorMap;
    private final int colorMapWidth;
    private final int colorMapHeight;

    private final Map<String, Integer> nameMap = new HashMap<>();
    private final float[] intensities;

    static class WrappedLightmap {

        int texture = 0;
    }

    private final WrappedLightmap lightmapTexture = new WrappedLightmap();

    public NLightmaps(
            String name, String[] names, int margin,
            float[] lightmaps, int width, int height,
            Vector3f[] indirectIntensities, byte[] indirectLightmaps, int indirectWidth, int indirectHeight,
            byte[] colorMap, int colorMapWidth, int colorMapHeight
    ) {
        if (name == null) {
            name = "Unnamed";
        }
        Objects.requireNonNull(names, "Names is null");
        Objects.requireNonNull(lightmaps, "Lightmaps is null");
        Objects.requireNonNull(indirectLightmaps, "Indirect Lightmaps is null");
        Objects.requireNonNull(indirectIntensities, "Indirect Intensities is null");
        Objects.requireNonNull(colorMap, "Color Map is null");
        if (width < 0) {
            throw new IllegalArgumentException("Width is negative");
        }
        if (height < 0) {
            throw new IllegalArgumentException("Height is negative");
        }
        if (margin < 0) {
            throw new IllegalArgumentException("Margin is negative");
        }
        if (indirectWidth < 0) {
            throw new IllegalArgumentException("Indirect Width is negative");
        }
        if (indirectHeight < 0) {
            throw new IllegalArgumentException("Indirect Height is negative");
        }
        if (colorMapWidth < 0) {
            throw new IllegalArgumentException("Color Map Width is negative");
        }
        if (colorMapHeight < 0) {
            throw new IllegalArgumentException("Color Map Height is negative");
        }
        if (indirectIntensities.length != names.length) {
            throw new IllegalArgumentException("Invalid amount of indirect intensities! required " + names.length + ", found " + indirectIntensities.length);
        }
        for (int i = 0; i < indirectIntensities.length; i++) {
            Vector3f at = indirectIntensities[i];
            if (at == null) {
                throw new IllegalArgumentException("Indirect Intensity is null at index " + i);
            }
        }

        int requiredPixels = width * height * names.length * 3;
        if (lightmaps.length != requiredPixels) {
            throw new IllegalArgumentException("Invalid lightmaps size! required " + requiredPixels + ", found " + lightmaps.length);
        }

        int requiredIndirectPixels = indirectWidth * indirectHeight * names.length * 3;
        if (indirectLightmaps.length != requiredIndirectPixels) {
            throw new IllegalArgumentException("Invalid indirect lightmaps size! required " + requiredIndirectPixels + ", found " + indirectLightmaps.length);
        }

        int requiredColorMapPixels = colorMapWidth * colorMapHeight * 4;
        if (colorMap.length != requiredColorMapPixels) {
            throw new IllegalArgumentException("Invalid color map size! required " + requiredColorMapPixels + ", found " + colorMap.length);
        }

        Set<String> nameSet = new HashSet<>();
        for (int i = 0; i < names.length; i++) {
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
        this.margin = margin;
        this.lightmaps = lightmaps;
        this.width = width;
        this.height = height;
        this.indirectIntensities = indirectIntensities;
        this.indirectLightmaps = indirectLightmaps;
        this.indirectWidth = indirectWidth;
        this.indirectHeight = indirectHeight;
        for (int i = 0; i < this.names.length; i++) {
            this.nameMap.put(this.names[i], i);
        }
        this.intensities = new float[this.names.length];
        for (int i = 0; i < this.intensities.length; i++) {
            this.intensities[i] = 1f;
        }
        this.colorMap = colorMap;
        this.colorMapWidth = colorMapWidth;
        this.colorMapHeight = colorMapHeight;

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

    public int getMargin() {
        return margin;
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

    public int getNumberOfIndirectIntensities() {
        return this.indirectIntensities.length;
    }

    public Vector3fc getIndirectIntensity(int index) {
        return this.indirectIntensities[index];
    }

    public byte[] getIndirectLightmaps() {
        return indirectLightmaps;
    }

    public int getIndirectWidth() {
        return indirectWidth;
    }

    public int getIndirectHeight() {
        return indirectHeight;
    }

    public byte[] getColorMap() {
        return colorMap;
    }

    public int getColorMapWidth() {
        return colorMapWidth;
    }

    public int getColorMapHeight() {
        return colorMapHeight;
    }

    public void sampleIndirect(float u, float v, Vector3f outIndirect) {
        outIndirect.zero();

        int x = (int) (u * this.indirectWidth);
        int y = (int) (v * this.indirectHeight);
        x = Math.min(Math.max(x, 0), this.indirectWidth - 1);
        y = Math.min(Math.max(y, 0), this.indirectHeight - 1);

        for (int i = 0; i < this.indirectIntensities.length; i++) {
            Vector3f indirectIntensity = this.indirectIntensities[i];

            int pixelIndex = (x * 3) + (y * this.indirectWidth * 3) + (i * this.indirectWidth * this.indirectHeight * 3);
            float r = ((this.indirectLightmaps[0 + pixelIndex] & 0xFF) / 255f) * indirectIntensity.x();
            float g = ((this.indirectLightmaps[1 + pixelIndex] & 0xFF) / 255f) * indirectIntensity.y();
            float b = ((this.indirectLightmaps[2 + pixelIndex] & 0xFF) / 255f) * indirectIntensity.z();

            float intensity = this.intensities[i];
            r *= intensity;
            g *= intensity;
            b *= intensity;

            outIndirect.add(r, g, b);
        }
    }

    public void sampleColorMap(float u, float v, Vector4f outColor) {
        int x = (int) (u * this.colorMapWidth);
        int y = (int) (v * this.colorMapHeight);
        x = Math.min(Math.max(x, 0), this.colorMapWidth - 1);
        y = Math.min(Math.max(y, 0), this.colorMapHeight - 1);

        int pixelIndex = (x * 4) + (y * this.colorMapWidth * 4);
        float r = ((this.colorMap[0 + pixelIndex] & 0xFF) / 255f);
        float g = ((this.colorMap[1 + pixelIndex] & 0xFF) / 255f);
        float b = ((this.colorMap[2 + pixelIndex] & 0xFF) / 255f);
        float a = ((this.colorMap[3 + pixelIndex] & 0xFF) / 255f);

        outColor.set(r, g, b, a);
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

        /*
        it looks like the bptc compressor on amd cards does
        not work correctly
        */
        String vendor = glGetString(GL_VENDOR);
        if (vendor != null && vendor.toLowerCase().contains("nvidia")) {
            if (Main.isSupported(4, 2)) {
                internalFormat = GL42C.GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT;
            } else if (GL.getCapabilities().GL_ARB_texture_compression_bptc) {
                internalFormat = ARBTextureCompressionBPTC.GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT_ARB;
            }
        }

        int maxLod = (int) Math.abs(
                Math.log(this.margin) / Math.log(2.0)
        );

        glActiveTexture(GL_TEXTURE0);

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

        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            glTexParameterf(
                    GL_TEXTURE_2D_ARRAY,
                    EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
            );
        }

        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

        if (GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(GL_TEXTURE, lightmap,
                    StringUtils.truncateStringTo255Bytes("lightmap_" + textureIds.getAndIncrement() + "_" + this.name)
            );
        }

        this.lightmapTexture.texture = lightmap;
    }

    public int lightmaps() {
        validate();
        return this.lightmapTexture.texture;
    }

}
