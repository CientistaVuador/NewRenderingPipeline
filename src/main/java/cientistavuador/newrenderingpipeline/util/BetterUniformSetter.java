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

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix2f;
import org.joml.Matrix2fc;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4f;
import org.joml.Vector4i;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.system.MemoryStack;

/**
 *
 * @author Cien
 */
public class BetterUniformSetter {

    public static void uniformMatrix2fv(int location, Matrix2fc matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrixBuffer = stack.mallocFloat(2 * 2);
            matrix.get(matrixBuffer);
            glUniformMatrix2fv(location, false, matrixBuffer);
        }
    }
    
    public static void uniformMatrix3fv(int location, Matrix3fc matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrixBuffer = stack.mallocFloat(3 * 3);
            matrix.get(matrixBuffer);
            glUniformMatrix3fv(location, false, matrixBuffer);
        }
    }
    
    public static void uniformMatrix4fv(int location, Matrix4fc matrix) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer matrixBuffer = stack.mallocFloat(4 * 4);
            matrix.get(matrixBuffer);
            glUniformMatrix4fv(location, false, matrixBuffer);
        }
    }
    
    private final int program;
    private final List<String> uniforms = new ArrayList<>();
    private final Map<String, Integer> locations = new HashMap<>();
    private final Map<Integer, Object> values = new HashMap<>();
    
    public BetterUniformSetter(int program) {
        this.program = program;
        
        int uniformsLength = glGetProgrami(program, GL_ACTIVE_UNIFORMS);
        for (int i = 0; i < uniformsLength; i++) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                String uniform = glGetActiveUniform(program, i, stack.callocInt(1), stack.callocInt(1));
                this.uniforms.add(uniform);
                this.locations.put(uniform, glGetUniformLocation(program, uniform));
            }
        }
    }

    public int getProgram() {
        return program;
    }

    public String[] getUniforms() {
        return uniforms.toArray(String[]::new);
    }
    
    public int locationOf(String uniform) {
        Integer e = this.locations.get(uniform);
        if (e == null) {
            int lookup = glGetUniformLocation(this.program, uniform);
            this.uniforms.add(uniform);
            this.locations.put(uniform, lookup);
            return lookup;
        }
        return e;
    }
    
    public BetterUniformSetter uniform1f(String uniform, float f1, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Float current = (Float) this.values.get(location);
        if (current != null && current == f1 && !force) {
            return this;
        }
        this.values.put(location, f1);
        glUniform1f(location, f1);
        return this;
    }
    
    public BetterUniformSetter uniform2f(String uniform, float f1, float f2, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Vector2f current = (Vector2f) this.values.get(location);
        if (current != null && current.equals(f1, f2) && !force) {
            return this;
        }
        if (current == null) {
            current = new Vector2f();
            this.values.put(location, current);
        }
        current.set(f1, f2);
        glUniform2f(location, f1, f2);
        return this;
    }
    
    public BetterUniformSetter uniform3f(String uniform, float f1, float f2, float f3, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Vector3f current = (Vector3f) this.values.get(location);
        if (current != null && current.equals(f1, f2, f3) && !force) {
            return this;
        }
        if (current == null) {
            current = new Vector3f();
            this.values.put(location, current);
        }
        current.set(f1, f2, f3);
        glUniform3f(location, f1, f2, f3);
        return this;
    }
    
    public BetterUniformSetter uniform4f(String uniform, float f1, float f2, float f3, float f4, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Vector4f current = (Vector4f) this.values.get(location);
        if (current != null && current.equals(f1, f2, f3, f4) && !force) {
            return this;
        }
        if (current == null) {
            current = new Vector4f();
            this.values.put(location, current);
        }
        current.set(f1, f2, f3, f4);
        glUniform4f(location, f1, f2, f3, f4);
        return this;
    }
    
    public BetterUniformSetter uniform1i(String uniform, int i1, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Integer current = (Integer) this.values.get(location);
        if (current != null && current == i1 && !force) {
            return this;
        }
        this.values.put(location, i1);
        glUniform1i(location, i1);
        return this;
    }
    
    public BetterUniformSetter uniform2i(String uniform, int i1, int i2, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Vector2i current = (Vector2i) this.values.get(location);
        if (current != null && current.equals(i1, i2) && !force) {
            return this;
        }
        if (current == null) {
            current = new Vector2i();
            this.values.put(location, current);
        }
        current.set(i1, i2);
        glUniform2i(location, i1, i2);
        return this;
    }
    
    public BetterUniformSetter uniform3i(String uniform, int i1, int i2, int i3, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Vector3i current = (Vector3i) this.values.get(location);
        if (current != null && current.equals(i1, i2, i3) && !force) {
            return this;
        }
        if (current == null) {
            current = new Vector3i();
            this.values.put(location, current);
        }
        current.set(i1, i2, i3);
        glUniform3i(location, i1, i2, i3);
        return this;
    }
    
    public BetterUniformSetter uniform4i(String uniform, int i1, int i2, int i3, int i4, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Vector4i current = (Vector4i) this.values.get(location);
        if (current != null && current.equals(i1, i2, i3, i4) && !force) {
            return this;
        }
        if (current == null) {
            current = new Vector4i();
            this.values.put(location, current);
        }
        current.set(i1, i2, i3, i4);
        glUniform4i(location, i1, i2, i3, i4);
        return this;
    }
    
    public BetterUniformSetter uniformMatrix2fv(String uniform, Matrix2fc matrix, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Matrix2f current = (Matrix2f) this.values.get(location);
        if (current != null && current.equals(matrix) && !force) {
            return this;
        }
        if (current == null) {
            current = new Matrix2f();
            this.values.put(location, current);
        }
        current.set(matrix);
        uniformMatrix2fv(location, matrix);
        return this;
    }
    
    public BetterUniformSetter uniformMatrix3fv(String uniform, Matrix3fc matrix, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Matrix3f current = (Matrix3f) this.values.get(location);
        if (current != null && current.equals(matrix) && !force) {
            return this;
        }
        if (current == null) {
            current = new Matrix3f();
            this.values.put(location, current);
        }
        current.set(matrix);
        uniformMatrix3fv(location, matrix);
        return this;
    }
    
    public BetterUniformSetter uniformMatrix4fv(String uniform, Matrix4fc matrix, boolean force) {
        int location = locationOf(uniform);
        if (location == -1) {
            return this;
        }
        Matrix4f current = (Matrix4f) this.values.get(location);
        if (current != null && current.equals(matrix) && !force) {
            return this;
        }
        if (current == null) {
            current = new Matrix4f();
            this.values.put(location, current);
        }
        current.set(matrix);
        uniformMatrix4fv(location, matrix);
        return this;
    }
    
    public BetterUniformSetter uniform1f(String uniform, float f1) {
        return uniform1f(uniform, f1, false);
    }
    
    public BetterUniformSetter uniform2f(String uniform, float f1, float f2) {
        return uniform2f(uniform, f1, f2, false);
    }
    
    public BetterUniformSetter uniform3f(String uniform, float f1, float f2, float f3) {
        return uniform3f(uniform, f1, f2, f3, false);
    }
    
    public BetterUniformSetter uniform4f(String uniform, float f1, float f2, float f3, float f4) {
        return uniform4f(uniform, f1, f2, f3, f4, false);
    }
    
    public BetterUniformSetter uniform1i(String uniform, int i1) {
        return uniform1i(uniform, i1, false);
    }
    
    public BetterUniformSetter uniform2i(String uniform, int i1, int i2) {
        return uniform2i(uniform, i1, i2, false);
    }
    
    public BetterUniformSetter uniform3i(String uniform, int i1, int i2, int i3) {
        return uniform3i(uniform, i1, i2, i3, false);
    }
    
    public BetterUniformSetter uniform4i(String uniform, int i1, int i2, int i3, int i4) {
        return uniform4i(uniform, i1, i2, i3, i4, false);
    }
    
    public BetterUniformSetter uniformMatrix2fv(String uniform, Matrix2fc matrix) {
        return uniformMatrix2fv(uniform, matrix, false);
    }
    
    public BetterUniformSetter uniformMatrix3fv(String uniform, Matrix3fc matrix) {
        return uniformMatrix3fv(uniform, matrix, false);
    }
    
    public BetterUniformSetter uniformMatrix4fv(String uniform, Matrix4fc matrix) {
        return uniformMatrix4fv(uniform, matrix, false);
    }
}
