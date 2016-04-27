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
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Tests for {@link GmpInteger}. */
public class GmpIntegerTest {

  /** Force GC to verify {@link GmpInteger#mpzMemory} cleans up properly without crashing. */
  @AfterClass public static void forceGc() throws InterruptedException {
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

  @Test public void testNegatives() {
    assertEquals(BigInteger.valueOf(-10), new GmpInteger(BigInteger.valueOf(-10)));
  }

  @Test public void testConstructors() {
    assertEquals(BigInteger.TEN, new GmpInteger(BigInteger.TEN));
    assertEquals(BigInteger.TEN, new GmpInteger(new byte[] {10}));
    assertEquals(BigInteger.TEN, new GmpInteger(1, new byte[] {10}));
    assertEquals(BigInteger.TEN, new GmpInteger("A", 16));
    assertEquals(BigInteger.TEN, new GmpInteger("10"));

    assertEquals(BigInteger.TEN, new GmpInteger(8, new Random() {
      @Override public void nextBytes(byte[] bytes) {
        assertEquals(1, bytes.length);
        bytes[0] = 10;
      }
    }));

    final AtomicBoolean firstTime = new AtomicBoolean(true);
    assertEquals(BigInteger.valueOf(13), new GmpInteger(4, 12, new Random() {
      @Override public int nextInt() {
        if (firstTime.compareAndSet(true, false)) {
          return 13;
        } else {
          return super.nextInt();
        }
      }
    }));
  }
}
