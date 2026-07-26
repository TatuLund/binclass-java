/*
Machine, Operating System and Compiler dependand defines and macros
*/


/* #define USE_CUSTOM_GAMMA */
/* uncomment above to use custom loggamma function in UNIX */

/* #define _MY_DEBUG */
/* uncomment above to enable time consuming faulty checks */

/* #define _SPECIAL_RANDOM */
/* comment above to use standard random number genarator */
/* Generator implented here is: Park-Miller with Bays-Durham shuffle */

#define _TEST_ALG1
/* uncomment above to include "distortion minimizer" module */

#define _TEST_ALG2
/* uncomment above to include "semi cumulative" module */

#ifndef RAND_MAX
#ifdef __GNUC__
#ifdef __sun__
#define RAND_MAX 0x7fffffff
/* in some versions of sun systems RAND_MAX was undefined */
#endif
#endif
#else
#ifdef __GNUC__
#if defined (WINNT) || defined (_WIN32)
/* #undef RAND_MAX */
/* #define RAND_MAX 0x7fff */
/* uncomment above if using old faulty port of GCC under NT/95*/
#endif
#endif
#endif

/* End of dep.h */
