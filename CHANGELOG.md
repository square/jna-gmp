Change Log
==========

Version 2.1.0 *(2018-03-24)*
----------------------------

 * New: Docker build for linux-x86-64 libgmp binary
 * Fixed: reduced required GLIBC to GLIBC_2.7
 * Fixed: modPow: support negative base, exponent.
 * Upgrade: jna updated from 4.0.0 -> 4.5.1 to support AIX, reduce required GLIBC version to GLIBC_2.7


Version 2.0.0 *(2016-11-29)*
----------------------------

 * New: support for `modInverse`, `mpz_kronecker`, `exactDivide`, `gcd`.
 * New: native stubs for `mul` and `mod` (no porcelain).
 * Fixed: support negative integers.
 * Upgrade: embedded LibGMP updated from 5.1.3 -> 6.1.1.


Version 1.1.0 *(2016-03-06)*
----------------------------

 * New: Support for x86-64 Solaris 10, SmartOS, and Illumos.


Version 1.0.1 *(2015-08-30)*
----------------------------

 * Fix: Use the same classloader as the JAR for looking up the native library.
 * Fix: Add precondition check for even modulus on `modPowSecure`.
 * Fix: Clean up preconditions messages on `modPow` variants.


Version 1.0.0 *(2014-02-12)*
----------------------------

Initial version.
