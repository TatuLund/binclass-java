
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <time.h>

#include "const.h"
#include "vars.h"
#include "format.h"

/* Prototypes */

#ifdef __osf__
#define log_2(x) log2(x)
#else
#define log_2(x) ((log(x) * ILOGOF2))
#endif
#define log_2e(x) ((epsilon > (x)) ? log_2(epsilon) : log_2(x))
/* logarithms of base two, with and without epsilon check */

#define put_dot if (verbose) { fputc('.',stdout); fflush(stdout); }
#define put_mark if (verbose) { fputc('#',stdout); fflush(stdout); }

void start_text (FILE *f);
void version_string (FILE *f);
/* some standardized messages */

void read_line (FILE *f, char *s, int max);

void stop_error (char *s, char *func);
void file_error (char *s, char *func);
void file_exists (char *s, char *func);
void out_of_mem (void);
void internal_error (char *func);
/* some error messages */

double give_stat_random (int i);
/* give random number biased by i:th component of the */
/* statistical profile of the data from interval 0..1 */

double *prepare_log2_factorials (int s);
/* calculate logarithms of base two of the factorials in range 1..s to */
/* table to be later used with stocastic_complexity_u in distmin.c */

double give_true_random (void);
/* just give a random number from interval 0..1 */
/* either use custom generator or default generator depending on _SPECIAL_RANDOM */
/* defined in dep.h */

int before_strain (char *s1, char *s2);

void print_time (FILE *f, time_t dtm);
/* just print time dtm (in seconds to file f */
/* splitted in hours, minutes and seconds */

double unassigned_sc();

double log2_gamma_custom(double x);
/* POSIX compliant UNIX systems have ln(gamma) function in libm (lgamma) */
/* this is only needed for Win95/NT, MSDOS and AmigaOS and used automatically */
/* if UNIX is not defined by header files. */
/* you can use custom version in UNIX by defining USE_CUSTOM_GAMMA in dep.h */
/* used by stochastic_complexity_j in distmin.c */

int random_index (int m);

/* Implementation */

void version_string (FILE *f) {
#ifdef __BORLANDC__
  const char *comp_string = "Borland C";
  const char *comp_version = "";
#ifdef __WIN32__
  const char *os_string = "WindowsNT";
#elif __MSDOS__
  const char *os_string = "MS-DOS";
#else
  const char *os_string = "Unknown OS";
#endif
#elif __GNUC__
  const char *comp_string = "GNU CC";
  const char *comp_version = "";
#if defined (WINNT) || defined (_WIN32)
  const char *os_string = "WindowsNT";
#elif AMIGA
  const char *os_string = "AmigaOS";
#elif __sun__
  const char *os_string = "SunOS";
#elif __osf__
  const char *os_string = "OSF";
#elif __linux__
  const char *os_string = "Linux";
#elif linux
  const char *os_string = "Linux";
#elif SYSTYPE_BSD
  const char *os_string = "UNIX/BSD";
#elif _SYSTYPE_BSD
  const char *os_string = "UNIX/BSD";
#elif UNIX
  const char *os_string = "UNIX";
#elif unix
  const char *os_string = "unix";
#else
  const char *os_string = "Unknown OS";
#endif
#else
  const char *comp_string = "Unknown C";
#if __sun__
  const char *os_string = "SunOS";
#elif __osf__
  const char *os_string = "OSF";
#elif SYSTYPE_BSD
  const char *os_string = "UNIX/BSD";
#elif _SYSTYPE_BSD
  const char *os_string = "UNIX/BSD";
#elif UNIX
  const char *os_string = "UNIX";
#elif unix
  const char *os_string = "unix";
#else
  const char *os_string = "Unknown OS";
#endif
  const char *comp_version = "";
#endif
  fprintf(f,"BinClass v2.2.3 (18.09.2001) %s/%s %s",os_string,comp_string,comp_version);
  
}

