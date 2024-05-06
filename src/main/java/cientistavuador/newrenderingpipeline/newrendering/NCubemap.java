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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.lwjgl.opengl.ARBSeamlessCubemapPerTexture;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.EXTTextureSRGB;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;
import static org.lwjgl.system.MemoryUtil.*;

/**
 *
 * @author Cien
 */
public class NCubemap {

    public static final NCubemap NULL_CUBE_MAP;
    
    static {
        byte[] data = {
            (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF,
            (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0x00, (byte) 0xFF, (byte) 0xFF
        };
        
        ByteBuffer totalData = ByteBuffer.allocate(data.length + 4).putInt(2).put(data).flip();
        String sha256 = CryptoUtils.sha256(totalData);
        
        NULL_CUBE_MAP = new NCubemap(
                "Null/Error Cubemap",
                2,
                data,
                sha256,
                true,
                true,
                false,
                null,
                null,
                null,
                null
        );
    }
    
    public static final NCubemap EMPTY_CUBE_MAP;
    
    static {
        byte[] data = {
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0xFF
        };
        
        ByteBuffer totalData = ByteBuffer.allocate(data.length + 4).putInt(1).put(data).flip();
        String sha256 = CryptoUtils.sha256(totalData);
        
        EMPTY_CUBE_MAP = new NCubemap(
                "Empty Cubemap",
                1,
                data,
                sha256,
                true,
                true,
                false,
                null,
                null,
                null,
                null
        );
    }
    
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
    private final int size;
    private final byte[] cubemap;
    private final String sha256;
    private final boolean srgb;
    private final boolean compressed;
    private final boolean parallaxCorrected;
    private final Vector3d parallaxCubemapPosition;
    private final Vector3d parallaxPosition;
    private final Quaternionf parallaxRotation;
    private final Vector3f parallaxSize;
    private final WrappedCubemap wrappedCubemap;

    public NCubemap(
            String name,
            int size,
            byte[] cubemap,
            String sha256,
            boolean srgb,
            boolean compressed,
            boolean parallaxCorrected,
            Vector3dc parallaxCubemapPosition,
            Vector3dc parallaxPosition,
            Quaternionf parallaxRotation,
            Vector3fc parallaxSize
    ) {
        if (name == null) {
            this.name = sha256;
        } else {
            this.name = name;
        }

        int requiredSize = size * size * 4 * SIDES;
        if (cubemap.length != requiredSize) {
            throw new IllegalArgumentException("Invalid cubemap size, required " + requiredSize + " but found " + cubemap.length);
        }
        
        this.size = size;
        this.cubemap = cubemap;
        this.sha256 = sha256;
        this.srgb = srgb;
        this.compressed = compressed;
        this.parallaxCorrected = parallaxCorrected;
        
        if (parallaxCubemapPosition == null) {
            this.parallaxCubemapPosition = new Vector3d();
        } else {
            this.parallaxCubemapPosition = new Vector3d(parallaxCubemapPosition);
        }
        
        if (parallaxPosition == null) {
            this.parallaxPosition = new Vector3d();
        } else {
            this.parallaxPosition = new Vector3d(parallaxPosition);
        }
        
        if (parallaxRotation == null) {
            this.parallaxRotation = new Quaternionf();
        } else {
            this.parallaxRotation = new Quaternionf(parallaxRotation);
        }
        
        if (parallaxSize == null) {
            this.parallaxSize = new Vector3f();
        } else {
            this.parallaxSize = new Vector3f(parallaxSize);
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

    public int getSize() {
        return size;
    }

    public byte[] getCubemap() {
        return cubemap;
    }

    public String getSha256() {
        return sha256;
    }

    public boolean isSrgb() {
        return srgb;
    }

    public boolean isCompressed() {
        return compressed;
    }

    public boolean isParallaxCorrected() {
        return parallaxCorrected;
    }

    public Vector3dc getParallaxCubemapPosition() {
        return parallaxCubemapPosition;
    }

    public Vector3dc getParallaxPosition() {
        return parallaxPosition;
    }

    public Quaternionfc getParallaxRotation() {
        return parallaxRotation;
    }
    
    public Vector3fc getParallaxSize() {
        return parallaxSize;
    }

    public void manualFree() {
        final WrappedCubemap wrapped = this.wrappedCubemap;

        final int texture = wrapped.texture;
        if (texture != 0) {
            glDeleteTextures(texture);
            wrapped.texture = 0;
        }
    }
    
    private void uploadCubemapSide(int internalFormat, int sideTarget, int sideIndex, ByteBuffer data) {
        int offset = this.size * this.size * 4 * sideIndex;
        data.position(offset);
        glTexImage2D(sideTarget, 0, internalFormat, this.size, this.size, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
    }
    
    private void validateCubemap() {
        if (this.wrappedCubemap.texture != 0) {
            return;
        }

        ByteBuffer nativeMemory = memAlloc(this.cubemap.length).put(this.cubemap).flip();
        try {
            glActiveTexture(GL_TEXTURE0);

            int cubemapTexture = glGenTextures();
            glBindTexture(GL_TEXTURE_CUBE_MAP, cubemapTexture);
            
            int internalFormat;
            
            if (this.srgb) {
                internalFormat = GL_SRGB8;
            } else {
                internalFormat = GL_RGB8;
            }
            
            if (GL.getCapabilities().GL_EXT_texture_compression_s3tc && this.compressed) {
                if (this.srgb) {
                    if (GL.getCapabilities().GL_EXT_texture_sRGB) {
                        internalFormat = EXTTextureSRGB.GL_COMPRESSED_SRGB_S3TC_DXT1_EXT;
                    }
                } else {
                    internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGB_S3TC_DXT1_EXT;
                }
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
    public int hashCode() {
        int hash = 7;
        hash = 23 * hash + Objects.hashCode(this.sha256);
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
        return Objects.equals(this.sha256, other.sha256);
    }

}
