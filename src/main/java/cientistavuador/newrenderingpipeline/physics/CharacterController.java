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
package cientistavuador.newrenderingpipeline.physics;

import cientistavuador.newrenderingpipeline.Main;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.PhysicsTickListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsSweepTestResult;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.MeshCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Transform;
import com.jme3.math.Triangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 *
 * @author Cien
 */
public class CharacterController implements PhysicsTickListener {

    private static enum SweepTestFilter {
        NO_PLAYER_NO_GHOSTS,
        NO_CEILINGS,
        NO_WALLS,
        NO_FLOORS,
        NO_OUTSIDE_OF_GROUND_THRESHOLD;
    }
    
    public static final float EPSILON = 0.001f;

    public static final float TEST_BOX_HEIGHT = 0.01f;

    public static final float STEP_UP_MINIMUM_HEIGHT = 0.08f;
    public static final float STEP_UP_EXTRA_HEIGHT = 0.06f;
    public static final int STEP_UP_EXTRA_HEIGHT_TICKS = 6;
    public static final float STEP_DOWN_MINIMUM_HEIGHT = 0.02f;
    public static final float PRECISE_BLOCK_TUNNELING_CCD_PENETRATION = 0.04f;

    public static final float VELOCITY_CUTOFF = 0.01f;

    //physics space
    private PhysicsSpace space = null;

    //character info
    private final float totalHeight;
    private final float crouchTotalHeight;
    private final float radius;

    //character collision
    private final CollisionShape collisionShape;
    private final CollisionShape crouchCollisionShape;

    private final PhysicsRigidBody rigidBody;

    private final BoxCollisionShape sweepTestBox;

    //character state
    private boolean noclipEnabled = false;
    private boolean noclipStateChanged = false;

    private boolean crouched = false;
    private boolean crouchStateChanged = false;
    private boolean airCrouched = false;
    private boolean onGround = false;

    private final Vector3f groundNormal = new Vector3f(0f, 1f, 0f);
    private final Vector3f groundOrientedWalkDirection = new Vector3f();

    //character movement configuration
    private float walkDirectionX = 0f;
    private float walkDirectionZ = 0f;
    private float walkDirectionSpeed = 0f;

    private float gravityCoefficient = 2.75f;

    private float airMovementRoughness = 3f;
    private float groundMovementRoughness = 10f;

    private float groundThreshold = 0.75f;

    private float airFriction = 1f;
    private float groundFriction = 6f;

    private float stepUpHeight = 0.60f;
    private float stepUpMargin = 0.1f;
    private float stepDownHeight = 0.60f;
    private float stepMaxExternalSpeed = 3f;

    private float depenetrationMargin = 0.6f;

    private float sweepTestTolerance = 0.1f;

    private float onGroundThreshold = 0.05f;

    private float gravityCutoffTime = 0.1f;

    private float nextJumpImpulse = 0f;
    private int stepUpExtraHeightTicks = 0;
    private float stepUpHeightDetected = 0f;

    //applied velocities
    private float appliedWalkX = 0f;
    private float appliedWalkY = 0f;
    private float appliedWalkZ = 0f;

    private float appliedJump = 0f;

    private float appliedGravityX = 0f;
    private float appliedGravityY = 0f;
    private float appliedGravityZ = 0f;

    //total applied velocities
    private float appliedTotalX = 0f;
    private float appliedTotalY = 0f;
    private float appliedTotalZ = 0f;

    //velocity delta
    private float deltaX = 0f;
    private float deltaY = 0f;
    private float deltaZ = 0f;

    //velocities
    private float walkX = 0f;
    private float walkY = 0f;
    private float walkZ = 0f;

    private float jump = 0f;

    private float gravityX = 0f;
    private float gravityY = 0f;
    private float gravityZ = 0f;

    private float internalX = 0f;
    private float internalY = 0f;
    private float internalZ = 0f;

    private float externalX = 0f;
    private float externalY = 0f;
    private float externalZ = 0f;

    //position
    private float lastPositionX = 0f;
    private float lastPositionY = 0f;
    private float lastPositionZ = 0f;

    //gravity
    private float internalGravityCoefficient = this.gravityCoefficient;
    private float gravityGroundCounter = 0f;

    //recycled objects
    private final com.jme3.math.Triangle filterTriangle = new Triangle();
    private final com.jme3.math.Vector3f filterNormal = new com.jme3.math.Vector3f();

    private final com.jme3.math.Vector3f physicsPosition = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f physicsPositionSet = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f physicsVelocity = new com.jme3.math.Vector3f();

    private final com.jme3.math.Vector3f sweepTestScale = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f sweepTestStartPosition = new com.jme3.math.Vector3f();
    private final com.jme3.math.Vector3f sweepTestEndPosition = new com.jme3.math.Vector3f();

    private final com.jme3.math.Transform sweepTestStart = new Transform();
    private final com.jme3.math.Transform sweepTestEnd = new Transform();

    private final com.jme3.math.Vector3f velocityApply = new com.jme3.math.Vector3f();

    private final com.jme3.math.Vector3f spaceGravity = new com.jme3.math.Vector3f();
    private final Vector3f spaceGravityGet = new Vector3f();

    private final com.jme3.math.Vector3f positionStore = new com.jme3.math.Vector3f();
    private final Vector3f jomlPositionStore = new Vector3f();

    private final com.jme3.math.Vector3f interpolatedPositionStore = new com.jme3.math.Vector3f();
    private final Vector3f interpolatedJomlPositionStore = new Vector3f();

