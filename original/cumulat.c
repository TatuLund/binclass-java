/*
Module for Cumulative classification (Bayesian Predictive Identification)
*/


#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "const.h"
#include "bottom.h"
#include "vectors.h"
#include "binset.h"
#include "distmin.h"
#include "compare.h"
#include "format.h"
#include "bootstra.h"
#include "binstuff.h"
#include "vars.h"

/* #define CUM_DEBUG uncomment to get maximum verbosity */

/* #define CUM_DEBUG */
#define _LOG_MODEL

#define dp_wij(s,n) (log_2((double)((n) + 1)) - log_2((double)(s - (n) + 1)))
#define dp_lambdaj(s) (log_2((double)(s+1)))
#define dp_initial_prob(l,d) (-((double)(-l) + log_2((double)d)))

DynPartition* dp_allocate_class (int l);
void dp_deallocate (DynPartition *DP);
/* Allocate and deallocate space for new class node */

void dp_update_freq (DynPartition *P, BV *x);
/* Update frequency vector and hmo in P as x is assigned to class */

void dp_add_vector (DynPartition *P, BV *x);
/* Add vector to node P */

DynPartition *dp_initialize (BV *x);
/* Intitialize one class dynamic partition with vector x */

void dp_extend (DynPartition *P, BV *x);
/* Add new class of vector x to dynamic partition */

double dp_prob (DynPartition *P, BV *x);
/* Calculate distance to probability vector freq */

int dp_find_class (DynPartition *P, BV *x, int delta);
int dp_find_class_sc (DynPartition *P, BV *x);
/* Find class in partition P that is nearest to given vector x */
/*    by maximal predictivty or stochastic complexity (_sc) */

void dp_put_vector (DynPartition *P, BV *x, int i);
/* Add vector x to i:th class of partition */

DynPartition *dp_issue (DynPartition *P, ST *V, Vector *PF, Vector *SC, char *outfile, int log_actions);
/* Issue cumulative clasification to set V */

Partition *dp_convert (DynPartition *DP);
/* Convert dynamic partitition to static partition */

DynPartition *dp_trevnoc (Partition *P);
/* Convert static partition to dynamic partitition  */

DynPartition *dp_copy (DynPartition *P);
/* Make exact copy of partiton */

ST *dp_read_set (FILE *f, char *hdrfile);
/* Read training set in random order */

ST *dp_read_set_order (FILE *f, char *hdrfile);
/* Read training set in ascending order presented by ID string  */

ST *dp_redraw (ST *V);
/* Randomize order of vectors in training set V */

double dp_stochastic_complexity (DynPartition *DP);
double dp_stochastic_complexity_x (DynPartition *DP, int c, BV *x);
double dp_stochastic_complexity_xnew (DynPartition *DP, BV *x);
/* Calculate stochastic complexity (SCmb) for partition and */
/*    partition where x is put to class c (_x) and */
/*    partition plus new vector as a new class (_xnew) */

double dp_predictive_fit (DynPartition *P);
double predictive_fit (Partition *P);
/* Calculate Predictive Fit (ie Stochastic Complexity) for partition */
/*    with given delta value */

void do_cumulative_classification (char *datfile, char *basfile, char* outfile, char *parfile, char *hdrfile);
/* Setup training set and enivironment for cumulative classification and start it */

void analyse_cumulative (char *datfile, char *basfile, char *cmpfile, char *outfile, char *parfile1, char *parfile2, char *ordfile1, char *ordfile2, char *hdrfile);

int delta_value (int k);

int delta_value (int k) {
  return (fixed_delta) ? real_delta_value : (real_delta_value - k);
}

DynPartition* dp_allocate_class (int l) {
  DynPartition *P;
  
  if ((P = (DynPartition *) malloc(sizeof(DynPartition))) == NULL) out_of_mem();
  P->size = 0;
  P->el = NULL;
  P->next = NULL;
  P->freq = allocate_ivector(l);
  P->hmo = allocate_ivector(l);
  P->nij = allocate_ivector(l);
  return P;
}

void dp_deallocate (DynPartition *P) {
  free(P->freq);
  free(P->hmo);
  free(P->nij);
  free(P);
}

int dp_nij (DynPartition *P, int i) {
  ST *tmp;
  int *x;
  int *hmo;
  int n;

  tmp = P->el;
  hmo = P->hmo->el;
  n = 0;
  while (elements_left(tmp)) {
    x = get_vector(tmp);
    n += (hmo[i] != x[i]);
    tmp = next_element(tmp);
  }
  return n;
}

void dp_update_freq (DynPartition *P, BV *x) {
  int f,i,l,s;
  double a;
  ST *V;
  int *y;
  int *freq;
  int *hmo;
  int *nij;
  int *el;

  freq = P->freq->el;
  el = x->el;
  l = P->freq->l;
  s = P->size;
  hmo = P->hmo->el;
  nij = P->nij->el;
  for (i=1;i<l;i++) {
    f = freq[i];
    f += el[i];
    freq[i] = f;
    a = (double)((double)f / (double)s);
    hmo[i] = (a < 0.5) ? 0 : 1;
  }
  V = P->el;
  for (i=1;i<l;i++) nij[i] = 0;
  while (elements_left(V)) {
    y = get_vector(V);
    for (i=1;i<l;i++) {
      nij[i] += (y[i] != hmo[i]);
    }
    V = next_element(V);
  }
}

void dp_add_vector (DynPartition *P, BV *x) {

  P->el = add_element(P->el,x);
  P->size += 1;
  dp_update_freq(P,x);
}

void dp_extend (DynPartition *P, BV *x) {
  DynPartition *tmp;
  DynPartition *nP;
  int l;
  
  l = vec_len;
  tmp = P;
  while (tmp->next != NULL) tmp = tmp->next;
  nP = dp_allocate_class(l);
  tmp->next = nP;
  dp_add_vector(nP,x);
}

int dp_k (DynPartition *P) {
  DynPartition *tmp;
  int k;
  
  if (P == NULL) return 0;
  tmp = P;
  k = 1;
  while (tmp->next != NULL) {
    tmp = tmp->next;
    k++;
  }
  return k;
}

DynPartition *dp_initialize (BV *x) {
  DynPartition *P;
  int l;
  
  l = vec_len;
  
  P = dp_allocate_class(l);
  dp_add_vector(P,x);
  return P;
}

double dp_bj (DynPartition *P, int s, int l) {
  int i;
  double bj;
  int *nij;

  nij = P->nij->el;
  bj = 0.0;
  for (i=1;i<l;i++) {
    bj += (log_2((double)(s - nij[i] + 1)) - log_2((double)(s + 2)));
  }
  return bj;
}

