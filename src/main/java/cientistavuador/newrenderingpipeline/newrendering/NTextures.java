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
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.EXTTextureSRGB;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL42C;

/**
 *
 * @author Cien
 */
public class NTextures {

    private static final AtomicLong textureIds = new AtomicLong();

    public static final NTextures NULL_TEXTURE;

    static {
        int blackPixel = 0x00_00_00_FF;
        int pinkPixel = 0xFF_00_FF_FF;
        int nullTextureSize = 64;
        int[] nullTexturePixels = new int[nullTextureSize * nullTextureSize];
        for (int y = 0; y < nullTextureSize; y++) {
            int pixelA = pinkPixel;
            int pixelB = blackPixel;
            if (y % 2 != 0) {
                pixelA = blackPixel;
                pixelB = pinkPixel;
            }
            for (int x = 0; x < nullTextureSize; x++) {
                if (x % 2 == 0) {
                    nullTexturePixels[x + (y * nullTextureSize)] = pixelA;
                } else {
                    nullTexturePixels[x + (y * nullTextureSize)] = pixelB;
                }
            }
        }

        byte[] nullTextureData = new byte[nullTexturePixels.length * 4];
        for (int i = 0; i < nullTexturePixels.length; i++) {
            int pixel = nullTexturePixels[i];

            int r = (pixel >>> 24) & 0xFF;
            int g = (pixel >>> 16) & 0xFF;
            int b = (pixel >>> 8) & 0xFF;
            int a = (pixel >>> 0) & 0xFF;

            nullTextureData[(i * 4) + 0] = (byte) r;
            nullTextureData[(i * 4) + 1] = (byte) g;
            nullTextureData[(i * 4) + 2] = (byte) b;
            nullTextureData[(i * 4) + 3] = (byte) a;
        }

        NULL_TEXTURE = NTexturesIO.load(
                "Error/Null/Empty Texture",
                nullTextureSize, nullTextureSize,
                nullTextureData,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static class WrappedTexture {

        public int texture = 0;
    }

    private final String name;
    private final int width;
    private final int height;
    private final byte[] redGreenBlueAlpha;
    private final byte[] heightRoughnessMetallicNormalX;
    private final byte[] emissiveRedGreenBlueNormalY;
    private final NBlendingMode blendingMode;
    private final boolean heightMapSupported;
    private final String sha256;

    private final WrappedTexture r_g_b_a = new WrappedTexture();
    private final WrappedTexture ht_rg_mt_nx = new WrappedTexture();
    private final WrappedTexture er_eg_eb_ny = new WrappedTexture();

    public NTextures(
            String name,
            int width, int height,
            byte[] redGreenBlueAlpha,
            byte[] heightRoughnessMetallicNormalX,
            byte[] emissiveRedGreenBlueNormalY,
            NBlendingMode blendingMode,
            boolean heightMapSupported,
            String sha256
    ) {
        if (width < 0) {
            throw new IllegalArgumentException("width is negative");
        }
        if (height < 0) {
            throw new IllegalArgumentException("height is negative");
        }

        Objects.requireNonNull(redGreenBlueAlpha, "redGreenBlueAlpha is null");
        Objects.requireNonNull(heightRoughnessMetallicNormalX, "heightRoughnessMetallicNormalX is null");
        Objects.requireNonNull(emissiveRedGreenBlueNormalY, "emissiveRedGreenBlueNormalY is null");
        Objects.requireNonNull(blendingMode, "blendingMode is null");

        int pixels = width * height * 4;

        if (redGreenBlueAlpha.length != pixels) {
            throw new IllegalArgumentException("redGreenBlueAlpha has a invalid amount of bytes, found: " + redGreenBlueAlpha.length + ", required: " + pixels);
        }

        if (heightRoughnessMetallicNormalX.length != pixels) {
            throw new IllegalArgumentException("heightRoughnessMetallicNormalX has a invalid amount of bytes, found: " + heightRoughnessMetallicNormalX.length + ", required: " + pixels);
        }

        if (emissiveRedGreenBlueNormalY.length != pixels) {
            throw new IllegalArgumentException("emissiveRedGreenBlueNormalY has a invalid amount of bytes, found: " + emissiveRedGreenBlueNormalY.length + ", required: " + pixels);
        }

        this.width = width;
        this.height = height;
        this.redGreenBlueAlpha = redGreenBlueAlpha;
        this.heightRoughnessMetallicNormalX = heightRoughnessMetallicNormalX;
        this.emissiveRedGreenBlueNormalY = emissiveRedGreenBlueNormalY;
        this.blendingMode = blendingMode;
        this.heightMapSupported = heightMapSupported;

        if (sha256 == null) {
            ByteBuffer totalData = ByteBuffer.allocate(
                    Integer.BYTES
                    + Integer.BYTES
                    + this.redGreenBlueAlpha.length
                    + this.heightRoughnessMetallicNormalX.length
                    + this.emissiveRedGreenBlueNormalY.length
            )
                    .putInt(width)
                    .putInt(height)
                    .put(this.redGreenBlueAlpha)
                    .put(this.heightRoughnessMetallicNormalX)
                    .put(this.emissiveRedGreenBlueNormalY)
                    .flip();

            sha256 = CryptoUtils.sha256(totalData);
        }
        this.sha256 = sha256;

        registerForCleaning();

        if (name == null) {
            this.name = this.sha256;
        } else {
            this.name = name;
        }
    }

    private void registerForCleaning() {
        final WrappedTexture final_r_g_b_a = this.r_g_b_a;
        final WrappedTexture final_ht_rg_mt_nx = this.ht_rg_mt_nx;
        final WrappedTexture final_er_eg_eb_ny = this.er_eg_eb_ny;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                int tex_r_g_b_a = final_r_g_b_a.texture;
                int tex_ht_rg_mt_nx = final_ht_rg_mt_nx.texture;
                int tex_er_eg_eb_ny = final_er_eg_eb_ny.texture;

                if (tex_r_g_b_a != 0) {
                    glDeleteTextures(tex_r_g_b_a);
                    final_r_g_b_a.texture = 0;
                }
                if (tex_ht_rg_mt_nx != 0) {
                    glDeleteTextures(tex_ht_rg_mt_nx);
                    final_ht_rg_mt_nx.texture = 0;
                }
                if (tex_er_eg_eb_ny != 0) {
                    glDeleteTextures(tex_er_eg_eb_ny);
                    final_er_eg_eb_ny.texture = 0;
                }
            });
        });
    }

    public String getName() {
        return name;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getRedGreenBlueAlpha() {
        return redGreenBlueAlpha;
    }

    public byte[] getHeightRoughnessMetallicNormalX() {
        return heightRoughnessMetallicNormalX;
    }

    public byte[] getEmissiveRedGreenBlueNormalY() {
        return emissiveRedGreenBlueNormalY;
    }

    public NBlendingMode getBlendingMode() {
        return blendingMode;
    }

    public boolean isHeightMapSupported() {
        return heightMapSupported;
    }

    public String getSha256() {
        return sha256;
    }

    private int loadTexture(int internalFormat, byte[] textureData, String name, long textureId) {
        ByteBuffer textureBuffer = MemoryUtil
                .memAlloc(textureData.length)
                .put(textureData)
                .flip();
        try {
            int mipLevels = (int) Math.abs(
                    Math.log(Math.max(this.width, this.height)) / Math.log(2.0)
            ) + 1;
            if (NBlendingMode.ALPHA_TESTING.equals(this.blendingMode)) {
                mipLevels = 1;
            }

            int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);

            if (Main.isSupported(4, 2)) {
                GL42C.glTexStorage2D(
                        GL_TEXTURE_2D,
                        mipLevels,
                        internalFormat,
                        this.width,
                        this.height
                );
                glTexSubImage2D(
                        GL_TEXTURE_2D,
                        0,
                        0, 0,
                        this.width, this.height,
                        GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        textureBuffer
                );
            } else {
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        internalFormat,
                        this.width,
                        this.height,
                        0,
                        GL_RGBA,
                        GL_UNSIGNED_BYTE,
                        textureBuffer
                );
            }

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            if (NBlendingMode.ALPHA_TESTING.equals(this.blendingMode)) {
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            } else {
                glGenerateMipmap(GL_TEXTURE_2D);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            }

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

            if (GL.getCapabilities().GL_EXT_texture_filter_anisotropic) {
                glTexParameterf(
                        GL_TEXTURE_2D,
                        EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT,
                        glGetFloat(EXTTextureFilterAnisotropic.GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT)
                );
            }

            glBindTexture(GL_TEXTURE_2D, 0);

            if (GL.getCapabilities().GL_KHR_debug) {
                KHRDebug.glObjectLabel(GL_TEXTURE, texture,
                        StringUtils.truncateStringTo255Bytes(name + "_" + textureId + "_" + this.name)
                );
            }

            return texture;
        } finally {
            MemoryUtil.memFree(textureBuffer);
        }
    }

    private void validateTextures() {
        if (this.r_g_b_a.texture != 0) {
            return;
        }

        int internalFormatSRGB = GL_SRGB8_ALPHA8;
        int internalFormatRGB = GL_RGBA8;

        if (GL.getCapabilities().GL_EXT_texture_compression_s3tc) {
            internalFormatRGB = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
            if (GL.getCapabilities().GL_EXT_texture_sRGB) {
                internalFormatSRGB = EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT;
            }
        }

        long textureId = NTextures.textureIds.getAndIncrement();
        glActiveTexture(GL_TEXTURE0);

        this.r_g_b_a.texture = loadTexture(internalFormatSRGB,
                this.redGreenBlueAlpha,
                "r_g_b_a",
                textureId
        );
        this.ht_rg_mt_nx.texture = loadTexture(internalFormatRGB,
                this.heightRoughnessMetallicNormalX,
                "ht_rg_mt_nx",
                textureId
        );
        this.er_eg_eb_ny.texture = loadTexture(internalFormatSRGB,
                this.emissiveRedGreenBlueNormalY,
                "er_eg_eb_ny",
                textureId
        );
    }

    public int r_g_b_a() {
        validateTextures();
        return this.r_g_b_a.texture;
    }

    public int ht_rg_mt_nx() {
        validateTextures();
        return this.ht_rg_mt_nx.texture;
    }

    public int er_eg_eb_ny() {
        validateTextures();
        return this.er_eg_eb_ny.texture;
    }

    public void manualFree() {
        final WrappedTexture final_r_g_b_a = this.r_g_b_a;
        final WrappedTexture final_ht_rg_mt_nx = this.ht_rg_mt_nx;
        final WrappedTexture final_er_eg_eb_ny = this.er_eg_eb_ny;

        int tex_r_g_b_a = final_r_g_b_a.texture;
        int tex_ht_rg_mt_nx = final_ht_rg_mt_nx.texture;
        int tex_er_eg_eb_ny = final_er_eg_eb_ny.texture;

        if (tex_r_g_b_a != 0) {
            glDeleteTextures(tex_r_g_b_a);
            final_r_g_b_a.texture = 0;
        }
        if (tex_ht_rg_mt_nx != 0) {
            glDeleteTextures(tex_ht_rg_mt_nx);
            final_ht_rg_mt_nx.texture = 0;
        }
        if (tex_er_eg_eb_ny != 0) {
            glDeleteTextures(tex_er_eg_eb_ny);
            final_er_eg_eb_ny.texture = 0;
        }
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 53 * hash + Objects.hashCode(this.sha256);
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
        final NTextures other = (NTextures) obj;
        return Objects.equals(this.sha256, other.sha256);
    }

}
