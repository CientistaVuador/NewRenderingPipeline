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

import java.awt.image.BufferedImage;
import java.util.Objects;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class RPImage {

    public static final RPImage NULL_IMAGE = new RPImage(new float[0], 0, 0);

    public static final int BIAS = 127;
    public static final double MINIMUM_BASE = Math.pow(32.0, 1.0 / (255 - BIAS));
    
    private final double base;
    private final byte[] data;
    private final int width;
    private final int height;

    public RPImage(double base, byte[] data, int width, int height) {
        Objects.requireNonNull(data, "data is null");

        int pixels = width * height;
        if (data.length / 4 != pixels) {
            throw new IllegalArgumentException("Invalid amount of pixels! required " + pixels + ", found " + (data.length / 4));
        }

        this.base = base;
        this.data = data;
        this.width = width;
        this.height = height;
    }

    public RPImage(float[] data, int width, int height) {
        Objects.requireNonNull(data, "data is null");

        int pixels = width * height;
        if (data.length / 3 != pixels) {
            throw new IllegalArgumentException("Invalid amount of pixels! required " + pixels + ", found " + (data.length / 4));
        }

        float maxValue = 0f;
        for (float f : data) {
            if (f < 0f || !Float.isFinite(f)) {
                throw new IllegalArgumentException("Image contains negative or invalid values.");
            }
            maxValue = Math.max(maxValue, f);
        }

        this.base = Math.max(Math.pow(maxValue, 1.0 / (255 - BIAS)), MINIMUM_BASE);

        this.data = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = data[0 + (x * 3) + (y * width * 3)];
                float g = data[1 + (x * 3) + (y * width * 3)];
                float b = data[2 + (x * 3) + (y * width * 3)];

                float intensity = Math.max(r, Math.max(g, b));

                if (intensity == 0f) {
                    this.data[0 + (x * 4) + (y * width * 4)] = 0;
                    this.data[1 + (x * 4) + (y * width * 4)] = 0;
                    this.data[2 + (x * 4) + (y * width * 4)] = 0;
                    this.data[3 + (x * 4) + (y * width * 4)] = 0;
                    continue;
                }

                double exponent = (Math.log(intensity) / Math.log(this.base)) + BIAS;
                int roundExponent = Math.min(Math.max((int) Math.ceil(exponent), 0), 255);

                float newIntensity = (float) Math.pow(this.base, roundExponent - BIAS);
                float invIntensity = 1f / newIntensity;

                r *= invIntensity;
                g *= invIntensity;
                b *= invIntensity;

                int mRed = Math.min(Math.max(Math.round(r * 255f), 0), 255);
                int mGreen = Math.min(Math.max(Math.round(g * 255f), 0), 255);
                int mBlue = Math.min(Math.max(Math.round(b * 255f), 0), 255);

                this.data[0 + (x * 4) + (y * width * 4)] = (byte) mRed;
                this.data[1 + (x * 4) + (y * width * 4)] = (byte) mGreen;
                this.data[2 + (x * 4) + (y * width * 4)] = (byte) mBlue;

                this.data[3 + (x * 4) + (y * width * 4)] = (byte) roundExponent;
            }
        }

        this.width = width;
        this.height = height;
    }

    public double getBase() {
        return base;
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

    public void sample(int x, int y, Vector3f outColor) {
        int index = (x * 4) + (y * this.width * 4);

        int mRed = this.data[0 + index] & 0xFF;
        int mGreen = this.data[1 + index] & 0xFF;
        int mBlue = this.data[2 + index] & 0xFF;
        int bExp = this.data[3 + index] & 0xFF;

        float r = mRed / 255f;
        float g = mGreen / 255f;
        float b = mBlue / 255f;
        float intensity = (float) Math.pow(this.base, bExp - BIAS);

        outColor.set(r, g, b).mul(intensity);
    }

    public float[] toFloatArray() {
        Vector3f color = new Vector3f();

        float[] array = new float[this.width * this.height * 3];

        for (int y = 0; y < this.height; y++) {
            for (int x = 0; x < this.width; x++) {
                sample(x, y, color);

                array[0 + (x * 3) + (y * this.width * 3)] = color.x();
                array[1 + (x * 3) + (y * this.width * 3)] = color.y();
                array[2 + (x * 3) + (y * this.width * 3)] = color.z();
            }
        }

        return array;
    }

    public BufferedImage toBufferedImage() {
        BufferedImage resultImage = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < resultImage.getHeight(); y++) {
            for (int x = 0; x < resultImage.getWidth(); x++) {
                int r = data[0 + (x * 4) + (y * resultImage.getWidth() * 4)] & 0xFF;
                int g = data[1 + (x * 4) + (y * resultImage.getWidth() * 4)] & 0xFF;
                int b = data[2 + (x * 4) + (y * resultImage.getWidth() * 4)] & 0xFF;
                int e = data[3 + (x * 4) + (y * resultImage.getWidth() * 4)] & 0xFF;
                
                int argb = (e << 24) | (r << 16) | (g << 8) | (b << 0);

                resultImage.setRGB(x, (resultImage.getHeight() - 1) - y, argb);
            }
        }

        return resultImage;
    }

}