double dp_prob (DynPartition *P, BV *x) {
  double d;
  int s,l,i;
  int *hmo;
  int *nij;
  int *el;

  s = P->size;
  l = x->length-1;
  hmo = P->hmo->el;
  nij = P->nij->el;
  el = x->el;
  d = 0.0;
  for (i=1;i<l;i+=2) {
    d += (dp_wij(s,nij[i]) * ((double)(el[i] != hmo[i])));
    d += (dp_wij(s,nij[i+1]) * ((double)(el[i+1] != hmo[i+1])));
  }
  if (l % 2) d += (dp_wij(s,nij[l]) * ((double)(el[l] != hmo[l])));
  d += dp_bj(P,s,(l+1)) + dp_lambdaj(s);
  return -d;
}

#ifdef CUM_DEBUG
void print_debug_info2 (BV *x, DynPartition *P, double d, int k) {
  IntVector *hmo;
  IntVector *nij;
  int i,l;

  hmo = P->hmo;
  nij = P->nij;
  l = x->length;
  fprintf(stdout,"HMO = (");
  for (i=1;i<l;i++) {
    if (hmo->el[i]) fputc('1',stdout);
    else fputc('0',stdout);
  }
  fprintf(stdout,") l(%d) = %1.4f\n",k,d);
  fprintf(stdout,"Nij = (");
  for (i=1;i<l;i++) {
    fprintf(stdout,"%d",nij->el[i]);
    if (i<(l-1)) fputc(',',stdout);
  }
  fprintf(stdout,")\n");
}

void print_debug_info1 (BV *x, double d) {
  int i,l;

  l = x->length;
  fprintf(stdout,"l(k+1) = %1.4f\n X = ",d);
  for (i=1;i<l;i++) {
    if (x->el[i]) fputc('1',stdout);
    else fputc('0',stdout);
  }
  fprintf(stdout,"\n");
}
#endif

int dp_find_class (DynPartition *P, BV *x, int delta) {
  DynPartition *tmp;
  int i,l,imin;
  double d,dmin;
  
  tmp = P;
  if (cum_no_new_classes) {
    i = 1;
    imin = i;
    dmin = dp_prob(tmp,x);
#ifdef CUM_DEBUG
    print_debug_info2(x,tmp,d,i);
#endif
    tmp = tmp->next;
  } else {
    i = 0;
    imin = i;
    l = (x->length)-1;
    dmin = dp_initial_prob(l,delta);
#ifdef CUM_DEBUG
    print_debug_info1(x,dmin);
#endif
  }
  while (tmp != NULL) {
    i++;
    d = dp_prob(tmp,x);
#ifdef CUM_DEBUG
    print_debug_info2(x,tmp,d,i);
#endif
    if (d < dmin) {
      dmin = d;
      imin = i;
    }
    tmp = tmp->next;
  }
  x->dist = dmin;
  return imin;
}

void dp_put_vector (DynPartition *P, BV *x, int i) {
  int j;
  DynPartition *tmp;
  
  tmp = P;
  j = 1;
  while ((tmp != NULL) && (j < i)) {
    j++;
    tmp = tmp->next;
  }
  dp_add_vector(tmp,x);
}

double dp_stochastic_complexity (DynPartition *DP) {
  int j,n,k,l;
  double sc1,sc2;
  IntVector *F;
  DynPartition *tmp;
  
  /* coding of the bits */
  l = vec_len;
  k = 0;
  n = 0;
  sc2 = 0.0;
  tmp = DP;
  while (tmp != NULL) {
    k++;
    n += tmp->size;
    for (j=1;j<l;j++) {
      sc2 += log2_factorial(tmp->size + 1);
      F = tmp->freq;
      sc2 -= log2_factorial(F->el[j]);
      sc2 -= log2_factorial((tmp->size) - (F->el[j]));
    }
    tmp = tmp->next;
  }
  
  /* coding of the codebook */
  sc1 = log2_factorial(n);
  tmp = DP;
  while (tmp != NULL) {
    sc1 -= log2_factorial(tmp->size);
    tmp = tmp->next;
  }
  sc1 += log2_factorial(n+k-1);
  sc1 -= log2_factorial(n);
  sc1 -= log2_factorial(k-1);
  
  /* stochastic complexity per vector */
  return ((sc1 + sc2) / (double) n);
}

double dp_stochastic_complexity_xnew (DynPartition *DP, BV *x) {
  int j,n,k,l;
  double sc1,sc2;
  IntVector *F;
  DynPartition *tmp;
  
  /* coding of the bits */
  l = vec_len;
  k = 0;
  n = 0;
  sc2 = 0.0;
  tmp = DP;
  while (tmp != NULL) {
    k++;
    n += tmp->size;
    for (j=1;j<l;j++) {
      sc2 += log2_factorial(tmp->size + 1);
      F = tmp->freq;
      sc2 -= log2_factorial(F->el[j]);
      sc2 -= log2_factorial((tmp->size) - (F->el[j]));
    }
    tmp = tmp->next;
  }
  /* plus x as new class */
  n++;
  k++;
  for (j=1;j<l;j++) {
    sc2 += log2_factorial(2);
    sc2 -= log2_factorial(x->el[j]);
    sc2 -= log2_factorial((1 - (x->el[j])));
  }
  
  /* coding of the codebook */
  sc1 = log2_factorial(n);
  tmp = DP;
  while (tmp != NULL) {
    sc1 -= log2_factorial(tmp->size);
    tmp = tmp->next;
  }
  /* plus x as new class */
  sc1 -= log2_factorial(1);
  /* normal stuff */
  sc1 += log2_factorial(n+k-1);
  sc1 -= log2_factorial(n);
  sc1 -= log2_factorial(k-1);
  
  /* stochastic complexity per vector */
  return ((sc1 + sc2) / (double) n);
}

double dp_stochastic_complexity_x (DynPartition *DP, int c, BV *x) {
  int j,i,n,k,l;
  double sc1,sc2;
  IntVector *F;
  DynPartition *tmp;
  
  /* coding of the bits */
  l = vec_len;
  k = 0;
  n = 0;
  sc2 = 0.0;
  tmp = DP;
  while (tmp != NULL) {
    k++;
    /* case 1: x is in this class */
    if (k == c) {
      n += (tmp->size) + 1;
      for (j=1;j<l;j++) {
	sc2 += log2_factorial((tmp->size + 2));
	F = tmp->freq;
	sc2 -= log2_factorial(((F->el[j]) + (x->el[j])));
	sc2 -= log2_factorial((((tmp->size) + 1) - ((F->el[j]) + (x->el[j]))));
      }
      /* case 2: no change */
    } else {
      n += tmp->size;
      for (j=1;j<l;j++) {
	sc2 += log2_factorial(tmp->size + 1);
	F = tmp->freq;
	sc2 -= log2_factorial(F->el[j]);
	sc2 -= log2_factorial((tmp->size) - (F->el[j]));
      }
    }
    tmp = tmp->next;
  }
  
  /* coding of the codebook */
  sc1 = log2_factorial(n);
  tmp = DP;
  i = 0;
  while (tmp != NULL) {
    i++;
    if (i == c) sc1 -= log2_factorial(((tmp->size) + 1));
    else sc1 -= log2_factorial(tmp->size);
    tmp = tmp->next;
  }
  sc1 += log2_factorial(n+k-1);
  sc1 -= log2_factorial(n);
  sc1 -= log2_factorial(k-1);
  
  /* stochastic complexity per vector */
  return ((sc1 + sc2) / (double) n);
}

