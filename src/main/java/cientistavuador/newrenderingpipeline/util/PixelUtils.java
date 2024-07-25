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

/**
 *
 * @author Cien
 */
public class PixelUtils {
    
    public static boolean dither50(int x, int y) {
        return (x % 2 == 0 && y % 2 == 0) 
               || (x % 2 != 0 && y % 2 != 0);
    }
    
    public static boolean dither25(int x, int y) {
        return (y % 2 == 0) 
                && 
                (
                    (((y / 2) % 2 == 0 && x % 2 == 0))
                    ||
                    (((y / 2) % 2 != 0 && x % 2 != 0))
                );
    }
    
    public static class PixelStructure {
        private final int width;
        private final int height;
        private final int components;
        private final boolean clampToEdge;
        
        private PixelStructure(int width, int height, int components, boolean clampToEdge) {
            this.width = width;
            this.height = height;
            this.components = components;
            this.clampToEdge = clampToEdge;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int components() {
            return components;
        }

        public boolean clampToEdge() {
            return clampToEdge;
        }
        
    }
    
    public static PixelStructure getPixelStructure(int width, int height, int components, boolean clampToEdge) {
        return new PixelStructure(width, height, components, clampToEdge);
    }
    
    public static int getPixelComponentIndex(PixelStructure storage, int x, int y, int component) {
        if (storage.clampToEdge()) {
            x = Math.min(Math.max(x, 0), storage.width() - 1);
            y = Math.min(Math.max(y, 0), storage.height() - 1);
        }
        return component + (x * storage.components()) + (y * storage.width() * storage.components());
    }
    
    private PixelUtils() {
        
    }
}
