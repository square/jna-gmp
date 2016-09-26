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
package com.squareup.jnagmp;

import com.squareup.jnagmp.LibGmp.mpz_t;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import java.util.Random;
import org.junit.After;
import org.junit.Test;

import static com.squareup.jnagmp.LibGmp.__gmpz_clear;
import static com.squareup.jnagmp.LibGmp.__gmpz_cmp_si;
import static com.squareup.jnagmp.LibGmp.__gmpz_export;
import static com.squareup.jnagmp.LibGmp.__gmpz_import;
import static com.squareup.jnagmp.LibGmp.__gmpz_init;
import static com.squareup.jnagmp.LibGmp.__gmpz_init2;
import static com.squareup.jnagmp.LibGmp.__gmpz_invert;
import static com.squareup.jnagmp.LibGmp.__gmpz_mod;
import static com.squareup.jnagmp.LibGmp.__gmpz_mul;
import static com.squareup.jnagmp.LibGmp.__gmpz_neg;
import static com.squareup.jnagmp.LibGmp.__gmpz_powm;
import static com.squareup.jnagmp.LibGmp.__gmpz_powm_sec;
import static com.squareup.jnagmp.LibGmp.readSizeT;
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
    assertEquals("6.1.1", LibGmp.__gmp_version);
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

  @Test public void testMod() {
    scratch.write(0, new byte[] {8, 3}, 0, 2);
    mpz_t[] mvalues = new mpz_t[2];
    try {
      for (int i = 0; i < 2; ++i) {
        mpz_t mvalue = new mpz_t(mpzScratch.share(i * mpz_t.SIZE, mpz_t.SIZE));
        __gmpz_init(mvalue);
        mvalues[i] = mvalue;
        __gmpz_import(mvalue, 1, 1, 1, 1, 0, scratch.share(i));
      }

      // We can reuse the modulus for the result, result must be < modulus.
      mpz_t result = mvalues[1];
      __gmpz_mod(result, mvalues[0], mvalues[1]);

      __gmpz_export(scratch, count, 1, 1, 1, 0, result);

      assertEquals(1, readSizeT(count));
      assertEquals(2, scratch.getByte(0));
    } finally {
      for (mpz_t mvalue : mvalues) {
        if (mvalue != null) {
          __gmpz_clear(mvalue);
        }
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

  @Test public void testModInverse() {
    scratch.write(0, new byte[] {3, 5}, 0, 2);
    mpz_t[] mvalues = new mpz_t[2];
    try {
      for (int i = 0; i < 2; ++i) {
        mpz_t mvalue = new mpz_t(mpzScratch.share(i * mpz_t.SIZE, mpz_t.SIZE));
        __gmpz_init(mvalue);
        mvalues[i] = mvalue;
        __gmpz_import(mvalue, 1, 1, 1, 1, 0, scratch.share(i));
      }

      // We can reuse the modulus for the result, result must be < modulus.
      mpz_t result = mvalues[1];
      __gmpz_invert(result, mvalues[0], mvalues[1]);

      __gmpz_export(scratch, count, 1, 1, 1, 0, result);

      assertEquals(1, readSizeT(count));
      assertEquals(2, scratch.getByte(0));
    } finally {
      for (mpz_t mvalue : mvalues) {
        if (mvalue != null) {
          __gmpz_clear(mvalue);
        }
      }
    }
  }

  @Test public void testMul() {
    scratch.write(0, new byte[] {2, 3}, 0, 2);
    mpz_t[] mvalues = new mpz_t[2];
    try {
      for (int i = 0; i < 2; ++i) {
        mpz_t mvalue = new mpz_t(mpzScratch.share(i * mpz_t.SIZE, mpz_t.SIZE));
        __gmpz_init(mvalue);
        mvalues[i] = mvalue;
        __gmpz_import(mvalue, 1, 1, 1, 1, 0, scratch.share(i));
      }

      // We can reuse the modulus for the result, result must be < modulus.
      mpz_t result = mvalues[1];
      __gmpz_mul(result, mvalues[0], mvalues[1]);

      __gmpz_export(scratch, count, 1, 1, 1, 0, result);

      assertEquals(1, readSizeT(count));
      assertEquals(6, scratch.getByte(0));
    } finally {
      for (mpz_t mvalue : mvalues) {
        if (mvalue != null) {
          __gmpz_clear(mvalue);
        }
      }
    }
  }

  @Test public void testNegSign() {
    scratch.write(0, new byte[] {1, 0, 1}, 0, 3);
    mpz_t[] mvalues = new mpz_t[3];
    try {
      for (int i = 0; i < 3; ++i) {
        mpz_t mvalue = new mpz_t(mpzScratch.share(i * mpz_t.SIZE, mpz_t.SIZE));
        __gmpz_init(mvalue);
        mvalues[i] = mvalue;
        __gmpz_import(mvalue, 1, 1, 1, 1, 0, scratch.share(i));
      }

      // The first value should be -1
      __gmpz_neg(mvalues[0], mvalues[0]);

      // Check absolute values.
      for (int i = 0; i < 3; ++i) {
        mpz_t result = mvalues[i];
        __gmpz_export(scratch, count, 1, 1, 1, 0, result);

        if (i == 1) {
          // The middle value is 0
          assertEquals(0, readSizeT(count));
        } else {
          // The other two are 1 (absolute value is exported)
          assertEquals(1, readSizeT(count));
          assertEquals(1, scratch.getByte(0));
        }
      }

      // Now comparisons
      assertEquals(1, __gmpz_cmp_si(mvalues[0], new NativeLong(-2))); // -1 > -2
      assertEquals(0, __gmpz_cmp_si(mvalues[0], new NativeLong(-1))); // -1 == -1
      assertEquals(-1, __gmpz_cmp_si(mvalues[0], new NativeLong(0))); // -1 < 0

      assertEquals(1, __gmpz_cmp_si(mvalues[1], new NativeLong(-1))); // 0 > -1
      assertEquals(0, __gmpz_cmp_si(mvalues[1], new NativeLong(0))); // 0 == 0
      assertEquals(-1, __gmpz_cmp_si(mvalues[1], new NativeLong(1))); // 0 < 1

      assertEquals(1, __gmpz_cmp_si(mvalues[2], new NativeLong(0))); // 1 > 0
      assertEquals(0, __gmpz_cmp_si(mvalues[2], new NativeLong(1))); // 1 == 1
      assertEquals(-1, __gmpz_cmp_si(mvalues[2], new NativeLong(2))); // 1 < 2
    } finally {
      for (mpz_t mvalue : mvalues) {
        if (mvalue != null) {
          __gmpz_clear(mvalue);
        }
      }
    }
  }
}
