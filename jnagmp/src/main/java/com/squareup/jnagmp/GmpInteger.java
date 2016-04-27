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
import java.math.BigInteger;
import java.util.Random;

/**
 * A {@link BigInteger} that caches a native peer {@link LibGmp.mpz_t}.
 *
 * <p> Use this class when you plan to perform many operations using this value as an operand.  For
 * example, when doing repeated RSA operations, you would want to construct {@code GmpIntegers} for
 * the modulus and exponent (which are the same each time) but not for the message, which will
 * generally differ.
 */
public class GmpInteger extends BigInteger {

  private static class MpzMemory extends Memory {
    public final mpz_t peer;

    MpzMemory() {
      super(mpz_t.SIZE);
      peer = new mpz_t(this);
      LibGmp.__gmpz_init(peer);
    }

    @Override protected void finalize() {
      LibGmp.__gmpz_clear(peer);
      super.finalize();
    }
  }

  private final MpzMemory mpzMemory = new MpzMemory();

  {
    Gmp.INSTANCE.get().mpzImport(mpzMemory.peer, super.signum(), super.abs().toByteArray());
  }

  mpz_t getPeer() {
    return mpzMemory.peer;
  }

  /** Constructs a GmpInteger from a BigInteger. */
  public GmpInteger(BigInteger other) {
    super(other.toByteArray());
  }

  /** @see BigInteger#BigInteger(byte[]) */
  public GmpInteger(byte[] val) {
    super(val);
  }

  /** @see BigInteger#BigInteger(int, byte[]) */
  public GmpInteger(int signum, byte[] magnitude) {
    super(signum, magnitude);
  }

  /** @see BigInteger#BigInteger(String, int) ) */
  public GmpInteger(String val, int radix) {
    super(val, radix);
  }

  /** @see BigInteger#BigInteger(String) */
  public GmpInteger(String val) {
    super(val);
  }

  /** @see BigInteger#BigInteger(int, Random) */
  public GmpInteger(int numBits, Random rnd) {
    super(numBits, rnd);
  }

  /** @see BigInteger#BigInteger(int, int, Random) */
  public GmpInteger(int bitLength, int certainty, Random rnd) {
    super(bitLength, certainty, rnd);
  }
}