void start_text (FILE *f) {
  fprintf(f,"\n");
  version_string(f);
  fprintf(f,"\n\nProgram for binary classification in\n  maximal predictivity and stocastic complexity\n\n");
  fprintf(f,"Program by\n");
  fprintf(f,"  Tatu J. Lund / University of Turku\n\n");
}

void read_line (FILE *f, char *s, int max) {
  fgets(s,max,f);
  s[strlen(s)-1] = 0;
}

void stop_error (char *s, char *func) {
  /*quit the program and display the message s*/
  fprintf(stderr,"\nERROR: %s!\nFunction: %s\n",s,func);
  exit(1);
}

void file_error (char *s, char *func) {
  /*quit the program and display the message s*/
  fprintf(stderr,"\nERROR: Cannot open file %s!\nFunction: %s\n",s,func);
  exit(1);
}

void file_exists (char *s, char *func) {
  /*quit the program and display the message s*/
  fprintf(stderr,"\nERROR: File %s already exists!\nFunction: %s\n",s,func);
  exit(1);
}

void out_of_mem (void) {
  /*quit the program and display the message s*/
  fprintf(stderr,"\nERROR: Out of memory!\n");
  exit(1);
}

void internal_error (char *func) {
  /*quit the program and display the message s*/
  fprintf(stderr,"\nERROR: Internal error, possibly NULL pointer!\nFunction: %s\n",func);
  exit(1);
}

void division_error (char *func) {
  /*quit the program and display the message s*/
  fprintf(stderr,"\nERROR: Internal error, possibly division by zero!\nFunction: %s\n",func);
  exit(1);
}


double *prepare_log2_factorials (int s) {
  double *lt;
  int i;
  
  if ((lt = (double *) malloc(sizeof(double)*s)) == NULL) out_of_mem();
  lt[0] = 0.0;
  for (i=1;i<s;i++) lt[i] = lt[i-1] + log_2((double)i);
  return lt;
}


double give_stat_random (int i) {
  double r;
  double prob;
  
  r = give_true_random();
  prob = total_freqs[i];
  if (r < prob) {
    r = (0.95);
  } else {
    r = (0.05);
  }
  return r;
}

#ifdef _SPECIAL_RANDOM

#define IA 16807
#define IM 2147483647
#define AM (1.0/IM)
#define IQ 127773
#define IR 2836
#define NTAB 32
#define NDIV (1+(IM)/NTAB)
#define REPS 1.2e-7
#define RNMX (1.0-REPS)

int special_seed;
int long iy=0;
long iv[NTAB];
		   
void special_set_rand(int seed_value) {
  srand((unsigned)seed_value);
  special_seed = -rand();
}

/* Minimal Park and Miller random number generator with */
/* Bays-Durham shuffle */
/* From Numerical Recipes in C */
double give_true_random (void) {
  int j;
  long k;
  double temp;
  
  if ((special_seed <= 0) || (!iy))  {
    if (-(special_seed) < 1) special_seed=1;
    for (j=NTAB+7;j>0;j--) {
      k=special_seed/IQ;
      special_seed=IA*(special_seed-k*IQ)-IR*k;
      if (special_seed < 0) special_seed += IM;
      if (j < NTAB) iv[j] = special_seed;
    }
    iy=iv[0];
  }
  k=(special_seed)/IQ;
  special_seed=IA*(special_seed-k*IQ)-IR*k;
  if (special_seed < 0) special_seed += IM;
  j=iy/NDIV;
  iy=iv[j];
  iv[j]=special_seed;
  if ((temp=AM*iy) > RNMX) return RNMX;
  else return temp;
}
#else
double give_true_random (void) {
  return (double)((double)rand() / (double)RAND_MAX);
}
#endif

