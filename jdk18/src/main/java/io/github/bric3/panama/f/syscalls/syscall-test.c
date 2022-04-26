/*
 * Test the stephen syscall (#329)
 */
//#define _GNU_SOURCE
#include <unistd.h>
#include <sys/syscall.h>
#include <stdio.h>

/*
 * Put your syscall number here.
 */
//#define SYS_stephen 329

// SYS_gettimeofday

int main(int argc, char **argv)
{
//  if (argc <= 1) {
//    printf("Must provide a string to give to system call.\n");
//    return -1;
//  }
  printf("Making system call.\n");
  long res = syscall(SYS_getpid);
  printf("System call SYS_getpid returned %ld.\n", res);
  return 0;
}