    private final Vector3f orientedWalk = new Vector3f();
    private final Vector3f orientedTangent = new Vector3f();
    private final Vector3f orientedBitangent = new Vector3f();

    public CharacterController(float radius, float totalHeight, float crouchTotalHeight, float mass) {
        this.totalHeight = totalHeight;
        this.crouchTotalHeight = crouchTotalHeight;
        this.radius = radius;

        {
            BoxCollisionShape box = new BoxCollisionShape(
                    radius * Main.TO_PHYSICS_ENGINE_UNITS,
                    totalHeight * 0.5f * Main.TO_PHYSICS_ENGINE_UNITS,
                    radius * Main.TO_PHYSICS_ENGINE_UNITS
            );
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(box,
                    0f,
                    totalHeight * 0.5f * Main.TO_PHYSICS_ENGINE_UNITS,
                    0f
            );
            this.collisionShape = compound;
        }

        {
            BoxCollisionShape box = new BoxCollisionShape(
                    radius * Main.TO_PHYSICS_ENGINE_UNITS,
                    crouchTotalHeight * 0.5f * Main.TO_PHYSICS_ENGINE_UNITS,
                    radius * Main.TO_PHYSICS_ENGINE_UNITS
            );
            CompoundCollisionShape compound = new CompoundCollisionShape(1);
            compound.addChildShape(box,
                    0f,
                    crouchTotalHeight * 0.5f * Main.TO_PHYSICS_ENGINE_UNITS,
                    0f
            );
            this.crouchCollisionShape = compound;
        }

        this.rigidBody = new PhysicsRigidBody(this.collisionShape, mass);
        this.rigidBody.setAngularFactor(0f);
        this.rigidBody.setEnableSleep(false);
        this.rigidBody.setFriction(0f);
        this.rigidBody.setRestitution(0f);
        this.rigidBody.setProtectGravity(true);
        this.rigidBody.setGravity(com.jme3.math.Vector3f.ZERO);

        {
            this.sweepTestBox = new BoxCollisionShape(1f, 1f, 1f);
        }
    }

    public void addToPhysicsSpace(PhysicsSpace space) {
        if (this.space != null) {
            throw new IllegalArgumentException("Already on a physics space!");
        }
        this.space = space;
        this.space.addTickListener(this);
        this.space.addCollisionObject(this.rigidBody);
    }

    public void removeFromPhysicsSpace() {
        if (this.space == null) {
            return;
        }
        this.space.removeTickListener(this);
        this.space.removeCollisionObject(this.rigidBody);
        this.space = null;
    }

    public PhysicsSpace getPhysicsSpace() {
        return space;
    }

    public float getTotalHeight() {
        return totalHeight;
    }

    public float getCrouchTotalHeight() {
        return crouchTotalHeight;
    }

    public float getCurrentHeight() {
        if (isCrouched()) {
            return getCrouchTotalHeight();
        }
        return getTotalHeight();
    }

    public float getRadius() {
        return radius;
    }

    public CollisionShape getCollisionShape() {
        return this.collisionShape;
    }

    public CollisionShape getCrouchCollisionShape() {
        return this.crouchCollisionShape;
    }

    public CollisionShape getCurrentCollisionShape() {
        if (isCrouched()) {
            return getCrouchCollisionShape();
        }
        return getCollisionShape();
    }

    public PhysicsRigidBody getRigidBody() {
        return this.rigidBody;
    }

    public Vector3fc getPosition() {
        this.rigidBody.getPhysicsLocation(this.positionStore)
                .multLocal(Main.FROM_PHYSICS_ENGINE_UNITS);
        this.jomlPositionStore.set(this.positionStore.x, this.positionStore.y, this.positionStore.z);
        return this.jomlPositionStore;
    }

    public Vector3f getPosition(Vector3f receiver) {
        return receiver.set(getPosition());
    }

    public Vector3fc getInterpolatedPosition() {
        this.rigidBody.getMotionState()
                .getLocation(this.interpolatedPositionStore)
                .multLocal(Main.FROM_PHYSICS_ENGINE_UNITS);
        this.interpolatedJomlPositionStore.set(
                this.interpolatedPositionStore.x,
                this.interpolatedPositionStore.y,
                this.interpolatedPositionStore.z
        );
        return this.interpolatedJomlPositionStore;
    }

    public Vector3f getInterpolatedPosition(Vector3f receiver) {
        return receiver.set(getInterpolatedPosition());
    }

    public void setPosition(float x, float y, float z) {
        this.positionStore.set(x, y, z)
                .multLocal(Main.TO_PHYSICS_ENGINE_UNITS);
        this.rigidBody.setPhysicsLocation(this.positionStore);
    }

    public void setPosition(Vector3fc position) {
        setPosition(position.x(), position.y(), position.z());
    }

    public boolean isNoclipEnabled() {
        return noclipEnabled;
    }

    public void setNoclipEnabled(boolean noclipEnabled) {
        this.noclipEnabled = noclipEnabled;
        this.noclipStateChanged = true;
    }

    public boolean isCrouched() {
        return crouched;
    }

    public void setCrouched(boolean crouched) {
        this.crouchStateChanged = (this.crouched != crouched);
    }

    public boolean onGround() {
        return this.onGround;
    }

    public Vector3fc getGroundNormal() {
        return groundNormal;
    }

    public Vector3f getGroundNormal(Vector3f receiver) {
        return receiver.set(this.groundNormal);
    }

