/*
 * ScalaCL - putting Scala on the GPU with JavaCL / OpenCL
 * http://scalacl.googlecode.com/
 *
 * Copyright (c) 2009-2013, Olivier Chafik (http://ochafik.com/)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Olivier Chafik nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY OLIVIER CHAFIK AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS AND CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package scalacl
package impl

import com.nativelibs4java.opencl.{ CLMem, CLEvent }
import org.bridj.{ Pointer, PointerIO }
import scala.collection.mutable.ArrayBuffer

private[impl] abstract class TupleDataIO[T : Manifest] extends DataIO[T] {
  
  override def toArray(length: Int, buffers: Array[ScheduledBuffer[_]]): Array[T] = {
    val eventsToWaitFor = new ArrayBuffer[CLEvent]
    val pointers = buffers.map(_.read(eventsToWaitFor).withoutValidityInformation) // unsafe, but faster
    CLEvent.waitFor(eventsToWaitFor.toArray: _*)
    (0 until length.toInt).par.map(i => get(i, pointers, 0)).toArray // TODO check
  }
}

class Tuple2DataIO[T1 : Manifest : DataIO, T2 : Manifest : DataIO]
  extends TupleDataIO[(T1, T2)] {
  val io1 = implicitly[DataIO[T1]]
  val io2 = implicitly[DataIO[T2]]
  
  override def typeString = "(" + io1.typeString + ", " + io2.typeString + ")"
  override val bufferCount = io1.bufferCount + io2.bufferCount
  private[scalacl] override def foreachScalar(f: ScalarDataIO[_] => Unit) {
    io1.foreachScalar(f)
    io2.foreachScalar(f)
  }
  override def allocateBuffers(length: Long, out: ArrayBuffer[ScheduledBuffer[_]])(implicit context: Context) = {
    io1.allocateBuffers(length, out)
    io2.allocateBuffers(length, out)
  }
  override def get(index: Long, buffers: Array[Pointer[_]], bufferOffset: Int) = {
    val v1 = io1.get(index, buffers, bufferOffset)
    val v2 = io2.get(index, buffers, bufferOffset + io1.bufferCount)
    (v1, v2)
  }

  override def set(index: Long, buffers: Array[Pointer[_]], bufferOffset: Int, value: (T1, T2)) = {
    val (v1, v2) = value
    io1.set(index, buffers, bufferOffset, v1)
    io2.set(index, buffers, bufferOffset + io1.bufferCount, v2)
  }
}