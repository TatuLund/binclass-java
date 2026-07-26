
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "bottom.h"
#include "binstuff.h"
#include "binset.h"
#include "vars.h"
#include "vectors.h"
#include "centroid.h"

/* prototypes */

/* Frequency functions */

int freq (ST *V, IntVector *fr, int l);
/* calculate number of onebits per column in given class V */
/* used by stochastic_complexity_j and stochastic_complexity_u */
void inf_average (ST *V, Centroid *x, int rounded, int s);
void inf_average_12 (ST *V, Centroid *x, int s);
/* recalculate centroid x (including class weight) from the class V, */ 
/* if value of parameter rounded is true round the centroid */

/* Nearest neighor processes */

/* codelength */

double code_length (BV *x, Centroid *y);
/* logarithmic distance (Shannon codelength) between vector x and centroid y */
double code_length2 (BV *x, Centroid *y);
/* logarithmic distance (Shannon codelength) between vector x and centroid y with class weight */
double class_code_length (Partition *P, InfCentroid *C, int clas, int s);
/* average code_length or code_length2 of class clas */
/* depending on variable use_class_weights */
double average_codelength (Partition *P, InfCentroid *C, int precalc);
/* average code_length2 of whole classification */
/* used as GLA iteration convergence criteria */
void inf_nearest_neighbour (ST *V, Partition *P, InfCentroid *C, int weights);
/* assign vectors in set V to their nearest centroid and move to partition P */
/* with code_length or code_length2 */
/* depending on variable use_class_weights */

/* hamming distance */
int hamming_distance (BV *x, Centroid *y);
/* calculate Hamming distance between vector x and centroid y */
double class_distortion (ST *W, Centroid *C);
double overall_distortion (Partition *P, InfCentroid *C);
/* used as GLA iteration convergence criteria */
void fast_nearest_neighbour (ST *V, Partition *P, InfCentroid *C);
/* assign vectors in set V to their nearest centroid and move to partition P */
/* with hamming_distance */

/* L1 norm */
double L1_distance (BV *x, Centroid *y);
/* calculate euclidean distance (L1) between vector x and centroid y */
double class_MAE (Partition *P, InfCentroid *C, int clas);
double overall_MAE (Partition *P, InfCentroid *C);
/* used as GLA iteration convergence criteria */
void MAE_nearest_neighbour (ST *V, Partition *P, InfCentroid *C);
/* assign vectors in set V to their nearest centroid and move to partition P */
/* with L1_distance */

/* L2 norm */
double L2_distance (BV *x, Centroid *y);
/* calculate squared distance (L2) between vector x and centroid y */
double class_MSE (Partition *P, InfCentroid *C, int clas);
double overall_MSE (Partition *P, InfCentroid *C);
/* used as GLA iteration convergence criteria */
void MSE_nearest_neighbour (ST *V, Partition *P, InfCentroid *C);
/* assign vectors in set V to their nearest centroid and move to partition P */
/* with L2_distance */

/* other */

double shannon_entropy (Partition *P, InfCentroid *C, int precalc);
/* calculate shannon entropy of the classification */
double stochastic_complexity (Partition *P, int k, int l);
/* inteface for either stochastic_complexity_j or stochastic_complexity_u */
/* depending on variable use_jeffreys_prior */
/* these functions use logarithm tables build by prepare_log2_factorials in binset.c */
double stochastic_complexity_j (Partition *P, int k, int d);
/* variant of stochastic complexity with Jeffreys prior */
double stochastic_complexity_u (Partition *P, int k, int l);
/* variant of stochastic complexity with uniform prior */

void local_repartition_mse (int c, Partition *P, InfCentroid *C);
void local_repartition (int c, Partition *P, InfCentroid *C);

/* 
notes: 
- define _MY_DEBUG for NULL pointer checks in time critical functions
*/

/* implementation */