int dp_find_class_sc (DynPartition *P, BV *x) {
  DynPartition *tmp;
  int i,imin;
  double d,dmin;
  
  tmp = P;
  if (cum_no_new_classes) {
    i = 0;
    imin = i;
    dmin = dp_stochastic_complexity_xnew(P,x);
  } else {
    i = 1;
    imin = i;
    dmin = dp_stochastic_complexity_x(P,i,x);
    tmp = tmp->next;
  }
  while (tmp != NULL) {
    i++;
    d = dp_stochastic_complexity_x(P,i,x);
    if (d < dmin) {
      dmin = d;
      imin = i;
    }
    tmp = tmp->next;
  }
  return imin;
}

DynPartition *dp_issue (DynPartition *P, ST *V, Vector *PF, Vector *SC, char *outfile, int log_actions) {
  const char *func = "dp_issue";
  BV *x;
  FILE *o = NULL;
  int i,j,k,Vn,Pn,n,iw,step;
  DynPartition *tmp1;
  double pf,sc;
  
  j = 1;
  Vn = size(V);
  iw = Vn / 10;
  Pn = 0;
  k = 0;
  if (P != NULL) {
    tmp1 = P;
    while (tmp1 != NULL) {
      Pn += tmp1->size;
      tmp1 = tmp1->next;
      k++;
    }
    step = (Pn + Vn) / cumulative_samples;
    /* for (i=1;i<(Pn+1);i++) if ((i % step) == 0) j++; */
  } else {
    step = Vn / cumulative_samples;
  }
  if (log_file && log_actions) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    x = get_element(V);
    fprintf(o,"\n\n\nl(k+1) = %.4f\n",dp_initial_prob(((x->length)-1),delta_value(k)));
    fclose(o);
  }
  if (P == NULL) {
    x = get_element(V);
    P = dp_initialize(x);
    k = 1;
    n = Pn + 1;
    V = del_element(V);
    if (log_file && log_actions) {
      pf = dp_predictive_fit(P);
      sc = dp_stochastic_complexity(P);
      if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
      fprintf(o,"First class\n");
      fprintf(o,"   1: %s %s (d = ",x->clasname,x->strain);
      if ((x->dist < 9.99995) && (x->dist > 0.0)) fputc(' ',o);
      fprintf(o,"%.4f, pf = ",x->dist);
      if ((pf < 9.99995) && (pf > 0.0)) fputc(' ',o);
      fprintf(o,"%.4f, sc = ",pf);
      if (sc < 9.99995) fputc(' ',o);
      fprintf(o,"%.4f)\n",sc);
      fclose(o);
    } else if ((n % step) == 1) {
      PF->el[1] = dp_predictive_fit(P);
      SC->el[1] = dp_stochastic_complexity(P);
      j++;
    }
  } else {
    n = Pn;
    if (log_file && log_actions) {
      pf = dp_predictive_fit(P);
      sc = dp_stochastic_complexity(P);
      if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
      fprintf(o,"Base file consists of %d vectors (SC = %1.4f, PF = %1.4f)\n",n,sc,pf);
      fclose(o);
    }
  }
  while (elements_left(V)) {
    if ((verbose) && ((n % iw) == 0)) {
      fputc('.',stdout);
      fflush(stdout);
    }
    x = get_element(V);
    if (bayesian_predictive) i = dp_find_class(P,x,delta_value(k));
    else i = dp_find_class_sc(P,x);
    if (log_file && log_actions) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    if (i == 0) {
      dp_extend(P,x);
      k++;
      i = k;
      if (log_file && log_actions) fprintf(o,"New class %d:\n",i);
      if (!fixed_delta) if (log_file && log_actions) fprintf(o,"l(k+1) = %1.4f\n",dp_initial_prob(((x->length)-1),delta_value(k)));
    } else {
      dp_put_vector(P,x,i);
    }
    if (log_file && log_actions) {
      pf = dp_predictive_fit(P);
      sc = dp_stochastic_complexity(P);
      fprintf(o,"%4d: %s %s (d = ",i,x->clasname,x->strain);
      if (x->dist < 9.99995) fputc(' ',o);
      fprintf(o,"%.4f, pf = ",x->dist);
      if ((pf < 9.99995) && (pf > 0.0)) fputc(' ',o);
      fprintf(o,"%.4f, sc = ",pf);
      if (sc < 9.99995) fputc(' ',o);
      fprintf(o,"%.4f)\n",sc);
    } else if ((n % step) == 1) {
      PF->el[j] = dp_predictive_fit(P);
      SC->el[j] = dp_stochastic_complexity(P);
      j++;
    }
    V = del_element(V);
    n++;
    if (log_file && log_actions) fclose(o);
  }
  if (verbose) fprintf(stdout,"\n");
  return P;
}

Partition *dp_convert (DynPartition *DP) {
  int k,i;
  DynPartition *tmp;
  DynPartition *tmp2;
  Partition *P;
  
  k = 0;
  tmp = DP;
  while (tmp != NULL) {
    k++;
    tmp = tmp->next;
  }
  P = allocate_partition(k+1);
  tmp = DP;
  k++;
  for (i=1;i<k;i++) {
    P->el[i] = tmp->el;
    tmp->el = NULL;
    tmp2 = tmp;
    tmp = tmp->next;
    dp_deallocate(tmp2);
  }
  return P;
}

DynPartition *dp_trevnoc2 (Partition *P) {
  DynPartition *DP = NULL;
  ST *V;
  BV *x;
  int k,i;
  
  k = P->k;
  for (i=1;i<k;i++) {
    V = P->el[i];
    x = get_element(V);
    if (DP == NULL) {
      DP = dp_initialize(x);
    } else {
      dp_extend(DP,x);
    }
    V = del_element(V);
    while (elements_left(V)) {
      x = get_element(V);
      dp_put_vector(DP,x,i);
      V = del_element(V);
    }
  }
  return DP;
}

