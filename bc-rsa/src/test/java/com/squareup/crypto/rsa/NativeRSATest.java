// Copyright 2013, Square, Inc.
package com.squareup.crypto.rsa;

import com.squareup.jnagmp.Gmp;
import java.math.BigInteger;
import org.bouncycastle.crypto.engines.RSAEngine;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.squareup.crypto.rsa.NativeRSAVectors.TestVector;
import static org.junit.Assert.assertEquals;

/** Tests NativeRSAEngine. */
public class NativeRSATest {

  @BeforeClass public static void checkLoaded() {
    Gmp.checkLoaded();
  }

  @Test public void testVectorsJava() {
    doTest(new RSAEngine(), NativeRSAVectors.VECTOR1);
    doTest(new RSAEngine(), NativeRSAVectors.VECTOR2);
    doTest(new RSAEngine(), NativeRSAVectors.VECTOR3);
  }

  @Test public void testVectorsNative() {
    doTest(new NativeRSAEngine(), NativeRSAVectors.VECTOR1);
    doTest(new NativeRSAEngine(), NativeRSAVectors.VECTOR2);
    doTest(new NativeRSAEngine(), NativeRSAVectors.VECTOR3);
  }

  private void doTest(RSAEngine engine, TestVector v) {
    engine.init(true, v.getPrivateKey());
    byte[] message = v.message.toByteArray();
    byte[] signed = engine.processBlock(message, 0, message.length);
    assertEquals(v.signed, new BigInteger(1, signed));

    engine.init(false, v.getPublicKey());
    byte[] decoded = engine.processBlock(signed, 0, message.length);
    assertEquals(v.message, new BigInteger(1, decoded));
  }
}