int freq (ST *V, IntVector *fr, int l) {
  /* count number of one bits columnwise */
#ifdef _MY_DEBUG
  const char *func = "freq";
#endif
  int *el;
  int *x;
  int i,n;
  
#ifdef _MY_DEBUG
  if (V == NULL) internal_error((char *)func);
  if (fr == NULL) internal_error((char *)func);
#endif
  
  el = fr->el;
  for (i=1;i<l;i++) el[i] = 0;
  n = 0;
  while (elements_left(V)) {
    x = get_vector(V);
    for (i=1;i<l;i++) el[i] += x[i];
    V = next_element(V);
    n++;
  }
  fr->l = l;
  return n;
}


void inf_average (ST *V, Centroid *x, int rounded, int s) {
  /* Take the average of V and round it */
  /* ie. Generate new centroids for next round of the GLA */
#ifdef _MY_DEBUG
  const char *func = "inf_average";
#endif
  IntVector *U;
  int *el;
  int *w;
  int n,l,i;
   
#ifdef _MY_DEBUG
  if (V == NULL) internal_error((char *)func); 
  if (x == NULL) internal_error((char *)func);
#endif
  
  l = V->el->length;
  U = allocate_ivector(l);
  el = U->el;
  n = 0; /* amount of vectors sofar */
  while (elements_left(V)) {
#ifdef _MY_DEBUG
    if (V->el == NULL) internal_error((char *)func);
#endif
    w = get_vector(V);
    for (i=1;i<l;i++) {
      el[i] += w[i];
    }
    V = next_element(V);
    n++;
  }
  x->l = l;
  for (i=1;i<l;i++) {
    x->el[i] = (double) ((double) el[i] / (double) n);
  }

  x->weight = (double) n / (double) s;
  if (x->weight < EPS) x->weight = EPS;

  if (rounded) for (i=1;i<l;i++) x->el[i] = (x->el[i] < 0.5) ? 0.0 : 1.0;

  deallocate_ivector(U);
}

void inf_average_12 (ST *V, Centroid *x, int s) {
  /* Take the average of V and round it */
  /* ie. Generate new centroids for next round of the GLA */
#ifdef _MY_DEBUG
  const char *func = "inf_average_12";
#endif
  IntVector *U;
  int *el;
  int *w;
  int n,l,i;
   
#ifdef _MY_DEBUG
  if (V == NULL) internal_error((char *)func); 
  if (x == NULL) internal_error((char *)func);
#endif
  
  l = V->el->length;
  U = allocate_ivector(l);
  el = U->el;
  n = 0; /* amount of vectors sofar */
  while (elements_left(V)) {
#ifdef _MY_DEBUG
    if (V->el == NULL) internal_error((char *)func);
#endif
    w = get_vector(V);
    for (i=1;i<l;i++) {
      el[i] += w[i];
    }
    V = next_element(V);
    n++;
  }
  x->l = l;
  for (i=1;i<l;i++) {
    x->el[i] = (double) ((double)(el[i]+1) / (double)(n+2));
  }

  deallocate_ivector(U);
}

/* ------------ */
/* GLA: Codelength minimizer */

double code_length (BV *x, Centroid *y) {
  /* Calculates optimal Shannon codelength */
#ifdef _MY_DEBUG
  const char *es1 = "Unequal vector lengths";
  const char *func = "code_length";
#endif
  double h;
  int i,l;
  int *elx;
  double *el1;
  double *el0;
  
#ifdef _MY_DEBUG
  if (x == NULL) internal_error((char *)func);
  if (y == NULL) internal_error((char *)func);
#endif
  
  l = (x->length);
#ifdef _MY_DEBUG
  if (l != (y->l)) stop_error((char *)es1,(char *)func);
#endif
  h = 0.0;
  elx = x->el;
  el0 = y->log0;
  el1 = y->log1;
  for (i=1;i<l;i++) h -= (elx[i]) ? el0[i] : el1[i];
  return h;
}

