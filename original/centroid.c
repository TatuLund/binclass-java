/*
Module for centroid functions and sorting and loading centroids
Centroid file format is
k
0.XXX:0.XXX ... 0.XXX
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "const.h"
#include "vars.h"
#include "bottom.h"
#include "binset.h"
#include "vectors.h"
#include "format.h"
#include "adding.h"
#include "distmin.h"

/* prototypes */

void do_save_centroids (char* parfile, char* ctrfile, char* hdrfile);

void calculate_logs (InfCentroid *C);
void mix_centroids (InfCentroid *C, int temp);
Centroid *allocate_centroid (int l);
InfCentroid *allocate_centroids (int k, int l);
void deallocate_centroid (Centroid *c);
void deallocate_centroids (InfCentroid *C);
void add_centroid (InfCentroid *C);
void remove_centroid (InfCentroid *C, int i);

InfCentroid *load_centroids (FILE *f);
/* Load set of centroids from readily opened file, if file suddenly ends */
/* or does not pass simple format check return NULL, else return pointer */
/* to a new set of centroids. File format must conform to one saved by */
/* save_centroids */

void save_centroids (FILE *f, InfCentroid *C);
/* save centroids to readily opened file */

void normal_centroids (int k, int l, InfCentroid *C);
void statistical_centroids (int k, int l, InfCentroid *C);
void semi_random_centroids (int k, int l, InfCentroid *C);
void random_centroids (int k, int l, InfCentroid *C, ST *V);
void copy_centroids (InfCentroid *cmin, InfCentroid *C);
double edistance_2 (double *x, double *y, int l);
void pick_centroids (int k, int l, InfCentroid *C, ST *V);

/* implementation */

void calculate_logs (InfCentroid *C) {
  int k,i,j,l,si;
  Centroid *t;
#ifdef _MY_DEBUG
  const char *func = "calculate_logs";
#endif
  
#ifdef _MY_DEBUG
  if (C == NULL) internal_error((char *)func);
#endif

  k = (C->k);
  l = (C->el[1]->l);
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++) {
    t = C->el[i];
    for (j=1;j<l;j++) {
      if (t->el[j] < epsilon) t->el[j] = epsilon;
      if (t->el[j] > (1.0-epsilon)) t->el[j] = 1.0-epsilon;
      t->log0[j] = log_2(t->el[j]);
      t->log1[j] = log_2(1.0-(t->el[j]));
    }
  }
}

void mix_centroids (InfCentroid *C, int temp) {
  int k,i,j,l,si;
  Centroid *t;
  const char *func = "mix_centroids";
  double r;
  
  if (C == NULL) internal_error((char *)func);
  k = (C->k);
  l = (C->el[1]->l);
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++)	{
    if (C->el[i] != NULL) {
      t = C->el[i];
      for (j=1;j<l;j++) {
	r = give_true_random();
	if (r < ((double) temp / (double) MAX_TEMP)) {
	  t->el[j] = give_true_random();
	}
      }
    } else {
      internal_error((char *)func);
    }
  }
}

Centroid *allocate_centroid (int l) {
  Centroid *c;

  if ((c = (Centroid *) malloc(sizeof(Centroid))) == NULL) out_of_mem();
  if ((c->el = (double *) malloc(sizeof(double)*(l+1))) == NULL) out_of_mem();
  /* new */
  if ((c->log0 = (double *) malloc(sizeof(double)*(l+1))) == NULL) out_of_mem();
  if ((c->log1 = (double *) malloc(sizeof(double)*(l+1))) == NULL) out_of_mem();
  c->l = l;
  c->weight = 0.0;
  return c;
}

InfCentroid *allocate_centroids (int k, int l) {
  int i;
  Centroid **t;
  InfCentroid *C;

  if ((C = (InfCentroid *) malloc(sizeof(InfCentroid))) == NULL) out_of_mem();
  if ((t = malloc(k*sizeof(void *))) == NULL) out_of_mem();
  for (i=0;i<k;i++) t[i] = allocate_centroid(l);  
  C->el = t;
  C->k = k;
  C->SC = 0.0;
  C->I = 0.0;
  C->I2 = 0.0;
  return C;
}

