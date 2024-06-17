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

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.joml.Vector4f;
import static org.lwjgl.stb.STBImage.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryUtil.*;

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
    
    private ImageUtils() {
        
    }
}
