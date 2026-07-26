/*
vectors.c - operations for integer and double type vectors and matrixes
*/

#include <sys/types.h>
#include <stdlib.h>

#include "const.h"
#include "bottom.h"

#define deallocate_ivector(X) { free(X->el); free(X); }
#define deallocate_dvector(X) { free(X->el); free(X); }

IntVector *allocate_ivector (int n);
Vector *allocate_dvector (int n);
IntMatrix *allocate_imatrix (int x, int y);
void deallocate_imatrix (IntMatrix *M);
Matrix *allocate_dmatrix (int x, int y);
void deallocate_dmatrix (Matrix *M);
void indexed_qsort (Vector *X, IntVector *I);

Vector *matrix_vector_multiply (Matrix *M, Vector *X);
Matrix *matrix_transpose (Matrix *M);
Matrix *matrix_multiply (Matrix *M1, Matrix *M2);
Matrix *matrix_inverse (Matrix *M);
Matrix *matrix_pseudo_inverse (Matrix *A);

void sort_dvector(Vector *X);

void qwik_sort (double *a, int l, int r) {
  int i,j;
  double x,y;
  
  i = l;
  j = r;
  
  x = a[(l+r)/2];
  while (i <= j) {
    while (a[i] < x) i++;
    while (x < a[j]) j++;
    if (i <= j) {
      y = a[i];
      a[i] = a[j];
      a[j] = y;
      i++;
      j++;
    }
  }
  if (l < j) qwik_sort(a,l,j);
  if (i < r) qwik_sort(a,i,r);
}

void sort_dvector(Vector *X) {
  double *el;
  int n;
  
  el = X->el;
  n = X->l;
  qwik_sort(el,1,n);
}

void matrix_error (void) {
  fprintf(stderr,"\nERROR: Error in matrix dimensions!");
  exit(1);
}

void matrix_null (void) {
  fprintf(stderr,"\nERROR: Empty matrix or vector!");
  exit(1);
}

void matrix_sign (void) {
  fprintf(stderr,"\nERROR: Signular matrix detected!");
  exit(1);
}

Matrix *matrix_transpose (Matrix *M) {
  Matrix *Mt;
  int x,y;
  int i,j;
  
  if (M == NULL) matrix_null();
  
  x = M->s;
  y = M->el[1]->l;
  
  Mt = allocate_dmatrix(y,x);
  for (i=1;i<(x+1);i++) {
    for (j=1;j<(y+1);j++) {
      Mt->el[j]->el[i] = M->el[i]->el[j];
    }
  }
  return Mt;
}

Matrix *matrix_multiply (Matrix *M1, Matrix *M2) {
  Matrix *R;
  int x,y;
  int i,j,l;
  double sum;
  
  if (M1 == NULL) matrix_null();
  if (M2 == NULL) matrix_null();
  
  x = M1->s;
  y = M2->el[1]->l;
  if (y != x) matrix_error();

  R = allocate_dmatrix(x,y);
  for (i=1;i<(x+1);i++) {
    for (j=1;j<(y+1);j++) {
      sum = 0.0;
      for (l=1;l<(x+1);l++) {
	sum = sum + ((M1->el[l]->el[j]) * (M2->el[i]->el[l]));
      }
      R->el[i]->el[j] = sum;
    }
  }
  return R;
}

Matrix *matrix_inverse (Matrix *M) {
  Matrix *Mi;
  double a,a1,c;
  int k,i;
  int j = 1;
  int x,y;
  
  if (M == NULL) matrix_null();
  
  x = M->s;
  y = M->el[1]->l;
  
  if (x != y) matrix_error();
  
  /* initialize Mi with I */
  Mi = allocate_dmatrix(x,x);
  for (i=1;i<(x+1);i++) {
    Mi->el[i]->el[i] = 1.0;
  }
  
  /* find M^(-1) with Gauss elimination */
  for (i=1;i<(x+1);i++) {
    /* get a(i,i) */
    a = M->el[i]->el[i];
    if (a != 0.0) {
      a1 = 1.0 / a;
      /* multiply i:th line with 1 / a(i,i) */
      for (j=1;j<(x+1);j++) {
	M->el[i]->el[j] = (M->el[i]->el[j]) * a1;
	Mi->el[i]->el[j] = (Mi->el[i]->el[j]) * a1;
      }
    } else {
      k = i;
      while ((k < (x+1)) && (M->el[j]->el[i] == 0.0)) {
	k++;
      }
      if (k == (x+1)) {
	/* we didn't find such a(k,i), signular matrix cannot be inverted */
	matrix_sign();
      } else {
	/* swap lines k and i */
	for (j=1;j<(x+1);j++) {
	  a = M->el[k]->el[j];
	  M->el[k]->el[j] = M->el[i]->el[j];
	  M->el[i]->el[j] = a;
	  a = Mi->el[k]->el[j];
	  Mi->el[k]->el[j] = Mi->el[i]->el[j];
	  Mi->el[i]->el[j] = a;
	}
	a = M->el[i]->el[i];
	a1 = 1.0 / a;
	for (j=1;j<(x+1);j++) {
	  /* multiply i:th line with 1 / a(i,i) */
	  M->el[i]->el[j] = (M->el[i]->el[j]) * a1;
	  Mi->el[i]->el[j] = (Mi->el[i]->el[j]) * a1;
	}
      }
    }
    for (k=(i+1);k<(x+1);k++) {
      /* get a(k,i) and a(i,i) */
      a = M->el[k]->el[i];
      a1 = M->el[i]->el[i];
      if (a != 0.0) {
	/* add i:th line to k:th line multiplied with c */
	c = (-1.0) * (a / a1);
	for (j=1;j<(x+1);j++) {
	  M->el[k]->el[j] = ((M->el[k]->el[j]) + (c * (M->el[i]->el[i])));
	  Mi->el[k]->el[j] = ((Mi->el[k]->el[j]) + (c * (Mi->el[i]->el[i])));
	}
      }
    }
  }
  
  return Mi;
}

