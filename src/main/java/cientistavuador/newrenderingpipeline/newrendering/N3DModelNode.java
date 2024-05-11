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

import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/**
 *
 * @author Cien
 */
public class N3DModelNode {

    private N3DModelNode parent;
    
    private final String name;
    private final Matrix4f transformation = new Matrix4f();
    private final NGeometry[] geometries;
    private final N3DModelNode[] children;
    
    private Matrix4f totalTransformation = null;

    public N3DModelNode(String name, Matrix4fc transformation, NGeometry[] geometries, N3DModelNode[] children) {
        this.name = name;
        this.transformation.set(transformation);
        this.geometries = geometries.clone();
        this.children = children.clone();
        for (N3DModelNode child:this.children) {
            if (child.parent != null) {
                throw new IllegalArgumentException("Child already has a parent!");
            }
            child.parent = this;
        }
    }
    
    public N3DModelNode getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public Matrix4fc getTransformation() {
        return transformation;
    }

    public int getNumberOfGeometries() {
        return this.geometries.length;
    }
    
    public NGeometry getGeometry(int index) {
        return this.geometries[index];
    }

    public int getNumberOfChildren() {
        return this.children.length;
    }
    
    public N3DModelNode getChild(int index) {
        return this.children[index];
    }
    
    private void calculateTotalTransformation() {
        List<Matrix4fc> transformations = new ArrayList<>();
        
        N3DModelNode currentNode = this;
        do {
            transformations.add(currentNode.getTransformation());
        } while ((currentNode = currentNode.getParent()) != null);
        
        this.totalTransformation = new Matrix4f();
        for (int i = (transformations.size() - 1); i >= 0; i--) {
            this.totalTransformation.mul(transformations.get(i));
        }
    }

    public Matrix4fc getTotalTransformation() {
        if (this.totalTransformation == null) {
            calculateTotalTransformation();
        }
        return this.totalTransformation;
    }
    
    
}