double code_length2 (BV *x, Centroid *y) {
  /* Calculates optimal Shannon codelength */
#ifdef _MY_DEBUG
  const char *es1 = "Unequal vector lengths";
  const char *func = "code_length";
#endif
  double h;
  int i,l;
  int *elx;
  double *el1;
  double *el0;
  
#ifdef _MY_DEBUG
  if (x == NULL) internal_error((char *)func);
  if (y == NULL) internal_error((char *)func);
#endif
  
  l = (x->length);
#ifdef _MY_DEBUG
  if (l != (y->l)) stop_error((char *)es1,(char *)func);
#endif
  h = 0.0;
  elx = x->el;
  el0 = y->log0;
  el1 = y->log1;
  for (i=1;i<l;i++) h -= (elx[i]) ? el0[i] : el1[i];
  return h - log_2(y->weight);
}


double average_codelength (Partition *P, InfCentroid *C, int precalc) {
  /* calculate overall distortion */
  const char *func = "average_code_length";
  int i,n,k;
  ST *W;
  double h;
  Centroid *c;

  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);

  n = 0;
  k = C->k;
  h = 0.0;
  if (rounded_centroids) for (i=1;i<k;i++) inf_average(P->el[i],C->el[i],FALSE,n);

  for (i=1;i<k;i++) {
    W = P->el[i];
    c = C->el[i];
    while (elements_left(W)) {
      h += (precalc) ? W->el->dist - log_2(c->weight) : code_length2(get_element(W),c);
      n++;
      W = next_element(W);
    }
  }

  if (n < 1) division_error((char *)func);
  return (h / (double) n);
}

double class_code_length (Partition *P, InfCentroid *C, int clas, int s) {
  /* calculate overall distortion */
  const char *func = "class_code_length";
  int i,n;
  ST *W;
  double h,h1;

  n = 0;
  h = 0;
  i = clas;
  W = P->el[i];
  if (rounded_centroids) inf_average(P->el[i],C->el[i],FALSE,s);
  while (elements_left(W)) {
    h1 = (use_class_weights) ? code_length2(get_element(W),C->el[i]) : code_length(get_element(W),C->el[i]);
    W->el->dist = h1;
    h += h1;
    W = next_element(W);
    n++;
  }
  if (n < 1) division_error((char *)func);
  return (h / (double) n);
}

void inf_nearest_neighbour (ST *V, Partition *P, InfCentroid *C, int weights) {
  /* Make a partition */
#ifdef _MY_DEBUG
  const char *es1 = "No centroids";
  const char *es2 = "Empty set";
  const char *func = "inf_nearest_neighbour";
#endif
  int k,i,closest,si;
  BV *x;
  double mindist,dist;
  
#ifdef _MY_DEBUG
  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
#endif
  
  k = C->k;
#ifdef _MY_DEBUG
  if (k == 0) stop_error((char *)es1,(char *)func);
  if (V == NULL) stop_error((char *)es2,(char *)func);
#endif
  
  si = (trashcan) ? 1 : 2;
  P->k = k;
  while (elements_left(V)) {
    x = get_element(V);
    closest = si-1;
    mindist = (weights) ? code_length2(x,C->el[si-1]) : code_length(x,C->el[si-1]);
    for (i=si;i<k;i++) {
      dist = (weights) ? code_length2(x,C->el[i]) : code_length(x,C->el[i]);
      if (dist < mindist) {
	closest = i;
	mindist = dist;
      }
    }
    x->dist = code_length(x,C->el[closest]);;
    x->hdist = hamming_distance(x,C->el[closest]);
    P->el[closest] = add_element(P->el[closest],x);
    V = del_element(V);
  }
}


/* ------------ */
/* GLA: Hamming distance minimizer */


int hamming_distance (BV *x, Centroid *y) {
  /* Calculates Hamming distance */
#ifdef _MY_DEBUG
  const char *es1 = "Unequal vector lengths";
  const char *func = "hamming_distance";
#endif
  int h,i,l;
  int *elx;
  double *ely;
  
#ifdef _MY_DEBUG
  if (x == NULL) internal_error((char *)func);
  if (y == NULL) internal_error((char *)func);
#endif
  
  l = (x->length);
#ifdef _MY_DEBUG
  if (l != (y->l)) stop_error((char *)es1,(char *)func);
#endif
  h = 0;
  elx = x->el;
  ely = y->el;
  for (i=1;i<l;i++) h += (((ely[i] < 0.5) ? 0 : 1) != elx[i]);
  return h;
}


