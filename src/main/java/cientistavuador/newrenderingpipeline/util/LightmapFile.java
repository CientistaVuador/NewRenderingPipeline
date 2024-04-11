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
package cientistavuador.newrenderingpipeline.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author Cien
 */
public class LightmapFile {

    public static void encode(LightmapData data, float precision, OutputStream output) throws IOException {
        new LightmapFile(data, precision, output).encode();
    }

    public static LightmapData decode(InputStream input) throws IOException {
        return new LightmapFile(input).decode();
    }

    public static class Lightmap {

        private final String groupName;
        private final float[] lightmap;

        public Lightmap(String groupName, float[] lightmap) {
            this.groupName = groupName;
            this.lightmap = lightmap;
        }

        public String groupName() {
            return groupName;
        }

        public float[] data() {
            return lightmap;
        }

    }

    public static class LightmapData {

        private final float pixelToWorldRatio;
        private final float scaleX;
        private final float scaleY;
        private final float scaleZ;

        private final int lightmapSize;
        private final Lightmap[] lightmaps;

        public LightmapData(float pixelToWorldRatio, float scaleX, float scaleY, float scaleZ, int lightmapSize, Lightmap[] lightmaps) {
            this.pixelToWorldRatio = pixelToWorldRatio;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.scaleZ = scaleZ;
            this.lightmapSize = lightmapSize;
            this.lightmaps = lightmaps;
        }

        public float pixelToWorldRatio() {
            return pixelToWorldRatio;
        }

        public float scaleX() {
            return scaleX;
        }

        public float scaleY() {
            return scaleY;
        }

        public float scaleZ() {
            return scaleZ;
        }

        public int lightmapSize() {
            return lightmapSize;
        }

        public Lightmap[] lightmaps() {
            return lightmaps;
        }

    }

    private float pixelToWorldRatio = 0f;
    private float scaleX = 0f;
    private float scaleY = 0f;
    private float scaleZ = 0f;

    private int lightmapSize = 0;
    private Lightmap[] lightmaps = null;
    
    private final Map<Integer, float[]> lightmapsRaw = new HashMap<>();
    private final Map<Integer, String> lightmapsNames = new HashMap<>();
    
    private float precision = 0f;

    private final StringBuilder comments = new StringBuilder();
    private final ZipInputStream input;
    private final ZipOutputStream output;

    private LightmapFile(InputStream input) {
        this.input = new ZipInputStream(input, StandardCharsets.UTF_8);
        this.output = null;
    }

    private LightmapFile(LightmapData data, float precision, OutputStream output) {
        this.input = null;
        this.precision = precision;
        this.output = new ZipOutputStream(output, StandardCharsets.UTF_8);

        this.pixelToWorldRatio = data.pixelToWorldRatio();
        this.scaleX = data.scaleX();
        this.scaleY = data.scaleY();
        this.scaleZ = data.scaleZ();
        this.lightmapSize = data.lightmapSize();
        this.lightmaps = data.lightmaps();
    }

    private long encodeFloat(float f) {
        return Integer.toUnsignedLong(Float.floatToRawIntBits(f));
    }

    private void writeProperties() throws IOException {
        Properties lightmapProperties = new Properties();

        long pixelToWorldRatioEncoded = encodeFloat(this.pixelToWorldRatio);
        long scaleXEncoded = encodeFloat(this.scaleX);
        long scaleYEncoded = encodeFloat(this.scaleY);
        long scaleZEncoded = encodeFloat(this.scaleZ);

        lightmapProperties.put("pixelToWorldRatio", Long.toHexString(pixelToWorldRatioEncoded).toUpperCase());
        lightmapProperties.put("scaleX", Long.toHexString(scaleXEncoded).toUpperCase());
        lightmapProperties.put("scaleY", Long.toHexString(scaleYEncoded).toUpperCase());
        lightmapProperties.put("scaleZ", Long.toHexString(scaleZEncoded).toUpperCase());

        this.comments
                .append("Created on: ").append(new Date(System.currentTimeMillis()).toString()).append('\n')
                .append("Pixel To World Ratio: ").append(String.format(Locale.US, "%.6f", this.pixelToWorldRatio)).append('\n')
                .append("Scale X: ").append(String.format(Locale.US, "%.6f", this.scaleX)).append('\n')
                .append("Scale Y: ").append(String.format(Locale.US, "%.6f", this.scaleY)).append('\n')
                .append("Scale Z: ").append(String.format(Locale.US, "%.6f", this.scaleZ)).append('\n');
        
        this.comments
                .append("Amount of Lightmaps: ").append(this.lightmaps.length).append('\n')
                .append("Lightmap Size: ").append(this.lightmapSize).append('x').append(this.lightmapSize).append('\n');
        
        lightmapProperties.put("amountOfLightmaps", Integer.toString(this.lightmaps.length));
        
        for (int i = 0; i < this.lightmaps.length; i++) {
            Lightmap lightmap = this.lightmaps[i];
            lightmapProperties.put(Integer.toString(i), lightmap.groupName());
            
            this.comments
                    .append("Index ")
                    .append(i)
                    .append(" is '")
                    .append(lightmap.groupName())
                    .append("'\n")
                    ;
        }

        ZipEntry entry = new ZipEntry("lightmap.xml");
        this.output.putNextEntry(entry);
        lightmapProperties.storeToXML(this.output, null, StandardCharsets.UTF_8);
        this.output.closeEntry();
    }
    
