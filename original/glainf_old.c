
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "const.h"
#include "vars.h"
#include "bottom.h"
#include "binstuff.h"
#include "binset.h"
#include "distmin.h"
#include "centroid.h"
#include "vectors.h"
#include "splitgla.h"

/* INTERFACES TO GLA */

/* interface to run trials with one of the possible 8 GLA algorithms */
/* one of the 3 possible heuristics might be used for generating initial */
/* solutions for successive trials */
/* statistical data on trials are calulated and outputed */
InfCentroid *use_gla (ST *V, Partition *P, int k, char* outfile, double lasti, int better, int filter, double minsc);
/* load centroids from the file and run one trial with one of the possible 8 GLA algorithms */
Partition *use_gla_load_centroids (ST *V, char *outfile);

/* GENERALIZED LLOYD ALGORITHM */

void special_gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);
/* this is special version of GLA used by Bootstrapper */

/* the following 8 are used by use_gla  */

int gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);
/* regular GLA with code_length or code_length2 (see: distmin.c) */
/* use inf_nearest_neighbor, intialize allways with code_length */
int gla_sr (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);
/* as gla, but with stochastic relaxation */
int gla_sa (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);
/* as gla, but with simulated annealing */
int hybrid_gla_l1 (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);
/* hybrid GLA with code_length or code_length2 (see: distmin.c) */
/* intialize with MAE_nearest_neighbor 1..10 iterations */
int hybrid_gla_l2 (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);
/* hybrid GLA with code_length or code_length2 (see: distmin.c) */
/* intialize with MSE_nearest_neighbor 1..10 iterations */
int MAE_gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);
int MSE_gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);
int fast_gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);

/* HEURISTICS */

void split_and_join (int k, int l, int n, InfCentroid *C, Partition *P);
/* form new set of centroids by joing closest pair (in sense of squared distance) */
/* of classes and by spliting the most incoherent class (in sense of distortion) */
void random_centroid_modify (int k, int l, int s, InfCentroid *C, Partition *P);
/* draw new random centroid for the most incoherent class (in sense of distortion) */
void rerandomize_centroids (int k, int l, InfCentroid *C, Partition *P);
/* draw new random centroids within the classification */
void random_swap (int k, int l, int s, InfCentroid *C, Partition *P);

/* OTHER */

void restore_partition (ST *V, Partition *P, InfCentroid *C, int weights);


void special_gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "special_gla";
  const char *es2 = "ERROR: No centroids";
  double d,nd;
  int improvement;
  int i,k;
  
  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);

  if (decreasing_epsilon) epsilon = 0.1;

  /* Phaze 1: Minimize L1 */
  MAE_nearest_neighbour(V,P,C);
  improvement = TRUE;
  remove_empty(P,C); /* empty cell problem */
  k = C->k;
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,n);
  }
  calculate_logs(C);
  d = average_codelength(P,C,FALSE);
  
  /* Phaze 2: Minimize Codelength */
  while (improvement) {
    inf_remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    calculate_logs(C);
    V = partition_to_set(P);
    inf_nearest_neighbour(V,P,C,use_class_weights);
    nd = average_codelength(P,C,TRUE);
    if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
    else improvement = FALSE; /* stopcriterium */
    put_dot;
    if (decreasing_epsilon) epsilon = epsilon / 2.0;
  }
  
  *dmin = d;
}

#ifdef DUMP_GLA_DATA
void dump_gla_data(Partition *P, InfCentroid *C) {
  int k,i,j,l,s;
  Centroid *c;

  k = P->k;
  i = 1;
  for (i;i<k;i++) {
    s = size(P->el[i]);
    fprintf(stdout,"\nclass %d:\n  t = %d\n",i,s);
    if (s > 1) {
      c = C->el[i];
      l = c->l;
      fprintf(stdout,"  lambda = %f\n",c->weight);
      for (j=1;j<l;j++) fprintf(stdout,"  theta[%d] = %f",j,c->el[j]);
    }
  }
}
#endif

int gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "gla";
  const char *es2 = "ERROR: No centroids";
  double d,nd;
  int improvement;
  int i,s,k;
  
  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);

  if (decreasing_epsilon) epsilon = 0.1;

  inf_nearest_neighbour(V,P,C,FALSE);
  inf_remove_empty(P,C); /* empty cell problem */
  k = C->k;
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,n);
  }
  calculate_logs(C);
  d = average_codelength(P,C,TRUE);
  
  improvement = TRUE;
  s = 1;
  while (improvement) {
    s++;
    inf_remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    calculate_logs(C);
    V = partition_to_set(P);
    inf_nearest_neighbour(V,P,C,use_class_weights);
    nd = average_codelength(P,C,TRUE);
    if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
    else improvement = FALSE; /* stopcriterium */
    if (decreasing_epsilon) epsilon = epsilon / 2.0;
  }
  inf_remove_empty(P,C); /* empty cell problem */

  *dmin = d;
  return s;
}

int gla_sr (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "gla_sr";
  const char *es2 = "ERROR: No centroids";
  int temp;
  double d,nd;
  int improvement,mix_this;
  int i,s,k;
  
  temp = ceil((double)MAX_TEMP / 2.0);

  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);
  
  if (decreasing_epsilon) epsilon = 0.1;

  MAE_nearest_neighbour(V,P,C);
  remove_empty(P,C); /* empty cell problem */
  k = C->k;
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,n);
  }
  calculate_logs(C);
  d = average_codelength(P,C,FALSE);

  mix_this = FALSE;
  improvement = TRUE;
  s = 1;
  while (temp > 1) {
    while (improvement) {
      s++;
      inf_remove_empty(P,C); /* empty cell problem */
      k = C->k;
      if (mix_this) {
	mix_centroids(C,temp);
	improvement = TRUE;
	d = average_codelength(P,C,TRUE);
	mix_this = FALSE;
      }	else {
	for (i=1;i<k;i++) {
	  inf_average(P->el[i],C->el[i],rounded_centroids,n);
	}
      }
      calculate_logs(C);
      V = partition_to_set(P);
      inf_nearest_neighbour(V,P,C,use_class_weights);
      nd = average_codelength(P,C,TRUE);
      if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
      else improvement = FALSE; /* stopcriterium */
      if (decreasing_epsilon) epsilon = epsilon / 2.0;
    }
    temp = floor((double)temp / 2.0);
    if (temp > 1) {
      mix_this = TRUE;
    }
  }
  inf_remove_empty(P,C); /* empty cell problem */

  *dmin = d;
  return s;
}

int gla_sa (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "gla_sa";
  const char *es2 = "ERROR: No centroids";
  int temp;
  double d,nd;
  int improvement;
  int i,s,k;
  
  temp = ceil((double)MAX_TEMP / 2.0);

  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);
  
  if (decreasing_epsilon) epsilon = 0.1;

  MAE_nearest_neighbour(V,P,C);
  remove_empty(P,C); /* empty cell problem */
  k = C->k;
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,n);
  }
  calculate_logs(C);
  d = average_codelength(P,C,FALSE);
  
  improvement = TRUE;
  s = 1;
  while (improvement) {
    s++;
    inf_remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    if (temp > 1) {
      temp = floor((double)temp / 2.0);
      mix_centroids(C,temp);
    }
    calculate_logs(C);
    V = partition_to_set(P);
    inf_nearest_neighbour(V,P,C,use_class_weights);
    nd = average_codelength(P,C,TRUE);
    if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
    else improvement = FALSE; /* stopcriterium */
    if (decreasing_epsilon) epsilon = epsilon / 2.0;
  }
  inf_remove_empty(P,C); /* empty cell problem */

  *dmin = d;
  return s;
}

int hybrid_gla_l1 (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "hybrid_gla_l1";
  const char *es2 = "ERROR: No centroids";
  double d,nd;
  int improvement;
  int i,s,k,t;

  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);

  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);

  t = 1;
  if (k > 41) t = 11;
  else if (k > 21) t = 9;
  else if (k > 6) t = 6;

  /* Phaze 1: Minimize L1 */
  MAE_nearest_neighbour(V,P,C);

  s = 1;
  while (s<t) {
    s++;
    remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    V = partition_to_set(P);
    MAE_nearest_neighbour(V,P,C);
  }

  remove_empty(P,C); /* empty cell problem */
  improvement = TRUE;
  k = C->k;
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,n);
  }
  calculate_logs(C);
  d = average_codelength(P,C,FALSE);
  if (decreasing_epsilon) epsilon = 0.1;
  
  /* Phaze 2: Minimize Codelength */
  while (improvement) {
    s++;
    inf_remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    calculate_logs(C);
    V = partition_to_set(P);
    inf_nearest_neighbour(V,P,C,use_class_weights);
    nd = average_codelength(P,C,TRUE);
    if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
    else improvement = FALSE; /* stopcriterium */
    if (decreasing_epsilon) epsilon = epsilon / 2.0;
  }
  inf_remove_empty(P,C); /* empty cell problem */
  
  *dmin = d;
  return s;
}

