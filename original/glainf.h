/*
Declarations of routines in module glainf.c
*/

extern Partition *use_gla_load_centroids (ST *V, char* outfile);
extern InfCentroid *use_gla_deterministic (ST *V, Partition *P, int k, char* outfile);
extern InfCentroid *use_gla (ST *V, Partition *P, int k, char* outfile, double lasti, int better, int filter, double minsc);
extern int gla (ST *V, Partition *P, InfCentroid *C, double *dmin, int n);
extern void special_gla (ST *V, Partition *P, InfCentroid *C, double *dmin);
extern void replace_worst (int k, int l, InfCentroid *C, Partition *P);

/* End of glainf.h */
