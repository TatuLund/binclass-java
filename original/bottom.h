
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

/*
Definitons of routines in bottom.c
*/


/* inline macro will do it faster */
#ifdef __osf__
#define log_2(x) log2(x)
#else
#define log_2(x) ((log(x) * ILOGOF2))
#endif
#define log_2e(x) ((epsilon > (x)) ? log_2(epsilon) : log_2(x)
/* logarithms of base two, with and without epsilon check */

#define log2_factorial(x) (x < 0) ? log2_factorials[0] : log2_factorials[x]

#define put_dot if (verbose) { fputc('.',stdout); fflush(stdout); }
#define put_mark if (verbose) { fputc('#',stdout); fflush(stdout); }

#ifdef __unix__
#ifdef USE_CUSTOM_GAMMA
#define log2_gamma(x) log2_gamma_custom(x)
#else
#define log2_gamma(x) (lgamma(x) * ILOGOF2)
#endif
#else
#define log2_gamma(x) log2_gamma_custom(x)
#endif

#ifdef _SPECIAL_RAND
#define set_rand(s) special_set_rand((int)s)
#else
#define set_rand(s) srand((unsigned)s)
#endif

#define before(s1,s2) ((strcmp(s2,s1) < 0) ? TRUE : FALSE)

extern void start_text (FILE *f);
extern void version_string (FILE *f);
extern void internal_error (char *func);
extern void division_error (char *func);
extern void file_error (char *s, char *func);
extern void file_exists (char *s, char *func);
extern void stop_error (char *s, char *func);
extern void out_of_mem (void);
extern void read_line (FILE *f, char *s, int max);
extern double give_stat_random (int i);
extern double give_true_random (void);
extern int before_strain (char *s1, char *s2);
extern void print_time (FILE *f, time_t dtm);
extern double *prepare_log2_factorials (int s);
extern double unassigned_sc (void);
extern double log2_gamma_custom(double x);
#ifdef _SPECIAL_RANDOM
void special_set_rand(int seed_value);
#endif
extern int random_index (int m);

/* End of bottom.h */