int hybrid_gla_l2 (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "hybrid_gla_l2";
  const char *es2 = "ERROR: No centroids";
  double d,nd;
  int improvement;
  int i,s,k,t;
  
  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);


  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);

  t = 1;
  if (k > 41) t = 11;
  else if (k > 21) t = 9;
  else if (k > 6) t = 6;

  /* Phaze 1: Minimize L2 */
  MSE_nearest_neighbour(V,P,C);

  s = 1;
  while (s<t) {
    s++;
    remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    V = partition_to_set(P);
    MSE_nearest_neighbour(V,P,C);
  }

  remove_empty(P,C); /* empty cell problem */
  improvement = TRUE;
  k = C->k;
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,n);
  }
  calculate_logs(C);
  d = average_codelength(P,C,FALSE);
  if (decreasing_epsilon) epsilon = 0.1;
  
  /* Phaze 2: Minimize Codelength */
  while (improvement) {
    s++;
    inf_remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    calculate_logs(C);
    V = partition_to_set(P);
    inf_nearest_neighbour(V,P,C,use_class_weights);
    nd = average_codelength(P,C,TRUE);
    if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
    else improvement = FALSE; /* stopcriterium */
    if (decreasing_epsilon) epsilon = epsilon / 2.0;
  }
  inf_remove_empty(P,C); /* empty cell problem */
  
  *dmin = d;
  return s;
}

int MAE_gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "MAE_fast";
  const char *es2 = "ERROR: No centroids";
  double d,nd;
  int improvement;
  int i,s,k;
  
  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);
  
  MAE_nearest_neighbour(V,P,C);
  d = overall_MAE(P,C);
  
  improvement = TRUE;
  s = 1;
  while (improvement) {
    s++;
    remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    V = partition_to_set(P);
    MAE_nearest_neighbour(V,P,C);
    nd = overall_MAE(P,C);
    if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
    else improvement = FALSE; /* stopcriterium */
  }
  remove_empty(P,C); /* empty cell problem */

  *dmin = d;
  return s;
}

int MSE_gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "fast_gla2";
  const char *es2 = "ERROR: No centroids";
  double d,nd;
  int improvement;
  int i,s,k;
  
  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);
  
  MSE_nearest_neighbour(V,P,C);
  d = overall_MSE(P,C);
  
  improvement = TRUE;
  s = 1;
  while (improvement) {
    s++;
    remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    V = partition_to_set(P);
    MSE_nearest_neighbour(V,P,C);
    nd = overall_MSE(P,C);
    if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
    else improvement = FALSE; /* stopcriterium */
  }
  remove_empty(P,C); /* empty cell problem */

  *dmin = d;
  return s;
}

int fast_gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "fast_gla1";
  const char *es2 = "ERROR: No centroids";
  double d,nd;
  int improvement;
  int i,s,k;
  
  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);
  
  fast_nearest_neighbour(V,P,C);
  d = overall_distortion(P,C);
  
  improvement = TRUE;
  s = 0;
  while (improvement) {
    s++;
    remove_empty(P,C); /* empty cell problem */
    k = C->k;
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,n);
    }
    V = partition_to_set(P);
    fast_nearest_neighbour(V,P,C);
    nd = overall_distortion(P,C);
    if (fabs(nd - d) > EPS) d = nd; /* there is an improvement */
    else improvement = FALSE; /* stopcriterium */
  }
  remove_empty(P,C); /* empty cell problem */

  *dmin = d;
  return s;
}

