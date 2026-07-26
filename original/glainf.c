
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
/* form new set of centroids by joining closest pair (in sense of squared distance) */
/* of classes and by spliting the most incoherent class (in sense of distortion) */
void split_and_join2 (int k, int l, int n, InfCentroid *C, Partition *P);
/* form new set of centroids by joining smallest class and its closest pair */
/* (in sense of squared distance)  of classes and by spliting the most incoherent */
/* class (in sense of distortion) */
void replace_worst (int k, int l, int s, InfCentroid *C, Partition *P);
/* draw new random centroid for the most incoherent class (in sense of distortion) */
void replace_smallest (int k, int l, InfCentroid *C, Partition *P);
/* draw new random centroid for the smallest class */
void random_swap (int k, int l, int s, InfCentroid *C, Partition *P);
/* draw a random centroid for a random class */
void random_swap2 (int k, int l, int s, InfCentroid *C, Partition *P);
/* draw a random centroid within the class for a random class */

/* OTHER */

void restore_partition (ST *V, Partition *P, InfCentroid *C, int weights);
void gla_put_to_log (FILE *o, int k, int g, int i, int max_iter, double sc, double d);


void special_gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "special_gla";
  const char *es2 = "ERROR: No centroids";
  double d,nd;
  int improvement;
  int i,k,s,t;
  
  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);

  if (decreasing_epsilon) epsilon = 0.1;

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
  d = average_codelength(P,C,FALSE);
  
  /* Phaze 2: Minimize Codelength */
  while (improvement) {
    inf_remove_empty(P,C,n); /* empty cell problem */
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
  inf_remove_empty(P,C,n); /* empty cell problem */
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
    inf_remove_empty(P,C,n); /* empty cell problem */
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
  inf_remove_empty(P,C,n); /* empty cell problem */

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
      inf_remove_empty(P,C,n); /* empty cell problem */
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
  inf_remove_empty(P,C,n); /* empty cell problem */

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
    inf_remove_empty(P,C,n); /* empty cell problem */
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
  inf_remove_empty(P,C,n); /* empty cell problem */

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
    inf_remove_empty(P,C,n); /* empty cell problem */
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
  inf_remove_empty(P,C,n); /* empty cell problem */
  
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
    inf_remove_empty(P,C,n); /* empty cell problem */
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
  inf_remove_empty(P,C,n); /* empty cell problem */
  
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
  const char *func = "MSE_gla";
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

int MSE_gla2 (ST *V, Partition *P, InfCentroid *C, double *dmin, int n) {
  /* This is the generalized Lloyd algorithm */
  const char *func = "MSE_gla2";
  const char *es2 = "ERROR: No centroids";
  int i,k;
  
  if (V == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es2,(char *)func);
  
  MSE_nearest_neighbour(V,P,C);
  remove_empty(P,C); /* empty cell problem */
  k = C->k;
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,n);
  }
  V = partition_to_set(P);
  MSE_nearest_neighbour(V,P,C);
  remove_empty(P,C); /* empty cell problem */
  *dmin = overall_MSE(P,C);

  return 2;
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
  int l,gt,g,k,n;
  double d,dmin,sc;
  InfCentroid *C;
  Partition *P;
  const char *func = "use_gla_load_centroids";
  const char *es = "incompatible centroids and data";
  
  gt = 0;

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

  /* NEW STUFF */

    if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    
    if (log_file) fprintf(o," %4d: ",1);

    if ((ls_heuristic != HEUR_NONE) && (k > 4)) {
      g = MSE_gla2(V,P,C,&d,n);
    } else {
      if (distance_type == DT_L1_CL) g = hybrid_gla_l1(V,P,C,&d,n);
      else if (distance_type == DT_L2_CL) g = hybrid_gla_l2(V,P,C,&d,n);
      else if (distance_type == DT_SA) g = gla_sa(V,P,C,&d,n);
      else if (distance_type == DT_SR) g = gla_sr(V,P,C,&d,n);
      else if (distance_type == DT_CL) g = gla(V,P,C,&d,n);
      else if (distance_type == DT_L1) g = MAE_gla(V,P,C,&d,n);
      else if (distance_type == DT_L2) g = MSE_gla(V,P,C,&d,n);
      else g = fast_gla(V,P,C,&d,n);
    }

    gt+=g;

    sc = stochastic_complexity(P,(C->k),l);
    if (log_file) {
      gla_put_to_log (o,C->k-1,g,1,1,sc,d);
      fflush(o);
    }

    if ((ls_heuristic != HEUR_NONE) && (k > 3)) {
      g=local_search(o,P,C,sc,&d,k,l,n);
      gt+=g;
      sc = stochastic_complexity(P,(C->k),l);
      fprintf(o,"Final: ");
      gla_put_to_log (o,C->k-1,g,1,1,sc,d);
      fflush(o);
    }
    if (log_file) {
      fclose(o);
    }