void add_centroid (InfCentroid *C) {
  int k,l,i;
  Centroid **t;
  Centroid **el;


  k = C->k+1;
  l = C->el[1]->l;

  el = C->el;
  if ((t = malloc(k*sizeof(void *))) == NULL) out_of_mem();
  for (i=0;i<(k-1);i++) t[i] = el[i];
  t[k-1] = allocate_centroid(l);
  C->el = t;
  C->k = k;
  free(el);
}

void remove_centroid (InfCentroid *C, int idx) {
  int k,i;
  Centroid **t;
  Centroid **el;

  k = C->k;

  el = C->el;
  if ((t = malloc((k-1)*sizeof(void *))) == NULL) out_of_mem();
  for (i=0;i<idx;i++) t[i] = el[i];
  deallocate_centroid(el[idx]);
  for (i=idx;i<(k-1);i++) t[i] = el[i+1];
  C->el = t;
  C->k = k-1;
  free(el);
}

void deallocate_centroid (Centroid *c) {
  free(c->el);
  free(c->log1);
  free(c->log0);
  free(c);
}

void deallocate_centroids (InfCentroid *C) {
  int i,k;
  Centroid **t;
  const char *func = "deallocate_centroids";
  
  if (C == NULL) internal_error((char *)func);
  k = C->k;
  t = C->el;
  for (i=0;i<k;i++) deallocate_centroid(t[i]);
  free(C->el);
  free(C);
}

int valid_number_format (char *s, int offs) {
  if (!((s[offs] == '0') || (s[offs] == '1'))) return FALSE;
  if (s[offs+7] != ' ') return FALSE;
  if (s[offs+1] != '.') return FALSE;
  if ((s[offs+2] < '0') || (s[offs] > '9')) return FALSE;
  if ((s[offs+3] < '0') || (s[offs] > '9')) return FALSE;
  if ((s[offs+4] < '0') || (s[offs] > '9')) return FALSE;
  if ((s[offs+5] < '0') || (s[offs] > '9')) return FALSE;
  if ((s[offs+6] < '0') || (s[offs] > '9')) return FALSE;

  return TRUE;
}

InfCentroid *load_centroids (FILE *f) {
  int k,l,i,j,max,offs;
  char *s;
  char *b;
  InfCentroid *C;
  Centroid *t;
  
  /* Get number and length of the centroids */
  if ((s = (char *) malloc (20*sizeof(char))) == NULL) out_of_mem();
  read_line(f,s,20);
  if (feof(f)) return NULL;
  k = atoi(s)+1;
  if (k < 2) return NULL;
  read_line(f,s,20);
  if (feof(f)) return NULL;
  l = atoi(s)+1;
  if (l < 2) return NULL;
  free(s);
  /* put_mark; */

  kstart = k;
  kstop = k;
  max_iter = 1;
  max = 8*l+13;
  if ((s = (char *) malloc (max*sizeof(char))) == NULL) out_of_mem();
  if ((b = (char *) malloc (13*sizeof(char))) == NULL) out_of_mem();
  /* Allocate space for centroids */
  C = allocate_centroids(k,l);
  if (trashcan) {
    t = C->el[0];
    t->l = l;
    for(j=1;j<l;j++) t->el[j] = 0.5;
  }
  for (i=1;i<k;i++)	{
    read_line(f,s,max);
    if (feof(f)) return NULL;
    t = C->el[i];
    l = (t->l);
    for (j=1;j<l;j++) {
      offs = (j-1)*8;
      /* if (!valid_number_format(s,offs)) return NULL; */
      strncpy(b,&s[offs],7);
      b[7] = 0;
      t->el[j] = atof(b);
    }
    offs = (l-1)*8;
    if (s[offs+1] != '.') return NULL;
    strncpy(b,&s[offs],12);
    b[12] = 0;
    t->weight = atof(b);
    /* put_dot; */
  }
  /* put_mark; */
  free(s);
  free(b);
  calculate_logs(C);
  return C;
}

