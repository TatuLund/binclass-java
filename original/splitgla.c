/*
Split - GLA hybrid algorithm for fast search of k
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "const.h"
#include "vars.h"
#include "glainf.h"
#include "bottom.h"
#include "vectors.h"
#include "centroid.h"
#include "binset.h"
#include "binstuff.h"
#include "distmin.h"

int bv_hamming_distance (BV *x, BV *y);
void worst_matching_vectors (ST *V, BV **x, BV **y);
void abs_worst_matching_vectors (ST *V, BV **x, BV **y);
void set_first_centroids (InfCentroid *C, BV *x, BV *y);
int point_worst_class (Partition *P, InfCentroid *C);
void set_new_centroids (InfCentroid *Cnew, InfCentroid *C, BV *x, BV *y, int wp);
ST *split_gla (ST *V, double *scmin, double *scs, char *outfile, char *parfile);
void fclassify_vectors (char *datfile, char *outfile, char *parfile, char *hdrfile);


int bv_hamming_distance (BV *x, BV *y) {
  const char *func = "bv_hamming_distance";
  int *ex;
  int *ey;
  int l,i,d;
  
  if (x == NULL) internal_error((char *)func);
  if (y == NULL) internal_error((char *)func);
  
  ex = x->el;
  ey = y->el;
  l = x->length;
  d = 0;
  for (i=1;i<l;i++) {
    d += (ey[i] != ex[i]);
  }
  return d;
}

void worst_matching_vectors (ST *V, BV **x, BV **y) {
  const char *func = "worst_matching_vectors";
  int n,ind,dm,d;
  double r;
  BV *c;
  ST *w;
  
  if (V == NULL) internal_error((char *)func);
  if (x == NULL) internal_error((char *)func);
  if (y == NULL) internal_error((char *)func);
  
  /* get a random vector */
  n = size(V);
  r = give_true_random();
  ind = floor(r * (double) n);
  if (ind < 1) ind = 1;
  if (ind > (n-1)) ind = (n-1);
  c = get_vector_i(V,ind);
  if (c == NULL) internal_error((char *)func);
  *x = c;
  /* find the worst pair for it (L2) */
  w = V;
  dm = 0;
  while (elements_left(w)) {
    c = get_element(w);
    d = bv_hamming_distance(*x,c);
    if (d >= dm) {
      *y = c;
      dm = d;
    }
    w = next_element(w);
  }
}

void abs_worst_matching_vectors (ST *V, BV **x, BV **y) {
  const char *func = "abs_worst_matching_vectors";
  int dm,d;
  BV *c1;
  BV *c2;
  ST *w1;
  ST *w2;
  
  if (V == NULL) internal_error((char *)func);
  
  /* find the worst pair */
  w1 = V;
  w2 = V;
  dm = 0;
  while (elements_left(w1)) {
    c1 = get_element(w1);
    while (elements_left(w2)) {
      c2 = get_element(w2);
      d = bv_hamming_distance(c1,c2);
      if (d >= dm) {
	*y = c1;
	*x = c2;
	dm = d;
      }
      w2 = next_element(w2);
    }
    w1 = next_element(w1);
  }
}

void set_first_centroids (InfCentroid *C, BV *x, BV *y) {
  const char *func = "set_first_centroids";
  Centroid *t1;
  Centroid *t2;
  int i,l;
  
  if (C == NULL) internal_error((char *)func);
  if (y == NULL) internal_error((char *)func);
  if (x == NULL) internal_error((char *)func);
  
  l = x->length;
  t1 = C->el[1];
  t2 = C->el[2];
  for (i=1;i<l;i++) {
    t1->el[i] = x->el[i];
    t2->el[i] = y->el[i];
  }
}

int point_worst_class (Partition *P, InfCentroid *C) {
  const char *func = "point_worst_class";
  double d,dm;
  int i,worst,k;
  
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = P->k;
  dm = 0.0;
  worst = 1;
  for (i=1;i<k;i++) {
    d = class_distortion(P->el[i],C->el[i]);
    if (d >= dm) {
      if (P->el[i] == NULL) internal_error((char *)func);
      dm = d;
      worst = i;
    }
  }
  return worst;
}