DynPartition *dp_trevnoc (Partition *P) {
  DynPartition *DP = NULL;
  DynPartition *tmp = NULL;
  DynPartition *nP;
  ST *V;
  BV *x;
  int k,i,j,l,s,f;
  double a;
  IntVector *freq;
  IntVector *nij;
  IntVector *hmo;

  l = vec_len;
  k = P->k;
  for (i=1;i<k;i++) {
    /* allocate new class */
    if (tmp == NULL) {
      DP = dp_allocate_class(l);
      tmp = DP;
    } else {
      nP = dp_allocate_class(l);
      tmp->next = nP;
      tmp = tmp->next;
    }
    tmp->el = P->el[i];
    P->el[i] = NULL;
    /* update frequency vector */
    freq = tmp->freq;
    hmo = tmp->hmo;
    s = 0;
    V = tmp->el;
    while (elements_left(V)) {
      x = get_element(V);
      for (j=1;j<l;j++) freq->el[j] = freq->el[j] + x->el[j];
      s++;
      V = next_element(V);
    }
    tmp->size = s;
    /* calculate HMO */
    for (j=1;j<l;j++) {
      f = freq->el[j];
      a = (double)((double)f / (double)s);
      if (a < 0.5) hmo->el[j] = 0;
      else hmo->el[j] = 1;
    }
    /* count differing bits */
    nij = tmp->nij;
    V = tmp->el;
    while (elements_left(V)) {
      x = get_element(V);
      for (j=1;j<l;j++) if (x->el[j] != hmo->el[j]) nij->el[j] = nij->el[j] + 1;
      V = next_element(V);
    }
  }
  deallocate_partition(P);
  
  return DP;
}

DynPartition *dp_copy (DynPartition *P) {
  const char *func = "dp_copy";
  DynPartition *DP = NULL;
  DynPartition *tmp;
  ST *V;
  BV *x;
  int i;
  
  if (P == NULL) internal_error((char *)func);
  tmp = P;
  i = 0;
  /* while there are classes left */
  while (tmp != NULL) {
    V = tmp->el;
    if (V == NULL) internal_error((char *)func);
    x = bv_copy(get_element(V));
    if (DP == NULL) DP = dp_initialize(x);
    else dp_extend(DP,x);
    V = next_element(V);
    i++; /* class number */
    while (elements_left(V)) {
      x = bv_copy(get_element(V));
      dp_put_vector(DP,x,i);
      V = next_element(V);
    }
    /* next class */
    tmp = tmp->next;
  }
  return DP;
}

ST *dp_redraw (ST *V) {
  const char *func = "dp_redraw";
  ST *W = NULL;
  BV *x;
  int ind,n;
  double r;
  
  n = size(V);
  
  while (elements_left(V)) {
    r = give_true_random();
    ind = floor(r * (double)n);
    if (ind < 1) ind = 1;
    if (ind > n) ind = n;
    if (ind > 1) {
      x = get_vector_i(V,ind);
      if (x == NULL) internal_error((char *)func);
      W = add_element(W,x);
      n--;
      V = del_vector_i(V,ind);
    } else {
      x = get_element(V);
      W = add_element(W,x);
      n--;
      V = del_element(V);
    }
  }
  
  return W;
}

ST *dp_read_set (FILE *f, char *hdrfile) {
  const char *func = "dp_read_set";
  ST *V;
  ST *W = NULL;
  BV *x;
  int ind,n;
  double r;

  V = read_set(f,hdrfile);
  n = size(V);
  
  while (elements_left(V)) {
    r = give_true_random();
    ind = floor(r * (double)n);
    if (ind < 1) ind = 1;
    if (ind > n) ind = n;
    if (ind > 1) {
      x = get_vector_i(V,ind);
      if (x == NULL) internal_error((char *)func);
      W = add_element(W,x);
      n--;
      V = del_vector_i(V,ind);
    } else {
      x = get_element(V);
      W = add_element(W,x);
      n--;
      V = del_element(V);
    }
  }

  return W;
}

ST *dp_read_set_order (FILE *f, char *hdrfile) {
  ST *V = NULL;
  BV *x;
  char *xs;
  int l,sl,n,i;

  read_header(hdrfile);

  if ( (xs = (char *) malloc(sizeof(char)*MAX_LENGTH)) == NULL ) out_of_mem();
  sl = vec_len;
  n = 0;
  while (!feof(f)) {
    read_line(f,xs,MAX_LENGTH);
    if (!feof(f)) {
      n++;
      /* allocate space for new vector */
      x = bv_allocate();
      x->num = n;
      /* file empty spaces */
      l = strlen(&xs[vec_offs])+1;
      if (l < sl) {
	for (i=l;i<sl;i++) xs[vec_offs+l-1] = ' ';
	xs[vec_offs+sl-1] = 0;
      }
      x->length = sl;
      /* convert string to vector */
      pic_read_bv(x,xs);
      x->dist = 0.0;
      x->hdist = 0;
      if (is_in_set(x,V)) fprintf(stderr,"\nWARNING: Identifier conflict: %s!",x->strain);
      V = add_element_in_order(V,x);
    }
  }
  free(xs);
  
  return V;
}

ST *dp_read_set_po (FILE *f, char *hdrfile) {
  ST *V = NULL;
  BV *x;
  char *xs;
  int l,sl,n,i;
  
  read_header(hdrfile);
  
  if ( (xs = (char *) malloc(sizeof(char)*MAX_LENGTH)) == NULL ) out_of_mem();
  sl = vec_len;
  n = 0;
  while (!feof(f)) {
    read_line(f,xs,MAX_LENGTH);
    if (!feof(f)) {
      n++;
      /* allocate space for new vector */
      x = bv_allocate();
      x->num = n;
      /* file empty spaces */
      l = strlen(&xs[vec_offs])+1;
      if (l < sl) {
	for (i=l;i<sl;i++) xs[vec_offs+l-1] = ' ';
	xs[vec_offs+sl-1] = 0;
      }
      x->length = sl;
      /* convert string to vector */
      pic_read_bv(x,xs);
      x->dist = 0.0;
      x->hdist = 0;
      if (is_in_set(x,V)) fprintf(stderr,"\nWARNING: Identifier conflict: %s!",x->strain);
      V = add_element_tail(V,x);
    }
  }
  free(xs);
  
  return V;
}

int part_size (Partition *P) {
  int nofb,i,k;
  ST *W;

  nofb = 0;
  k = P->k;
  for (i=1;i<k;i++) {
    W = P->el[i];
    nofb += size(W);
  }
  return nofb;
}