Matrix *matrix_pseudo_inverse (Matrix *A) {
  Matrix *At;
  Matrix *Mi;
  Matrix *Ap;
  Matrix *M;
  
  if (A == NULL) matrix_null();
  
  /* calculate A+ */
  At = matrix_transpose(A);
  M = matrix_multiply(At,A);
  Mi = matrix_inverse(M);
  Ap = matrix_multiply(Mi,At);
  
  deallocate_dmatrix(At);
  deallocate_dmatrix(M);
  deallocate_dmatrix(Mi);
  return Ap;
}

Vector *matrix_vector_multiply (Matrix *M, Vector *X) {
  Vector *Y;
  int x,y;
  int i,j;
  double sum;
  
  if (M == NULL) matrix_null();
  if (X == NULL) matrix_null();
  
  x = M->s;
  y = M->el[1]->l;
  
  if (X->l != y) matrix_error();
  
  Y = allocate_dvector(x);
  
  for (i=1;i<(x+1);i++) {
    sum = 0;
    for (j=1;j<(y+1);j++) {
      sum = sum + ((M->el[i]->el[j]) * (X->el[j]));
    }
    Y->el[i] = sum;
  }
  return Y;
}

IntVector *allocate_ivector (int n) {
  IntVector *X;
  int i;
  
  if ((X = (IntVector *) malloc(sizeof(IntVector))) == NULL) out_of_mem();
  if ((X->el = (int *) malloc(sizeof(int)*(n+1))) == NULL) out_of_mem();
  for (i=0;i<(n+1);i++) X->el[i] = 0;
  X->l = n;
  return X;
}

Vector *allocate_dvector (int n) {
  Vector *X;
  int i;
  
  if ((X = (Vector *) malloc(sizeof(Vector))) == NULL) out_of_mem();
  if ((X->el = (double *) malloc(sizeof(double)*(n+1))) == NULL) out_of_mem();
  for (i=0;i<(n+1);i++) X->el[i] = 0.0;
  X->l = n;
  return X;
}

IntMatrix *allocate_imatrix (int x, int y) {
  IntVector **t;
  IntMatrix *M;
  int i;
  
  if ((M = (IntMatrix *) malloc(sizeof(IntMatrix))) == NULL) out_of_mem();
  if ((t = malloc((x+1)*sizeof(void *))) == NULL) out_of_mem();
  for (i=0;i<(x+1);i++) t[i] = allocate_ivector(y);
  M->el = t;
  M->s = x;
  return M;
}

void deallocate_imatrix (IntMatrix *M) {
  int i,s;
  
  s = M->s;
  for (i=0;i<s;i++) deallocate_ivector(M->el[i]);
  free(M);
}

Matrix *allocate_dmatrix (int x, int y) {
  Vector **t;
  Matrix *M;
  int i;
  
  if ((M = (Matrix *) malloc(sizeof(Matrix))) == NULL) out_of_mem();
  if ((t = malloc((x+1)*sizeof(void *))) == NULL) out_of_mem();
  for (i=0;i<(x+1);i++) t[i] = allocate_dvector(y);
  M->el = t;
  M->s = x;
  return M;
}

void deallocate_dmatrix (Matrix *M) {
  int i,s;
  
  s = M->s;
  for (i=0;i<s;i++) deallocate_dvector(M->el[i]);
  free(M);
}

void swap_them (double *X, int *I, int i, int j) {
  double t1;
  int t2;
  
  t1 = X[i];
  X[i] = X[j];
  X[j] = t1;
  t2 = I[i];
  I[i] = I[j];
  I[j] = t2;
}

void kwaaksort (double *X, int *I, int l, int r) {
  int i,last,p;
  
  if (l>=r) return;
  p = (l+r)/2;
  swap_them(X,I,l,p);
  last = l;
  for (i=(l+1);i<=r;i++) if (X[i] < X[l]) swap_them(X,I,++last,i);
  swap_them(X,I,l,last);
  kwaaksort(X,I,l,(last-1));
  kwaaksort(X,I,(last+1),r);
}

void indexed_qsort (Vector *X, IntVector *I) {
  int l;
  int *eli;
  double *elx;
  
  l = (X->l)-1;
  elx = X->el;
  eli = I->el;
  kwaaksort(elx,eli,1,l);
}

/* end of vectors.c */
