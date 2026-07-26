/*
This is module for doing mixture classification
*/

extern void trim_cluster (Partition *P);

extern void apply_mixture_classifier(char *datfile, char *outfile, char *parfile1, char* parfile2, char *resfile, char *hdrfile, int m);
extern void apply_mixture_classifier_once(char *datfile, char *outfile, char *parfile, char *hdrfile, int m);

extern void calculate_matrix (Matrix *P, InfCentroid *B, ST *X, Vector *W, int m, int d, int n);

/* end of mixture.h */
