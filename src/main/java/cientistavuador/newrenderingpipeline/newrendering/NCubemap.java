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
import cientistavuador.newrenderingpipeline.util.E8Image;
import cientistavuador.newrenderingpipeline.util.ObjectCleaner;
import cientistavuador.newrenderingpipeline.util.StringUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.opengl.ARBSeamlessCubemapPerTexture;
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
public class NCubemap {

    public static void calculateColor(DXT5Texture side, Vector3f outColor) {
        Objects.requireNonNull(side, "Side is null.");
        if (side.width() == 0 || side.height() == 0) {
            outColor.set(0f, 0f, 0f);
            return;
        }

        E8Image img = new E8Image(side.decompress(), side.width(), side.height());

        float r = 0f;
        float g = 0f;
        float b = 0f;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                img.read(x, y, outColor);

                r += outColor.x();
                g += outColor.y();
                b += outColor.z();
            }
        }
        float inv = 1f / (img.getWidth() * img.getHeight());
        r *= inv;
        g *= inv;
        b *= inv;

        outColor.set(r, g, b);
    }

    public static final String ERROR_CUBEMAP_SIDE_DATA = "KLUv/aTwVQUA9AIA0oUPGMCnNf//m/7vk8qEpNcol2T9xZau2d22Un93FxVR8bd2d/d/axFieLBnPd0YzJij/JI4TfXTwIoQDhnMWgEIAIXnA3BykD/4qf358w9Q/lD/AwgJJM4VB+zWAUwAAAh/AQD8/zkQAiUBAJAAAAUFUFARRBFEF7gXuKqqqqoEAF0Fel7/AU/y/zP1OqAuEMj5YlU=";
    public static final NCubemap NULL_CUBEMAP;

    static {
        try {
            DXT5Texture errorCubemapSide = DXT5TextureStore.readDXT5Texture(new ByteArrayInputStream(Base64.getDecoder().decode(ERROR_CUBEMAP_SIDE_DATA)));
            DXT5Texture[] sides = new DXT5Texture[NCubemap.SIDES];
            for (int i = 0; i < sides.length; i++) {
                sides[i] = errorCubemapSide;
            }

            NULL_CUBEMAP = new NCubemap(
                    "Error/Null/Empty Cubemap Texture",
                    "Error/Null/Empty Cubemap Texture",
                    null,
                    sides,
                    new Vector3f(0.5f, 0f, 0.5f)
            );
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static final AtomicLong textureIds = new AtomicLong();

    private static final class WrappedCubemap {

        int texture = 0;
    }

    public static final int SIDES = 6;

    public static final int POSITIVE_X = 0;
    public static final int NEGATIVE_X = 1;

    public static final int POSITIVE_Y = 2;
    public static final int NEGATIVE_Y = 3;

    public static final int POSITIVE_Z = 4;
    public static final int NEGATIVE_Z = 5;

    private final String name;
    private final String uid;
    private final NCubemapBox cubemapBox;
    private final DXT5Texture[] sideTextures;
    private final int size;
    private final Vector3f cubemapColor;

    private float intensity = 1f;

    @SuppressWarnings("unchecked")
    private final WeakReference<E8Image>[] sideTexturesData = new WeakReference[SIDES];

    private final WrappedCubemap wrappedCubemap;

    public NCubemap(
            String name,
            String uid,
            NCubemapBox cubemapBox,
            DXT5Texture[] sideTextures,
            Vector3fc cubemapColor
    ) {
        if (name == null) {
            name = "Unnamed";
        }
        this.name = name;

        if (uid == null) {
            uid = UUID.randomUUID().toString();
        }
        this.uid = uid;

        if (cubemapBox == NCubemapBox.NULL_CUBEMAP_BOX) {
            cubemapBox = null;
        }
        this.cubemapBox = cubemapBox;

        if (sideTextures == null) {
            throw new NullPointerException("Side Textures is null.");
        }
        if (sideTextures.length != SIDES) {
            throw new IllegalArgumentException("Side Textures Length is not " + SIDES + ".");
        }

        int cubemapSize = -1;
        this.sideTextures = new DXT5TextureStore.DXT5Texture[SIDES];
        for (int i = 0; i < sideTextures.length; i++) {
            DXT5Texture texture = sideTextures[i];
            if (texture == null) {
                throw new NullPointerException("Texture at index " + i + " is null.");
            }
            if (texture.width() != texture.height()) {
                throw new IllegalArgumentException("Texture at index " + i + " is not a square.");
            }
            if (cubemapSize == -1) {
                cubemapSize = texture.width();
            }
            if (texture.width() != cubemapSize) {
                throw new IllegalArgumentException("Texture at index " + i + " does not have the same size.");
            }
            this.sideTextures[i] = texture;
        }
        this.size = cubemapSize;

        this.cubemapColor = new Vector3f();
        if (cubemapColor == null) {
            float r = 0f;
            float g = 0f;
            float b = 0f;
            for (int i = 0; i < this.sideTextures.length; i++) {
                calculateColor(this.sideTextures[i], this.cubemapColor);
                r += this.cubemapColor.x();
                g += this.cubemapColor.y();
                b += this.cubemapColor.z();
            }
            float inv = 1f / SIDES;
            r *= inv;
            g *= inv;
            b *= inv;

            this.cubemapColor.set(r, g, b);
        } else {
            this.cubemapColor.set(cubemapColor);
        }

        this.wrappedCubemap = new WrappedCubemap();
        
        registerForCleaning();
    }

    private void registerForCleaning() {
        final WrappedCubemap wrapped = this.wrappedCubemap;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                final int texture = wrapped.texture;
                if (texture != 0) {
                    glDeleteTextures(texture);
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

    public NCubemapBox getCubemapBox() {
        return cubemapBox;
    }

    public boolean isParallaxCorrected() {
        return getCubemapBox() != null;
    }

    public int getNumberOfSides() {
        return this.sideTextures.length;
    }

    public DXT5Texture getSideTexture(int index) {
        return this.sideTextures[index];
    }

    public E8Image getSideTextureData(int index) {
        WeakReference<E8Image> reference = this.sideTexturesData[index];
        if (reference != null) {
            E8Image cached = reference.get();
            if (cached != null) {
                return cached;
            }
        }

        E8Image decompressed = new E8Image(this.sideTextures[index].decompress(), this.size, this.size);
        this.sideTexturesData[index] = new WeakReference<>(decompressed);
        return decompressed;
    }

    public int getSize() {
        return size;
    }

    public Vector3fc getCubemapColor() {
        return cubemapColor;
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    private void validateCubemap() {
        if (this.wrappedCubemap.texture != 0) {
            return;
        }

        glActiveTexture(GL_TEXTURE0);

        int cubemapTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_CUBE_MAP, cubemapTexture);

        int internalFormat = GL_RGBA8;
        if (GL.getCapabilities().GL_EXT_texture_compression_s3tc) {
            internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
        }

        for (int i = 0; i < this.getNumberOfSides(); i++) {
            DXT5Texture texture = this.getSideTexture(i);

            if (internalFormat == GL_RGBA8) {
                byte[] uncompressed = texture.decompress();

                ByteBuffer data = memAlloc(uncompressed.length).put(uncompressed).flip();
                try {
                    glTexImage2D(
                            GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                            0,
                            internalFormat,
                            texture.width(),
                            texture.height(),
                            0,
                            GL_RGBA,
                            GL_UNSIGNED_BYTE,
                            data
                    );
                } finally {
                    memFree(data);
                }
            } else {
                for (int j = 0; j < texture.mips(); j++) {
                    glCompressedTexImage2D(
                            GL_TEXTURE_CUBE_MAP_POSITIVE_X + i,
                            j,
                            internalFormat,
                            texture.mipWidth(j),
                            texture.mipHeight(j),
                            0,
                            texture.mipSlice(j)
                    );
                }
            }

        }

        if (internalFormat == GL_RGBA8) {
            glGenerateMipmap(GL_TEXTURE_CUBE_MAP);
        }

        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);

        if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
            glTexParameterf(
                    GL_TEXTURE_CUBE_MAP,
                    EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
            );
        }

        if (GL.getCapabilities().GL_ARB_seamless_cubemap_per_texture) {
            glTexParameteri(GL_TEXTURE_CUBE_MAP, ARBSeamlessCubemapPerTexture.GL_TEXTURE_CUBE_MAP_SEAMLESS, GL_TRUE);
        }
        
        glBindTexture(GL_TEXTURE_CUBE_MAP, 0);

        if (GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(GL_TEXTURE, cubemapTexture,
                    StringUtils.truncateStringTo255Bytes("cubemap_" + textureIds.getAndIncrement() + "_" + this.getName())
            );
        }

        this.wrappedCubemap.texture = cubemapTexture;
    }

    public void manualFree() {
        final WrappedCubemap wrapped = this.wrappedCubemap;

        final int texture = wrapped.texture;
        if (texture != 0) {
            glDeleteTextures(texture);
            wrapped.texture = 0;
        }
    }

    public int cubemap() {
        validateCubemap();
        return this.wrappedCubemap.texture;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 89 * hash + Objects.hashCode(this.uid);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NCubemap other = (NCubemap) obj;
        return Objects.equals(this.uid, other.uid);
    }

}