void do_cumulative_classification (char *datfile, char *basfile, char *outfile, char *parfile, char *hdrfile) {
  const char *func = "do_cumulative_classfication";
  FILE *o = NULL;
  FILE *f;
  ST *V;
  Partition *P;
  Vector *PF = NULL;
  Vector *SC = NULL;
  DynPartition *DP = NULL;
  int nofv,k;
  int nofb = 0;
  int log_actions = log_file;
  double pf;
  time_t tm;
  
  tm = time(&tm);
  set_rand(tm);
  
  if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  
  if (log_file) fprintf(o,"ANALYSIS:\n\n");
  if (log_file) fprintf(o,"FILES:\n Input file:                      %s\n",datfile);
  if (log_file) fprintf(o," Output file:                     %s\n",outfile);
  if (log_file) fprintf(o," Partition file:                  %s\n",parfile);
  if (verbose) {
    fprintf(stdout,"Delta: %d\n",real_delta_value);
    if (!fixed_delta) fprintf(stdout,"Decreasing Delta value\n");
    if (cumulative_in_order) fprintf(stdout,"ID ordered input\n");
    else if (cumulative_input_order) fprintf(stdout,"Applying in input order\n");
    else fprintf(stdout,"Randomized input order\n");
  }
  if (log_file) {
    fprintf(o,"DELTA: %d\n",real_delta_value);
    if (!fixed_delta) fprintf(o,"Decreasing Delta value\n");
    if (cumulative_in_order) fprintf(o,"ID ordered input\n");
    else if (cumulative_input_order) fprintf(o,"Applying in input order\n");
    else fprintf(o,"Randomized input order\n");
  }
  
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..");
  if (cumulative_in_order) {
    V = dp_read_set_order(f,hdrfile);
  } else if (cumulative_input_order) {
    V = dp_read_set_po(f,hdrfile);
  } else {
    V = dp_read_set(f,hdrfile);
  }
  fclose(f);
  nofv = size(V);
  if (log_file) fprintf(o,"Size: %d\n",nofv);
  if (verbose) fprintf(stdout,".. read %d vectors of data\n",nofv);
  
  if ((f = fopen(basfile,"r")) != NULL) {
    if (verbose) fprintf(stdout,"Using base file\n");
    P = read_partition(f,FALSE);
    nofb = part_size(P);
    DP = dp_trevnoc(P);
    if (log_file) fprintf(o,"Using base file of %d classes\n",((P->k)-1));
    fclose(f);
  }
  
  log2_factorials = prepare_log2_factorials((nofv+nofb)*2);

  if (log_file) fclose(o);
  
  if (verbose) fprintf(stdout,"Running: ");
  DP = dp_issue(DP,V,PF,SC,outfile,log_actions);
  pf = dp_predictive_fit(DP);
  P = dp_convert(DP);
  k = (P->k)-1;
  if (verbose) {
    fprintf(stdout,"Found %d classes\n",k);
    fprintf(stdout,"Saving partition ..");
  }
 
  f = fopen(parfile,"w");
  inf_write_partition_po_delta(f,P,delta_value(k));
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    fprintf(o,"\nPF = %4f\n",pf);
    fclose(o);
  }
  if (verbose) fprintf(stdout,"\nPF = %.4f\n",pf);

}

double dp_predictive_fit (DynPartition *DP) {
  DynPartition *tmp;
  int i,l,s,t;
  IntVector *nij;
  double pf,L;

  l = vec_len;
  tmp = DP;
  t = 0;
  /* formula 4.4 */
  L = 0.0;
  while (tmp != NULL) {
    s = tmp->size;
    t += s;
    nij = tmp->nij;
    for (i=1;i<l;i++)	{
      L += (dp_wij(s,nij->el[i]) * (double)nij->el[i]);
    }
    L += (s * dp_bj(tmp,s,l));
    tmp = tmp->next;
  }
  L = -L;

  pf = L;
  return (pf / (double) t);
}


double dp_predictive_fit2 (DynPartition *DP, int delta) {
  DynPartition *tmp;
  int i,l,s,t,k;
  IntVector *nij;
  double pf,L,P;
  
  l = vec_len;
  tmp = DP;
  t = 0;
  /* formula 4.4 */
  L = 0.0;
  k = 0;
  while (tmp != NULL) {
    k++;
    s = tmp->size;
    t += s;
    nij = tmp->nij;
    for (i=1;i<l;i++)	{
      L += (dp_wij(s,nij->el[i]) * (double)nij->el[i]);
    }
    L += (s * dp_bj(tmp,s,l));
    tmp = tmp->next;
  }
  L = -L;

  /* formula 6.2 */
  P = 0.0;
  tmp = DP;
  while (tmp != NULL) {
    s = tmp->size;
    P += log_2((double)(s + 1));
    tmp = tmp->next;
  }
  P = -P;
  P -= log_2((double)delta) - (double)(vec_len - 1) + ((double)(k+1) * log_2((double)t + (double)k + (double)delta));
  
  pf = L + P;

  return (pf / (double) t);
}


double predictive_fit (Partition *P) {
  ST *V;
  BV *x;
  int k,i,j,l,s,f,t;
  double a,bj,pf,pfL;
  IntVector *freq;
  IntVector *nij;
  IntVector *hmo;

  l = vec_len;
  k = P->k;

  t = 0;
  pfL = 0.0;
  for (i=1;i<k;i++) {
    freq = allocate_ivector(l);
    nij = allocate_ivector(l);
    hmo = allocate_ivector(l);
    /* update frequency vector */
    s = 0;
    V = P->el[i];
    while (elements_left(V)) {
      x = get_element(V);
      for (j=1;j<l;j++) freq->el[j] += x->el[j];
      s++;
      V = next_element(V);
    }
    /* calculate HMO */
    for (j=1;j<l;j++) {
      f = freq->el[j];
      a = (double)((double)f / (double)s);
      hmo->el[j] = (a < 0.5) ? 0 : 1;
    }
    /* count differing bits */
    V = P->el[i];
    while (elements_left(V)) {
      x = get_element(V);
      for (j=1;j<l;j++) nij->el[j] += (x->el[j] != hmo->el[j]);
      V = next_element(V);
    }
    
    t += s;
    bj = 0.0;
    for (j=1;j<l;j++)	{
      pfL += (dp_wij(s,nij->el[j]) * (double)nij->el[j]);
      bj += log_2((double)(s - nij->el[j] + 1)) - log_2((double)(s + 2));
    }
    pfL += (s * bj);
    
    deallocate_ivector(freq);
    deallocate_ivector(hmo);
    deallocate_ivector(nij);
  }
  pfL = -pfL;

  pf = pfL;
  return (pf / (double) t);
}

int nontrash_classes (Partition *P) {
  int i,k,c;

  k = P->k;
  c = 0;
  for (i=1;i<k;i++) {
    if (size(P->el[i]) > 1) c++;
  }
  return c;
}

void cumulat_dump_stat (FILE *f, Vector *X, int ca) {
  double sd,me,avg;
  int i;

  avg = 0.0;
  for (i=1;i<ca;i++) avg = avg + X->el[i];
  avg = (avg / (double) (ca-1));
  sd = 0.0;
  for (i=1;i<ca;i++) sd = sd + pow((X->el[i] - avg),2.0);
  sd = sqrt(sd / (double) (ca-1));
  me = sd / sqrt((double) (ca-1));
  fprintf(f," Average:            %1.5f\n",avg);
  fprintf(f," Standard deviation: %1.5f\n",sd);
  fprintf(f," Mean error:         %1.5f\n",me);
  fprintf(f," Variance:           %1.5f\n",pow(sd,2.0));
}

