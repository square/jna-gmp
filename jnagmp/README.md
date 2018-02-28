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

### modInverse

A faster version of BigInteger.modInverse()

### kronecker (jacobi, legendre)

The GMP kronecker implementation generalizes jacobi and legendre symbols.

### exactDivide

A *very* fast way to perform `dividend / divisor` _if and only if_ `dividend % divisor == 0`.
That is, if the `dividend` is a whole-number/integer multiple of `divisor`, then exactDivide is
a much faster way to perform division. If the division operation does not follow the 
`dividend % divisor == 0` requirement then you will get the wrong answer and no error. This is a 
limitation of the GMP function (this method is based on:
[`mpz_divexact`](https://gmplib.org/manual/Integer-Division.html#index-mpz_005fdivexact)). 

### gcd

Finds the greatest common divisor of the two values that are input. It is exactly
analagous to `BigInteger.gcd()` but **much** faster. When benchmarked with two 6148-bit values
(sharing a 3074-bit factor) the GMP implementation was about 20x faster. 

##Notes

- The maven artifact/jar embeds a precompiled libgmp for some platforms.  LibGmp will
try to load the native library from the Java classpath first. If that fails, it falls
back to trying a system-installed libgmp. We are missing binaries for many platforms.

## Building Native Library

Below are instructions for building the libgmp library that ships with this module

#### Linux
There is an included Dockerfile that can be used to compile libgmp with 

First build the Docker image

    docker build -t jnagmp-linux-x86-64 -f Dockerfile.linux-x86-64 .
    
Next run the Docker image which will execute the [Makefile](Makefile.libgmp) and output the compiled library in to _src/main/resources_

    docker run -v $(pwd)/src/main/resources:/build/src/main/resources -t jnagmp-linux-x86-64