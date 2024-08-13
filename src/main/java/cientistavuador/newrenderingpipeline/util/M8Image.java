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

/**
 *
 * @author Cien
 */
public class M8Image {
    
    public static final float MAX_VALUE = 255f / 255f;
    public static final float MIN_VALUE = 1f / 255f;
    
    public static void rgbaToM8(byte[] rgba, int width, int height) {
        ImageUtils.validate(rgba, width, height, 4);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = (rgba[0 + (x * 4) + (y * width * 4)] & 0xFF) / 255f;
                float g = (rgba[1 + (x * 4) + (y * width * 4)] & 0xFF) / 255f;
                float b = (rgba[2 + (x * 4) + (y * width * 4)] & 0xFF) / 255f;
                
                int iR = 255;
                int iG = 255;
                int iB = 255;
                int iM = 0;
                encode:
                {
                    if (r < MIN_VALUE && g < MIN_VALUE && b < MIN_VALUE) {
                        break encode;
                    }
                    
                    float m = Math.max(Math.max(r, g), b);
                    
                    r /= m;
                    g /= m;
                    b /= m;
                    
                    iR = Math.min(Math.max(Math.round(r * 255f), 0), 255);
                    iG = Math.min(Math.max(Math.round(g * 255f), 0), 255);
                    iB = Math.min(Math.max(Math.round(b * 255f), 0), 255);
                    iM = Math.min(Math.max(Math.round(m * 255f), 0), 255);
                }
                
                rgba[0 + (x * 4) + (y * width * 4)] = (byte) iR;
                rgba[1 + (x * 4) + (y * width * 4)] = (byte) iG;
                rgba[2 + (x * 4) + (y * width * 4)] = (byte) iB;
                rgba[3 + (x * 4) + (y * width * 4)] = (byte) iM;
            }
        }
    }
    
    public static void m8ToRGBA(byte[] rgbm, int width, int height) {
        ImageUtils.validate(rgbm, width, height, 4);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int inputIndex = (x * 4) + (y * width * 4);
                int outputIndex = (x * 4) + (y * width * 4);
                
                float r = (rgbm[inputIndex + 0] & 0xFF) / 255f;
                float g = (rgbm[inputIndex + 1] & 0xFF) / 255f;
                float b = (rgbm[inputIndex + 2] & 0xFF) / 255f;
                float m = (rgbm[inputIndex + 3] & 0xFF) / 255f;
                
                r *= m;
                g *= m;
                b *= m;
                
                int iR = Math.min(Math.max(Math.round(r * 255f), 0), 255);
                int iG = Math.min(Math.max(Math.round(g * 255f), 0), 255);
                int iB = Math.min(Math.max(Math.round(b * 255f), 0), 255);
                int iA = 255;

                rgbm[0 + outputIndex] = (byte) iR;
                rgbm[1 + outputIndex] = (byte) iG;
                rgbm[2 + outputIndex] = (byte) iB;
                rgbm[3 + outputIndex] = (byte) iA;
            }
        }
    }
    
    public static M8Image createFromRGBA(byte[] rgba, int width, int height) {
        ImageUtils.validate(rgba, width, height, 4);
        
        byte[] rgbm = rgba.clone();
        rgbaToM8(rgba, width, height);
        return new M8Image(rgbm, width, height);
    }
    
    private final byte[] rgbm;
    private final int width;
    private final int height;

    public M8Image(byte[] rgbm, int width, int height) {
        ImageUtils.validate(rgbm, width, height, 4);

        this.rgbm = rgbm;
        this.width = width;
        this.height = height;
    }

    public byte[] getRGBM() {
        return rgbm;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] toRGBA() {
        byte[] copy = this.rgbm.clone();
        m8ToRGBA(copy, this.width, this.height);
        return copy;
    }
    
}