    public float getWalkDirectionX() {
        return this.walkDirectionX * this.walkDirectionSpeed;
    }

    public float getWalkDirectionZ() {
        return this.walkDirectionZ * this.walkDirectionSpeed;
    }

    public void setWalkDirection(float x, float z) {
        float length = ((float) Math.sqrt((x * x) + (z * z)));
        if (length == 0f) {
            this.walkDirectionSpeed = 0f;
            this.walkDirectionX = 0f;
            this.walkDirectionZ = 0f;
        } else {
            this.walkDirectionSpeed = length;
            float inv = 1f / length;
            this.walkDirectionX = x * inv;
            this.walkDirectionZ = z * inv;
        }
    }

    public float getGravityCoefficient() {
        return gravityCoefficient;
    }

    public void setGravityCoefficient(float gravityCoefficient) {
        this.gravityCoefficient = gravityCoefficient;
    }

    public float getAirMovementRoughness() {
        return airMovementRoughness;
    }

    public void setAirMovementRoughness(float airMovementRoughness) {
        this.airMovementRoughness = airMovementRoughness;
    }

    public float getGroundMovementRoughness() {
        return groundMovementRoughness;
    }

    public void setGroundMovementRoughness(float groundMovementRoughness) {
        this.groundMovementRoughness = groundMovementRoughness;
    }

    public float getGroundThreshold() {
        return groundThreshold;
    }

    public void setGroundThreshold(float groundThreshold) {
        this.groundThreshold = groundThreshold;
    }

    public float getAirFriction() {
        return airFriction;
    }

    public void setAirFriction(float airFriction) {
        this.airFriction = airFriction;
    }

    public float getGroundFriction() {
        return groundFriction;
    }

    public void setGroundFriction(float groundFriction) {
        this.groundFriction = groundFriction;
    }

    public void setStepUpHeight(float stepUpHeight) {
        this.stepUpHeight = stepUpHeight;
    }

    public float getStepUpHeight() {
        return stepUpHeight;
    }

    public void setStepUpMargin(float stepUpMargin) {
        this.stepUpMargin = stepUpMargin;
    }

    public float getStepUpMargin() {
        return stepUpMargin;
    }

    public float getStepDownHeight() {
        return stepDownHeight;
    }

    public void setStepDownHeight(float stepDownHeight) {
        this.stepDownHeight = stepDownHeight;
    }

    public float getStepMaxExternalSpeed() {
        return stepMaxExternalSpeed;
    }

    public void setStepMaxExternalSpeed(float stepMaxExternalSpeed) {
        this.stepMaxExternalSpeed = stepMaxExternalSpeed;
    }

    public void jump(float speed) {
        this.nextJumpImpulse += (speed * Main.TO_PHYSICS_ENGINE_UNITS);
    }

    public boolean isJumping() {
        return this.jump != 0f;
    }

    public boolean isGroundNormalInsideThreshold() {
        return this.groundNormal.dot(0f, 1f, 0f) >= this.groundThreshold;
    }

    public void checkedJump(float speed, float crouchSpeed) {
        if (!onGround() || isJumping() || isNoclipEnabled() || !isGroundNormalInsideThreshold()) {
            return;
        }
        if (isCrouched()) {
            jump(crouchSpeed);
        } else {
            jump(speed);
        }
    }

    public void setDepenetrationMargin(float depenetrationMargin) {
        this.depenetrationMargin = depenetrationMargin;
    }

    public float getDepenetrationMargin() {
        return depenetrationMargin;
    }

    public float getSweepTestTolerance() {
        return sweepTestTolerance;
    }

    public void setSweepTestTolerance(float sweepTestTolerance) {
        this.sweepTestTolerance = sweepTestTolerance;
    }

    public float getOnGroundThreshold() {
        return onGroundThreshold;
    }

    public void setOnGroundThreshold(float onGroundThreshold) {
        this.onGroundThreshold = onGroundThreshold;
    }

    public float getGravityCutoffTime() {
        return gravityCutoffTime;
    }

    public void setGravityCutoffTime(float gravityCutoffTime) {
        this.gravityCutoffTime = gravityCutoffTime;
    }

    private com.jme3.math.Vector3f physicsVelocity() {
        this.rigidBody.getLinearVelocity(this.physicsVelocity);
        return this.physicsVelocity;
    }

    private com.jme3.math.Vector3f physicsPosition() {
        this.rigidBody.getPhysicsLocation(this.physicsPosition);
        return this.physicsPosition;
    }

    private void physicsPosition(float x, float y, float z) {
        this.physicsPositionSet.set(x, y, z);
        this.rigidBody.setPhysicsLocation(this.physicsPositionSet);
    }

    private float physicsHeight() {
        return getCurrentHeight() * Main.TO_PHYSICS_ENGINE_UNITS;
    }

    private float physicsRadius() {
        return getRadius() * Main.TO_PHYSICS_ENGINE_UNITS;
    }

    private boolean physicsGravityEnabled() {
        return this.internalGravityCoefficient != 0f;
    }

    private boolean physicsWalking() {
        return this.walkDirectionSpeed != 0f;
    }

    private boolean externalVelocityInsideThreshold() {
        float externalLength = (float) Math.sqrt((this.externalX * this.externalX) + (this.externalY * this.externalY) + (this.externalZ * this.externalZ));
        return externalLength <= (this.stepMaxExternalSpeed * Main.TO_PHYSICS_ENGINE_UNITS);
    }