Partition *use_gla_load_centroids (ST *V, char* outfile) {
  FILE *f;
  FILE *o;
  int l,gt,k,n;
  double dmin;
  InfCentroid *C;
  Partition *P;
  const char *func = "use_gla_load_centroids";
  const char *es = "incompatible centroids and data";
  
  if (V == NULL) internal_error((char *)func);
  n = size(V);

  if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  
  if (verbose) fprintf(stdout,"Loading centroids ..");
  fprintf(o,"Loading centroids ..");
  
  if ((f = fopen(centroidfile,"r")) == NULL) file_error(centroidfile,(char *)func);
  C = load_centroids(f);
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");
  fprintf(o,".. ok\n");
  if (C == NULL) stop_error((char *)es,(char *)func);
  k = C->k;
  l = C->el[1]->l;
  if (l != V->el->length) {
    stop_error((char *)es,(char *)func);
  }
  
  /* Allocating space for parition */
  P = allocate_partition(k);
  
  if (verbose) fprintf(stdout,"Number of classes to search: %d\n",(k-1));
  if (verbose) fprintf(stdout,"Length of vector: %d\n",(l-1));
  
  fprintf(o,"\nNumber of classes to search: %d\n",(k-1));
  fprintf(o,"Length of vector: %d\n",(l-1));
  
  if (verbose) fprintf(stdout,"\nGLA: ");
  if (distance_type == DT_L1_CL) gt = hybrid_gla_l1(V,P,C,&dmin,n);
  else if (distance_type == DT_L2_CL) gt = hybrid_gla_l2(V,P,C,&dmin,n);
  else if (distance_type == DT_CL) gt = gla(V,P,C,&dmin,n);
  else if (distance_type == DT_L1) gt = MAE_gla(V,P,C,&dmin,n);
  else if (distance_type == DT_SR) gt = gla_sa(V,P,C,&dmin,n);
  else if (distance_type == DT_SA) gt = gla_sr(V,P,C,&dmin,n);
  else if (distance_type == DT_L2) gt = MSE_gla(V,P,C,&dmin,n);
  else gt = fast_gla(V,P,C,&dmin,n);

  if (verbose) fprintf(stdout," ok.\n");
  fprintf(o,"Actual classes = %3d total gla = %3d dmin = %2.3f\n\n",((C->k)-1),gt,dmin);
  fclose(o);
  
  deallocate_centroids(C);
  
  return P;
}

void split_and_join (int k, int l, int n, InfCentroid *C, Partition *P) {
  const char *func = "split_and_join";
  double d,dmin,max,r;
  int i,j,imin;
  int imax = 1;
  int jmin = 1;
  Vector *X;
  BV *x;
  BV *y;
  
  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  /* calculate class distortions to vector X */
  X = allocate_dvector(k);
  dmin = ((double)l)+1.0;
  X->el[0] = 0.0;
  for (i=1;i<k;i++) X->el[i] = class_distortion(P->el[i],C->el[i]);
  
  /* take a random class (imin) and find closest pair to it (jmin) */
  r = give_true_random();
  imin = floor(r * (double) k);
  if (imin < 1) imin = 1;
  if (imin > (k-1)) imin = k-1;
  for (j=1;j<k;j++) {
    if (j != imin) {
      d = edistance_2(C->el[imin]->el,C->el[j]->el,l);
      if (d < dmin) {
	dmin = d;
	jmin = j;
      }
    }
  }
  /* join classes imin and jmin */
  P->el[imin] = join_class(P->el[imin],P->el[jmin]);
  P->el[jmin] = P->el[k-1];
  P->el[k-1] = NULL;
  if (imin != (k-1)) inf_average(P->el[imin],C->el[imin],rounded_centroids,n);
  else inf_average(P->el[jmin],C->el[jmin],rounded_centroids,n);
  
  /* search worst classification (exepct newly joined one) */
  max = 0.0;
  for (i=1;i<(k-1);i++) {
    if (i != imin) {
      if (X->el[i] > max) {
	max = X->el[i];
	imax = i;
      }
    }
  }
  
  /* split it by taking new centroids: random and its worst pair */
  worst_matching_vectors(P->el[imax],&x,&y);
  for (i=1;i<l;i++) {
    C->el[k-1]->el[i] = (double) x->el[i];
    C->el[imax]->el[i] = (double) y->el[i];
  }
  
  calculate_logs(C);
  deallocate_dvector(X);
}

