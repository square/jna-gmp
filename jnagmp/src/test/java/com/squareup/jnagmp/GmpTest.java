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

import static com.squareup.jnagmp.Gmp.kronecker;
import static com.squareup.jnagmp.Gmp.modInverse;
import static com.squareup.jnagmp.Gmp.modPowInsecure;
import static com.squareup.jnagmp.Gmp.modPowSecure;
import static com.squareup.jnagmp.Gmp.multiply;
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
  public void testMultiplySmallNumbers() {
    BigInteger result = multiply(BigInteger.valueOf(15), BigInteger.valueOf(10));

    assertEquals(result, BigInteger.valueOf(150));
  }

  @Test
  public void testMultiplyLargeNumbers() {
    BigInteger factor1 =   new BigInteger("595626405312658704599636370024200076395151472"+
                                          "771767924568367390440634854836130642401309734"+
                                          "848159964191109372749251136446006243551080802"+
                                          "443468883807974005482441860971736801068233313"+
                                          "491232693258531224835309787079942460705208862"+
                                          "391066304008929671240068786982214789313974208"+
                                          "121277040682624462127077310398113929298032224"+
                                          "406902296964052163261880140026245816176689134"+
                                          "688646742318394515117125117931094080634872026"+
                                          "347848034669856506748754843134161491874260587"+
                                          "327337252669362585036570429186753965648086618"+
                                          "124304059986689706447677381144991687868142047"+
                                          "249722766880033576608579416659574000278570884"+
                                          "27139264803160717153834950082732");

    BigInteger factor2 =   new BigInteger("460133273971390735744145615685462110941026662"+
                                          "872377688410148134167657174457131391561681026"+
                                          "343648834014013242938124201005972667172338011"+
                                          "731770664675780475494554955408031889842685747"+
                                          "339694953330696186125702585670064808647571207"+
                                          "588789281591389310763609659973902705551579262"+
                                          "219102156751267333493035940073864847077832336"+
                                          "568113237185953229882343361927635723766493649"+
                                          "384192635115510718823130742006688425246084761"+
                                          "794243459815313486028010705533642096144745946"+
                                          "599634217741317208344059364932570572685763361"+
                                          "928329323093624941613003679183345155384065602"+
                                          "501020875593634261236141372148269233960019631"+
                                          "11550067913622812130192147468387");

    BigInteger expected =  new BigInteger("274067527940324210164675059514169936706321077"+
                                          "716587152619149078627045197914608984656858592"+
                                          "528731102244773578922061217916250359858144388"+
                                          "719574574464967245379708325471583057239021064"+
                                          "917836916693411052993506893861086722124125827"+
                                          "022950820568155493697312539680639490524889344"+
                                          "865066516444160950915851524094176072393367374"+
                                          "025168676216783811304536549738909665484744275"+
                                          "067155246143772526555170109187191188801364722"+
                                          "517600818541591728004681673515982387104854566"+
                                          "903102995390579614393188326476025663247392444"+
                                          "533092715986735530343056344869496641278845202"+
                                          "223650494175070090294917949972774528610072824"+
                                          "458947092719245467211789956591268607216863753"+
                                          "679624901561412621785554722144008198982178053"+
                                          "538189569539219096856509995119366322932129988"+
                                          "343166711969023877595312100502075950468648415"+
                                          "477775557723829828636660991989941812527795172"+
                                          "124018492383737675186922273677672581350580918"+
                                          "910464808941060995674130486560435794939120577"+
                                          "191490033679039526235346391627269525486933484"+
                                          "148314307406131056278053429513527453093301308"+
                                          "815427956560369404097518372123409181494661668"+
                                          "490006843233849864232293233597067511416359584"+
                                          "421163109870277486363444655636394336745144869"+
                                          "526122913683198214580081892822347978030635397"+
                                          "012712786913863911810555531545723879718478274"+
                                          "2825997470004593284");

    BigInteger result = multiply(factor1, factor2);

    assertEquals(result, expected);
  }

  @Test
  public void testMultiplyLargeNegativeNumbers() {
    BigInteger factor1 =   new BigInteger("-595626405312658704599636370024200076395151472"+
                                          "771767924568367390440634854836130642401309734"+
                                          "848159964191109372749251136446006243551080802"+
                                          "443468883807974005482441860971736801068233313"+
                                          "491232693258531224835309787079942460705208862"+
                                          "391066304008929671240068786982214789313974208"+
                                          "121277040682624462127077310398113929298032224"+
                                          "406902296964052163261880140026245816176689134"+
                                          "688646742318394515117125117931094080634872026"+
                                          "347848034669856506748754843134161491874260587"+
                                          "327337252669362585036570429186753965648086618"+
                                          "124304059986689706447677381144991687868142047"+
                                          "249722766880033576608579416659574000278570884"+
                                          "27139264803160717153834950082732");

    BigInteger factor2 =   new BigInteger("460133273971390735744145615685462110941026662"+
                                          "872377688410148134167657174457131391561681026"+
                                          "343648834014013242938124201005972667172338011"+
                                          "731770664675780475494554955408031889842685747"+
                                          "339694953330696186125702585670064808647571207"+
                                          "588789281591389310763609659973902705551579262"+
                                          "219102156751267333493035940073864847077832336"+
                                          "568113237185953229882343361927635723766493649"+
                                          "384192635115510718823130742006688425246084761"+
                                          "794243459815313486028010705533642096144745946"+
                                          "599634217741317208344059364932570572685763361"+
                                          "928329323093624941613003679183345155384065602"+
                                          "501020875593634261236141372148269233960019631"+
                                          "11550067913622812130192147468387");

    BigInteger expected =  new BigInteger("-274067527940324210164675059514169936706321077"+
                                          "716587152619149078627045197914608984656858592"+
                                          "528731102244773578922061217916250359858144388"+
                                          "719574574464967245379708325471583057239021064"+
                                          "917836916693411052993506893861086722124125827"+
                                          "022950820568155493697312539680639490524889344"+
                                          "865066516444160950915851524094176072393367374"+
                                          "025168676216783811304536549738909665484744275"+
                                          "067155246143772526555170109187191188801364722"+
                                          "517600818541591728004681673515982387104854566"+
                                          "903102995390579614393188326476025663247392444"+
                                          "533092715986735530343056344869496641278845202"+
                                          "223650494175070090294917949972774528610072824"+
                                          "458947092719245467211789956591268607216863753"+
                                          "679624901561412621785554722144008198982178053"+
                                          "538189569539219096856509995119366322932129988"+
                                          "343166711969023877595312100502075950468648415"+
                                          "477775557723829828636660991989941812527795172"+
                                          "124018492383737675186922273677672581350580918"+
                                          "910464808941060995674130486560435794939120577"+
                                          "191490033679039526235346391627269525486933484"+
                                          "148314307406131056278053429513527453093301308"+
                                          "815427956560369404097518372123409181494661668"+
                                          "490006843233849864232293233597067511416359584"+
                                          "421163109870277486363444655636394336745144869"+
                                          "526122913683198214580081892822347978030635397"+
                                          "012712786913863911810555531545723879718478274"+
                                          "2825997470004593284");

    BigInteger result = multiply(factor1, factor2);

    assertEquals(result, expected);
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