    private List<PhysicsSweepTestResult> boxSweepTest(
            PhysicsSpace space,
            float startX, float startY, float startZ,
            float endX, float endY, float endZ,
            float radius, float height, float penetration
    ) {
        com.jme3.math.Vector3f startPosition = this.sweepTestStartPosition.set(
                startX,
                startY,
                startZ
        );
        com.jme3.math.Vector3f endPosition = this.sweepTestEndPosition.set(
                endX,
                endY,
                endZ
        );

        this.sweepTestStart.setTranslation(startPosition);
        this.sweepTestEnd.setTranslation(endPosition);
        this.sweepTestBox.setScale(this.sweepTestScale.set(
                radius,
                height * 0.5f,
                radius
        ));

        List<PhysicsSweepTestResult> results = space.sweepTest(this.sweepTestBox,
                this.sweepTestStart,
                this.sweepTestEnd,
                new ArrayList<>(),
                penetration
        );
        results.sort((o1, o2) -> Float.compare(o1.getHitFraction(), o2.getHitFraction()));

        return results;
    }

    private boolean containsFilter(SweepTestFilter[] filters, SweepTestFilter filter) {
        if (filters == null || filters.length == 0) {
            return false;
        }
        for (SweepTestFilter e : filters) {
            if (filter.equals(e)) {
                return true;
            }
        }
        return false;
    }

    private void filterSweepResults(List<PhysicsSweepTestResult> results, Map<PhysicsSweepTestResult, com.jme3.math.Vector3f> normals, SweepTestFilter... filters) {
        List<PhysicsSweepTestResult> toRemove = new ArrayList<>();

        for (PhysicsSweepTestResult e : results) {
            PhysicsCollisionObject obj = e.getCollisionObject();
            if (obj == null) {
                toRemove.add(e);
                continue;
            }

            if (containsFilter(filters, SweepTestFilter.NO_PLAYER_NO_GHOSTS)) {
                if (obj.equals(this.rigidBody) || obj instanceof PhysicsGhostObject) {
                    toRemove.add(e);
                    continue;
                }
            }

            if (obj.getCollisionShape() instanceof MeshCollisionShape mesh) {
                mesh.getSubmesh(e.partIndex())
                        .copyTriangle(e.triangleIndex(), this.filterTriangle);
                this.filterTriangle.calculateNormal();
                
                com.jme3.math.Vector3f triangleNormal = this.filterTriangle.getNormal();
                this.filterNormal.set(
                        triangleNormal.x,
                        triangleNormal.y,
                        triangleNormal.z
                );
            } else {
                e.getHitNormalLocal(this.filterNormal);
            }

            boolean ceiling = this.filterNormal.y < -0.008f;
            boolean wall = this.filterNormal.y >= -0.008f && this.filterNormal.y <= 0.008f;
            boolean floor = this.filterNormal.y > 0.008f;
            boolean threshold = this.filterNormal.dot(com.jme3.math.Vector3f.UNIT_Y) >= this.groundThreshold;

            boolean removed = true;
            filter:
            {
                if (containsFilter(filters, SweepTestFilter.NO_CEILINGS) && ceiling) {
                    toRemove.add(e);
                    break filter;
                }
                if (containsFilter(filters, SweepTestFilter.NO_WALLS) && wall) {
                    toRemove.add(e);
                    break filter;
                }
                if (containsFilter(filters, SweepTestFilter.NO_FLOORS) && floor) {
                    toRemove.add(e);
                    break filter;
                }
                if (containsFilter(filters, SweepTestFilter.NO_OUTSIDE_OF_GROUND_THRESHOLD) && !threshold) {
                    toRemove.add(e);
                    break filter;
                }
                removed = false;
            }

            if (removed) {
                continue;
            }

            if (normals != null) {
                normals.put(e, new com.jme3.math.Vector3f(this.filterNormal));
            }
        }
        results.removeAll(toRemove);
    }

    private void applyVelocity(float x, float y, float z) {
        float mass = this.rigidBody.getMass();
        this.velocityApply.set(x * mass, y * mass, z * mass);
        this.rigidBody.applyCentralImpulse(this.velocityApply);
    }

    private Vector3fc spaceGravity(PhysicsSpace space) {
        space.getGravity(this.spaceGravity);
        this.spaceGravityGet.set(
                this.spaceGravity.x,
                this.spaceGravity.y,
                this.spaceGravity.z
        );
        return this.spaceGravityGet;
    }

    private float normalize(float value, float totalSum) {
        if (Math.abs(totalSum) < EPSILON) {
            return 0f;
        }
        return value /= totalSum;
    }

    private void checkNoclipState() {
        if (this.noclipStateChanged) {
            this.rigidBody.setContactResponse(!this.noclipEnabled);
            this.rigidBody.setKinematic(this.noclipEnabled);

            if (this.noclipEnabled) {
                this.rigidBody.setLinearVelocity(com.jme3.math.Vector3f.ZERO);

                this.walkX = 0f;
                this.walkY = 0f;
                this.walkZ = 0f;

                this.jump = 0f;

                this.gravityX = 0f;
                this.gravityY = 0f;
                this.gravityZ = 0f;
            }
        }
    }

    private void cutVelocity() {
        if (!physicsWalking()
                && !isJumping()
                && !physicsGravityEnabled()
                && physicsVelocity().length() < VELOCITY_CUTOFF) {
            this.rigidBody.setLinearVelocity(com.jme3.math.Vector3f.ZERO);
        }
    }

