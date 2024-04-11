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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 *
 * @author Cien
 */
public class MultiPNG {

    public static final float MAX_NUMBER_OF_PNGS = 16;

    public static class MultiPNGOutput {
        private final int width;
        private final int height;
        private final float[] data;

        public MultiPNGOutput(int width, int height, float[] data) {
            this.width = width;
            this.height = height;
            this.data = data;
        }

        public int width() {
            return width;
        }
        
        public int height() {
            return height;
        }
        
        public float[] data() {
            return data;
        }
        
    }
    
    public static void encode(float[] data, int width, int height, float precision, OutputStream output) throws IOException {
        new MultiPNG(data, width, height, precision, output).encode();
    }
    
    public static MultiPNGOutput decode(InputStream input) throws IOException {
        return new MultiPNG(input).decode();
    }
    
    private final StringBuilder comments = new StringBuilder();
    
    private final float precision;
    private final ZipInputStream input;
    private final ZipOutputStream output;

    private int imageWidth;
    private int imageHeight;

    private float[] aBuffer = null;
    private byte[] bBuffer = null;
    private float[] diffBuffer = null;

    private int currentPng;
    private float maxValue;
    private float minValue;

    private MultiPNG(float[] data, int width, int height, float precision, OutputStream output) {
        this.precision = precision;
        this.input = null;
        this.output = new ZipOutputStream(output, StandardCharsets.UTF_8);
        
        this.imageWidth = width;
        this.imageHeight = height;
        
        this.aBuffer = new float[(width * height) * 3];
        this.bBuffer = new byte[(width * height) * 3];
        this.diffBuffer = new float[(width * height) * 3];
        
        System.arraycopy(data, 0, this.aBuffer, 0, (width * height) * 3);
    }

    private MultiPNG(InputStream input) {
        this.precision = Float.NaN;
        this.input = new ZipInputStream(input, StandardCharsets.UTF_8);
        this.output = null;
    }
    
    private void findMaxMinValues() {
        this.maxValue = Float.NEGATIVE_INFINITY;
        this.minValue = Float.POSITIVE_INFINITY;
        for (int y = 0; y < this.imageHeight; y++) {
            for (int x = 0; x < this.imageWidth; x++) {
                for (int i = 0; i < 3; i++) {
                    float aValue = this.aBuffer[i + (x * 3) + (y * this.imageWidth * 3)];
                    this.maxValue = Math.max(
                            this.maxValue,
                            aValue
                    );
                    this.minValue = Math.min(
                            this.minValue,
                            aValue
                    );
                }
            }
        }
    }

    private void calculateBufferB() {
        for (int y = 0; y < this.imageHeight; y++) {
            for (int x = 0; x < this.imageWidth; x++) {
                for (int i = 0; i < 3; i++) {
                    float aValue = this.aBuffer[i + (x * 3) + (y * this.imageWidth * 3)];

                    aValue -= this.minValue;
                    aValue /= Math.abs(this.maxValue - this.minValue);
                    aValue *= 255f;

                    int bValue = Math.min(Math.max(Math.round(aValue), 0), 255);
                    this.bBuffer[i + (x * 3) + (y * this.imageWidth * 3)] = (byte) (bValue);
                }
            }
        }
    }

    private void outputBufferB() throws IOException {
        BufferedImage outputImage = new BufferedImage(this.imageWidth, this.imageHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < this.imageHeight; y++) {
            for (int x = 0; x < this.imageWidth; x++) {
                int index = (x * 3) + (y * this.imageWidth * 3);

                int r = this.bBuffer[0 + index] & 0xFF;
                int g = this.bBuffer[1 + index] & 0xFF;
                int b = this.bBuffer[2 + index] & 0xFF;

                int argb = 0xFF_00_00_00 | (r << 16) | (g << 8) | (b << 0);

                outputImage.setRGB(x, (this.imageHeight - 1) - y, argb);
            }
        }

        String filename
                = this.currentPng
                + "_"
                + Integer.toHexString(Float.floatToRawIntBits(this.minValue)).toUpperCase()
                + "_"
                + Integer.toHexString(Float.floatToRawIntBits(this.maxValue)).toUpperCase()
                + ".png";

        ZipEntry entry = new ZipEntry(filename);

        this.comments
            .append("PNG Index ").append(this.currentPng).append('\n')
            .append("Compressed in ").append(new Date(System.currentTimeMillis()).toString()).append('\n')
            .append("Min Value: ").append(String.format(Locale.US, "%.15f", this.minValue)).append('\n')
            .append("Max Value: ").append(String.format(Locale.US, "%.15f", this.maxValue)).append('\n')
            .append("Precision: ").append(String.format(Locale.US, "%.15f", Math.abs(this.maxValue - this.minValue) / 255f)).append("\n\n");

        this.output.putNextEntry(entry);
        ImageIO.write(outputImage, "PNG", this.output);
        this.output.closeEntry();
    }

