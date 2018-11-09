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

import org.lwjgl.BufferUtils
import java.awt.image.DataBuffer
import java.nio.ByteBuffer

import org.lwjgl.BufferUtils.createByteBuffer
import kotlin.experimental.and

/**
 * Concrete class which backs a data buffer with a native [ByteBuffer].
 *
 * @author David Yazel
 * @author Marvin Froehlich (aka Qudus)
 */
class DirectDataBufferByte : DataBuffer {
    private val bb: ByteBuffer

    val byteBuffer: ByteBuffer
        get() = bb

    /**
     * {@inheritDoc}
     */
    override fun getElem(bank: Int, i: Int): Int {
        return bb.get(i).toInt()
    }

    /**
     * {@inheritDoc}
     */
    override fun setElem(bank: Int, i: Int, `val`: Int) {
        bb.put(i, `val`.toByte())
    }

    constructor(bb: ByteBuffer) : super(DataBuffer.TYPE_BYTE, bb.capacity()) {

        this.bb = bb
        //this.bb.limit( bb.capacity() );
        //this.bb.position( 0 );
    }

    constructor(size: Int) : super(DataBuffer.TYPE_BYTE, size) {

        bb = BufferUtils.createByteBuffer(size)
        bb.limit(size)
        bb.position(0)
    }
}
