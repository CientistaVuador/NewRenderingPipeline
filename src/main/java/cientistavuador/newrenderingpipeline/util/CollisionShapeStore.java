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

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.CylinderCollisionShape;
import com.jme3.bullet.collision.shapes.GImpactCollisionShape;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.jme3.bullet.collision.shapes.infos.CompoundMesh;
import com.jme3.bullet.collision.shapes.infos.IndexedMesh;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.jme3.util.BufferUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 
 * 
 * @author Cien
 */
public class CollisionShapeStore {

    @SuppressWarnings("unchecked")
    public static <T extends CollisionShape> T writeAndReadBack(T shape) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(65535);
            encode(out, shape);

            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            return (T) decode(in);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    public static final long MAGIC_NUMBER = -986246985435411789L;
    
    public static final int COMPOUND_ID = 0;
    public static final int SPHERE_ID = 1;
    public static final int BOX_ID = 2;
    public static final int CYLINDER_ID = 3;
    public static final int CAPSULE_ID = 4;
    public static final int HULL_ID = 5;
    public static final int MESH_ID = 6;
    public static final int GIMPACT_ID = 7;

    private static final Map<Class<? extends CollisionShape>, Integer> shapeIds = new HashMap<>();

    static {
        shapeIds.put(CompoundCollisionShape.class, COMPOUND_ID);

        shapeIds.put(SphereCollisionShape.class, SPHERE_ID);
        shapeIds.put(BoxCollisionShape.class, BOX_ID);
        shapeIds.put(CylinderCollisionShape.class, CYLINDER_ID);
        shapeIds.put(CapsuleCollisionShape.class, CAPSULE_ID);

        shapeIds.put(HullCollisionShape.class, HULL_ID);
        shapeIds.put(MeshCollisionShape.class, MESH_ID);
        shapeIds.put(GImpactCollisionShape.class, GIMPACT_ID);
    }

    public static final int shapeId(CollisionShape shape) {
        Integer e = shapeIds.get(shape.getClass());
        if (e == null) {
            throw new UnsupportedOperationException("Unsupported shape: " + shape.getClass().toString());
        }
        return e;
    }

    public static void encode(OutputStream output, CollisionShape shape) throws IOException {
        new CollisionShapeStore(output, shape).encode();
    }

    public static CollisionShape decode(InputStream input) throws IOException {
        return new CollisionShapeStore(input).decode();
    }

    private final GZIPInputStream input;
    private final GZIPOutputStream output;

    private final DataInputStream dataInput;
    private final DataOutputStream dataOutput;

    private final CollisionShape shape;

    private CollisionShapeStore(OutputStream output, CollisionShape shape) throws IOException {
        this.input = null;
        this.output = new GZIPOutputStream(output, 8192);

        this.dataInput = null;
        this.dataOutput = new DataOutputStream(this.output);

        this.shape = shape;
    }

    private CollisionShapeStore(InputStream input) throws IOException {
        this.input = new GZIPInputStream(input, 8192);
        this.output = null;

        this.dataInput = new DataInputStream(this.input);
        this.dataOutput = null;

        this.shape = null;
    }

    private void writeShape(CollisionShape shape) throws IOException {
        switch (shapeId(shape)) {
            case COMPOUND_ID ->
                writeCompound((CompoundCollisionShape) shape);
            case SPHERE_ID ->
                writeSphere((SphereCollisionShape) shape);
            case BOX_ID ->
                writeBox((BoxCollisionShape) shape);
            case CYLINDER_ID ->
                writeCylinder((CylinderCollisionShape) shape);
            case CAPSULE_ID ->
                writeCapsule((CapsuleCollisionShape) shape);
            case HULL_ID ->
                writeHull((HullCollisionShape) shape);
            case MESH_ID ->
                writeMesh((MeshCollisionShape) shape);
            case GIMPACT_ID ->
                writeGImpact((GImpactCollisionShape) shape);
        }
    }