void random_centroid_modify (int k, int l, int s, InfCentroid *C, Partition *P) {
  const char *func = "random_centroid_modify";
  double max;
  int i,ind,n,j,c;
  int imax = 1;
  Vector *X;
  Centroid *t;
  BV *x;

  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  /* calculate class distortions to vector X */
  X = allocate_dvector(k);
  X->el[0] = 0.0;
  for (i=1;i<k;i++) X->el[i] = class_distortion(P->el[i],C->el[i]);
  
  /* find worst class */
  max = 0.0;
  for (i=1;i<k;i++) {
    if (X->el[i] > max) {
      max = X->el[i];
      imax = i;
    }
  }

  c = (int)(give_true_random() * (double)(k-1));
  n = size(P->el[c]);
  if (c < 1) c = 1;
  if (c > k-1) c = k-1;
  ind = (int)(give_true_random() * (double)n);
  if (ind < 1) ind = 1;
  if (ind > n) ind = n;
  
  x = get_vector_i(P->el[c],ind);
  if (x == NULL) internal_error((char *)func);

  t = C->el[imax];
  for (j=1;j<l;j++) {
    t->el[j] = (1.0 + (double) (x->el[j])) / 3.0;
    t->log0[j] = log_2(t->el[j]);
    t->log1[j] = log_2(1.0-(t->el[j]));
  }
  /* local_repartition(imax,P,C,TRUE); */

  deallocate_dvector(X);
}


void rerandomize_centroids (int k, int l, InfCentroid *C, Partition *P) {
  const char *func = "rerandomize_centroids";
  double r;
  int n,i,j,ind;
  BV *x;
  Centroid *t;

  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  for (i=1;i<k;i++) {
    t = C->el[i];
    n = size(P->el[i]);

    r = give_true_random();
    ind = (int)(r * (double)n);
    if (ind < 1) ind = 1;
    if (ind > n) ind = n;

    x = get_vector_i(P->el[i],ind);

    if (x == NULL) internal_error((char *)func);

    for (j=1;j<l;j++) t->el[j] = (1.0 + (double) (x->el[j])) / 3.0;
  }
  calculate_logs(C);

}

void random_swap (int k, int l, int s, InfCentroid *C, Partition *P) {
  const char *func = "random_swap";
  double r;
  int c1,c2,j,ind,n,i;
  BV *x;
  Centroid *t;
  ST *V = NULL;

  /* draw a random class which to modify */
  c1 = (int)(give_true_random() * (double)(k-1));
  if (c1 < 1) c1 = 1;
  if (c1 > k-1) c1 = k-1;

  /* draw a random vector from a random class */
  c2 = (int)(give_true_random() * (double)(k-1));
  if (c2 < 1) c2 = 1;
  if (c2 > k-1) c2 = k-1;
  n = size(P->el[c2]);
  ind = (int)(give_true_random() * (double)n);
  if (ind < 1) ind = 1;
  if (ind > n) ind = n;
  
  x = get_vector_i(P->el[c2],ind);
  if (x == NULL) internal_error((char *)func);

  /* reset centroid of the modified class */
  t = C->el[c1];
  for (j=1;j<l;j++) {
    t->el[j] = (1.0 + (double) (x->el[j])) / 3.0;
    t->log0[j] = log_2(t->el[j]);
    t->log1[j] = log_2(1.0-(t->el[j]));
  }
  /* local_repartition(c1,P,C,TRUE); */
}

void restore_partition (ST *V, Partition *P, InfCentroid *C, int weights) {
  calculate_logs(C);
  if ((distance_type == DT_L1_CL) || (distance_type == DT_L2_CL) || (distance_type == DT_SA) || (distance_type == DT_SR) || (distance_type == DT_CL)) inf_nearest_neighbour(V,P,C,weights);
  else if (distance_type == DT_L1) MAE_nearest_neighbour(V,P,C);
  else if (distance_type == DT_L2) MSE_nearest_neighbour(V,P,C);
  else fast_nearest_neighbour(V,P,C);

}

double *expand_scvector(double *scs, int i, int vs) {
  double *scs_new;
  int j;
  
  vs = (2 * vs);
  if ((scs_new = (double *) malloc(sizeof(double)*vs)) == NULL) out_of_mem();
  for (j=1;j<(i+1);j++) scs_new[j] = scs[j];
  free(scs);
  return scs_new;
}