double class_distortion (ST *W, Centroid *C) {
  /* calculate overall distortion (with Hamming distance) */
  const char *func = "class_distortion";
  int n;
  int h,h1;
  
  n = 0;
  h = 0;
  while (elements_left(W)) {
    h1 = hamming_distance(get_element(W),C);
    W->el->hdist = h1;
    h += h1;
    n++;
    W = next_element(W);
  }
  if (n < 1) division_error((char *)func);
  return (double)((double) h / (double) n);
}

double overall_distortion (Partition *P, InfCentroid *C) {
  /* calculate overall distortion */
  const char *func = "overall_distortion";
  int n;
  Centroid *fr;
  ST *W;
  int h,h1,i,si,k;
  
  n = 0;
  h = 0;
  k = P->k;
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++) {
    W = P->el[i];
    fr = C->el[i];
    while (elements_left(W)) {
      h1 = hamming_distance(get_element(W),fr);
      W->el->hdist = h1;
      h += h1;
      n++;
      W = next_element(W);
    }
  }
  if (n < 1) division_error((char *)func);
  return (double)((double) h / (double) n);
}

void fast_nearest_neighbour (ST *V, Partition *P, InfCentroid *C) {
  /* Make a partition */
#ifdef _MY_DEBUG
  const char *es1 = "No centroids";
  const char *es2 = "Empty set";
  const char *func = "fast_nearest_neighbour";
#endif
  int k,i,closest,si;
  BV *x;
  int minhdist,hdist;
  
#ifdef _MY_DEBUG
  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
#endif
  
  k = C->k;
#ifdef _MY_DEBUG
  if (k == 0) stop_error((char *)es1,(char *)func);
  if (V == NULL) stop_error((char *)es2,(char *)func);
#endif
  
  si = (trashcan) ? 0 : 1;
  P->k = k;
  /* for (i=si;i<k;i++) P->el[i] = NULL; */
  while (elements_left(V)) {
    x = get_element(V);
    closest = si;
    minhdist = hamming_distance(x,C->el[si]);
    for (i=(si+1);i<k;i++) {
      hdist = hamming_distance(x,C->el[i]);
      if (hdist < minhdist) {
	closest = i;
	minhdist = hdist;
      }
    }
    x->hdist = minhdist;
    P->el[closest] = add_element(P->el[closest],x);
    V = del_element(V);
  }
}



/* ------------ */
/* GLA: L1-norm minimizer */

double L1_distance (BV *x, Centroid *y) {
  /* Calculates plain distance (absolute error to centroid) */
#ifdef _MY_DEBUG
  const char *es1 = "Unequal vector lengths";
  const char *func = "L1_distance";
#endif
  double h;
  int l,i;
  int *elx;
  double *ely;
  
#ifdef _MY_DEBUG
  if (x == NULL) internal_error((char *)func);
  if (y == NULL) internal_error((char *)func);
#endif
  
  l = (x->length);
#ifdef _MY_DEBUG
  if (l != (y->l)) stop_error((char *)es1,(char *)func);
#endif
  h = 0.0;
  elx = x->el;
  ely = y->el;
  for (i=1;i<l;i++) h += fabs((double)elx[i]-ely[i]);
  return h;
}

double class_MAE (Partition *P, InfCentroid *C, int clas) {
  /* calculate overall distortion */
  const char *func = "class_MAE";
  int n;
  Centroid *fr;
  ST *W;
  double h,h1;
  
  n = 0;
  h = 0.0;
  W = P->el[clas];
  fr = C->el[clas];
  while (elements_left(W)) {
    h1 = L1_distance(get_element(W),fr);
    W->el->hdist = h1;
    h += h1;
    n++;
    W = next_element(W);
  }
  if (n < 1) division_error((char *)func);
  return (double)(h / (double) n);
}

