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

import cientistavuador.newrenderingpipeline.util.PixelUtils.PixelStructure;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.joml.Vector4f;
import org.lwjgl.PointerBuffer;
import static org.lwjgl.stb.STBDXT.*;
import static org.lwjgl.stb.STBImage.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryUtil.*;
import org.lwjgl.util.tinyexr.EXRChannelInfo;
import org.lwjgl.util.tinyexr.EXRHeader;
import org.lwjgl.util.tinyexr.EXRImage;
import static org.lwjgl.util.tinyexr.TinyEXR.*;

/**
 *
 * @author Cien
 */
public class ImageUtils {

    public static class Image {

        private final byte[] data;
        private final int width;
        private final int height;
        private final int channels;

        private Image(byte[] data, int width, int height, int channels) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.channels = channels;
        }

        public byte[] getData() {
            return data;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public int getChannels() {
            return channels;
        }

    }

    public static Image load(byte[] imageData, int channels) {
        ByteBuffer imageMemory = memAlloc(imageData.length).put(imageData).flip();
        try {
            stbi_set_flip_vertically_on_load_thread(1);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer widthBuffer = stack.callocInt(1);
                IntBuffer heightBuffer = stack.callocInt(1);
                IntBuffer channelsBuffer = stack.callocInt(1);

                ByteBuffer imageDataResult = stbi_load_from_memory(
                        imageMemory,
                        widthBuffer, heightBuffer,
                        channelsBuffer,
                        channels
                );

                if (imageDataResult == null) {
                    throw new RuntimeException("Failed to load image: " + stbi_failure_reason());
                }

                try {
                    byte[] data = new byte[imageDataResult.remaining()];
                    imageDataResult.get(data).flip();

                    return new Image(
                            data,
                            widthBuffer.get(),
                            heightBuffer.get(),
                            channelsBuffer.get()
                    );
                } finally {
                    stbi_image_free(imageDataResult);
                }
            }
        } finally {
            memFree(imageMemory);
        }
    }

    public static void sample(
            float[] image, int width, int height, int channels,
            int x, int y,
            Vector4f outColor
    ) {
        x = Math.min(Math.max(x, 0), width - 1);
        y = Math.min(Math.max(y, 0), height - 1);
        for (int i = 0; i < channels; i++) {
            outColor.setComponent(
                    i,
                    image[i + (x * channels) + (y * width * channels)]
            );
        }
    }

    public static float[] mipmap(float[] image, int width, int height, int channels) {
        int newWidth = width / 2;
        int newHeight = height / 2;

        Vector4f color = new Vector4f();
        Vector4f resultColor = new Vector4f();

        float[] newImage = new float[newWidth * newHeight * channels];
        for (int y = 0; y < newHeight; y++) {
            for (int x = 0; x < newWidth; x++) {
                resultColor.zero();
                for (int yOffset = 0; yOffset < 2; yOffset++) {
                    for (int xOffset = 0; xOffset < 2; xOffset++) {
                        sample(
                                image,
                                width, height, channels,
                                (x * 2) + xOffset, (y * 2) + yOffset,
                                color
                        );
                        resultColor.add(color);
                    }
                }
                resultColor.div(4f);

                for (int c = 0; c < channels; c++) {
                    newImage[c + (x * channels) + (y * newWidth * channels)] = resultColor.get(c);
                }
            }
        }

        return newImage;
    }

    public static Image asImage(byte[] data, int width, int height, int channels) {
        return new Image(data, width, height, channels);
    }

    public static Image fromBufferedImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = new byte[width * height * 4];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);

                data[0 + (x * 4) + (y * width * 4)] = (byte) ((argb >> 16) & 0xFF);
                data[1 + (x * 4) + (y * width * 4)] = (byte) ((argb >> 8) & 0xFF);
                data[2 + (x * 4) + (y * width * 4)] = (byte) ((argb >> 0) & 0xFF);
                data[3 + (x * 4) + (y * width * 4)] = (byte) ((argb >> 24) & 0xFF);
            }
        }

        return new Image(data, width, height, 4);
    }

    public static BufferedImage toBufferedImage(Image img) {
        if (img.getChannels() > 4 || img.getChannels() < 1) {
            throw new IllegalArgumentException("More than 4 channels or less than 1!");
        }

        byte[] data = img.getData();
        int w = img.getWidth();
        int h = img.getHeight();

        BufferedImage outImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int channels = img.getChannels();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int alpha = 255;

                switch (img.getChannels()) {
                    case 1 -> {
                        red = data[0 + (x * channels) + (y * w * channels)] & 0xFF;
                        green = red;
                        blue = red;
                    }
                    case 2 -> {
                        red = data[0 + (x * channels) + (y * w * channels)] & 0xFF;
                        green = red;
                        blue = red;
                        alpha = data[1 + (x * channels) + (y * w * channels)] & 0xFF;
                    }
                    case 3 -> {
                        red = data[0 + (x * channels) + (y * w * channels)] & 0xFF;
                        green = data[1 + (x * channels) + (y * w * channels)] & 0xFF;
                        blue = data[2 + (x * channels) + (y * w * channels)] & 0xFF;
                    }
                    case 4 -> {
                        red = data[0 + (x * channels) + (y * w * channels)] & 0xFF;
                        green = data[1 + (x * channels) + (y * w * channels)] & 0xFF;
                        blue = data[2 + (x * channels) + (y * w * channels)] & 0xFF;
                        alpha = data[3 + (x * channels) + (y * w * channels)] & 0xFF;
                    }
                }

                outImage.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | (blue << 0));
            }
        }

        return outImage;
    }

    public static byte[] compressDXT5Block(byte[] data, int width, int height, int x, int y) {
        if (data.length != (width * height * 4)) {
            throw new IllegalArgumentException("Invalid amount of bytes, required " + (width * height * 4) + ", found " + data.length);
        }

        PixelStructure st = PixelUtils.getPixelStructure(width, height, 4, true);
        byte[] outputArray;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer pixels = stack.malloc(4 * 4 * 4);

            for (int yOffset = 0; yOffset < 4; yOffset++) {
                for (int xOffset = 0; xOffset < 4; xOffset++) {
                    for (int i = 0; i < 4; i++) {
                        pixels.put(
                                data[PixelUtils.getPixelComponentIndex(
                                        st, x + xOffset, y + yOffset, i
                                )]
                        );
                    }
                }
            }

            pixels.flip();

            ByteBuffer output = stack.malloc(16);
            stb_compress_dxt_block(
                    output,
                    pixels,
                    true,
                    STB_DXT_HIGHQUAL
            );

            outputArray = new byte[output.capacity()];
            output.get(outputArray);
        }

        return outputArray;
    }

    public static byte[] compressDXT5(byte[] data, int width, int height) {
        if (data.length != (width * height * 4)) {
            throw new IllegalArgumentException("Invalid amount of bytes, required " + (width * height * 4) + ", found " + data.length);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(65535);

        for (int blockY = 0; blockY < height; blockY += 4) {
            for (int blockX = 0; blockX < width; blockX += 4) {
                try {
                    out.write(compressDXT5Block(data, width, height, blockX, blockY));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        }

        return out.toByteArray();
    }

    public static void validate(int length, int width, int height, int components) {
        if (width < 0) {
            throw new IllegalArgumentException("Width is negative.");
        }
        if (height < 0) {
            throw new IllegalArgumentException("Height is negative.");
        }
        int requiredSize = width * height * components;
        if (length != requiredSize) {
            throw new IllegalArgumentException("Invalid data size! required " + requiredSize + " of length, found " + length + ".");
        }
    }

    public static void validate(byte[] data, int width, int height, int components) {
        if (data == null) {
            throw new NullPointerException("Image Data is Null.");
        }
        validate(data.length, width, height, components);
    }

    public static void writeRGBFloatEXR(float[] data, int width, int height, File output) throws IOException {
        validate(data.length, width, height, 3);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            EXRHeader header = EXRHeader.calloc(stack);
            EXRImage image = EXRImage.calloc(stack);
            
            InitEXRHeader(header);
            InitEXRImage(image);

            image.num_channels(3);
            
            FloatBuffer red = memAllocFloat(width * height);
            FloatBuffer green = memAllocFloat(width * height);
            FloatBuffer blue = memAllocFloat(width * height);
            
            try {
                for (int i = 0; i < width * height; i++) {
                    red.put(i, data[(i * 3) + 0]);
                    green.put(i, data[(i * 3) + 1]);
                    blue.put(i, data[(i * 3) + 2]);
                }

                image.images(stack.pointers(blue, green, red));
                image.width(width);
                image.height(height);
                
                header.num_channels(image.num_channels());
                
                EXRChannelInfo.Buffer channelInfos = EXRChannelInfo.calloc(header.num_channels(), stack);
                channelInfos.get(0).name(stack.ASCII("B"));
                channelInfos.get(1).name(stack.ASCII("G"));
                channelInfos.get(2).name(stack.ASCII("R"));
                header.channels(channelInfos);
                
                header.pixel_types(stack.callocInt(header.num_channels()));
                header.requested_pixel_types(stack.callocInt(header.num_channels()));
                for (int i = 0; i < header.num_channels(); i++) {
                    header.pixel_types().put(i, TINYEXR_PIXELTYPE_FLOAT);
                    header.requested_pixel_types().put(i, TINYEXR_PIXELTYPE_HALF);
                }

                PointerBuffer err = stack.callocPointer(1);
                int ret = SaveEXRImageToFile(image, header, output.getAbsolutePath(), err);
                if (ret != TINYEXR_SUCCESS) {
                    String errorString = err.getStringASCII(0);
                    FreeEXRErrorMessage(err.getByteBuffer(errorString.length() + 1));
                    throw new IOException("Code "+ret+" - Failed to write EXR File: " + errorString);
                }
            } finally {
                memFree(red);
                memFree(green);
                memFree(blue);
            }
        }
    }

    private ImageUtils() {

    }
}
