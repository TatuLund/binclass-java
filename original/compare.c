/*
compare.c - methods for comparing two given partitions
	Constraints:
	- Partitions must countain exactly same vectors, ie. they are generated
	  from same input
	- IDs of vectors must be unique
	Comparison matrix:
	Distance measures:
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "const.h"
#include "vars.h"
#include "distmin.h"
#include "binset.h"
#include "binstuff.h"
#include "adding.h"
#include "bottom.h"
#include "centroid.h"
#include "vectors.h"
#include "format.h"

IntVector *match_partition (ST *V, Partition *P);
IntMatrix *do_comparison(ST *V, Partition *P1, Partition *P2, FILE *f);
ST *read_set_alpha (FILE *f, char *hdrfile);

double compute_distance (IntMatrix *M);
double calculate_distance (ST *V, Partition *P1, Partition *P2);
void print_comp_matrix (FILE *f, IntMatrix *M);
void comparison_results (FILE *f, IntMatrix *M);
void pair_sort (IntVector *X1, IntVector *X2, int k);
void print_match (IntVector *X1, IntVector *X2, int k, FILE *f);

void compare_partitions (char *datfile, char *parfile1, char *parfile2, char *resfile, char *hdrfile);

IntVector *match_partition (ST *V, Partition *P) {
  const char *func = "match_partition";
  IntVector *X;
  InfCentroid *C = NULL;
  ST *tmp;
  BV *x;
  int n,i,j,k,l,found,s;
  int closest = 1;
  double mindist,dist;
  
  k = P->k;
  l = vec_len;
  if (!exact_matches) {
    C = allocate_centroids(k,l);
    s = 0;
    for (i=1;i<k;i++) s += size(P->el[i]);
    for (j=1;j<k;j++) {
      inf_average(P->el[j],C->el[j],rounded_centroids,s);
    }
    calculate_logs(C);
  }
  n = size(V);
  X = allocate_ivector(n);
  tmp = V;
  i=0;
  while (elements_left(tmp)) {
    x = get_element(tmp);
    if (exact_matches) {
      j=1;
      found = FALSE;
      while ((j<k) && (!found)) {
	found = is_in_set(x,P->el[j]);
	j++;
      }
      if (found) {
	closest = j-1;
      } else {
	internal_error((char *)func);
      }
    } else {
      closest = 1;
      mindist = code_length(x,C->el[1]);
      for (j=2;j<k;j++) {
	dist = code_length(x,C->el[j]);
	if (dist < mindist) {
	  closest = j;
	  mindist = dist;
	}
      }
    }
    X->el[i] = closest;
    tmp = next_element(tmp);
    i++;
  }
  if (!exact_matches) {
    deallocate_centroids(C);
  }
  return X;
}

void pair_sort (IntVector *X1, IntVector *X2, int k) {
/* counting sort - prety kinky one though */
  int n,i,j,x1,x2,h,m,h1;
  int *el1;
  int *el2;
  IntMatrix *M;
  
  M = allocate_imatrix(k,k);
  el1 = X1->el;
  el2 = X2->el;
  n = (X1->l)+1;
  put_dot;
  for (i=0;i<n;i++) {
    x1 = el1[i];
    x2 = el2[i];
    m = M->el[x1]->el[x2];
    m++;
    M->el[x1]->el[x2] = m;
  }
  h1 = 0;
  put_dot;
  for (i=1;i<k;i++) {
    for (j=1;j<k;j++) {
      m = M->el[i]->el[j];
      for (h=0;h<m;h++) {
	el1[h1] = i;
	el2[h1] = j;
	h1++;
      }
    }
  }
  deallocate_imatrix(M);
}

void print_vecs (IntVector *X1, IntVector *X2, FILE *f) {
  int i,n;
  int *el1;
  int *el2;
  
  n = (X1->l)+1;
  el1 = X1->el;
  el2 = X2->el;
  for (i=0;i<n;i++) fprintf(f,"%3d %3d\n",el1[i],el2[i]);
}

void print_match (IntVector *X1, IntVector *X2, int k, FILE *f) {
  int n,i,m1,m2,j;
  int *el1;
  int *el2;
  double p;
  IntVector *I;
  
  n = (X1->l)+1;
  el1 = X1->el;
  el2 = X2->el;
  I = allocate_ivector(k);
  m1 = 1;
  for (i=1;i<n;i++) {
    if (el1[i-1] == el1[i]) m1++;
    else {
      j = el1[i-1];
      I->el[j] = m1;
      m1 = 1;
    }
  }
  put_dot;
  m2 = 1;
  for (i=1;i<n;i++) {
    if ((el1[i-1] == el1[i]) && (el2[i-1] == el2[i])) {
      m2++;
    } else {
      j = el1[i-1];
      p = ((I->el[j]) != 0) ? ((double)(m2)) / ((double)(I->el[j])) : 0.0;
      fprintf(f,"%3d -> %3d: %4d (%.4f)\n",el1[i-1],el2[i-1],m2,p);
      m2 = 1;
      if (el1[i-1] != el1[i]) {
	fprintf(f,"%4d\n--\n",I->el[j]);
      }
    }
  }
  put_dot;
  deallocate_ivector(I);
  fflush(f);
}

