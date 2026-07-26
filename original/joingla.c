
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>

#include "const.h"
#include "vars.h"
#include "glainf.h"
#include "bottom.h"
#include "vectors.h"
#include "centroid.h"
#include "binset.h"
#include "binstuff.h"
#include "distmin.h"

InfCentroid *FC = NULL;

InfCentroid *set_first_centroids_pnn2 (ST *V) {
  const char *func = "set_first_centroids_pnn2";
  Matrix *M;
  int n,j,i,cont,l;
  int jmin = 1;
  int imin = 1;
  double d,dmin;
  ST *W;
  Centroid *t;
  IntVector *X;
  InfCentroid *C;
  
  if (verbose) fprintf(stdout,"Setting initial centroids: ");
  l = vec_len;
  if (V == NULL) internal_error((char *)func);
  n = size(V);
  M = allocate_dmatrix(n,l);
  X = allocate_ivector(n);
  W = V;
  for (i=1;i<n;i++) {
    X->el[i] = 1;
    for (j=1;j<l;j++) {
      M->el[i]->el[j] = W->el->el[j];
    }
    W = next_element(W);
  }
  dmin = l+1;
  /* loop until we have desired amount of centroids */
  cont = TRUE;
  while (cont) {
    /* shearch nearest pair */
    for (i=1;i<n;i++) {
      for (j=(i+1);j<n;j++) {
	if (i != j) {
	  d = edistance_2(M->el[i]->el,M->el[j]->el,l);
	  if (d < dmin) {
	    dmin = d;
	    jmin = j;
	    imin = i;
	  }
	}
			}
    }
    if (dmin < gla_treshold) {
      /* combine pair and reduce */
      for (i=1;i<l;i++) {
	M->el[imin]->el[i] = (((X->el[imin] * M->el[imin]->el[i]) + (X->el[jmin] * M->el[jmin]->el[i])) / ( + X->el[imin]));
      }
      X->el[imin] = X->el[imin] + X->el[jmin];
      if (jmin != n) {
	for (i=1;i<l;i++) {
	  M->el[jmin]->el[i] = M->el[n]->el[i];
	}
      }
      X->el[n] = 0;
      free(M->el[n]);
      n--;
      dmin = l+1;
    } else {
      cont = FALSE;
    }
    put_dot;
  }
  C = allocate_centroids(n+1,l);
  /* set centroids */
  for (i=1;i<n;i++) {
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
  deallocate_ivector(X);
  if (verbose) fprintf(stdout," ok\n\n");
  return C;
}

InfCentroid *set_first_centroids_pnn (ST *V) {
  const char *func = "set_first_centroids_pnn";
  Matrix *M;
  int n,j,i,cont,l;
  int jmin = 1;
  int imin = 1;
  double d,dmin;
  ST *W;
  Centroid *t;
  InfCentroid *C;
  
  if (verbose) fprintf(stdout,"Setting initial centroids: ");
  l = vec_len;
  if (V == NULL) internal_error((char *)func);
  n = size(V);
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
  cont = TRUE;
  while (cont) {
    /* shearch nearest pair */
    for (i=1;i<n;i++) {
      for (j=(i+1);j<n;j++) {
	if (i != j) {
	  d = edistance_2(M->el[i]->el,M->el[j]->el,l);
	  if (d < dmin) {
	    dmin = d;
	    jmin = j;
	    imin = i;
	  }
	}
      }
		}
    if (dmin < gla_treshold) {
      /* combine pair and reduce */
      for (i=1;i<l;i++) {
	M->el[imin]->el[i] = ((M->el[imin]->el[i] + M->el[jmin]->el[i]) * 0.5);
      }
      if (jmin != n) {
	for (i=1;i<l;i++) {
	  M->el[jmin]->el[i] = M->el[n]->el[i];
	}
      }
      free(M->el[n]);
      n--;
      dmin = l+1;
    } else {
      cont = FALSE;
    }
    put_dot;
  }
  C = allocate_centroids(n+1,l);
  /* set centroids */
  for (i=1;i<n;i++) {
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
  if (verbose) fprintf(stdout," ok\n\n");
  return C;
}

void join_two_classes (InfCentroid *C, Partition *P) {
  int k,j,i,s,l;
  int imin = 1;
  int jmin = 1;
  int *el;
  double d,dmin;
  double *t;
  ST *Vi;
  ST *Vj;
  
  l = vec_len;
  k = C->k;
  dmin = (double)vec_len + 1.0;
  for (i=1;i<k;i++) {
    for (j=(i+1);j<k;j++) {
      if (i != j) {
	d = edistance_2(C->el[i]->el,C->el[j]->el,l);
	if (d < dmin) {
	  dmin = d;
	  jmin = j;
	  imin = i;
	}
      }
    }
  }
  /* Update new centroid (i)*/
  Vi = P->el[imin];
  Vj = P->el[jmin];
  t = C->el[imin]->el;
  for (j=1;j<l;j++) {
    t[j] = 0.0;
  }
  s = 0;
  while (elements_left(Vi)) {
    s++;
    el = get_vector(Vi);
    for (j=1;j<l;j++) t[j] += el[j];
    Vi = next_element(Vi);
  }
  while (elements_left(Vj)) {
    s++;
    el = get_vector(Vj);
    for (j=1;j<l;j++) t[j] += el[j];
    Vj = next_element(Vj);
  }
  for (j=1;j<l;j++) t[j] /= (double) s;
  
  /* remove centroid j */
  if (jmin != (k-1)) for (j=1;j<l;j++) C->el[jmin]->el[j] = C->el[(k-1)]->el[j];
  free(C->el[k-1]);
  k--;
  C->k = k;
}

void join_two_classes_rand (InfCentroid *C, Partition *P) {
  int k,imin,j,s,l;
  int jmin = 1;
  int *el;
  double r,d,dmin;
  double *t;
  ST *Vi;
  ST *Vj;
  
  l = vec_len;
  k = C->k;
  r = give_true_random();
  imin = (int) (r * (double) k);
  if (imin < 1) imin = 1;
  if (imin > (k-1)) imin = k-1;
  dmin = (double)vec_len + 1.0;
  for (j=1;j<k;j++) {
    if (imin != j) {
      d = edistance_2(C->el[imin]->el,C->el[j]->el,l);
      if (d < dmin) {
	dmin = d;
	jmin = j;
      }
    }
  }
  /* Update new centroid (i)*/
  Vi = P->el[imin];
  Vj = P->el[jmin];
  t = C->el[imin]->el;
  for (j=1;j<l;j++) {
    t[j] = 0.0;
  }
  s = 0;
  while (elements_left(Vi)) {
    s++;
    el = get_vector(Vi);
    for (j=1;j<l;j++) t[j] += el[j];
    Vi = next_element(Vi);
  }
  while (elements_left(Vj)) {
    s++;
    el = get_vector(Vj);
    for (j=1;j<l;j++) t[j] += el[j];
    Vj = next_element(Vj);
  }
  for (j=1;j<l;j++) t[j] /= (double) s;

  /* remove centroid j */
  if (jmin != (k-1)) for (j=1;j<l;j++) C->el[jmin]->el[j] = C->el[(k-1)]->el[j];
  free(C->el[k-1]);
  k--;
  C->k = k;
}

ST *join_gla (ST *V, double *scmin, double *scs, char *outfile, char *parfile) {
  const char *func = "join_gla";
  int l,k;
  InfCentroid *C;
  Partition *P;
  double dmin,sc;
  FILE *o = NULL;
  FILE *p;
  
  if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  
  l = vec_len;
  if (FC == NULL) {
    C = set_first_centroids_pnn2(V);
    k = C->k;
    FC = allocate_centroids(k,l);
    copy_centroids(FC,C);
  } else {
    k = FC->k;
    C = allocate_centroids(k,l);
    copy_centroids(C,FC);
    k = C->k;
  }
  P = allocate_partition(k);
  if (verbose) fprintf(stdout,"%d: ",k-1);
  special_gla(V,P,C,&dmin);
  sc = stochastic_complexity(P,k,l);
  if (verbose) fprintf(stdout,"\n  sc = %.4f, d = %.4f\n",sc,dmin);
  if (log_file) fprintf(o,"%5d: sc = %.4f\n",(k-1),sc);
  if (sc < scs[k-1]) scs[k-1] = sc;
  if (sc < *scmin) {
    *scmin = sc;
    if (verbose) fprintf(stdout,"  best so far\n");
    p = fopen(parfile,"w");
    inf_write_partition(p,P);
    fclose(p);
  }
  if (use_abs_match) join_two_classes(C,P);
  else join_two_classes_rand(C,P);
  V = partition_to_set(P);
  deallocate_partition(P);
  while (k > 3) {
    k--;
    P = allocate_partition(k);
    if (verbose) fprintf(stdout,"%d: ",(k-1));
    special_gla(V,P,C,&dmin);
    sc = stochastic_complexity(P,k,l);
    if (verbose) fprintf(stdout,"\n  sc = %.4f, d = %.4f\n",sc,dmin);
    if (log_file) {
      fprintf(o,"%5d: sc = %.4f, d = %.4f",(k-1),sc,dmin);
    }
    if (sc < scs[k-1]) scs[k-1] = sc;
    if (sc < *scmin) {
      *scmin = sc;
      if (verbose) fprintf(stdout,"  best so far\n");
      if (log_file) fprintf(o," B");
      p = fopen(parfile,"w");
      inf_write_partition(p,P);
      fclose(p);
    }
    if (log_file) {
      fprintf(o,"\n");
      fflush(o);
    }
    if (use_abs_match) join_two_classes(C,P);
    else join_two_classes_rand(C,P);
    V = partition_to_set(P);
    deallocate_partition(P);
  }
  fclose(o);
  deallocate_centroids(C);
  return V;
}

void use_join_gla (char *datfile, char *outfile, char *parfile, char *hdrfile) {
  FILE *f;
  FILE *o = NULL;
  ST *V;
  int s,i,replications;
  const char *func = "use_join_gla";
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
    fprintf(o,"\nAlogrithm: Join-GLA\n");
    
    if (use_abs_match) fprintf(o,"  Deterministic version using absolute best match\n\n");
    else fprintf (o,"  Running for %d times\n\n",replications);
    fclose(o);
  }
  
  scmin = (double) vec_len * 2.0;
  
  if ((scs = malloc((s+1)*sizeof(double))) == NULL) out_of_mem();
  for (i=0;i<(s+1);i++) scs[i] = unassigned_sc();
  
  for (i=0;i<replications;i++) {
    if (log_file) {
      if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
      fprintf(o,"--\nrun: %d\n\n",i+1);
      fclose(o);
    }
    if (verbose) fprintf(stdout,"\n--\nrun: %d\n\n",i+1);
    V = join_gla(V,&scmin,scs,outfile,parfile);
    
  }
  
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    i = 2;
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

/* end of joingla.c */
