/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.blaze;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.spark.TaskContext;
import org.apache.spark.TaskContext$;
import org.apache.spark.deploy.SparkHadoopUtil;

public class JniBridge {
  public static final ConcurrentHashMap<String, Object> resourcesMap = new ConcurrentHashMap<>();

  public static native void initNative(
      long batchSize, long nativeMemory, double memoryFraction, String tmpDirs);

  public static native long callNative(BlazeCallNativeWrapper wrapper);

  public static native void releaseRuntime(long runtimePtr);

  public static void raiseThrowable(Throwable t) throws Throwable {
    throw t;
  }

  public static ClassLoader getContextClassLoader() {
    return Thread.currentThread().getContextClassLoader();
  }

  public static void setContextClassLoader(ClassLoader cl) {
    Thread.currentThread().setContextClassLoader(cl);
  }

  public static FileSystem getHDFSFileSystem() throws IOException {
    return FileSystem.get(SparkHadoopUtil.get().conf());
  }

  public static Object getResource(String key) {
    return resourcesMap.get(key);
  }

  public static TaskContext getTaskContext() {
    return TaskContext$.MODULE$.get();
  }

  public static void setTaskContext(TaskContext tc) {
    TaskContext$.MODULE$.setTaskContext(tc);
  }

  /**
   * shim method to FSDataInputStream.read()
   *
   * @return bytes read
   * @throws IOException
   */
  public static int readFSDataInputStream(FSDataInputStream in, ByteBuffer bb, long pos)
      throws IOException {
    int bytesRead;

    synchronized (in) {
      in.seek(pos);
      try {
        bytesRead = in.read(bb);
      } catch (UnsupportedOperationException e) {
        ReadableByteChannel channel = Channels.newChannel(in);
        bytesRead = channel.read(bb);
      }
      return bytesRead;
    }
  }

  /**
   * shim method to SeekableByteChannel.seek
   *
   * @param pos when non-negative: seek from start, else seek from end
   * @return current position from start after seeked
   * @throws IOException
   */
  public static long seekByteChannel(SeekableByteChannel channel, long pos) throws IOException {
    long abstractPos = pos;
    if (pos < 0) {
      abstractPos += channel.size();
    }
    channel.position(abstractPos);
    return abstractPos;
  }
}
