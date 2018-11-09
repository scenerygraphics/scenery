/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package graphics.scenery.utils

/**
 * Copyright (c) 2007-2009, JAGaToo Project Group all rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the 'Xith3D Project Group' nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) A
 * RISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE
 */

import graphics.scenery.ShaderProperty
import java.awt.Graphics2D
import java.awt.Transparency
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.ComponentColorModel
import java.awt.image.DataBuffer
import java.awt.image.PixelInterleavedSampleModel
import java.awt.image.SampleModel
import java.awt.image.WritableRaster
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer

import javax.imageio.ImageIO
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * This is a BufferedImage extension, that uses a DataBuffer, that stores its
 * data directly in a ByteBuffer.<br></br>
 * This allows for minimum memory usage, since no data is stored more than once.
 *
 * @author David Yazel
 * @author Marvin Froehlich (aka Qudus)
 */
class DirectBufferedImage private constructor(private val directType: Type/*, byte[] buffer*/, model: ColorModel, raster: WritableRaster, rasterPremultiplied: Boolean) : BufferedImage(model, raster, rasterPremultiplied, null) {

    private val numBytes: Int

    val byteBuffer: ByteBuffer
        get() {
            val dataBuffer = raster.dataBuffer as DirectDataBufferByte

            return dataBuffer.byteBuffer
        }

    enum class Type {
        DIRECT_RGB,
        DIRECT_RGBA,
        DIRECT_TWO_BYTES,
        DIRECT_ONE_BYTE
    }
    //private byte[] data;

    /*
    public void setDirectType( Type directType )
    {
        this.directType = directType;
    }
    */

    fun getDirectType(): Type {
        return directType
    }

    fun getNumBytes(): Int {
        return numBytes
    }

    private class DirectWritableRaster(width: Int, height: Int, bytesPerPixel: Int, bandOffsets: IntArray, dataBuffer: DirectDataBufferByte)/*
            this.scanLine = width * bytesPerPixel;
            this.bytesPerPixel = bytesPerPixel;
            this.bb = dataBuffer.getByteBuffer();
            */ : WritableRaster(createSampleModel(width, height, bytesPerPixel, bandOffsets), dataBuffer, java.awt.Point(0, 0)) {
        /*
        private final int scanLine;
        private final int bytesPerPixel;
        private final ByteBuffer bb;
        */


    }



    init {
        val field = this.javaClass.superclass.getDeclaredField("imageType")
        field.isAccessible = true
        field.set(this, 1)
        println("field is $field")
        this.numBytes = raster.dataBuffer.size
        //this.data = buffer;
    }