double calculate_distance (ST *V, Partition *P1, Partition *P2) {
  IntVector *X1;
  IntVector *X2;
  IntMatrix *M;
  int *el1;
  int *el2;
  int k1,k2,n,i,mk,m;
  double D;
  
  if (verbose) {
    fprintf(stdout,"Computing distance: ");
    fflush(stdout);
  }
  X1 = match_partition(V,P1);
  put_dot;
  X2 = match_partition(V,P2);
  put_dot;
  
  k1 = (P1->k-1);
  k2 = (P2->k-1);
  k1++;
  k2++;
  mk = (k1 > k2) ? k1 : k2;
  
  M = allocate_imatrix((k2*2),k1);
  n = X1->l;
  
  pair_sort(X1,X2,mk);
  put_dot;
  
  el1 = X1->el;
  el2 = X2->el;
  m = 1;
  for (i=1;i<(n+1);i++) {
    if ((el1[i-1] == el1[i]) && (el2[i-1] == el2[i])) {
      m++;
    } else {
      M->el[((el2[i-1])*2)]->el[el1[i-1]] = m;
      m = 1;
    }
  }
  put_dot;
  
  pair_sort(X2,X1,mk);
  put_dot;
  
  el1 = X2->el;
  el2 = X1->el;
  m = 1;
  for (i=1;i<(n+1);i++) {
    if ((el1[i-1] == el1[i]) && (el2[i-1] == el2[i])) {
      m++;
    } else {
      M->el[((el1[i-1])*2)+1]->el[el2[i-1]] = m;
      m = 1;
    }
  }
  put_dot;
  
  deallocate_ivector(X1);
  deallocate_ivector(X2);
  D = compute_distance(M);
  deallocate_imatrix(M);
  if (verbose) fprintf(stdout,"ok\n");
  return D;
}

IntMatrix *do_comparison (ST *V, Partition *P1, Partition *P2, FILE *f) {
  IntVector *X1;
  IntVector *X2;
  IntMatrix *M;
  int *el1;
  int *el2;
  int k1,k2,n,i,mk,m;
  
  start_text(f);
  fprintf(f,"\nCOMPARISON RESULTS\n==================\n");
  fflush(f);
  if (verbose) fprintf(stdout,"\nCounting matching in partitions\n");
  X1 = match_partition(V,P1);
  X2 = match_partition(V,P2);
  
  k1 = (P1->k-1);
  k2 = (P2->k-1);
  k1++;
  k2++;
  if (k1 > k2) mk = k1;
  else mk = k2;
  
  M = allocate_imatrix((k2*2),k1);
  n = X1->l;
  
  if (verbose) {
    fprintf(stdout,"Checking transitions from P1 to P2: sorting ");
    fflush(stdout);
  }
  pair_sort(X1,X2,mk);
  if (print_matches) {
    if (verbose) fprintf(stdout," saving ");
    fprintf(f,"\nTransitions from P1 to P2\n\n");
    print_match(X1,X2,mk,f);
  }
  if (verbose) fprintf(stdout," ok\n");
  
  if (verbose) fprintf(stdout,"Building a comparison matrix, part 1\n");
  el1 = X1->el;
  el2 = X2->el;
  m = 1;
  for (i=1;i<(n+1);i++) {
    if ((el1[i-1] == el1[i]) && (el2[i-1] == el2[i])) {
      m++;
    } else {
      M->el[((el2[i-1])*2)]->el[el1[i-1]] = m;
      m = 1;
    }
  }

  if (verbose) {
    fprintf(stdout,"Checking transitions from P2 to P1: sorting ");
    fflush(stdout);
  }
  pair_sort(X2,X1,mk);
  if (print_matches) {
    if (verbose) fprintf(stdout," saving ");
    fprintf(f,"\n\nTransitions from P2 to P1\n\n");
    print_match(X2,X1,mk,f);
  }
  fprintf(stdout," ok\n");
  
  
  if (verbose) fprintf(stdout,"Building a comparison matrix, part 2\n");
  el1 = X2->el;
  el2 = X1->el;
  m = 1;
  for (i=1;i<(n+1);i++) {
    if ((el1[i-1] == el1[i]) && (el2[i-1] == el2[i])) {
      m++;
    } else {
      M->el[((el1[i-1])*2)+1]->el[el2[i-1]] = m;
      m = 1;
    }
  }
  
  deallocate_ivector(X1);
  deallocate_ivector(X2);
  return M;
}

