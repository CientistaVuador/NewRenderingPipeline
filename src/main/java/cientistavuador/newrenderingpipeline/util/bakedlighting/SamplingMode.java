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
package cientistavuador.newrenderingpipeline.util.bakedlighting;

import java.awt.image.BufferedImage;

/**
 *
 * @author Cien
 */
public enum SamplingMode {
    SAMPLE_1("1.png"),
    SAMPLE_5("5.png"),
    SAMPLE_9("9.png"),
    SAMPLE_13("13.png"),
    SAMPLE_17("17.png"),
    SAMPLE_21("21.png"),
    SAMPLE_25("25.png"),
    SAMPLE_29("29.png"),
    SAMPLE_33("33.png"),
    SAMPLE_37("37.png"),
    SAMPLE_41("41.png"),
    SAMPLE_45("45.png"),
    SAMPLE_49("49.png"),
    SAMPLE_53("53.png"),
    SAMPLE_57("57.png"),
    SAMPLE_61("61.png"),
    SAMPLE_65("65.png"),
    SAMPLE_69("69.png"),
    SAMPLE_73("73.png"),
    SAMPLE_77("77.png"),
    SAMPLE_81("81.png"),
    SAMPLE_85("85.png"),
    SAMPLE_89("89.png"),
    SAMPLE_93("93.png"),
    SAMPLE_97("97.png"),
    SAMPLE_101("101.png"),
    SAMPLE_105("105.png"),
    SAMPLE_109("109.png"),
    SAMPLE_113("113.png"),
    SAMPLE_117("117.png"),
    SAMPLE_121("121.png"),
    SAMPLE_125("125.png"),
    SAMPLE_129("129.png"),
    SAMPLE_133("133.png"),
    SAMPLE_137("137.png"),
    SAMPLE_141("141.png"),
    SAMPLE_145("145.png"),
    SAMPLE_149("149.png"),
    SAMPLE_153("153.png"),
    SAMPLE_157("157.png"),
    SAMPLE_161("161.png"),
    SAMPLE_165("165.png"),
    SAMPLE_169("169.png"),
    SAMPLE_173("173.png"),
    SAMPLE_177("177.png"),
    SAMPLE_181("181.png"),
    SAMPLE_185("185.png"),
    SAMPLE_189("189.png"),
    SAMPLE_193("193.png"),
    SAMPLE_197("197.png"),
    SAMPLE_201("201.png"),
    SAMPLE_205("205.png"),
    SAMPLE_209("209.png"),
    SAMPLE_213("213.png"),
    SAMPLE_217("217.png"),
    SAMPLE_221("221.png"),
    SAMPLE_225("225.png"),
    SAMPLE_229("229.png"),
    SAMPLE_233("233.png"),
    SAMPLE_237("237.png"),
    SAMPLE_241("241.png"),
    SAMPLE_245("245.png"),
    SAMPLE_249("249.png"),
    SAMPLE_253("253.png"),
    SAMPLE_257("257.png"),
    ;
    
    private final float[] sampleLocations;
    private final BufferedImage image;

    private SamplingMode(String file) {
        this.sampleLocations = SamplingModeLoader.SAMPLES.get(file);
        this.image = SamplingModeLoader.SAMPLES_IMAGES.get(file);
    }

    public BufferedImage image() {
        return image;
    }
    
    public int numSamples() {
        return this.sampleLocations.length / 2;
    }

    public float sampleX(int sample) {
        return this.sampleLocations[(sample * 2) + 0];
    }

    public float sampleY(int sample) {
        return this.sampleLocations[(sample * 2) + 1];
    }

    @Override
    public String toString() {
        int samples = numSamples();
        if (samples == 1) {
            return samples+" Sample";
        }
        return samples+" Samples";
    }
    
}
