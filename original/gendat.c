/*
gendat.c - methods for generating data
	Random data:
	- Usual random data, for test purposes, should not distribute into
	  classes, codelength ~ number of elements
	Bernoulis Multivariate Distribution:
	- Generates pseudo data with same statistical profile as with given
	  partition in BMD sense. If partition represents optimal classification
	  generated data should classify similarly. Can generate vectors that
	  does not appear in the input. Weaker than Markov.
	Markov Model:
	- Generates pseudo data with automata build on basis of input data.
	  Pseudo data will have same dependencies as input. Should classify
	  similarly than input data. Cannot generate vectors that does not
	  appear in the input.
	Random Vectors:
	- Generates data by picking random vectors from input. Roughly equal
	  to Markov model generator, but simplier. Picked vectors go to
	  .generated1 and vectors not included to .generated2
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "const.h"
#include "vars.h"
#include "distmin.h"
#include "binset.h"
#include "binstuff.h"
#include "adding.h"
#include "bottom.h"
#include "centroid.h"
#include "format.h"

/* prototypes */

int tosh_clas (int *cumsum, int sum, int k);
ST *bernouli_gen (Partition *P, int amount);
void bernouli_generator (char *parfile, char *genfile, int amount, char *hdrfile);

Automata *new_automata (void);
Automata *add_to_automata (Automata *A, BV *x);
void fix_automata (Automata *A);
ST *markov_gen (ST *V, int amount);
void markov_generator (char *datfile, char *genfile, int amount, char *hdrfile);

ST *vector_gen (ST *V, int amount);
void vector_generator (char *datfile, char *genfile1, char *genfile2, int amount, char *hdrfile);

ST *random_gen (int amount);
void random_generator (char *genfile, int amount, char *hdrfile);

/* implementation */

int tosh_clas (int *cumsum, int sum, int k) {
  int j,i;
  
  i = (int)(((double)sum) * give_true_random());
  j = 1;
  while ((cumsum[j] < i) && (j<k)) j++;
  return j;
}

void bernouli_generator (char *parfile, char *genfile, int amount, char *hdrfile) {
  const char *func = "bernouli_generator";
  FILE *f;
  int i;
  Partition *P;
  BV *x;
  ST *V;
  
  read_header(hdrfile);
  
  if ((f = fopen(parfile,"r")) == NULL) file_error(parfile,(char *)func);
  P = read_partition(f,FALSE);
  fclose(f);
  
  if (verbose) fprintf(stdout,"Generating data ..");
  
  V = bernouli_gen(P,amount);
  
  if ((f = fopen(genfile,"w")) == NULL) file_error(genfile,(char *)func);
  
  amount++;
  for (i=1;i<amount;i++) {
    x = get_element(V);
    pic_write_bv(f,x);
    V = next_element(V);
  }
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");
}

ST *bernouli_gen (Partition *P, int amount) {
  const char *func = "bernouli_gen";
  int *cumsum;
  int clas,sum,i,j,k,l,s,n;
  char bit;
  double r;
  InfCentroid *C;
  ST *V = NULL;
  BV *x;
  Centroid *t;
  time_t stm;
  
  stm = time(&stm);
  set_rand(stm);
  
  k = P->k;
  l = vec_len;
  
  if ((cumsum = (int *) malloc(sizeof(int)*k)) == NULL) out_of_mem();
  C = allocate_centroids(k,l);
  
  /* Centroids represent the distribution within classes */
  if (verbose) fprintf(stdout,".. counting frequencies ..");
  sum = 0;
  cumsum[0] = 0;
  n = 0;
  for (i=1;i<k;i++) n += size(P->el[i]);
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,n);
    s = size(P->el[i]);
    cumsum[i] = cumsum[i-1] + s;
    sum = sum + s;
  }
  
  if (verbose) fprintf(stdout,".. %d vectors in %d classes ..",sum,(k-1));
  
  if (verbose) fprintf(stdout,".. generating ..");
  amount++;
  
  for (i=1;i<amount;i++) {
    x = bv_allocate();
    x->length = l;
    bv_set_name(x,"BERN DATA");
    bv_set_id(x,"0000-00");
    clas = tosh_clas(cumsum,sum,k);
    if (clas < k) {
      t = C->el[clas];
      for (j=1;j<l;j++) {
	r = give_true_random();
	if (r < (t->el[j])) bit = 1;
	else bit = 0;
	x->el[j] = bit;
      }
      V = add_element(V,x);
    } else {
      internal_error((char *)func);
    }
  }
  free(cumsum);
  deallocate_centroids(C);
  return V;
}

Automata *new_automata (void) {
  Automata *A;
  if ( (A = (Automata *) malloc(sizeof(Automata))) == NULL ) out_of_mem();
  A->zero_prob = 0.0;
  A->one_prob = 0.0;
  A->zero = NULL;
  A->one = NULL;
  return A;
}

Automata *add_to_automata (Automata *A, BV *x) {
  int l,i;
  Automata *tmp;
  
  if (A == NULL) {
    A = new_automata();
  }
  tmp = A;
  l = (x->length);
  /*
     if (x->el[1]) {
     tmp->one_prob = ((tmp->one_prob)+1.0);
     } else {
     tmp->zero_prob = ((tmp->zero_prob)+1.0);
     }
     */
  for (i=1;i<l;i++) {
    if (x->el[i]) {
      if ((tmp->one) == NULL) tmp->one = new_automata();
      tmp->one_prob = ((tmp->one_prob)+1.0);
      tmp = tmp->one;
    } else {
      if ((tmp->zero) == NULL) tmp->zero = new_automata();
      tmp->zero_prob = ((tmp->zero_prob)+1.0);
      tmp = tmp->zero;
    }
  }
  return A;
}