    private void writeLightmap(int index) throws IOException {
        Lightmap lightmap = this.lightmaps[index];
        
        ZipEntry entry = new ZipEntry(index+".multipng");
        this.output.putNextEntry(entry);
        MultiPNG.encode(
                lightmap.data(),
                this.lightmapSize,
                this.lightmapSize,
                this.precision,
                this.output
        );
        this.output.closeEntry();
    }
    
    private void encode() throws IOException {
        writeProperties();
        for (int i = 0; i < this.lightmaps.length; i++) {
            writeLightmap(i);
        }
        this.output.setComment(this.comments.toString());
        this.output.finish();
    }

    private void readEntry(ZipEntry entry) throws IOException {
        if (entry.getName().equalsIgnoreCase("lightmap.xml")) {
            readProperties();
            return;
        }
        if (entry.getName().toLowerCase().endsWith(".multipng")) {
            int index = Integer.parseInt(entry.getName().split(Pattern.quote("."))[0]);
            readLightmap(index);
            return;
        }
    }
    
    private void readProperties() throws IOException {
        Properties properties = new Properties();
        
        ByteArrayOutputStream out = new ByteArrayOutputStream(65535);
        byte[] buffer = new byte[4096];
        int r;
        while ((r = this.input.read(buffer)) != -1) {
            out.write(buffer, 0, r);
        }
        
        properties.loadFromXML(new ByteArrayInputStream(out.toByteArray()));
        
        for (Map.Entry<Object, Object> entry:properties.entrySet()) {
            String key = entry.getKey().toString();
            String value = entry.getValue().toString();
            
            switch (key) {
                case "pixelToWorldRatio" -> {
                    this.pixelToWorldRatio = Float.intBitsToFloat((int) Long.parseLong(value, 16));
                }
                case "scaleX" -> {
                    this.scaleX = Float.intBitsToFloat((int) Long.parseLong(value, 16));
                }
                case "scaleY" -> {
                    this.scaleY = Float.intBitsToFloat((int) Long.parseLong(value, 16));
                }
                case "scaleZ" -> {
                    this.scaleZ = Float.intBitsToFloat((int) Long.parseLong(value, 16));
                }
                case "amountOfLightmaps" -> {
                    this.lightmaps = new Lightmap[Integer.parseInt(value)];
                }
                default -> {
                    this.lightmapsNames.put(Integer.valueOf(key), value);
                }
            }
        }
    }
    
    private void readLightmap(int index) throws IOException {
        MultiPNG.MultiPNGOutput out = MultiPNG.decode(this.input);
        
        this.lightmapSize = Math.min(out.width(), out.height());
        this.lightmapsRaw.put(index, out.data());
    }
    
    private LightmapData decode() throws IOException {
        ZipEntry entry;
        while ((entry = this.input.getNextEntry()) != null) {
            readEntry(entry);
        }
        for (int i = 0; i < this.lightmaps.length; i++) {
            this.lightmaps[i] = new Lightmap(this.lightmapsNames.get(i), this.lightmapsRaw.get(i));
        }
        return new LightmapData(
                this.pixelToWorldRatio,
                this.scaleX, this.scaleY, this.scaleZ,
                this.lightmapSize, this.lightmaps
        );
    }

}