    private void checkIfShouldCrouch() {
        if (!this.crouchStateChanged) {
            return;
        }

        if (!isCrouched()) {
            com.jme3.math.Vector3f position = physicsPosition();

            this.rigidBody.setCollisionShape(this.crouchCollisionShape);
            this.crouchStateChanged = false;
            this.crouched = true;

            if (!onGround()) {
                physicsPosition(
                        position.x,
                        position.y + ((this.totalHeight - this.crouchTotalHeight) * Main.TO_PHYSICS_ENGINE_UNITS),
                        position.z
                );
                this.airCrouched = true;
            } else {
                this.airCrouched = false;
            }
        }
    }

    private void findGroundOrientedDirection() {
        this.groundOrientedWalkDirection.set(0f, 0f, 0f);
        if (this.walkDirectionX != 0f && this.walkDirectionZ != 0f) {
            Vector3f walkDir = this.orientedWalk.set(this.walkDirectionX, 0f, this.walkDirectionZ);

            Vector3fc normal = this.groundNormal;
            Vector3f tangent = walkDir.cross(normal, this.orientedTangent).normalize();
            Vector3f bitangent = normal.cross(tangent, this.orientedBitangent).normalize();

            this.groundOrientedWalkDirection.set(bitangent);
        }
    }

    private void disableGravityIfNeeded(float timeStep) {
        float externalLength = (float) Math.sqrt((this.externalX * this.externalX) + (this.externalY * this.externalY) + (this.externalZ * this.externalZ));
        if (!onGround() || !isGroundNormalInsideThreshold() || externalLength > (this.stepMaxExternalSpeed * Main.TO_PHYSICS_ENGINE_UNITS)) {
            this.gravityGroundCounter = 0f;
            this.internalGravityCoefficient = this.gravityCoefficient;
            return;
        }
        this.gravityGroundCounter += timeStep;
        if (this.gravityGroundCounter >= this.gravityCutoffTime) {
            this.gravityGroundCounter = this.gravityCutoffTime;
            this.internalGravityCoefficient = 0f;
        }
    }

    private void applyWalk(float timeStep) {
        float movementRoughness = (onGround() ? getGroundMovementRoughness() : getAirMovementRoughness());

        float targetX = this.groundOrientedWalkDirection.x() * this.walkDirectionSpeed * Main.TO_PHYSICS_ENGINE_UNITS;
        float targetY = this.groundOrientedWalkDirection.y() * this.walkDirectionSpeed * Main.TO_PHYSICS_ENGINE_UNITS;
        float targetZ = this.groundOrientedWalkDirection.z() * this.walkDirectionSpeed * Main.TO_PHYSICS_ENGINE_UNITS;

        float dX = targetX - this.walkX;
        float dY = targetY - this.walkY;
        float dZ = targetZ - this.walkZ;

        float dXStep = dX * timeStep * movementRoughness;
        float dYStep = dY * timeStep * movementRoughness;
        float dZStep = dZ * timeStep * movementRoughness;

        if (Math.abs(dXStep) > Math.abs(dX) || Math.abs(dX) < EPSILON) {
            dXStep = dX;
        }
        if (Math.abs(dYStep) > Math.abs(dY) || Math.abs(dY) < EPSILON) {
            dYStep = dY;
        }
        if (Math.abs(dZStep) > Math.abs(dZ) || Math.abs(dZ) < EPSILON) {
            dZStep = dZ;
        }

        this.appliedWalkX = dXStep;
        this.appliedWalkY = dYStep;
        this.appliedWalkZ = dZStep;

        applyVelocity(dXStep, dYStep, dZStep);
    }

    private void applyJump() {
        if (this.nextJumpImpulse == 0f) {
            this.appliedJump = 0f;
            return;
        }
        applyVelocity(
                0f,
                this.nextJumpImpulse + -this.gravityY,
                0f
        );
        this.appliedJump = this.nextJumpImpulse + -this.gravityY;
        this.nextJumpImpulse = 0f;
    }

    private void applyGravity(PhysicsSpace space, float timeStep) {
        Vector3fc gravity = spaceGravity(space);

        float vX = gravity.x() * timeStep * this.internalGravityCoefficient;
        float vY = gravity.y() * timeStep * this.internalGravityCoefficient;
        float vZ = gravity.z() * timeStep * this.internalGravityCoefficient;

        applyVelocity(vX, vY, vZ);

        this.appliedGravityX = vX;
        this.appliedGravityY = vY;
        this.appliedGravityZ = vZ;
    }