void cumulat_dump_stat2 (FILE *f, IntVector *X, int ca) {
  double sd,me,avg;
  int i;

  avg = 0.0;
  for (i=1;i<ca;i++) avg = avg + ((double)(X->el[i]));
  avg = (avg / (double) (ca-1));
  sd = 0.0;
  for (i=1;i<ca;i++) sd = sd + pow((((double)(X->el[i])) - avg),2.0);
  sd = sqrt(sd / (double) (ca-1));
  me = sd / sqrt((double) (ca-1));
  fprintf(f," Average:            %1.2f\n",avg);
  fprintf(f," Standard deviation: %1.3f\n",sd);
  fprintf(f," Mean error:         %1.3f\n",me);
  fprintf(f," Variance:           %1.3f\n",pow(sd,2.0));
}

void cumulat_correlation(FILE *f, Vector *X1, Vector *X2) {
  double a,b,mse,ccoef;

  mle_approx_2dim(X1,X2,&a,&b);
  fprintf(f,"  a                   %.4f\n",a);
  fprintf(f,"  b                   %.4f\n",b);
  mse = calculate_mse(a,b,X1,X2);
  fprintf(f,"  error               %.4f\n",mse);
  ccoef = correlation_coef(X1,X2);
  fprintf(f,"  correlation         %.4f\n",ccoef);
}

void progressive_data(FILE *o, int nofv, int nofs, int nofb, Vector *Xavg, Vector *Xmin, Vector *Xmax) {
  int step,j;

  step = nofv / cumulative_samples;
  for (j=1;j<nofs;j++) {
    Xavg->el[j] = Xavg->el[j] / (double)(cumulative_analysis - 1);
    fprintf(o,"%5d, avg = ",(j*(nofv / cumulative_samples))-(step-1)+nofb);
    if ((Xavg->el[j] < 9.99995) && (Xavg->el[j] > 0.0)) fputc(' ',o);
    fprintf(o,"%.4f [",Xavg->el[j]);
    if ((Xmax->el[j] < 9.99995) && (Xmax->el[j] > 0.0)) fputc(' ',o);
    fprintf(o,"%.4f:",Xmax->el[j]);
    if ((Xmin->el[j] < 9.99995) && (Xmin->el[j] > 0.0)) fputc(' ',o);
    fprintf(o,"%.4f]\n",Xmin->el[j]);
  }
}

void cumulat_dump_data (FILE *o, int ca, double pfmin, IntVector *K, IntVector *K2, Vector *D, Vector *PF, Vector *SC) {
  int i;

  fprintf(o,"\nRESULTS:\n--\n");
  for (i=1;i<ca;i++) {
    fprintf(o,"%4d: k = %3d (%3d) d = ",i,K->el[i],K2->el[i]);
    if (D->el[i] < 9999.95) fputc(' ',o);
    if (D->el[i] < 999.95) fputc(' ',o);
    if (D->el[i] < 99.95) fputc(' ',o);
    if (D->el[i] < 9.95) fputc(' ',o);
    fprintf(o,"%.1f, pf = ",D->el[i]);
    if (PF->el[i] < 9.99995) fputc(' ',o);
    fprintf(o,"%.4f (",PF->el[i]);
    if (fabs((PF->el[i])-pfmin) < 9.99995) fputc(' ',o);
    fprintf(o,"%.4f)",fabs((PF->el[i])-pfmin));
    fprintf(o,", sc = ");
    if (SC->el[i] < 9.99995) fputc(' ',o);
    fprintf(o,"%.4f\n",SC->el[i]);
  }
}

void cumulat_check_bounds (int nofs, Vector *Xtmp, Vector *Xmin, Vector *Xmax, Vector *Xavg) {
  int j;

  for (j=1;j<nofs;j++) {
    if (Xmin->el[j] > Xtmp->el[j]) Xmin->el[j] = Xtmp->el[j];
    if (Xmax->el[j] < Xtmp->el[j]) Xmax->el[j] = Xtmp->el[j];
    Xavg->el[j] = Xavg->el[j] + Xtmp->el[j];
  }
}

void cumulat_messages(FILE *o, char *datfile, char *outfile, char *cmpfile, char *parfile1, char *parfile2, char *ordfile1, char *ordfile2) {
  
  fprintf(o,"ANALYSIS:\n\n");
  fprintf(o,"FILES:\n Input file:                      %s\n",datfile);
  fprintf(o," Output file:                     %s\n",outfile);
  fprintf(o," Comparison file:                 %s\n",cmpfile);
  fprintf(o," Partition file (best PF):        %s\n",parfile1);
  fprintf(o," Order file (best PF):            %s\n",ordfile1);
  fprintf(o," Partition file (worst PF):       %s\n",parfile2);
  fprintf(o," Order file (worst PF):           %s\n",ordfile2);
  fprintf(o,"DELTA: %d\n",real_delta_value);
  if (!fixed_delta) fprintf(o,"Decreasing Delta value\n");
  if (cumulative_in_order) fprintf(o,"ID ordered input\n");
  else if (cumulative_input_order) fprintf(o,"Applying in input order\n");
  else fprintf(o,"Randomized input order\n");
}

DynPartition *read_base (FILE *o, char *basfile, int *nofb, ST* Vfull) {
  FILE *f;
  Partition *P;
  ST *W;
  BV *x;
  int i,k;
  DynPartition *DPbase = NULL;

  *nofb = 0;
  if ((f = fopen(basfile,"r")) != NULL) {
    if (verbose) fprintf(stdout,"Using base file:\n");
    P = read_partition(f,FALSE);
    k = P->k;
    for (i=1;i<k;i++) {
      W = P->el[i];
      *nofb += size(W);
      while (elements_left(W)) {
	x = bv_copy(W->el);
	if (!is_in_set(x,Vfull)) Vfull = add_element_in_alpha(Vfull,x);
	else fprintf(stderr,"WARNING: Identifier conflict: %s!",x->strain);
	W = next_element(W);
      }
    }
    if (log_file) fprintf(o,"Using base file of %d classes\n",((P->k)-1));
    DPbase = dp_trevnoc(P);
    fclose(f);
  }
  return DPbase;
}

