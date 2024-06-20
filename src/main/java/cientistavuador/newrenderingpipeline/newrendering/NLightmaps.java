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
import cientistavuador.newrenderingpipeline.util.CryptoUtils;
import cientistavuador.newrenderingpipeline.util.ImageUtils;
import cientistavuador.newrenderingpipeline.util.ObjectCleaner;
import cientistavuador.newrenderingpipeline.util.E8Image;
import cientistavuador.newrenderingpipeline.util.StringUtils;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapAmbientCube;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapAmbientCubeBVH;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Vector3f;
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
            1, 1,
            new float[]{0f, 0f, 0f}, new float[]{0f, 0f, 0f}, new float[]{1f, 1f, 1f, 1f},
            null, null,
            0, 0, null,
            null,
            null
    );

    private final String name;
    private final String[] names;
    private final int margin;

    private final float[] lightmaps;
    private final int width;
    private final int height;

    private final E8Image cpuLightmaps;
    private final E8Image cpuLightmapsEmissive;

    private final byte[] colorMap;
    private final int colorMapWidth;
    private final int colorMapHeight;
    
    private final LightmapAmbientCubeBVH ambientCubes;

    private final Map<String, Integer> nameMap = new HashMap<>();
    private final float[] intensities;

    private final String sha256;

    static class WrappedLightmap {

        int texture = 0;
    }

    private final WrappedLightmap lightmapTexture = new WrappedLightmap();

    public NLightmaps(
            String name, String[] names, int margin,
            int width, int height,
            float[] lightmaps, float[] lightmapsEmissive, float[] color,
            E8Image cpuLightmaps, E8Image cpuLightmapsEmissive,
            int colorWidth, int colorHeight, byte[] colorMap,
            LightmapAmbientCubeBVH ambientCubes,
            String sha256
    ) {
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
        for (int i = 0; i < names.length; i++) {
            if (names[i] == null) {
                throw new IllegalArgumentException("Lightmap at index " + i + " has a null name");
            }
            if (nameSet.contains(names[i])) {
                throw new IllegalArgumentException("Duplicate lightmap name: " + names[i]);
            }
            nameSet.add(names[i]);
        }
        
        if (ambientCubes == null) {
            ambientCubes = LightmapAmbientCubeBVH.create(new ArrayList<>());
        }

        this.name = name;
        this.names = names;
        this.margin = margin;

        this.lightmaps = lightmaps;
        this.width = width;
        this.height = height;

        int divisions = Math.max((int) Math.floor(Math.log(margin) / Math.log(2.0)), 2);

        if (cpuLightmaps == null) {
            float[] newLightmaps = lightmaps.clone();
            int newLightmapsWidth = width;
            int newLightmapsHeight = height * names.length;

            for (int i = 0; i < divisions; i++) {
                newLightmaps = ImageUtils.mipmap(newLightmaps, newLightmapsWidth, newLightmapsHeight, 3);
                newLightmapsWidth /= 2;
                newLightmapsHeight /= 2;
            }

            this.cpuLightmaps = new E8Image(newLightmaps, newLightmapsWidth, newLightmapsHeight);
        } else {
            this.cpuLightmaps = cpuLightmaps;
        }

        if (cpuLightmapsEmissive == null) {
            Objects.requireNonNull(lightmapsEmissive, "Lightmaps emissive is null.");

            if (lightmapsEmissive.length != requiredPixels) {
                throw new IllegalArgumentException("Invalid lightmaps emissive size! required " + requiredPixels + ", found " + lightmapsEmissive.length);
            }

            float[] newEmissive = lightmapsEmissive.clone();
            int newEmissiveWidth = width;
            int newEmissiveHeight = height * names.length;

            for (int i = 0; i < divisions; i++) {
                newEmissive = ImageUtils.mipmap(newEmissive, newEmissiveWidth, newEmissiveHeight, 3);
                newEmissiveWidth /= 2;
                newEmissiveHeight /= 2;
            }

            this.cpuLightmapsEmissive = new E8Image(newEmissive, newEmissiveWidth, newEmissiveHeight);
        } else {
            this.cpuLightmapsEmissive = cpuLightmapsEmissive;
        }

        if (colorMap == null) {
            Objects.requireNonNull(color, "Color is null");

            int requiredColorPixels = width * height * 4;

            if (color.length != requiredColorPixels) {
                throw new IllegalArgumentException("Invalid color size! required " + requiredColorPixels + ", found " + color.length);
            }

            float[] newColor = color;
            int newColorWidth = width;
            int newColorHeight = height;

            for (int i = 0; i < divisions; i++) {
                newColor = ImageUtils.mipmap(newColor, newColorWidth, newColorHeight, 4);
                newColorWidth /= 2;
                newColorHeight /= 2;
            }

            this.colorMap = new byte[newColorWidth * newColorHeight * 4];
            for (int y = 0; y < newColorHeight; y++) {
                for (int x = 0; x < newColorWidth; x++) {
                    int r = (int) Math.min(Math.max(newColor[0 + (x * 4) + (y * newColorWidth * 4)] * 255f, 0f), 255f);
                    int g = (int) Math.min(Math.max(newColor[1 + (x * 4) + (y * newColorWidth * 4)] * 255f, 0f), 255f);
                    int b = (int) Math.min(Math.max(newColor[2 + (x * 4) + (y * newColorWidth * 4)] * 255f, 0f), 255f);
                    int a = (int) Math.min(Math.max(newColor[3 + (x * 4) + (y * newColorWidth * 4)] * 255f, 0f), 255f);

                    this.colorMap[0 + (x * 4) + (y * newColorWidth * 4)] = (byte) r;
                    this.colorMap[1 + (x * 4) + (y * newColorWidth * 4)] = (byte) g;
                    this.colorMap[2 + (x * 4) + (y * newColorWidth * 4)] = (byte) b;
                    this.colorMap[3 + (x * 4) + (y * newColorWidth * 4)] = (byte) a;
                }
            }
            this.colorMapWidth = newColorWidth;
            this.colorMapHeight = newColorHeight;
        } else {
            int required = colorWidth * colorHeight * 4;
            if (colorMap.length != required) {
                throw new IllegalArgumentException("Color Map requires " + required + " components! but found " + colorMap.length);
            }
            
            this.colorMap = colorMap;
            this.colorMapWidth = colorWidth;
            this.colorMapHeight = colorHeight;
        }
        
        this.ambientCubes = ambientCubes;
        
        for (int i = 0; i < this.names.length; i++) {
            this.nameMap.put(this.names[i], i);
        }
        this.intensities = new float[this.names.length];
        for (int i = 0; i < this.intensities.length; i++) {
            this.intensities[i] = 1f;
        }

        if (sha256 == null) {
            ByteBuffer buffer = ByteBuffer.allocate(
                    (this.lightmaps.length * 4) + this.cpuLightmaps.getData().length + this.colorMap.length
            );

            for (float f : this.lightmaps) {
                buffer.putFloat(f);
            }
            buffer.put(this.cpuLightmaps.getData());
            buffer.put(this.colorMap);

            buffer.flip();

            this.sha256 = CryptoUtils.sha256(buffer);
        } else {
            this.sha256 = sha256;
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

    public E8Image getCPULightmaps() {
        return cpuLightmaps;
    }

    public E8Image getCPULightmapsEmissive() {
        return cpuLightmapsEmissive;
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

    public LightmapAmbientCubeBVH getAmbientCubes() {
        return ambientCubes;
    }

    public String getSha256() {
        return sha256;
    }
    
    public List<LightmapAmbientCube> searchStaticAmbientCubes(float x, float y, float z) {
        List<LightmapAmbientCube> cubes = this.ambientCubes.search(
                x, y, z,
                this.ambientCubes.getAverageRadius() * 2f
        );
        return cubes;
    }

    public void sampleCPULightmaps(float u, float v, Vector3f outLightmap) {
        int w = this.cpuLightmaps.getWidth();
        int h = this.cpuLightmaps.getHeight() / this.intensities.length;

        int x = (int) (u * w);
        int y = (int) (v * h);
        x = Math.min(Math.max(x, 0), w - 1);
        y = Math.min(Math.max(y, 0), h - 1);

        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (int i = 0; i < this.intensities.length; i++) {
            float intensity = this.intensities[i];
            this.cpuLightmaps.read(
                    x,
                    y + (i * h),
                    outLightmap
            );

            r += outLightmap.x() * intensity;
            g += outLightmap.y() * intensity;
            b += outLightmap.z() * intensity;
        }

        outLightmap.set(r, g, b);
    }

    public void sampleCPULightmapsEmissive(float u, float v, Vector3f outLightmap) {
        int w = this.cpuLightmapsEmissive.getWidth();
        int h = this.cpuLightmapsEmissive.getHeight() / this.intensities.length;

        int x = (int) (u * w);
        int y = (int) (v * h);
        x = Math.min(Math.max(x, 0), w - 1);
        y = Math.min(Math.max(y, 0), h - 1);

        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (int i = 0; i < this.intensities.length; i++) {
            float intensity = this.intensities[i];
            this.cpuLightmapsEmissive.read(
                    x,
                    y + (i * h),
                    outLightmap
            );

            r += outLightmap.x() * intensity;
            g += outLightmap.y() * intensity;
            b += outLightmap.z() * intensity;
        }

        outLightmap.set(r, g, b);
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