    private void calculateTotalVelocities() {
        this.appliedTotalX = this.appliedWalkX + this.appliedGravityX;
        this.appliedTotalY = this.appliedWalkY + this.appliedGravityY + this.appliedJump;
        this.appliedTotalZ = this.appliedWalkZ + this.appliedGravityZ;

        this.appliedWalkX = normalize(this.appliedWalkX, this.appliedTotalX);
        this.appliedWalkY = normalize(this.appliedWalkY, this.appliedTotalY);
        this.appliedWalkZ = normalize(this.appliedWalkZ, this.appliedTotalZ);

        this.appliedJump = normalize(this.appliedJump, this.appliedTotalY);

        this.appliedGravityX = normalize(this.appliedGravityX, this.appliedTotalX);
        this.appliedGravityY = normalize(this.appliedGravityY, this.appliedTotalY);
        this.appliedGravityZ = normalize(this.appliedGravityZ, this.appliedTotalZ);

        this.appliedWalkX = Math.max(this.appliedWalkX, 0f);
        this.appliedWalkY = Math.max(this.appliedWalkY, 0f);
        this.appliedWalkZ = Math.max(this.appliedWalkZ, 0f);

        this.appliedJump = Math.max(this.appliedJump, 0f);

        this.appliedGravityX = Math.max(this.appliedGravityX, 0f);
        this.appliedGravityY = Math.max(this.appliedGravityY, 0f);
        this.appliedGravityZ = Math.max(this.appliedGravityZ, 0f);

        float sumX = this.appliedWalkX + this.appliedGravityX;
        float sumY = this.appliedWalkY + this.appliedJump + this.appliedGravityY;
        float sumZ = this.appliedWalkZ + this.appliedGravityZ;

        this.appliedWalkX = normalize(this.appliedWalkX, sumX);
        this.appliedWalkY = normalize(this.appliedWalkY, sumY);
        this.appliedWalkZ = normalize(this.appliedWalkZ, sumZ);

        this.appliedJump = normalize(this.appliedJump, sumY);

        this.appliedGravityX = normalize(this.appliedGravityX, sumX);
        this.appliedGravityY = normalize(this.appliedGravityY, sumY);
        this.appliedGravityZ = normalize(this.appliedGravityZ, sumZ);

        com.jme3.math.Vector3f velocity = physicsVelocity();

        this.deltaX = velocity.x;
        this.deltaY = velocity.y;
        this.deltaZ = velocity.z;
    }

    private void stepUp(PhysicsSpace space) {
        if (isJumping()
                || physicsGravityEnabled()
                || !externalVelocityInsideThreshold()
                || !isGroundNormalInsideThreshold()) {
            return;
        }

        final float boxHeight = TEST_BOX_HEIGHT;
        final float yOffset = boxHeight * 0.5f;

        com.jme3.math.Vector3f position = physicsPosition();

        {
            float startX = position.x;
            float startY = position.y + yOffset + (physicsHeight() * (1f - this.sweepTestTolerance));
            float startZ = position.z;

            float endX = position.x;
            float endY = position.y + yOffset + physicsHeight() + (this.stepUpHeight * Main.TO_PHYSICS_ENGINE_UNITS);
            float endZ = position.z;

            List<PhysicsSweepTestResult> results = boxSweepTest(space,
                    startX, startY, startZ,
                    endX, endY, endZ,
                    physicsRadius(), TEST_BOX_HEIGHT, 0f
            );
            filterSweepResults(results, null,
                    SweepTestFilter.NO_PLAYER_NO_GHOSTS,
                    SweepTestFilter.NO_WALLS,
                    SweepTestFilter.NO_FLOORS
            );

            if (!results.isEmpty()) {
                return;
            }
        }

        float height;
        {
            float startX = position.x;
            float startY = position.y + (this.stepUpHeight * Main.TO_PHYSICS_ENGINE_UNITS) + yOffset;
            float startZ = position.z;

            float endX = position.x;
            float endY = position.y + yOffset;
            float endZ = position.z;

            List<PhysicsSweepTestResult> results = boxSweepTest(space,
                    startX, startY, startZ,
                    endX, endY, endZ,
                    physicsRadius() + this.stepUpMargin, TEST_BOX_HEIGHT, 0f
            );
            filterSweepResults(results, null,
                    SweepTestFilter.NO_PLAYER_NO_GHOSTS,
                    SweepTestFilter.NO_CEILINGS,
                    SweepTestFilter.NO_WALLS,
                    SweepTestFilter.NO_OUTSIDE_OF_GROUND_THRESHOLD
            );

            if (results.isEmpty()) {
                return;
            }

            float closest = results.get(0).getHitFraction();

            float hitY = (startY * (1f - closest)) + (endY * closest);
            hitY -= yOffset;

            height = hitY - position.y;

            if (height < STEP_UP_MINIMUM_HEIGHT) {
                return;
            }

            height += STEP_UP_EXTRA_HEIGHT;

            if (height >= (this.stepUpHeight * Main.TO_PHYSICS_ENGINE_UNITS)) {
                return;
            }
        }

        physicsPosition(
                position.x,
                position.y + height,
                position.z
        );
        this.stepUpExtraHeightTicks = STEP_UP_EXTRA_HEIGHT_TICKS;
        this.stepUpHeightDetected = height;
    }

    private void storePosition() {
        com.jme3.math.Vector3f position = physicsPosition();

        this.lastPositionX = position.x;
        this.lastPositionY = position.y;
        this.lastPositionZ = position.z;
    }

    @Override
    public void prePhysicsTick(PhysicsSpace space, float timeStep) {
        checkNoclipState();

        if (this.noclipEnabled) {
            return;
        }

        cutVelocity();

        checkIfShouldCrouch();
        findGroundOrientedDirection();
        disableGravityIfNeeded(timeStep);

        applyWalk(timeStep);
        applyJump();
        applyGravity(space, timeStep);
        calculateTotalVelocities();

        stepUp(space);
        storePosition();
    }

    private float clamp(float velocity, float max) {
        if (Math.signum(velocity) != Math.signum(max)) {
            velocity = 0f;
        } else if (Math.abs(velocity) > Math.abs(max)) {
            velocity = max;
        }
        return velocity;
    }

