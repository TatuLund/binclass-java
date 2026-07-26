												 /*
Definitions of functions in module binseti.c
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern int freq (ST *V, IntVector *fr, int l);
extern void inf_average (ST *V, Centroid *x, int rounded, int s);
extern void inf_average_12 (ST *V, Centroid *x, int s);

/* codelength */
extern double code_length (BV *x, Centroid *y);
extern double code_length2 (BV *x, Centroid *y);
extern double class_code_length (Partition *P, InfCentroid *C, int clas, int s);
extern double average_codelength (Partition *P, InfCentroid *C, int precalc);
extern void inf_nearest_neighbour (ST *V, Partition *P, InfCentroid *C, int weights);
/* hamming distance */
extern int hamming_distance (BV *x, Centroid *y);
extern double class_distortion (ST *W, Centroid *C);
extern double overall_distortion (Partition *P, InfCentroid *C);
extern void fast_nearest_neighbour (ST *V, Partition *P, InfCentroid *C);
/* L1 norm */
extern double L1_distance (BV *x, Centroid *y);
extern double class_MAE (Partition *P, InfCentroid *C, int clas);
extern double overall_MAE (Partition *P, InfCentroid *C);
extern void MAE_nearest_neighbour (ST *V, Partition *P, InfCentroid *C);
/* L2 norm */
extern double L2_distance (BV *x, Centroid *y);
extern double class_MSE (Partition *P, InfCentroid *C, int clas);
extern double overall_MSE (Partition *P, InfCentroid *C);
extern void MSE_nearest_neighbour (ST *V, Partition *P, InfCentroid *C);

extern double shannon_entropy (Partition *P, InfCentroid *C, int precalc);
extern double stochastic_complexity (Partition *P, int k, int l);
extern double stochastic_complexity_j (Partition *P, int k, int d);
extern double stochastic_complexity_u (Partition *P, int k, int l);

extern void local_repartition_mse (int c, Partition *P, InfCentroid *C);
extern void local_repartition (int c, Partition *P, InfCentroid *C);

/* End of distmin.h */