void save_centroids (FILE *f, InfCentroid *C) {
  int k,i,j,l;
  Centroid *t;
  const char *func = "save_centroids";
  
  if (C == NULL) internal_error((char *)func);
  k = (C->k);
  l = (C->el[1]->l);
  fprintf(f,"%d\n",(k-1));
  fprintf(f,"%d\n",(l-1));
  for (i=1;i<k;i++)	{
    if (C->el[i] != NULL) {
      t = C->el[i];
      for (j=1;j<l;j++) {
	fprintf(f,"%1.5f",t->el[j]);
	fputc(' ',f);
      }
      fprintf(f,"%1.10f",t->weight);
      fprintf(f,"\n");
    } else {
      internal_error((char *)func);
    }
  }
  fprintf(f,"\n");
}

void normal_centroids (int k, int l, InfCentroid *C) {
/*Draw Random Centroids for starting points*/
  const char *func = "normal_centroids";
  int i,j;
  Centroid *t;
  
  if (C == NULL) internal_error((char *)func);
  if (trashcan) {
    t = C->el[0];
    t->l = l;
    for(j=1;j<l;j++) t->el[j] = 0.5;
  }
  for (i=1;i<k;i++)	{
    if (C->el[i] != NULL) {
      t = C->el[i];
      t->l = l;
      for (j=1;j<l;j++) {
	t->el[j] = (double) give_true_random();
      }
    } else {
      internal_error((char *)func);
    }
  }
  C->k = k;
}

double edistance_1 (double *x, double *y, int l) {
  int i;
  double d;
  
  d = 0;
  for (i=1;i<l;i++) {
    d += fabs((x[i])-(y[i]));
  }
  return d;
}

double edistance_2 (double *x, double *y, int l) {
  int i;
  double d,t;
  
  d = 0;
  for (i=1;i<l;i++) {
    t = x[i]-y[i];
    d += (t*t);
  }
  return d;
}

void pnn_centroids (int k, int l, InfCentroid *C, ST *V) {
  /* Calculate Random Centroids for starting points */
  /* Use Pairvise Nearest Neighbour algorithm */
  /* SLOOOW!! */
  const char *func = "pnn_centroids";
  Matrix *M;
  int n,j,i;
  int jmin = 1;
  int imin = 1;
  double dmin;
  double d = 0.0;
  ST *W;
  Centroid *t;
  
  if (V == NULL) internal_error((char *)func);
  n = size(V);
  if (C == NULL) internal_error((char *)func);
  M = allocate_dmatrix(n,l);
  W = V;
  for (i=1;i<n;i++) {
    for (j=1;j<l;j++) {
      M->el[i]->el[j] = W->el->el[j];
    }
    W = next_element(W);
  }
  dmin = l+1;
  /* loop until we have desired amount of centroids */
  while (n >= k) {
    /* shearch nearest pair */
    for (i=1;i<n;i++) {
      for (j=(i+1);j<n;j++) {
	if (i != j) {
	  if (distance_type == DT_L1) d = edistance_1(M->el[i]->el,M->el[j]->el,l);
	  else if (distance_type == DT_L2) d = edistance_2(M->el[i]->el,M->el[j]->el,l);
	  if (d < dmin) {
	    dmin = d;
	    jmin = j;
	    imin = i;
	  }
	}
      }
    }
    /* combine pair and reduce */
    for (i=1;i<l;i++) {
      M->el[imin]->el[i] = ((M->el[imin]->el[i] + M->el[jmin]->el[i]) / 2.0);
    }
    M->el[jmin] = M->el[n];
    free(M->el[n]);
    n--;
    dmin = l+1;
    put_dot;
  }
  /* set centroids */
  for (i=1;i<k;i++) {
    if (C->el[i] != NULL) {
      t = C->el[i];
      t->l = l;
      if (M->el[i] == NULL) internal_error((char *)func);
      for (j=1;j<l;j++) {
	t->el[j] = (M->el[i]->el[j]);
      }
      free(M->el[i]);
    } else {
      internal_error((char *)func);
    }
  }
  free(M->el[0]);
  free(M);
}


