// Copyright 2013, Square, Inc.
package com.squareup.crypto.rsa;

import com.squareup.jnagmp.Gmp;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import org.bouncycastle.crypto.engines.RSAEngine;

import static com.squareup.crypto.rsa.NativeRSAVectors.TestVector;
import static org.junit.Assert.assertEquals;

/** Test native RSA performance. */
public final class NativeRSAPerf {

  public static void main(String[] args) {
    Gmp.checkLoaded();

    testJavaPublic(NativeRSAVectors.VECTOR1, 512, 200000);
    testJavaPublic(NativeRSAVectors.VECTOR2, 1024, 100000);
    testJavaPublic(NativeRSAVectors.VECTOR3, 2048, 50000);
    System.out.println();

    testJava(NativeRSAVectors.VECTOR1, 512, 10000);
    testJava(NativeRSAVectors.VECTOR2, 1024, 2000);
    testJava(NativeRSAVectors.VECTOR3, 2048, 400);
    System.out.println();

    testNativePublic(NativeRSAVectors.VECTOR1, 512, 300000);
    testNativePublic(NativeRSAVectors.VECTOR2, 1024, 150000);
    testNativePublic(NativeRSAVectors.VECTOR3, 2048, 75000);
    System.out.println();

    testNative(NativeRSAVectors.VECTOR1, 512, 30000);
    testNative(NativeRSAVectors.VECTOR2, 1024, 6000);
    testNative(NativeRSAVectors.VECTOR3, 2048, 1200);
    System.out.println();
  }

  private static void testJavaPublic(TestVector v, int bits, int count) {
    System.out.println("Testing " + count + " " + bits + "-bit public rsa's Java");
    RSAEngine engine = new RSAEngine();
    engine.init(false, v.getPublicKey());
    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      byte[] signed = v.signed.toByteArray();
      int index = (signed[0] == 0) ? 1 : 0;
      byte[] message = engine.processBlock(signed, index, signed.length - index);
      assertEquals(v.message, new BigInteger(1, message));
    }
    long end = System.nanoTime();
    long diff = end - start;
    long durationMillis = TimeUnit.NANOSECONDS.toMillis(diff);
    double durationSecs = durationMillis / 1000.0d;
    System.out.println(count + " " + bits + "-bit public rsa's in " + durationSecs + "s");
    String perSecond = String.format("%.2f", count / durationSecs);
    System.out.println(perSecond + " ops/s");
  }

  private static void testJava(TestVector v, int bits, int count) {
    System.out.println("Testing " + count + " " + bits + "-bit private rsa's Java");
    RSAEngine engine = new RSAEngine();
    engine.init(true, v.getPrivateKey());
    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      byte[] message = v.message.toByteArray();
      byte[] signed = engine.processBlock(message, 0, message.length);
      assertEquals(v.signed, new BigInteger(1, signed));
    }
    long end = System.nanoTime();
    long diff = end - start;
    long durationMillis = TimeUnit.NANOSECONDS.toMillis(diff);
    double durationSecs = durationMillis / 1000.0d;
    System.out.println(count + " " + bits + "-bit private rsa's in " + durationSecs + "s");
    String perSecond = String.format("%.2f", count / durationSecs);
    System.out.println(perSecond + " ops/s");
  }

  private static void testNativePublic(TestVector v, int bits, int count) {
    System.out.println("Testing " + count + " " + bits + "-bit public rsa's NativeInsecure");
    RSAEngine engine = new NativeRSAEngine();
    engine.init(false, v.getPublicKey());
    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      byte[] signed = v.signed.toByteArray();
      int index = (signed[0] == 0) ? 1 : 0;
      byte[] message = engine.processBlock(signed, index, signed.length - index);
      assertEquals(v.message, new BigInteger(1, message));
    }
    long end = System.nanoTime();
    long diff = end - start;
    long durationMillis = TimeUnit.NANOSECONDS.toMillis(diff);
    double durationSecs = durationMillis / 1000.0d;
    System.out.println(count + " " + bits + "-bit public rsa's in " + durationSecs + "s");
    String perSecond = String.format("%.2f", count / durationSecs);
    System.out.println(perSecond + " ops/s");
  }

  private static void testNative(TestVector v, int bits, int count) {
    System.out.println("Testing " + count + " " + bits + "-bit private rsa's NativeSecure");
    RSAEngine engine = new NativeRSAEngine();
    engine.init(true, v.getPrivateKey());
    long start = System.nanoTime();
    for (int i = 0; i < count; ++i) {
      byte[] message = v.message.toByteArray();
      byte[] signed = engine.processBlock(message, 0, message.length);
      assertEquals(v.signed, new BigInteger(1, signed));
    }
    long end = System.nanoTime();
    long diff = end - start;
    long durationMillis = TimeUnit.NANOSECONDS.toMillis(diff);
    double durationSecs = durationMillis / 1000.0d;
    System.out.println(count + " " + bits + "-bit private rsa's in " + durationSecs + "s");
    String perSecond = String.format("%.2f", count / durationSecs);
    System.out.println(perSecond + " ops/s");
  }

  private NativeRSAPerf() {
  }
}
