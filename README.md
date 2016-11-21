# JNA GMP project

A minimal JNA wrapper around the
[GNU Multiple Precision Arithmetic Library](http://gmplib.org/) ("libgmp").

## Features

### [jnagmp](jnagmp/README.md)

The basic JNA GMP library; minimal dependencies.

Available on Maven central as `com.squareup.jnagmp:jnagmp`.

### [bc-rsa](bc-rsa/README.md)

Example module that shows integrating JNA GMP with Bouncy Castle.

Available on Maven central as `com.squareup.jnagmp:bouncycastle-rsa`.

## Contributors

- Scott Blum <dragonsinth@gmail.com>
- Jake Wharton <jakewharton@gmail.com> - project management
- Jesse Wilson <jwilson@squareup.com> - project management
- Nathan McCauley <mccauley@squareup.com> - initial security review
- Daniele Perito <daniele@squareup.com> - initial security review
- Sam Quigley <sq@squareup.com> - initial security review
- Josh Humphries <jh@squareup.com> - initial code review
- Christian Meier <meier.kristian@gmail.com> - classloader fix
- Elijah Zupancic <elijah.zupancic@joyent.com> - Solaris binary build, Makefile
- Wilko Henecka <wilko.henecka@nicta.com.au> - modInverse
- David Ruescas <fastness@gmail.com> - kronecker
- Jacob Wilder <os@jacobwilder.org> - gcd, exactDivide, native stubs for mul and mod
- Per Allansson <pallansson@gmail.com> - libgmp 6.1.1 update

## Licensing

- [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)
- libgmp is licensed under the [GNU LGPL](https://www.gnu.org/copyleft/lesser.html)