double overall_MAE (Partition *P, InfCentroid *C) {
  /* calculate overall distortion */
  const char *func = "overall_MAE";
  int n;
  Centroid *fr;
  ST *W;
  double h,h1;
  int i,si,k;
  
  n = 0;
  h = 0;
  k = P->k;
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++) {
    W = P->el[i];
    fr = C->el[i];
    while (elements_left(W)) {
      h1 = L1_distance(get_element(W),fr);
      h += h1;
      n++;
      W = next_element(W);
    }
  }
  if (n < 1) division_error((char *)func);
  return (double)(h / (double) n);
}

void MAE_nearest_neighbour (ST *V, Partition *P, InfCentroid *C) {
  /* Make a partition */
#ifdef _MY_DEBUG
  const char *es1 = "No centroids";
  const char *es2 = "Empty set";
  const char *func = "nearest_neighbour";
#endif
  int k,i,closest,si,minhdist;
  BV *x;
  double mindist,dist;
  
#ifdef _MY_DEBUG
  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
#endif
  
  k = C->k;
#ifdef _MY_DEBUG
  if (k == 0) stop_error((char *)es1,(char *)func);
  if (V == NULL) stop_error((char *)es2,(char *)func);
#endif
  
  si = (trashcan) ? 0 : 1;
  P->k = k;
  /* for (i=si;i<k;i++) P->el[i] = NULL; */
  while (elements_left(V)) {
    x = get_element(V);
    closest = si;
    mindist = L1_distance(x,C->el[si]);
    for (i=(si+1);i<k;i++) {
      dist = L1_distance(x,C->el[i]);
      if (dist < mindist) {
	closest = i;
	mindist = dist;
      }
    }
    minhdist = hamming_distance(x,C->el[closest]);
    x->hdist = minhdist;
    P->el[closest] = add_element(P->el[closest],x);
    V = del_element(V);
  }
}



/* ------------ */
/* GLA: L2-norm minimizer */

double L2_distance (BV *x, Centroid *y) {
  /* Calculates plain distance (absolute error to centroid) */
#ifdef _MY_DEBUG
  const char *es1 = "Unequal vector lengths";
  const char *func = "L2_distance";
#endif
  double h;
  int l,i;
  int *elx;
  double *ely;
  
#ifdef _MY_DEBUG
  if (x == NULL) internal_error((char *)func);
  if (y == NULL) internal_error((char *)func);
#endif
  
  l = (x->length);
#ifdef _MY_DEBUG
  if (l != (y->l)) stop_error((char *)es1,(char *)func);
#endif
  h = 0.0;
  elx = x->el;
  ely = y->el;
  for (i=1;i<l;i++) h += ((double)(elx[i])-(ely[i])) * ((double)(elx[i])-(ely[i]));
  return h;
}

double class_MSE (Partition *P, InfCentroid *C, int clas) {
  /* calculate overall distortion */
  const char *func = "class_MSE";
  int n;
  Centroid *fr;
  ST *W;
  double h,h1;
  
  n = 0;
  h = 0.0;
  W = P->el[clas];
  fr = C->el[clas];
  while (elements_left(W)) {
    h1 = L2_distance(get_element(W),fr);
    W->el->hdist = h1;
    h += h1;
    n++;
    W = next_element(W);
  }
  if (n < 1) division_error((char *)func);
  return (double)(h / (double) n);
}

double overall_MSE (Partition *P, InfCentroid *C) {
  /* calculate overall distortion */
  const char *func = "overall_MSE";
  int n;
  Centroid *fr;
  ST *W;
  double h,h1;
  int i,si,k;
  
  n = 0;
  h = 0;
  k = P->k;
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++) {
    W = P->el[i];
    fr = C->el[i];
    while (elements_left(W)) {
      h1 = L2_distance(get_element(W),fr);
      h += h1;
      n++;
      W = next_element(W);
    }
  }
  if (n < 1) division_error((char *)func);
  return (double)(h / (double) n);
}

