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
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

/** Manual test for modpow leaks. Run this and keep an eye on the memory. */
public final class ModPowLeak {

  // TODO(scottb): is there some better way to do this?

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final BigInteger SIGNING_EXPONENT = BigInteger.valueOf(3);
  public static final int RSA_KEY_BITS = 2048;
  public static final int CORES = 4;

  private static KeyPair generateKeyPair(int rsaKeyBits) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");

    RSAKeyGenParameterSpec rsaKeyGenParameterSpec =
        new RSAKeyGenParameterSpec(rsaKeyBits, SIGNING_EXPONENT);
    generator.initialize(rsaKeyGenParameterSpec);
    return generator.generateKeyPair();
  }

  public static void main(String[] args) throws Exception {
    Gmp.checkLoaded();

    KeyPair pair = generateKeyPair(RSA_KEY_BITS);
    RSAPrivateCrtKey priv = (RSAPrivateCrtKey) pair.getPrivate();
    RSAPublicKey pub = (RSAPublicKey) pair.getPublic();

    byte[] random = new byte[2048 / 8];
    SECURE_RANDOM.nextBytes(random);
    // Clear the top bit to ensure it fits.
    random[0] &= 0x7F;

    final BigInteger message = new BigInteger(1, random);
    BigInteger signed =
        Gmp.modPowSecure(message, priv.getPrivateExponent(), priv.getModulus());

    BigInteger recovered =
        Gmp.modPowSecure(signed, pub.getPublicExponent(), pub.getModulus());

    assertEquals(message, recovered);

    ExecutorService service = Executors.newFixedThreadPool(4);
    final GmpInteger exponent = new GmpInteger(pub.getPublicExponent());
    final GmpInteger modulus = new GmpInteger(pub.getModulus());
    for (int i = 0; i < CORES; ++i) {
      service.execute(new Runnable() {
        @Override public void run() {
          while (true) {
            Gmp.modPowSecure(message, exponent, modulus);
          }
        }
      });
    }
    service.shutdown();

    while (true) {
      Thread.sleep(1000);
      System.gc();
    }
  }

  private ModPowLeak() {
  }
}
