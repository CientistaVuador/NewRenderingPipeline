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
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class NCubemapStore {
    
    public static final String MAGIC_FILE_IDENTIFIER = "85603ba5-dfef-42ce-901c-a5feb6a9deaa";
    
    public static void writeCubemap(NCubemap cubemap, OutputStream out) throws IOException {
        ZipOutputStream zipOut = new ZipOutputStream(out, StandardCharsets.UTF_8);
        
        zipOut.putNextEntry(new ZipEntry(MAGIC_FILE_IDENTIFIER));
        zipOut.closeEntry();
        
        Properties properties = new Properties();
        
        properties.setProperty("name", cubemap.getName());
        properties.setProperty("uid", cubemap.getUID());
        properties.setProperty("colorR", Float.toString(cubemap.getCubemapColor().x()));
        properties.setProperty("colorG", Float.toString(cubemap.getCubemapColor().y()));
        properties.setProperty("colorB", Float.toString(cubemap.getCubemapColor().z()));
        
        for (int i = 0; i < cubemap.getNumberOfSides(); i++) {
            DXT5Texture side = cubemap.getSideTexture(i);
            
            zipOut.putNextEntry(new ZipEntry(i+"."+DXT5TextureStore.EXTENSION));
            DXT5TextureStore.writeDXT5Texture(side, zipOut);
            zipOut.closeEntry();
        }
        
        NCubemapInfo info = cubemap.getCubemapInfo();
        
        properties.setProperty("info.positionX", Double.toString(info.getCubemapPosition().x()));
        properties.setProperty("info.positionY", Double.toString(info.getCubemapPosition().y()));
        properties.setProperty("info.positionZ", Double.toString(info.getCubemapPosition().z()));
        
        properties.setProperty("info.parallaxCorrected", Boolean.toString(info.isParallaxCorrected()));
        
        properties.setProperty("info.parallaxPositionX", Double.toString(info.getParallaxPosition().x()));
        properties.setProperty("info.parallaxPositionY", Double.toString(info.getParallaxPosition().y()));
        properties.setProperty("info.parallaxPositionZ", Double.toString(info.getParallaxPosition().z()));
        
        properties.setProperty("info.parallaxRotationX", Float.toString(info.getParallaxRotation().x()));
        properties.setProperty("info.parallaxRotationY", Float.toString(info.getParallaxRotation().y()));
        properties.setProperty("info.parallaxRotationZ", Float.toString(info.getParallaxRotation().z()));
        properties.setProperty("info.parallaxRotationW", Float.toString(info.getParallaxRotation().w()));
        
        properties.setProperty("info.parallaxHalfExtentsX", Float.toString(info.getParallaxHalfExtents().x()));
        properties.setProperty("info.parallaxHalfExtentsY", Float.toString(info.getParallaxHalfExtents().y()));
        properties.setProperty("info.parallaxHalfExtentsZ", Float.toString(info.getParallaxHalfExtents().z()));
        
        zipOut.putNextEntry(new ZipEntry("cubemap.xml"));
        properties.storeToXML(zipOut, null, StandardCharsets.UTF_8);
        zipOut.closeEntry();
        
        zipOut.finish();
    }
    
    public static NCubemap readCubemap(String jarFile) throws IOException {
        try (InputStream stream = ClassLoader.getSystemResourceAsStream(jarFile)) {
            return readCubemap(stream);
        }
    }
    
    public static NCubemap readCubemap(InputStream in) throws IOException {
        ZipInputStream zipIn = new ZipInputStream(in, StandardCharsets.UTF_8);
        
        Map<String, byte[]> fs = new HashMap<>();
        ZipEntry entry;
        while ((entry = zipIn.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            fs.put(entry.getName(), zipIn.readAllBytes());
        }
        
        if (fs.get(MAGIC_FILE_IDENTIFIER) == null) {
            throw new IOException("Not a valid cubemap file!");
        }
        
        Properties properties = new Properties();
        properties.loadFromXML(new ByteArrayInputStream(fs.get("cubemap.xml")));
        
        NCubemapInfo info = new NCubemapInfo(
                new Vector3d(
                        Double.parseDouble(properties.getProperty("info.positionX")),
                        Double.parseDouble(properties.getProperty("info.positionY")),
                        Double.parseDouble(properties.getProperty("info.positionZ"))
                ),
                Boolean.parseBoolean(properties.getProperty("info.parallaxCorrected")),
                new Vector3d(
                        Double.parseDouble(properties.getProperty("info.parallaxPositionX")),
                        Double.parseDouble(properties.getProperty("info.parallaxPositionY")),
                        Double.parseDouble(properties.getProperty("info.parallaxPositionZ"))
                ),
                new Quaternionf(
                        Float.parseFloat(properties.getProperty("info.parallaxRotationX")),
                        Float.parseFloat(properties.getProperty("info.parallaxRotationY")),
                        Float.parseFloat(properties.getProperty("info.parallaxRotationZ")),
                        Float.parseFloat(properties.getProperty("info.parallaxRotationW"))
                ),
                new Vector3f(
                        Float.parseFloat(properties.getProperty("info.parallaxHalfExtentsX")),
                        Float.parseFloat(properties.getProperty("info.parallaxHalfExtentsY")),
                        Float.parseFloat(properties.getProperty("info.parallaxHalfExtentsZ"))
                )
        );
        
        DXT5Texture[] cubemapSides = new DXT5Texture[NCubemap.SIDES];
        for (int i = 0; i < cubemapSides.length; i++) {
            cubemapSides[i] = DXT5TextureStore.readDXT5Texture(new ByteArrayInputStream(fs.get(i+"."+DXT5TextureStore.EXTENSION)));
        }
        
        return new NCubemap(
                properties.getProperty("name"),
                properties.getProperty("uid"),
                Float.parseFloat(properties.getProperty("colorR")),
                Float.parseFloat(properties.getProperty("colorG")),
                Float.parseFloat(properties.getProperty("colorB")),
                info, cubemapSides
        );
    }
    
    private NCubemapStore() {
        
    }
}
