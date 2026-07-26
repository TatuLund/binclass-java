
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
#include "distmin.h"

ST *alg1_init (Partition *P, ST *V, int k, int t) {
  BV *x;
  int i,ind;
  ST *C = NULL;

  i=1;
  while (i<k) {
    ind = random_index(t);
    x = get_vector_i(V,ind);
    if ((x != NULL) && (!is_in_set(x,C))) {
      i++;
      C = add_element(C,x);
      V = del_vector_i(V,ind);
    }
  }

  i=0;
  while(elements_left(C)) {
    i++;
    x = get_element(C);
    C = del_element(C);
    P->el[i] = add_element(P->el[i],x);
  }

  return V;
}

double alg1_distance (BV *x, ST *V) {
  int d,t;
  BV *y;

  d = 0;
  t = 0;
  while (elements_left(V)) {
    t++;
    y = get_element(V);
    d += bv_dist(x,y);
    V = next_element(V);
  }
  return (((double)d) / ((double)t));
}

void alg1_step1 (Partition *P, ST *V, int k) {
  double d,dmin;
  int i,imin;
  BV *x;

  while (elements_left(V)) {
    x = get_element(V);
    dmin = alg1_distance(x,P->el[1]);
    imin = 1;
    for (i=2;i<k;i++) {
      d = alg1_distance(x,P->el[i]);
      if (d < dmin) {
	dmin = d;
	imin = i;
      }
    }
    P->el[imin] = add_element(P->el[imin],x);
    V = del_element(V);
  }
}

int alg1_step2 (Partition *P, int k) {
  double d,dmin;
  int j,i,imin,c,l;
  BV *x;
  ST *N = NULL;
  ST *O = NULL;
  
  l = vec_len;
  c = 0;
  for (j=1;j<k;j++) {
    if (size(P->el[j]) > 2) {
      O = NULL;
      N = NULL;
      O = copy_set(P->el[j]);
      while (elements_left(O)) {
	x = get_element(O);
	O = del_element(O);
	
	imin = j;
	dmin = alg1_distance(x,P->el[j]);

	for (i=1;i<k;i++) {
	  if (i != j) {
	    d = alg1_distance(x,P->el[i]);
	    
	    if (d < dmin) {
	      dmin = d;
	      imin = i;
	    }
	  }
	}
	
	if (imin != j) {
	  c++;
	  P->el[imin] = add_element(P->el[imin],x);
	} else {
	  N = add_element(N,x);
	}
      }
      deallocate_set(P->el[j]);
      P->el[j] = N;     
    }
  }
  return c;
}


Partition *alg1 (FILE *o, ST *V, int k, int t) {
  Partition *P;
  int c,i;

  P = allocate_partition(k);
  if (verbose) fprintf(stdout,"Initializing: ");
  V = alg1_init(P,V,k,t);
  put_dot;
  alg1_step1(P,V,k);
  put_dot;

  fprintf(o,"Size:   %d\nK:      %d\nD:      %d\n",t,k-1,vec_len-1);
  fflush(o);
  fprintf(o,"Init:   SC = %1.4f\n",stochastic_complexity(P,k,vec_len)); 

  fprintf(o,"Step 2: ");
  fflush(o);

  c = 1;
  i = 0;
  if (verbose) fprintf(stdout," ok!\nRunning 1: ");
  while (c != 0) {
    i++;
    c = alg1_step2(P,k);
    put_dot;
  }

  fprintf(o,"SC = %1.4f\n",stochastic_complexity(P,k,vec_len));
  fprintf(o,"        %d iterations\n",i);
  fflush(o);

  if (verbose) fprintf(stdout," ok!\n");
  return P;
}

Partition *alg1_rs (Partition *P, int k) {
  Partition *Pn;
  ST *V;
  BV *x;
  int c1,c2,tc,ind;

  Pn = copy_partition(P);

  c1 = random_index(k-1);
  c2 = random_index(k-1);
  tc = size(P->el[c2]);

  x = NULL;
  while (x == NULL) {
    ind = random_index(tc);
    x = get_vector_i(Pn->el[c2],ind);
  }
  Pn->el[c2] = del_vector_i(Pn->el[c2],ind);

  V = Pn->el[c1];
  Pn->el[c1] = NULL;
  Pn->el[c1] = add_element(Pn->el[c1],x);

  alg1_step1(Pn,V,k);

  return Pn;
}

Partition *alg1_enhance (FILE *o, Partition *P, int k) {
  Partition *Pn;
  int c,j,s;
  double sc,scn;
 
  if (verbose) fprintf(stdout,"Running 2: ");
  fprintf(o,"Step 3: ");
  fflush(o);

  s = 0;
  for (j=1;j<t1_rs_count;j++) {
    sc = stochastic_complexity(P,k,vec_len);
    Pn = alg1_rs(P,k);
    if (t1_extra_iter) c = alg1_step2(Pn,k);

    scn = stochastic_complexity(Pn,k,vec_len);
    if (scn < sc) {
      deallocate_partition(P);
      P = Pn;
      Pn = NULL;
      s++;
      fprintf(o,"SC = %1.4f\n        ",scn);
      fflush(o);
    } else {
      deallocate_partition(Pn);
      Pn = NULL;
    }
    if ((j % (t1_rs_count / 10)) == 0) put_dot;
  }
  sc = stochastic_complexity(P,k,vec_len);
  fprintf(o,"%d successful swaps\nFinal:  SC = %1.4f\n\n",s,sc);
  fflush(o);

  if (verbose) fprintf(stdout," ok!\n");
  return P;
}

void apply_alg1 (char *datfile, char* outfile, char *parfile, char *hdrfile) {
  const char *func = "apply_alg1";
  time_t tm;
  ST *V;
  FILE *f;
  FILE *o;
  int t,i;
  Partition *P;
  double sc,scmin;

  tm = time(&tm);
  set_rand(tm);
  
  /* Read input data */
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..");
  V = read_set(f,hdrfile);
  fclose(f);
  t = size(V);
  if (verbose) fprintf(stdout,".. read %d vectors of data\n",t);

  if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
  start_text(o);
  fflush(o);
  scmin = unassigned_sc();
  log2_factorials = prepare_log2_factorials(t+t);

  for(i=1;i<t1_trials;i++) {
    P = alg1(o,V,kstart,size(V));
    P = alg1_enhance(o,P,kstart);

    sc = stochastic_complexity(P,kstart,vec_len);
    if (sc < scmin) {
      if ((f = fopen(parfile,"w")) == NULL) file_error(parfile,(char *)func);
      inf_write_partition(f,P);
      fclose(f);
    }

    V = partition_to_set(P);
    deallocate_partition(P);
  }

  fclose(o);
  
}