void analyse_cumulative (char *datfile, char *basfile, char *cmpfile, char *outfile, char *parfile1, char *parfile2, char *ordfile1, char *ordfile2, char *hdrfile) {
  const char *func = "analyse_cumulative";
  int i,j,nofv,nofs,nofb,no_compare;
  double d,pf,sc,dmin,pfmin,pfmax,scmin,scmax;
  int log_actions = FALSE;
  DynPartition *DPbase = NULL;
  DynPartition *DP = NULL;
  ST *V;
  ST *W;
  ST *Vfull;
  ST *O = NULL;
  Partition *P;
  Partition *Pcmp;
  Partition *Pmin = NULL;
  Partition *Pmax = NULL;
  IntVector *K;
  IntVector *K2;
  Vector *PF;
  Vector *PFtmp;
  Vector *PFavg;
  Vector *PFmin;
  Vector *PFmax;
  Vector *SC;
  Vector *SCtmp;
  Vector *SCavg;
  Vector *SCmin;
  Vector *SCmax;
  Vector *D;
  time_t tm;
  FILE *o = NULL;
  FILE *f;
  
  tm = time(&tm);
  set_rand(tm);
  
  exact_matches = TRUE;
  
  if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);

  if (log_file) cumulat_messages(o,datfile,outfile,cmpfile,parfile1,ordfile1,parfile2,ordfile2);
  if (verbose) cumulat_messages(stdout,datfile,outfile,cmpfile,parfile1,ordfile1,parfile2,ordfile2);
  
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..");
  V = dp_read_set(f,hdrfile);
  fclose(f);
  
  Vfull = copy_set_in_alpha(V);
  
  nofv = size(V);
  if (log_file) fprintf(o,"Set tize:    %d\n",nofv);
  if (verbose) fprintf(stdout,".. read %d vectors of data\n",nofv);

  DPbase = read_base(o,basfile,&nofb,Vfull);
  
  log2_factorials = prepare_log2_factorials((nofv+nofb)*2);

  nofs = (nofv / cumulative_samples);
  if (nofs < 1) nofs = 1;
  nofs = (nofv / nofs);
  
  if (log_file) fprintf(o,"Sample size: %d\n",nofs);
  nofs++;
  
  PFtmp = allocate_dvector(nofs);
  PFavg = allocate_dvector(nofs);
  PFmin = allocate_dvector(nofs);
  PFmax = allocate_dvector(nofs);
  SCtmp = allocate_dvector(nofs);
  SCavg = allocate_dvector(nofs);
  SCmin = allocate_dvector(nofs);
  SCmax = allocate_dvector(nofs);
  
  for (j=1;j<nofs;j++) {
    PFmin->el[j] = (((double) vec_len) * 2.0);
    SCmin->el[j] = (((double) vec_len) * 2.0);
  }
  
  if (log_file) fclose(o);
  
  if (verbose) fprintf(stdout,"Reading comparison partition\n");
  if ((f = fopen(cmpfile,"r")) == NULL) no_compare = TRUE; /* file_error(cmpfile,(char *)func); */
  else {
    no_compare = FALSE; 
    Pcmp = read_partition(f,FALSE);
  }
  fclose(f);
  
  cumulative_analysis++;
  SC = allocate_dvector(cumulative_analysis-1);
  PF = allocate_dvector(cumulative_analysis-1);
  D = allocate_dvector(cumulative_analysis-1);
  K = allocate_ivector(cumulative_analysis-1);
  K2 = allocate_ivector(cumulative_analysis-1);
  
  pfmin = (double) (vec_len * 2);
  scmin = (double) (vec_len * 2);
  scmax = 0.0;
  dmin = (double) size(Vfull);
  pfmax = 0.0;

  for (i=1;i<cumulative_analysis;i++) {
    if (DPbase != NULL) DP = dp_copy(DPbase);
    W = copy_set(V);
    deallocate_set(O);
    O = copy_set(V);
    if (verbose) fprintf(stdout,"Running: ");
    DP = dp_issue(DP,W,PFtmp,SCtmp,outfile,log_actions);
    cumulat_check_bounds(nofs,PFtmp,PFmin,PFmax,PFavg);
    cumulat_check_bounds(nofs,SCtmp,SCmin,SCmax,SCavg);
    pf = dp_predictive_fit(DP);
    P = dp_convert(DP);
    sc = stochastic_complexity(P,(P->k),vec_len);
    SC->el[i] = sc;
    PF->el[i] = pf;
    if (pf < pfmin) {
      if (cum_save_by_pf) {
	deallocate_partition(Pmin);
	Pmin = copy_partition(P);
	if ((f = fopen(ordfile1,"w")) == NULL) file_error(ordfile1,(char *)func);
	write_set(f,O);
	fclose(f);
      }
      pfmin = pf;
    }
    if (sc < scmin) {
      if (!cum_save_by_pf) {
	deallocate_partition(Pmin);
	Pmin = copy_partition(P);
	if ((f = fopen(ordfile1,"w")) == NULL) file_error(ordfile1,(char *)func);
	write_set(f,O);
	fclose(f);
      }
      scmin = sc;
    }
    K2->el[i] = nontrash_classes(P);
    K->el[i] = (P->k)-1;
    if (no_compare) d = 100.0;
    else d = calculate_distance(Vfull,P,Pcmp);
    D->el[i] = d;
    if (pf > pfmax) {
      if (cum_save_by_pf) {
	deallocate_partition(Pmax);
	Pmax = copy_partition(P);
	if ((f = fopen(ordfile2,"w")) == NULL) file_error(ordfile2,(char *)func);
	write_set(f,O);
	fclose(f);
      }
      pfmax = pf;
    }
    if (sc > scmax) {
      if (!cum_save_by_pf) {
	deallocate_partition(Pmax);
	Pmax = copy_partition(P);
	if ((f = fopen(ordfile2,"w")) == NULL) file_error(ordfile2,(char *)func);
	write_set(f,O);
	fclose(f);
      }
      scmax = sc;
    }
    if (d < dmin) dmin = d;
    deallocate_partition(P);
    V = dp_redraw(V);
    DP = NULL;
    if (verbose) fprintf(stdout,"%4d: k = %3d, d = %.1f, pf = %.4f, sc = %.4f\n",i,K->el[i],D->el[i],PF->el[i],SC->el[i]);
  }
  
  if (verbose) fprintf(stdout,"\nCaluclating distance between best and worst:\n");
  d = 100.0; /* calculate_distance(Vfull,Pmin,Pmax); */
  
  if (verbose) fprintf(stdout,"\n\nPFmin = %.4f, Dmin = %.1f\n",pfmin,dmin);
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    fprintf(o,"\n\nPFmin = %.4f, PFmax = %.4f, SCmin = %.4f, SCmax = %.4f, Dmin = %.1f\n",pfmin,pfmax,scmin,scmax,dmin);
    fprintf(o,"d(Pmax,Pmin) = %.1f\n",d);
    fclose(o);
  }
  
  if (verbose) fprintf(stdout,"\nSaving best and worst partitions ..");
  
  if ((f = fopen(parfile1,"w")) == NULL) file_error(parfile1,(char *)func);
  inf_write_partition_po_delta(f,Pmin,delta_value((Pmin->k)-1));
  fclose(f);
  
  if ((f = fopen(parfile2,"w")) == NULL) file_error(parfile2,(char *)func);
  inf_write_partition_po_delta(f,Pmax,delta_value((Pmax->k)-1));
  fclose(f);
  
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);

    /* dump data */
    cumulat_dump_data(o,cumulative_analysis,pfmin,K,K2,D,PF,SC);

    /* statistical data */
    /* SCpf */
    fprintf(f,"\nSCpf\n");
    cumulat_dump_stat(o,PF,cumulative_analysis);
    /* SCmb */
    fprintf(f,"SCmb\n");
    cumulat_dump_stat(o,SC,cumulative_analysis);
    /* D */
    fprintf(f,"D\n");
    cumulat_dump_stat(o,D,cumulative_analysis);
    /* K */
    fprintf(f,"K\n");
    cumulat_dump_stat2(o,K,cumulative_analysis);
    /* K2 */
    fprintf(f,"K2\n");
    cumulat_dump_stat2(o,K2,cumulative_analysis);

    /* correlations */
    /* PF - D */
    fprintf(o,"\n(PF,D)\n  MLE line (y = a + bx)\n");
    cumulat_correlation(f,PF,D);
    /* PF - SC */
    fprintf(o,"\n(PF,SC)\n  MLE line (y = a + bx)\n");
    cumulat_correlation(f,PF,SC);

    /* progressive data */
    fprintf(o,"\nRESULTS: (SCpf development)\n--\n");
    progressive_data(o,nofv,nofs,nofb,PFavg,PFmin,PFmax);
    fprintf(o,"\nRESULTS: (SCmb development)\n--\n");
    progressive_data(o,nofv,nofs,nofb,SCavg,SCmin,SCmax);

    fclose(o);
  }
  
  if (verbose) fprintf(stdout,"..ok\n");
}


