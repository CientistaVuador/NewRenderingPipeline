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

import cientistavuador.newrenderingpipeline.util.MultiPNG;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static org.lwjgl.stb.STBImage.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryUtil.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Cien
 */
public class NLightmapsStore {

    public static final String MAGIC_FILE_IDENTIFIER = "57a0974d-75e9-4965-868d-998740fb5660";
    private static final String INDENT = "    ";

    private static class StoreLightmap {

        int index;
        String name;
        Vector3f indirectIntensity;
        float intensity;

        String toXML() {
            StringBuilder b = new StringBuilder();
            b.append(INDENT).append("<lightmap").append('\n');
            b.append(INDENT).append(INDENT).append("index=").append('"').append(this.index).append('"').append('\n');
            b.append(INDENT).append(INDENT).append("name=").append('"').append(URLEncoder.encode(this.name, StandardCharsets.UTF_8)).append('"').append('\n');
            b.append(INDENT).append(INDENT).append("indirectIntensityR=").append('"').append(this.indirectIntensity.x()).append('"').append(' ');
            b.append("indirectIntensityG=").append('"').append(this.indirectIntensity.y()).append('"').append(' ');
            b.append("indirectIntensityB=").append('"').append(this.indirectIntensity.z()).append('"').append('\n');
            b.append(INDENT).append(INDENT).append("intensity=").append('"').append(this.intensity).append('"').append('\n');
            b.append(INDENT).append("/>");
            return b.toString();
        }
    }

    private static class StoreLightmaps {

        String name;
        int margin;

        String colorFile;
        String indirectFile;
        String lightmapFile;

        String sha256;

        List<StoreLightmap> lightmaps;

        String toXML() {
            StringBuilder b = new StringBuilder();
            b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>").append('\n');
            b.append("<lightmaps").append('\n');
            b.append(INDENT).append("name=").append('"').append(URLEncoder.encode(this.name, StandardCharsets.UTF_8)).append('"').append('\n');
            b.append(INDENT).append("margin=").append('"').append(this.margin).append('"').append('\n');
            b.append(INDENT).append('\n');
            b.append(INDENT).append("colorFile=").append('"').append(this.colorFile).append('"').append('\n');
            b.append(INDENT).append("indirectFile=").append('"').append(this.indirectFile).append('"').append('\n');
            b.append(INDENT).append("lightmapFile=").append('"').append(this.lightmapFile).append('"').append('\n');
            b.append(INDENT).append('\n');
            b.append(INDENT).append("sha256=").append('"').append(this.sha256).append('"').append('\n');
            b.append(">\n");
            for (StoreLightmap lightmap : this.lightmaps) {
                b.append(lightmap.toXML()).append('\n');
            }
            b.append("</lightmaps>");
            return b.toString();
        }
    }