void set_new_centroids (InfCentroid *Cnew, InfCentroid *C, BV *x, BV *y, int wp) {
  const char *func = "set_new_centroids";
  Centroid *n;
  Centroid *o;
  int l,k,i,j;
  
  if (Cnew == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  if (x == NULL) internal_error((char *)func);
  if (y == NULL) internal_error((char *)func);
  
  /* copy old centroids to new, replace old worst centroid with */
  /* other of the new two centroids and put hte other to new */
  /* position */
  l = x->length;
  k = C->k;
  for (i=1;i<k;i++) {
    if (i == wp) {
      n = Cnew->el[i];
      for (j=1;j<l;j++) n->el[j] = x->el[j];
    } else {
      n = Cnew->el[i];
      o = C->el[i];
      for (j=1;j<l;j++) n->el[j] = o->el[j];
    }
  }
  n = Cnew->el[k];
  for (j=1;j<l;j++) n->el[j] = y->el[j];
}

ST *split_gla (ST *V, double *scmin, double *scs, char *outfile, char *parfile) {
  const char *func = "split_gla";
  InfCentroid *C;
  InfCentroid *Cn;
  Partition *P;
  BV *x;
  BV *y;
  int k,l,kc,wp;
  double dmin,sc,scm;
  FILE *p;
  FILE *o = NULL;
  
  if (V == NULL) internal_error((char *)func);
  
  if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  
  l = vec_len;
  k = 2;
  kc = 0;
  P = allocate_partition(2);
  P->el[1] = V;
  /* calculate first stochastic complexity */
  sc = stochastic_complexity(P,k,l);
  scm = sc;
  if (verbose) fprintf(stdout,"1:\n  sc = %.4f\n",sc);
  if (log_file) fprintf(o,"  1: sc = %.4f\n",sc);
  if (sc < scs[1]) scs[1] = sc;
  if (sc < *scmin) {
    *scmin = sc;
    if (verbose) fprintf(stdout,"  best so far\n");
    p = fopen(parfile,"w");
    inf_write_partition(p,P);
    fclose(p);
  }
  P->el[1] = NULL;
  deallocate_partition(P);

  /* iniate search */
  if (use_abs_match) abs_worst_matching_vectors(V,&x,&y);
  else worst_matching_vectors(V,&x,&y);
  C = allocate_centroids(k+1,l);
  set_first_centroids(C,x,y);
  while (kc < kstopwhen) {
    P = allocate_partition(k+1);
    if (verbose) fprintf(stdout,"%d: ",k);
    special_gla(V,P,C,&dmin);
    sc = stochastic_complexity(P,k+1,l);
    if (verbose) fprintf(stdout,"\n  sc = %.4f, d = %.4f\n",sc,dmin);
    if (log_file) {
      fprintf(o,"%3d: sc = %.4f, d = %.4f",k,sc,dmin);
    }
    if (sc < scs[k]) scs[k] = sc;
    if (sc < *scmin) {
      *scmin = sc;
      scm = sc;
      if (verbose) fprintf(stdout,"  best so far\n");
      if (log_file) fprintf(o," B");
      p = fopen(parfile,"w");
      inf_write_partition(p,P);
      fclose(p);
      kc = 0;
    } else {
      if (sc < scm) {
	if (log_file) fprintf(o," b");
	scm = sc;
	kc = 0;
      } else {
	kc++;
      }
    }
    if (log_file) {
      fprintf(o,"\n");
      fflush(o);
    }
    wp = point_worst_class(P,C);
    if (use_abs_match) abs_worst_matching_vectors(P->el[wp],&x,&y);
    else worst_matching_vectors(P->el[wp],&x,&y);
    k++;
    Cn = allocate_centroids(k+1,l);
    set_new_centroids(Cn,C,x,y,wp);
    deallocate_centroids(C);
    V = partition_to_set(P);
    deallocate_partition(P);
    C = Cn;
  }
  fclose(o);
  return V;
}

void fclassify_vectors (char *datfile, char *outfile, char *parfile, char *hdrfile) {
  FILE *f;
  FILE *o = NULL;
  ST *V;
  int s,i,replications;
  const char *func = "fclassify_vectors";
  time_t tm;
  double scmin;
  double *scs;
  
  tm = time(&tm);
  set_rand(tm);
  
  /* Read input data */
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..");
  V = read_set(f,hdrfile);
  fclose(f);
  s = size(V);
  if (verbose) fprintf(stdout,"Read %d vectors of data\n",s);
  
  log2_factorials = prepare_log2_factorials(s+s);

  if (log_file) if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);

  if (use_abs_match) replications = 1;
  else replications = 10;
  
  if (log_file) {
    start_text(o);
    fprintf(o,"\nAlogrithm: Split-GLA\n");
    
    if (use_abs_match) fprintf(o,"  Deterministic version using absolute worst match\n\n");
    else fprintf (o,"  Running for %d times\n\n",replications);
    fclose(o);
  }
  
  scmin = vec_len + 1.0;
  
  if ((scs = malloc((s+1)*sizeof(double))) == NULL) out_of_mem();
  for (i=0;i<(s+1);i++) scs[i] = unassigned_sc();
  
  for (i=0;i<replications;i++) {
    if (log_file) {
      if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
      fprintf(o,"--\nrun: %d\n\n",i+1);
      fclose(o);
    }
    if (verbose) fprintf(stdout,"\n--\nrun: %d\n\n",i+1);
    V = split_gla(V,&scmin,scs,outfile,parfile);
    
  }
  
  
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    i = 1;
    fprintf(o,"\nSC as function of k\n");
    while (scs[i] < unassigned_sc()) {
      fprintf(o,"%3d:%1.5f\n",i,scs[i]);
      i++;
    }
    fclose(o);
  }
  free(scs);

  /* Free frequency vector */
  free(total_freqs);

}


