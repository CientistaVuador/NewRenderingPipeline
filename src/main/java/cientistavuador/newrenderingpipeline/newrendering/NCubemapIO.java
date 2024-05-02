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
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/**
 *
 * @author Cien
 */
public class NCubemapIO {

    public static NCubemap loadFromJar(String path, boolean srgb, boolean compressed) {
        String[] split = path.split("/");
        String name = split[split.length - 1];

        byte[] imageData;
        try {
            InputStream stream = ClassLoader.getSystemResourceAsStream(path);
            imageData = stream.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        return loadFromImage(name, imageData, srgb, compressed);
    }

    public static NCubemap loadFromImage(String name, byte[] image, boolean srgb, boolean compressed) {
        NTexturesIO.LoadedImage img = NTexturesIO.loadImage(image);
        return load(name, img.width, img.height, img.pixelData, srgb, compressed);
    }

    private static void getSide(
            boolean flipX, boolean flipY,
            int x, int y, int size,
            byte[] cubemap, int width,
            byte[] output, int outputOffset
    ) {
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
                            outputOffset + (flippedX * 4) + (flippedY * size * 4),
                            4
                    );
                }
            } else {
                System.arraycopy(cubemap,
                        (x * 4) + ((localY + y) * width * 4),
                        output,
                        outputOffset + (flippedY * size * 4),
                        size * 4
                );
            }
        }
    }

    public static NCubemap load(String name, int width, int height, byte[] cubemap, boolean srgb, boolean compressed) {
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

        byte[] cubemapSides = new byte[size * size * 4 * NCubemap.SIDES];

        getSide(true, true, size * 2, size * 1, size, cubemap, width, cubemapSides, size * size * 4 * NCubemap.POSITIVE_X);
        getSide(true, true, size * 0, size * 1, size, cubemap, width, cubemapSides, size * size * 4 * NCubemap.NEGATIVE_X);
        getSide(false, false, size * 1, size * 2, size, cubemap, width, cubemapSides, size * size * 4 * NCubemap.POSITIVE_Y);
        getSide(false, false, size * 1, size * 0, size, cubemap, width, cubemapSides, size * size * 4 * NCubemap.NEGATIVE_Y);
        getSide(true, true, size * 1, size * 1, size, cubemap, width, cubemapSides, size * size * 4 * NCubemap.NEGATIVE_Z);
        getSide(true, true, size * 3, size * 1, size, cubemap, width, cubemapSides, size * size * 4 * NCubemap.POSITIVE_Z);

        ByteBuffer totalData = ByteBuffer.allocate(cubemapSides.length + 4).putInt(size).put(cubemapSides).flip();
        String sha256 = CryptoUtils.sha256(totalData);

        return new NCubemap(name, size, cubemapSides, sha256, srgb, compressed, false, null, null);
    }

    private NCubemapIO() {

    }
}
