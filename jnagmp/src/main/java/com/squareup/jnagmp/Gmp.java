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
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.math.BigInteger;

import static com.squareup.jnagmp.LibGmp.__gmpz_clear;
import static com.squareup.jnagmp.LibGmp.__gmpz_export;
import static com.squareup.jnagmp.LibGmp.__gmpz_import;
import static com.squareup.jnagmp.LibGmp.__gmpz_init;
import static com.squareup.jnagmp.LibGmp.__gmpz_powm;
import static com.squareup.jnagmp.LibGmp.__gmpz_powm_sec;
import static com.squareup.jnagmp.LibGmp.readSizeT;

/** High level Java API for accessing {@link LibGmp} safely. */
public final class Gmp {

  private static final UnsatisfiedLinkError LOAD_ERROR;

  static {
    UnsatisfiedLinkError localLoadError = null;
    try {
      LibGmp.init();
    } catch (UnsatisfiedLinkError e) {
      localLoadError = e;
    }
    LOAD_ERROR = localLoadError;
  }

  /**
   * Verifies this library is loaded properly.
   *
   * @throws UnsatisfiedLinkError if the library failed to load properly.
   */
  public static void checkLoaded() {
    if (LOAD_ERROR != null) {
      throw LOAD_ERROR;
    }
    // Make a test call, sometimes the error won't occur until you try the native method.
    // 2 ^ 3 = 8, 8 mod 5 = 3
    BigInteger two = BigInteger.valueOf(2);
    BigInteger three = BigInteger.valueOf(3);
    BigInteger five = BigInteger.valueOf(5);
    BigInteger answer;

    answer = modPowInsecure(two, three, five);
    if (!three.equals(answer)) {
      throw new AssertionError("libgmp is loaded but modPowInsecure returned the wrong answer");
    }

    answer = modPowSecure(two, three, five);
    if (!three.equals(answer)) {
      throw new AssertionError("libgmp is loaded but modPowSecure returned the wrong answer");
    }
  }

  /**
   * Calculate (base ^ exponent) % modulus; faster, VULNERABLE TO TIMING ATTACKS.
   *
   * @param base the base, must be positive
   * @param exponent the exponent
   * @param modulus the modulus
   * @return the (base ^ exponent) % modulus
   * @throws ArithmeticException if modulus is non-positive
   * @throws IllegalArgumentException if modulus is even, base is negative, or exponent is negative
   */
  public static BigInteger modPowInsecure(BigInteger base, BigInteger exponent,
      BigInteger modulus) {
    if (modulus.signum() <= 0) {
      throw new ArithmeticException("modulus must be positive");
    }
    if (base.signum() < 0) {
      throw new IllegalArgumentException("base must be non-negative");
    }
    if (exponent.signum() < 0) {
      throw new IllegalArgumentException("exponent must be non-negative");
    }
    return INSTANCE.get().modPowInsecureImpl(base, exponent, modulus);
  }

  /**
   * Calculate (base ^ exponent) % modulus; slower, hardened against timing attacks.
   *
   * <p> NOTE: this methods REQUIRES modulus to be odd, due to a crash-bug in libgmp. This is not a
   * problem for RSA where the modulus is always odd.</p>
   *
   * @param base the base, must be positive
   * @param exponent the exponent
   * @param modulus the modulus
   * @return the (base ^ exponent) % modulus
   * @throws ArithmeticException if modulus is non-positive
   * @throws IllegalArgumentException if modulus is even, base is negative, or exponent is negative
   */
  public static BigInteger modPowSecure(BigInteger base, BigInteger exponent, BigInteger modulus) {
    if (modulus.signum() <= 0) {
      throw new ArithmeticException("modulus must be positive");
    }
    if (!modulus.testBit(0)) {
      throw new IllegalArgumentException("modulus must be odd");
    }
    if (base.signum() < 0) {
      throw new IllegalArgumentException("base must be non-negative");
    }
    if (exponent.signum() < 0) {
      throw new IllegalArgumentException("exponent must be non-negative");
    }
    return INSTANCE.get().modPowSecureImpl(base, exponent, modulus);
  }

  /**
   * VISIBLE FOR TESTING. Reuse the same buffers over and over to minimize allocations and native
   * boundary crossings.
   */
  static final ThreadLocal<Gmp> INSTANCE = new ThreadLocal<Gmp>() {
    @Override protected Gmp initialValue() {
      return new Gmp();
    }
  };

