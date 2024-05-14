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

import cientistavuador.newrenderingpipeline.util.CryptoUtils;
import cientistavuador.newrenderingpipeline.util.postprocess.MarginAutomata;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.regex.Pattern;
import org.lwjgl.system.MemoryUtil;
import static org.lwjgl.stb.STBImage.*;
import org.lwjgl.system.MemoryStack;

/**
 *
 * @author Cien
 */
public class NTexturesIO {

    public static final float MINIMUM_AMBIENT_OCCLUSION = 0.5f;

    private static byte[] loadFromJarOrNull(String path) throws FileNotFoundException, IOException {
        if (path == null) {
            return null;
        }
        try (InputStream stream = ClassLoader.getSystemResourceAsStream(path)) {
            if (stream == null) {
                throw new FileNotFoundException("File not found: " + path);
            }
            byte[] data = stream.readAllBytes();
            return data;
        }
    }

    public static NTextures loadFromJar(
            String diffusePath,
            String aoPath,
            String heightPath,
            String roughnessPath,
            String normalPath,
            String metallicPath,
            String emissivePath
    ) throws IOException {
        String[] paths = new String[]{
            diffusePath, aoPath, heightPath, roughnessPath, normalPath, metallicPath, emissivePath
        };
        StringBuilder b = new StringBuilder();
        for (String path1 : paths) {
            if (path1 != null) {
                String path = path1;
                String[] files = path.split(Pattern.quote("/"));
                String filename = files[files.length - 1].split(Pattern.quote("."))[0];
                if (!b.isEmpty()) {
                    b.append("_");
                }
                b.append(filename);
            }
        }
        String name = b.toString();
        if (name.isEmpty()) {
            name = null;
        }

        byte[] diffuse = loadFromJarOrNull(diffusePath);
        byte[] ao = loadFromJarOrNull(aoPath);
        byte[] height = loadFromJarOrNull(heightPath);
        byte[] roughness = loadFromJarOrNull(roughnessPath);
        byte[] normal = loadFromJarOrNull(normalPath);
        byte[] metallic = loadFromJarOrNull(metallicPath);
        byte[] emissive = loadFromJarOrNull(emissivePath);

        return loadFromImages(name, diffuse, ao, height, roughness, normal, metallic, emissive);
    }

    public static class ImageFailedToLoadException extends Exception {

        private static final long serialVersionUID = 1L;

        public ImageFailedToLoadException(String message) {
            super(message);
        }

    }

    public static class LoadedImage {

        public final int width;
        public final int height;
        public final byte[] pixelData;

        public LoadedImage(int width, int height, byte[] pixelData) {
            this.width = width;
            this.height = height;
            this.pixelData = pixelData;
        }
    }

