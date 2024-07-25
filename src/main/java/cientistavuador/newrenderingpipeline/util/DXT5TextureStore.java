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

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.lwjgl.stb.STBImage.*;
import org.lwjgl.system.MemoryStack;
import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.util.zstd.Zstd.*;
import static org.lwjgl.util.zstd.ZstdX.*;

/**
 *
 * @author Cien
 */
public class DXT5TextureStore {

    public static final String ZST_EXTENSION = "zst";
    public static final String DDS_EXTENSION = "dds";
    public static final String EXTENSION = DDS_EXTENSION + "." + ZST_EXTENSION;

    public static class DXT5Texture {

        public static final int DDS_MAGIC_NUMBER = 0x20_53_44_44;
        public static final int DXT5_TYPE = 0x35_54_58_44;

        public static final int MAGIC_NUMBER_OFFSET = 0x00;
        public static final int WIDTH_OFFSET = 0x0C;
        public static final int HEIGHT_OFFSET = 0x10;
        public static final int MIPS_OFFSET = 0x1C;
        public static final int TYPE_OFFSET = 0x54;

        private final int width;
        private final int height;
        private final int mips;
        private final int[] mipsWidth;
        private final int[] mipsHeight;
        private final int[] mipsOffset;
        private final int[] mipsSize;

        private WeakReference<byte[]> decompressed = null;

        private static class WrappedBuffer {

            ByteBuffer buffer;
        }

        private final WrappedBuffer wrappedBuffer;

        public DXT5Texture(ByteBuffer buffer) {
            if (buffer == null) {
                throw new NullPointerException("Buffer is null.");
            }

            this.wrappedBuffer = new WrappedBuffer();
            this.wrappedBuffer.buffer = buffer;

            if (buffer.capacity() < 128) {
                throw new IllegalArgumentException("Invalid DXT5 Buffer, too small!");
            }

            buffer
                    .position(0)
                    .limit(buffer.capacity())
                    .order(ByteOrder.LITTLE_ENDIAN);

            if (buffer.getInt(MAGIC_NUMBER_OFFSET) != DDS_MAGIC_NUMBER) {
                throw new IllegalArgumentException("Invalid DXT5 Buffer, invalid magic!");
            }

            if (buffer.getInt(TYPE_OFFSET) != DXT5_TYPE) {
                throw new IllegalArgumentException("Invalid DXT5 Buffer, invalid type!");
            }

            this.width = buffer.getInt(WIDTH_OFFSET);
            this.height = buffer.getInt(HEIGHT_OFFSET);
            this.mips = buffer.getInt(MIPS_OFFSET);

            if (this.width < 0 || this.width > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Texture too large!");
            }
            if (this.height < 0 || this.height > Short.MAX_VALUE) {
                throw new IllegalArgumentException("Texture too large!");
            }

            if (this.mips != MipmapUtils.numberOfMipmaps(this.width, this.height)) {
                throw new IllegalArgumentException("Invalid amount of mips! required " + MipmapUtils.numberOfMipmaps(this.width, this.height));
            }

            this.mipsWidth = new int[this.mips];
            this.mipsHeight = new int[this.mips];
            this.mipsOffset = new int[this.mips];
            this.mipsSize = new int[this.mips];

            int offset = 128;
            for (int i = 0; i < this.mips; i++) {
                this.mipsWidth[i] = MipmapUtils.mipmapSize(this.width, i);
                this.mipsHeight[i] = MipmapUtils.mipmapSize(this.height, i);
                this.mipsSize[i] = TextureCompressor.DXT5OrBCH6Size(
                        this.mipsWidth[i],
                        this.mipsHeight[i]
                );
                this.mipsOffset[i] = offset;
                offset += this.mipsSize[i];
            }

            if (buffer.capacity() != offset) {
                throw new IllegalArgumentException("Invalid DXT5 Buffer Total Size! Required " + offset + " bytes!");
            }

            registerForCleaning();
        }

        private void registerForCleaning() {
            final WrappedBuffer wrapped = this.wrappedBuffer;
            ObjectCleaner.get().register(this, () -> {
                if (wrapped.buffer == null) {
                    return;
                }
                memFree(wrapped.buffer);
                wrapped.buffer = null;
            });
        }