  /** Initial bit size of the scratch buffer. */
  private static final int INITIAL_BUF_BITS = 2048;
  private static final int INITIAL_BUF_SIZE = INITIAL_BUF_BITS / 8;

  /** Maximum number of operands we need for any operation. */
  private static final int MAX_OPERANDS = 4;

  private static final int SHARED_MEM_SIZE = mpz_t.SIZE * MAX_OPERANDS + Native.SIZE_T_SIZE;

  /**
   * Operands that can be reused over and over to avoid costly initialization and tear down. Backed
   * by {@link #sharedMem}.
   */
  private final mpz_t[] sharedOperands = new mpz_t[MAX_OPERANDS];

  /** The out size_t pointer for export. Backed by {@link #sharedMem}. */
  private final Pointer countPtr;

  /** A fixed, shared, reusable memory buffer. */
  private final Memory sharedMem = new Memory(SHARED_MEM_SIZE) {
    /** Must explicitly destroy the gmp_t structs before freeing the underlying memory. */
    @Override protected void finalize() {
      for (mpz_t sharedOperand : sharedOperands) {
        if (sharedOperand != null) {
          __gmpz_clear(sharedOperand);
        }
      }
      super.finalize();
    }
  };

  /** Reusable scratch buffer for moving data between byte[] and mpz_t. */
  private Memory scratchBuf = new Memory(INITIAL_BUF_SIZE);

  private Gmp() {
    int offset = 0;
    for (int i = 0; i < MAX_OPERANDS; ++i) {
      this.sharedOperands[i] = new mpz_t(sharedMem.share(offset, mpz_t.SIZE));
      __gmpz_init(sharedOperands[i]);
      offset += mpz_t.SIZE;
    }
    this.countPtr = sharedMem.share(offset, Native.SIZE_T_SIZE);
    offset += Native.SIZE_T_SIZE;
    assert offset == SHARED_MEM_SIZE;
  }

  private BigInteger modPowInsecureImpl(BigInteger base, BigInteger exp, BigInteger mod) {
    mpz_t basePeer = getPeer(base, sharedOperands[0]);
    mpz_t expPeer = getPeer(exp, sharedOperands[1]);
    mpz_t modPeer = getPeer(mod, sharedOperands[2]);

    __gmpz_powm(sharedOperands[3], basePeer, expPeer, modPeer);

    // The result size should be <= modulus size, but round up to the nearest byte.
    int requiredSize = (mod.bitLength() + 7) / 8;
    return new BigInteger(1, mpzExport(sharedOperands[3], requiredSize));
  }

  private BigInteger modPowSecureImpl(BigInteger base, BigInteger exp, BigInteger mod) {
    mpz_t basePeer = getPeer(base, sharedOperands[0]);
    mpz_t expPeer = getPeer(exp, sharedOperands[1]);
    mpz_t modPeer = getPeer(mod, sharedOperands[2]);

    __gmpz_powm_sec(sharedOperands[3], basePeer, expPeer, modPeer);

    // The result size should be <= modulus size, but round up to the nearest byte.
    int requiredSize = (mod.bitLength() + 7) / 8;
    return new BigInteger(1, mpzExport(sharedOperands[3], requiredSize));
  }

  /**
   * If {@code value} is a {@link GmpInteger}, return its peer. Otherwise, import {@code value} into
   * {@code sharedPeer} and return {@code sharedPeer}.
   */
  private mpz_t getPeer(BigInteger value, mpz_t sharedPeer) {
    if (value instanceof GmpInteger) {
      return ((GmpInteger) value).getPeer();
    }
    mpzImport(sharedPeer, value.toByteArray());
    return sharedPeer;
  }

  void mpzImport(mpz_t ptr, byte[] bytes) {
    int expectedLength = bytes.length;
    ensureBufferSize(expectedLength);
    scratchBuf.write(0, bytes, 0, bytes.length);
    __gmpz_import(ptr, bytes.length, 1, 1, 1, 0, scratchBuf);
  }

  private byte[] mpzExport(mpz_t ptr, int requiredSize) {
    ensureBufferSize(requiredSize);
    __gmpz_export(scratchBuf, countPtr, 1, 1, 1, 0, ptr);

    int count = readSizeT(countPtr);
    byte[] result = new byte[count];
    scratchBuf.read(0, result, 0, count);
    return result;
  }

  private void ensureBufferSize(int size) {
    if (scratchBuf.size() < size) {
      long newSize = scratchBuf.size();
      while (newSize < size) {
        newSize <<= 1;
      }
      scratchBuf = new Memory(newSize);
    }
  }
}
