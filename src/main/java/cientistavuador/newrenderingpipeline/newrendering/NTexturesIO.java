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
    
    private static byte[] loadFromJarOrNull(String path) throws FileNotFoundException, IOException {
        if (path == null) {
            return null;
        }
        try (InputStream stream = ClassLoader.getSystemResourceAsStream(path)) {
            if (stream == null) {
                throw new FileNotFoundException("File not found: "+path);
            }
            byte[] data = stream.readAllBytes();
            return data;
        }
    }
    
    public static NTextures loadFromJar(
            String diffusePath,
            String heightPath,
            String exponentPath,
            String normalPath,
            String reflectivenessPath
    ) throws IOException {
        String[] paths = new String[] {
            diffusePath, heightPath, exponentPath, normalPath, reflectivenessPath
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
        byte[] height = loadFromJarOrNull(heightPath);
        byte[] exponent = loadFromJarOrNull(exponentPath);
        byte[] normal = loadFromJarOrNull(normalPath);
        byte[] reflectiveness = loadFromJarOrNull(reflectivenessPath);
        
        return loadFromImages(name, diffuse, height, exponent, normal, reflectiveness);
    }
    
    private static class LoadedImage {
        public final int width;
        public final int height;
        public final byte[] pixelData;

        public LoadedImage(int width, int height, byte[] pixelData) {
            this.width = width;
            this.height = height;
            this.pixelData = pixelData;
        }
    }
    
    private static LoadedImage load(byte[] image) {
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
                    throw new RuntimeException("Image failed to load: "+stbi_failure_reason());
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
            byte[] heightImage,
            byte[] exponentImage,
            byte[] normalImage,
            byte[] reflectivenessImage
    ) {
        LoadedImage diffuse = load(diffuseImage);
        LoadedImage height = load(heightImage);
        LoadedImage exponent = load(exponentImage);
        LoadedImage normal = load(normalImage);
        LoadedImage reflectiveness = load(reflectivenessImage);
        
        LoadedImage[] loadedArray = new LoadedImage[] {
            diffuse, height, exponent, normal, reflectiveness
        };
        
        int foundWidth = -1;
        int foundHeight = -1;
        
        for (LoadedImage i:loadedArray) {
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
                (height != null ? height.pixelData : null),
                (exponent != null ? exponent.pixelData : null),
                (normal != null ? normal.pixelData : null),
                (reflectiveness != null ? reflectiveness.pixelData : null)
        );
    }
    
    private static void validate(String mapName, byte[] map, int requiredPixels) {
        if (map != null && map.length != (requiredPixels * 4)) {
            throw new IllegalArgumentException(mapName+" requires "+requiredPixels+" pixels but found "+(map.length / 4));
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
            byte[] heightMap,
            byte[] exponentMap,
            byte[] normalMap,
            byte[] reflectivenessMap
    ) {
        if (width < 0) {
            throw new IllegalArgumentException("width is negative.");
        }
        if (height < 0) {
            throw new IllegalArgumentException("height is negative.");
        }
        
        int pixels = width * height;
        
        validate("diffuse map", diffuseMap, pixels);
        validate("height map", heightMap, pixels);
        validate("exponent map", exponentMap, pixels);
        validate("normal map", normalMap, pixels);
        validate("reflectiveness map", reflectivenessMap, pixels);
        
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
        
        byte[] rgbaorh = new byte[pixels * 4];
        byte[] exry = new byte[pixels * 4];
        
        for (int p = 0; p < pixels; p++) {
            int r = fetch(diffuseMap, (p * 4) + 0, 255);
            int g = fetch(diffuseMap, (p * 4) + 1, 255);
            int b = fetch(diffuseMap, (p * 4) + 2, 255);
            int a = fetch(diffuseMap, (p * 4) + 3, 255);
            int hei = fetch(heightMap, (p * 4) + 0, 255);
            int exp = fetch(exponentMap, (p * 4) + 0, 0);
            int nx = fetch(normalMap, (p * 4) + 0, 127);
            int re = fetch(reflectivenessMap, (p * 4) + 0, 0);
            int ny = fetch(normalMap, (p * 4) + 1, 127);
            
            if (NBlendingMode.OPAQUE.equals(mode) && hei != 255) {
                mode = NBlendingMode.OPAQUE_WITH_HEIGHT_MAP;
            }
            
            rgbaorh[(p * 4) + 0] = (byte) r;
            rgbaorh[(p * 4) + 1] = (byte) g;
            rgbaorh[(p * 4) + 2] = (byte) b;
            rgbaorh[(p * 4) + 3] = (byte) (NBlendingMode.OPAQUE.equals(mode) || NBlendingMode.OPAQUE_WITH_HEIGHT_MAP.equals(mode) ? hei : a);
            
            exry[(p * 4) + 0] = (byte) exp;
            exry[(p * 4) + 1] = (byte) nx;
            exry[(p * 4) + 2] = (byte) re;
            exry[(p * 4) + 3] = (byte) ny;
        }
        
        if (!NBlendingMode.OPAQUE.equals(mode) && !NBlendingMode.OPAQUE_WITH_HEIGHT_MAP.equals(mode)) {
            MarginAutomata.MarginAutomataIO io = new MarginAutomata.MarginAutomataIO() {
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
                    int alpha = fetch(rgbaorh, pixel + 3, 255);
                    return alpha == 0;
                }
                
                @Override
                public void read(int x, int y, MarginAutomata.MarginAutomataColor color) {
                    int pixel = ((y * width) + x) * 4;
                    float red = fetch(rgbaorh, pixel + 0, 255) / 255f;
                    float green = fetch(rgbaorh, pixel + 1, 255) / 255f;
                    float blue = fetch(rgbaorh, pixel + 2, 255) / 255f;
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
                    rgbaorh[pixel + 0] = (byte) red;
                    rgbaorh[pixel + 1] = (byte) green;
                    rgbaorh[pixel + 2] = (byte) blue;
                }
            };
            MarginAutomata.generateMargin(io, -1);
        }
        
        return new NTextures(name, width, height, rgbaorh, exry, mode);
    }
    
    private NTexturesIO() {
        
    }
    
}