    private void collectAppliedVelocities() {
        com.jme3.math.Vector3f velocity = physicsVelocity();
        this.deltaX -= velocity.x;
        this.deltaY -= velocity.y;
        this.deltaZ -= velocity.z;

        this.appliedTotalX = clamp(this.appliedTotalX + this.deltaX, this.appliedTotalX);
        this.appliedTotalY = clamp(this.appliedTotalY + this.deltaY, this.appliedTotalY);
        this.appliedTotalZ = clamp(this.appliedTotalZ + this.deltaZ, this.appliedTotalZ);

        this.appliedWalkX *= this.appliedTotalX;
        this.appliedWalkY *= this.appliedTotalY;
        this.appliedWalkZ *= this.appliedTotalZ;

        this.appliedJump *= this.appliedTotalY;

        this.appliedGravityX *= this.appliedTotalX;
        this.appliedGravityY *= this.appliedTotalY;
        this.appliedGravityZ *= this.appliedTotalZ;

        this.walkX += this.appliedWalkX;
        this.walkY += this.appliedWalkY;
        this.walkZ += this.appliedWalkZ;

        this.jump += this.appliedJump;

        this.gravityX += this.appliedGravityX;
        this.gravityY += this.appliedGravityY;
        this.gravityZ += this.appliedGravityZ;

        this.internalX = this.walkX + this.gravityX;
        this.internalY = this.walkY + this.jump + this.gravityY;
        this.internalZ = this.walkZ + this.gravityZ;

        this.walkX = normalize(this.walkX, this.internalX);
        this.walkY = normalize(this.walkY, this.internalY);
        this.walkZ = normalize(this.walkZ, this.internalZ);

        this.jump = normalize(this.jump, this.internalY);

        this.gravityX = normalize(this.gravityX, this.internalX);
        this.gravityY = normalize(this.gravityY, this.internalY);
        this.gravityZ = normalize(this.gravityZ, this.internalZ);

        this.walkX = Math.max(this.walkX, 0f);
        this.walkY = Math.max(this.walkY, 0f);
        this.walkZ = Math.max(this.walkZ, 0f);

        this.jump = Math.max(this.jump, 0f);

        this.gravityX = Math.max(this.gravityX, 0f);
        this.gravityY = Math.max(this.gravityY, 0f);
        this.gravityZ = Math.max(this.gravityZ, 0f);

        this.internalX = clamp(this.internalX, velocity.x);
        this.internalY = clamp(this.internalY, velocity.y);
        this.internalZ = clamp(this.internalZ, velocity.z);

        this.walkX *= this.internalX;
        this.walkY *= this.internalY;
        this.walkZ *= this.internalZ;

        this.jump *= this.internalY;

        this.gravityX *= this.internalX;
        this.gravityY *= this.internalY;
        this.gravityZ *= this.internalZ;

        this.externalX = velocity.x - this.internalX;
        this.externalY = velocity.y - this.internalY;
        this.externalZ = velocity.z - this.internalZ;
    }

    private void applyFriction(float timestep) {
        float friction = (onGround() ? getGroundFriction() : getAirFriction());

        float dX = this.externalX;
        float dY = this.externalY;
        float dZ = this.externalZ;

        float fX = -dX * timestep * friction;
        float fY = -dY * timestep * friction;
        float fZ = -dZ * timestep * friction;

        if (Math.signum(fX + dX) != Math.signum(dX)) {
            fX = -dX;
        }
        if (Math.signum(fY + dY) != Math.signum(dY)) {
            fY = -dY;
        }
        if (Math.signum(fZ + dZ) != Math.signum(dZ)) {
            fZ = -dZ;
        }

        applyVelocity(fX, fY, fZ);

        this.externalX += fX;
        this.externalY += fY;
        this.externalZ += fZ;
    }

    private void blockTunneling(PhysicsSpace space) {
        com.jme3.math.Vector3f position = physicsPosition();
        float physicsHeight = physicsHeight();
        float physicsRadius = physicsRadius();
        float yOffset = (physicsHeight() * 0.5f);

        float startX = this.lastPositionX;
        float startY = this.lastPositionY + yOffset;
        float startZ = this.lastPositionZ;

        float endX = position.x;
        float endY = position.y + yOffset;
        float endZ = position.z;

        {
            List<PhysicsSweepTestResult> results = boxSweepTest(space,
                    startX, startY, startZ,
                    endX, endY, endZ,
                    physicsRadius, physicsHeight, this.depenetrationMargin
            );
            filterSweepResults(results, null, SweepTestFilter.NO_PLAYER_NO_GHOSTS);

            if (results.isEmpty()) {
                return;
            }
        }

        List<PhysicsSweepTestResult> results = boxSweepTest(space,
                startX, startY, startZ,
                endX, endY, endZ,
                physicsRadius, physicsHeight, PRECISE_BLOCK_TUNNELING_CCD_PENETRATION
        );
        filterSweepResults(results, null, SweepTestFilter.NO_PLAYER_NO_GHOSTS);

        float hit = results.get(0).getHitFraction();

        float hitX = (startX * (1f - hit)) + (endX * hit);
        float hitY = (startY * (1f - hit)) + (endY * hit);
        float hitZ = (startZ * (1f - hit)) + (endZ * hit);
        hitY -= yOffset;

        physicsPosition(hitX, hitY, hitZ);
    }

