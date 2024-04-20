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

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public class NBoneAnimation {
    
    private final String boneName;
    
    private final int numberOfPositions;
    private final float[] positionTimes;
    private final float[] positions;
    
    private final int numberOfRotations;
    private final float[] rotationTimes;
    private final float[] rotations;
    
    private final int numberOfScalings;
    private final float[] scalingTimes;
    private final float[] scalings;
    
    public NBoneAnimation(
            String boneName,
            float[] positionTimes, float[] positions,
            float[] rotationTimes, float[] rotations,
            float[] scalingTimes, float[] scalings
    ) {
        this.boneName = boneName;
        
        this.numberOfPositions = positionTimes.length;
        if (positions.length != this.numberOfPositions * 3) {
            throw new IllegalArgumentException("Invalid amount of positions");
        }
        this.positionTimes = positionTimes.clone();
        this.positions = positions.clone();
        
        this.numberOfRotations = rotationTimes.length;
        if (rotations.length != this.numberOfRotations * 4) {
            throw new IllegalArgumentException("Invalid amount of rotations");
        }
        this.rotationTimes = rotationTimes.clone();
        this.rotations = rotations.clone();
        
        this.numberOfScalings = scalingTimes.length;
        if (scalings.length != this.numberOfScalings * 3) {
            throw new IllegalArgumentException("Invalid amount of scalings");
        }
        this.scalingTimes = scalingTimes.clone();
        this.scalings = scalings.clone();
    }
    
    public String getBoneName() {
        return boneName;
    }

    public int getNumberOfPositions() {
        return numberOfPositions;
    }

    public int getNumberOfRotations() {
        return numberOfRotations;
    }

    public int getNumberOfScalings() {
        return numberOfScalings;
    }
    
    public void getPosition(int index, Vector3f outPosition) {
        if (index < 0 || (index * 3) > this.positions.length) {
            throw new IndexOutOfBoundsException("Position index "+index+" out of bounds for length "+this.numberOfPositions);
        }
        outPosition.set(
                this.positions[(index * 3) + 0],
                this.positions[(index * 3) + 1],
                this.positions[(index * 3) + 2]
        );
    }
    
    public void getRotation(int index, Quaternionf outRotation) {
        if (index < 0 || (index * 4) > this.rotations.length) {
            throw new IndexOutOfBoundsException("Rotation index "+index+" out of bounds for length "+this.numberOfRotations);
        }
        outRotation.set(
                this.rotations[(index * 4) + 0],
                this.rotations[(index * 4) + 1],
                this.rotations[(index * 4) + 2],
                this.rotations[(index * 4) + 3]
        );
    }
    
    public void getScaling(int index, Vector3f outScale) {
        if (index < 0 || (index * 3) > this.scalings.length) {
            throw new IndexOutOfBoundsException("Scaling index "+index+" out of bounds for length "+this.numberOfScalings);
        }
        outScale.set(
                this.scalings[(index * 3) + 0],
                this.scalings[(index * 3) + 1],
                this.scalings[(index * 3) + 2]
        );
    }
    
    public float getPositionTime(int index) {
        return this.positionTimes[index];
    }
    
    public float getRotationTime(int index) {
        return this.rotationTimes[index];
    }
    
    public float getScalingTime(int index) {
        return this.scalingTimes[index];
    }
    
}