void MSE_nearest_neighbour (ST *V, Partition *P, InfCentroid *C) {
  /* Make a partition */
#ifdef _MY_DEBUG
  const char *es1 = "No centroids";
  const char *es2 = "Empty set";
  const char *func = "MSE_nearest_neighbour";
#endif
  int k,i,closest,si,minhdist;
  BV *x;
  double mindist,dist;
  
#ifdef _MY_DEBUG
  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
#endif
  
  k = C->k;
#ifdef _MY_DEBUG
  if (k == 0) stop_error((char *)es1,(char *)func);
  if (V == NULL) stop_error((char *)es2,(char *)func);
#endif
  
  si = (trashcan) ? 0 : 1;
  P->k = k;
  /* for (i=si;i<k;i++) P->el[i] = NULL; */
  while (elements_left(V)) {
    x = get_element(V);
    closest = si;
    mindist = L2_distance(x,C->el[si]);
    for (i=(si+1);i<k;i++) {
      dist = L2_distance(x,C->el[i]);
      if (dist < mindist) {
	closest = i;
	mindist = dist;
      }
    }
    minhdist = hamming_distance(x,C->el[closest]);
    x->hdist = minhdist;
    P->el[closest] = add_element(P->el[closest],x);
    V = del_element(V);
  }
}


double shannon_entropy (Partition *P, InfCentroid *C, int precalc) {
  /* calculate overall codelength with parameters */
  const char *func = "shannon_entropy";
  int n,i,j,l,k;
  ST *W;
  double N,h,K;

  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  k = C->k;
  K = (double)(k-1);
  l = C->el[1]->l;
  n = 0;
  h = 0.0;
  for (i=1;i<k;i++) {
    /* for every class */
    W = P->el[i];
    while (elements_left(W)) {
      /* every BV in the class */
      h += code_length2(W->el,C->el[i]);
      n++;
      W = next_element(W);
    }
  }
  N = (double)n;

  /* for the labeling of the classes */
  for (i=1;i<k;i++) h -= (0.5 * log_2(C->el[i]->weight));
  h += (0.5 * K * log_2(N * 0.0833333));
  h -= log2_factorial(k-2);

  /* for the labeling of the features */
  for (i=1;i<k;i++) for (j=1;j<l;j++) h += (0.5 * log_2((C->el[i]->weight * N) * 0.0833333)) - (0.5 * (log_2(C->el[i]->el[j]) + log_2(1.0 - C->el[i]->el[j])));

  if (n < 1) division_error((char *)func);
  return (h / N);

}

double stochastic_complexity (Partition *P, int k, int l) {
  return (use_jeffreys_prior) ? stochastic_complexity_j(P,k,l) : stochastic_complexity_u(P,k,l);
}

double stochastic_complexity_j (Partition *P, int k, int d) {
/* calculate stochastic complexity using Jeffreys prior */
  const char *func = "stochastic_complexity_j";
  IntVector *tj;
  IntVector *tij;
  int t,i,j;
  double h;
  double K,D;

  if (P == NULL) internal_error((char *)func);
  if (log2_factorials == NULL) internal_error((char *)func);

  t = 0;
  tj = allocate_ivector(k);
  tij = allocate_ivector(d);
  /* calculate sizes of classes (ti) and total number of vectors (t)*/
  for (j=1;j<k;j++) {
    tj->el[j] = size((P->el[j]));
    if (tj->el[j] == 0) internal_error((char *)func);
    t += tj->el[j];
  }

  K = (double)(k-1);
  D = (double)(d-1);

  /* the part for coding the class no */
  h = ((D * K) + (K/2.0)) * LPI;
  h += log2_gamma(K/2.0);
  h += log2_gamma((double)t + (K/2.0));
  for (j=1;j<k;j++) {
    h -= log2_gamma((double)(tj->el[j]) + 0.5);
  }

  /* the part for coding the bits */
  for (j=1;j<k;j++) {
    freq(P->el[j],tij,d);
    for (i=1;i<d;i++) {
      h += log2_factorial(tj->el[j]);
      h -= log2_gamma((double)(tij->el[i])+0.5);
      h -= log2_gamma((double)(tj->el[j] - tij->el[i]) + 0.5);
    }
  }

  deallocate_ivector(tij);
  deallocate_ivector(tj);

  return (h / (double)t);
}

