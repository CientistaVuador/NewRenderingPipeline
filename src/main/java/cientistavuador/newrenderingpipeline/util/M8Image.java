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
    
    public static M8Image createFromRGB(byte[] rgb, int components, int width, int height) {
        if (components != 3 && components != 4) {
            throw new IllegalArgumentException("Components must be 3 or 4.");
        }
        ImageUtils.validate(rgb, width, height, components);

        byte[] rgbm = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = (rgb[0 + (x * components) + (y * width * components)] & 0xFF) / 255f;
                float g = (rgb[1 + (x * components) + (y * width * components)] & 0xFF) / 255f;
                float b = (rgb[2 + (x * components) + (y * width * components)] & 0xFF) / 255f;

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

                rgbm[0 + (x * 4) + (y * width * 4)] = (byte) iR;
                rgbm[1 + (x * 4) + (y * width * 4)] = (byte) iG;
                rgbm[2 + (x * 4) + (y * width * 4)] = (byte) iB;
                rgbm[3 + (x * 4) + (y * width * 4)] = (byte) iM;
            }
        }

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

    public byte[] toRGB(boolean withAlpha) {
        int components = 3;
        if (withAlpha) {
            components = 4;
        }

        byte[] rgb = new byte[this.width * this.height * components];

        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                int inputIndex = (x * 4) + (y * this.width * 4);
                int outputIndex = (x * components) + (y * this.width * components);

                float r = (this.rgbm[inputIndex + 0] & 0xFF) / 255f;
                float g = (this.rgbm[inputIndex + 1] & 0xFF) / 255f;
                float b = (this.rgbm[inputIndex + 2] & 0xFF) / 255f;
                float m = (this.rgbm[inputIndex + 3] & 0xFF) / 255f;
                
                r *= m;
                g *= m;
                b *= m;

                int iR = Math.min(Math.max(Math.round(r * 255f), 0), 255);
                int iG = Math.min(Math.max(Math.round(g * 255f), 0), 255);
                int iB = Math.min(Math.max(Math.round(b * 255f), 0), 255);
                int iA = 255;

                rgb[0 + outputIndex] = (byte) iR;
                rgb[1 + outputIndex] = (byte) iG;
                rgb[2 + outputIndex] = (byte) iB;

                if (withAlpha) {
                    rgb[3 + outputIndex] = (byte) iA;
                }
            }
        }

        return rgb;
    }

}
