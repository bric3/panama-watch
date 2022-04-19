/**
 * BLAKE3 is a cryptographic hash function that is:
 *
 * * Much faster than MD5, ShA-1, SHA-2, SHA-3, and BLAKE3.
 * * Secure, unlike MD5 and SHA-1, And secure against length extensions, unlike SHA-2.
 * * Highly parallelizable across any number of threads and SIMD lanes, because it's a Merkle tree on the inside.
 * * Capable of verified streaming and incremental updates, again because it's a Merkle tree.
 * * A PRF, MAC, KDF, and XOF, as well as a regular hash.
 * * One algorithm with no variants, which is fast on x86-64 and also on smaller architectures.
 *
 * https://github.com/BLAKE3-team/BLAKE3
 *
 *
 * ```
 * gcc -c -fPIC -O3 -msse2 blake3_sse2.c -o blake3_sse2.o
 * gcc -c -fPIC -O3 -msse4.1 blake3_sse41.c -o blake3_sse41.o
 * gcc -c -fPIC -O3 -mavx2 blake3_avx2.c -o blake3_avx2.o
 * gcc -c -fPIC -O3 -mavx512f -mavx512vl blake3_avx512.c -o blake3_avx512.o
 * gcc -shared -O3 -o libblake3.so blake3.c blake3_dispatch.c blake3_portable.c \
 *     blake3_avx2.o blake3_avx512.o blake3_sse41.o blake3_sse2.o
 * ```
 *
 *
 */
package io.github.bric3.panama.c.blake3;