    private void writeGeneric(CollisionShape shape) throws IOException {
        DataOutputStream out = this.dataOutput;

        int id = shapeId(shape);

        if (id != COMPOUND_ID) {
            Vector3f scale = shape.getScale(null);
            out.writeFloat(scale.x);
            out.writeFloat(scale.y);
            out.writeFloat(scale.z);
        }

        out.writeBoolean(shape.isContactFilterEnabled());

        if (id != SPHERE_ID && id != CAPSULE_ID) {
            out.writeFloat(shape.getMargin());
        }
    }

    private void writeSphere(SphereCollisionShape sphere) throws IOException {
        DataOutputStream out = this.dataOutput;

        out.writeInt(SPHERE_ID);
        out.writeFloat(sphere.getRadius());

        writeGeneric(sphere);
    }

    private void writeBox(BoxCollisionShape box) throws IOException {
        DataOutputStream out = this.dataOutput;

        out.writeInt(BOX_ID);

        Vector3f halfExtents = box.getHalfExtents(null);
        out.writeFloat(halfExtents.x);
        out.writeFloat(halfExtents.y);
        out.writeFloat(halfExtents.z);

        writeGeneric(box);
    }

    private void writeCylinder(CylinderCollisionShape cylinder) throws IOException {
        DataOutputStream out = this.dataOutput;

        out.writeInt(CYLINDER_ID);

        Vector3f halfExtents = cylinder.getHalfExtents(null);
        out.writeFloat(halfExtents.x);
        out.writeFloat(halfExtents.y);
        out.writeFloat(halfExtents.z);
        out.writeInt(cylinder.getAxis());

        writeGeneric(cylinder);
    }

    private void writeCapsule(CapsuleCollisionShape capsule) throws IOException {
        DataOutputStream out = this.dataOutput;

        out.writeInt(CAPSULE_ID);

        out.writeFloat(capsule.getRadius());
        out.writeFloat(capsule.getHeight());
        out.writeInt(capsule.getAxis());

        writeGeneric(capsule);
    }

    private void writeHull(HullCollisionShape hull) throws IOException {
        DataOutputStream out = this.dataOutput;

        out.writeInt(HULL_ID);

        ByteArrayOutputStream meshBytes = new ByteArrayOutputStream();
        MeshStore.encode(hull.copyHullVertices(), 3, new int[0], meshBytes);
        byte[] bytes = meshBytes.toByteArray();
        out.writeInt(bytes.length);
        out.write(bytes);

        writeGeneric(hull);
    }

    private void writeMesh(MeshCollisionShape mesh) throws IOException {
        DataOutputStream out = this.dataOutput;

        out.writeInt(MESH_ID);

        CompoundMesh compound;
        try {
            Field f = mesh.getClass().getDeclaredField("nativeMesh");
            f.setAccessible(true);
            compound = (CompoundMesh) f.get(mesh);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException ex) {
            throw new RuntimeException(ex);
        }

        int numberOfMeshes = compound.countSubmeshes();
        out.writeInt(numberOfMeshes);

        for (int i = 0; i < numberOfMeshes; i++) {
            IndexedMesh m = compound.getSubmesh(i);

            FloatBuffer verticesBuffer = m.copyVertexPositions().flip();
            IntBuffer indicesBuffer = m.copyIndices().flip();

            float[] vertices = new float[verticesBuffer.remaining()];
            int[] indices = new int[indicesBuffer.remaining()];

            verticesBuffer.get(vertices);
            indicesBuffer.get(indices);

            ByteArrayOutputStream meshBytes = new ByteArrayOutputStream();
            MeshStore.encode(vertices, 3, indices, meshBytes);
            byte[] bytes = meshBytes.toByteArray();
            out.writeInt(bytes.length);
            out.write(bytes);
        }
        
        byte[] bvh = mesh.serializeBvh();
        out.writeInt(bvh.length);
        out.write(bvh);
        
        writeGeneric(mesh);
    }

    private void writeGImpact(GImpactCollisionShape gimpact) throws IOException {
        DataOutputStream out = this.dataOutput;

        out.writeInt(GIMPACT_ID);

        CompoundMesh compound;
        try {
            Field f = gimpact.getClass().getDeclaredField("nativeMesh");
            f.setAccessible(true);
            compound = (CompoundMesh) f.get(gimpact);
        } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException ex) {
            throw new RuntimeException(ex);
        }