/*  OLD STUFF
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
  fprintf(o,"Actual classes = %3d total gla = %5d dmin = %2.3f\n\n",((C->k)-1),gt,dmin);
  fclose(o);
*/
  
  deallocate_centroids(C);
  
  return P;
}

void split_and_join (int k, int l, int n, InfCentroid *C, Partition *P) {
  const char *func = "split_and_join";
  double d,dmin,max;
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
  imin = random_index(k-1);
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
    C->el[k-1]->el[i] = 1.0 + ((double) x->el[i]) / 3.0;
    C->el[imax]->el[i] = 1.0 + ((double) y->el[i]) / 3.0;
  }
  
  deallocate_dvector(X);
}

#ifdef _KADAPTIVE
void join_only (int k, int l, int n, InfCentroid *C, Partition *P) {
  const char *func = "join_only";
  double d,dmin,max;
  int i,j,imin;
  int imax = 1;
  int jmin = 1;
  BV *x;
  BV *y;
  
  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  /* take a random class (imin) and find closest pair to it (jmin) */
  imin = random_index(k-1);
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

  inf_remove_empty(P,C);
}

void split_only (int k, int l, int n, InfCentroid *C, Partition *P) {
  const char *func = "split_only";
  double d,dmin,max;
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
  
  /* search worst classification */
  max = 0.0;
  for (i=1;i<k;i++) {
    if (X->el[i] > max) {
      max = X->el[i];
      imax = i;
    }
  }
  
  /* split it by taking new centroids: random and its worst pair */
  worst_matching_vectors(P->el[imax],&x,&y);
  for (i=1;i<l;i++) {
    add_centroid(C);
    C->el[k]->el[i] = 1.0 + ((double) x->el[i]) / 3.0;
    C->el[imax]->el[i] = 1.0 + ((double) y->el[i]) / 3.0;
  }
  
  deallocate_dvector(X);
}
#endif

void split_and_join2 (int k, int l, int n, InfCentroid *C, Partition *P) {
  const char *func = "split_and_join2";
  double d,dmin;
  int smin,max;
  int i,j,imin;
  int imax = 1;
  int jmin = 1;
  IntVector *X;
  Vector *D;
  BV *x;
  BV *y;
  
  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  /* calculate class sizes to vector X */
  X = allocate_ivector(k);
  D = allocate_dvector(k);
  X->el[0] = 0;
  D->el[0] = 0.0;
  for (i=1;i<k;i++) {
    X->el[i] = size(P->el[i]);
    D->el[i] = class_distortion(P->el[i],C->el[i]);
  }
  
  /* find smallest class */
  smin = n;
  imin = 1;
  for (j=1;j<k;j++) {
    if (X->el[j] < smin) {
      smin = X->el[j];
      imin = j;
    }
  }
  /* find its closest neighbor */
  dmin = ((double)l)+1.0;
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
  
  /* search most incoherent class */
  max = 0.0;
  for (i=1;i<(k-1);i++) {
    if (i != imin) {
      if (D->el[i] > max) {
	max = D->el[i];
	imax = i;
      }
    }
  }
  
  /* split it by taking new centroids: random and its worst pair */
  worst_matching_vectors(P->el[imax],&x,&y);
  for (i=1;i<l;i++) {
    C->el[k-1]->el[i] = 1.0 + ((double) x->el[i]) / 3.0;
    C->el[imax]->el[i] = 1.0 + ((double) y->el[i]) / 3.0;
  }
  
  deallocate_ivector(X);
  deallocate_dvector(D);
}

