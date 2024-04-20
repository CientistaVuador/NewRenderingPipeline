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

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Cien
 */
public class NAnimation {

    private final String name;
    private final float duration;
    private final float ticksPerSecond;
    private final NBoneAnimation[] boneAnimations;
    
    private final Map<String, Integer> boneMap = new HashMap<>();
    
    public NAnimation(String name, float duration, float ticksPerSecond, NBoneAnimation[] boneAnimations) {
        this.name = name;
        this.duration = duration;
        this.ticksPerSecond = ticksPerSecond;
        this.boneAnimations = boneAnimations.clone();
        
        for (int i = 0; i < this.boneAnimations.length; i++) {
            this.boneMap.put(this.boneAnimations[i].getBoneName(), i);
        }
    }

    public String getName() {
        return name;
    }

    public float getDuration() {
        return duration;
    }

    public float getTicksPerSecond() {
        return ticksPerSecond;
    }
    
    public int numberOfBoneAnimations() {
        return this.boneAnimations.length;
    }
    
    public NBoneAnimation getBoneAnimation(int index) {
        return this.boneAnimations[index];
    }
    
    public NBoneAnimation getBoneAnimation(String name) {
        Integer index = this.boneMap.get(name);
        if (index == null) {
            return null;
        }
        return getBoneAnimation(index);
    }
    
}
