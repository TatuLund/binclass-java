
#include <stdio.h>
#include <stdlib.h>

#include "const.h"
#include "vars.h"
#include "binset.h"
#include "bottom.h"
#include "format.h"
#include "mixture.h"
#include "adding.h"
#include "vectors.h"
#include "centroid.h"
#include "glainf.h"
#include "distmin.h"

Partition *do_simple_int (Partition *P1, Partition *P2) {
  Partition *P;
  int k1,k2,j,i;
  ST *X1;

  k1 = P1->k;
  k2 = P2->k;
  P = allocate_partition(k1);
  for (i=1;i<k1;i++) {
    X1 = P1->el[i];
    while (elements_left(X1)) {
      for (j=1;j<k2;j++) if (is_in_set(get_element(X1),P2->el[j])) P->el[i] = add_element(P->el[i],get_element(X1));
      X1 = next_element(X1);
    }
    put_dot;
  }
  trim_cluster(P);
  return P;
}

Partition *do_min_int (Partition *P1, Partition *P2) {
  Partition *P;
  int k1,k2,j,i;
  ST *X1;
  IntVector *S;
  int *s;
  int nm,max,J;

  k1 = P1->k;
  k2 = P2->k;
  P = allocate_partition(k1);
  S = allocate_ivector(k2);
  s = S->el;
  for (i=1;i<k1;i++) {

    /* count match */
    X1 = P1->el[i];
    for (j=1;j<k2;j++) s[j] = 0;
    while (elements_left(X1)) {
      for (j=1;j<k2;j++) if (is_in_set(get_element(X1),P2->el[j])) s[j]++;
      X1 = next_element(X1);
    }

    /* check maximal match */
    max = s[1];
    J = 1;
    for (j=2;j<k2;j++) {
      if (s[j] > max) {
	max = s[j];
	J = j;
      }
    }

    /* check number of maximal matches */
    nm = 0;
    for (j=1;j<k2;j++) if (s[j] == max) nm++;

    /* perform intersection from class of maximal match if it was unique */
    if (nm == 1) {
      X1 = P1->el[i];
      while (elements_left(X1)) {
	if (is_in_set(get_element(X1),P2->el[J])) P->el[i] = add_element(P->el[i],get_element(X1));
	X1 = next_element(X1);
      }
    }

    put_dot;

  }
  deallocate_ivector(S);
  trim_cluster(P);
  return P;
}

Partition *do_max_int (Partition *P1, Partition *P2) {
  Partition *P;
  int k1,k2,j,i;
  ST *X1;
  IntVector *S;
  int *s;
  int max,k;

  k1 = P1->k;
  k2 = P2->k;
  k = 0;
  P = allocate_partition(k1*k2);
  S = allocate_ivector(k2);
  s = S->el;
  for (i=1;i<k1;i++) {

    /* count match */
    X1 = P1->el[i];
    for (j=1;j<k2;j++) s[j] = 0;
    while (elements_left(X1)) {
      for (j=1;j<k2;j++) if (is_in_set(get_element(X1),P2->el[j])) s[j]++;
      X1 = next_element(X1);
    }

    /* check maximal match */
    max = s[1];
    for (j=2;j<k2;j++) if (s[j] > max) max = s[j];

    /* perform intersection from class of each maximal match */
    for (j=1;j<k2;j++) {
      if (max == s[j]) {
	k++;
	X1 = P1->el[i];
	while (elements_left(X1)) {
	  if (is_in_set(get_element(X1),P2->el[j])) P->el[k] = add_element(P->el[k],get_element(X1));
	  X1 = next_element(X1);
	}
      }
    }

    put_dot;

  }
  P->k = k+1;
  deallocate_ivector(S);
  trim_cluster(P);
  return P;
}