int before_strain (char *s1, char *s2) {
  int i,t1,t2;
  char *ss1;
  char *ss2;
  
  if ((ss1 = (char *) malloc(sizeof(char)*(id_len)+1)) == NULL) out_of_mem();
  if ((ss2 = (char *) malloc(sizeof(char)*(id_len)+1)) == NULL) out_of_mem();
  for (i=0;i<id_len;i++) {
    if (id_ord[i] != 16) {
      ss1[i] = s1[(int)id_ord[i]-1];
      ss2[i] = s2[(int)id_ord[i]-1];
    } else {
      ss1[i] = 0;
      ss2[i] = 0;
    }
  }
  t1 = atoi(ss1);
  t2 = atoi(ss2);
  free(ss1);
  free(ss2);
  return (t1 < t2);
}

void print_time (FILE *f, time_t dtm) {
  int s,m,h,d;
  d = (dtm / 86400);
  dtm = dtm - (d * 86400);
  h = (dtm / 3600);
  dtm = dtm - (h * 3600);
  m = (dtm / 60);
  dtm = dtm - (m * 60);
  s = dtm;
  fprintf(f,"%3dd %2dh %2dm %2ds\n",d,h,m,s);
}

double unassigned_sc (void) {
  return (double) vec_len * 2.0;
}

#ifdef __unix__
#ifdef USE_CUSTOM_GAMMA
#define _CUSTOM_GAMMA_
#endif
#else
#define _CUSTOM_GAMMA_
#endif

