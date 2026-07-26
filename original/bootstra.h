
extern void run_bootstrap (char *datfile, char *btsfile, char *hdrfile, int k, char *parfile);
extern void mle_approx_2dim (Vector *Y, Vector *X, double *a, double *b);
extern double correlation_coef (Vector *X, Vector *Y);
extern double calculate_mse (double a, double b, Vector *X, Vector *Y);

/* end of bootstra.h */

