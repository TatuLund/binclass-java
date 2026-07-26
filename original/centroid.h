/*
Declarations of routines in module centroid.c 
*/

extern void do_save_centroids (char* parfile, char* ctrfile, char* hdrfile);

extern void calculate_logs (InfCentroid *C);
extern void mix_centroids (InfCentroid *C, int temp);
extern Centroid *allocate_centroid (int l);
extern InfCentroid *allocate_centroids (int k, int l);
extern void deallocate_centroid (Centroid *c);
extern void deallocate_centroids (InfCentroid *C);
extern InfCentroid *load_centroids (FILE *f);
extern void save_centroids (FILE *f, InfCentroid *C);
extern void random_centroids (int k, int l, InfCentroid *C, ST *V);
extern void copy_centroids (InfCentroid *cmin, InfCentroid *C);
extern double edistance_2 (double *x, double *y, int l);
extern void pick_centroids (int k, int l, InfCentroid *C, ST *V);
extern InfCentroid *add_centroid (InfCentroid *C);

/* End of centroid.h */

