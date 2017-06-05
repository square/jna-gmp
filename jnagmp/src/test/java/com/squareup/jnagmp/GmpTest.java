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

import com.squareup.jnagmp.ModPowVectors.TestVector;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.squareup.jnagmp.Gmp.exactDivide;
import static com.squareup.jnagmp.Gmp.gcd;
import static com.squareup.jnagmp.Gmp.kronecker;
import static com.squareup.jnagmp.Gmp.modInverse;
import static com.squareup.jnagmp.Gmp.modPowInsecure;
import static com.squareup.jnagmp.Gmp.modPowSecure;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Tests {@link Gmp}. */
public class GmpTest {

  public static final ModPowStrategy JAVA = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return base.modPow(exponent, modulus);
    }
  };
  public static final ModPowStrategy INSECURE = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return modPowInsecure(base, exponent, modulus);
    }
  };
  public static final ModPowStrategy INSECURE_GMP_INTS = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return modPowInsecure(new GmpInteger(base), new GmpInteger(exponent),
          new GmpInteger(modulus));
    }
  };
  public static final ModPowStrategy SECURE = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return modPowSecure(base, exponent, modulus);
    }
  };
  public static final ModPowStrategy SECURE_GMP_INTS = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return modPowSecure(new GmpInteger(base), new GmpInteger(exponent), new GmpInteger(modulus));
    }
  };

  @BeforeClass public static void checkLoaded() {
    Gmp.checkLoaded();
  }

  /** Force GC to verify {@link Gmp#finalize()} cleans up properly without crashing. */
  @AfterClass public static void forceGc() throws InterruptedException {
    Gmp.INSTANCE.remove();
    final AtomicBoolean gcHappened = new AtomicBoolean(false);
    new Object() {
      @Override protected void finalize() throws Throwable {
        super.finalize();
        gcHappened.set(true);
      }
    };
    while (!gcHappened.get()) {
      System.gc();
      Thread.sleep(100);
    }
  }

  interface ModPowStrategy {
    BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus);
  }

  ModPowStrategy strategy;

  private void doTest(TestVector v) {
    assertEquals(v.pResult, strategy.modPow(v.message, v.dp, v.p));
    assertEquals(v.qResult, strategy.modPow(v.message, v.dq, v.q));
    assertNotEquals(v.pResult, strategy.modPow(v.message, v.dp, v.q));
    assertNotEquals(v.pResult, strategy.modPow(v.message, v.dq, v.p));
    assertNotEquals(v.qResult, strategy.modPow(v.message, v.dp, v.q));
    assertNotEquals(v.qResult, strategy.modPow(v.message, v.dq, v.p));
  }

  public long modPow(long base, long exponent, long modulus) {
    return strategy.modPow(BigInteger.valueOf(base), BigInteger.valueOf(exponent),
        BigInteger.valueOf(modulus)).longValue();
  }

  @Test public void testExamplesJava() {
    strategy = JAVA;
    testOddExamples();
    testEvenExamples();
  }

  @Test public void testExamplesInsecure() {
    strategy = INSECURE;
    testOddExamples();
    testEvenExamples();
  }

  @Test public void testExamplesInsecureGmpInts() {
    strategy = INSECURE_GMP_INTS;
    testOddExamples();
    testEvenExamples();
  }

  @Test public void testExamplesSecure() {
    strategy = SECURE;
    testOddExamples();
  }

  @Test public void testExamplesSecureGmpInts() {
    strategy = SECURE_GMP_INTS;
    testOddExamples();
  }

  @Test
  public void testExactDivide() {
    assertEquals(BigInteger.valueOf(1), exactDivide(BigInteger.valueOf(1), BigInteger.valueOf(1)));
    assertEquals(BigInteger.valueOf(3), exactDivide(BigInteger.valueOf(9), BigInteger.valueOf(3)));
    assertEquals(BigInteger.valueOf(4), exactDivide(BigInteger.valueOf(12), BigInteger.valueOf(3)));
    Random rnd = new Random();
    for (int i = 0; i < 100; i++) {
      BigInteger a = new BigInteger(1024, rnd);
      BigInteger b = new BigInteger(1024, rnd);
      assertEquals(a.gcd(b), gcd(a, b));
    }
  }

  @Test
  public void testExactDivideArithmeticException() {
    try {
      exactDivide(BigInteger.ONE, BigInteger.ZERO);
      fail("ArithmeticException expected");
    } catch (ArithmeticException expected) {
    }
  }

  @Test public void testExactDivideSmallExhaustive() {
    for (int a = 10; a >= -10; --a) {
      for (int b = 10; b >= -10; --b) {
        int p = a * b;
        BigInteger aVal = BigInteger.valueOf(a);
        BigInteger bVal = BigInteger.valueOf(b);
        BigInteger pVal = BigInteger.valueOf(p);

        // exactDivide should work both ways
        if (a != 0) {
          assertEquals(String.format("a %d, b %d, p %d", a, b, p), bVal, exactDivide(pVal, aVal));
        } else {
          try {
            exactDivide(pVal, aVal);
            fail("ArithmeticException expected");
          } catch (ArithmeticException expected) {
          }
        }
      }
    }
  }

  @Test
  public void testGcd() {
    assertEquals(BigInteger.valueOf(11), gcd(BigInteger.valueOf(99), BigInteger.valueOf(88)));
    assertEquals(BigInteger.valueOf(4), gcd(BigInteger.valueOf(100), BigInteger.valueOf(88)));
    assertEquals(BigInteger.valueOf(1), gcd(BigInteger.valueOf(101), BigInteger.valueOf(88)));
    Random rnd = new Random();
    for (int i = 0; i < 100; i++) {
      BigInteger a = new BigInteger(1024, rnd);
      BigInteger b = new BigInteger(1024, rnd);
      assertEquals(a.gcd(b), gcd(a, b));
    }
  }

  @Test public void testGcdSmallExhaustive() {
    for (int a = 10; a >= -10; --a) {
      for (int b = 10; b >= -10; --b) {
        BigInteger aVal = BigInteger.valueOf(a);
        BigInteger bVal = BigInteger.valueOf(b);
        BigInteger expected = aVal.gcd(bVal);
        BigInteger actual = gcd(aVal, bVal);
        assertEquals(String.format("a %d, b %d", a, b) + b, expected, actual);
      }
    }
  }

  @Test
  public void testModInverse() {
    assertEquals(BigInteger.valueOf(2),
        modInverse(BigInteger.valueOf(3), BigInteger.valueOf(5)));
    Random rnd = new Random();
    BigInteger m = new BigInteger(1024, rnd).nextProbablePrime();
    for (int i = 0; i < 100; i++) {
      BigInteger x = new BigInteger(1023, rnd);
      assertEquals(x.modInverse(m), modInverse(x, m));
    }
  }

  @Test public void testModInverseSmallExhaustive() {
    for (int val = 10; val >= 0; --val) {
      for (int mod = 10; mod >= -1; --mod) {
        BigInteger bVal = BigInteger.valueOf(val);
        BigInteger bMod = BigInteger.valueOf(mod);
        try {
          BigInteger expected = bVal.modInverse(bMod);
          BigInteger actual = modInverse(bVal, bMod);
          assertEquals(String.format("val %d, mod %d", val, mod) + mod, expected, actual);
        } catch (ArithmeticException e) {
          try {
            modInverse(bVal, bMod);
            fail("ArithmeticException expected");
          } catch (ArithmeticException expected) {
          }
        }
      }
    }
  }

  @Test
  public void testModInverseArithmeticException() {
    try {
      modInverse(BigInteger.ONE, BigInteger.valueOf(-1));
      fail("ArithmeticException expected");
    } catch (ArithmeticException expected) {
    }
    try {
      modInverse(BigInteger.valueOf(3), BigInteger.valueOf(9));
      fail("ArithmeticException expected");
    } catch (ArithmeticException expected) {
    }
  }

  @Test
  public void testKronecker() {
    // Prime (legendre)
    assertEquals(0, kronecker(BigInteger.valueOf(0), BigInteger.valueOf(7)));
    assertEquals(1, kronecker(BigInteger.valueOf(1), BigInteger.valueOf(7)));
    assertEquals(1, kronecker(BigInteger.valueOf(2), BigInteger.valueOf(7)));
    assertEquals(-1, kronecker(BigInteger.valueOf(3), BigInteger.valueOf(7)));
    assertEquals(1, kronecker(BigInteger.valueOf(4), BigInteger.valueOf(7)));
    assertEquals(-1, kronecker(BigInteger.valueOf(5), BigInteger.valueOf(7)));
    assertEquals(-1, kronecker(BigInteger.valueOf(6), BigInteger.valueOf(7)));
    assertEquals(0, kronecker(BigInteger.valueOf(7), BigInteger.valueOf(7)));

    // Non-prime odd (jacobi)
    assertEquals(0, kronecker(BigInteger.valueOf(0), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(1), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(2), BigInteger.valueOf(9)));
    assertEquals(0, kronecker(BigInteger.valueOf(3), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(4), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(5), BigInteger.valueOf(9)));
    assertEquals(0, kronecker(BigInteger.valueOf(6), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(7), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(8), BigInteger.valueOf(9)));
    assertEquals(0, kronecker(BigInteger.valueOf(9), BigInteger.valueOf(9)));

    // Anything (kronecker)
    assertEquals(0, kronecker(BigInteger.valueOf(0), BigInteger.valueOf(8)));
    assertEquals(1, kronecker(BigInteger.valueOf(1), BigInteger.valueOf(8)));
    assertEquals(0, kronecker(BigInteger.valueOf(2), BigInteger.valueOf(8)));
    assertEquals(-1, kronecker(BigInteger.valueOf(3), BigInteger.valueOf(8)));
    assertEquals(0, kronecker(BigInteger.valueOf(4), BigInteger.valueOf(8)));
    assertEquals(-1, kronecker(BigInteger.valueOf(5), BigInteger.valueOf(8)));
    assertEquals(0, kronecker(BigInteger.valueOf(6), BigInteger.valueOf(8)));
    assertEquals(1, kronecker(BigInteger.valueOf(7), BigInteger.valueOf(8)));
    assertEquals(0, kronecker(BigInteger.valueOf(8), BigInteger.valueOf(8)));

    assertEquals(0, kronecker(BigInteger.valueOf(0), BigInteger.valueOf(-8)));
    assertEquals(1, kronecker(BigInteger.valueOf(1), BigInteger.valueOf(-8)));
    assertEquals(0, kronecker(BigInteger.valueOf(2), BigInteger.valueOf(-8)));
    assertEquals(-1, kronecker(BigInteger.valueOf(3), BigInteger.valueOf(-8)));
    assertEquals(0, kronecker(BigInteger.valueOf(4), BigInteger.valueOf(-8)));
    assertEquals(-1, kronecker(BigInteger.valueOf(5), BigInteger.valueOf(-8)));
    assertEquals(0, kronecker(BigInteger.valueOf(6), BigInteger.valueOf(-8)));
    assertEquals(1, kronecker(BigInteger.valueOf(7), BigInteger.valueOf(-8)));
    assertEquals(0, kronecker(BigInteger.valueOf(8), BigInteger.valueOf(-8)));
  }

  private void testOddExamples() {
    // 2 ^ 3 = 8
    assertEquals(2, modPow(2, 3, 3));
    assertEquals(3, modPow(2, 3, 5));
    assertEquals(1, modPow(2, 3, 7));
    assertEquals(8, modPow(2, 3, 9));
  }

  private void testEvenExamples() {
    // 2 ^ 3 = 8
    assertEquals(0, modPow(2, 3, 2));
    assertEquals(0, modPow(2, 3, 4));
    assertEquals(2, modPow(2, 3, 6));
    assertEquals(0, modPow(2, 3, 8));
  }

  @Test public void testSmallExhaustiveInsecure() {
    for (int base = 10; base >= -10; --base) {
      for (int exp = 10; exp >= -10; --exp) {
        for (int mod = 10; mod >= -1; --mod) {
          this.strategy = JAVA;
          Object expected;
          try {
            expected = modPow(base, exp, mod);
          } catch (Exception e) {
            expected = e.getClass();
          }

          this.strategy = INSECURE;
          Object actual;
          try {
            actual = modPow(base, exp, mod);
          } catch (Exception e) {
            actual = e.getClass();
          }
          String message = String.format("base %d, exp %d, mod %d", base, exp, mod);
          assertEquals(message, expected, actual);
        }
      }
    }
  }

  @Test public void testSmallExhaustiveSecure() {
    for (int base = 10; base >= -10; --base) {
      for (int exp = 10; exp >= -10; --exp) {
        for (int mod = 10; mod >= -1; --mod) {
          this.strategy = JAVA;
          Object expected;
          try {
            expected = modPow(base, exp, mod);
          } catch (Exception e) {
            expected = e.getClass();
          }

          this.strategy = SECURE;
          Object actual;
          try {
            actual = modPow(base, exp, mod);
          } catch (Exception e) {
            actual = e.getClass();
          }
          if (mod > 0 && mod % 2 == 0) {
            // modPowSecure does not support even modulus
            assertEquals(IllegalArgumentException.class, actual);
          } else {
            String message = String.format("base %d, exp %d, mod %d", base, exp, mod);
            assertEquals(message, expected, actual);
          }
        }
      }
    }
  }

  @Test public void testVectorsJava() {
    strategy = JAVA;
    testVectors();
  }

  @Test public void testVectorInsecure() {
    strategy = INSECURE;
    testVectors();
  }

  @Test public void testVectorsSecure() {
    strategy = SECURE;
    testVectors();
  }

  private void testVectors() {
    doTest(ModPowVectors.VECTOR1);
    doTest(ModPowVectors.VECTOR2);
    doTest(ModPowVectors.VECTOR3);
  }

  private static void assertNotEquals(Object expected, Object actual) {
    if ((expected == null) != (actual == null)) {
      return;
    }
    if (expected != actual && !expected.equals(actual)) {
      return;
    }
    fail("Expected not equals, was: " + actual);
  }
}
