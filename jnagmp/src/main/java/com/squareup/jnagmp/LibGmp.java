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
/*
 * GMP function documentation licensed under GNU Free Documentation License.
 * http://gmplib.org/manual/GNU-Free-Documentation-License.html
 *
 * Copyright 1991, 1993, 1994, 1995, 1996, 1997, 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005,
 * 2006, 2007, 2008, 2009, 2010, 2011, 2012, 2013 Free Software Foundation, Inc.
 */
package com.squareup.jnagmp;

import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import java.io.File;

/** Direct JNA mappings to select libgmp functions. */
public final class LibGmp {

  /**
   * Selected based on whether {@code sizeof(size_t)} is {@code 4} or {@code 8} on this system.
   * Mapping size_t directly to int or long is easier and more efficient (less objects created) than
   * the otherwise recommended {@code class size_t extends IntegerType} scheme.
   */
  private static final Class SIZE_T_CLASS;

  static {
    if (Native.SIZE_T_SIZE == 4) {
      SIZE_T_CLASS = SizeT4.class;
    } else if (Native.SIZE_T_SIZE == 8) {
      SIZE_T_CLASS = SizeT8.class;
    } else {
      throw new AssertionError("Unexpected Native.SIZE_T_SIZE: " + Native.SIZE_T_SIZE);
    }
  }

  static {
    loadLibGmp();
  }

  private static void loadLibGmp() {
    try {
      // Explicitly try to load the embedded version first.
      File file = Native.extractFromResourcePath("gmp", LibGmp.class.getClassLoader());
      load(file.getAbsolutePath());
      return;
    } catch (Exception ignored) {
    } catch (UnsatisfiedLinkError ignored) {
    }
    // Fall back to system-wide search.
    load("gmp");
  }

  private static void load(String name) {
    NativeLibrary library = NativeLibrary.getInstance(name, LibGmp.class.getClassLoader());
    Native.register(LibGmp.class, library);
    Native.register(SIZE_T_CLASS, library);
  }

  // CHECKSTYLE.OFF: ConstantName match native name
  /**
   * The GMP version number, as a null-terminated string, in the form “i.j.k”. The embedded release
   * is "5.1.3". Note that the format “i.j” was used, before version 4.3.0, when k was zero.
   */
  public static final String __gmp_version;
  // CHECKSTYLE.ON: ConstantName

  static {
    __gmp_version = NativeLibrary.getProcess() // library is already loaded and linked.
        .getGlobalVariableAddress("__gmp_version") // &(const char* __gmp_version)
        .getPointer(0) // const char* __gmp_version
        .getString(0);
  }

  /** Dummy method to force class initialization. */
  public static void init() {
  }

  /** Helper method to read the value of a (size_t*), depends on {@code sizeof(size_t)}. */
  public static int readSizeT(Pointer ptr) {
    // TODO(scottb): make not public.
    if (SIZE_T_CLASS == SizeT4.class) {
      int result;
      result = ptr.getInt(0);
      assert result >= 0;
      return result;
    } else {
      long result = ptr.getLong(0);
      assert result >= 0;
      assert result < Integer.MAX_VALUE;
      return (int) result;
    }
  }

  // CHECKSTYLE.OFF: TypeName match native name

  /**
   * Pointer to a native libgmp {@code __mpz_struct}; equivalent to {@code mpz_t}.
   *
   * <p> Must point to an allocated block of memory at least {@link #SIZE} bytes large.  This struct
   * does not manage its own memory, the memory must be managed externally.
   *
   * <p> Initialize the structure by calling {@link LibGmp#__gmpz_init} or {@link
   * LibGmp#__gmpz_init2}, and clean up by calling {@link LibGmp#__gmpz_clear}.  You MUST clear the
   * structure before the underlying memory is de-allocated, or you will leak native memory.
   *
   * <p> We chose not to extend {@link com.sun.jna.Structure} because that would add extra
   * marshaling overhead for no benefit.  We don't need direct field access because libgmp manages
   * the struct internals opaquely.
   *
   * @see <a href="https://gmplib.org/manual/Initializing-Integers.html">Initializing Integers</a>
   */
  public static class mpz_t extends Pointer {
    /** The size, in bytes, of the native structure. */
    public static final int SIZE = 16;

