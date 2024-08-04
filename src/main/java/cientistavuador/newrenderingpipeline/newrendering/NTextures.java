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
import cientistavuador.newrenderingpipeline.util.StringUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.EXTTextureCompressionS3TC;
import org.lwjgl.opengl.EXTTextureFilterAnisotropic;
import org.lwjgl.opengl.EXTTextureSRGB;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.KHRDebug;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class NTextures {

    private static final AtomicLong textureIds = new AtomicLong();

    public static final String ERROR_R_G_B_A_DATA = "KLUv/aDwVQUA9AIA0oUPGMCnNd7//+r/0890pNcol2T9xZau2d22Un93FxVR8bd2d/d/axFieLBnPd0YzJij/JI4TfXTwIoQDhnMWgEIAIXnA3BykD/4qf358w9Q/lD/AwgJJM4VB+zWAUwAAAj/AQD8/zkQAiUBAJAAAAUFUFARRBFEF7gXuKqqqqoEAF0Fel7/AU/y/zP1OqAuEA==";
    public static final String ERROR_HT_RG_MT_NX_DATA = "KLUv/aDwVQUAVAIAksQNFdBdA4AKeMBEAKKkVBxtUTmLlUtUCrXWeu21/0/pyZDRk+oX/zn8e5H9txXh5X/x4IsZ/zxOqQUA2v5n5gsgJJA4VxywWwdEAAAAAQD9/5NPIEUAAAABAO1VOQAC";
    public static final String ERROR_ER_EG_EB_NY_DATA = "KLUv/aDwVQUALAIAkoQMFMBrDv/D/I9YrqzKzPdv1ZBak0Skd3f/N80iDQm2rJ+rwYy14xGjKdE/A6MknFHJWgEFANr+Z+YLICSQOFccsFsHRAAAAAEA/f+TTyBFAAAAAQDtVTkAAg==";
    
    public static final NTextures NULL_TEXTURE;
    
    static {
        try {
            DXT5Texture r_g_b_a = DXT5TextureStore.readDXT5Texture(new ByteArrayInputStream(Base64.getDecoder().decode(ERROR_R_G_B_A_DATA)));
            DXT5Texture ht_rg_mt_nx = DXT5TextureStore.readDXT5Texture(new ByteArrayInputStream(Base64.getDecoder().decode(ERROR_HT_RG_MT_NX_DATA)));
            DXT5Texture er_eg_eb_ny = DXT5TextureStore.readDXT5Texture(new ByteArrayInputStream(Base64.getDecoder().decode(ERROR_ER_EG_EB_NY_DATA)));
            
            NULL_TEXTURE = new NTextures(
                    "Error/Null/Empty Texture",
                    "Error/Null/Empty Texture",
                    NBlendingMode.OPAQUE,
                    false,
                    r_g_b_a, ht_rg_mt_nx, er_eg_eb_ny
            );
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static class WrappedTexture {

        public int texture = 0;
    }

    private final String name;
    private final String uid;
    private final NBlendingMode blendingMode;
    private final boolean heightMapSupported;
    private final int width;
    private final int height;
    private final DXT5Texture texture_r_g_b_a;
    private final DXT5Texture texture_ht_rg_mt_nx;
    private final DXT5Texture texture_er_eg_eb_ny;

    private final WrappedTexture r_g_b_a = new WrappedTexture();
    private final WrappedTexture ht_rg_mt_nx = new WrappedTexture();
    private final WrappedTexture er_eg_eb_ny = new WrappedTexture();

    public NTextures(
            String name,
            String uid,
            NBlendingMode blendingMode,
            boolean heightMapSupported,
            DXT5Texture texture_r_g_b_a,
            DXT5Texture texture_ht_rg_mt_nx,
            DXT5Texture texture_er_eg_eb_ny
    ) {
        Objects.requireNonNull(texture_r_g_b_a, "texture_r_g_b_a is null.");
        Objects.requireNonNull(texture_ht_rg_mt_nx, "texture_ht_rg_mt_nx is null.");
        Objects.requireNonNull(texture_er_eg_eb_ny, "texture_er_eg_eb_ny is null.");

        this.width = texture_r_g_b_a.width();
        this.height = texture_r_g_b_a.height();

        if (texture_ht_rg_mt_nx.width() != this.width || texture_ht_rg_mt_nx.height() != this.height) {
            throw new IllegalArgumentException("Textures sizes are different!");
        }

        if (texture_er_eg_eb_ny.width() != this.width || texture_er_eg_eb_ny.height() != this.height) {
            throw new IllegalArgumentException("Textures sizes are different!");
        }

        this.texture_r_g_b_a = texture_r_g_b_a;
        this.texture_ht_rg_mt_nx = texture_ht_rg_mt_nx;
        this.texture_er_eg_eb_ny = texture_er_eg_eb_ny;

        if (name == null) {
            name = "Unnamed";
        }
        this.name = name;

        if (uid == null) {
            uid = UUID.randomUUID().toString();
        }
        this.uid = uid;

        if (blendingMode == null) {
            blendingMode = NBlendingMode.OPAQUE;
        }
        this.blendingMode = blendingMode;
        
        this.heightMapSupported = heightMapSupported;
        
        registerForCleaning();
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

    public String getUid() {
        return uid;
    }

    public NBlendingMode getBlendingMode() {
        return blendingMode;
    }

    public boolean isHeightMapSupported() {
        return heightMapSupported;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public DXT5Texture texture_r_g_b_a() {
        return texture_r_g_b_a;
    }

    public DXT5Texture texture_ht_rg_mt_nx() {
        return texture_ht_rg_mt_nx;
    }

    public DXT5Texture texture_er_eg_eb_ny() {
        return texture_er_eg_eb_ny;
    }

    public byte[] data_r_g_b_a() {
        return texture_r_g_b_a().decompress();
    }

    public byte[] data_ht_rg_mt_nx() {
        return texture_ht_rg_mt_nx().decompress();
    }

    public byte[] data_er_eg_eb_ny() {
        return texture_er_eg_eb_ny().decompress();
    }

    private int loadTexture(DXT5Texture textureData, boolean srgb, String name, long textureId) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);

        int internalFormat;
        boolean compressed = false;

        if (srgb) {
            internalFormat = GL_SRGB8_ALPHA8;
            if (GL.getCapabilities().GL_EXT_texture_compression_s3tc
                    && GL.getCapabilities().GL_EXT_texture_sRGB) {
                internalFormat = EXTTextureSRGB.GL_COMPRESSED_SRGB_ALPHA_S3TC_DXT5_EXT;
                compressed = true;
            }
        } else {
            internalFormat = GL_RGBA8;
            if (GL.getCapabilities().GL_EXT_texture_compression_s3tc) {
                internalFormat = EXTTextureCompressionS3TC.GL_COMPRESSED_RGBA_S3TC_DXT5_EXT;
                compressed = true;
            }
        }

        if (compressed) {
            for (int i = 0; i < textureData.mips(); i++) {
                glCompressedTexImage2D(
                        GL_TEXTURE_2D,
                        i,
                        internalFormat, textureData.mipWidth(i), textureData.mipHeight(i),
                        0,
                        textureData.mipSlice(i)
                );
            }
        } else {
            byte[] uncompressedTextureData = textureData.decompress();
            ByteBuffer uncompressed = MemoryUtil
                    .memAlloc(uncompressedTextureData.length)
                    .put(uncompressedTextureData)
                    .flip();
            try {
                glTexImage2D(
                        GL_TEXTURE_2D,
                        0,
                        internalFormat, textureData.width(), textureData.height(),
                        0,
                        GL_RGBA, GL_UNSIGNED_BYTE, uncompressed
                );
                glGenerateMipmap(GL_TEXTURE_2D);
            } finally {
                MemoryUtil.memFree(uncompressed);
            }
        }

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

        if (GL.getCapabilities().GL_KHR_debug) {
            KHRDebug.glObjectLabel(GL_TEXTURE, texture,
                    StringUtils.truncateStringTo255Bytes(name + "_" + textureId + "_" + this.name)
            );
        }

        return texture;
    }
    
    private void validateTextures() {
        if (this.r_g_b_a.texture != 0) {
            return;
        }
        
        long textureId = NTextures.textureIds.getAndIncrement();
        glActiveTexture(GL_TEXTURE0);

        this.r_g_b_a.texture = loadTexture(
                this.texture_r_g_b_a,
                true,
                "r_g_b_a",
                textureId
        );
        this.ht_rg_mt_nx.texture = loadTexture(
                this.texture_ht_rg_mt_nx,
                false,
                "ht_rg_mt_nx",
                textureId
        );
        this.er_eg_eb_ny.texture = loadTexture(
                this.texture_er_eg_eb_ny,
                false,
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
        return Objects.equals(this.uid, other.uid);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + Objects.hashCode(this.uid);
        return hash;
    }

}
