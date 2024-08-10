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

/**
 *
 * @author Cien
 */
public class MipmapUtils {
    
    public static int mipmapSize(int size, int level) {
        if (size <= 0) {
            return 0;
        }
        if (level == 0) {
            return size;
        }
        if (level == 1) {
            return Math.max(size / 2, 1);
        }
        return (int) Math.max(size / Math.pow(2.0, level), 1);
    }
    
    public static int mipmapSize(int size) {
        return mipmapSize(size, 1);
    }
    
    public static int numberOfMipmaps(int width, int height) {
        return (int) Math.abs(Math.log(Math.max(width, height)) / Math.log(2.0)) + 1;
    }
    
    public static Pair<Pair<Integer, Integer>, byte[]> mipmap(
            byte[] data,
            int width, int height
    ) {
        ImageUtils.validate(data, width, height, 4);
        
        int mipWidth = mipmapSize(width);
        int mipHeight = mipmapSize(height);
        
        PixelStructure inSt = PixelUtils.getPixelStructure(width, height, 4, true);
        PixelStructure outSt = PixelUtils.getPixelStructure(mipWidth, mipHeight, 4, true);
        
        byte[] outMipmap = new byte[mipWidth * mipHeight * 4];
        
        for (int y = 0; y < mipHeight; y++) {
            for (int x = 0; x < mipWidth; x++) {
                int red = 0;
                int green = 0;
                int blue = 0;
                int alpha = 0;
                
                for (int yOffset = 0; yOffset < 2; yOffset++) {
                    for (int xOffset = 0; xOffset < 2; xOffset++) {
                        int totalX = (x * 2) + xOffset;
                        int totalY = (y * 2) + yOffset;
                        
                        red += data[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 0)] & 0xFF;
                        green += data[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 1)] & 0xFF;
                        blue += data[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 2)] & 0xFF;
                        alpha += data[PixelUtils.getPixelComponentIndex(inSt, totalX, totalY, 3)] & 0xFF;
                    }
                }
                
                red /= 4;
                green /= 4;
                blue /= 4;
                alpha /= 4;
                
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 0)] = (byte) red;
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 1)] = (byte) green;
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 2)] = (byte) blue;
                outMipmap[PixelUtils.getPixelComponentIndex(outSt, x, y, 3)] = (byte) alpha;
            }
        }
        
        return new Pair<>(
                new Pair<>(outSt.width(), outSt.height()),
                outMipmap
        );
    }
    
    private MipmapUtils() {
        
    }
    
}