#ifdef _CUSTOM_GAMMA_
double log2_gamma_custom (double x) {
#ifdef _MY_DEBUG
  const char *func = "log2_gamma_custom";
#endif

  /* This is logarithm of base two of Eulers gamma function */

  /* algorithm is the same as in Matlab 5.2 (C. Moler 2-1-91), which in turn bases on */
  /* Fortran code from Argonne National Laboratory NETLIB/SPECFUN by W.J.Cody (June 16, 1988) */
  /* it is application of Chebyshev approximations */
  /* NB: in this contenx this function is never called with values below 0.5 */

  /* References: */

  /* W. J. Cody and K. E. Hillstrom, 'Chebyshev Approximations for */
  /* the Natural Logarithm of the Gamma Function,' Math. Comp. 21, */
  /* 1967, pp. 198-203. */

  /* K. E. Hillstrom, ANL/AMD Program ANLC366S, DGAMMA/DLGAMA, May, 1969. */
 
  /* Hart, Et. Al., Computer Approximations, Wiley and sons, New York, 1968.*/

  /* predefined constants */
  double d1 = -5.772156649015328605195174e-1;
  double p1[8] = {4.945235359296727046734888e0, 2.018112620856775083915565e2, 2.290838373831346393026739e3, 1.131967205903380828685045e4, 2.855724635671635335736389e4, 3.848496228443793359990269e4, 2.637748787624195437963534e4, 7.225813979700288197698961e3};
  double q1[8] = {6.748212550303777196073036e1, 1.113332393857199323513008e3, 7.738757056935398733233834e3, 2.763987074403340708898585e4, 5.499310206226157329794414e4, 6.161122180066002127833352e4, 3.635127591501940507276287e4, 8.785536302431013170870835e3};
  double d2 = 4.227843350984671393993777e-1;
  double p2[8] = {4.974607845568932035012064e0, 5.424138599891070494101986e2, 1.550693864978364947665077e4, 1.847932904445632425417223e5, 1.088204769468828767498470e6, 3.338152967987029735917223e6, 5.106661678927352456275255e6, 3.074109054850539556250927e6};
  double q2[8] = {1.830328399370592604055942e2, 7.765049321445005871323047e3, 1.331903827966074194402448e5, 1.136705821321969608938755e6, 5.267964117437946917577538e6, 1.346701454311101692290052e7, 1.782736530353274213975932e7, 9.533095591844353613395747e6};
  double d4 = 1.791759469228055000094023e0;
  double p4[8] = {1.474502166059939948905062e4, 2.426813369486704502836312e6, 1.214755574045093227939592e8, 2.663432449630976949898078e9, 2.940378956634553899906876e10, 1.702665737765398868392998e11, 4.926125793377430887588120e11, 5.606251856223951465078242e11};
  double q4[8] = {2.690530175870899333379843e3, 6.393885654300092398984238e5, 4.135599930241388052042842e7, 1.120872109616147941376570e9, 1.488613728678813811542398e10, 1.016803586272438228077304e11, 3.417476345507377132798597e11, 4.463158187419713286462081e11};
  double c[7] = {-1.910444077728e-03, 8.4171387781295e-04, -5.952379913043012e-04, 7.93650793500350248e-04, -2.777777777777681622553e-03, 8.333333333333333331554247e-02, 5.7083835261e-03};

  double res=0.0,y,xden,xnum,xm1,xm2,xm4,r,ysq,corr,spi;
  int i;

#ifdef _MY_DEBUG
  if (x < 0.0) stop_error("illegal argument supplied",(char *)func);
#endif

/* 0 <= x <= EPS */

  if (x <= EPS) {
    res = -log(x);
  }

/* EPS < x <= 0.5 */

  if ((x > EPS) && (x <= 0.5)) {
    y = x;
    xden = 1.0;
    xnum = 0.0;
    for (i=0;i<8;i++) {
      xnum = xnum * y + p1[i];
      xden = xden * y + q1[i];
    }
    res = -log(y) + ((y * (d1 + (y * (xnum / xden)))));
  }

/* 0.5 < x <= 0.6796875 */

  if ((x > 0.5) && (x <= 0.6796875)) {
    xm1 = (x - 0.5) - 0.5;
    xden = 1.0;
    xnum = 0.0;
    for (i=0;i<8;i++) {
      xnum = xnum * xm1 + p2[i];
      xden = xden * xm1 + q2[i];
    }
    res = -log(x) + (xm1 * (d2 + (xm1 * (xnum / xden))));
  }

/* 0.6796875 < x <= 1.5 */

  if ((x > 0.6796875) && (x <= 1.5)) {
    xm1 = (x - 0.5) - 0.5;
    xden = 1.0;
    xnum = 0.0;
    for (i=0;i<8;i++) {
      xnum = xnum * xm1 + p1[i];
      xden = xden * xm1 + q1[i];
    }
    res = xm1 * (d1 + (xm1 * (xnum / xden)));
  }

/* 1.5 < x <= 4 */

  if ((x > 1.5) && (x <= 4.0)) {
    xm2 = x - 2.0;
    xden = 1.0;
    xnum = 0;
    for (i=0;i<8;i++) {
      xnum = xnum * xm2 + p2[i];
      xden = xden * xm2 + q2[i];
    }
    res = xm2 * (d2 + (xm2 * (xnum / xden)));
  }

/* 4 < x <= 12 */

   if ((x > 4.0) && (x <= 12.0)) {
     xm4 = x - 4.0;
     xden = -1.0;
     xnum = 0;
     for (i=0;i<8;i++) {
       xnum = xnum * xm4 + p4[i];
       xden = xden * xm4 + q4[i];
     }
     res = d4 + (xm4 * (xnum / xden));
   }

/* x > 12 */

  if (x > 12.0) {
    y = x;
    r = c[6] * 1.0;
    ysq = y * y;
    for(i=0;i<6;i++) {
      r = r / ysq + c[i];
    }
    r = r / y;
    corr = log(y);
    spi = 0.9189385332046727417803297;
    res = r + spi - (0.5*corr) + (y * (corr-1.0));
  }

  return res * ILOGOF2;
}
#endif

int random_index (int m) {
  int ind;
  double r;

  r = give_true_random();
  ind = (int) (r * (double) m);
  if (ind <= 1) ind = 1;
  if (ind >= m) ind = m;
  return ind;
}

/* end of bottom.c */