        int numberOfMeshes = compound.countSubmeshes();
        out.writeInt(numberOfMeshes);

        for (int i = 0; i < numberOfMeshes; i++) {
            IndexedMesh m = compound.getSubmesh(i);

            FloatBuffer verticesBuffer = m.copyVertexPositions().flip();
            IntBuffer indicesBuffer = m.copyIndices().flip();

            float[] vertices = new float[verticesBuffer.remaining()];
            int[] indices = new int[indicesBuffer.remaining()];

            verticesBuffer.get(vertices);
            indicesBuffer.get(indices);

            ByteArrayOutputStream meshBytes = new ByteArrayOutputStream();
            MeshStore.encode(vertices, 3, indices, meshBytes);
            byte[] bytes = meshBytes.toByteArray();
            out.writeInt(bytes.length);
            out.write(bytes);
        }

        writeGeneric(gimpact);
    }

    private void writeCompound(CompoundCollisionShape compound) throws IOException {
        DataOutputStream out = this.dataOutput;

        out.writeInt(COMPOUND_ID);

        ChildCollisionShape[] children = compound.listChildren();
        out.writeInt(children.length);

        for (ChildCollisionShape c : children) {
            Transform transform = c.copyTransform(null);

            Vector3f scale = transform.getScale();
            Quaternion rotation = transform.getRotation();
            Vector3f translation = transform.getTranslation();

            out.writeFloat(translation.x);
            out.writeFloat(translation.y);
            out.writeFloat(translation.z);

            out.writeFloat(rotation.getX());
            out.writeFloat(rotation.getY());
            out.writeFloat(rotation.getZ());
            out.writeFloat(rotation.getW());

            out.writeFloat(scale.x);
            out.writeFloat(scale.y);
            out.writeFloat(scale.z);

            writeShape(c.getShape());
        }

        writeGeneric(compound);
    }

    private void encode() throws IOException {
        this.dataOutput.writeLong(MAGIC_NUMBER);
        writeShape(this.shape);

        this.dataOutput.flush();
        this.output.finish();
    }

    private CollisionShape readShape() throws IOException {
        DataInputStream in = this.dataInput;

        int shapeId = in.readInt();

        switch (shapeId) {
            case COMPOUND_ID -> {
                return readCompound();
            }
            case SPHERE_ID -> {
                return readSphere();
            }
            case BOX_ID -> {
                return readBox();
            }
            case CYLINDER_ID -> {
                return readCylinder();
            }
            case CAPSULE_ID -> {
                return readCapsule();
            }
            case HULL_ID -> {
                return readHull();
            }
            case MESH_ID -> {
                return readMesh();
            }
            case GIMPACT_ID -> {
                return readGImpact();
            }
            default ->
                throw new UnsupportedOperationException("Unknown shape id: " + shapeId);
        }
    }

    private void readGeneric(CollisionShape shape) throws IOException {
        DataInputStream in = this.dataInput;

        int id = shapeId(shape);

        if (id != COMPOUND_ID) {
            Vector3f scale = new Vector3f(
                    in.readFloat(),
                    in.readFloat(),
                    in.readFloat()
            );
            if (shape.canScale(scale)) {
                shape.setScale(scale);
            }
        }

        boolean contactFilter = in.readBoolean();
        shape.setContactFilterEnabled(contactFilter);

        if (id != SPHERE_ID && id != CAPSULE_ID) {
            float margin = in.readFloat();
            shape.setMargin(margin);
        }
        
    }

    private SphereCollisionShape readSphere() throws IOException {
        DataInputStream in = this.dataInput;

        SphereCollisionShape sphere = new SphereCollisionShape(in.readFloat());
        readGeneric(sphere);

        return sphere;
    }

    private BoxCollisionShape readBox() throws IOException {
        DataInputStream in = this.dataInput;

        BoxCollisionShape box = new BoxCollisionShape(new Vector3f(
                in.readFloat(),
                in.readFloat(),
                in.readFloat()
        ));
        readGeneric(box);

        return box;
    }

    private CylinderCollisionShape readCylinder() throws IOException {
        DataInputStream in = this.dataInput;

        CylinderCollisionShape cylinder = new CylinderCollisionShape(
                new Vector3f(in.readFloat(), in.readFloat(), in.readFloat()),
                in.readInt()
        );
        readGeneric(cylinder);

        return cylinder;
    }

    private CapsuleCollisionShape readCapsule() throws IOException {
        DataInputStream in = this.dataInput;

        CapsuleCollisionShape capsule = new CapsuleCollisionShape(
                in.readFloat(),
                in.readFloat(),
                in.readInt()
        );
        readGeneric(capsule);

        return capsule;
    }

    private HullCollisionShape readHull() throws IOException {
        DataInputStream in = this.dataInput;

        byte[] meshBytes = new byte[in.readInt()];
        in.readFully(meshBytes);
        MeshStore.MeshStoreOutput meshStore = MeshStore.decode(new ByteArrayInputStream(meshBytes));

        HullCollisionShape hull = new HullCollisionShape(
                meshStore.vertices()
        );
        readGeneric(hull);

        return hull;
    }

    private MeshCollisionShape readMesh() throws IOException {
        DataInputStream in = this.dataInput;

        IndexedMesh[] meshes = new IndexedMesh[in.readInt()];
        for (int i = 0; i < meshes.length; i++) {
            byte[] meshBytes = new byte[in.readInt()];
            in.readFully(meshBytes);
            MeshStore.MeshStoreOutput meshStore = MeshStore.decode(new ByteArrayInputStream(meshBytes));

            float[] vertices = meshStore.vertices();
            int[] indices = meshStore.indices();

            IndexedMesh indexed = new IndexedMesh(
                    BufferUtils.createFloatBuffer(vertices),
                    BufferUtils.createIntBuffer(indices)
            );

            meshes[i] = indexed;
        }
        
        byte[] bvhData = new byte[in.readInt()];
        in.readFully(bvhData);
        
        MeshCollisionShape mesh = new MeshCollisionShape(bvhData, meshes);
        readGeneric(mesh);

        return mesh;
    }

    private GImpactCollisionShape readGImpact() throws IOException {
        DataInputStream in = this.dataInput;

        IndexedMesh[] meshes = new IndexedMesh[in.readInt()];
        for (int i = 0; i < meshes.length; i++) {
            byte[] meshBytes = new byte[in.readInt()];
            in.readFully(meshBytes);
            MeshStore.MeshStoreOutput meshStore = MeshStore.decode(new ByteArrayInputStream(meshBytes));

            float[] vertices = meshStore.vertices();
            int[] indices = meshStore.indices();

            IndexedMesh indexed = new IndexedMesh(
                    BufferUtils.createFloatBuffer(vertices),
                    BufferUtils.createIntBuffer(indices)
            );

            meshes[i] = indexed;
        }

        GImpactCollisionShape gimpact = new GImpactCollisionShape(meshes);
        readGeneric(gimpact);

        return gimpact;
    }

    private CompoundCollisionShape readCompound() throws IOException {
        DataInputStream in = this.dataInput;

        CompoundCollisionShape compound = new CompoundCollisionShape();

        int amoundOfChildren = in.readInt();
        for (int i = 0; i < amoundOfChildren; i++) {
            Transform transform = new Transform(
                    new Vector3f(in.readFloat(), in.readFloat(), in.readFloat()),
                    new Quaternion(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat()),
                    new Vector3f(in.readFloat(), in.readFloat(), in.readFloat())
            );
            CollisionShape child = readShape();

            compound.addChildShape(child, transform);
        }

        readGeneric(compound);

        return compound;
    }

    private CollisionShape decode() throws IOException {
        DataInputStream in = this.dataInput;
        long magic = in.readLong();
        if (magic != MAGIC_NUMBER) {
            throw new UnsupportedOperationException("Unknown magic number: " + magic);
        }
        return readShape();
    }
}