        public int width() {
            return this.width;
        }

        public int height() {
            return this.height;
        }

        public int mips() {
            return this.mips;
        }

        public int mipWidth(int level) {
            return this.mipsWidth[level];
        }

        public int mipHeight(int level) {
            return this.mipsHeight[level];
        }

        public int mipOffset(int level) {
            return this.mipsOffset[level];
        }

        public int mipSize(int level) {
            return this.mipsSize[level];
        }

        public ByteBuffer buffer() {
            return this.wrappedBuffer.buffer;
        }

        public ByteBuffer bufferSlice() {
            return buffer().slice(0, buffer().capacity());
        }

        public ByteBuffer mipSlice(int level) {
            return buffer().slice(mipOffset(level), mipSize(level));
        }

        public byte[] decompress() {
            if (this.decompressed != null) {
                byte[] cached = this.decompressed.get();
                if (cached != null) {
                    return cached;
                } else {
                    this.decompressed = null;
                }
            }

            if (!TextureCompressor.isAnySupported()) {
                throw new IllegalArgumentException("Texture Decompression not supported!");
            }

            try {
                Path workDir = TextureCompressor.createTempCompressorFolder();

                File workDirFile = workDir.toFile();
                File inputFile = workDir.resolve("input.dds").toFile();
                File outputFile = workDir.resolve("output.png").toFile();

                workDirFile.deleteOnExit();

                StringBuilder log = new StringBuilder();

                {
                    ByteBuffer slice = bufferSlice();
                    byte[] ddsArray = new byte[slice.capacity()];
                    slice.get(ddsArray);

                    Files.write(inputFile.toPath(), ddsArray);
                }

                inputFile.deleteOnExit();

                Process p;
                if (TextureCompressor.isNVIDIATextureToolsSupported()) {
                    p = TextureCompressor.callNVIDIATextureTools(workDirFile, false, "-format png " + inputFile.getName() + " " + outputFile.getName());
                } else if (TextureCompressor.isAMDCompressonatorSupported()) {
                    p = TextureCompressor.callAMDCompressonator(workDirFile, inputFile.getName() + " " + outputFile.getName());
                } else {
                    throw new UnsupportedOperationException("Decompression not supported.");
                }

                BufferedReader reader = p.inputReader();
                String s;
                while ((s = reader.readLine()) != null) {
                    log.append(s).append('\n');
                }

                try {
                    p.waitFor();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }

                inputFile.delete();

                if (!outputFile.exists()) {
                    System.out.println("Output Log:");
                    System.out.println(log.toString());
                    throw new IOException("Output file not generated!");
                }

                outputFile.deleteOnExit();

                byte[] decompressedImage;

                stbi_set_flip_vertically_on_load_thread(0);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.callocInt(1);
                    IntBuffer h = stack.callocInt(1);
                    IntBuffer channels = stack.callocInt(1);
                    ByteBuffer imageData = stbi_load(outputFile.getAbsolutePath(), w, h, channels, 4);
                    if (imageData == null) {
                        throw new IOException("Failed to load image: " + stbi_failure_reason());
                    }
                    try {
                        if (w.get() != this.width || h.get() != this.height) {
                            throw new IOException("Corrupted image, width and height are different.");
                        }
                        decompressedImage = new byte[this.width * this.height * 4];
                        imageData.get(decompressedImage).flip();
                    } finally {
                        stbi_image_free(imageData);
                    }
                }

                outputFile.delete();
                workDirFile.delete();

                this.decompressed = new WeakReference<>(decompressedImage);

                return decompressedImage;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        public void free() {
            if (this.wrappedBuffer.buffer == null) {
                throw new IllegalArgumentException("Already freed!");
            }
            memFree(this.wrappedBuffer.buffer);
            this.wrappedBuffer.buffer = null;
        }

    }