    private void calculateBufferDiff() {
        for (int y = 0; y < this.imageHeight; y++) {
            for (int x = 0; x < this.imageWidth; x++) {
                for (int i = 0; i < 3; i++) {
                    int index = i + (x * 3) + (y * this.imageWidth * 3);

                    float aValue = this.aBuffer[index];

                    float bValue = ((int) this.bBuffer[index]) & 0xFF;
                    bValue /= 255f;
                    bValue *= Math.abs(this.maxValue - this.minValue);
                    bValue += this.minValue;

                    this.diffBuffer[index] = aValue - bValue;
                }
            }
        }
    }

    private void flipBuffers() {
        float[] aBufferStore = this.aBuffer;
        float[] diffBufferStore = this.diffBuffer;
        this.aBuffer = diffBufferStore;
        this.diffBuffer = aBufferStore;
    }

    private void encode() throws IOException {
        for (int i = 0; i < MAX_NUMBER_OF_PNGS; i++) {
            this.currentPng = i;
            findMaxMinValues();
            calculateBufferB();
            outputBufferB();
            calculateBufferDiff();
            flipBuffers();

            float calculatedPrecision = Math.abs(this.maxValue - this.minValue) / 255f;
            if (calculatedPrecision <= this.precision) {
                break;
            }
        }
        this.output.setComment(this.comments.toString());
        this.output.finish();
    }

    private void readEntry(ZipEntry entry, boolean first) throws IOException {
        String name = entry.getName().split(Pattern.quote("."))[0];
        String[] components = name.split(Pattern.quote("_"));

        float imageMinValue = Float.intBitsToFloat(((int) Long.parseLong(components[1], 16)));
        float imageMaxValue = Float.intBitsToFloat(((int) Long.parseLong(components[2], 16)));

        decodeImage(ImageIO.read(this.input), imageMinValue, imageMaxValue, first);
    }

    private float decodeValue(float value, float min, float max) {
        value /= 255f;
        value *= Math.abs(max - min);
        value += min;
        return value;
    }

    private void decodeImage(BufferedImage image, float min, float max, boolean first) {
        if (first) {
            this.imageWidth = image.getWidth();
            this.imageHeight = image.getHeight();
            this.diffBuffer = new float[this.imageWidth * this.imageHeight * 3];
        }

        for (int y = 0; y < this.imageHeight; y++) {
            for (int x = 0; x < this.imageWidth; x++) {
                int argb = image.getRGB(x, (this.imageHeight - 1) - y);
                
                float rValue = (argb >> 16) & 0xFF;
                float gValue = (argb >> 8) & 0xFF;
                float bValue = (argb >> 0) & 0xFF;
                
                int index = (x * 3) + (y * this.imageWidth * 3);
                
                this.diffBuffer[0 + index] += decodeValue(rValue, min, max);
                this.diffBuffer[1 + index] += decodeValue(gValue, min, max);
                this.diffBuffer[2 + index] += decodeValue(bValue, min, max);
            }
        }
    }

    private MultiPNGOutput outputImage() {
        return new MultiPNGOutput(this.imageWidth, this.imageHeight, this.diffBuffer.clone());
    }
    
    private MultiPNGOutput decode() throws IOException {
        boolean first = true;
        ZipEntry entry;
        while ((entry = this.input.getNextEntry()) != null) {
            readEntry(entry, first);
            if (first) {
                first = false;
            }
        }
        return outputImage();
    }
}
