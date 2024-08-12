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
import cientistavuador.newrenderingpipeline.util.DXT5TextureStore;
import cientistavuador.newrenderingpipeline.util.DXT5TextureStore.DXT5Texture;
import cientistavuador.newrenderingpipeline.util.ObjectCleaner;
import cientistavuador.newrenderingpipeline.util.E8Image;
import cientistavuador.newrenderingpipeline.util.MipmapUtils;
import cientistavuador.newrenderingpipeline.util.RGBA8Image;
import cientistavuador.newrenderingpipeline.util.StringUtils;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapAmbientCube;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapAmbientCubeBVH;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;
import static org.lwjgl.system.MemoryUtil.*;

/**
 *
 * @author Cien
 */
public class NLightmaps {

    public static final float MAX_LOD = 2f;

    private static final AtomicLong textureIds = new AtomicLong();

    public static final NLightmaps NULL_LIGHTMAPS;
    static {
        try {
            NULL_LIGHTMAPS = new NLightmaps(
                    "Null/Empty Lightmaps",
                    "Null/Empty Lightmaps",
                    
                    new String[] {"Empty"},
                    new DXT5Texture[] {DXT5TextureStore.readDXT5Texture(new ByteArrayInputStream(Base64.getDecoder().decode("KLUv/WDwFAUCAAKECxTApzX/W38mI7RGOVzr6l9D9cpaKU0rIZGWe3AUOCXF+KNQkuevt9uhzhmE3AUFAF01swcAXMCQQOIUgRM=")))},
                    new E8Image[] {new E8Image(new float[] {0f, 0f, 0f}, 1, 1)},
                    new RGBA8Image(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, 1, 1),
                    
                    LightmapAmbientCubeBVH.create(new ArrayList<>())
            );
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    static class WrappedLightmap {

        int texture = 0;
    }

    private final String name;
    private final String uid;

    private final int numberOfLightmaps;

    private final int width;
    private final int height;
    private final int cpuLightmapWidth;
    private final int cpuLightmapHeight;

    private final String[] lightmapsNames;
    private final DXT5Texture[] lightmaps;
    private final E8Image[] cpuLightmaps;
    private final RGBA8Image cpuColor;

    private final LightmapAmbientCubeBVH ambientCubes;

    private final Map<String, Integer> nameMap = new HashMap<>();
    private final float[] intensities;

    private final WrappedLightmap lightmapTexture = new WrappedLightmap();

    public NLightmaps(
            String name,
            String uid,
            String[] lightmapNames,
            DXT5Texture[] lightmaps,
            E8Image[] cpuLightmaps,
            RGBA8Image cpuColor,
            LightmapAmbientCubeBVH ambientCubes
    ) {
        Objects.requireNonNull(lightmapNames, "Lightmap Names is null.");
        Objects.requireNonNull(lightmaps, "Lightmaps is null.");
        Objects.requireNonNull(cpuLightmaps, "CPU Lightmaps is null.");
        Objects.requireNonNull(cpuColor, "CPU Color is null.");
        
        Objects.requireNonNull(ambientCubes, "Ambient Cubes is null.");

        if (name == null) {
            name = "Unnamed";
        }
        if (uid == null) {
            uid = UUID.randomUUID().toString();
        }

        this.name = name;
        this.uid = uid;

        this.numberOfLightmaps = lightmapNames.length;

        if (lightmaps.length != this.numberOfLightmaps) {
            throw new IllegalArgumentException("Lightmaps length is not " + this.numberOfLightmaps);
        }
        if (cpuLightmaps.length != this.numberOfLightmaps) {
            throw new IllegalArgumentException("CPU Lightmaps length is not " + this.numberOfLightmaps);
        }

        if (!ambientCubes.getAmbientCubes().isEmpty()) {
            if (ambientCubes.getAmbientCubes().get(0).getNumberOfAmbientCubes() != this.numberOfLightmaps) {
                throw new IllegalArgumentException("Ambient Cube BVH amount of lightmaps is not " + this.numberOfLightmaps);
            }
        }

        for (int i = 0; i < this.numberOfLightmaps; i++) {
            Objects.requireNonNull(lightmapNames[i], "Lightmap Name at index " + i + " is null.");
            Objects.requireNonNull(lightmaps[i], "Lightmap at index " + i + " is null.");
            Objects.requireNonNull(cpuLightmaps[i], "CPU Lightmap at index " + i + " is null.");
        }
        
        int w = 0;
        int h = 0;
        int cpuw = 0;
        int cpuh = 0;
        if (this.numberOfLightmaps != 0) {
            w = lightmaps[0].width();
            h = lightmaps[0].height();

            cpuw = cpuLightmaps[0].getWidth();
            cpuh = cpuLightmaps[0].getHeight();
        }
        this.width = w;
        this.height = h;
        this.cpuLightmapWidth = cpuw;
        this.cpuLightmapHeight = cpuh;
        
        for (int i = 0; i < this.numberOfLightmaps; i++) {
            DXT5Texture lightmap = lightmaps[i];
            E8Image cLightmap = cpuLightmaps[i];

            if (lightmap.width() != this.width || lightmap.height() != this.height) {
                throw new IllegalArgumentException("Lightmap at index " + i + " has different dimensions!");
            }
            if (cLightmap.getWidth() != this.cpuLightmapWidth || cLightmap.getHeight() != this.cpuLightmapHeight) {
                throw new IllegalArgumentException("CPU Lightmap at index " + i + " has different dimensions!");
            }
        }

        this.lightmapsNames = lightmapNames.clone();
        this.lightmaps = lightmaps.clone();
        this.cpuLightmaps = cpuLightmaps.clone();
        this.cpuColor = cpuColor;

        this.ambientCubes = ambientCubes;

        for (int i = 0; i < this.lightmapsNames.length; i++) {
            this.nameMap.put(this.lightmapsNames[i], i);
        }
        this.intensities = new float[this.numberOfLightmaps];
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

    public String getUID() {
        return uid;
    }

    public int getNumberOfLightmaps() {
        return this.numberOfLightmaps;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getCPULightmapWidth() {
        return cpuLightmapWidth;
    }

    public int getCPULightmapHeight() {
        return cpuLightmapHeight;
    }
    
    public String getLightmapName(int index) {
        return this.lightmapsNames[index];
    }

    public DXT5Texture getLightmap(int index) {
        return this.lightmaps[index];
    }

    public E8Image getCPULightmap(int index) {
        return this.cpuLightmaps[index];
    }
    
    public RGBA8Image getCPUColor() {
        return this.cpuColor;
    }

    public LightmapAmbientCubeBVH getAmbientCubes() {
        return ambientCubes;
    }

    public int indexOf(String name) {
        Integer i = this.nameMap.get(name);
        if (i == null) {
            return -1;
        }
        return i;
    }

    public float getIntensity(int index) {
        return this.intensities[index];
    }

    public void setIntensity(int index, float intensity) {
        this.intensities[index] = intensity;
    }

    public List<LightmapAmbientCube> searchAmbientCubes(float x, float y, float z) {
        List<LightmapAmbientCube> cubes = getAmbientCubes().search(
                x, y, z,
                getAmbientCubes().getAverageRadius() * 2f
        );
        return cubes;
    }

    public void sampleLightmaps(float u, float v, Vector3f outLightmap) {
        int w = getCPULightmapWidth();
        int h = getCPULightmapHeight();

        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (int i = 0; i < getNumberOfLightmaps(); i++) {
            int x = (int) (u * w);
            int y = (int) (v * h);
            x = Math.min(Math.max(x, 0), w - 1);
            y = Math.min(Math.max(y, 0), h - 1);

            float intensity = getIntensity(i);
            getCPULightmap(i).read(x, y, outLightmap);

            r += outLightmap.x() * intensity;
            g += outLightmap.y() * intensity;
            b += outLightmap.z() * intensity;
        }

        outLightmap.set(r, g, b);
    }

    public void sampleColor(float u, float v, Vector4f outColor) {
        RGBA8Image color = getCPUColor();

        int x = (int) (u * color.getWidth());
        int y = (int) (v * color.getHeight());
        x = Math.min(Math.max(x, 0), color.getWidth() - 1);
        y = Math.min(Math.max(y, 0), color.getHeight() - 1);

        color.sample(x, y, outColor);
    }

    public void sampleColorLinear(float u, float v, Vector4f outColor) {
        sampleColor(u, v, outColor);

        outColor.set(
                Math.pow(outColor.x(), 2.2),
                Math.pow(outColor.y(), 2.2),
                Math.pow(outColor.z(), 2.2),
                outColor.w()
        );
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

        glActiveTexture(GL_TEXTURE0);

        int lightmap = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, lightmap);

        int internalFormat = GL_RGBA8;
        if (GL.getCapabilities().GL_EXT_texture_compression_s3tc) {
            internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
        }

        int mipLevels = MipmapUtils.numberOfMipmaps(getWidth(), getHeight());
        for (int i = 0; i < mipLevels; i++) {
            glTexImage3D(
                    GL_TEXTURE_2D_ARRAY,
                    i,
                    internalFormat,
                    
                    MipmapUtils.mipmapSize(getWidth(), i),
                    MipmapUtils.mipmapSize(getHeight(), i),
                    getNumberOfLightmaps(),
                    
                    0,
                    
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    (ByteBuffer) null
            );
        }

        for (int i = 0; i < getNumberOfLightmaps(); i++) {
            DXT5Texture texture = getLightmap(i);
            if (internalFormat == GL_RGBA8) {
                byte[] uncompressed = texture.decompress();

                ByteBuffer data = memAlloc(uncompressed.length).put(uncompressed).flip();
                try {
                    glTexSubImage3D(
                            GL_TEXTURE_2D_ARRAY,
                            0,
                            
                            0,
                            0,
                            i,
                            
                            texture.width(),
                            texture.height(),
                            1,
                            
                            GL_RGBA,
                            GL_UNSIGNED_BYTE,
                            data
                    );
                } finally {
                    memFree(data);
                }
            } else {
                for (int j = 0; j < texture.mips(); j++) {
                    glCompressedTexSubImage3D(
                            GL_TEXTURE_2D_ARRAY,
                            j,
                            
                            0,
                            0,
                            i,
                            
                            texture.mipWidth(j),
                            texture.mipHeight(j),
                            1,
                            
                            internalFormat,
                            texture.mipSlice(j)
                    );
                }
            }
        }

        if (internalFormat == GL_RGBA8) {
            glGenerateMipmap(GL_TEXTURE_2D_ARRAY);
        }

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        glTexParameterf(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAX_LOD, MAX_LOD);

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