    public static DXT5Texture createDXT5Texture(byte[] data, int width, int height) {
        if (!TextureCompressor.isAnySupported()) {
            throw new IllegalArgumentException("Texture Compression not supported!");
        }

        ImageUtils.validate(data, width, height, 4);
        int amountOfMips = MipmapUtils.numberOfMipmaps(width, height);

        try {
            Path workDir = TextureCompressor.createTempCompressorFolder();

            File workDirFile = workDir.toFile();
            File inputFile = workDir.resolve("input.png").toFile();
            File outputFile = workDir.resolve("output.dds").toFile();

            workDirFile.deleteOnExit();

            StringBuilder log = new StringBuilder();

            {
                BufferedImage img = ImageUtils.toBufferedImage(ImageUtils.asImage(data, width, height, 4));
                ImageIO.write(img, "PNG", inputFile);
            }

            inputFile.deleteOnExit();

            Process p;
            if (TextureCompressor.isNVIDIATextureToolsSupported()) {
                p = TextureCompressor.callNVIDIATextureTools(workDirFile, true, "-bc3 -max-mip-count " + amountOfMips + " -highest " + inputFile.getName() + " " + outputFile.getName());
            } else if (TextureCompressor.isAMDCompressonatorSupported()) {
                p = TextureCompressor.callAMDCompressonator(workDirFile, "-fd DXT5 -miplevels " + amountOfMips + " " + inputFile.getName() + " " + outputFile.getName());
            } else {
                throw new UnsupportedOperationException("Compression not supported.");
            }

            BufferedReader reader = p.inputReader();
            String s;
            while ((s = reader.readLine()) != null) {
                log.append(s).append('\n');
            }

            try {
                p.waitFor();
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            inputFile.delete();

            if (!outputFile.exists()) {
                System.out.println("Output Log:");
                System.out.println(log.toString());
                throw new IOException("Output file not generated!");
            }

            outputFile.deleteOnExit();

            byte[] ddsData = Files.readAllBytes(outputFile.toPath());

            outputFile.delete();
            workDirFile.delete();

            ByteBuffer nativeMemory = memAlloc(ddsData.length).put(ddsData).flip();
            try {
                return new DXT5Texture(nativeMemory);
            } catch (Throwable t) {
                memFree(nativeMemory);
                throw t;
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static void writeDXT5Texture(DXT5Texture texture, OutputStream out) throws IOException {
        ByteBuffer toCompress = texture.bufferSlice();
        ByteBuffer compressed = memAlloc((int) ZSTD_compressBound(toCompress.capacity()));
        try {
            long size = ZSTD_compress(compressed, toCompress, Math.min(19, ZSTD_maxCLevel()));
            if (ZSTD_isError(size)) {
                throw new IOException("ZSTD Error: " + ZSTD_getErrorName(size));
            }

            byte[] buffer = new byte[16384];
            int toWrite = (int) Math.min(size, buffer.length);
            while (toWrite != 0) {
                compressed.get(buffer, 0, toWrite);
                out.write(buffer, 0, toWrite);

                size -= toWrite;
                toWrite = (int) Math.min(size, buffer.length);
            }

            compressed.flip();
        } finally {
            memFree(compressed);
        }
    }

    @SuppressWarnings("UseSpecificCatch")
    public static DXT5Texture readDXT5Texture(InputStream in) throws IOException {
        ByteBuffer toDecompress;
        {
            byte[] data = in.readAllBytes();
            toDecompress = memAlloc(data.length).put(data).flip();
        }
        ByteBuffer decompressed;
        try {
            long decompressedSize = ZSTD_decompressBound(toDecompress);
            if (decompressedSize == ZSTD_CONTENTSIZE_ERROR) {
                throw new IOException("Invalid ZSTD File! " + decompressedSize);
            }
            if (decompressedSize < 0 || decompressedSize > Integer.MAX_VALUE) {
                throw new IOException("Too large compressed file! " + decompressedSize);
            }
            decompressed = memAlloc((int) decompressedSize);
            try {
                long output = ZSTD_decompress(decompressed, toDecompress);
                if (ZSTD_isError(output)) {
                    throw new IOException("ZSTD Error: " + ZSTD_getErrorName(output));
                }
                if (output != decompressedSize) {
                    throw new IOException("Output size is not the same as decompressed size!");
                }
            } catch (Throwable t) {
                memFree(decompressed);
                throw t;
            }
        } finally {
            memFree(toDecompress);
        }

        try {
            return new DXT5Texture(decompressed);
        } catch (Throwable t) {
            memFree(decompressed);
            throw t;
        }
    }

    private DXT5TextureStore() {

    }

}
