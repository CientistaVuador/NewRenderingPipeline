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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 *
 * @author Cien
 */
public class NAnimationStore {
    
    public static final long MAGIC_NUMBER = 1251136182124926237L;
    
    private static void writeString(String s, ObjectOutputStream out) throws IOException {
        byte[] stringBytes = s.getBytes(StandardCharsets.UTF_8);
        
        out.writeInt(stringBytes.length);
        out.write(stringBytes);
    }
    
    private static void writeInterleaved(float[] array, int lineSize, ObjectOutputStream out) throws IOException {
        out.writeInt(array.length);
        out.writeInt(lineSize);
        
        for (int i = 0; i < lineSize; i++) {
            for (int j = 0; j < array.length; j += lineSize) {
                out.writeFloat(array[j + i]);
            }
        }
    }
    
    private static void writeBoneAnimation(NBoneAnimation boneAnimation, ObjectOutputStream out) throws IOException {
        writeString(boneAnimation.getBoneName(), out);
        
        writeInterleaved(boneAnimation.getPositionTimes(), 1, out);
        writeInterleaved(boneAnimation.getRotationTimes(), 1, out);
        writeInterleaved(boneAnimation.getScalingTimes(), 1, out);
        
        writeInterleaved(boneAnimation.getPositions(), NBoneAnimation.POSITION_COMPONENTS, out);
        writeInterleaved(boneAnimation.getRotations(), NBoneAnimation.ROTATION_COMPONENTS, out);
        writeInterleaved(boneAnimation.getScalings(), NBoneAnimation.SCALING_COMPONENTS, out);
    }
    
    public static void writeAnimation(NAnimation animation, OutputStream output) throws IOException {
        GZIPOutputStream compressedOutput = new GZIPOutputStream(output);
        ObjectOutputStream out = new ObjectOutputStream(compressedOutput);
        
        out.writeLong(MAGIC_NUMBER);
        
        writeString(animation.getName(), out);
        out.writeFloat(animation.getDuration());
        
        out.writeInt(animation.getNumberOfBoneAnimations());
        for (int i = 0; i < animation.getNumberOfBoneAnimations(); i++) {
            NBoneAnimation boneAnimation = animation.getBoneAnimation(i);
            writeBoneAnimation(boneAnimation, out);
        }
        
        out.flush();
        compressedOutput.finish();
    }
    
    private static String readString(ObjectInputStream in) throws IOException {
        int arraySize = in.readInt();
        byte[] stringBytes = in.readNBytes(arraySize);
        
        return new String(stringBytes, StandardCharsets.UTF_8);
    }
    
    private static float[] readInterleaved(ObjectInputStream in) throws IOException {
        int arraySize = in.readInt();
        int lineSize = in.readInt();
        
        float[] array = new float[arraySize];
        
        for (int i = 0; i < lineSize; i++) {
            for (int j = 0; j < arraySize; j += lineSize) {
                array[j + i] = in.readFloat();
            }
        }
        
        return array;
    }
    
    private static NBoneAnimation readBoneAnimation(ObjectInputStream in) throws IOException {
        String boneName = readString(in);
        
        float[] positionTimes = readInterleaved(in);
        float[] rotationTimes = readInterleaved(in);
        float[] scalingTimes = readInterleaved(in);
        
        float[] positions = readInterleaved(in);
        float[] rotations = readInterleaved(in);
        float[] scalings = readInterleaved(in);
        
        return new NBoneAnimation(boneName, positionTimes, positions, rotationTimes, rotations, scalingTimes, scalings);
    }
    
    public static NAnimation readAnimation(InputStream input) throws IOException {
        GZIPInputStream compressedInput = new GZIPInputStream(input);
        ObjectInputStream in = new ObjectInputStream(compressedInput);
        
        long magicNumber = in.readLong();
        if (magicNumber != MAGIC_NUMBER) {
            throw new IllegalArgumentException("Invalid magic number, expected "+MAGIC_NUMBER+", found: "+magicNumber);
        }
        
        String animationName = readString(in);
        float duration = in.readFloat();
        
        List<NBoneAnimation> boneAnimations = new ArrayList<>();
        
        int numberOfBoneAnimations = in.readInt();
        for (int i = 0; i < numberOfBoneAnimations; i++) {
            boneAnimations.add(readBoneAnimation(in));
        }
        
        return new NAnimation(animationName, duration, boneAnimations.toArray(NBoneAnimation[]::new));
    }
    
    private NAnimationStore() {
        
    }
    
}