void replace_worst (int k, int l, int s, InfCentroid *C, Partition *P) {
  const char *func = "replace_worst";
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

  c = random_index(k-1);
  n = size(P->el[c]);
  ind = random_index(n);
  
  x = get_vector_i(P->el[c],ind);
  if (x == NULL) internal_error((char *)func);

  t = C->el[imax];
  for (j=1;j<l;j++) t->el[j] = 1.0 + ((double) x->el[j]) / 3.0;
  local_repartition_mse(imax,P,C);

  deallocate_dvector(X);
}


#ifdef _KADAPTIVE
void add_new_class (int k, int l, int n, InfCentroid *C, Partition *P) {
  const char *func = "add_new_class";
  double d,dmin,max;
  int i,j,imin;
  int imax = 1;
  int jmin = 1;
  BV *x;
  
  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  /* draw a random x */
  c = random_index(k-1);
  n = size(P->el[c]);
  ind = random_index(n);
  
  x = get_vector_i(P->el[c],ind);
  if (x == NULL) internal_error((char *)func);

  /* add a new centroid and set it to x */
  add_centroid(C);
  for (i=1;i<l;i++) {
    C->el[k]->el[i] = 1.0 + ((double) x->el[i]) / 3.0;
  }
}
#endif

void replace_smallest (int k, int l, InfCentroid *C, Partition *P) {
  const char *func = "replace_smallest";
  int max;
  int i,ind,n,j,c;
  int imax = 1;
  IntVector *X;
  Centroid *t;
  BV *x;

  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  /* calculate class sizes to vector X */
  X = allocate_ivector(k);
  X->el[0] = 0;
  for (i=1;i<k;i++) X->el[i] = size(P->el[i]);
  
  /* find worst class */
  max = 0;
  for (i=1;i<k;i++) {
    if (X->el[i] > max) {
      max = X->el[i];
      imax = i;
    }
  }

  c = random_index(k-1);
  n = size(P->el[c]);
  ind = random_index(n);
  
  x = get_vector_i(P->el[c],ind);
  if (x == NULL) internal_error((char *)func);

  t = C->el[imax];
  for (j=1;j<l;j++) t->el[j] = 1.0 + ((double) x->el[j]) / 3.0;
  local_repartition_mse(imax,P,C);

  deallocate_dvector(X);
}

#ifdef _KADAPTIVE
void remove_smallest (int k, int l, InfCentroid *C, Partition *P) {
  const char *func = "remove_smallest";
  int max;
  int i,ind,n,j,c;
  int imax = 1;
  IntVector *X;
  Centroid *t;
  BV *x;

  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  /* calculate class sizes to vector X */
  X = allocate_ivector(k);
  X->el[0] = 0;
  for (i=1;i<k;i++) X->el[i] = size(P->el[i]);
  
  /* find smallest class */
  max = 0;
  for (i=1;i<k;i++) {
    if (X->el[i] > max) {
      max = X->el[i];
      imax = i;
    }
  }

  deallocate_dvector(X);
}
#endif

void random_swap (int k, int l, int s, InfCentroid *C, Partition *P) {
  const char *func = "random_swap";
  int c1,c2,j,ind,n;
  BV *x;
  Centroid *t;

  /* draw a random class which to modify */
  c1 = random_index(k-1);

  /* draw a random vector from a random class */
  c2 = random_index(k-1);
  n = size(P->el[c2]);
  ind = random_index(n);

  x = get_vector_i(P->el[c2],ind);
  if (x == NULL) internal_error((char *)func);

  /* reset centroid of the modified class */
  t = C->el[c1];
  for (j=1;j<l;j++) t->el[j] = 1.0 + ((double) x->el[j]) / 3.0;
  local_repartition_mse(c1,P,C);
}

