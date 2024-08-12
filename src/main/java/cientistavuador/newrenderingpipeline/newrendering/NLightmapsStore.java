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
import cientistavuador.newrenderingpipeline.util.E8Image;
import cientistavuador.newrenderingpipeline.util.RGBA8Image;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapAmbientCubeBVH;
import cientistavuador.newrenderingpipeline.util.bakedlighting.LightmapAmbientCubeBVHStore;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class NLightmapsStore {

    public static final String MAGIC_FILE_IDENTIFIER = "9e6b2847-9f06-4103-9935-e7a376a3f821";
    private static final String INDENT = "    ";
    
    private static class StoreLightmap {
        int index;
        String name;
        String lightmapFile;
        String cpuLightmapFile;

        public String toString(String indent) {
            StringBuilder b = new StringBuilder();
            
            b.append(indent).append("<lightmap").append('\n');
            b.append(indent).append(INDENT).append("index=").append('"').append(this.index).append('"').append('\n');
            b.append(indent).append(INDENT).append("name=").append('"').append(URLEncoder.encode(this.name, StandardCharsets.UTF_8)).append('"').append('\n');
            b.append(indent).append(INDENT).append("lightmapFile=").append('"').append(this.lightmapFile).append('"').append('\n');
            b.append(indent).append(INDENT).append("cpuLightmapFile=").append('"').append(this.cpuLightmapFile).append('"').append('\n');
            b.append(indent).append("/>");
            return b.toString();
        }
    }
    
    private static class StoreLightmaps {
        String name;
        String uid;
        String cpuColorFile;
        String ambientCubesFile;
        
        final List<StoreLightmap> lightmaps = new ArrayList<>();

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder();
            b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append('\n');
            b.append("<lightmaps").append('\n');
            b.append(INDENT).append("name=").append('"').append(URLEncoder.encode(this.name, StandardCharsets.UTF_8)).append('"').append('\n');
            b.append(INDENT).append("uid=").append('"').append(URLEncoder.encode(this.uid, StandardCharsets.UTF_8)).append('"').append('\n');
            b.append(INDENT).append("cpuColorFile=").append('"').append(this.cpuColorFile).append('"').append('\n');
            b.append(INDENT).append("ambientCubesFile=").append('"').append(this.ambientCubesFile).append('"').append('\n');
            b.append(">").append('\n');
            for (StoreLightmap e:lightmaps) {
                b.append(e.toString(INDENT)).append('\n');
            }
            b.append("</lightmaps>");
            return b.toString();
        }
    }
    
    private static String createFileName(AtomicInteger counter, String name, String extension) {
        String prefix = "f"+counter.getAndIncrement()+"_";
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String suffix = "." + extension;
        
        return prefix + encodedName + suffix;
    }
    
    public static void writeLightmaps(NLightmaps lightmaps, OutputStream output) throws IOException {
        ZipOutputStream out = new ZipOutputStream(output, StandardCharsets.UTF_8);
        
        out.putNextEntry(new ZipEntry(MAGIC_FILE_IDENTIFIER));
        out.closeEntry();
        
        AtomicInteger fileCounter = new AtomicInteger();
        
        StoreLightmaps storeLightmaps = new StoreLightmaps();
        
        storeLightmaps.name = lightmaps.getName();
        storeLightmaps.uid = lightmaps.getUID();
        
        storeLightmaps.cpuColorFile = createFileName(fileCounter, "color", "png");
        storeLightmaps.ambientCubesFile = createFileName(fileCounter, "ambientCubes", "acb");
        
        out.putNextEntry(new ZipEntry(storeLightmaps.cpuColorFile));
        out.write(lightmaps.getCPUColor().toPNG());
        out.closeEntry();
        
        out.putNextEntry(new ZipEntry(storeLightmaps.ambientCubesFile));
        LightmapAmbientCubeBVHStore.writeBVH(lightmaps.getAmbientCubes(), out);
        out.closeEntry();
        
        for (int i = 0; i < lightmaps.getNumberOfLightmaps(); i++) {
            StoreLightmap storeLightmap = new StoreLightmap();
            
            storeLightmap.index = i;
            storeLightmap.name = lightmaps.getLightmapName(i);
            
            storeLightmap.lightmapFile = createFileName(fileCounter, i+"_"+storeLightmap.name, DXT5TextureStore.EXTENSION);
            storeLightmap.cpuLightmapFile = createFileName(fileCounter, i+"_cpu_"+storeLightmap.name, "png");
            
            out.putNextEntry(new ZipEntry(storeLightmap.lightmapFile));
            DXT5TextureStore.writeDXT5Texture(lightmaps.getLightmap(i), out);
            out.closeEntry();
            
            out.putNextEntry(new ZipEntry(storeLightmap.cpuLightmapFile));
            E8Image cpuLightmap = lightmaps.getCPULightmap(i);
            out.write(RGBA8Image.toPNG(cpuLightmap.getRGBE(), cpuLightmap.getWidth(), cpuLightmap.getHeight()));
            out.closeEntry();
            
            storeLightmaps.lightmaps.add(storeLightmap);
        }
        
        out.putNextEntry(new ZipEntry("lightmaps.xml"));
        out.write(storeLightmaps.toString().getBytes(StandardCharsets.UTF_8));
        out.closeEntry();
        
        out.flush();
        out.finish();
    }
    
    public static NLightmaps readLightmaps(InputStream input) throws IOException {
        Map<String, byte[]> fs = new HashMap<>();
        ZipInputStream zipIn = new ZipInputStream(input, StandardCharsets.UTF_8);
        ZipEntry entry;
        while ((entry = zipIn.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                continue;
            }
            fs.put(entry.getName(), zipIn.readAllBytes());
        }
        
        if (!fs.containsKey(MAGIC_FILE_IDENTIFIER)) {
            throw new IOException("Invalid lightmaps file!");
        }
        
        Document lightmapsXml;
        
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            lightmapsXml = builder.parse(new ByteArrayInputStream(fs.get("lightmaps.xml")));
        } catch (ParserConfigurationException | SAXException ex) {
            throw new IOException(ex);
        }
        
        Element rootNode = lightmapsXml.getDocumentElement();
        rootNode.normalize();
        
        StoreLightmaps storeLightmaps = new StoreLightmaps();
        
        storeLightmaps.name = URLDecoder.decode(rootNode.getAttribute("name"), StandardCharsets.UTF_8);
        storeLightmaps.uid = URLDecoder.decode(rootNode.getAttribute("uid"), StandardCharsets.UTF_8);
        storeLightmaps.cpuColorFile = rootNode.getAttribute("cpuColorFile");
        storeLightmaps.ambientCubesFile = rootNode.getAttribute("ambientCubesFile");
        
        NodeList list = rootNode.getElementsByTagName("lightmap");
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            if (e.getParentNode() != rootNode) {
                continue;
            }
            StoreLightmap storeLightmap = new StoreLightmap();
            
            storeLightmap.index = Integer.parseInt(e.getAttribute("index"));
            storeLightmap.name = URLDecoder.decode(e.getAttribute("name"), StandardCharsets.UTF_8);
            storeLightmap.lightmapFile = e.getAttribute("lightmapFile");
            storeLightmap.cpuLightmapFile = e.getAttribute("cpuLightmapFile");
            
            storeLightmaps.lightmaps.add(storeLightmap);
        }
        
        String name = storeLightmaps.name;
        String uid = storeLightmaps.uid;
        String[] lightmapNames = new String[storeLightmaps.lightmaps.size()];
        DXT5Texture[] lightmaps = new DXT5Texture[lightmapNames.length];
        E8Image[] cpuLightmaps = new E8Image[lightmapNames.length];
        RGBA8Image cpuColor;
        LightmapAmbientCubeBVH ambientCubes;
        
        for (StoreLightmap lightmap:storeLightmaps.lightmaps) {
            int index = lightmap.index;
            
            lightmapNames[index] = lightmap.name;
            lightmaps[index] = DXT5TextureStore.readDXT5Texture(fs.get(lightmap.lightmapFile));
            cpuLightmaps[index] = new E8Image(RGBA8Image.fromPNG(fs.get(lightmap.cpuLightmapFile)));
        }
        
        cpuColor = RGBA8Image.fromPNG(fs.get(storeLightmaps.cpuColorFile));
        ambientCubes = LightmapAmbientCubeBVHStore.readBVH(fs.get(storeLightmaps.ambientCubesFile));
        
        return new NLightmaps(
                name,
                uid,
                lightmapNames,
                lightmaps,
                cpuLightmaps,
                cpuColor,
                ambientCubes
        );
    }
    
    public static NLightmaps readLightmaps(String jarFile) throws IOException {
        try (InputStream stream = ClassLoader.getSystemResourceAsStream(jarFile)) {
            return readLightmaps(stream);
        }
    }
    
    private NLightmapsStore() {

    }

}
