
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "const.h"
#include "bottom.h"
#include "binstuff.h"
#include "binset.h"
#include "vars.h"
#include "vectors.h"
#include "centroid.h"
#include "distmin.h"
#include "cumulat.h"

int alg2_distance (BV *x, int *hmo) {
  /* Calculates Hamming distance */
#ifdef _MY_DEBUG
  const char *es1 = "Unequal vector lengths";
  const char *func = "hamming_distance";
#endif
  int h,i,l;
  int *elx;
  
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
  for (i=1;i<l;i++) {
    if ((hmo[i] == 1) && (elx[i] == 1)) h++;
    else if (((hmo[i] == 0) && (elx[i] == 1)) || ((hmo[i] == 1) && (elx[i] == 0))) h--;
  }
  return l-h;
}

double alg2_distortion(DynPartition *P) {
  DynPartition *tmp = NULL;
  double h;
  int t;
  BV *x;
  ST *V;

  tmp = P;
  t = 0;
  while (tmp != NULL) {
    V = tmp->el;
    while (elements_left(V)) {
      x = get_element(V);
      h += alg2_distance(x,tmp->hmo->el);
      V = next_element(V);
      t++;
    }
    tmp = tmp->next;
  }
  return h / (double) t;
}


DynPartition *alg2_init (FILE *o, ST *V) {
  BV *x;
  int i,k,d,dmin;
  int no_match;
  DynPartition *P = NULL;
  DynPartition *tmp = NULL;


  x = get_element(V);
  P = dp_initialize(x);
  V = next_element(V);

  while (elements_left(V)) {
    x = get_element(V);
    tmp = P;
    i = 1;
    no_match = TRUE;
    dmin = vec_len*2;
    while (tmp != NULL) {
      d = alg2_distance(x,tmp->hmo->el);
      if (d < dmin) {
	dmin = d;
	k = i;
/*	fprintf(stdout,"%d: %d!\n",i,d); */
	if (d < (vec_len+t2_treshold)) no_match = FALSE;
      } else {
/*	fprintf(stdout,"%d: %d\n",i,d); */
      }
      tmp = tmp->next;
      i++;
    }
    if (no_match) {
      dp_extend(P,x);
    } else {
      dp_put_vector(P,x,k);
    }
    V = next_element(V);
  }
  return P;
}


void alg2_iterate (FILE *o, DynPartition *P) {
  ST *W;
  ST *V = NULL;
  DynPartition *tmp;
  DynPartition *tmp2;
  int no_match = TRUE;
  int i,k,j,d,dmin;
  BV *x;

  j = 1;
  tmp = P;
  while (tmp != NULL) {
    W = tmp->el;
    dmin = vec_len*2;
    while (elements_left(W)) {
      x = get_element(W);
      tmp2 = P;
      i = 1;
      while (tmp2 != NULL) {
	d = alg2_distance(x,tmp->hmo->el);
	if (d < dmin) {
	  dmin = d;
	  k = i;
	  if (d < vec_len) no_match = FALSE;
	}
	tmp2 = tmp2->next;
	i++;
      }
      if (no_match) {
	dp_extend(P,x);
      } else {
	if (k != j) {
	  dp_put_vector(P,x,k);
	} else {
	  V = add_element(V,x);
	}
      }
      del_element(W);
    }
    tmp->el = V;
    tmp = tmp->next;
    j++;
  }
}


void apply_alg2 (char *datfile, char* outfile, char *parfile, char *hdrfile) {
  const char *func = "apply_alg2";
  time_t tm;
  ST *V;
  ST *W;
  FILE *f;
  FILE *o;
  int t,i,origv;
  DynPartition *P;
  Partition *R;
  Partition *R2;
  double c,d,dmin,cavg,kavg,davg;

  tm = time(&tm);
  set_rand(tm);
  
  /* Read input data */
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..");
  V = dp_read_set(f,hdrfile);
  fclose(f);
  t = size(V);
  if (log2_factorials == NULL) log2_factorials = prepare_log2_factorials(t+t);
  if (verbose) fprintf(stdout,".. read %d vectors of data\n",t);

  if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
  start_text(o);
  fflush(o);
  dmin = unassigned_sc();
  cavg = 0.0;
  davg = 0.0;
  kavg = 0.0;

  fprintf(o,"Treshold: %d\n",t2_treshold);
  for(i=1;i<101;i++) {

    W = copy_set(V);
    if (verbose) fprintf(stdout,"Initializing: ..");
    P = alg2_init(o,W);
    if (verbose) fprintf(stdout,".. ok \n");
/*
    if (verbose) fprintf(stdout,"\nIterating: ");
    alg2_iterate(o,P);
*/

    d = alg2_distortion(P);
    R = dp_convert(P);
    if ((f = fopen("tmpfile","w")) == NULL) file_error(parfile,(char *)func);
    inf_write_partition(f,R);
    fclose(f);
    origv = verbose;
    verbose = FALSE;
    if ((f = fopen("tmpfile","r")) == NULL) file_error(parfile,(char *)func);
    R2 = read_partition(f,TRUE);
    fclose(f);
    verbose = origv;

    c = stochastic_complexity(R2,(R->k),vec_len); 
    deallocate_partition(R2);

    fprintf(o,"%3d: %3d, %2.4f %2.4f\n",i,((R->k)-1),c,d);
    cavg += c;
    davg += d;
    kavg += (double)((R->k)-1);
    fflush(o);

    if (d < dmin) {
      if (verbose) fprintf(stdout,"Saving: ");
      dmin = d;
      if ((f = fopen(parfile,"w")) == NULL) file_error(parfile,(char *)func);
      inf_write_partition(f,R);
      fclose(f);
      if (verbose) fprintf(stdout,".. ok \n");
    }

    V = dp_redraw(V);
    deallocate_partition(R);

  }
  fprintf(o,"Avg: %3.0f, %2.4f %2.4f\n",kavg/100.0,cavg/100.0,davg/100.0);

  fclose(o);
  
}
