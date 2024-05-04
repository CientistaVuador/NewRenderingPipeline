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
package cientistavuador.newrenderingpipeline;

import cientistavuador.newrenderingpipeline.camera.FreeCamera;
import cientistavuador.newrenderingpipeline.physics.PlayerController;
import cientistavuador.newrenderingpipeline.debug.AabRender;
import cientistavuador.newrenderingpipeline.debug.LineRender;
import cientistavuador.newrenderingpipeline.geometry.Geometries;
import cientistavuador.newrenderingpipeline.geometry.GeometriesLoader;
import cientistavuador.newrenderingpipeline.geometry.Geometry;
import cientistavuador.newrenderingpipeline.newrendering.N3DModel;
import cientistavuador.newrenderingpipeline.newrendering.N3DModelImporter;
import cientistavuador.newrenderingpipeline.newrendering.N3DModelNode;
import cientistavuador.newrenderingpipeline.newrendering.N3DObject;
import cientistavuador.newrenderingpipeline.newrendering.N3DObjectRenderer;
import cientistavuador.newrenderingpipeline.newrendering.NAnimator;
import cientistavuador.newrenderingpipeline.newrendering.NCubemap;
import cientistavuador.newrenderingpipeline.newrendering.NCubemapIO;
import cientistavuador.newrenderingpipeline.newrendering.NGeometry;
import cientistavuador.newrenderingpipeline.newrendering.NLight;
import cientistavuador.newrenderingpipeline.newrendering.NMaterial;
import cientistavuador.newrenderingpipeline.newrendering.NMesh;
import cientistavuador.newrenderingpipeline.newrendering.NTextures;
import cientistavuador.newrenderingpipeline.newrendering.NTexturesIO;
import cientistavuador.newrenderingpipeline.popups.BakePopup;
import cientistavuador.newrenderingpipeline.resources.mesh.MeshConfiguration;
import cientistavuador.newrenderingpipeline.resources.mesh.MeshData;
import cientistavuador.newrenderingpipeline.shader.GeometryProgram;
import cientistavuador.newrenderingpipeline.text.GLFontRenderer;
import cientistavuador.newrenderingpipeline.text.GLFontSpecification;
import cientistavuador.newrenderingpipeline.text.GLFontSpecifications;
import cientistavuador.newrenderingpipeline.texture.Textures;
import cientistavuador.newrenderingpipeline.ubo.CameraUBO;
import cientistavuador.newrenderingpipeline.ubo.UBOBindingPoints;
import cientistavuador.newrenderingpipeline.util.CollisionShapeStore;
import cientistavuador.newrenderingpipeline.util.LightmapFile;
import cientistavuador.newrenderingpipeline.util.MeshUtils;
import cientistavuador.newrenderingpipeline.util.bakedlighting.BakedLighting;
import cientistavuador.newrenderingpipeline.util.raycast.RayResult;
import cientistavuador.newrenderingpipeline.util.bakedlighting.SamplingMode;
import cientistavuador.newrenderingpipeline.util.bakedlighting.Scene;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.HullCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.objects.infos.RigidBodyMotionState;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import org.lwjgl.opengl.GL42C;

/**
 *
 * @author Cien
 */
public class Game {

    private static final Game GAME = new Game();

    public static Game get() {
        return GAME;
    }

    private final FreeCamera camera = new FreeCamera();
    private final List<RayResult> rays = new ArrayList<>();
    private final Scene scene = new Scene();

    private final Map<Geometry, LightmapFile.LightmapData> geometryLightmaps = new HashMap<>();