void pnn_centroids_rand (int k, int l, InfCentroid *C, ST *V) {
  /* Calculate Random Centroids for starting points */
  /* Use Pairvise Nearest Neighbour algorithm */
  /* SLOOOW!! */
  const char *func = "pnn_centroids";
  Matrix *M;
  int n,j,i,imin;
  int jmin = 1;
  double d,dmin,r;
  ST *W;
  Centroid *t;
  
  if (V == NULL) internal_error((char *)func);
  n = size(V);
  if (C == NULL) internal_error((char *)func);
  M = allocate_dmatrix(n,l);
  W = V;
  for (i=1;i<n;i++) {
    for (j=1;j<l;j++) {
      M->el[i]->el[j] = W->el->el[j];
    }
    W = next_element(W);
  }
  dmin = l+1;
  /* loop until we have desired amount of centroids */
  while (n >= k) {
    /* shearch nearest pair for random vector */
    r = give_true_random();
    imin = (int) (r * (double) n);
    if (imin < 1) imin = 1;
    if (imin > n) imin = n;
    for (j=1;j<n;j++) {
      if (imin != j) {
	d = edistance_2(M->el[imin]->el,M->el[j]->el,l);
	if (d < dmin) {
	  dmin = d;
	  jmin = j;
	}
      }
    }
    /* combine pair and reduce */
    for (i=1;i<l;i++) {
      M->el[imin]->el[i] = ((M->el[imin]->el[i] + M->el[jmin]->el[i]) / 2.0);
    }
    if (jmin != n) {
      for (i=1;i<l;i++) {
	M->el[jmin]->el[i] = M->el[n]->el[i];
      }
    }
    free(M->el[n]);
    n--;
    dmin = l+1;
  }
  /* set centroids */
  for (i=1;i<k;i++) {
    if (C->el[i] != NULL) {
      t = C->el[i];
      t->l = l;
      if (M->el[i] == NULL) internal_error((char *)func);
      for (j=1;j<l;j++) {
	t->el[j] = (M->el[i]->el[j]);
      }
      free(M->el[i]);
    } else {
      internal_error((char *)func);
    }
  }
  free(M->el[0]);
  free(M->el);
  free(M);
}


void pick_centroids (int k, int l, InfCentroid *C, ST *V) {
  /* Draw Random Centroids for starting points */
  const char *func = "pick_centroids";
  int i,j,n,ind;
  Centroid *t;
  BV *x;
  ST *W;

  if (V == NULL) internal_error((char *)func);
  n = size(V);
  if (C == NULL) internal_error((char *)func);
  if (trashcan) {
    t = C->el[0];
    t->l = l;
    for(j=1;j<l;j++) t->el[j] = 0.5;
  }
  i=1;
  W = NULL;
  while (i<k) {
    ind = random_index(n);
    x = get_vector_i(V,ind);
    if ((x != NULL) && (!is_in_set(x,W))) {
      i++;
      W = add_element(W,x);
    }
  }
  i=0;
  while (elements_left(W)) {
    i++;
    if (C->el[i] != NULL) {
      t = C->el[i];
      t->l = l;
      x = get_element(W);
      for (j=1;j<l;j++) t->el[j] = (1.0 + (double) (x->el[j])) / 3.0;
      W = del_element(W);
    } else {
      internal_error((char *)func);
    }
  }
  C->k = k;
}

void statistical_centroids (int k, int l, InfCentroid *C) {
  /* Draw Random Centroids for starting points, use statistics */
  const char *func = "statistical_centroids";
  int i,j;
  Centroid *t;
  
  if (C == NULL) internal_error((char *)func);
  if (trashcan) {
    t = C->el[0];
    t->l = l;
    for(j=1;j<l;j++) t->el[j] = 0.5;
  }
  for (i=1;i<k;i++)	{
    if (C->el[i] != NULL) {
      t = C->el[i];
      t->l = l;
      for (j=1;j<l;j++) {
	t->el[j] = (double) give_stat_random(j);
      }
    } else {
      internal_error((char *)func);
    }
  }
  C->k = k;
}

