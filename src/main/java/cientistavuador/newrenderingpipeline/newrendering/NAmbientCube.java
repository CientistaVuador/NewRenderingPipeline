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

import java.util.Arrays;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class NAmbientCube {
    
    public static final NAmbientCube NULL_AMBIENT_CUBE = new NAmbientCube() {
        @Override
        public void setSide(int side, Vector3fc color) {
            throw new UnsupportedOperationException("Trying to set a side on null ambient cube.");
        }

        @Override
        public void setSide(int side, float r, float g, float b) {
            throw new UnsupportedOperationException("Trying to set a side on null ambient cube.");
        }

        @Override
        public void setLerp(NAmbientCube a, NAmbientCube b, float factor) {
            throw new UnsupportedOperationException("Trying to set a side on null ambient cube.");
        }
        
    };
    
    public static final int SIDES = 6;
    
    public static final int POSITIVE_X = 0;
    public static final int NEGATIVE_X = 1;
    
    public static final int POSITIVE_Y = 2;
    public static final int NEGATIVE_Y = 3;
    
    public static final int POSITIVE_Z = 4;
    public static final int NEGATIVE_Z = 5;
    
    public static final Vector3fc[] SIDE_DIRECTIONS = new Vector3fc[] {
        new Vector3f(1f, 0f, 0f),
        new Vector3f(-1f, 0f, 0f),
        new Vector3f(0f, 1f, 0f),
        new Vector3f(0f, -1f, 0f),
        new Vector3f(0f, 0f, 1f),
        new Vector3f(0f, 0f, -1f)
    };
    
    private final Vector3f[] sides = new Vector3f[SIDES];
    
    public NAmbientCube() {
        for (int i = 0; i < this.sides.length; i++) {
            this.sides[i] = new Vector3f(0f, 0f, 0f);
        }
    }
    
    public Vector3fc getSide(int side) {
        return this.sides[side];
    }
    
    public void setSide(int side, float r, float g, float b) {
        this.sides[side].set(r, g, b);
    }
    
    public void setSide(int side, Vector3fc color) {
        setSide(side, color.x(), color.y(), color.z());
    }
    
    public void setLerp(NAmbientCube a, NAmbientCube b, float factor) {
        for (int i = 0; i < this.sides.length; i++) {
            Vector3fc colorA = a.getSide(i);
            Vector3fc colorB = b.getSide(i);
            
            this.sides[i].set(colorA).lerp(colorB, factor);
        }
    }
    
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 43 * hash + Arrays.deepHashCode(this.sides);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NAmbientCube other = (NAmbientCube) obj;
        return Arrays.deepEquals(this.sides, other.sides);
    }
    
}