ST *read_set_alpha (FILE *f,char *hdrfile) {
  /* read set V from input f */
  /* if exists m read info on missing data too */
  /* output goes to o and stdout */
  char *xs;
  BV *x;
  ST *V = NULL;
  int l,i,sl,n;
  
  read_header(hdrfile);
  
  if ( (xs = (char *) malloc(sizeof(char)*MAX_LENGTH)) == NULL ) out_of_mem();
  sl = vec_len;
  n = 0;
  while (!feof(f)) {
    read_line(f,xs,MAX_LENGTH);
    if (!feof(f)) {
      n++;
      /* allocate space for new vector */
      x = bv_allocate();
      x->num = n;
      for (i=1;i<sl;i++) x->miss[i] = 0;
      /* file empty spaces */
      l = strlen(&xs[vec_offs])+1;
      if (l < sl) {
	for (i=l;i<sl;i++) xs[vec_offs+l-1] = ' ';
	xs[vec_offs+sl-1] = 0;
      }
      x->length = sl;
      /* convert string to vector */
      pic_read_bv(x,xs);
      x->dist = 0.0;
      x->hdist = 0;
      if (is_in_set(x,V)) fprintf(stderr,"\nWARNING: Identifier conflict: %s!",x->strain);
      V = add_element_in_alpha(V,x);
    }
  }
  free(xs);
  
  log2_factorials = prepare_log2_factorials((n+n));
  
  return V;
}

double compute_distance (IntMatrix *M) {
  int s,i,j,l,dist,sum,max,x;
  IntVector *X;
  
  s = M->s;
  l = M->el[1]->l;
  /**/
  dist = 0;
  for(i=1;i<s;i+=2) {
    X = M->el[i];
    max = 0;
    sum = 0;
    for(j=1;j<l;j++) {
      x = X->el[j];
      max = (x > max) ? x : max;
      sum+=x;
    }
    dist+=(sum-max);
  }
  put_dot;
  for(i=1;i<l;i++) {
    max = 0;
    sum = 0;
    for(j=2;j<s;j+=2) {
      x = M->el[j]->el[i];
      max = (x > max) ? x : max;
      sum+=x;
    }
    dist+=(sum-max);
  }
  put_dot;

  return (((double)dist) / 2.0);
}