double stochastic_complexity_u (Partition *P, int k, int d) {
/* calculate the stochastic complexity using uniform prior */
  const char *func = "stochastic_complexity";
  IntVector *tj;
  IntVector *tij;
  int i,j,t;
  double h1,h2;
  
  /* NB: k and d are adjusted to value of plus one for for-loops */
  
  if (P == NULL) internal_error((char *)func);
  if (log2_factorials == NULL) internal_error((char *)func);
  
  t = 0;
  tj = allocate_ivector(k);
  tij = allocate_ivector(d);
  /* calculate sizes of classes (tj) and total number of vectors (t)*/
  for (j=1;j<k;j++) {
    tj->el[j] = size((P->el[j]));
    if (tj->el[j] == 0) internal_error((char *)func);
    t += tj->el[j];
  }
  
  /* the part for coding the class no */
  h1 = log2_factorial(t);
  for (j=1;j<k;j++) {
    h1 -= log2_factorial(tj->el[j]);
  }
  /* k is one too big, thus minus extra one */
  h1 += log2_factorial(t+k-2);
  h1 -= log2_factorial(t);
  /* k is one too big, thus minus extra one */
  h1 -= log2_factorial(k-2);
  
  /* the part for coding the bits */
  h2 = 0.0;
  for (j=1;j<k;j++) {
    /* calculate frequencies of bits in particular class (tij) */
    freq(P->el[j],tij,d);
    for (i=1;i<d;i++) {
      h2 += log2_factorial(tj->el[j]+1);
      h2 -= log2_factorial(tij->el[i]);
      h2 -= log2_factorial(tj->el[j] - tij->el[i]);
    }
  }
  deallocate_ivector(tij);
  deallocate_ivector(tj);

  return ((h1 + h2) / (double) t);
}

void local_repartition (int c, Partition *P, InfCentroid *C) {
  ST *V;

  /* move elements of class number c to set V */
  V = P->el[c];
  P->el[c] = NULL;
  
  /* apply set with proper classifier */
  if ((distance_type == DT_L1_CL) || (distance_type == DT_L2_CL) || (distance_type == DT_SA) || (distance_type == DT_SR) || (distance_type == DT_CL)) inf_nearest_neighbour(V,P,C,FALSE);
  else if (distance_type == DT_L1) MAE_nearest_neighbour(V,P,C);
  else if (distance_type == DT_L2) MSE_nearest_neighbour(V,P,C);
  else fast_nearest_neighbour(V,P,C);

}

void local_repartition_mse (int c, Partition *P, InfCentroid *C) {
  int i,s,k;
  ST *V;

  /* move elements of class number c to set V */
  s = 0;
  k = P->k;
  for (i=1;i<k;i++) s += size(P->el[i]);
  V = P->el[c];
  P->el[c] = NULL;

  /* apply set with proper classifier */
  /*
  if ((distance_type == DT_L1_CL) || (distance_type == DT_L2_CL) || (distance_type == DT_SA) || (distance_type == DT_SR) || (distance_type == DT_CL)) inf_nearest_neighbour(V,P,C,FALSE);
  else if (distance_type == DT_L1) MAE_nearest_neighbour(V,P,C);
  else if (distance_type == DT_L2) MSE_nearest_neighbour(V,P,C);
  else fast_nearest_neighbour(V,P,C);
  */
  MSE_nearest_neighbour(V,P,C);

  /* recalculate centroids */
  remove_empty(P,C);
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,s);
  }
}

/* End of distmin.c */
