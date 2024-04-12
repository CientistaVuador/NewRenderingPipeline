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
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.EXTTextureSRGB;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.MemoryUtil;

/**
 *
 * @author Cien
 */
public class NTextures {

    private static final AtomicLong textureIds = new AtomicLong();
    
    private static class WrappedTexture {

        public int texture = 0;
    }

    private final String name;
    private final int width;
    private final int height;
    private final byte[] redGreenBlueAlphaOrHeight;
    private final byte[] exponentNormalXreflectivenessNormalY;
    private final NBlendingMode blendingMode;
    private final String sha256;

    private final WrappedTexture r_g_b_a_or_h = new WrappedTexture();
    private final WrappedTexture e_nx_r_ny = new WrappedTexture();

    public NTextures(String name, int width, int height, byte[] redGreenBlueAlphaOrHeight, byte[] exponentNormalXreflectivenessNormalY, NBlendingMode blendingMode) {
        if (width < 0) {
            throw new IllegalArgumentException("width is negative");
        }
        if (height < 0) {
            throw new IllegalArgumentException("height is negative");
        }

        Objects.requireNonNull(redGreenBlueAlphaOrHeight, "redGreenBlueAlphaOrHeight is null");
        Objects.requireNonNull(exponentNormalXreflectivenessNormalY, "exponentNormalXreflectivenessNormalY is null");
        Objects.requireNonNull(blendingMode, "blendingMode is null");

        int pixels = width * height * 4;

        if (redGreenBlueAlphaOrHeight.length != pixels) {
            throw new IllegalArgumentException("redGreenBlueAlphaOrHeight has a invalid amount of bytes, found: " + redGreenBlueAlphaOrHeight.length + ", required: " + pixels);
        }

        if (exponentNormalXreflectivenessNormalY.length != pixels) {
            throw new IllegalArgumentException("exponentNormalXreflectivenessNormalY has a invalid amount of bytes, found: " + exponentNormalXreflectivenessNormalY.length + ", required: " + pixels);
        }
        
        this.width = width;
        this.height = height;
        this.redGreenBlueAlphaOrHeight = redGreenBlueAlphaOrHeight;
        this.exponentNormalXreflectivenessNormalY = exponentNormalXreflectivenessNormalY;
        this.blendingMode = blendingMode;

        registerForCleaning();

        String hash;
        {
            byte[] data = new byte[redGreenBlueAlphaOrHeight.length + exponentNormalXreflectivenessNormalY.length];

            System.arraycopy(redGreenBlueAlphaOrHeight, 0, data, 0, redGreenBlueAlphaOrHeight.length);
            System.arraycopy(exponentNormalXreflectivenessNormalY, 0, data, redGreenBlueAlphaOrHeight.length, exponentNormalXreflectivenessNormalY.length);

            byte[] sha256Bytes;
            try {
                sha256Bytes = MessageDigest.getInstance("SHA256").digest(data);
            } catch (NoSuchAlgorithmException ex) {
                throw new RuntimeException(ex);
            }

            StringBuilder b = new StringBuilder();
            for (int i = 0; i < sha256Bytes.length; i++) {
                String hex = Integer.toHexString(sha256Bytes[i] & 0xFF);
                if (hex.length() <= 1) {
                    b.append('0');
                }
                b.append(hex);
            }

            hash = b.toString();
        }
        this.sha256 = hash;
        
        if (name == null) {
            this.name = this.sha256;
        } else {
            this.name = name;
        }
    }

    private void registerForCleaning() {
        final WrappedTexture final_r_g_b_a_or_h = this.r_g_b_a_or_h;
        final WrappedTexture final_e_nx_r_ny = this.e_nx_r_ny;

        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                int tex_r_g_b_a_or_h = final_r_g_b_a_or_h.texture;
                int tex_e_nx_r_ny = final_e_nx_r_ny.texture;

                if (tex_r_g_b_a_or_h != 0) {
                    glDeleteTextures(tex_r_g_b_a_or_h);
                    final_r_g_b_a_or_h.texture = 0;
                }
                if (tex_e_nx_r_ny != 0) {
                    glDeleteTextures(tex_e_nx_r_ny);
                    final_e_nx_r_ny.texture = 0;
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
    
    public byte[] getRedGreenBlueAlphaOrHeight() {
        return redGreenBlueAlphaOrHeight;
    }
    
    public byte[] getExponentNormalXreflectivenessNormalY() {
        return exponentNormalXreflectivenessNormalY;
    }

    public NBlendingMode getBlendingMode() {
        return blendingMode;
    }

    public String getSha256() {
        return sha256;
    }

    private void validateTextures() {
        if (this.r_g_b_a_or_h.texture != 0) {
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

        long texId = NTextures.textureIds.getAndIncrement();
        
        glActiveTexture(GL_TEXTURE0);

        ByteBuffer rgbahData = MemoryUtil
                .memAlloc(this.redGreenBlueAlphaOrHeight.length)
                .put(this.redGreenBlueAlphaOrHeight)
                .flip();
        try {
            int rgbah = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, rgbah);
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormatSRGB, this.width, this.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgbahData);

            glGenerateMipmap(GL_TEXTURE_2D);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

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
            
            this.r_g_b_a_or_h.texture = rgbah;
            
            if (GL.getCapabilities().GL_KHR_debug) {
                KHRDebug.glObjectLabel(GL_TEXTURE, rgbah, "r_g_b_a_or_h_"+texId+"_"+this.name);
            }
        } finally {
            MemoryUtil.memFree(rgbahData);
        }
        
        ByteBuffer exryData = MemoryUtil
                .memAlloc(this.exponentNormalXreflectivenessNormalY.length)
                .put(this.exponentNormalXreflectivenessNormalY)
                .flip();
        try {
            int exry = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, exry);
            glTexImage2D(GL_TEXTURE_2D, 0, internalFormatRGB, this.width, this.height, 0, GL_RGBA, GL_UNSIGNED_BYTE, exryData);
            
            glGenerateMipmap(GL_TEXTURE_2D);

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);

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
            
            this.e_nx_r_ny.texture = exry;
            
            if (GL.getCapabilities().GL_KHR_debug) {
                KHRDebug.glObjectLabel(GL_TEXTURE, exry, "e_x_r_y_"+texId+"_"+this.name);
            }
        } finally {
            MemoryUtil.memFree(exryData);
        }
    }

    public int r_g_b_a_or_h() {
        validateTextures();
        return this.r_g_b_a_or_h.texture;
    }

    public int e_nx_r_ny() {
        validateTextures();
        return this.e_nx_r_ny.texture;
    }

    public void manualFree() {
        final WrappedTexture final_r_g_b_a_or_h = this.r_g_b_a_or_h;
        final WrappedTexture final_e_nx_r_ny = this.e_nx_r_ny;

        int tex_r_g_b_a_or_h = final_r_g_b_a_or_h.texture;
        int tex_e_nx_r_ny = final_e_nx_r_ny.texture;

        if (tex_r_g_b_a_or_h != 0) {
            glDeleteTextures(tex_r_g_b_a_or_h);
            final_r_g_b_a_or_h.texture = 0;
        }
        if (tex_e_nx_r_ny != 0) {
            glDeleteTextures(tex_e_nx_r_ny);
            final_e_nx_r_ny.texture = 0;
        }
    }

}
