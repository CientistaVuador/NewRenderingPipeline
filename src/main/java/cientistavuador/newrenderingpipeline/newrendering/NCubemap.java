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
import cientistavuador.newrenderingpipeline.util.ObjectCleaner;
import cientistavuador.newrenderingpipeline.util.StringUtils;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.ARBSeamlessCubemapPerTexture;
import org.lwjgl.opengl.ARBTextureCompressionBPTC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL42C;
import static org.lwjgl.system.MemoryUtil.*;
import org.lwjgl.opengl.KHRDebug;

/**
 *
 * @author Cien
 */
public class NCubemap {
    
    public static final NCubemap NULL_CUBEMAP = new NCubemap(
            "Null/Error Cubemap",
            null, null,
            4,
            new float[] {
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f,
                1f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f
            }
    );
    
    public static final NCubemap EMPTY_CUBEMAP = new NCubemap(
            "Empty Cubemap",
            null, null,
            1,
            new float[] {
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f
            }
    );
    
    public static final int SIDES = 6;

    public static final int POSITIVE_X = 0;
    public static final int NEGATIVE_X = 1;

    public static final int POSITIVE_Y = 2;
    public static final int NEGATIVE_Y = 3;

    public static final int POSITIVE_Z = 4;
    public static final int NEGATIVE_Z = 5;

    private static final AtomicLong textureIds = new AtomicLong();

    private static final class WrappedCubemap {

        int texture = 0;
    }

    private final String name;
    private final NCubemapInfo cubemapInfo;
    private final String sha256;
    private final int size;
    private final float[] cubemap;
    private float intensity = 1f;
    
    private final WrappedCubemap wrappedCubemap;

    public NCubemap(
            String name,
            NCubemapInfo cubemapInfo,
            String sha256,
            int size,
            float[] cubemap
    ) {
        int requiredSize = size * size * 3 * SIDES;
        if (cubemap.length != requiredSize) {
            throw new IllegalArgumentException("Invalid cubemap size, required " + requiredSize + " but found " + cubemap.length);
        }

        if (cubemapInfo == null) {
            cubemapInfo = new NCubemapInfo();
        }

        this.size = size;
        this.cubemap = cubemap;
        this.cubemapInfo = cubemapInfo;

        if (sha256 == null) {
            ByteBuffer data = ByteBuffer.allocate(
                    this.cubemap.length * Float.BYTES
            );

            for (float f : this.cubemap) {
                data.putFloat(f);
            }
            
            data.flip();

            sha256 = CryptoUtils.sha256(data);
        }

        if (name == null) {
            name = sha256;
        }

        this.name = name;
        this.sha256 = sha256;

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

    public NCubemapInfo getCubemapInfo() {
        return cubemapInfo;
    }

    public String getSha256() {
        return sha256;
    }

    public int getSize() {
        return size;
    }

    public float[] getCubemap() {
        return cubemap;
    }

    public float getIntensity() {
        return intensity;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public void manualFree() {
        final WrappedCubemap wrapped = this.wrappedCubemap;

        final int texture = wrapped.texture;
        if (texture != 0) {
            glDeleteTextures(texture);
            wrapped.texture = 0;
        }
    }

    private void uploadCubemapSide(int internalFormat, int sideTarget, int sideIndex, FloatBuffer data) {
        int offset = this.size * this.size * 3 * sideIndex;
        data.position(offset);
        glTexImage2D(sideTarget, 0, internalFormat, this.size, this.size, 0, GL_RGB, GL_FLOAT, data);
    }

    private void validateCubemap() {
        if (this.wrappedCubemap.texture != 0) {
            return;
        }

        FloatBuffer nativeMemory = memAllocFloat(this.cubemap.length).put(this.cubemap).flip();
        try {
            glActiveTexture(GL_TEXTURE0);

            int cubemapTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_CUBE_MAP, cubemapTexture);

            int internalFormat = GL_R11F_G11F_B10F;
            if (Main.isSupported(4, 2)) {
                internalFormat = GL42C.GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT;
            }
            if (GL.getCapabilities().GL_ARB_texture_compression_bptc) {
                internalFormat = ARBTextureCompressionBPTC.GL_COMPRESSED_RGB_BPTC_UNSIGNED_FLOAT_ARB;
            }

            uploadCubemapSide(internalFormat, GL_TEXTURE_CUBE_MAP_POSITIVE_X, POSITIVE_X, nativeMemory);
            uploadCubemapSide(internalFormat, GL_TEXTURE_CUBE_MAP_NEGATIVE_X, NEGATIVE_X, nativeMemory);
            uploadCubemapSide(internalFormat, GL_TEXTURE_CUBE_MAP_POSITIVE_Y, POSITIVE_Y, nativeMemory);
            uploadCubemapSide(internalFormat, GL_TEXTURE_CUBE_MAP_NEGATIVE_Y, NEGATIVE_Y, nativeMemory);
            uploadCubemapSide(internalFormat, GL_TEXTURE_CUBE_MAP_POSITIVE_Z, POSITIVE_Z, nativeMemory);
            uploadCubemapSide(internalFormat, GL_TEXTURE_CUBE_MAP_NEGATIVE_Z, NEGATIVE_Z, nativeMemory);

            glGenerateMipmap(GL_TEXTURE_CUBE_MAP);

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
                        StringUtils.truncateStringTo255Bytes("cubemap_" + textureIds.getAndIncrement() + "_" + this.name)
                );
            }

            this.wrappedCubemap.texture = cubemapTexture;
        } finally {
            memFree(nativeMemory);
        }
    }

    public int cubemap() {
        validateCubemap();
        return this.wrappedCubemap.texture;
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
        return Objects.equals(this.sha256, other.sha256);
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 97 * hash + Objects.hashCode(this.sha256);
        return hash;
    }

}
