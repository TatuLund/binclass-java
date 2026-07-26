/*
vectors.h
*/

#define deallocate_ivector(X) { free(X->el); free(X); }
#define deallocate_dvector(X) { free(X->el); free(X); }

extern IntVector *allocate_ivector (int n);
/* extern void deallocate_ivector (IntVector *X); */
extern Vector *allocate_dvector (int n);
/* extern void deallocate_dvector (Vector *X); */
extern IntMatrix *allocate_imatrix (int x, int y);
extern void deallocate_imatrix (IntMatrix *M);
extern Matrix *allocate_dmatrix (int x, int y);
extern void deallocate_dmatrix (Matrix *M);
extern void indexed_qsort (Vector *X, IntVector *I);

extern Vector *matrix_vector_multiply (Matrix *M, Vector *X);
extern Matrix *matrix_transpose (Matrix *M);
extern Matrix *matrix_multiply (Matrix *M1, Matrix *M2);
extern Matrix *matrix_inverse (Matrix *M);
extern Matrix *matrix_pseudo_inverse (Matrix *A);

/* end of vectors.h */