    private final BakedLighting.BakedLightingOutput writeToTexture = new BakedLighting.BakedLightingOutput() {
        private Geometry geometry = null;
        private MeshData.LightmapMesh mesh = null;
        private int lightmapSize = 0;
        private String[] groups = null;
        private int texture = 0;
        private int count = 0;
        private float[][] lightmaps = null;

        @Override
        public void prepare(Geometry geometry, MeshData.LightmapMesh mesh, int lightmapSize, String[] groups) {
            this.geometry = geometry;
            this.mesh = mesh;
            this.lightmapSize = lightmapSize;
            this.groups = groups;
            this.count = groups.length;
            this.lightmaps = new float[groups.length][];

            this.texture = glGenTextures();
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D_ARRAY, this.texture);

            if (Main.isSupported(4, 2)) {
                GL42C.glTexStorage3D(GL_TEXTURE_2D_ARRAY, 1, GL_RGB9_E5, this.lightmapSize, this.lightmapSize, this.groups.length);
            } else {
                glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGB9_E5, this.lightmapSize, this.lightmapSize, this.groups.length, 0, GL_RGBA, GL_FLOAT, 0);
            }

            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_LINEAR);

            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        }

        @Override
        public void write(float[] lightmap, int groupIndex) {
            this.lightmaps[groupIndex] = lightmap;

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D_ARRAY, this.texture);

            glTexSubImage3D(
                    GL_TEXTURE_2D_ARRAY, 0,
                    0, 0, groupIndex,
                    this.lightmapSize, this.lightmapSize, 1,
                    GL_RGB, GL_FLOAT, lightmap);

            glBindTexture(GL_TEXTURE_2D_ARRAY, 0);

            this.count--;
            if (this.count == 0) {
                this.geometry.setLightmapTextureHint(this.texture);
                this.geometry.setLightmapMesh(this.mesh);

                LightmapFile.Lightmap[] lightmaps = new LightmapFile.Lightmap[this.groups.length];
                for (int i = 0; i < lightmaps.length; i++) {
                    lightmaps[i] = new LightmapFile.Lightmap(this.groups[groupIndex], this.lightmaps[groupIndex]);
                }

                Game.this.geometryLightmaps.put(this.geometry, new LightmapFile.LightmapData(
                        this.mesh.getPixelToWorldRatio(),
                        this.mesh.getScaleX(),
                        this.mesh.getScaleY(),
                        this.mesh.getScaleZ(),
                        this.lightmapSize,
                        lightmaps
                ));
            }
        }
    };

    private BakedLighting.Status status = BakedLighting.dummyStatus();
    private float interiorIntensity = 1f;
    private float sunIntensity = 1f;
    private boolean interiorEnabled = true;
    private boolean sunEnabled = true;

    private boolean bakeWindowOpen = false;
    private final AtomicBoolean saveLightmapProcessing = new AtomicBoolean(false);

    private final PhysicsSpace physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
    private final List<PhysicsRigidBody> rigidBodies = new ArrayList<>();
    private final Scene.DirectionalLight sun = new Scene.DirectionalLight();
    private final SphereCollisionShape sphereShape = new SphereCollisionShape((0.35f / 2f) * Main.TO_PHYSICS_ENGINE_UNITS);

    private Geometry stairGeometry = null;
    private final Geometry monkeyGeometry = new Geometry(Geometries.MONKEY);
    private final MeshData monkeyCollisionMesh;
    private final CollisionShape monkeyShape;
    private final CollisionShape ciencolaShape;

    {
        CollisionShape compound;

        /*Vhacd4Parameters parameters = new Vhacd4Parameters();
        
        parameters.setFindBestPlane(true);
        parameters.setMaxHulls(6);
        parameters.setVolumePercentError(10);
        
        compound = MeshUtils.createConvexCollisionShapeFromMeshes(
        new float[][]{this.monkeyGeometry.getMesh().getVertices()},
        new int[][]{this.monkeyGeometry.getMesh().getIndices()},
        new Matrix4fc[]{new Matrix4f()},
        MeshData.SIZE,
        MeshData.XYZ_OFFSET,
        parameters
        );
        
        try {
        CollisionShapeStore.encode(new FileOutputStream("monkey.collision"), compound);
        } catch (IOException ex) {
        throw new UncheckedIOException(ex);
        }*/
        try {
            try (InputStream stored = Geometries.class.getResourceAsStream("monkey.collision")) {
                compound = CollisionShapeStore.decode(stored);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        this.monkeyShape = compound;
        this.monkeyCollisionMesh = MeshUtils.createMeshFromCollisionShape("monkeyCollision", compound);
    }

    {
        MeshData ciencola = Geometries.CIENCOLA;

        this.ciencolaShape = MeshUtils.cylinderCollisionFromVertices(
                ciencola.getVertices(), MeshData.SIZE, MeshData.XYZ_OFFSET,
                0f, 0f, 0f,
                1
        );
    }

    private final NTextures textures;

    {
        try {
            textures = NTexturesIO.loadFromJar(
                    "cientistavuador/newrenderingpipeline/resources/image/diffuse.jpg",
                    "cientistavuador/newrenderingpipeline/resources/image/ao.jpg",
                    "cientistavuador/newrenderingpipeline/resources/image/height.jpg",
                    "cientistavuador/newrenderingpipeline/resources/image/invertedexponent.jpg",
                    "cientistavuador/newrenderingpipeline/resources/image/normal.jpg",
                    null,//"cientistavuador/newrenderingpipeline/resources/image/reflectiveness.jpg",
                    null
            );
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private final NMesh mesh;

    {
        float[] vertices = new float[]{
            0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            20f, 0f, 0f, 10f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            20f, 0f, -20f, 10f, 10f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            20f, 0f, -20f, 10f, 10f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
            0f, 0f, -20f, 0f, 10f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f
        };
        int[] indices = new int[]{
            0, 1, 2,
            3, 4, 5
        };

        MeshUtils.generateTangent(
                vertices,
                NMesh.VERTEX_SIZE,
                NMesh.OFFSET_POSITION_XYZ,
                NMesh.OFFSET_TEXTURE_XY,
                NMesh.OFFSET_TANGENT_XYZ
        );

        mesh = new NMesh(
                "newmesh",
                vertices,
                indices,
                null
        );
    }

    private final N3DObject groundObj = new N3DObject("ground", new N3DModel(
            "ground",
            new N3DModelNode(
                    "ground",
                    new Matrix4f(),
                    new NGeometry[]{
                        new NGeometry(
                                "ground",
                                this.mesh,
                                new NMaterial("ground_material", textures)
                        )
                    },
                    new N3DModelNode[0]
            )
    ));

    private final PlayerController player = new PlayerController();
    private boolean playerActive = false;

    private final HullCollisionShape stoneShape;
    private final MeshData stoneShapeMesh;

    {
        MeshData stone = Geometries.ASTEROID;

        this.stoneShape = MeshUtils.createHullCollisionShapeFromMeshes(
                new float[][]{stone.getVertices()},
                new int[][]{stone.getIndices()},
                new Matrix4fc[]{null},
                MeshData.SIZE,
                MeshData.XYZ_OFFSET
        );

        this.stoneShapeMesh = MeshUtils.createMeshFromCollisionShape("stoneShapeMesh", this.stoneShape);
    }

    private Game() {

    }

    public void loadLightmap(Geometry geometry, String lightmap) {
        LightmapFile.LightmapData data;
        try {
            try (InputStream stream = Geometries.class.getResourceAsStream(lightmap)) {
                data = LightmapFile.decode(stream);
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.out);
            return;
        }

        MeshData.LightmapMesh mesh = geometry.getMesh().scheduleLightmapMesh(
                data.pixelToWorldRatio(),
                data.scaleX(), data.scaleY(), data.scaleZ()
        );
        String[] groups = new String[data.lightmaps().length];
        for (int i = 0; i < groups.length; i++) {
            groups[i] = data.lightmaps()[i].groupName();
        }

        this.writeToTexture.prepare(geometry, mesh, data.lightmapSize(), groups);

        for (int i = 0; i < data.lightmaps().length; i++) {
            this.writeToTexture.write(data.lightmaps()[i].data(), i);
        }

        geometry.setLightmapMesh(mesh);
    }

    private void print(N3DModelNode node, int depth) {
        String spacing = "\t".repeat(depth);

        System.out.println(spacing + "node name: " + node.getName() + ", depth: " + depth + " ->");

        NGeometry[] geometries = node.getGeometries();
        System.out.println(spacing + "amount of geometries: " + geometries.length);

        if (geometries.length != 0) {
            System.out.println(spacing + "list of geometries:");
            for (NGeometry g : geometries) {
                System.out.println(spacing + "geometry name: " + g.getName() + ", vertices: " + (g.getMesh().getVertices().length / NMesh.VERTEX_SIZE) + ", indices: " + g.getMesh().getIndices().length);
            }
        }

        N3DModelNode[] children = node.getChildren();

        System.out.println(spacing + "amount of children: " + children.length);
        if (children.length != 0) {
            for (N3DModelNode child : children) {
                print(child, depth + 1);
            }
        }

        System.out.println(spacing + "<- end node");
    }

    private N3DObject testModel = null;
    private N3DObject myBalls = null;
    private N3DObject waterBottle = null;
    private N3DObject fox = null;
    private NCubemap cubemap = null;
    
    public void start() {
        cubemap = NCubemapIO.loadFromJar("cientistavuador/newrenderingpipeline/resources/image/generic_cubemap.png", true, false);
        
        try {
            N3DModel model = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/cc0_zacxophone_triceratops.glb");
            testModel = new N3DObject("test model", model);
            testModel.setAnimator(new NAnimator(model, "Armature|Armature|Fall"));
            testModel.getPosition().set(8f, 10.75f, -25f);
            testModel.getRotation().rotateY((float) Math.toRadians(-90f + -45f));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        try {
            N3DModel model = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/my_metallic_balls.glb");
            myBalls = new N3DObject("test model", model);
            myBalls.getPosition().set(0f, 20f, -15f);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        try {
            N3DModel waterBottle3DModel = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/cc0_WaterBottle.glb");
            waterBottle = new N3DObject("water bottle", waterBottle3DModel);
            waterBottle.getPosition().set(0f, 25f, -15f);
            waterBottle.getScale().set(5f);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        try {
            N3DModel fox3DModel = N3DModelImporter.importFromJarFile("cientistavuador/newrenderingpipeline/cc0_Fox.glb");
            fox = new N3DObject("fox", fox3DModel);
            fox.getPosition().set(0f, 10f, -20f);
            fox.getScale().set(0.02f);

            NAnimator foxAnimator = new NAnimator(fox3DModel, "Run");
            fox.setAnimator(foxAnimator);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        
        this.groundObj.getPosition().set(0f, 10f, -15f);

        this.physicsSpace.setMaxSubSteps(8);
        this.physicsSpace.setAccuracy(1f / 60f);
        this.physicsSpace.setGravity(new com.jme3.math.Vector3f(
                0f, -9.8f * Main.TO_PHYSICS_ENGINE_UNITS, 0f
        ));

        this.player.getCharacterController().addToPhysicsSpace(this.physicsSpace);

        resetPlayer();

        //this.monkeyGeometry.setModel(new Matrix4f().translate(0, 20, 0));
        this.monkeyGeometry.setModel(new Matrix4f().translate(40, -10, 0).scale(20f));

        camera.setPosition(1f, 3f, -5f);
        camera.setUBO(CameraUBO.create(UBOBindingPoints.PLAYER_CAMERA));

        GeometryProgram program = GeometryProgram.INSTANCE;
        program.use();
        program.setModel(new Matrix4f());
        program.setColor(1f, 1f, 1f, 1f);
        program.setSunDirection(new Vector3f(-1f, -1f, 0f).normalize());
        program.setSunDiffuse(1f, 1f, 1f);
        program.setSunAmbient(0.2f, 0.2f, 0.2f);
        program.setTextureUnit(0);
        program.setLightingEnabled(true);
        glUseProgram(0);

        for (int i = 0; i < 4; i++) {
            this.scene.getGeometries().add(new Geometry(Geometries.GARAGE[i]));
        }
        loadLightmap(this.scene.getGeometries().get(0), "concrete.lightmap");
        loadLightmap(this.scene.getGeometries().get(1), "grass.lightmap");
        loadLightmap(this.scene.getGeometries().get(2), "bricks.lightmap");
        loadLightmap(this.scene.getGeometries().get(3), "red.lightmap");

        {
            Geometry stair = new Geometry(GeometriesLoader.load(
                    MeshConfiguration.lightmapped("stupid_stair.obj")
            ).get("stupid_stair.obj"));
            stair.getMesh().setTextureHint(Textures.CONCRETE);
            stair.setModel(new Matrix4f()
                    .translate(-7.4f, 0f, 0f)
                    .scale(1.51f, 1.51f, 2f)
                    .rotateY((float) Math.toRadians(180f))
            );

            loadLightmap(stair, "stairs.lightmap");

            this.scene.getGeometries().add(stair);

            HullCollisionShape clippedStairs = MeshUtils.createHullCollisionShapeFromMeshes(
                    new float[][]{stair.getMesh().getVertices()},
                    new int[][]{stair.getMesh().getIndices()},
                    new Matrix4fc[]{stair.getModel()},
                    MeshData.SIZE,
                    MeshData.XYZ_OFFSET
            );

            PhysicsRigidBody clippedStairsBody = new PhysicsRigidBody(clippedStairs, 0f);
            clippedStairsBody.setFriction(1f);
            clippedStairsBody.setRestitution(1f);
            this.physicsSpace.addCollisionObject(clippedStairsBody);
        }

        {
            Geometry stair = new Geometry(GeometriesLoader.load(
                    MeshConfiguration.lightmapped("not_so_stupid_stair.obj")
            ).get("not_so_stupid_stair.obj"));
            stair.getMesh().setTextureHint(Textures.CONCRETE);
            stair.setModel(new Matrix4f()
                    .translate(5f, 5f, 0f)
                    .rotateY((float) Math.toRadians(-90f))
            );

            this.stairGeometry = stair;
        }

        this.scene.setIndirectLightingEnabled(true);
        this.scene.setDirectLightingEnabled(true);
        this.scene.setShadowsEnabled(true);

        this.scene.setIndirectLightingBlurArea(4f);
        this.scene.setShadowBlurArea(1.2f);

        this.scene.setSamplingMode(SamplingMode.SAMPLE_17);

        this.scene.setFastModeEnabled(false);

        sun.setGroupName("sun");
        sun.setDirection(1f, -0.75f, 1f);
        this.scene.getLights().add(sun);

        List<float[]> meshVertices = new ArrayList<>();
        List<int[]> meshIndices = new ArrayList<>();
        List<Matrix4fc> meshModels = new ArrayList<>();

        for (Geometry g : this.scene.getGeometries()) {
            if (g.getMesh().getName().equals("stupid_stair.obj")) {
                continue;
            }
            meshVertices.add(g.getMesh().getVertices());
            meshIndices.add(g.getMesh().getIndices());
            meshModels.add(g.getModel());
        }

        meshVertices.add(this.monkeyGeometry.getMesh().getVertices());
        meshIndices.add(this.monkeyGeometry.getMesh().getIndices());
        meshModels.add(this.monkeyGeometry.getModel());

        meshVertices.add(this.stairGeometry.getMesh().getVertices());
        meshIndices.add(this.stairGeometry.getMesh().getIndices());
        meshModels.add(this.stairGeometry.getModel());

        MeshCollisionShape world = MeshUtils.createStaticCollisionShapeFromMeshes(
                meshVertices.toArray(float[][]::new),
                meshIndices.toArray(int[][]::new),
                meshModels.toArray(Matrix4fc[]::new),
                MeshData.SIZE, MeshData.XYZ_OFFSET
        );

        PhysicsRigidBody worldBody = new PhysicsRigidBody(world, 0f);
        worldBody.setRestitution(1f);
        worldBody.setFriction(1f);
        this.physicsSpace.addCollisionObject(worldBody);
    }

    public void loop() {
        if (!this.status.isDone()) {
            try {
                Thread.sleep(16);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }
        }

        for (RayResult r : this.rays) {
            LineRender.queueRender(r.getOrigin(), r.getHitPosition());
        }

        if (this.status.hasError()) {
            try {
                this.status.throwException();
            } catch (ExecutionException ex) {
                throw new RuntimeException(ex);
            }
        }

        float speed = 1f;

        if (this.interiorEnabled) {
            this.interiorIntensity += Main.TPF * speed;
        } else {
            this.interiorIntensity -= Main.TPF * speed;
        }

        if (this.sunEnabled) {
            this.sunIntensity += Main.TPF * speed;
        } else {
            this.sunIntensity -= Main.TPF * speed;
        }

        this.interiorIntensity = Math.min(Math.max(this.interiorIntensity, 0f), 1f);
        this.sunIntensity = Math.min(Math.max(this.sunIntensity, 0f), 1f);

        GeometryProgram.INSTANCE.setBakedLightGroupIntensity(0, this.interiorIntensity);
        GeometryProgram.INSTANCE.setBakedLightGroupIntensity(1, this.sunIntensity);

        if (this.playerActive) {
            this.player.update(this.camera.getFront(), this.camera.getRight());

            camera.setPosition(
                    this.player.getEyePosition().x(),
                    this.player.getEyePosition().y(),
                    this.player.getEyePosition().z()
            );
        }

        if (this.player.getCharacterController().getPosition().y() < -100f) {
            resetPlayer();
        }

        camera.updateMovement();
        camera.updateUBO();

        Matrix4f cameraProjectionView = new Matrix4f(this.camera.getProjectionView());

        GeometryProgram program = GeometryProgram.INSTANCE;
        program.use();
        program.updateLightsUniforms();
        program.setProjectionView(cameraProjectionView);
        program.setTextureUnit(0);
        program.setLightmapTextureUnit(1);
        program.setLightingEnabled(false);
        program.setColor(1f, 1f, 1f, 1f);
        for (Geometry geo : this.scene.getGeometries()) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, geo.getMesh().getTextureHint());
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D_ARRAY, geo.getLightmapTextureHint());
            program.setModel(geo.getModel());

            MeshData mesh = geo.getMesh();
            MeshData.LightmapMesh lightmap = geo.getLightmapMesh();

            if (lightmap == null || !lightmap.isDone()) {
                glBindVertexArray(mesh.getVAO());
            } else {
                glBindVertexArray(lightmap.getVAO());
            }
            mesh.render();
            glBindVertexArray(0);
        }
        for (Scene.Light light : this.scene.getLights()) {
            if (light instanceof Scene.PointLight p) {
                float r = p.getDiffuse().x();
                float g = p.getDiffuse().y();
                float b = p.getDiffuse().z();
                float max = Math.max(r, Math.max(g, b));
                if (max > 1f) {
                    float invmax = 1f / max;
                    r *= invmax;
                    g *= invmax;
                    b *= invmax;
                }
                program.setColor(r, g, b, 1f);
                Matrix4f model = new Matrix4f();
                model.translate(p.getPosition()).scale(p.getLightSize());
                program.setModel(model);

                MeshData sphere = Geometries.SPHERE;

                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, sphere.getTextureHint());
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D_ARRAY, Textures.EMPTY_LIGHTMAP);

                glBindVertexArray(Geometries.SPHERE.getVAO());
                sphere.render();
                glBindVertexArray(0);
            }
        }

        program.setLightingEnabled(true);
        program.setSunDiffuse(this.sun.getDiffuse());
        program.setSunAmbient(this.sun.getAmbient());
        program.setSunDirection(this.sun.getDirection());

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, this.monkeyGeometry.getMesh().getTextureHint());
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D_ARRAY, Textures.EMPTY_LIGHTMAP);
        program.setModel(this.monkeyGeometry.getModel());
        this.monkeyGeometry.getMesh().bindRenderUnbind();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, this.stairGeometry.getMesh().getTextureHint());
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D_ARRAY, Textures.EMPTY_LIGHTMAP);
        program.setModel(this.stairGeometry.getModel());
        this.stairGeometry.getMesh().bindRenderUnbind();

        if (!this.playerActive) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, Textures.WHITE_TEXTURE);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D_ARRAY, Textures.EMPTY_LIGHTMAP);
            program.setModel(new Matrix4f()
                    .translate(this.player.getCharacterController().getPosition())
            );
            MeshData data;
            if (this.player.getCharacterController().isCrouched()) {
                data = this.player.getCrouchCollisionMeshData();
            } else {
                data = this.player.getCollisionMeshData();
            }
            data.bindRenderUnbind();
        }

        for (PhysicsRigidBody e : this.rigidBodies) {
            Geometry geo = (Geometry) e.getUserObject();

            program.setColor(1f, 1f, 1f, 1f);
            Matrix4f model = new Matrix4f();

            RigidBodyMotionState motion = e.getMotionState();

            com.jme3.math.Vector3f pos = motion.getLocation(null);

            Vector3f position = new Vector3f(
                    pos.x * Main.FROM_PHYSICS_ENGINE_UNITS,
                    pos.y * Main.FROM_PHYSICS_ENGINE_UNITS,
                    pos.z * Main.FROM_PHYSICS_ENGINE_UNITS
            );

            boolean shadow = Geometry.fastTestRay(position, this.sun.getDirectionNegated(), Float.POSITIVE_INFINITY, this.scene.getGeometries());

            if (shadow) {
                program.setSunDiffuse(0f, 0f, 0f);
            } else {
                program.setSunDiffuse(this.sun.getDiffuse());
            }

            model.translate(position);

            if (geo.getMesh().equals(Geometries.SPHERE)) {
                model.scale(this.sphereShape.getRadius() * 2f * Main.FROM_PHYSICS_ENGINE_UNITS);
            }

            com.jme3.math.Matrix3f rot = motion.getOrientation((com.jme3.math.Matrix3f) null);
            Matrix3f rotation = new Matrix3f();
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    rotation.set(i, j, rot.get(j, i));
                }
            }
            model.mul(new Matrix4f().set(rotation));
            geo.setModel(model);

            program.setModel(model);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, geo.getMesh().getTextureHint());
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D_ARRAY, Textures.EMPTY_LIGHTMAP);

            //if (geo.getMesh().equals(Geometries.ASTEROID)) {
            //    this.stoneShapeMesh.bindRenderUnbind();
            //} else {
            geo.getMesh().bindRenderUnbind();
            //}
        }
        program.setLightingEnabled(false);

        glUseProgram(0);

        List<NLight> lights = new ArrayList<>();
        NLight.NDirectionalLight sun = new NLight.NDirectionalLight("sun");
        sun.getDirection().set(1f, -1f, 1f).normalize();
        sun.getDiffuse().set(1f);
        sun.getSpecular().set(1f);
        sun.getAmbient().set(0.1f);
        lights.add(sun);

        NLight.NPointLight point = new NLight.NPointLight("point");
        point.getPosition().set(13f, 11f, -17f);
        //lights.add(point);

        this.fox.getAnimator().update(Main.TPF);
        N3DObjectRenderer.queueRender(this.fox);
        N3DObjectRenderer.queueRender(this.waterBottle);
        N3DObjectRenderer.queueRender(this.myBalls);
        N3DObjectRenderer.queueRender(this.groundObj);
        this.testModel.getAnimator().update(Main.TPF);
        N3DObjectRenderer.queueRender(this.testModel);
        
        N3DObjectRenderer.render(this.camera, lights, cubemap);

        AabRender.renderQueue(camera);
        LineRender.renderQueue(camera);

        if (!this.status.isDone()) {
            String[] text = new String[]{
                new StringBuilder()
                .append(this.status.getASCIIProgressBar()).append('\n')
                .append(this.status.getCurrentStatus()).append('\n')
                .append(this.status.getRaysPerSecondFormatted()).append('\n')
                .append("Estimated Time: ").append(this.status.getEstimatedTimeFormatted()).append("\n")
                .toString()
            };
            GLFontRenderer.render(-0.895f, 0.795f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_035_BLACK}, text);
            GLFontRenderer.render(-0.90f, 0.80f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_035_WHITE}, text);
        }

        String[] text = new String[]{
            new StringBuilder()
            .append("Escape - Lock/Unlock Mouse\n")
            .append("LMB - Push, RMB - Pull\n")
            .append("E - Ball, C - Can, M - Monkey, T - Stone\n")
            .append("F - Player/Freecam\n")
            .append("Space - Jump\n")
            .append("G - Random Impulse\n")
            .append("R - Reset Player Position\n")
            .append("V - Noclip\n")
            .toString()
        };
        GLFontRenderer.render(-0.895f, -0.70f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_035_BLACK}, text);
        GLFontRenderer.render(-0.90f, -0.695f, new GLFontSpecification[]{GLFontSpecifications.SPACE_MONO_REGULAR_0_035_WHITE}, text);

        Main.WINDOW_TITLE += " (DrawCalls: " + Main.NUMBER_OF_DRAWCALLS + ", Vertices: " + Main.NUMBER_OF_VERTICES + ")";
        Main.WINDOW_TITLE += " (x:" + (int) Math.floor(camera.getPosition().x()) + ",y:" + (int) Math.floor(camera.getPosition().y()) + ",z:" + (int) Math.ceil(camera.getPosition().z()) + ")";

        if (glfwGetMouseButton(Main.WINDOW_POINTER, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS) {
            List<Geometry> geoList = new ArrayList<>();
            Map<Geometry, PhysicsRigidBody> map = new HashMap<>();
            for (Geometry g : this.scene.getGeometries()) {
                geoList.add(g);
            }
            for (PhysicsRigidBody b : this.rigidBodies) {
                Geometry g = (Geometry) b.getUserObject();
                geoList.add(g);
                map.put(g, b);
            }
            RayResult[] results = Geometry.testRay(
                    new Vector3f().set(this.camera.getPosition()),
                    this.camera.getFront(),
                    geoList
            );
            if (results.length != 0) {
                RayResult closest = results[0];

                PhysicsRigidBody sphere = map.get(closest.getGeometry());
                if (sphere != null) {
                    Vector3f camTarget = new Vector3f();
                    camTarget.set(this.camera.getFront()).mul(0.25f);
                    camTarget.add(new Vector3f().set(this.camera.getPosition()));

                    Vector3f spherePosition = new Vector3f();
                    com.jme3.math.Vector3f center = sphere.getPhysicsLocation(null);
                    spherePosition.set(
                            center.x * Main.FROM_PHYSICS_ENGINE_UNITS,
                            center.y * Main.FROM_PHYSICS_ENGINE_UNITS,
                            center.z * Main.FROM_PHYSICS_ENGINE_UNITS
                    );

                    camTarget.sub(spherePosition).normalize();

                    sphere.applyCentralForce(
                            new com.jme3.math.Vector3f(
                                    camTarget.x() * sphere.getMass() * 20f * Main.TO_PHYSICS_ENGINE_UNITS,
                                    camTarget.y() * sphere.getMass() * 20f * Main.TO_PHYSICS_ENGINE_UNITS,
                                    camTarget.z() * sphere.getMass() * 20f * Main.TO_PHYSICS_ENGINE_UNITS
                            )
                    );
                }
            }
        }

        List<PhysicsRigidBody> removed = new ArrayList<>();
        for (PhysicsRigidBody e : this.rigidBodies) {
            if (e.getPhysicsLocation(null).y < -100f * Main.TO_PHYSICS_ENGINE_UNITS) {
                this.physicsSpace.removeCollisionObject(e);
                removed.add(e);
            }
        }
        this.rigidBodies.removeAll(removed);

        this.physicsSpace.update((float) Main.TPF);

        Main.WINDOW_TITLE += " (Speed: " + String.format("%.2f", this.player.getCharacterController().getRigidBody().getLinearVelocity(null).length()) + ")";
    }

    public void bakePopupCallback(BakePopup popup) {
        if (!this.status.isDone()) {
            return;
        }

        for (Geometry geo : this.scene.getGeometries()) {
            if (geo.getLightmapTextureHint() != Textures.EMPTY_LIGHTMAP) {
                glDeleteTextures(geo.getLightmapTextureHint());
                geo.setLightmapTextureHint(Textures.EMPTY_LIGHTMAP);
            }
        }

        BakePopup.toScene(this.scene, popup);
        this.status = BakedLighting.bake(this.writeToTexture, this.scene);
    }

    public void resetPlayer() {
        this.player.getCharacterController().setPosition(0f, 5f, -5f);
        this.player.getCharacterController().getRigidBody().setLinearVelocity(com.jme3.math.Vector3f.ZERO);
    }

    public void mouseCursorMoved(double x, double y) {
        camera.mouseCursorMoved(x, y);
    }

    public void windowSizeChanged(int width, int height) {
        camera.setDimensions(width, height);
    }

    private void configureRigidBody(PhysicsRigidBody body) {
        float radius = body.getCollisionShape().maxRadius();
        if (Float.isFinite(radius)) {
            body.setCcdSweptSphereRadius(radius);
            body.setCcdMotionThreshold(radius);
        }
        Vector3fc front = this.camera.getFront();
        body.setPhysicsLocation(new com.jme3.math.Vector3f(
                (float) (this.camera.getPosition().x() + front.x()) * Main.TO_PHYSICS_ENGINE_UNITS,
                (float) (this.camera.getPosition().y() + front.y()) * Main.TO_PHYSICS_ENGINE_UNITS,
                (float) (this.camera.getPosition().z() + front.z()) * Main.TO_PHYSICS_ENGINE_UNITS
        ));
        body.applyCentralImpulse(new com.jme3.math.Vector3f(
                front.x() * body.getMass() * 10f * Main.TO_PHYSICS_ENGINE_UNITS,
                front.y() * body.getMass() * 10f * Main.TO_PHYSICS_ENGINE_UNITS,
                front.z() * body.getMass() * 10f * Main.TO_PHYSICS_ENGINE_UNITS
        ));
    }

    public void keyCallback(long window, int key, int scancode, int action, int mods) {
        if (key == GLFW_KEY_F && action == GLFW_PRESS) {
            this.playerActive = !this.playerActive;
            this.camera.setMovementDisabled(this.playerActive);
            if (this.playerActive) {
                this.player.forceEyePositionUpdate();
            }
        }
        if (key == GLFW_KEY_F1 && action == GLFW_PRESS) {
            if (!this.bakeWindowOpen) {
                this.bakeWindowOpen = true;
                BakePopup.show((t) -> {
                    BakePopup.fromScene(this.scene, t);
                }, (t) -> {
                    Main.MAIN_TASKS.add(() -> {
                        Game.this.bakePopupCallback(t);
                    });
                }, (t) -> {
                    this.bakeWindowOpen = false;
                });
                if (this.camera.isCaptureMouse()) {
                    this.camera.pressEscape();
                }
            }
        }
        if (key == GLFW_KEY_F2 && action == GLFW_PRESS) {
            if (!this.geometryLightmaps.isEmpty() && !this.saveLightmapProcessing.get()) {
                this.saveLightmapProcessing.set(true);
                final List<LightmapFile.LightmapData> finalList = new ArrayList<>();
                final List<Geometry> finalGeometryList = new ArrayList<>();
                for (Map.Entry<Geometry, LightmapFile.LightmapData> lightmap : this.geometryLightmaps.entrySet()) {
                    finalList.add(lightmap.getValue());
                    finalGeometryList.add(lightmap.getKey());
                }
                new Thread(() -> {
                    try {
                        JFrame dummyFrame = new JFrame("dummy frame");
                        dummyFrame.setLocationRelativeTo(null);
                        dummyFrame.setVisible(true);
                        dummyFrame.toFront();
                        dummyFrame.setVisible(false);

                        JFileChooser chooser = new JFileChooser();
                        chooser.setCurrentDirectory(null);
                        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                        chooser.setAcceptAllFileFilterUsed(false);
                        int option = chooser.showOpenDialog(dummyFrame);
                        if (option == JFileChooser.APPROVE_OPTION) {
                            File directory = chooser.getSelectedFile();
                            if (!directory.exists()) {
                                directory.mkdirs();
                            }
                            for (int i = 0; i < finalList.size(); i++) {
                                LightmapFile.LightmapData lightmap = finalList.get(i);
                                Geometry geometry = finalGeometryList.get(i);

                                File output = new File(directory, i + "_" + geometry.getMesh().getName() + ".lightmap");

                                try {
                                    try (FileOutputStream outputStream = new FileOutputStream(output)) {
                                        LightmapFile.encode(lightmap, 0.001f, outputStream);
                                    }
                                } catch (IOException ex) {
                                    ex.printStackTrace(System.out);
                                }
                            }
                        }
                    } finally {
                        this.saveLightmapProcessing.set(false);
                    }
                }).start();
            }
        }
        if (key == GLFW_KEY_E && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
            PhysicsRigidBody physicsSphere = new PhysicsRigidBody(sphereShape, 5f);
            configureRigidBody(physicsSphere);
            physicsSphere.setFriction(1f);
            physicsSphere.setRollingFriction(0.02f);
            physicsSphere.setSpinningFriction(0.02f);
            physicsSphere.setRestitution(0.5f);
            physicsSphere.setUserObject(new Geometry(Geometries.SPHERE));
            this.physicsSpace.addCollisionObject(physicsSphere);
            this.rigidBodies.add(physicsSphere);
        }
        if (key == GLFW_KEY_M && action == GLFW_PRESS) {
            PhysicsRigidBody physicsMonkey = new PhysicsRigidBody(this.monkeyShape, 350f);
            configureRigidBody(physicsMonkey);
            physicsMonkey.setFriction(1f);
            physicsMonkey.setRollingFriction(0.0f);
            physicsMonkey.setSpinningFriction(0.0f);
            physicsMonkey.setRestitution(0.05f);
            physicsMonkey.setUserObject(new Geometry(Geometries.MONKEY));
            this.physicsSpace.addCollisionObject(physicsMonkey);
            this.rigidBodies.add(physicsMonkey);
        }
        if (key == GLFW_KEY_C && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
            PhysicsRigidBody physicsCiencola = new PhysicsRigidBody(this.ciencolaShape, 0.5f);
            configureRigidBody(physicsCiencola);
            physicsCiencola.setCcdSweptSphereRadius(0.8f * 0.9f);
            physicsCiencola.setCcdMotionThreshold(0.01f);
            physicsCiencola.setFriction(0.4f);
            physicsCiencola.setRollingFriction(0.06f);
            physicsCiencola.setSpinningFriction(0.06f);
            physicsCiencola.setRestitution(0.2f);
            physicsCiencola.setUserObject(new Geometry(Geometries.CIENCOLA));
            this.physicsSpace.addCollisionObject(physicsCiencola);
            this.rigidBodies.add(physicsCiencola);
        }
        if (key == GLFW_KEY_T && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
            PhysicsRigidBody physicsStone = new PhysicsRigidBody(this.stoneShape, 300f);
            configureRigidBody(physicsStone);
            physicsStone.setFriction(4f);
            physicsStone.setRollingFriction(1f);
            physicsStone.setSpinningFriction(1f);
            physicsStone.setRestitution(0.0f);
            physicsStone.setUserObject(new Geometry(Geometries.ASTEROID));
            this.physicsSpace.addCollisionObject(physicsStone);
            this.rigidBodies.add(physicsStone);
        }
        if (key == GLFW_KEY_G && action == GLFW_PRESS) {
            for (PhysicsRigidBody e : this.rigidBodies) {
                float x = (float) ((Math.random() * 2.0) - 1.0);
                float z = (float) ((Math.random() * 2.0) - 1.0);
                e.applyCentralImpulse(new com.jme3.math.Vector3f(
                        x * e.getMass() * 10f,
                        e.getMass() * 10f,
                        z * e.getMass() * 10f
                ));
            }
        }
        if (key == GLFW_KEY_R && action == GLFW_PRESS) {
            resetPlayer();
        }
        if (key == GLFW_KEY_SPACE && action == GLFW_PRESS && this.playerActive) {
            if (this.playerActive) {
                this.player.jump();
            }
        }
        if (key == GLFW_KEY_V && action == GLFW_PRESS) {
            if (this.playerActive) {
                this.player.getCharacterController().setNoclipEnabled(!this.player.getCharacterController().isNoclipEnabled());
            }
        }
        if (key == GLFW_KEY_H && action == GLFW_PRESS) {
            N3DObjectRenderer.REFLECTIONS_ENABLED = !N3DObjectRenderer.REFLECTIONS_ENABLED;
        }
    }

    public void mouseCallback(long window, int button, int action, int mods) {
        if (button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
            List<Geometry> geoList = new ArrayList<>();
            Map<Geometry, PhysicsRigidBody> map = new HashMap<>();
            for (Geometry g : this.scene.getGeometries()) {
                geoList.add(g);
            }
            for (PhysicsRigidBody b : this.rigidBodies) {
                Geometry g = (Geometry) b.getUserObject();
                geoList.add(g);
                map.put(g, b);
            }
            RayResult[] results = Geometry.testRay(
                    new Vector3f().set(this.camera.getPosition()),
                    this.camera.getFront(),
                    geoList
            );
            if (results.length != 0) {
                RayResult closest = results[0];

                PhysicsRigidBody sphere = map.get(closest.getGeometry());
                if (sphere != null) {
                    Vector3f position = new Vector3f();
                    position.set(closest.getHitPosition());
                    com.jme3.math.Vector3f center = sphere.getPhysicsLocation(null);
                    position.sub(
                            center.x * Main.FROM_PHYSICS_ENGINE_UNITS,
                            center.y * Main.FROM_PHYSICS_ENGINE_UNITS,
                            center.z * Main.FROM_PHYSICS_ENGINE_UNITS
                    );

                    com.jme3.math.Vector3f o = new com.jme3.math.Vector3f(
                            position.x() * Main.TO_PHYSICS_ENGINE_UNITS,
                            position.y() * Main.TO_PHYSICS_ENGINE_UNITS,
                            position.z() * Main.TO_PHYSICS_ENGINE_UNITS
                    );

                    sphere.applyImpulse(
                            new com.jme3.math.Vector3f(
                                    this.camera.getFront().x() * sphere.getMass() * 10f * Main.TO_PHYSICS_ENGINE_UNITS,
                                    this.camera.getFront().y() * sphere.getMass() * 10f * Main.TO_PHYSICS_ENGINE_UNITS,
                                    this.camera.getFront().z() * sphere.getMass() * 10f * Main.TO_PHYSICS_ENGINE_UNITS
                            ),
                            o
                    );
                }
            }
        }
    }
}
