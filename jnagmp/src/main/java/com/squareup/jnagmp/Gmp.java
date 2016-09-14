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
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.math.BigInteger;

import static java.lang.Math.max;
import static java.lang.Math.min;

import static com.squareup.jnagmp.LibGmp.__gmpz_clear;
import static com.squareup.jnagmp.LibGmp.__gmpz_cmp_si;
import static com.squareup.jnagmp.LibGmp.__gmpz_export;
import static com.squareup.jnagmp.LibGmp.__gmpz_import;
import static com.squareup.jnagmp.LibGmp.__gmpz_init;
import static com.squareup.jnagmp.LibGmp.__gmpz_invert;
import static com.squareup.jnagmp.LibGmp.__gmpz_jacobi;
import static com.squareup.jnagmp.LibGmp.__gmpz_neg;
import static com.squareup.jnagmp.LibGmp.__gmpz_powm;
import static com.squareup.jnagmp.LibGmp.__gmpz_powm_sec;
import static com.squareup.jnagmp.LibGmp.__gmpz_mul;
import static com.squareup.jnagmp.LibGmp.__gmpz_mod;
import static com.squareup.jnagmp.LibGmp.__gmpz_divexact;
import static com.squareup.jnagmp.LibGmp.__gmpz_gcd;
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
    BigInteger four = BigInteger.valueOf(4);
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

    int answr = kronecker(four, five);
    if (answr != 1) {
      throw new AssertionError("libgmp is loaded but kronecker returned the wrong answer");
    }
  }

  /**
   * Calculate kronecker symbol a|p.  Generalization of legendre and jacobi.
   *
   * @param a an integer
   * @param p the modulus
   * @return a|p
   */
  public static int kronecker(BigInteger a, BigInteger p) {
    return INSTANCE.get().kroneckerImpl(a, p);
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
   * Calculate val^-1 % modulus.
   *
   * @param val must be positive
   * @param modulus the modulus
   * @return val^-1 % modulus
   * @throws ArithmeticException if modulus is non-positive or val is not invertible
   */
  public static BigInteger modInverse(BigInteger val, BigInteger modulus) {
    if (modulus.signum() <= 0) {
      throw new ArithmeticException("modulus must be positive");
    }
    return INSTANCE.get().modInverseImpl(val, modulus);
  }

  /**
   * Calculate factor1 * factor2.
   *
   * @param factor1 to multiply
   * @param factor2 to multiply
   * @return factor1 * factor2
   */
  protected static BigInteger multiply(BigInteger factor1, BigInteger factor2) {
    return INSTANCE.get().mulImpl(factor1, factor2);
  }

  /**
   * Calculate dividend % modulus.
   *
   * @param dividend
   * @param modulus the modulus
   * @return dividend mod modulus
   * @throws ArithmeticException if modulus is non-positive
   */
  protected static BigInteger mod(BigInteger dividend, BigInteger modulus) {
    if (modulus.signum() <= 0) {
      throw new ArithmeticException("modulus must be positive");
    }

    return INSTANCE.get().modImpl(dividend, modulus);
  }

  /**
   * Calculate (factor1 * factor2) % modulus.
   *
   * @param factor1
   * @param factor2
   * @param modulus the positive modulus
   * @return (factor1 * factor2) % modulus
   * @throws ArithmeticException if modulus is non-positive
   */
  public static BigInteger modularMultiply(BigInteger factor1, BigInteger factor2,
                                           BigInteger modulus) {
    if (modulus.signum() <= 0) {
      throw new ArithmeticException("modulus must be positive");
    }

    return INSTANCE.get().mulModImpl(factor1, factor2, modulus);
  }

  /**
   * Calculate (factor1 * factor2) % modulus.
   *
   * @param factor1
   * @param factor2
   * @param modulus the positive modulus
   * @return (factor1 * factor2) % modulus
   * @throws ArithmeticException if modulus is non-positive
   */
  public static BigInteger modularMultiplyAlt(BigInteger factor1, BigInteger factor2,
                                              BigInteger modulus) {
    if (modulus.signum() <= 0) {
      throw new ArithmeticException("modulus must be positive");
    }

    return INSTANCE.get().mulModImplAlt(factor1, factor2, modulus);
  }

  /**
   * Divide dividend by divisor. This method only returns correct answers when the division produces
   * no remainder. Correct answers should not be expected when the divison would result in a
   * remainder.
   *
   * @param dividend
   * @param divisor
   * @throws ArithmeticException if divisor is zero
   * @return dividend / divisor
   */
  public static BigInteger exactDivide(BigInteger dividend, BigInteger divisor) {

    if (divisor.equals(BigInteger.valueOf(0))) {
      throw new ArithmeticException("divisor can not be zero");
    }
    return INSTANCE.get().exactDivImpl(dividend, divisor);
  }

  /**
   * Return the greatest common divisor of value1 and value2. The result is always positive even if
   * one or both input operands are negative. Except if both inputs are zero; then this method
   * defines gcd(0,0) = 0.
   *
   * @param value1
   * @param value2
   * @return greatest common divisor of value1 and value2
   */
  public static BigInteger gcd(BigInteger value1, BigInteger value2) {
    return INSTANCE.get().gcdImpl(value1, value2);
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
  private static final int MAX_OPERANDS = 6;

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

  private int kroneckerImpl(BigInteger a, BigInteger p) {
    mpz_t aPeer = getPeer(a, sharedOperands[0]);
    mpz_t pPeer = getPeer(p, sharedOperands[1]);

    return __gmpz_jacobi(aPeer, pPeer);
  }

  private BigInteger modPowInsecureImpl(BigInteger base, BigInteger exp, BigInteger mod) {
    mpz_t basePeer = getPeer(base, sharedOperands[0]);
    mpz_t expPeer = getPeer(exp, sharedOperands[1]);
    mpz_t modPeer = getPeer(mod, sharedOperands[2]);

    __gmpz_powm(sharedOperands[3], basePeer, expPeer, modPeer);

    // The result size should be <= modulus size, but round up to the nearest byte.
    int requiredSize = (mod.bitLength() + 7) / 8;
    return new BigInteger(mpzSgn(sharedOperands[3]), mpzExport(sharedOperands[3], requiredSize));
  }

  private BigInteger modPowSecureImpl(BigInteger base, BigInteger exp, BigInteger mod) {
    mpz_t basePeer = getPeer(base, sharedOperands[0]);
    mpz_t expPeer = getPeer(exp, sharedOperands[1]);
    mpz_t modPeer = getPeer(mod, sharedOperands[2]);

    __gmpz_powm_sec(sharedOperands[3], basePeer, expPeer, modPeer);

    // The result size should be <= modulus size, but round up to the nearest byte.
    int requiredSize = (mod.bitLength() + 7) / 8;
    return new BigInteger(mpzSgn(sharedOperands[3]), mpzExport(sharedOperands[3], requiredSize));
  }

  private BigInteger modInverseImpl(BigInteger val, BigInteger mod) {
    mpz_t valPeer = getPeer(val, sharedOperands[0]);
    mpz_t modPeer = getPeer(mod, sharedOperands[1]);

    int res = __gmpz_invert(sharedOperands[2], valPeer, modPeer);
    if (res == 0) {
      throw new ArithmeticException("val not invertible");
    }

    // The result size should be <= modulus size, but round up to the nearest byte.
    int requiredSize = (mod.bitLength() + 7) / 8;
    return new BigInteger(mpzSgn(sharedOperands[2]), mpzExport(sharedOperands[2], requiredSize));
  }

  private BigInteger mulImpl(BigInteger factor1, BigInteger factor2) {
    mpz_t factor1Peer = getPeer(factor1, sharedOperands[0]);
    mpz_t factor2Peer = getPeer(factor2, sharedOperands[1]);

    __gmpz_mul(sharedOperands[2], factor1Peer, factor2Peer);

    // The result may require as many bits as the sum of the bitlengths of factor1 and factor2.
    int requiredSize = factor1.bitLength() + factor2.bitLength() + 1;
    return new BigInteger(mpzSgn(sharedOperands[2]), mpzExport(sharedOperands[2], requiredSize));
  }

  private BigInteger modImpl(BigInteger dividend, BigInteger mod) {
    mpz_t dividendPeer = getPeer(dividend, sharedOperands[0]);
    mpz_t modPeer = getPeer(mod, sharedOperands[1]);

    __gmpz_mod(sharedOperands[2], dividendPeer, modPeer);

    // The result size should be <= mod size, but round up to the nearest byte.
    int requiredSize = (mod.bitLength() + 7) / 8;
    return new BigInteger(mpzSgn(sharedOperands[2]), mpzExport(sharedOperands[2], requiredSize));
  }

  private BigInteger mulModImpl(BigInteger factor1, BigInteger factor2, BigInteger mod) {
    // (A * B) mod C
    mpz_t factor1Peer = getPeer(factor1, sharedOperands[0]);
    mpz_t factor2Peer = getPeer(factor2, sharedOperands[1]);
    mpz_t modPeer = getPeer(mod, sharedOperands[2]);

    __gmpz_mul(sharedOperands[3], factor1Peer, factor2Peer);
    __gmpz_mod(sharedOperands[3], sharedOperands[3], modPeer);

    // The result size should be <= mod size, but round up to the nearest byte.
    int requiredSize = (mod.bitLength() + 7) / 8;
    return new BigInteger(mpzSgn(sharedOperands[3]), mpzExport(sharedOperands[3], requiredSize));
  }

  private BigInteger mulModImplAlt(BigInteger factor1, BigInteger factor2, BigInteger mod) {
    // (A mod C * B mod C) mod C
    // 
    // A -> sharedOperands[0]
    // B -> sharedOperands[1]
    // C -> sharedOperands[2]
    // 
    // A mod C -> sharedOperands[3]
    // B mod C -> sharedOperands[0]
    // (A mod C) * (B mod C) -> sharedOperands[1]
    // ((A mod C) * (B mod C)) mod C -> sharedOperands[0]
    mpz_t factor1Peer = getPeer(factor1, sharedOperands[0]);
    mpz_t factor2Peer = getPeer(factor2, sharedOperands[1]);
    mpz_t modPeer = getPeer(mod, sharedOperands[2]);
    
    __gmpz_mod(sharedOperands[3], factor1Peer, modPeer);
    __gmpz_mod(sharedOperands[0], factor2Peer, modPeer);

    __gmpz_mul(sharedOperands[1], sharedOperands[3], sharedOperands[0]);

    __gmpz_mod(sharedOperands[0], sharedOperands[1], modPeer);

    // The result size should be <= mod size, but round up to the nearest byte.
    int requiredSize = (mod.bitLength() + 7) / 8;
    return new BigInteger(mpzSgn(sharedOperands[0]), mpzExport(sharedOperands[0], requiredSize));
  }

  private BigInteger exactDivImpl(BigInteger dividend, BigInteger divisor) {
    mpz_t dividendPeer = getPeer(dividend, sharedOperands[0]);
    mpz_t divisorPeer = getPeer(divisor, sharedOperands[1]);

    __gmpz_divexact(sharedOperands[2], dividendPeer, divisorPeer);

    // The result size is never larger than the bit length of the dividend minus that of the divisor
    // plus 1 (but is at least 1 bit long to hold the case that the two values are exactly equal)
    int requiredSize = max(dividend.bitLength() - divisor.bitLength() + 1, 1);
    return new BigInteger(mpzSgn(sharedOperands[2]), mpzExport(sharedOperands[2], requiredSize));
  }

  private BigInteger gcdImpl(BigInteger value1, BigInteger value2) {
    mpz_t value1Peer = getPeer(value1, sharedOperands[0]);
    mpz_t value2Peer = getPeer(value2, sharedOperands[1]);

    __gmpz_gcd(sharedOperands[2], value1Peer, value2Peer);

    // The result size will be no larger than the smaller of the inputs
    int requiredSize = min(value1.bitLength(), value2.bitLength());
    return new BigInteger(mpzSgn(sharedOperands[2]), mpzExport(sharedOperands[2], requiredSize));
  }

  /**
   * If {@code value} is a {@link GmpInteger}, return its peer. Otherwise, import {@code value} into
   * {@code sharedPeer} and return {@code sharedPeer}.
   */
  private mpz_t getPeer(BigInteger value, mpz_t sharedPeer) {
    if (value instanceof GmpInteger) {
      return ((GmpInteger) value).getPeer();
    }
    mpzImport(sharedPeer, value.signum(), value.abs().toByteArray());
    return sharedPeer;
  }

  void mpzImport(mpz_t ptr, int signum, byte[] bytes) {
    int expectedLength = bytes.length;
    ensureBufferSize(expectedLength);
    scratchBuf.write(0, bytes, 0, bytes.length);
    __gmpz_import(ptr, bytes.length, 1, 1, 1, 0, scratchBuf);
    if (signum < 0) {
      __gmpz_neg(ptr, ptr);
    }
  }

  private byte[] mpzExport(mpz_t ptr, int requiredSize) {
    ensureBufferSize(requiredSize);
    __gmpz_export(scratchBuf, countPtr, 1, 1, 1, 0, ptr);

    int count = readSizeT(countPtr);
    byte[] result = new byte[count];
    scratchBuf.read(0, result, 0, count);
    return result;
  }

  private static final NativeLong ZERO = new NativeLong();

  int mpzSgn(mpz_t ptr) {
    int result = __gmpz_cmp_si(ptr, ZERO);
    if (result < 0) {
      return -1;
    } else if (result > 0) {
      return 1;
    }
    return 0;
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
