/*
 * Copyright 2013 Square Inc.
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
package com.squareup.jna.gmp;

import com.squareup.jna.gmp.LibGmp.mpz_t;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import java.util.Random;
import org.junit.After;
import org.junit.Test;

import static com.squareup.jna.gmp.LibGmp.__gmpz_clear;
import static com.squareup.jna.gmp.LibGmp.__gmpz_export;
import static com.squareup.jna.gmp.LibGmp.__gmpz_import;
import static com.squareup.jna.gmp.LibGmp.__gmpz_init;
import static com.squareup.jna.gmp.LibGmp.__gmpz_init2;
import static com.squareup.jna.gmp.LibGmp.__gmpz_powm;
import static com.squareup.jna.gmp.LibGmp.__gmpz_powm_sec;
import static com.squareup.jna.gmp.LibGmp.readSizeT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** Tests for {@link LibGmp}. */
public class LibGmpTest {

  /** Just adds "free()" for immediate de-allocation. */
  static class Memory extends com.sun.jna.Memory {
    public Memory(long size) {
      super(size);
    }

    public void free() {
      super.dispose();
    }
  }

  private static final Random RANDOM = new Random();

  final Memory scratch = new Memory(1024);
  final Memory count = new Memory(Native.SIZE_T_SIZE);
  final Memory mpzScratch = new Memory(mpz_t.SIZE * 3);

  @After public void tearDown() {
    scratch.free();
    mpzScratch.free();
    count.free();
  }

  @Test public void testVersion() {
    assertEquals("5.1.3", LibGmp.__gmp_version);
  }

  @Test public void testInitClear() {
    mpz_t mvalue = new mpz_t(mpzScratch);
    __gmpz_init(mvalue);
    __gmpz_clear(mvalue);
  }

  @Test public void testInit2Clear() {
    for (int size = 0; size < 1000; ++size) {
      mpz_t mvalue = new mpz_t(mpzScratch);
      __gmpz_init2(mvalue, new NativeLong(8 * size));
      __gmpz_clear(mvalue);
    }
  }

  /** Without any leading zero, import/export should match exactly. */
  @Test public void testImportExportNoLeadingZero() {
    for (int size = 0; size < 1000; ++size) {
      mpz_t mvalue = new mpz_t(mpzScratch);
      __gmpz_init2(mvalue, new NativeLong(8 * size));
      try {
        byte[] in = new byte[size];
        RANDOM.nextBytes(in);
        if (size > 0 && in[0] == 0) {
          in[0] = 1;
        }

        scratch.write(0, in, 0, size);
        __gmpz_import(mvalue, in.length, 1, 1, 1, 0, scratch);

        scratch.clear();
        __gmpz_export(scratch, count, 1, 1, 1, 0, mvalue);

        assertEquals(size, readSizeT(count));
        byte[] out = new byte[size];
        scratch.read(0, out, 0, size);

        assertArrayEquals(in, out);
      } finally {
        __gmpz_clear(mvalue);
      }
    }
  }

  /** GMP will strip off any leading zeroes with import/export. */
  @Test public void testImportExportLeadingZeroes() {
    for (int size = 0; size < 1000; ++size) {
      mpz_t mvalue = new mpz_t(mpzScratch);
      __gmpz_init2(mvalue, new NativeLong(8 * size));
      try {
        byte[] in = new byte[size];
        RANDOM.nextBytes(in);

        int leadingZeros = size / 4;
        for (int i = 0; i < leadingZeros; ++i) {
          in[i] = 0;
        }
        if (leadingZeros < size && in[leadingZeros] == 0) {
          in[leadingZeros] = 1;
        }

        scratch.write(0, in, 0, size);
        __gmpz_import(mvalue, in.length, 1, 1, 1, 0, scratch);

        scratch.clear();
        __gmpz_export(scratch, count, 1, 1, 1, 0, mvalue);

        assertEquals(size - leadingZeros, readSizeT(count));
        byte[] out = new byte[size];
        scratch.read(0, out, leadingZeros, size - leadingZeros);

        assertArrayEquals(in, out);
      } finally {
        __gmpz_clear(mvalue);
      }
    }
  }

  @Test public void testModPow() {
    scratch.write(0, new byte[] {2, 3, 5}, 0, 3);
    mpz_t[] mvalues = new mpz_t[3];
    try {
      for (int i = 0; i < 3; ++i) {
        mpz_t mvalue = new mpz_t(mpzScratch.share(i * mpz_t.SIZE, mpz_t.SIZE));
        __gmpz_init(mvalue);
        mvalues[i] = mvalue;
        __gmpz_import(mvalue, 1, 1, 1, 1, 0, scratch.share(i));
      }

      // We can reuse the modulus for the result, result must be < modulus.
      mpz_t result = mvalues[2];
      __gmpz_powm(result, mvalues[0], mvalues[1], mvalues[2]);

      __gmpz_export(scratch, count, 1, 1, 1, 0, result);

      assertEquals(1, readSizeT(count));
      assertEquals(3, scratch.getByte(0));
    } finally {
      for (mpz_t mvalue : mvalues) {
        if (mvalue != null) {
          __gmpz_clear(mvalue);
        }
      }
    }
  }

  @Test public void testModPowSec() {
    scratch.write(0, new byte[] {2, 3, 5}, 0, 3);
    mpz_t[] mvalues = new mpz_t[3];
    try {
      for (int i = 0; i < 3; ++i) {
        mpz_t mvalue = new mpz_t(mpzScratch.share(i * mpz_t.SIZE, mpz_t.SIZE));
        __gmpz_init(mvalue);
        mvalues[i] = mvalue;
        __gmpz_import(mvalue, 1, 1, 1, 1, 0, scratch.share(i));
      }

      // We can reuse the modulus for the result, result must be < modulus.
      mpz_t result = mvalues[2];
      __gmpz_powm_sec(result, mvalues[0], mvalues[1], mvalues[2]);

      __gmpz_export(scratch, count, 1, 1, 1, 0, result);

      assertEquals(1, readSizeT(count));
      assertEquals(3, scratch.getByte(0));
    } finally {
      for (mpz_t mvalue : mvalues) {
        if (mvalue != null) {
          __gmpz_clear(mvalue);
        }
      }
    }
  }
}