void gla_statistics (FILE *o, time_t start_tm, time_t stop_tm, int gt, int tot_iter, int k, int l, int n, double scmin, double *scs) {
  double avg,sd,gti,sd2,T;
  int i;

  T = (double) tot_iter;

  fprintf(o,"Statistical data\n Total count of GLA: %d\n",gt);
  gti = (double) gt / T;
  fprintf(o," Average GLA:        %1.2f\n",gti);
  gti = (double)(stop_tm - start_tm) / (double) gt;
  fprintf(o," Time / GLA:         %1.2f\n",gti);
  gti = gti / (double) (k-1);
  fprintf(o," Benchmark:          %1.4f\n",gti);
  gti = (double) (stop_tm - start_tm);
  if (gti < 0.01) gti = 1.0;
  gti = ((double) (k-1) * (double) l * (double) gt * (double) n) / gti;
  fprintf(o," Bitrate:            %1.0f\n",gti);
    
  avg = 0.0;
  for (i=1;i<=tot_iter;i++) avg += scs[i];
  avg /= T;
  sd = 0.0;
  sd2 = 0.0;
  for (i=1;i<=tot_iter;i++) {
    sd2 += pow((scs[i] - scmin),2.0);
    sd += pow((scs[i] - avg),2.0);
  }
  sd = sqrt(sd / T);
  sd2 = sqrt(sd2 / T);
  fprintf(o," Average SC:         %1.5f\n",avg);
  fprintf(o," Standard deviation: %1.5f (%1.5f)\n",sd,sd2);
  fprintf(o," Mean error:         %1.5f (%1.5f)\n",(sd/sqrt(T)),(sd2/sqrt(T)));
  fprintf(o," Variance:           %1.5f (%1.5f)\n",pow(sd,2.0),pow(sd2,2.0));
}


void gla_put_to_log (FILE *o, int k, int g, int i, int max_iter, int hnow, double sc, double d) {
  
  fprintf(o,"ak = %4d, gla = %3d, d = ",k,g);
  if (d < 9.999999) fputc(' ',o);
  fprintf(o,"%1.5f, sc = ",d);
  if (sc < 9.999999) fputc(' ',o);
  fprintf(o,"%1.5f",sc);
  if ((hnow) && (i < (max_iter+1))) fprintf(o," -\n");
  else if (i > max_iter) fprintf(o," |\n");
  else if ((hnow) && (i > max_iter)) fprintf(o," +\n");
  else fprintf(o,"\n");
}


#define no_heuristic_now(i,k) (((gla_heuristic != HEUR_NONE) && (i > 1) && (k < 5) && ((i % gla_heuristic_count) == 0) ) || (gla_heuristic != HEUR_SPLITJOIN))

#define do_trials(i,dmin,scmin,minsc,better) ((i<(max_iter+1)) || (better && (dmin > lasti) && (i < (safety_limit+1))) || (filter && !(dmin < 10000.0)) || ((minsc > 0.0) && (minsc < scmin) && (i < (safety_limit+1))))

#define heuristic_now(i,k) ((gla_heuristic != HEUR_NONE) && (i > 1) && (k > 4) && ((i % gla_heuristic_count) != 0))

