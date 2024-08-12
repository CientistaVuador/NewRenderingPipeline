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

import cientistavuador.newrenderingpipeline.util.DXT5TextureStore;
import cientistavuador.newrenderingpipeline.util.DXT5TextureStore.DXT5Texture;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author Cien
 */
public class NTexturesStore {
    
    public static final String MAGIC_FILE_IDENTIFIER = "b615aed4-f79b-405b-92e8-9c8ab83a177a";
    
    private static void writeProperties(ZipOutputStream out, NTextures textures) throws IOException {
        ZipEntry entry = new ZipEntry("properties.xml");
        out.putNextEntry(entry);
        
        Properties properties = new Properties();
        
        properties.setProperty("name", textures.getName());
        properties.setProperty("blendingMode", textures.getBlendingMode().name());
        properties.setProperty("heightMapSupported", Boolean.toString(textures.isHeightMapSupported()));
        properties.setProperty("uid", textures.getUID());
        
        properties.storeToXML(out, null, StandardCharsets.UTF_8);
        
        out.closeEntry();
    }
    
    private static void writeTexture(String name, ZipOutputStream out, DXT5Texture texture) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        out.putNextEntry(entry);
        
        DXT5TextureStore.writeDXT5Texture(texture, out);
        
        out.closeEntry();
    }
    
    public static void writeTextures(NTextures textures, OutputStream output) throws IOException {
        ZipOutputStream out = new ZipOutputStream(output, StandardCharsets.UTF_8);
        
        out.putNextEntry(new ZipEntry(MAGIC_FILE_IDENTIFIER));
        out.closeEntry();
        
        writeProperties(out, textures);
        
        writeTexture("r_g_b_a."+DXT5TextureStore.EXTENSION, out, textures.texture_r_g_b_a());
        writeTexture("ht_rg_mt_nx."+DXT5TextureStore.EXTENSION, out, textures.texture_ht_rg_mt_nx());
        writeTexture("er_eg_eb_ny."+DXT5TextureStore.EXTENSION, out, textures.texture_er_eg_eb_ny());
        
        out.finish();
    }
    
    private static Map<String, byte[]> readFiles(ZipInputStream in) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        
        ZipEntry entry;
        while ((entry = in.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            files.put(entry.getName(), in.readAllBytes());
        }
        
        return files;
    }
    
    private static class TextureProperties {
        String name = "unnamed";
        NBlendingMode blendingMode = null;
        boolean heightMapSupported = false;
        String uid = null;
    }
    
    private static TextureProperties readProperties(Map<String, byte[]> files) throws IOException {
        TextureProperties textureProperties = new TextureProperties();
        byte[] propertiesFile = files.get("properties.xml");
        
        if (propertiesFile == null) {
            return textureProperties;
        }
        
        Properties properties = new Properties();
        properties.loadFromXML(new ByteArrayInputStream(propertiesFile));
        
        textureProperties.name = properties.getProperty("name");
        
        String blendingModeString = properties.getProperty("blendingMode");
        if (blendingModeString != null) {
            textureProperties.blendingMode = NBlendingMode.valueOf(blendingModeString);
        }
        
        String heightMapSupportedString = properties.getProperty("heightMapSupported");
        if (heightMapSupportedString != null) {
            textureProperties.heightMapSupported = Boolean.parseBoolean(heightMapSupportedString);
        }
        
        textureProperties.uid = properties.getProperty("uid");
        
        return textureProperties;
    }
    
    private static DXT5Texture readTexture(String image, Map<String, byte[]> files) throws IOException {
        byte[] textureData = files.get(image);
        return DXT5TextureStore.readDXT5Texture(new ByteArrayInputStream(textureData));
    }
    
    public static NTextures readTextures(InputStream input) throws IOException {
        ZipInputStream in = new ZipInputStream(input, StandardCharsets.UTF_8);
        
        Map<String, byte[]> files = readFiles(in);
        
        if (files.get(MAGIC_FILE_IDENTIFIER) == null) {
            throw new IllegalArgumentException("Invalid textures file!");
        }
        
        TextureProperties properties = readProperties(files);
        
        DXT5Texture rgba = readTexture("r_g_b_a."+DXT5TextureStore.EXTENSION, files);
        DXT5Texture hrmnx = readTexture("ht_rg_mt_nx."+DXT5TextureStore.EXTENSION, files);
        DXT5Texture eregebny = readTexture("er_eg_eb_ny."+DXT5TextureStore.EXTENSION, files);
        
        return new NTextures(
                properties.name,
                properties.uid,
                properties.blendingMode,
                properties.heightMapSupported,
                rgba, hrmnx, eregebny
        );
    }
    
    private NTexturesStore() {
        
    }
    
}
