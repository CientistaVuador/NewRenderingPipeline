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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/**
 *
 * @author Cien
 */
public class NCubemapImporter {

    public static NCubemap create(
            String name,
            String uid,
            NCubemapInfo cubemapInfo,
            int size,
            float[][] sideTextures
    ) {
        if (sideTextures == null) {
            throw new NullPointerException("Side Textures is null.");
        }
        if (sideTextures.length != NCubemap.SIDES) {
            throw new IllegalArgumentException("Side Textures Length is not " + NCubemap.SIDES + ".");
        }

        DXT5Texture[] sidesDXT5 = new DXT5Texture[NCubemap.SIDES];

        float totalAverageR = 0f;
        float totalAverageG = 0f;
        float totalAverageB = 0f;

        for (int i = 0; i < sideTextures.length; i++) {
            float[] side = sideTextures[i];
            if (side == null) {
                throw new NullPointerException("Side at index " + i + " is null.");
            }
            int expectedSize = size * size * 3;
            if (side.length != expectedSize) {
                throw new IllegalArgumentException("Expected size at side " + i + " is " + expectedSize + ", not " + side.length + ".");
            }

            float averageR = 0f;
            float averageG = 0f;
            float averageB = 0f;
            for (int j = 0; j < side.length; j += 3) {
                averageR += side[j + 0];
                averageG += side[j + 1];
                averageB += side[j + 2];
            }
            float inv = 1f / (side.length / 3);
            averageR *= inv;
            averageG *= inv;
            averageB *= inv;

            totalAverageR += averageR;
            totalAverageG += averageG;
            totalAverageB += averageB;

            E8Image e8Image = new E8Image(side, size, size);
            sidesDXT5[i] = DXT5TextureStore.createDXT5Texture(e8Image.getRGBE(), size, size);
        }

        float inv = 1f / NCubemap.SIDES;
        totalAverageR *= inv;
        totalAverageG *= inv;
        totalAverageB *= inv;

        return new NCubemap(name, uid, totalAverageR, totalAverageG, totalAverageB, cubemapInfo, sidesDXT5);
    }

    private static byte[] getSide(
            boolean flipX, boolean flipY,
            int x, int y, int size,
            byte[] cubemap, int width
    ) {
        byte[] output = new byte[size * size * 4];

        for (int localY = 0; localY < size; localY++) {
            int flippedY = localY;
            if (flipY) {
                flippedY = ((size - 1) - localY);
            }
            if (flipX) {
                for (int localX = 0; localX < size; localX++) {
                    int flippedX = ((size - 1) - localX);

                    System.arraycopy(cubemap,
                            ((localX + x) * 4) + ((localY + y) * width * 4),
                            output,
                            (flippedX * 4) + (flippedY * size * 4),
                            4
                    );
                }
            } else {
                System.arraycopy(cubemap,
                        (x * 4) + ((localY + y) * width * 4),
                        output,
                        (flippedY * size * 4),
                        size * 4
                );
            }
        }

        return output;
    }

    public static NCubemap load(String name, int width, int height, byte[] cubemap, boolean srgb) {
        int pixels = width * height;
        if (cubemap.length / 4 != pixels) {
            throw new IllegalArgumentException("Invalid image size, expected " + pixels + " pixels but found " + (cubemap.length / 4) + " pixels!");
        }

        int horizontalSize = width / 4;
        int verticalSize = height / 3;
        if (horizontalSize != verticalSize) {
            throw new IllegalArgumentException("Invalid cubemap size, sides must be squares!");
        }

        int size = horizontalSize;

        byte[][] cubemapSides = {
            getSide(true, true, size * 2, size * 1, size, cubemap, width),
            getSide(true, true, size * 0, size * 1, size, cubemap, width),
            getSide(false, false, size * 1, size * 2, size, cubemap, width),
            getSide(false, false, size * 1, size * 0, size, cubemap, width),
            getSide(true, true, size * 1, size * 1, size, cubemap, width),
            getSide(true, true, size * 3, size * 1, size, cubemap, width)
        };

        float[][] cubemapSidesFloat = new float[NCubemap.SIDES][];

        for (int i = 0; i < cubemapSidesFloat.length; i++) {
            byte[] cubemapSide = cubemapSides[i];
            float[] side = new float[size * size * 3];

            for (int j = 0; j < side.length; j += 4) {
                float r = (cubemapSide[j + 0] & 0xFF) / 255f;
                float g = (cubemapSide[j + 1] & 0xFF) / 255f;
                float b = (cubemapSide[j + 2] & 0xFF) / 255f;
                r = (float) Math.pow(r, 2.2);
                g = (float) Math.pow(g, 2.2);
                b = (float) Math.pow(b, 2.2);
                int floatIndex = (j / 4) * 3;
                side[floatIndex + 0] = r;
                side[floatIndex + 1] = g;
                side[floatIndex + 2] = b;
            }

            cubemapSidesFloat[i] = side;
        }
        
        return create(name, null, null, size, cubemapSidesFloat);
    }

    public static NCubemap loadFromImage(String name, byte[] image, boolean srgb) {
        NTexturesImporter.LoadedImage img = NTexturesImporter.loadImage(image);
        return load(name, img.width, img.height, img.pixelData, srgb);
    }

    public static NCubemap loadFromJar(String path, boolean srgb) {
        String[] split = path.split("/");
        String name = split[split.length - 1];

        byte[] imageData;
        try {
            try (InputStream stream = ClassLoader.getSystemResourceAsStream(path)) {
                imageData = stream.readAllBytes();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        return loadFromImage(name, imageData, srgb);
    }

    public static NCubemap loadFromStream(String name, InputStream stream, boolean srgb) {
        byte[] imageData;
        try {
            imageData = stream.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        return loadFromImage(name, imageData, srgb);
    }

    private NCubemapImporter() {

    }
}