Partition *do_int_1 (Partition *P1, Partition *P2) {
  Partition *C1;
  Partition *C2;
  Partition *P;
  ST *X;
  int k1,k2,i,j;
  BV *x;

  if (verbose) fprintf(stdout,"\nIntersecting\npass 1 ");
  C1 = do_min_int(P1,P2);
  if (verbose) fprintf(stdout,"\npass 2 ");
  C2 = do_min_int(P2,P1);
  if (verbose) fprintf(stdout,"\npass 3 ");
  k1 = C1->k;
  k2 = C2->k;
  P = allocate_partition(k1);
  for (i=1;i<k1;i++) {
    X = C1->el[i];
    for (j=1;j<k2;j++) {
      x = X->el;
      if (is_in_set(x,C2->el[j])) P->el[i] = C2->el[j];
    }
    put_dot;
  }
  trim_cluster(P);
  return P;
}

Partition *do_int_2 (Partition *P1, Partition *P2) {
  Partition *C1;
  Partition *C2;
  Partition *P;
  ST *X;
  int k1,k2,i,j;
  BV *x;

  if (verbose) fprintf(stdout,"\nIntersecting\npass 1 ");
  C1 = do_max_int(P1,P2);
  if (verbose) fprintf(stdout,"\npass 2 ");
  C2 = do_max_int(P2,P1);
  if (verbose) fprintf(stdout,"\npass 3 ");
  k1 = C1->k;
  k2 = C2->k;
  P = allocate_partition(k1);
  for (i=1;i<k1;i++) {
    X = C1->el[i];
    for (j=1;j<k2;j++) {
      x = X->el;
      if (is_in_set(x,C2->el[j])) P->el[i] = C2->el[j];
    }
    put_dot;
  }
  trim_cluster(P);
  return P;
}

Partition *do_int_analyse1 (ST *X) {
  Partition *P;
  Partition *P1;
  Partition *P2;
  int g,n,k,l,t,tn,i,j,c;
  ST *V1;
  ST *V2;
  double d,sc;
  InfCentroid *C;
  time_t stm;

  stm = time(&stm);
  set_rand(stm);

  use_class_weights = TRUE;
  ls_heuristic_cycler = TRUE;
  ls_heuristic_count = 500;
  k = kstart;
  l = vec_len;
 
  n = size(X);
  prepare_log2_factorials(n+n);

  V1 = copy_set_fast(X);
  V2 = copy_set_fast(X);

  /* generate two partitions */
  if (verbose) fprintf(stdout,"\nInit:\nClassifying ");
  P1 = allocate_partition(k);
  P2 = allocate_partition(k);
  C = allocate_centroids(k,l);

  random_centroids(k,l,C,X);    
  g = MSE_gla2(V1,P1,C,&d,n);
  sc = stochastic_complexity(P1,k,l);
  g += local_search(NULL,P1,C,sc,&d,k,l,n);
  sc = stochastic_complexity(P1,k,l);
  if (verbose) fprintf(stdout,"\nSC(P1) = %1.4f (%1.4f)\nClassifying ",sc,d);

  random_centroids(k,l,C,X);    
  g = MSE_gla2(V2,P2,C,&d,n);
  sc = stochastic_complexity(P2,k,l);
  g += local_search(NULL,P2,C,sc,&d,k,l,n);
  sc = stochastic_complexity(P2,k,l);
  if (verbose) fprintf(stdout,"\nSC(P2) = %1.4f (%1.4f)",sc,d);

  P = do_int_2(P1,P2);
  t = 0;
  for (i=1;i<(P->k);i++) t += size(P->el[i]);
  if (verbose) fprintf(stdout,"\n%d classes, %d elements left\n",P->k-1,t);

  c = TRUE;
  /* iterate 10 times */
  for (i=1;(c && (i<49));i++) {
    if (verbose) fprintf(stdout,"\nIter: %d\nClassifying ",i);
    random_centroids(k,l,C,X);    
    V2 = partition_to_set(P2);
    g = MSE_gla2(V2,P2,C,&d,n);
    sc = stochastic_complexity(P2,k,l);
    g += local_search(NULL,P2,C,sc,&d,k,l,n);
    sc = stochastic_complexity(P2,k,l);
    if (verbose) fprintf(stdout,"\nSC(P%d) = %1.4f (%1.4f)",i+2,sc,d);
    deallocate_partition(P1);
    P1 = copy_partition(P);
    P = do_int_2(P1,P2);
    tn = 0;
    for (j=1;j<(P->k);j++) tn += size(P->el[j]);
    fprintf(stdout,"\n%d classes, %d elements left\n",P->k-1,tn);
    c = (tn != t);
    t = tn;
  }

  return P;
}