double kappa (int d) {
  return ((double)real_delta_value) / pow(2.0,(double)(d-1));
}

void split_data_by_feature (FILE *o, int i, int t, ST *V, Partition *P) {
  BV *x;

  P->el[1] = NULL;
  P->el[2] = NULL;
  while (elements_left(V)) {
    x = get_element(V);
    if (x->el[i] == 0) P->el[1] = add_element(P->el[1],x);
    else P->el[2] = add_element(P->el[2],x);
    V = next_element(V);
  }
  if (o != NULL) fprintf(o,"\nSplitted by feature %3d:\n  0: %5d\n  1: %5d\n",i,size(P->el[1]),size(P->el[2]));
  put_dot;
}

double class_predictor (int d, int t, int tj) {
  double m = 2.0;
  double k;
  double h;

  k = kappa(d);
  d--;
  h = pow(2.0,(double)d);
#ifdef _LOG_MODEL
  return log_2(((double)tj) + (k * h)) - log_2(((double)t) + (k * m * h));
#else
  return (((double)tj) + (k * h)) / (((double)t) + (k * m * h));
#endif
}

double identification_rule (int d, int tj, IntVector *nj, BV *x) {
  double k;
  double h,h1,pj;
  int i;
  int nij;

  k = kappa(d);
  d--;
  h = pow(2.0,(double)d) * k;
  h1 = pow(2.0,((double)d-1)) * k;
#ifdef _LOG_MODEL
  pj = 0.0;
  for (i=1;i<d;i++) {
    nij = nj->el[i];
    if (x->el[i] == 1) pj += log_2(((double)nij) + h1) - log_2(((double)tj) + h);
    else pj += log_2(1.0 - ((((double)nij) + h1) / (((double)tj) + h)));
  }
#else
  pj = 1.0;
  for (i=1;i<d;i++) {
    nij = nj->el[i];
    if (x->el[i] == 1) pj *= (((double)nij) + h1) / (((double)tj) + h);
    else pj *= 1.0 - ((((double)nij) + h1) / (((double)tj) + h));
  }
#endif
  return pj;
}

IntVector *count_ones (int d, ST *V) {
  IntVector *nj;
  BV *x;
  int i;

  nj = allocate_ivector(d);
  while (elements_left(V)) {
    x = get_element(V);
    for (i;i<d;i++) if (x->el[i] == 1) nj->el[i]++;
    V = next_element(V);
  }
  return nj;
}

void reidentify_data (FILE *o, int d, int t, Partition *P) {
  ST *V;
  BV *x;
  int t1,t2,mm1,mm2;
  IntVector *n1;
  IntVector *n2;
  double lamb_t1,lamb_t2;
  double p1,p2;

  t1 = size(P->el[1]);
  t2 = size(P->el[2]);

  lamb_t1 = class_predictor(d,t,t1);
  lamb_t2 = class_predictor(d,t,t2);
  n1 = count_ones(d,P->el[1]);
  n2 = count_ones(d,P->el[2]);

  mm1 = 0;
  V = P->el[1];
  while (elements_left(V)) {
    x = get_element(V);
#ifdef _LOG_MODEL
    p1 = -(lamb_t1 + identification_rule(d,t1,n1,x));
    p2 = -(lamb_t2 + identification_rule(d,t2,n2,x));
#else
    p1 = lamb_t1 * identification_rule(d,t1,n1,x);
    p2 = lamb_t2 * identification_rule(d,t2,n2,x);
#endif
    if (p1 < p2) {
      mm1++;
      /*
      if (log_file) {
	fprintf(o,"%e < %e\n  ",p1,p2);
	pic_write_bv(o,x);
      }
      */
    } else {
      /* if (log_file) fprintf(o,"%e > %e\n",p1,p2); */
    }
    V = next_element(V);
  }
  put_dot;

  mm2 = 0;
  V = P->el[2];
  while (elements_left(V)) {
    x = get_element(V);
#ifdef _LOG_MODEL
    p1 = -(lamb_t1 + identification_rule(d,t1,n1,x));
    p2 = -(lamb_t2 + identification_rule(d,t2,n2,x));
#else
    p1 = lamb_t1 * identification_rule(d,t1,n1,x);
    p2 = lamb_t2 * identification_rule(d,t2,n2,x);
#endif
    if (p1 > p2) {
      mm2++;
      /*
      if (log_file) {
	fprintf(o,"%e > %e\n  ",p1,p2);
	pic_write_bv(o,x);
      }
      */
    } else {
      /* if (log_file) fprintf(o,"%e < %e\n",p1,p2);*/
    }
    V = next_element(V);
  }
  if (o != NULL) {
    fprintf(o,"Misidentified\n  0: %5d (%.2f)\n",mm1,(double)100.0*((double)(mm1)/(double)t1));
    fprintf(o,"  1: %5d (%.2f)\n  =  %5d (%.2f)\n",mm2,(double)100.0*((double)(mm2)/(double)t2),mm1+mm2,(double)100.0*((double)(mm1+mm2)/(double)t));
    fflush(o);
  }
  put_dot;

}

void reidentification_analysis  (char *datfile, char *outfile, char *hdrfile) {
  const char *func = "reidenitifaction_analysis";
  Partition *P;
  ST *V;
  int i,t,d;
  FILE *o = NULL;
  FILE *f;

  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..");
  V = read_set(f,hdrfile);
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");

  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    fprintf(o,"kappa = %e\n",kappa(vec_len));
  }
  P = allocate_partition(2);
  t = size(V);
  d = vec_len;
  for (i=1;i<d;i++) {
    if (verbose) fprintf(stdout,"Feature %d: ",i);
    split_data_by_feature(o,i,t,V,P);
    reidentify_data(o,d,t,P);
    if (verbose) fprintf(stdout," ok\n");
  }
  if (log_file) fclose(o);

}

/* end of cumulat.c */
