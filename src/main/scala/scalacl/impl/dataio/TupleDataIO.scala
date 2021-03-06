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

private[scalacl] class TupleDataIO[T <: Product: Manifest](
  ioArgs: DataIO[_]*)(
    newTuple: PartialFunction[Array[Any], T])
    extends DataIO[T] {

  private val ios = ioArgs.toArray

  override def toArray(length: Int, buffers: Array[ScheduledBuffer[_]]): Array[T] = {
    val eventsToWaitFor = new ArrayBuffer[CLEvent]
    val pointers = buffers.map(_.read(eventsToWaitFor).withoutValidityInformation) // unsafe, but faster
    CLEvent.waitFor(eventsToWaitFor.toArray: _*)
    (0 until length.toInt).par.map(i => get(i, pointers, 0)).toArray // TODO check
  }

  override def typeString = ios.map(_.typeString).mkString("(", ", ", ")")
  override val bufferCount = ios.map(_.bufferCount).sum
  private[scalacl] override def foreachScalar(f: ScalarDataIO[_] => Unit) {
    ios.foreach(_.foreachScalar(f))
  }
  override def allocateBuffers(length: Long, out: ArrayBuffer[ScheduledBuffer[_]])(implicit context: Context) = {
    ios.foreach(_.allocateBuffers(length, out))
  }
  // protected def newTuple(values: Array[Any]): T

  override def get(index: Long, buffers: Array[Pointer[_]], bufferOffset: Int) = {
    var offset = 0
    newTuple(for (io <- ios) yield {
      val v = io.get(index, buffers, offset)
      offset += io.bufferCount
      v
    })
  }

  override def set(index: Long, buffers: Array[Pointer[_]], bufferOffset: Int, value: T) = {
    val values = value.productIterator.toIterable
    var offset = 0
    for ((io, v) <- ios.zip(values)) {
      io.asInstanceOf[DataIO[Any]].set(index, buffers, offset, v)
      offset += io.bufferCount
    }
  }
}
