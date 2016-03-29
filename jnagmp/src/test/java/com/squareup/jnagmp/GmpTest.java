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
  public void testModInverse() {
    assertEquals(BigInteger.valueOf(2), Gmp.modInverse(BigInteger.valueOf(3), BigInteger.valueOf(5)));
    Random rnd = new Random();
    BigInteger m = new BigInteger(1024, rnd).nextProbablePrime();
    for (int i=0; i<100; i++){ 
      BigInteger x = new BigInteger(1023, rnd);
      assertEquals(x.modInverse(m), Gmp.modInverse(x, m));
    }
  }
  
  @Test
  public void testModInverseArithmeticException() {
    try{
      Gmp.modInverse(BigInteger.ONE, BigInteger.valueOf(-1));
      fail("ArithmeticException expected.");
    } catch(ArithmeticException e) {      
    }
    try{
      Gmp.modInverse(BigInteger.valueOf(3), BigInteger.valueOf(9));
      fail("ArithmeticException expected.");
    } catch(ArithmeticException e) {      
    }
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

  @Test public void testSignErrorsInsecure() {
    strategy = INSECURE;
    try {
      modPow(-1, 1, 1);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
    }

    try {
      modPow(1, -1, 1);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void testSignErrorsSecure() {
    strategy = SECURE;
    try {
      modPow(-1, 1, 1);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
    }

    try {
      modPow(1, -1, 1);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void testSmallExhaustiveInsecure() {
    for (int base = 10; base >= 0; --base) {
      for (int exp = 10; exp >= 0; --exp) {
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
    for (int base = 10; base >= 0; --base) {
      for (int exp = 10; exp >= 0; --exp) {
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
