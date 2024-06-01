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

import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 *
 * @author Cien
 */
public abstract class NLight {
    
    private final String name;
    
    private final Vector3f diffuse = new Vector3f(1f);
    private final Vector3f specular = new Vector3f(1f);
    private final Vector3f ambient = new Vector3f(0.05f);
    
    public NLight(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public Vector3f getDiffuse() {
        return diffuse;
    }

    public Vector3f getSpecular() {
        return specular;
    }

    public Vector3f getAmbient() {
        return ambient;
    }
    
    public static class NDirectionalLight extends NLight {
        
        private final Vector3f direction = new Vector3f(0f, -1f, -0.5f).normalize();
        
        public NDirectionalLight(String name) {
            super(name);
        }

        public Vector3f getDirection() {
            return direction;
        }
        
    }
    
    public static class NPointLight extends NLight {
        
        private final Vector3d position = new Vector3d();
        
        public NPointLight(String name) {
            super(name);
        }

        public Vector3d getPosition() {
            return position;
        }
        
    }
    
    public static class NSpotLight extends NLight {
        private final Vector3d position = new Vector3d();
        private final Vector3f direction = new Vector3f();
        
        private float innerCone = (float) Math.cos(Math.toRadians(25f));
        private float outerCone = (float) Math.cos(Math.toRadians(65f));
        
        public NSpotLight(String name) {
            super(name);
        }

        public Vector3d getPosition() {
            return position;
        }

        public Vector3f getDirection() {
            return direction;
        }

        public float getInnerCone() {
            return innerCone;
        }

        public void setInnerCone(float innerCone) {
            this.innerCone = innerCone;
        }

        public float getOuterCone() {
            return outerCone;
        }

        public void setOuterCone(float outerCone) {
            this.outerCone = outerCone;
        }
        
    }
    
}