void semi_random_centroids (int k, int l, InfCentroid *C) {
/*Draw Random Centroids for starting points*/
  const char *func = "semi_random_centroids";
  int i,j,a;
  double p;
  Centroid *t;
  
  if (C == NULL) internal_error((char *)func);
  if (trashcan) {
    t = C->el[0];
    t->l = l;
    for(j=1;j<l;j++) t->el[j] = 0.5;
  }
  for (i=1;i<k;i++)	{
    if (C->el[i] != NULL) {
      t = C->el[i];
      t->l = l;
      for (j=1;j<l;j++) {
	t->el[j] = 0.05;
      }
    } else {
      internal_error((char *)func);
    }
  }
  for (j=1;j<l;j++) {
    p = total_freqs[j];
    a = (int)(p * (double)(k-1));
    while (a > 0) {
      i = (int)(give_true_random()*(double)(k));
      C->el[i]->el[j] = 0.95;
      a = (a-1);
    }
  }
  C->k = k;
}

void random_class_weights (int k, InfCentroid *C) {
  int i;
  Centroid *t;
  double s;

  s = 0.0;
  for (i=1;i<k;i++) {
    t = C->el[i];
    t->weight = give_true_random() + 0.05;
    s += t->weight;
  }
  for (i=1;i<k;i++) {
    t = C->el[i];
    t->weight /= s;
  }
}

void random_centroids (int k, int l, InfCentroid *C, ST *V) {
  const char *func = "random_centroids";
  /* random_class_weights(k,C); */
  if (centroid_type == CT_CLASSIC) {
    normal_centroids(k,l,C);
  } else if (centroid_type == CT_SEMI) {
    semi_random_centroids(k,l,C);
  } else if (centroid_type == CT_SRAND) {
    statistical_centroids(k,l,C);
  } else if (centroid_type == CT_PNN) {
    pnn_centroids_rand(k,l,C,V);
  } else if (centroid_type == CT_RAND) {
    pick_centroids(k,l,C,V);
  } else {
    internal_error((char *)func);
  }
  calculate_logs(C);
}

void copy_centroids (InfCentroid *cmin, InfCentroid *C) {
/* Copy Centroids from set of centroids (C) to other (cmin)*/
  const char *func = "copy_centroids";
  int i,j,k,l,si;
  double *t1;
  double *t2;
  
  if (C == NULL) internal_error((char *)func);
  if (cmin == NULL) internal_error((char *)func);
  
  k = (C->k);
  si = (trashcan) ? 0 : 1;
  l = C->el[1]->l;
  for (i=si;i<k;i++)	{
    cmin->el[i]->weight = C->el[i]->weight;
    cmin->el[i]->l = l;
    t1 = cmin->el[i]->el;
    t2 = C->el[i]->el;
    for (j=1;j<l;j++) {
      t1[j] = t2[j];
    }
  }
  cmin->k = k;
  calculate_logs(C);
}

void do_save_centroids (char* parfile, char* ctrfile, char* hdrfile) {
  const char *func = "do_save_centroids";
  FILE *f;
  Partition *P;
  InfCentroid *C;
  int k,l,i,s;
  
  read_header(hdrfile);
  
  if (verbose) fprintf(stdout,"Reading partition ..");
  if ((f = fopen(parfile,"r")) == NULL) file_error(parfile,(char *)func);
  P = read_partition(f,FALSE);
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");
  
  if (verbose) fprintf(stdout,"Calculating ..");
  k = P->k;
  l = vec_len;
  C = allocate_centroids(k,l);
  s = 0;
  for (i=1;i<k;i++) s += size(P->el[i]);
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,s);
  }
  if (verbose) fprintf(stdout,".. ok\n");
  
  if (verbose) fprintf(stdout,"Saving ..");
  if ((f = fopen(ctrfile,"w")) == NULL) file_error(ctrfile,(char *)func);
  save_centroids(f,C);
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");
}

/* end of centroid.c */