void rerandomize_order (Partition **P) {
  int i,j;
  Partition *p;

  for (j=1;j<100;j++) {
    i = random_index(10);
    p = P[1];
    P[1] = P[i];
    P[i] = p;
  }
}

Partition *do_int_analyse2 (ST *X) {
  Partition **P;
  Partition *R;
  Partition *R2;
  int g,n,k,l,t,i,j;
  ST *V;
  double d,sc;
  InfCentroid *C;
  time_t stm;

  stm = time(&stm);
  set_rand(stm);

  use_class_weights = TRUE;
  ls_heuristic_cycler = TRUE;
  ls_heuristic_count = 500;
  k = kstart;
  l = vec_len;
 
  if ((P = (Partition **) malloc(sizeof(void *)*11)) == NULL) out_of_mem();

  n = size(X);
  prepare_log2_factorials(n+n);

  /* generate two partitions */
  C = allocate_centroids(k,l);

  /* iterate 10 times */
  for (i=1;i<11;i++) {
    P[i] = allocate_partition(k);
    if (verbose) fprintf(stdout,"\nIter: %d\nClassifying ",i);
    random_centroids(k,l,C,X);    
    V = copy_set_fast(X);
    g = MSE_gla2(V,P[i],C,&d,n);
    sc = stochastic_complexity(P[i],k,l);
    g += local_search(NULL,P[i],C,sc,&d,k,l,n);
    sc = stochastic_complexity(P[i],k,l);
    if (verbose) fprintf(stdout,"\nSC(P%d) = %1.4f (%1.4f)",i,sc,d);

   }

  for (j=1;j<11;j++) {
    verbose = FALSE;
    rerandomize_order(P);
    R = do_int_2(P[1],P[2]);
    for (i=3;i<11;i++) {
      R2 = copy_partition(R);
      R = do_int_2(R2,P[i]);
    }
    t = 0;
    for (i=1;i<(R->k);i++) t += size(R->el[i]);
    verbose = TRUE;
    if (verbose) fprintf(stdout,"\n%d classes, %d elements left",R->k-1,t);
  }
  if (verbose) fprintf(stdout,"\n");

  return R;
}

void int_partitions (char *parfile, char *datfile, char *parfile1, char *parfile2, char *hdrfile) {
  const char *func = "int_partitions";
  Partition *P1;
  Partition *P2;
  Partition *P;
  ST *X;
  FILE *f;
  int t,i;

  read_header(hdrfile);

  if (analyse_int || analyse_int_stab) {
    if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
    X = read_set(f,hdrfile);
    fclose(f);
  } else {
    if ((f = fopen(parfile1,"r")) == NULL) file_error(parfile1,(char *)func);
    P1 = read_partition(f,FALSE);
    fclose(f);
    if ((f = fopen(parfile2,"r")) == NULL) file_error(parfile2,(char *)func);
    P2 = read_partition(f,FALSE);
    fclose(f);
  }

  if (relative_int) {
    P = do_simple_int(P1,P2);
  } else if (minimal_int) {
    P = do_int_1(P1,P2);
  } else if (maximal_int) {
    P = do_int_2(P1,P2);
  } else if (analyse_int) {
    P = do_int_analyse1(X);
  } else if (analyse_int_stab) {
    P = do_int_analyse2(X);
  } else {
    P = do_min_int(P1,P2);
  }
  t = 0;
  for (i=1;i<(P->k);i++) t += size(P->el[i]);
  if (verbose) fprintf(stdout,"\nResulting partition contains %d classes and %d vectors\n",(P->k-1),t);
  if (verbose) fprintf(stdout,"Saving ..");
  if ((f = fopen(parfile,"w")) == NULL) file_error(parfile,(char *)func);
  inf_write_partition(f,P);
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");
}

/* end of cut.c */
