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

import cientistavuador.newrenderingpipeline.Main;
import cientistavuador.newrenderingpipeline.util.ObjectCleaner;
import com.jme3.math.Quaternion;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33C.*;

/**
 *
 * @author Cien
 */
public class N3DObject {
    
    private static class WrappedQueryObject {
        private int object = 0;
    }
    
    private final String name;
    private final N3DModel n3DModel;
    
    private final Vector3d position = new Vector3d(0.0, 0.0, 0.0);
    private final Quaternion rotation = new Quaternion();
    private final Vector3f scale = new Vector3f();
    private final Matrix4f transformation = new Matrix4f();
    private boolean billboardEnabled = false;
    
    private final Matrix4f model = new Matrix4f();
    private final Vector3d hintPosition = new Vector3d(0.0, 0.0, 0.0);
    
    private final WrappedQueryObject queryObject = new WrappedQueryObject();
    
    private NAnimator animator = null;
    
    public N3DObject(String name, N3DModel n3DModel) {
        this.name = name;
        this.n3DModel = n3DModel;
        
        registerForCleaning();
    }
    
    private void registerForCleaning() {
        final WrappedQueryObject finalWrapped = this.queryObject;
        
        ObjectCleaner.get().register(this, () -> {
            Main.MAIN_TASKS.add(() -> {
                int obj = finalWrapped.object;
                if (obj != 0) {
                    glDeleteQueries(obj);
                }
                finalWrapped.object = 0;
            });
        });
    }
    
    public String getName() {
        return name;
    }
    
    public N3DModel getN3DModel() {
        return n3DModel;
    }

    public Vector3d getPosition() {
        return position;
    }

    public Quaternion getRotation() {
        return rotation;
    }

    public Vector3f getScale() {
        return scale;
    }

    public Matrix4f getTransformation() {
        return transformation;
    }

    public boolean isBillboardEnabled() {
        return billboardEnabled;
    }

    public void setBillboardEnabled(boolean billboardEnabled) {
        this.billboardEnabled = billboardEnabled;
    }
    
    public Matrix4f getModel() {
        return model;
    }

    public Vector3d getHintPosition() {
        return hintPosition;
    }
    
    public int getQueryObject() {
        if (this.queryObject.object == 0) {
            this.queryObject.object = glGenQueries();
        }
        return this.queryObject.object;
    }
    
    public void transformAabb(Vector3f outMin, Vector3f outMax) {
        this.model.transformAab(
                this.n3DModel.getAabbMin(), this.n3DModel.getAabbMax(),
                outMin, outMax
        );
    }
    
    public void transformAnimatedAabb(Vector3f outMin, Vector3f outMax) {
        this.model.transformAab(
                this.n3DModel.getAnimatedAabbMin(), this.n3DModel.getAnimatedAabbMax(),
                outMin, outMax
        );
    }
    
    public NAnimator getAnimator() {
        return animator;
    }

    public void setAnimator(NAnimator animator) {
        this.animator = animator;
    }
    
}