void random_swap2 (int k, int l, int s, InfCentroid *C, Partition *P) {
  const char *func = "random_swap2";
  int c1,j,ind,n;
  BV *x;
  Centroid *t;

  /* draw a random class which to modify */
  c1 = random_index(k-1);

  n = size(P->el[c1]);
  ind = random_index(n);

  x = get_vector_i(P->el[c1],ind);
  if (x == NULL) internal_error((char *)func);

  /* reset centroid of the modified class */
  t = C->el[c1];
  for (j=1;j<l;j++) t->el[j] = 1.0 + ((double) x->el[j]) / 3.0;
  local_repartition_mse(c1,P,C);
}

void restore_partition (ST *V, Partition *P, InfCentroid *C, int weights) {
  calculate_logs(C);
  if ((distance_type == DT_L1_CL) || (distance_type == DT_L2_CL) || (distance_type == DT_SA) || (distance_type == DT_SR) || (distance_type == DT_CL)) inf_nearest_neighbour(V,P,C,weights);
  else if (distance_type == DT_L1) MAE_nearest_neighbour(V,P,C);
  else if (distance_type == DT_L2) MSE_nearest_neighbour(V,P,C);
  else fast_nearest_neighbour(V,P,C);
}

double *expand_scvector (double *scs, int i, int vs) {
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


void gla_put_to_log (FILE *o, int k, int g, int i, int max_iter, double sc, double d) {
  
  fprintf(o,"ak = %4d, gla = %5d, d = ",k,g);
  if (d < 9.999999) fputc(' ',o);
  fprintf(o,"%1.5f, sc = ",d);
  if (sc < 9.999999) fputc(' ',o);
  fprintf(o,"%1.5f",sc);
  if (i > max_iter) fprintf(o," |\n");
  else fprintf(o,"\n");
}

void heur_put_to_log (FILE *o, int i, double sc, double d) {
  
  fprintf(o,"     %4d: sc = ",i);
  if (sc < 9.999999) fputc(' ',o);
  if ((ls_heuristic_cycler) || (ls_adaptive_heuristic)) {
    fprintf(o,"%1.5f (%1.5f)",sc,d);
    if (ls_heuristic == HEUR_SPLITJOIN1) fprintf(o," SJ1\n");
    else if (ls_heuristic == HEUR_REPLACEWORST) fprintf(o," RWO\n");
    else if (ls_heuristic == HEUR_RANDOMSWAP) fprintf(o," RS1\n");
    else if (ls_heuristic == HEUR_RANDOMSWAP2) fprintf(o," RS2\n");
    else if (ls_heuristic == HEUR_REPLACESMALLEST) fprintf(o," RSA\n");
    else if (ls_heuristic == HEUR_SPLITJOIN2) fprintf(o," SJ2\n");
  }  else {
    fprintf(o,"%1.5f (%1.5f)\n",sc,d);
  }
}

#define do_trials(i,dmin,scmin,minsc,better) ((i<(max_iter+1)) || (better && (dmin > lasti) && (i < (safety_limit+1))) || (filter && !(dmin < 10000.0)) || ((minsc > 0.0) && (minsc < scmin) && (i < (safety_limit+1))))

int local_search (FILE *o, Partition *P, InfCentroid *C, double sc, double *d, int k, int l, int n) {
  double scn,r;
  ST *V;
  InfCentroid *cmin;
  int i,j,h,g,gt,s;
  int suc[6] = {0, 0, 0, 0, 0, 0};
  double w[6] = {0, 0, 0, 0, 0, 0};
  double p[6] = {1.0/6.0, 1.0/6.0, 1.0/6.0, 1.0/6.0, 1.0/6.0, 1.0/6.0};
  double alfa,beta,W;

  gt = 0;
  s = 0;
  alfa = 2.5;
  beta = 0.2;

  if ((ls_heuristic_cycler) || (ls_adaptive_heuristic)) {
    if ((log_file) && (o != NULL) && (ls_heuristic_cycler)) fprintf(o,"    Local Search: Cycling heuristics\n");
    if ((log_file) && (o != NULL) && (ls_adaptive_heuristic)) fprintf(o,"    Local Search: Adaptive heuristics\n");
    h = random_index(5);
    if (h == 0) ls_heuristic = HEUR_SPLITJOIN1;
    else if (h == 1) ls_heuristic = HEUR_REPLACEWORST;
    else if (h == 2) ls_heuristic = HEUR_REPLACESMALLEST;
    else if (h == 3) ls_heuristic = HEUR_RANDOMSWAP;
    else if (h == 4) ls_heuristic = HEUR_SPLITJOIN2;
    else if (h == 4) ls_heuristic = HEUR_RANDOMSWAP2;
  }
  else if (((log_file) && (o != NULL)) && (ls_heuristic == HEUR_SPLITJOIN1)) fprintf(o,"    Local Search: Split and Join v1 (SJ1)\n");
  else if (((log_file) && (o != NULL)) && (ls_heuristic == HEUR_REPLACEWORST)) fprintf(o,"    Local Search: Replace Worst (RWO)\n");
  else if (((log_file) && (o != NULL)) && (ls_heuristic == HEUR_REPLACESMALLEST))  fprintf(o,"    Local Search: Replace Smallest (RSA)\n");
  else if (((log_file) && (o != NULL)) && (ls_heuristic == HEUR_RANDOMSWAP))  fprintf(o,"    Local Search: Random Swap v1 (RS1)\n");
  else if (((log_file) && (o != NULL)) && (ls_heuristic == HEUR_RANDOMSWAP2))  fprintf(o,"    Local Search: Random Swap v2 (RS2)\n");
  else if (((log_file) && (o != NULL)) && (ls_heuristic == HEUR_SPLITJOIN2))  fprintf(o,"    Local Search: Split and Join v2 (SJ2)\n");

  cmin = allocate_centroids(k,l);
  copy_centroids(cmin,C);

  for (i=1;i<ls_heuristic_count;i++) {
    if (ls_heuristic == HEUR_SPLITJOIN1) split_and_join(k,l,n,C,P);
    else if (ls_heuristic == HEUR_REPLACEWORST) replace_worst(k,l,n,C,P);
    else if (ls_heuristic == HEUR_REPLACESMALLEST) replace_smallest(k,l,C,P);
    else if (ls_heuristic == HEUR_RANDOMSWAP) random_swap(k,l,n,C,P);
    else if (ls_heuristic == HEUR_RANDOMSWAP2) random_swap2(k,l,n,C,P);
    else if (ls_heuristic == HEUR_SPLITJOIN2) split_and_join2(k,l,n,C,P);
    
    V = partition_to_set(P);
    g = MSE_gla2(V,P,C,d,n);

    gt+=g;

    scn = stochastic_complexity(P,k,l);

    for (j=0;j<5;j++) if ((w[j]-beta) >= 0.0) w[j] -= beta;
    if (((i % 10) == 0) && (i < 1001)) beta /= 1.25;

    if (scn < sc) {
      s++;
      copy_centroids(cmin,C);
      sc = scn;
      if ((log_file) && (o != NULL)) {
	heur_put_to_log (o,i,sc,*d);
	fflush(o);
      }
      if (ls_heuristic == HEUR_SPLITJOIN1) {
	suc[0]++;
	w[0]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_REPLACEWORST) {
	suc[1]++;
	w[1]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_REPLACESMALLEST) {
	suc[2]++;
	w[2]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_RANDOMSWAP) {
	suc[3]++;
	w[3]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_SPLITJOIN2) {
	suc[4]++;
	w[4]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_RANDOMSWAP2) {
	suc[5]++;
	w[5]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      }
      /* if ((ls_adaptive_heuristic) && (o != NULL) && (log_file)) fprintf(o,"           %.2f, %.2f, %.2f, %.2f, %.2f, %.2f\n",p[0],p[1],p[2],p[3],p[4],p[5]); */
    } else {
      copy_centroids(C,cmin);
    }
    if ((i % (ls_heuristic_count / 10)) == 0) put_dot;
    if (ls_heuristic_cycler) {
      if (ls_heuristic == HEUR_RANDOMSWAP2) ls_heuristic = HEUR_SPLITJOIN1;
      else if (ls_heuristic == HEUR_SPLITJOIN1) ls_heuristic = HEUR_REPLACEWORST;
      else if (ls_heuristic == HEUR_REPLACEWORST) ls_heuristic = HEUR_REPLACESMALLEST;
      else if (ls_heuristic == HEUR_REPLACESMALLEST) ls_heuristic = HEUR_RANDOMSWAP;
      else if (ls_heuristic == HEUR_RANDOMSWAP) ls_heuristic = HEUR_SPLITJOIN2;
      else if (ls_heuristic == HEUR_SPLITJOIN2) ls_heuristic = HEUR_RANDOMSWAP2;
    } else if (ls_adaptive_heuristic) {
      r = give_true_random();
      if (r < p[0]) ls_heuristic = HEUR_SPLITJOIN1;
      else if (r <= (p[0]+p[1])) ls_heuristic = HEUR_REPLACEWORST;
      else if (r <= (p[0]+p[1]+p[2])) ls_heuristic = HEUR_REPLACESMALLEST;
      else if (r <= (p[0]+p[1]+p[2]+p[3])) ls_heuristic = HEUR_RANDOMSWAP;
      else if (r <= (p[0]+p[1]+p[2]+p[3]+p[4])) ls_heuristic = HEUR_SPLITJOIN2;
      else if (r <= (p[0]+p[1]+p[2]+p[3]+p[4]+p[5])) ls_heuristic = HEUR_RANDOMSWAP2;
    }
  }
  if ((log_file) && (o != NULL)) {
    fprintf(o,"     Successes: %4d\n",s);
    if ((ls_heuristic_cycler) || (ls_adaptive_heuristic)) {
      fprintf(o,"     SJ1: %3d, RWO: %3d, RSA: %3d, RS1: %3d, SJ2: %3d, RS2: %3d\n",suc[0],suc[1],suc[2],suc[3],suc[4],suc[5]);
      if (ls_adaptive_heuristic) fprintf(o,"     P:  %.2f,     %.2f,     %.2f,     %.2f,     %.2f,     %.2f\n",p[0],p[1],p[2],p[3],p[4],p[5]);
    }
    fflush(o);
  }

  copy_centroids(C,cmin);
  calculate_logs(C);
  V = partition_to_set(P);
  if (distance_type == DT_L1_CL) g = gla(V,P,C,d,n);
  else if (distance_type == DT_L2_CL) g = gla(V,P,C,d,n);
  else if (distance_type == DT_SA) g = gla(V,P,C,d,n);
  else if (distance_type == DT_SR) g = gla(V,P,C,d,n);
  else if (distance_type == DT_CL) g = gla(V,P,C,d,n);
  else if (distance_type == DT_L1) g = MAE_gla(V,P,C,d,n);
  else if (distance_type == DT_L2) g = MSE_gla(V,P,C,d,n);
  else g = fast_gla(V,P,C,d,n);
  
  scn = stochastic_complexity(P,k,l);
  if (scn > sc) copy_centroids(C,cmin);

  gt+=g;

  deallocate_centroids(cmin);

  return gt;
}

#ifdef _KADAPTIVE
int local_search2 (FILE *o, Partition *P, InfCentroid *C, double sc, double *d, int k, int l, int n) {
  double scn,r;
  ST *V;
  InfCentroid *cmin;
  int i,j,h,g,gt,s;
  int suc[6] = {0, 0, 0, 0, 0, 0};
  double w[6] = {0, 0, 0, 0, 0, 0};
  double p[6] = {1.0/6.0, 1.0/6.0, 1.0/6.0, 1.0/6.0, 1.0/6.0, 1.0/6.0};
  double alfa,beta,W;

  remove_empty_sets = TRUE;

  gt = 0;
  s = 0;
  alfa = 2.5;
  beta = 0.2;

  if (log_file) && (o != NULL) fprintf(o,"    Local Search: Adaptive heuristics (free k)\n");
  h = random_index(5);
  if (h == 0) ls_heuristic = HEUR_SPLIT;
  else if (h == 1) ls_heuristic = HEUR_REPLACEWORST;
  else if (h == 2) ls_heuristic = HEUR_REMOVESMALLEST;
  else if (h == 3) ls_heuristic = HEUR_RANDOMSWAP;
  else if (h == 4) ls_heuristic = HEUR_ADDNEW;
  else if (h == 5) ls_heuristic = HEUR_JOIN;

  cmin = allocate_centroids(k,l);
  copy_centroids(cmin,C);

  for (i=1;i<ls_heuristic_count;i++) {
    if (ls_heuristic == HEUR_SPLIT) split_only(k,l,n,C,P);
    else if (ls_heuristic == HEUR_REPLACEWORST) replace_worst(k,l,n,C,P);
    else if (ls_heuristic == HEUR_REMOVESMALLEST) remove_smallest(k,l,C,P);
    else if (ls_heuristic == HEUR_RANDOMSWAP) random_swap(k,l,n,C,P);
    else if (ls_heuristic == HEUR_ADDNEW) add_new_class(k,l,n,C,P);
    else if (ls_heuristic == HEUR_JOIN) join_only(k,l,n,C,P);
    k = P->k;

    V = partition_to_set(P);
    g = MSE_gla2(V,P,C,d,n);

    gt+=g;

    scn = stochastic_complexity(P,k,l);

    for (j=0;j<5;j++) if ((w[j]-beta) >= 0.0) w[j] -= beta;
    if (((i % 10) == 0) && (i < 1001)) beta /= 1.25;

    if (scn < sc) {
      s++;
      copy_centroids(cmin,C);
      sc = scn;
      if ((log_file) && (o != NULL)) {
	heur_put_to_log (o,i,sc,*d);
	fflush(o);
      }
      if (ls_heuristic == HEUR_SPLIT) {
	suc[0]++;
	w[0]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_REPLACEWORST) {
	suc[1]++;
	w[1]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_REMOVESMALLEST) {
	suc[2]++;
	w[2]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_RANDOMSWAP) {
	suc[3]++;
	w[3]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_JOIN) {
	suc[4]++;
	w[4]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      } else if (ls_heuristic == HEUR_ADDNEW) {
	suc[5]++;
	w[5]+=1.0;
	W = 0.0;
	for (j=0;j<5;j++) W += w[j];
	for (j=0;j<5;j++) p[j] = (w[j]+alfa) / (W+6.0*alfa);
      }
      /* if ((ls_adaptive_heuristic) && (o != NULL) && (log_file)) fprintf(o,"           %.2f, %.2f, %.2f, %.2f, %.2f, %.2f\n",p[0],p[1],p[2],p[3],p[4],p[5]); */
    } else {
      copy_centroids(C,cmin);
    }
    if ((i % (ls_heuristic_count / 10)) == 0) put_dot;
    r = give_true_random();
    if (r < p[0]) ls_heuristic = HEUR_SPLIT;
    else if (r <= (p[0]+p[1])) ls_heuristic = HEUR_REPLACEWORST;
    else if (r <= (p[0]+p[1]+p[2])) ls_heuristic = HEUR_REMOVESMALLEST;
    else if (r <= (p[0]+p[1]+p[2]+p[3])) ls_heuristic = HEUR_RANDOMSWAP;
    else if (r <= (p[0]+p[1]+p[2]+p[3]+p[4])) ls_heuristic = HEUR_JOIN;
    else if (r <= (p[0]+p[1]+p[2]+p[3]+p[4]+p[5])) ls_heuristic = HEUR_ADDNEW;
  }
  if ((log_file) && (o != NULL)) {
    fprintf(o,"     Successes: %4d\n",s);
    fprintf(o,"     SJ1: %3d, RWO: %3d, RSA: %3d, RS1: %3d, SJ2: %3d, RS2: %3d\n",suc[0],suc[1],suc[2],suc[3],suc[4],suc[5]);
    fprintf(o,"     P:  %.2f,     %.2f,     %.2f,     %.2f,     %.2f,     %.2f\n",p[0],p[1],p[2],p[3],p[4],p[5]);
    fflush(o);
  }

  copy_centroids(C,cmin);
  calculate_logs(C);
  V = partition_to_set(P);
  if (distance_type == DT_L1_CL) g = gla(V,P,C,d,n);
  else if (distance_type == DT_L2_CL) g = gla(V,P,C,d,n);
  else if (distance_type == DT_SA) g = gla(V,P,C,d,n);
  else if (distance_type == DT_SR) g = gla(V,P,C,d,n);
  else if (distance_type == DT_CL) g = gla(V,P,C,d,n);
  else if (distance_type == DT_L1) g = MAE_gla(V,P,C,d,n);
  else if (distance_type == DT_L2) g = MSE_gla(V,P,C,d,n);
  else g = fast_gla(V,P,C,d,n);
  
  scn = stochastic_complexity(P,k,l);
  if (scn > sc) copy_centroids(C,cmin);

  gt+=g;

  deallocate_centroids(cmin);

  return gt;
}
#endif

InfCentroid *use_gla (ST *V, Partition *P, int k, char* outfile, double lasti, int better, int filter, double minsc) {
  /* GLA with random selection of initial centroids */
  const char *func = "use_gla";
  int i,g,l,gt,tot_iter,vs,n;
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

    random_centroids(k,l,C,V);    

    if ((ls_heuristic != HEUR_NONE) && (k > 4)) {
      g = MSE_gla2(V,P,C,&d,n);
    } else {
      if (distance_type == DT_L1_CL) g = hybrid_gla_l1(V,P,C,&d,n);
      else if (distance_type == DT_L2_CL) g = hybrid_gla_l2(V,P,C,&d,n);
      else if (distance_type == DT_SA) g = gla_sa(V,P,C,&d,n);
      else if (distance_type == DT_SR) g = gla_sr(V,P,C,&d,n);
      else if (distance_type == DT_CL) g = gla(V,P,C,&d,n);
      else if (distance_type == DT_L1) g = MAE_gla(V,P,C,&d,n);
      else if (distance_type == DT_L2) g = MSE_gla(V,P,C,&d,n);
      else g = fast_gla(V,P,C,&d,n);
    }

    gt+=g;

    sc = stochastic_complexity(P,(C->k),l);
    if (log_file) {
      gla_put_to_log (o,C->k-1,g,i,max_iter,sc,d);
      fflush(o);
    }

    if ((ls_heuristic != HEUR_NONE) && (k > 3)) {
      g=local_search(o,P,C,sc,&d,k,l,n);
      gt+=g;
      sc = stochastic_complexity(P,(C->k),l);
      fprintf(o,"Final: ");
      gla_put_to_log (o,C->k-1,g,i,max_iter,sc,d);
      fflush(o);
    }
    if (log_file) {
      fclose(o);
    }

    scs[i] = sc;
    if (d < dmin) {
      dmin = d;
      if (((filter && (C->k == k)) || !filter) && (best_code_length)) copy_centroids(cmin,C);
    }
    if (sc < scmin) {
      scmin = sc;
      if (((filter && (C->k == k)) || !filter) && (!best_code_length)) copy_centroids(cmin,C);
    }
    if ((ls_heuristic != HEUR_NONE) && (k > 4)) {
      put_mark;
    } else {
      put_dot;
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