    public static LoadedImage nearestResize(LoadedImage image, int newWidth, int newHeight) {
        if (newWidth < 0 || newHeight < 0) {
            throw new IllegalArgumentException("Negative dimensions.");
        }
        byte[] newData = new byte[newWidth * newHeight * 4];
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                int ox = (int) Math.floor((x / ((float) newWidth)) * image.width);
                int oy = (int) Math.floor((y / ((float) newHeight)) * image.height);

                byte r = image.pixelData[0 + (ox * 4) + (oy * image.width * 4)];
                byte g = image.pixelData[1 + (ox * 4) + (oy * image.width * 4)];
                byte b = image.pixelData[2 + (ox * 4) + (oy * image.width * 4)];
                byte a = image.pixelData[3 + (ox * 4) + (oy * image.width * 4)];
                newData[0 + (x * 4) + (y * newWidth * 4)] = r;
                newData[1 + (x * 4) + (y * newWidth * 4)] = g;
                newData[2 + (x * 4) + (y * newWidth * 4)] = b;
                newData[3 + (x * 4) + (y * newWidth * 4)] = a;
            }
        }
        return new LoadedImage(newWidth, newHeight, newData);
    }

    public static LoadedImage flipY(LoadedImage image) {
        byte[] newData = new byte[image.width * image.height * 4];
        for (int y = 0; y < image.height; y++) {
            System.arraycopy(
                    image.pixelData, y * image.width * 4,
                    newData, ((image.height - 1) - y) * image.width * 4,
                    image.width * 4
            );
        }
        return new LoadedImage(image.width, image.height, newData);
    }

    public static LoadedImage flipX(LoadedImage image) {
        byte[] newData = new byte[image.width * image.height * 4];
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                System.arraycopy(
                        image.pixelData, (x * 4) + (y * image.width * 4),
                        newData, (((image.width - 1) - x) * 4) + (y * image.width * 4),
                        4
                );
            }
        }
        return new LoadedImage(image.width, image.height, newData);
    }

    public static LoadedImage loadImage(byte[] image) {
        try {
            return loadImageChecked(image);
        } catch (ImageFailedToLoadException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static LoadedImage loadImageChecked(byte[] image) throws ImageFailedToLoadException {
        if (image == null) {
            return null;
        }

        ByteBuffer nativeImage = MemoryUtil.memAlloc(image.length).put(image).flip();
        try {
            stbi_set_flip_vertically_on_load_thread(1);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer widthBuffer = stack.callocInt(1);
                IntBuffer heightBuffer = stack.callocInt(1);
                IntBuffer channelsBuffer = stack.callocInt(1);

                ByteBuffer pixelDataBuffer = stbi_load_from_memory(nativeImage, widthBuffer, heightBuffer, channelsBuffer, 4);
                if (pixelDataBuffer == null) {
                    throw new ImageFailedToLoadException("Image failed to load: " + stbi_failure_reason());
                }

                LoadedImage loaded = new LoadedImage(widthBuffer.get(), heightBuffer.get(), new byte[pixelDataBuffer.capacity()]);
                pixelDataBuffer.get(loaded.pixelData).flip();

                stbi_image_free(pixelDataBuffer);

                return loaded;
            }
        } finally {
            MemoryUtil.memFree(nativeImage);
        }
    }

    public static NTextures loadFromImages(
            String name,
            byte[] diffuseImage,
            byte[] aoImage,
            byte[] heightImage,
            byte[] roughnessImage,
            byte[] normalImage,
            byte[] metallicImage,
            byte[] emissiveImage
    ) {
        LoadedImage diffuse = loadImage(diffuseImage);
        LoadedImage ao = loadImage(aoImage);
        LoadedImage height = loadImage(heightImage);
        LoadedImage exponent = loadImage(roughnessImage);
        LoadedImage normal = loadImage(normalImage);
        LoadedImage metallic = loadImage(metallicImage);
        LoadedImage emissive = loadImage(emissiveImage);

        LoadedImage[] loadedArray = new LoadedImage[]{
            diffuse, ao, height, exponent, normal, metallic, emissive
        };

        int foundWidth = -1;
        int foundHeight = -1;

        for (LoadedImage i : loadedArray) {
            if (i != null) {
                foundWidth = i.width;
                foundHeight = i.height;
            }
            if (i != null && foundWidth != -1 && foundHeight != -1) {
                if (foundWidth != i.width || foundHeight != i.height) {
                    throw new IllegalArgumentException("Images width and height differ");
                }
            }
        }

        if (foundWidth == -1 && foundHeight == -1) {
            foundWidth = 0;
            foundHeight = 0;
        }

        return load(
                name,
                foundWidth,
                foundHeight,
                (diffuse != null ? diffuse.pixelData : null),
                (ao != null ? ao.pixelData : null),
                (height != null ? height.pixelData : null),
                (exponent != null ? exponent.pixelData : null),
                (normal != null ? normal.pixelData : null),
                (metallic != null ? metallic.pixelData : null),
                (emissive != null ? emissive.pixelData : null)
        );
    }

    private static void validate(String mapName, byte[] map, int requiredPixels) {
        if (map != null && map.length != (requiredPixels * 4)) {
            throw new IllegalArgumentException(mapName + " requires " + requiredPixels + " pixels but found " + (map.length / 4));
        }
    }

    private static int fetch(byte[] map, int index, int fallback) {
        if (map == null) {
            return fallback;
        }
        return map[index] & 0xFF;
    }

    public static NTextures load(
            String name,
            int width, int height,
            byte[] diffuseMap,
            byte[] aoMap,
            byte[] heightMap,
            byte[] roughnessMap,
            byte[] normalMap,
            byte[] metallicMap,
            byte[] emissiveMap
    ) {
        if (width < 0) {
            throw new IllegalArgumentException("width is negative.");
        }
        if (height < 0) {
            throw new IllegalArgumentException("height is negative.");
        }

        int pixels = width * height;

        validate("diffuse map", diffuseMap, pixels);
        validate("ao map", aoMap, pixels);
        validate("height map", heightMap, pixels);
        validate("roughness map", roughnessMap, pixels);
        validate("normal map", normalMap, pixels);
        validate("metallic map", metallicMap, pixels);
        validate("emissive map", emissiveMap, pixels);

        NBlendingMode mode = NBlendingMode.OPAQUE;

        if (diffuseMap != null) {
            for (int p = 0; p < pixels; p++) {
                int alpha = diffuseMap[(p * 4) + 3] & 0xFF;
                if (alpha == 0 && NBlendingMode.OPAQUE.equals(mode)) {
                    mode = NBlendingMode.ALPHA_TESTING;
                }
                if (alpha != 0 && alpha != 255) {
                    mode = NBlendingMode.ALPHA_BLENDING;
                    break;
                }
            }
        }

        byte[] rgba = new byte[pixels * 4];
        byte[] hrmnx = new byte[pixels * 4];
        byte[] eregebny = new byte[pixels * 4];

        boolean heightMapSupported = false;

        for (int p = 0; p < pixels; p++) {
            int r = fetch(diffuseMap, (p * 4) + 0, 255);
            int g = fetch(diffuseMap, (p * 4) + 1, 255);
            int b = fetch(diffuseMap, (p * 4) + 2, 255);
            int a = fetch(diffuseMap, (p * 4) + 3, 255);

            int ao = fetch(aoMap, (p * 4) + 0, 255);
            int hei = fetch(heightMap, (p * 4) + 0, 255);
            int rg = fetch(roughnessMap, (p * 4) + 0, 255);

            int nx = fetch(normalMap, (p * 4) + 0, 127);
            int ny = fetch(normalMap, (p * 4) + 1, 127);

            int mt = fetch(metallicMap, (p * 4) + 0, 0);

            int er = fetch(emissiveMap, (p * 4) + 0, 0);
            int eg = fetch(emissiveMap, (p * 4) + 1, 0);
            int eb = fetch(emissiveMap, (p * 4) + 2, 0);

            if (hei != 255) {
                heightMapSupported = true;
            }

            float ambientOcclusion = ((ao / 255f) * (1f - MINIMUM_AMBIENT_OCCLUSION)) + MINIMUM_AMBIENT_OCCLUSION;

            rgba[(p * 4) + 0] = (byte) Math.floor(r * ambientOcclusion);
            rgba[(p * 4) + 1] = (byte) Math.floor(g * ambientOcclusion);
            rgba[(p * 4) + 2] = (byte) Math.floor(b * ambientOcclusion);
            rgba[(p * 4) + 3] = (byte) a;

            hrmnx[(p * 4) + 0] = (byte) hei;
            hrmnx[(p * 4) + 1] = (byte) rg;
            hrmnx[(p * 4) + 2] = (byte) mt;
            hrmnx[(p * 4) + 3] = (byte) nx;

            eregebny[(p * 4) + 0] = (byte) er;
            eregebny[(p * 4) + 1] = (byte) eg;
            eregebny[(p * 4) + 2] = (byte) eb;
            eregebny[(p * 4) + 3] = (byte) ny;
        }

        if (!NBlendingMode.OPAQUE.equals(mode)) {
            class IO implements MarginAutomata.MarginAutomataIO {

                private final int buffer;

                public IO(int buffer) {
                    this.buffer = buffer;
                }

                @Override
                public int width() {
                    return width;
                }

                @Override
                public int height() {
                    return height;
                }

                @Override
                public boolean empty(int x, int y) {
                    int pixel = ((y * width) + x) * 4;
                    int alpha = fetch(rgba, pixel + 3, 255);
                    return alpha == 0;
                }

                @Override
                public void read(int x, int y, MarginAutomata.MarginAutomataColor color) {
                    int pixel = ((y * width) + x) * 4;
                    float red = 1f;
                    float green = 0f;
                    float blue = 1f;
                    switch (this.buffer) {
                        case 0 -> {
                            red = fetch(rgba, pixel + 0, 255) / 255f;
                            green = fetch(rgba, pixel + 1, 255) / 255f;
                            blue = fetch(rgba, pixel + 2, 255) / 255f;
                        }
                        case 1 -> {
                            red = fetch(hrmnx, pixel + 0, 255) / 255f;
                            green = fetch(hrmnx, pixel + 1, 255) / 255f;
                            blue = fetch(hrmnx, pixel + 2, 255) / 255f;
                        }
                        case 2 -> {
                            red = fetch(eregebny, pixel + 0, 255) / 255f;
                            green = fetch(eregebny, pixel + 1, 255) / 255f;
                            blue = fetch(eregebny, pixel + 2, 255) / 255f;
                        }
                        case 3 -> {
                            red = fetch(hrmnx, pixel + 3, 255) / 255f;
                            green = fetch(eregebny, pixel + 3, 255) / 255f;
                            blue = 0f;
                        }

                    }
                    color.r = red;
                    color.g = green;
                    color.b = blue;
                }

                @Override
                public void write(int x, int y, MarginAutomata.MarginAutomataColor color) {
                    int red = (int) (Math.min(Math.max(color.r, 0f), 1f) * 255f);
                    int green = (int) (Math.min(Math.max(color.g, 0f), 1f) * 255f);
                    int blue = (int) (Math.min(Math.max(color.b, 0f), 1f) * 255f);
                    int pixel = ((y * width) + x) * 4;
                    switch (this.buffer) {
                        case 0 -> {
                            rgba[pixel + 0] = (byte) red;
                            rgba[pixel + 1] = (byte) green;
                            rgba[pixel + 2] = (byte) blue;
                        }
                        case 1 -> {
                            hrmnx[pixel + 0] = (byte) red;
                            hrmnx[pixel + 1] = (byte) green;
                            hrmnx[pixel + 2] = (byte) blue;
                        }
                        case 2 -> {
                            eregebny[pixel + 0] = (byte) red;
                            eregebny[pixel + 1] = (byte) green;
                            eregebny[pixel + 2] = (byte) blue;
                        }
                        case 3 -> {
                            hrmnx[pixel + 3] = (byte) red;
                            eregebny[pixel + 3] = (byte) green;
                        }

                    }
                }
            }
            
            Thread[] threads = new Thread[4];
            Throwable[] exceptions = new Throwable[threads.length];
            for (int i = 0; i < threads.length; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    MarginAutomata.generateMargin(new IO(index), -1);
                }, "NTexturesIO-"+i+"-"+name);
                threads[i].setUncaughtExceptionHandler((t, ex) -> {
                    exceptions[index] = ex;
                });
                threads[i].start();
            }
            for (int i = 0; i < threads.length; i++) {
                try {
                    threads[i].join();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
            for (int i = 0; i < threads.length; i++) {
                if (exceptions[i] != null) {
                    throw new RuntimeException(exceptions[i]);
                }
            }
        }
        
        return new NTextures(
                name,
                width, height,
                rgba,
                hrmnx,
                eregebny,
                mode,
                heightMapSupported,
                null
        );
    }

    private NTexturesIO() {

    }

}