InfCentroid *use_gla (ST *V, Partition *P, int k, char* outfile, double lasti, int better, int filter, double minsc) {
  /* GLA with random selection of initial centroids */
  const char *func = "use_gla";
  int i,g,l,gt,tot_iter,vs,n,hnow;
  double dmin,d,sc,scmin;
  double *scs;
  InfCentroid *cmin;
  InfCentroid *C;
  FILE *o = NULL;
  time_t stop_tm;
  time_t start_tm;
  
  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if ((k-1) > maximum_class_number) internal_error((char *)func);
  
  l = V->el->length;
  n = size(V);
  
  /* Allocating space for centroids */
  C = allocate_centroids(k,l);
  /* Allocating space for best centroids */
  cmin = allocate_centroids(k,l);
  
  if (verbose) {
    if (better) fprintf(stdout,"\nNumber of classes to search: %d (# of trials=%d to %d until d < %1.4f)\n",(k-1),max_iter,safety_limit,lasti);
    else if (minsc > 0.0) fprintf(stdout,"\nNumber of classes to search: %d (# of trials=%d to %d until sc < %1.4f)\n",(k-1),max_iter,safety_limit,minsc);
    else fprintf(stdout,"\nNumber of classes to search: %d (# of trials=%d)\n",(k-1),max_iter);
    fprintf(stdout,"Length of vector: %d\n",(l-1));
  }
  
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    
    fprintf(o,"\nClasses:%d\n",(k-1));
    
    if (better) fprintf(o,"# of trials %d to %d\n  until d=%1.5f\n",max_iter,safety_limit,lasti);
    else if (minsc > 0.0) fprintf(o,"# of trials %d to %d\n  until sc=%1.5f\n",max_iter,safety_limit,minsc);
    else fprintf(o,"Trials %d\n",max_iter);
  }
  
  dmin = unassigned_sc();
  scmin = unassigned_sc();
  gt = 0;
  
  if (verbose) fprintf(stdout,"\nTrial: ");
  if (log_file) {
    fprintf(o,"Trials:\n");
    fclose(o);
  }
  
  vs = max_iter+1;
  if ((scs = (double *) malloc(sizeof(double)*vs)) == NULL) out_of_mem();
  
  start_tm = time(&start_tm);
  i=1;
  while (do_trials(i,dmin,scmin,minsc,better)) {
    if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    
    if (i == vs) {
      vs = (2 * vs);
      scs = expand_scvector(scs,i,vs);
    }
    if (log_file) fprintf(o," %4d: ",i);
    
    if (no_heuristic_now(i,k)) random_centroids(k,l,C,V);
    hnow = heuristic_now(i,k);

    if (distance_type == DT_L1_CL) g = hybrid_gla_l1(V,P,C,&d,n);
    else if (distance_type == DT_L2_CL) g = hybrid_gla_l2(V,P,C,&d,n);
    else if (distance_type == DT_SA) g = gla_sa(V,P,C,&d,n);
    else if (distance_type == DT_SR) g = gla_sr(V,P,C,&d,n);
    else if (distance_type == DT_CL) g = gla(V,P,C,&d,n);
    else if (distance_type == DT_L1) g = MAE_gla(V,P,C,&d,n);
    else if (distance_type == DT_L2) g = MSE_gla(V,P,C,&d,n);
    else g = fast_gla(V,P,C,&d,n);
    gt+=g;

    sc = stochastic_complexity(P,(C->k),l);

    scs[i] = sc;
    if (d < dmin) {
      dmin = d;
      if (((filter && (C->k == k)) || !filter) && (best_code_length)) copy_centroids(cmin,C);
    }
    if (sc < scmin) {
      scmin = sc;
      if (((filter && (C->k == k)) || !filter) && (!best_code_length)) copy_centroids(cmin,C);
    }
    put_dot;
    if (log_file) {
      gla_put_to_log (o,C->k-1,g,i,max_iter,hnow,sc,d);
      fclose(o);
    }
    if (hnow) {
      V = partition_to_set(P);
      restore_partition(V,P,cmin,use_class_weights);
      copy_centroids(C,cmin);
      if (gla_heuristic == HEUR_SPLITJOIN) split_and_join(k,l,n,C,P);
      else if (gla_heuristic == HEUR_REPLACEWORST) random_centroid_modify(k,l,n,C,P);
      else if (gla_heuristic == HEUR_RERANDOMIZE) rerandomize_centroids(k,l,C,P);
      else if (gla_heuristic == HEUR_RANDOMSWAP) random_swap(k,l,n,C,P);
    }
    V = partition_to_set(P);
    i++;
  } /* End of while */
  stop_tm = time(&stop_tm);
  
  if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  tot_iter = (i-1);
  if (log_file) gla_statistics(o,start_tm,stop_tm,gt,tot_iter,k,l,n,scmin,scs);
  if (log_file) {
    fprintf(o,"--\n\n");
    fclose(o);
  }

  /* Restoring best partition */
  restore_partition(V,P,cmin,use_class_weights);
  cmin->SC = scmin;
  cmin->I = dmin;
  
  /* Freeing space of centroids */
  free(scs);
  deallocate_centroids(C);
  
  if (verbose) {
    fprintf(stdout," ok.\n");
    fflush(stdout);
  }
  return cmin;
}

/*End of glainf.c*/
