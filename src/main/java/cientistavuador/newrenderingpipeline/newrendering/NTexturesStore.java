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

import java.awt.image.BufferedImage;
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
import javax.imageio.ImageIO;

/**
 *
 * @author Cien
 */
public class NTexturesStore {
    
    private static void writeProperties(ZipOutputStream out, NTextures textures) throws IOException {
        ZipEntry entry = new ZipEntry("properties.xml");
        out.putNextEntry(entry);
        
        Properties properties = new Properties();
        
        properties.setProperty("name", textures.getName());
        properties.setProperty("width", Integer.toString(textures.getWidth()));
        properties.setProperty("height", Integer.toString(textures.getHeight()));
        properties.setProperty("blendingMode", textures.getBlendingMode().name());
        properties.setProperty("heightMapSupported", Boolean.toString(textures.isHeightMapSupported()));
        properties.setProperty("sha256", textures.getSha256());
        
        properties.storeToXML(out, null, StandardCharsets.US_ASCII);
        
        out.closeEntry();
    }
    
    private static void writeImage(String name, ZipOutputStream out, byte[] data, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (x * 4) + (((height - 1) - y) * width * 4);
                
                int r = data[index + 0] & 0xFF;
                int g = data[index + 1] & 0xFF;
                int b = data[index + 2] & 0xFF;
                int a = data[index + 3] & 0xFF;
                
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | (b << 0));
            }
        }
        
        ZipEntry entry = new ZipEntry(name);
        out.putNextEntry(entry);
        
        ImageIO.write(image, "PNG", out);
        
        out.closeEntry();
    }
    
    public static void writeTextures(NTextures textures, OutputStream output) throws IOException {
        ZipOutputStream out = new ZipOutputStream(output, StandardCharsets.UTF_8);
        
        int w = textures.getWidth();
        int h = textures.getHeight();
        
        writeProperties(out, textures);
        writeImage("r_g_b_a.png", out, textures.getRedGreenBlueAlpha(), w, h);
        writeImage("ht_rg_mt_nx.png", out, textures.getHeightRoughnessMetallicNormalX(), w, h);
        writeImage("er_eg_eb_ny.png", out, textures.getEmissiveRedGreenBlueNormalY(), w, h);
        
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
        int width = 0;
        int height = 0;
        NBlendingMode blendingMode = NBlendingMode.OPAQUE;
        boolean heightMapSupported = false;
        String sha256 = "empty";
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
        textureProperties.width = Integer.parseInt(properties.getProperty("width"));
        textureProperties.height = Integer.parseInt(properties.getProperty("height"));
        textureProperties.blendingMode = NBlendingMode.valueOf(properties.getProperty("blendingMode"));
        textureProperties.heightMapSupported = Boolean.parseBoolean(properties.getProperty("heightMapSupported"));
        textureProperties.sha256 = properties.getProperty("sha256");
        
        return textureProperties;
    }
    
    private static byte[] readImage(String image, Map<String, byte[]> files) {
        byte[] imageData = files.get(image);
        return NTexturesIO.loadImage(imageData).pixelData;
    }
    
    public static NTextures readTextures(InputStream input) throws IOException {
        ZipInputStream in = new ZipInputStream(input, StandardCharsets.UTF_8);
        
        Map<String, byte[]> files = readFiles(in);
        
        TextureProperties properties = readProperties(files);
        byte[] rgba = readImage("r_g_b_a.png", files);
        byte[] hrmnx = readImage("ht_rg_mt_nx.png", files);
        byte[] eregebny = readImage("er_eg_eb_ny.png", files);
        
        return new NTextures(
                properties.name,
                properties.width, properties.height,
                rgba, hrmnx, eregebny,
                properties.blendingMode,
                properties.heightMapSupported,
                properties.sha256
        );
    }
    
    private NTexturesStore() {
        
    }
    
}