    public static void writeLightmaps(NLightmaps lightmaps, OutputStream output) throws IOException {
        ZipOutputStream zipOut = new ZipOutputStream(output, StandardCharsets.UTF_8);

        zipOut.putNextEntry(new ZipEntry(MAGIC_FILE_IDENTIFIER));
        zipOut.closeEntry();

        StoreLightmaps storeLightmaps = new StoreLightmaps();

        storeLightmaps.name = lightmaps.getName();
        storeLightmaps.margin = lightmaps.getMargin();

        {
            zipOut.putNextEntry(new ZipEntry("color.png"));

            BufferedImage outputImage = new BufferedImage(
                    lightmaps.getColorMapWidth(),
                    lightmaps.getColorMapHeight(),
                    BufferedImage.TYPE_INT_ARGB
            );

            byte[] colorMap = lightmaps.getColorMap();

            for (int y = 0; y < outputImage.getHeight(); y++) {
                for (int x = 0; x < outputImage.getWidth(); x++) {
                    int r = colorMap[0 + (x * 4) + (y * outputImage.getWidth() * 4)] & 0xFF;
                    int g = colorMap[1 + (x * 4) + (y * outputImage.getWidth() * 4)] & 0xFF;
                    int b = colorMap[2 + (x * 4) + (y * outputImage.getWidth() * 4)] & 0xFF;
                    int a = colorMap[3 + (x * 4) + (y * outputImage.getWidth() * 4)] & 0xFF;

                    int argb = (a << 24) | (r << 16) | (g << 8) | (b << 0);

                    outputImage.setRGB(x, (outputImage.getHeight() - 1) - y, argb);
                }
            }

            ImageIO.write(outputImage, "PNG", zipOut);

            zipOut.closeEntry();

            storeLightmaps.colorFile = "color.png";
        }

        {
            zipOut.putNextEntry(new ZipEntry("indirect.png"));

            BufferedImage outputImage = new BufferedImage(
                    lightmaps.getIndirectWidth(),
                    lightmaps.getIndirectHeight() * lightmaps.getNumberOfLightmaps(),
                    BufferedImage.TYPE_INT_ARGB
            );

            byte[] indirectMap = lightmaps.getIndirectLightmaps();

            for (int y = 0; y < outputImage.getHeight(); y++) {
                for (int x = 0; x < outputImage.getWidth(); x++) {
                    int r = indirectMap[0 + (x * 3) + (y * outputImage.getWidth() * 3)] & 0xFF;
                    int g = indirectMap[1 + (x * 3) + (y * outputImage.getWidth() * 3)] & 0xFF;
                    int b = indirectMap[2 + (x * 3) + (y * outputImage.getWidth() * 3)] & 0xFF;
                    int a = 255;

                    int argb = (a << 24) | (r << 16) | (g << 8) | (b << 0);

                    outputImage.setRGB(x, (outputImage.getHeight() - 1) - y, argb);
                }
            }

            ImageIO.write(outputImage, "PNG", zipOut);

            zipOut.closeEntry();

            storeLightmaps.indirectFile = "indirect.png";
        }

        {
            zipOut.putNextEntry(new ZipEntry("light.mpng"));

            MultiPNG.encode(
                    lightmaps.getLightmaps(),
                    lightmaps.getWidth(), lightmaps.getHeight() * lightmaps.getNumberOfLightmaps(),
                    0.001f,
                    zipOut
            );

            zipOut.closeEntry();

            storeLightmaps.lightmapFile = "light.mpng";
        }

        storeLightmaps.sha256 = lightmaps.getSha256();

        storeLightmaps.lightmaps = new ArrayList<>();

        for (int i = 0; i < lightmaps.getNumberOfLightmaps(); i++) {
            StoreLightmap lightmap = new StoreLightmap();

            lightmap.index = i;
            lightmap.name = lightmaps.getName(i);
            lightmap.indirectIntensity = new Vector3f(lightmaps.getIndirectIntensity(i));
            lightmap.intensity = lightmaps.getIntensity(i);

            storeLightmaps.lightmaps.add(lightmap);
        }

        zipOut.putNextEntry(new ZipEntry("lightmaps.xml"));
        zipOut.write(storeLightmaps.toXML().getBytes(StandardCharsets.UTF_8));
        zipOut.closeEntry();

        zipOut.finish();
    }

    static class Image {

        int channels;
        int width;
        int height;
        byte[] data;
    }

