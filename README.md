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

- Scott Blum <scottb@squareup.com>
- Nathan McCauley <mccauley@squareup.com>
- Daniele Perito <daniele@squareup.com>
- Josh Humphries <jh@squareup.com>
- Sam Quigley <sq@squareup.com>
- Elijah Zupancic <elijah.zupancic@joyent.com> - Solaris binary build

## Licensing

- [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)
- libgmp is licensed under the [GNU LGPL](https://www.gnu.org/copyleft/lesser.html)