void print_comp_matrix (FILE *f, IntMatrix *M) {
  int s,l,i,j,x,sum,x1;
  int ii,c,si,cont;
  double dx;
  IntVector *X;
  IntVector *sums;
  Matrix *dM;
  Vector *dX;
  
  /* calculate percetanges */
  s = M->s;
  l = M->el[1]->l;
  dM = allocate_dmatrix((s+1),(l+1));
  for (i=1;i<s;i+=2) {
    X = M->el[i];
    dX = dM->el[i];
    sum = 0;
    for (j=1;j<l;j++) {
      x = X->el[j];
      dX->el[j] = (double) x;
      sum+=x;
    }
    for (j=1;j<l;j++) {
      dx = dX->el[j];
      if (sum != 0) dx/=((double)sum);
      else dx = 0.0;
      dX->el[j] = dx;
    }
  }
  put_dot;
  for (i=1;i<l;i++) {
    sum = 0;
    for (j=2;j<s;j+=2) {
      x = M->el[j]->el[i];
      dM->el[j]->el[i] = (double) x;
      sum+=x;
    }
    for (j=2;j<s;j+=2) {
      dx = dM->el[j]->el[i];
      if (sum != 0) dx/=((double)sum);
      else dx = 0.0;
      dM->el[j]->el[i] = dx;
    }
  }
  sums = allocate_ivector(l);
  put_dot;
  
  /* print results */
  
  /* set base index and inital increment */
  si = 1;
  ii = 9;
  if (ii > l) ii = l;
  cont = TRUE;
  while (cont) {
    
    /* print class labels */
    fprintf(f,"     ");
    c = 0;
    i = 0;
    /* count to nine non empty classes if possible */
    while ((c < 9) && (si+i < l)) {
      /* calulate vertical sum */
      sum = 0;
      for(j=2;j<s;j+=2) {
	x1 = M->el[j]->el[si+i];
	sum+=x1;
      }
      /* don't print if no values */
      if (sum > 0) {
	fprintf(f," %5d ",si+i);
	c++; /* c indicates number of classes to print */
      } else ii++; /* ii indicates number of inspected classes */
      sums->el[si+i] = sum;
      i++;
    }
    /* if we counted zero, we have non empty classes left */
    if (c == 0) cont = FALSE;
    fprintf(f,"\n");
    
    /* print piece of matrix if classes left (from base index (si) forwards) */
    if (cont != FALSE) {
      /* reset ii if less than nine classes left */
      if (c < 9) ii=c;
      x = 0;
      /* i runs verticaly */
      for (i=2;i<s;i++) {
	/* dX is i:th line in percetange matrix */
	/* X is i:th line in matrix of values */
	dX = dM->el[i];
	X = M->el[i];
	sum = 0;
	/* calculate horisontal sum */
	for (j=1;j<l;j++) {
	  x1 = X->el[j];
	  sum+=x1;
	}
	/* increase label counter for everyohter line */
	if ((i%2) == 0) x++;
	/* if we have values, prit them */
	if (sum > 0) {
	  /* relative percetanges may vary, print to both directions */
	  if (print_percetanges) {
	    fprintf(f,"%3d  ",x);
	    for (j=0;j<ii;j++) {
	      if ((sums->el[si+j]) > 0) {
		if ((X->el[si+j]) != 0) fprintf(f,"%.4f ",(dX->el[si+j]));
		else fprintf(f,"       ");
	      }
	    }
	    if (print_values) fprintf(f," %3d\n",x);
	  }
	  /* values are equal for both lines, print only once */
	  if (print_values && ((i%2) == 1)) {
	    fprintf(f,"%3d  ",x);
	    for (j=0;j<ii;j++) {
	      if ((sums->el[si+j]) > 0) {
		x1 = X->el[si+j];
		if (x1 != 0) {
		  if (!print_percetanges) fprintf(f,"%6d ",x1);
		  else fprintf(f,"[%4d] ",x1);
		} else fprintf(f,"       ");
	      }
	    }
	  }
	  /* print label and horisontal sum if needed */
	  if ((i%2) == 1) fprintf(f," %3d  %4d\n",x,sum);
	  else if (print_percetanges && (!print_values)) fprintf(f," %3d\n",x);
	}
      }
      
      /* print class labels */
      fprintf(f,"     ");
      for(i=0;i<ii;i++) {
	if ((sums->el[si+i]) > 0) {
	  fprintf(f," %5d ",si+i);
	}
      }
      /* print vertical sums */
      fprintf(f,"\n     ");
      for(i=0;i<ii;i++) {
	if ((sums->el[si+i]) > 0) {
	  fprintf(f," %5d ",sums->el[si+i]);
	}
      }
      fprintf(f,"\n--\n\n");
      
      /* increase base index */
      si = si + ii;
      
    }
  }
  
  deallocate_ivector(sums);
  deallocate_dmatrix(dM);
  fflush(f);
}

void comparison_results (FILE *f, IntMatrix *M) {
  double d;
  
  if (verbose) fprintf(stdout,"Computing distance between P1 and P2 ");
  d = compute_distance(M);
  if (verbose) fprintf(stdout," ok\nDistance == %2.4f\n",d);
  fprintf(f,"\n\nMatrix\n----\n");
  if (verbose) fprintf(stdout,"Saving comparision matrix ");
  print_comp_matrix(f,M);
  fprintf(f,"\n----\n");
  fprintf(f,"Overal distance = %2.4f\n\n",d);
  if (verbose) fprintf(stdout," ok\n");
}

void compare_partitions (char *datfile, char *parfile1, char *parfile2, char *resfile, char *hdrfile) {
  const char *func = "compare_partitions";
  ST *V;
  Partition *P1;
  Partition *P2;
  FILE *f;
  IntMatrix *M;
  
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) {
    fprintf(stdout,"\nReading vectordata ..");
    fflush(stdout);
  }
  V = read_set_alpha(f,hdrfile);
  fclose(f);
  if (verbose) {
    fprintf(stdout,".. ok \n");
    fflush(stdout);
  }
  if ((f = fopen(parfile1,"r")) == NULL) file_error(parfile1,(char *)func);
  P1 = read_partition(f,FALSE);
  fclose(f);
  if ((f = fopen(parfile2,"r")) == NULL) file_error(parfile2,(char *)func);
  P2 = read_partition(f,FALSE);
  fclose(f);
  if ((f = fopen(resfile,"w")) == NULL) file_error(resfile,(char *)func);
  M = do_comparison(V,P1,P2,f);
  comparison_results(f,M);
  fclose(f);
  deallocate_imatrix(M);
}

/* end of compare.c */