    /**
     * Constructs an mpz_t from a native address.
     *
     * @param peer the address of a block of native memory at least {@link #SIZE} bytes large
     */
    public mpz_t(long peer) {
      super(peer);
    }

    /**
     * Constructs an mpz_t from a Pointer.
     *
     * @param from an block of native memory at least {@link #SIZE} bytes large
     */
    public mpz_t(Pointer from) {
      this(Pointer.nativeValue(from));
    }
  }
  // CHECKSTYLE.ON: TypeName match native name


  // CHECKSTYLE.OFF: MethodName match native names

  /** Used on systems with 4-byte size_t. */
  static class SizeT4 {
    static native void __gmpz_import(mpz_t rop, int count, int order, int size, int endian,
        int nails, Pointer buffer);

    static native Pointer __gmpz_export(Pointer rop, Pointer countp, int order, int size,
        int endian, int nails, mpz_t op);
  }

  /** Used on systems with 8-byte size_t. */
  static class SizeT8 {
    static native void __gmpz_import(mpz_t rop, long count, int order, int size, int endian,
        long nails, Pointer buffer);

    static native Pointer __gmpz_export(Pointer rop, Pointer countp, int order, long size,
        int endian, long nails, mpz_t op);
  }

  /**
   * Set rop from an array of word data at op.
   *
   * The parameters specify the format of the data. count many words are read, each size bytes.
   * order can be 1 for most significant word first or -1 for least significant first. Within each
   * word endian can be 1 for most significant byte first, -1 for least significant first, or 0 for
   * the native endianness of the host CPU. The most significant nails bits of each word are
   * skipped, this can be 0 to use the full words.
   *
   * There is no sign taken from the data, rop will simply be a positive integer. An application can
   * handle any sign itself, and apply it for instance with mpz_neg.
   *
   * There are no data alignment restrictions on op, any address is allowed.
   *
   * Here's an example converting an array of unsigned long data, most significant element first,
   * and host byte order within each value.
   *
   * <pre>{@code
   * unsigned long  a[20];
   * // Initialize z and a
   * mpzImport (z, 20, 1, sizeof(a[0]), 0, 0, a);
   * }</pre>
   *
   * This example assumes the full sizeof bytes are used for data in the given type, which is
   * usually true, and certainly true for unsigned long everywhere we know of. However on Cray
   * vector systems it may be noted that short and int are always stored in 8 bytes (and with sizeof
   * indicating that) but use only 32 or 46 bits. The nails feature can account for this, by passing
   * for instance 8*sizeof(int)-INT_BIT.
   */
  public static void __gmpz_import(mpz_t rop, int count, int order, int size, int endian, int nails,
      Pointer buffer) {
    if (SIZE_T_CLASS == SizeT4.class) {
      SizeT4.__gmpz_import(rop, count, order, size, endian, nails, buffer);
    } else {
      SizeT8.__gmpz_import(rop, count, order, size, endian, nails, buffer);
    }
  }

