/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

public class ResizeableMappedFile implements Forceable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.io.ResizeableMappedFile");

  private long myLogicalSize;
  private final PagedFileStorage myStorage;

  public ResizeableMappedFile(@NotNull File file, int initialSize, @Nullable PagedFileStorage.StorageLockContext lockContext, int pageSize,
                              boolean valuesAreBufferAligned) throws IOException {
    this(file, initialSize, lockContext, pageSize, valuesAreBufferAligned, false);
  }

  public ResizeableMappedFile(@NotNull File file,
                              int initialSize,
                              @Nullable PagedFileStorage.StorageLockContext lockContext,
                              int pageSize,
                              boolean valuesAreBufferAligned,
                              boolean nativeBytesOrder) throws IOException {
    myStorage = new PagedFileStorage(file, lockContext, pageSize, valuesAreBufferAligned, nativeBytesOrder);
    boolean exists = file.exists();
    if (!exists || file.length() == 0) {
      if (!exists) FileUtil.createParentDirs(file);
      writeLength(0);
    }

    myLogicalSize = readLength();
    if (myLogicalSize == 0) {
      try {
        getPagedFileStorage().lock();
        // use direct call to storage.resize() so that IOException is not masked with RuntimeException
        myStorage.resize(initialSize);
      }
      finally {
        getPagedFileStorage().unlock();
      }
    }
  }

  public ResizeableMappedFile(final File file, int initialSize, PagedFileStorage.StorageLock lock, int pageSize, boolean valuesAreBufferAligned) throws IOException {
    this(file, initialSize, lock.myDefaultStorageLockContext, pageSize, valuesAreBufferAligned);
  }

  public ResizeableMappedFile(final File file, int initialSize, PagedFileStorage.StorageLock lock) throws IOException {
    this(file, initialSize, lock, -1, false);
  }

  public long length() {
    return myLogicalSize;
  }

  private long realSize() {
    return myStorage.length();
  }

  private void resize(final long size) {
    try {
      myStorage.resize(size);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  void ensureSize(final long pos) {
    if (pos + 16 > Integer.MAX_VALUE) throw new RuntimeException("FATAL ERROR: Can't get over 2^32 address space");
    myLogicalSize = Math.max(pos, myLogicalSize);
    while (pos >= realSize()) {
      expand();
    }
  }

  private void expand() {
    final long newSize = Math.min(Integer.MAX_VALUE, ((realSize() + 1) * 13) >> 3);
    resize((int)newSize);
  }

  private File getLengthFile() {
    return new File(myStorage.getFile().getPath() + ".len");
  }

  private void writeLength(final long len) {
    final File lengthFile = getLengthFile();
    DataOutputStream stream = null;
    try {
      stream = FileUtilRt.doIOOperation(new FileUtilRt.RepeatableIOOperation<DataOutputStream, FileNotFoundException>() {
        @Nullable
        @Override
        public DataOutputStream execute(boolean lastAttempt) throws FileNotFoundException {
          try {
            return new DataOutputStream(new FileOutputStream(lengthFile));
          } catch (FileNotFoundException ex) {
            if (!lastAttempt) return null;
            throw ex;
          }
        }
      });
      if (stream != null) stream.writeLong(len);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    finally {
      if (stream != null) {
        try {
          stream.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  @Override
  public boolean isDirty() {
    return myStorage.isDirty();
  }

  @Override
  public void force() {
    if (isDirty()) {
      writeLength(myLogicalSize);
    }
    myStorage.force();
  }

  private long readLength() {
    File lengthFile = getLengthFile();
    DataInputStream stream = null;
    try {
      stream = new DataInputStream(new FileInputStream(lengthFile));
      return stream.readLong();
    }
    catch (IOException e) {
      writeLength(realSize());
      return realSize();
    }
    finally {
      if (stream != null) {
        try {
          stream.close();
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
    }
  }

  public int getInt(long index) {
    return myStorage.getInt(index);
  }

  public void putInt(long index, int value) {
    ensureSize(index + 4);
    myStorage.putInt(index, value);
  }

  public short getShort(long index) {
    return myStorage.getShort(index);
  }

  public void putShort(long index, short value) {
    ensureSize(index + 2);
    myStorage.putShort(index, value);
  }

  public long getLong(long index) {
    return myStorage.getLong(index);
  }

  public void putLong(long index, long value) {
    ensureSize(index + 8);
    myStorage.putLong(index, value);
  }

  public byte get(long index) {
    return myStorage.get(index);
  }

  public void put(long index, byte value) {
    ensureSize(index + 1);
    myStorage.put(index, value);
  }

  public void get(long index, byte[] dst, int offset, int length) {
    myStorage.get(index, dst, offset, length);
  }

  public void put(long index, byte[] src, int offset, int length) {
    ensureSize(index + length);
    myStorage.put(index, src, offset, length);
  }

  public void close() {
    try {
      force();
    }
    finally {
      myStorage.close();
    }
  }

  @NotNull
  public PagedFileStorage getPagedFileStorage() {
    return myStorage;
  }
}