    companion object {
        private fun createSampleModel(width: Int, height: Int, bytesPerPixel: Int, bandOffsets: IntArray): SampleModel {

            return PixelInterleavedSampleModel(DataBuffer.TYPE_BYTE,
                width, height,
                bytesPerPixel,
                width * bytesPerPixel,
                bandOffsets
            )
        }

        private fun createBandOffsets(bytesPerPixel: Int): IntArray {
            val bandOffsets = IntArray(bytesPerPixel)

            for (i in bandOffsets.indices) {
                bandOffsets[i] = i
            }

            return bandOffsets
        }

        private fun createNumBitsArray(bytesPerPixel: Int): IntArray {
            val numBits = IntArray(bytesPerPixel)

            for (i in numBits.indices) {
                numBits[i] = 8
            }

            return numBits
        }

        /**
         * Creates a buffered image which is backed by a NIO byte buffer
         */
        fun makeDirectImageRGBA(width: Int, height: Int, bandOffsets: IntArray?, bb: ByteBuffer): DirectBufferedImage {
            var bandOffsets = bandOffsets
            val pixelSize = bb.limit() * 8 / (width * height)
            val bytesPerPixel = pixelSize / 8

            //int[] bandOffsets = createBandOffsets( bytesPerPixel );
            if (bandOffsets == null)
                bandOffsets = intArrayOf(3, 2, 1, 0)

            // create the backing store
            val buffer = DirectDataBufferByte(bb)

            // build the raster with 4 bytes per pixel

            val newRaster = DirectWritableRaster(width, height, bytesPerPixel, bandOffsets, buffer)

            // create a standard sRGB color space
            val cs = ColorSpace.getInstance(ColorSpace.CS_sRGB)

            // create a color model which has three 8 bit values for RGB
            val nBits = createNumBitsArray(bytesPerPixel)
            val cm = ComponentColorModel(cs, nBits, true, false, Transparency.TRANSLUCENT, 0)

            // create the buffered image

            return DirectBufferedImage(Type.DIRECT_RGBA/*, bb*/, cm, newRaster, false)
        }

        /**
         * Creates a buffered image which is backed by a NIO byte buffer
         */
        fun makeDirectImageRGBA(width: Int, height: Int, pixelSize: Int): DirectBufferedImage {
            val bytesPerPixel = pixelSize / 8

            //int[] bandOffsets = createBandOffsets( bytesPerPixel );
            val bandOffsets = intArrayOf(3, 2, 1, 0)

            // create the backing store

            //byte[] bb = new byte[ width * height * bytesPerPixel ];

            // create a data buffer wrapping the byte array

            //DataBufferByte buffer = new DataBufferByte( width * height * bytesPerPixel );
            val buffer = DirectDataBufferByte(width * height * bytesPerPixel)

            // build the raster with 4 bytes per pixel

            //WritableRaster newRaster = java.awt.image.Raster.createInterleavedRaster( buffer, width, height, width * bytesPerPixel, bytesPerPixel, bandOffsets, null );
            //WritableRaster newRaster = java.awt.image.Raster.createInterleavedRaster( 0, width, height, width * bytesPerPixel, bytesPerPixel, bandOffsets, null );

            //WritableRaster newRaster = new ByteBufferInterleavedRaster( DirectWritableRaster.createSampleModel( width, height, bytesPerPixel, bandOffsets ), new java.awt.Point( 0, 0 ) );

            //SampleModel sm = new PixelInterleavedSampleModel( DataBuffer.TYPE_BYTE, width, height, bytesPerPixel, width * bytesPerPixel, new int[] { 0 } );
            //WritableRaster newRaster = Raster.createWritableRaster( sm, buffer, null );
            val newRaster = DirectWritableRaster(width, height, bytesPerPixel, bandOffsets, buffer)

            // create a standard sRGB color space
            val cs = ColorSpace.getInstance(ColorSpace.CS_sRGB)

            // create a color model which has three 8 bit values for RGB
            val nBits = createNumBitsArray(bytesPerPixel)
            val cm = ComponentColorModel(cs, nBits, true, false, Transparency.TRANSLUCENT, 0)

            // create the buffered image

            return DirectBufferedImage(Type.DIRECT_RGBA/*, bb*/, cm, newRaster, false)
        }

        /**
         * Creates a buffered image which is backed by a NIO byte buffer
         */
        fun makeDirectImageRGBA(width: Int, height: Int): DirectBufferedImage {
            return makeDirectImageRGBA(width, height, 32)
        }

        /**
         * Creates a buffered image which is backed by a NIO byte buffer
         */
        fun makeDirectImageRGB(width: Int, height: Int, bandOffsets: IntArray?, bb: ByteBuffer): DirectBufferedImage {
            var bandOffsets = bandOffsets
            val pixelSize = bb.limit() * 8 / (width * height)
            val bytesPerPixel = pixelSize / 8

            if (bandOffsets == null)
                bandOffsets = createBandOffsets(bytesPerPixel)

            // create the backing store

            // create a data buffer wrapping the byte array
            val buffer = DirectDataBufferByte(bb)

            // build the raster with 3 bytes per pixel
            val newRaster = DirectWritableRaster(width, height, bytesPerPixel, bandOffsets, buffer)

            // create a standard sRGB color space
            val cs = ColorSpace.getInstance(ColorSpace.CS_sRGB)

            // create a color model which has three 8 bit values for RGB
            val nBits = createNumBitsArray(bytesPerPixel)
            val cm = ComponentColorModel(cs, nBits, false, false, Transparency.OPAQUE, 0)

            // create the buffered image

            return DirectBufferedImage(Type.DIRECT_RGB/*, bb*/, cm, newRaster, false)
        }

        /**
         * Creates a buffered image which is backed by a NIO byte buffer
         */
        fun makeDirectImageRGB(width: Int, height: Int, pixelSize: Int): DirectBufferedImage {
            val bytesPerPixel = pixelSize / 8

            val bandOffsets = createBandOffsets(bytesPerPixel)

            // create the backing store
            //byte bb[] = (backingStore == null) ? new byte[ width * height * bytesPerPixel ] : backingStore;

            // create a data buffer wrapping the byte array
            //DataBuffer buffer = new DataBufferByte( bb, width * height * bytesPerPixel );
            val buffer = DirectDataBufferByte(width * height * bytesPerPixel)

            // build the raster with 3 bytes per pixel
            //WritableRaster newRaster = java.awt.image.Raster.createInterleavedRaster( buffer, width, height, width * bytesPerPixel, bytesPerPixel, bandOffsets, null );
            val newRaster = DirectWritableRaster(width, height, bytesPerPixel, bandOffsets, buffer)

            // create a standard sRGB color space
            val cs = ColorSpace.getInstance(ColorSpace.CS_sRGB)

            // create a color model which has three 8 bit values for RGB
            val nBits = createNumBitsArray(bytesPerPixel)
            val cm = ComponentColorModel(cs, nBits, false, false, Transparency.OPAQUE, 0)

            // create the buffered image

            return DirectBufferedImage(Type.DIRECT_RGB/*, bb*/, cm, newRaster, false)
        }

        /**
         * Creates a buffered image which is backed by a NIO byte buffer
         */
        fun makeDirectImageRGB(width: Int, height: Int): DirectBufferedImage {
            return makeDirectImageRGB(width, height, 24)
        }

        /**
         * Takes the source buffered image and converts it to a buffered image which
         * is backed by a direct byte buffer
         *
         * @param source
         * @return the DirectBufferedImage
         */
        fun makeDirectImageRGB(source: BufferedImage): DirectBufferedImage {
            val dest = makeDirectImageRGB(source.width, source.height)
            source.copyData(dest.raster)

            return dest
        }

        fun makeDirectImageTwoBytes(width: Int, height: Int, pixelSize: Int): DirectBufferedImage {
            val bytesPerPixel = pixelSize / 8

            val bandOffsets = createBandOffsets(bytesPerPixel)

            // create the backing store
            //byte bb[] = new byte[ width * height * bytesPerPixel ];

            // create a data buffer wrapping the byte array
            //DataBuffer buffer = new DataBufferByte( bb, width * height * bytesPerPixel );
            val buffer = DirectDataBufferByte(width * height * bytesPerPixel)

            // build the raster with 2 bytes per pixel
            //WritableRaster newRaster = Raster.createInterleavedRaster( buffer, width, height, width * bytesPerPixel,  bytesPerPixel, bandOffsets, null );
            val newRaster = DirectWritableRaster(width, height, bytesPerPixel, bandOffsets, buffer)

            // create a standard sRGB color space
            val cs = ColorSpace.getInstance(ColorSpace.CS_GRAY) // FIXME: We actually need two bytes!!!

            // create a color model which has two 8 bit values for luminance and alpha
            val nBits = createNumBitsArray(bytesPerPixel)
            val cm = ComponentColorModel(cs, nBits, false, false, Transparency.OPAQUE, 0)

            // create the buffered image

            return DirectBufferedImage(Type.DIRECT_ONE_BYTE/*, bb*/, cm, newRaster, false)
        }

        /**
         * Creates a buffered image which is backed by a NIO byte buffer
         */
        fun makeDirectImageTwoBytes(width: Int, height: Int): DirectBufferedImage {
            return makeDirectImageRGBA(width, height, 16)
        }

        fun makeDirectImageOneByte(width: Int, height: Int): DirectBufferedImage {
            val bytesPerPixel = 1

            val bandOffsets = createBandOffsets(bytesPerPixel)

            // create the backing store
            //byte bb[] = new byte[ width * height * bytesPerPixel ];

            // create a data buffer wrapping the byte array
            //DataBuffer buffer = new DataBufferByte( bb, width * height * bytesPerPixel );
            val buffer = DirectDataBufferByte(width * height * bytesPerPixel)

            // build the raster with 1 byte per pixel
            //WritableRaster newRaster = Raster.createInterleavedRaster( buffer, width, height, width * bytesPerPixel,  bytesPerPixel, bandOffsets, null );
            val newRaster = DirectWritableRaster(width, height, bytesPerPixel, bandOffsets, buffer)

            // create a standard sRGB color space
            val cs = ColorSpace.getInstance(ColorSpace.CS_GRAY)

            // create a color model which has one 8 bit value for GRAY
            val nBits = createNumBitsArray(bytesPerPixel)
            val cm = ComponentColorModel(cs, nBits, false, false, Transparency.OPAQUE, 0)

            // create the buffered image

            return DirectBufferedImage(Type.DIRECT_ONE_BYTE/*, bb*/, cm, newRaster, false)
        }

        /**
         * takes the source buffered image and converts it to a buffered image which
         * is backed by a direct byte buffer
         *
         * @param source
         */
        fun makeDirectImageRGBA(source: BufferedImage): DirectBufferedImage {
            val dest = makeDirectImageRGBA(source.width, source.height)
            source.copyData(dest.raster)

            return dest
        }

        fun make(type: Type, width: Int, height: Int): DirectBufferedImage {
            when (type) {
                Type.DIRECT_RGBA -> return makeDirectImageRGBA(width, height)

                Type.DIRECT_RGB -> return makeDirectImageRGB(width, height)

                Type.DIRECT_TWO_BYTES -> return makeDirectImageTwoBytes(width, height)

                Type.DIRECT_ONE_BYTE -> return makeDirectImageOneByte(width, height)
            }

            throw Error("Unknown direct image type $type")
        }


        private fun convertViaDrawing(source: BufferedImage, dest: DirectBufferedImage): DirectBufferedImage {
            val g = dest.graphics as Graphics2D
            g.drawImage(source, 0, 0, dest.width, dest.height, null)

            return dest
        }

        fun make(bi: BufferedImage, allowAlpha: Boolean): DirectBufferedImage {
            val hasAlpha = bi.colorModel.hasAlpha() && !bi.colorModel.isAlphaPremultiplied

            return if (hasAlpha && allowAlpha) {
                convertViaDrawing(bi, makeDirectImageRGBA(bi.width, bi.height))
            } else convertViaDrawing(bi, makeDirectImageRGB(bi.width, bi.height))

        }

        fun make(bi: BufferedImage): DirectBufferedImage {
            return make(bi, true)
        }

        /**
         * reads in an image using image io. It then detects if this is a RGBA or
         * RGB image and converts it to the appropriate direct image. Unfortunly
         * this does mean we are loading a buffered image which is thrown away, but
         * there is no help for that currently.
         *
         * @param in
         * @parma allowAlpha
         *
         * @throws java.io.IOException
         */
        @Throws(IOException::class)
        fun loadDirectImage(`in`: InputStream, allowAlpha: Boolean): DirectBufferedImage {
            var `in` = `in`
            if (`in` !is BufferedInputStream)
                `in` = BufferedInputStream(`in`)

            val bi = ImageIO.read(`in`)

            return make(bi, allowAlpha)
        }

        /**
         * reads in an image using image io. It then detects if this is a RGBA or
         * RGB image and converts it to the appropriate direct image. Unfortunly
         * this does mean we are loading a buffered image which is thrown away, but
         * there is no help for that currently.
         *
         * @param in
         *
         * @throws java.io.IOException
         */
        @Throws(IOException::class)
        fun loadDirectImage(`in`: InputStream): DirectBufferedImage {
            return loadDirectImage(`in`, true)
        }

        /**
         * reads in an image using image io. It then detects if this is a RGBA or
         * RGB image and converts it to the appropriate direct image. Unfortunly
         * this does mean we are loading a buffered image which is thrown away, but
         * there is no help for that currently.
         *
         * @param url
         * @param allowAlpha
         *
         * @throws java.io.IOException
         */
        @Throws(IOException::class)
        fun loadDirectImage(url: URL, allowAlpha: Boolean): DirectBufferedImage {
            return loadDirectImage(url.openStream(), allowAlpha)
        }

        @Throws(java.io.IOException::class)
        fun loadDirectImage(url: URL): BufferedImage {
            return loadDirectImage(url, true)
        }

        /**
         * reads in an image using image io. It then detects if this is a RGBA or
         * RGB image and converts it to the appropriate direct image. Unfortunly
         * this does mean we are loading a buffered image which is thrown away, but
         * there is no help for that currently.
         *
         * @param file
         * @param allowAlpha
         *
         * @throws java.io.IOException
         */
        @Throws(IOException::class)
        fun loadDirectImage(file: File, allowAlpha: Boolean): DirectBufferedImage {
            return loadDirectImage(FileInputStream(file), allowAlpha)
        }

        /**
         * reads in an image using image io. It then detects if this is a RGBA or
         * RGB image and converts it to the appropriate direct image. Unfortunly
         * this does mean we are loading a buffered image which is thrown away, but
         * there is no help for that currently.
         *
         * @param file
         *
         * @throws java.io.IOException
         */
        @Throws(IOException::class)
        fun loadDirectImage(file: File): DirectBufferedImage {
            return loadDirectImage(file, true)
        }

        /**
         * reads in an image using image io. It then detects if this is a RGBA or
         * RGB image and converts it to the appropriate direct image. Unfortunly
         * this does mean we are loading a buffered image which is thrown away, but
         * there is no help for that currently.
         *
         * @param name
         *
         * @throws java.io.IOException
         */
        @Throws(IOException::class)
        fun loadDirectImage(name: String, allowAlpha: Boolean): DirectBufferedImage {
            return loadDirectImage(File(name), allowAlpha)
        }

        @Throws(IOException::class)
        fun loadDirectImage(name: String): DirectBufferedImage {
            return loadDirectImage(name, true)
        }
    }
}