  /**
   * Fill rop with word data from op.
   *
   * The parameters specify the format of the data produced. Each word will be size bytes and order
   * can be 1 for most significant word first or -1 for least significant first. Within each word
   * endian can be 1 for most significant byte first, -1 for least significant first, or 0 for the
   * native endianness of the host CPU. The most significant nails bits of each word are unused and
   * set to zero, this can be 0 to produce full words.
   *
   * The number of words produced is written to *countp, or countp can be NULL to discard the count.
   * rop must have enough space for the data, or if rop is NULL then a result array of the necessary
   * size is allocated using the current GMP allocation function (see Custom Allocation). In either
   * case the return value is the destination used, either rop or the allocated block.
   *
   * If op is non-zero then the most significant word produced will be non-zero. If op is zero then
   * the count returned will be zero and nothing written to rop. If rop is NULL in this case, no
   * block is allocated, just NULL is returned.
   *
   * The sign of op is ignored, just the absolute value is exported. An application can use mpz_sgn
   * to get the sign and handle it as desired. (see Integer Comparisons)
   *
   * There are no data alignment restrictions on rop, any address is allowed.
   *
   * When an application is allocating space itself the required size can be determined with a
   * calculation like the following. Since mpz_sizeinbase always returns at least 1, count here will
   * be at least one, which avoids any portability problems with malloc(0), though if z is zero no
   * space at all is actually needed (or written).
   *
   * <pre>{@code
   * numb = 8*size - nail;
   * count = (mpz_sizeinbase (z, 2) + numb-1) / numb;
   * p = malloc (count * size);
   * }</pre>
   */
  public static void __gmpz_export(Pointer rop, Pointer countp, int order, int size, int endian,
      int nails, mpz_t op) {
    if (SIZE_T_CLASS == SizeT4.class) {
      SizeT4.__gmpz_export(rop, countp, order, size, endian, nails, op);
    } else {
      SizeT8.__gmpz_export(rop, countp, order, size, endian, nails, op);
    }
  }

  /**
   * Initialize integer with limb space and set the initial numeric value to 0. Each variable should
   * normally only be initialized once, or at least cleared out (using mpz_clear) between each
   * initialization.
   *
   * Here is an example of using mpz_init:
   *
   * <pre>{@code
   * MP_INT integ;
   * mpz_init (&integ);
   * ...
   * mpz_add (&integ, ...);
   * ...
   * mpz_sub (&integ, ...);
   *
   * // Unless you are now exiting the program, do ...
   * mpz_clear (&integ);
   * }</pre>
   *
   * As you can see, you can store new values any number of times, once an object is initialized.
   */
  public static native void __gmpz_init(mpz_t integer);

  /**
   * Initialize x, with space for n-bit numbers, and set its value to 0. Calling this function
   * instead of mpz_init or mpz_inits is never necessary; reallocation is handled automatically by
   * GMP when needed.
   *
   * While n defines the initial space, x will grow automatically in the normal way, if necessary,
   * for subsequent values stored. mpz_init2 makes it possible to avoid such reallocations if a
   * maximum size is known in advance.
   *
   * In preparation for an operation, GMP often allocates one limb more than ultimately needed. To
   * make sure GMP will not perform reallocation for x, you need to add the number of bits in
   * mp_limb_t to n.
   */
  public static native void __gmpz_init2(mpz_t x, NativeLong n);

  /**
   * Free the space occupied by x. Call this function for all mpz_t variables when you are done with
   * them.
   */
  public static native void __gmpz_clear(mpz_t x);

  /**
   * Set rop to (base raised to exp) modulo mod.
   *
   * Negative exp is supported if an inverse base^-1 mod mod exists (see mpz_invert in Number
   * Theoretic Functions). If an inverse doesn't exist then a divide by zero is raised.
   */
  public static native void __gmpz_powm(mpz_t rop, mpz_t base, mpz_t exp, mpz_t mod);

  /**
   * Set rop to (base raised to exp) modulo mod.
   *
   * It is required that exp > 0 and that mod is odd.
   *
   * This function is designed to take the same time and have the same cache access patterns for any
   * two same-size arguments, assuming that function arguments are placed at the same position and
   * that the machine state is identical upon function entry. This function is intended for
   * cryptographic purposes, where resilience to side-channel attacks is desired.
   */
  public static native void __gmpz_powm_sec(mpz_t rop, mpz_t base, mpz_t exp, mpz_t mod);

  private LibGmp() {
  }
}
