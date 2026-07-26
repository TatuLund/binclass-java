
#include "const.h"

extern void make_tree (char *parfile, char *trefile1, char *trefile2, char *hdrfile);
extern void make_joint (char *parfile1, char *parfile2, char *hdrfile);

extern void inf_average12 (ST *V, Centroid *x);
extern TreeNode *make_tree_pnn (FILE *f, InfCentroid *C, Partition *P, Vector *SC);
extern TreeNode *make_tree_pnn2 (FILE *f, InfCentroid *C, Partition *P, Vector *SC);
extern double hellinger_distance (double *x, double *y, int l);

/* end of tree.h */