    private void stepDown(PhysicsSpace space) {
        this.onGround = false;
        this.groundNormal.set(0f, 1f, 0f);

        com.jme3.math.Vector3f position = physicsPosition();

        float height = Float.NaN;
        com.jme3.math.Vector3f foundNormal = null;
        findGroundDetails:
        {
            final float boxHeight = TEST_BOX_HEIGHT;
            final float yOffset = boxHeight * 0.5f;

            float startX = position.x;
            float startY = position.y + yOffset + (physicsHeight() * this.sweepTestTolerance);
            float startZ = position.z;

            float endX = position.x;
            float endY = (position.y + yOffset) - (this.stepDownHeight * Main.TO_PHYSICS_ENGINE_UNITS);
            float endZ = position.z;

            List<PhysicsSweepTestResult> results = boxSweepTest(space,
                    startX, startY, startZ,
                    endX, endY, endZ,
                    physicsRadius(), boxHeight, 0f
            );
            Map<PhysicsSweepTestResult, com.jme3.math.Vector3f> normals = new HashMap<>();
            filterSweepResults(results, normals,
                    SweepTestFilter.NO_PLAYER_NO_GHOSTS,
                    SweepTestFilter.NO_CEILINGS,
                    SweepTestFilter.NO_WALLS
            );

            if (results.isEmpty()) {
                break findGroundDetails;
            }

            PhysicsSweepTestResult closestResult = results.get(0);

            float closestHit = closestResult.getHitFraction();
            float yValue = (startY * (1f - closestHit)) + (endY * closestHit);
            yValue -= yOffset;

            height = yValue - position.y;
            foundNormal = normals.get(closestResult);
        }

        if (!Float.isFinite(height) || foundNormal == null) {
            return;
        }

        if (height > -(this.onGroundThreshold * Main.TO_PHYSICS_ENGINE_UNITS)) {
            this.onGround = true;
            this.groundNormal.set(foundNormal.x, foundNormal.y, foundNormal.z);
        }

        if (isJumping()
                || physicsGravityEnabled()
                || !externalVelocityInsideThreshold()
                || !isGroundNormalInsideThreshold()
                || height > -STEP_DOWN_MINIMUM_HEIGHT) {
            return;
        }

        if (this.stepUpExtraHeightTicks > 0) {
            this.stepUpExtraHeightTicks--;
            if (Math.abs(this.stepUpHeightDetected + height) > EPSILON) {
                height += STEP_UP_EXTRA_HEIGHT;
            }
        }

        physicsPosition(
                position.x,
                position.y + height,
                position.z
        );

        this.onGround = true;
        this.groundNormal.set(foundNormal.x, foundNormal.y, foundNormal.z);
    }

    private void checkIfShouldUncrouch(PhysicsSpace space) {
        if (!this.crouchStateChanged) {
            return;
        }

        if (isCrouched()) {
            com.jme3.math.Vector3f position = physicsPosition();

            final float boxHeight = TEST_BOX_HEIGHT;
            final float yOffset = boxHeight * 0.5f;

            boolean canUncrouch;
            {
                float startX = position.x;
                float startY = position.y + yOffset + ((this.crouchTotalHeight * Main.TO_PHYSICS_ENGINE_UNITS) * (1f - this.sweepTestTolerance));
                float startZ = position.z;

                float endX = position.x;
                float endY = position.y + yOffset + (this.totalHeight * Main.TO_PHYSICS_ENGINE_UNITS);
                float endZ = position.z;

                List<PhysicsSweepTestResult> results = boxSweepTest(space,
                        startX, startY, startZ,
                        endX, endY, endZ,
                        physicsRadius(), boxHeight, 0f
                );
                filterSweepResults(results, null,
                        SweepTestFilter.NO_PLAYER_NO_GHOSTS,
                        SweepTestFilter.NO_WALLS,
                        SweepTestFilter.NO_FLOORS
                );

                canUncrouch = results.isEmpty();
            }

            if (canUncrouch) {
                this.rigidBody.setCollisionShape(this.collisionShape);
                if (this.airCrouched) {
                    boolean canAirUncrouch;
                    {
                        float startX = position.x;
                        float startY = position.y + yOffset + ((this.crouchTotalHeight * Main.TO_PHYSICS_ENGINE_UNITS) * this.sweepTestTolerance);
                        float startZ = position.z;

                        float endX = position.x;
                        float endY = (position.y + yOffset) - ((this.totalHeight - this.crouchTotalHeight) * Main.TO_PHYSICS_ENGINE_UNITS);
                        float endZ = position.z;

                        List<PhysicsSweepTestResult> results = boxSweepTest(space,
                                startX, startY, startZ,
                                endX, endY, endZ,
                                physicsRadius(), boxHeight, 0f
                        );
                        filterSweepResults(results, null,
                                SweepTestFilter.NO_PLAYER_NO_GHOSTS,
                                SweepTestFilter.NO_CEILINGS,
                                SweepTestFilter.NO_WALLS
                        );

                        canAirUncrouch = results.isEmpty();
                    }

                    if (canAirUncrouch) {
                        physicsPosition(
                                position.x,
                                position.y - ((this.totalHeight - this.crouchTotalHeight) * Main.TO_PHYSICS_ENGINE_UNITS),
                                position.z
                        );
                    }
                }
                this.crouchStateChanged = false;
                this.crouched = false;
                this.airCrouched = false;
            }
        }
    }

    @Override
    public void physicsTick(PhysicsSpace space, float timeStep) {
        if (this.noclipEnabled) {
            return;
        }

        collectAppliedVelocities();
        applyFriction(timeStep);

        blockTunneling(space);
        stepDown(space);

        checkIfShouldUncrouch(space);
    }

}
