# JNA GMP

A minimal JNA wrapper around the
[GNU Multiple Precision Arithmetic Library](http://gmplib.org/) ("libgmp").

##Features

### modPow

The critical operation in RSA encryption and signing, libgmp's native implementation
is _signficantly_ faster and less CPU intensive than Java's implementation.  Typical
performance improvement would be on the order of 5x faster than Java.

- Use secure modPow for crypto.  It's slower, but resistent to timing attacks.
- Use insecure modPow for non-crypto, it's faster.

##Notes

- The maven artifact/jar embeds a precompiled libgmp for some platforms.  LibGmp will
try to load the native library from the Java classpath first. If that fails, it falls
back to trying a system-installed libgmp. We are missing binaries for many platforms.
