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

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/** Test modpow performance. */
public final class ModPowPerf {

  public static void main(String[] args) throws Exception {
    Gmp.checkLoaded();

    testJava(ModPowVectors.VECTOR1, 512, 10000);
    testJava(ModPowVectors.VECTOR2, 1024, 2000);
    testJava(ModPowVectors.VECTOR3, 2048, 400);
    System.out.println();

    testNativeInsecure(ModPowVectors.VECTOR1, 512, 40000);
    testNativeInsecure(ModPowVectors.VECTOR2, 1024, 8000);
    testNativeInsecure(ModPowVectors.VECTOR3, 2048, 1600);
    System.out.println();

    testNativeSecure(ModPowVectors.VECTOR1, 512, 30000);
    testNativeSecure(ModPowVectors.VECTOR2, 1024, 6000);
    testNativeSecure(ModPowVectors.VECTOR3, 2048, 1200);
    System.out.println();

    GmpTest.forceGc();
  }

  private static void testJava(ModPowVectors.TestVector v, int bits, int count) {
    System.out.println("Testing " + count + " " + bits + "-bit private rsa's Java");
    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      BigInteger pResult = v.message.modPow(v.dp, v.p);
      assertEquals(v.pResult, pResult);
      BigInteger qResult = v.message.modPow(v.dq, v.q);
      assertEquals(v.qResult, qResult);
    }
    long end = System.nanoTime();
    long diff = end - start;
    long durationMillis = TimeUnit.NANOSECONDS.toMillis(diff);
    double durationSecs = durationMillis / 1000.0d;
    System.out.println(count + " " + bits + "-bit private rsa's in " + durationSecs + "s");
    String perSecond = String.format("%.2f", count / durationSecs);
    System.out.println(perSecond + " ops/s");
  }

  private static void testNativeInsecure(ModPowVectors.TestVector v, int bits, int count) {
    System.out.println("Testing " + count + " " + bits + "-bit private rsa's NativeInsecure");
    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      BigInteger pResult = Gmp.modPowInsecure(v.message, v.dp, v.p);
      assertEquals(v.pResult, pResult);
      BigInteger qResult = Gmp.modPowInsecure(v.message, v.dq, v.q);
      assertEquals(v.qResult, qResult);
    }
    long end = System.nanoTime();
    long diff = end - start;
    long durationMillis = TimeUnit.NANOSECONDS.toMillis(diff);
    double durationSecs = durationMillis / 1000.0d;
    System.out.println(count + " " + bits + "-bit private rsa's in " + durationSecs + "s");
    String perSecond = String.format("%.2f", count / durationSecs);
    System.out.println(perSecond + " ops/s");
  }

  private static void testNativeSecure(ModPowVectors.TestVector v, int bits, int count) {
    System.out.println("Testing " + count + " " + bits + "-bit private rsa's NativeSecure");
    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      BigInteger pResult = Gmp.modPowSecure(v.message, v.dp, v.p);
      if (!pResult.equals(v.pResult)) {
        throw new AssertionError("pResult != v.pResult");
      }
      BigInteger qResult = Gmp.modPowSecure(v.message, v.dq, v.q);
      if (!qResult.equals(v.qResult)) {
        throw new AssertionError("qResult != v.qResult");
      }
    }
    long end = System.nanoTime();
    long diff = end - start;
    long durationMillis = TimeUnit.NANOSECONDS.toMillis(diff);
    double durationSecs = durationMillis / 1000.0d;
    System.out.println(count + " " + bits + "-bit private rsa's in " + durationSecs + "s");
    String perSecond = String.format("%.2f", count / durationSecs);
    System.out.println(perSecond + " ops/s");
  }

  private ModPowPerf() {
  }
}
