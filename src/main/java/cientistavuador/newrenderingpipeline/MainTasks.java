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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 *
 * @author Cien
 */
public class MainTasks {

    public static void init() {
        
    }
    
    public static final ConcurrentLinkedQueue<Runnable> MAIN_TASKS = new ConcurrentLinkedQueue<>();
    public static final Thread MAIN_THREAD = Thread.currentThread();
    
    public static boolean isMainThread() {
        return Thread.currentThread() == MAIN_THREAD;
    }
    
    public static void runTasks() {
        if (!isMainThread()) {
            throw new IllegalCallerException("Not main thread.");
        }
        
        Runnable r;
        while ((r = MAIN_TASKS.poll()) != null) {
            r.run();
        }
    }
    
    public static <T> CompletableFuture<T> run(Supplier<T> task) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        Runnable r = () -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        };
        
        if (isMainThread()) {
            r.run();
        } else {
            MAIN_TASKS.add(r);
        }
        
        return future;
    }
    
    public static CompletableFuture<Void> run(Runnable r) {
        return run(() -> {
            r.run();
            return null;
        });
    }
    
    private MainTasks() {

    }
}