void fix_automata (Automata *A) {
  double n;
  
  if (A != NULL) {
    n = (A->one_prob) + (A->zero_prob);
    if (n < 1.0) n = 1.0;
    A->one_prob = ((A->one_prob) / ((double) n));
    A->zero_prob = ((A->zero_prob) / ((double) n));
  }
  if (A->one != NULL) fix_automata(A->one);
  if (A->zero != NULL) fix_automata(A->zero);
}

void markov_generator (char *datfile, char *genfile, int amount, char *hdrfile) {
  const char *func = "markov_generator";
  ST *V;
  ST *W;
  BV *x;
  int i;
  FILE *f;
  
  read_header(hdrfile);
  
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  V = read_set(f,hdrfile);
  fclose(f);
  
  if (verbose) fprintf(stdout,"Generating data ..");
  
  W = markov_gen(V,amount);
  
  if ((f = fopen(genfile,"w")) == NULL) file_error(genfile,(char *)func);
  amount++;
  for (i=1;i<amount;i++) {
    x = get_element(W);
    pic_write_bv(f,x);
    W = next_element(W);
  }
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");
}

ST *markov_gen (ST *V, int amount) {
  const char *func = "markov_gen";
  time_t stm;
  int l,j,bit,i;
  BV *x;
  ST *W = NULL;
  double r;
  Automata *A = NULL;
  Automata *tmp;
  
  stm = time(&stm);
  set_rand(stm);
  
  if (verbose) fprintf(stdout,".. generating automata ..");
  
  while (elements_left(V)) {
    x = get_element(V);
    A = add_to_automata(A,x);
    V = next_element(V);
  }
  
  if (verbose) fprintf(stdout,".. fixing ..");
  fix_automata(A);
  
  /* use automata */
  
  l = vec_len;
  if (verbose) fprintf(stdout,".. running ..");
  amount++;
  for (i=1;i<amount;i++) {
    tmp = A;
    x = bv_allocate();
    x->length = l;
    bv_set_name(x,"MARC PROC");
    bv_set_id(x,"0000-00");
    
    for (j=1;j<l;j++) {
      r = give_true_random();
      if (r < (tmp->one_prob)) bit = 1;
      else bit = 0;
      x->el[j] = bit;
      if (bit && (tmp->one != NULL)) {
	tmp = tmp->one;
      } else if (tmp->zero != NULL) {
	tmp = tmp->zero;
      } else {
	internal_error((char *)func);
      }
    }
    W = add_element(W,x);
  }
  return W;
}

void vector_generator (char *datfile, char *genfile1, char *genfile2, int amount, char *hdrfile) {
  const char *func = "vector_generator";
  ST *V;
  ST *W;
  ST *tmp;
  BV *x;
  int i;
  FILE *f;
  
  read_header(hdrfile);
  
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  V = read_set(f,hdrfile);
  fclose(f);
  
  if (verbose) fprintf(stdout,"Generating data ..");
  
  W = vector_gen(V,amount);
  
  tmp = W;
  if ((f = fopen(genfile1,"w")) == NULL) file_error(genfile1,(char *)func);
  amount++;
  for (i=1;i<amount;i++) {
    x = get_element(tmp);
    pic_write_bv(f,x);
    tmp = next_element(tmp);
  }
  fclose(f);
  if ((f = fopen(genfile2,"w")) == NULL) file_error(genfile2,(char *)func);
  while (elements_left(V)) {
    x = get_element(V);
    if (!is_in_set(x,W)) pic_write_bv(f,x);
    V = next_element(V);
  }
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");
}

ST *vector_gen (ST *V, int amount) {
  const char *func = "vector_gen";
  time_t stm;
  ST *W = NULL;
  BV *x;
  int n,i,ind;
  double r;
  
  stm = time(&stm);
  set_rand(stm);
  
  n = size(V);
  
  amount++;
  i = 1;
  while (i<amount) {
    r = give_true_random();
    ind = (int) (r * (double) n);
    if (ind < 1) ind = 1;
    if (ind > n) ind = n;
    x = copy_vector_i(V,ind);
    if (x == NULL) internal_error((char *)func);
    if (unique_vectors) {
      if (!is_in_set(x,W)) {
	W = add_element(W,x);
	i++;
      }
    } else {
      W = add_element(W,x);
      i++;
    }
  }
  return W;
}

void random_generator (char *genfile, int amount, char *hdrfile) {
  const char *func = "random_generator";
  BV *x = NULL;
  int i;
  FILE *f;
  ST *V;
  
  read_header(hdrfile);
  
  V = random_gen(amount);
  
  if ((f = fopen(genfile,"w")) == NULL) file_error(genfile,(char *)func);
  amount++;
  for (i=1;i<amount;i++) {
    x = get_element(V);
    pic_write_bv(f,x);
    V = next_element(V);
  }
  
  fclose(f);
  free(x->el);
  free(x);
}

ST *random_gen (int amount) {
  time_t stm;
  BV *x;
  int i,j,l,bit;
  double r;
  ST *V = NULL;
  
  stm = time(&stm);
  set_rand(stm);
  
  l = vec_len;
  
  if (verbose) fprintf(stdout,"Generating data ..");
  amount++;
  for (i=1;i<amount;i++) {
    x = bv_allocate();
    x->length = l;
    bv_set_name(x,"RAND VECT");
    bv_set_id(x,"0000-00");
    for (j=1;j<l;j++) {
      r = give_true_random();
      bit = (r < 0.5) ? 1 : 0;
      x->el[j] = bit;
    }
    V = add_element(V,x);
  }
  
  if (verbose) fprintf(stdout,".. ok\n");
  return V;
}

/* end of gendat.c */