    private static Image load(byte[] image, int channels) throws IOException {
        ByteBuffer imageMemory = memAlloc(image.length).put(image).flip();
        try {
            stbi_set_flip_vertically_on_load_thread(1);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer widthBuffer = stack.callocInt(1);
                IntBuffer heightBuffer = stack.callocInt(1);
                IntBuffer channelsBuffer = stack.callocInt(1);

                ByteBuffer imageData = stbi_load_from_memory(
                        imageMemory,
                        widthBuffer, heightBuffer,
                        channelsBuffer,
                        channels
                );

                if (imageData == null) {
                    throw new IOException("Failed to load image: " + stbi_failure_reason());
                }

                try {
                    Image img = new Image();
                    img.channels = channels;
                    img.width = widthBuffer.get();
                    img.height = heightBuffer.get();
                    img.data = new byte[imageData.remaining()];
                    imageData.get(img.data).flip();
                    
                    return img;
                } finally {
                    stbi_image_free(imageData);
                }
            }
        } finally {
            memFree(imageMemory);
        }
    }

    public static NLightmaps readLightmaps(InputStream input) throws IOException {
        ZipInputStream zipInput = new ZipInputStream(input, StandardCharsets.UTF_8);

        Map<String, byte[]> fs = new HashMap<>();

        ZipEntry e;
        while ((e = zipInput.getNextEntry()) != null) {
            if (e.isDirectory()) {
                continue;
            }
            fs.put(e.getName(), zipInput.readAllBytes());
        }

        if (fs.get(MAGIC_FILE_IDENTIFIER) == null) {
            throw new IOException("Invalid Lightmaps File!");
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

        storeLightmaps.name = rootNode.getAttribute("name");
        storeLightmaps.margin = Integer.parseInt(rootNode.getAttribute("margin"));

        storeLightmaps.colorFile = rootNode.getAttribute("colorFile");
        storeLightmaps.indirectFile = rootNode.getAttribute("indirectFile");
        storeLightmaps.lightmapFile = rootNode.getAttribute("lightmapFile");

        storeLightmaps.sha256 = rootNode.getAttribute("sha256");
        if (storeLightmaps.sha256.isBlank()) {
            storeLightmaps.sha256 = null;
        }

        storeLightmaps.lightmaps = new ArrayList<>();

        NodeList maps = rootNode.getElementsByTagName("lightmap");
        for (int i = 0; i < maps.getLength(); i++) {
            Element map = (Element) maps.item(i);
            if (map.getParentNode() != rootNode) {
                continue;
            }

            StoreLightmap storeLightmap = new StoreLightmap();

            storeLightmap.index = Integer.parseInt(map.getAttribute("index"));
            storeLightmap.name = URLDecoder.decode(map.getAttribute("name"), StandardCharsets.UTF_8);
            storeLightmap.indirectIntensity = new Vector3f(
                    Float.parseFloat(map.getAttribute("indirectIntensityR")),
                    Float.parseFloat(map.getAttribute("indirectIntensityG")),
                    Float.parseFloat(map.getAttribute("indirectIntensityB"))
            );
            storeLightmap.intensity = Float.parseFloat(map.getAttribute("intensity"));

            storeLightmaps.lightmaps.add(storeLightmap);
        }
        
        int amountOfLightmaps = storeLightmaps.lightmaps.size();
        
        String[] names = new String[amountOfLightmaps];
        Vector3fc[] indirectIntensities = new Vector3fc[amountOfLightmaps];
        float[] intensities = new float[amountOfLightmaps];
        
        for (StoreLightmap l:storeLightmaps.lightmaps) {
            names[l.index] = l.name;
            indirectIntensities[l.index] = l.indirectIntensity;
            intensities[l.index] = l.intensity;
        }
        
        Image color = load(fs.get(storeLightmaps.colorFile), 4);
        Image indirect = load(fs.get(storeLightmaps.indirectFile), 3);
        MultiPNG.MultiPNGOutput light = MultiPNG.decode(new ByteArrayInputStream(fs.get(storeLightmaps.lightmapFile)));
        
        NLightmaps lightmaps = new NLightmaps(
                storeLightmaps.name, names, storeLightmaps.margin,
                light.data(), light.width(), light.height() / names.length, 
                indirectIntensities, indirect.data, indirect.width, indirect.height / names.length,
                color.data, color.width, color.height,
                storeLightmaps.sha256
        );
        
        for (int i = 0; i < intensities.length; i++) {
            lightmaps.setIntensity(i, intensities[i]);
        }
        
        return lightmaps;
    }

    private NLightmapsStore() {

    }

}