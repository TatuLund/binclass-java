
#include <sys/types.h>

#ifndef BINCLASS_TYPES

#define BINCLASS_TYPES

#include "dep.h"
#define FALSE 0
#define TRUE 1
#define MAX_LENGTH 2000
#define MAX_TEMP 16378           /* initial temperature of simulated annealing / stochastic relaxation */
#define L_MAX MAX_LENGTH 	 /* maximum amount of tests */
#define ZEROCHAR '.'
#define ONECHAR 'X'
#define ALPHA 0.15               /* bit alteration parameter #UNUSED# */
#define SMALL_NUMBER 0.001       /* default epsilon #UNUSED# */
#define EPS 0.0000001            /* epsilon used in GLA convergence deremination */
#define LPI 1.6514961            /* just regular logarithm of PI needed for SC with Jeffreys prior */
#define ILOGOF2 1.44269504       /* inverse of natural logarithm of two */

typedef struct {
  int *el;
  int *miss;
  int length;
  char *clasname;
  char *strain;
  double dist;
  int hdist;
  int num;
} BV;

typedef struct {
  BV *el;
  void *next;
  void *last;
  /* int size; */
} ST;

typedef struct {
  ST **el;
  int k;
} Partition;

typedef struct {
  double *el;
  int l;
} Vector;

typedef struct {
  int *el;
  int l;
} IntVector;

typedef struct {
  ST *el;
  IntVector *freq;
  IntVector *hmo;
  IntVector *nij;
  int size;
  void *next;
} DynPartition;

typedef struct {
  IntVector **el;
  int s;
} IntMatrix;

typedef struct {
  Vector **el;
  int s;
} Matrix;

typedef struct {
  double *el;
  double *log0;
  double *log1;
  int l;
  double weight;
} Centroid;

typedef struct {
  Centroid **el;
  int k;
  double SC;
  double I;
  double I2;
} InfCentroid;

typedef struct {
  double zero_prob;
  double one_prob;
  void *zero;
  void *one;
} Automata;

typedef struct {
  double *el;
  int k;
  int num;
  int size;
  double dist;
  double sc;
  char *name;
  void *left;
  void *right;
} TreeNode;

typedef struct {
  IntVector *freq;
  int size;
  int linked;
  void *linkage;
} Frequencies;

typedef enum {HEUR_REPLACESMALLEST=1,HEUR_SPLITJOIN1,HEUR_SPLITJOIN2,HEUR_REPLACEWORST,HEUR_RANDOMSWAP,HEUR_RANDOMSWAP2,HEUR_NONE} eHeuristic;
typedef enum {DT_HAM=1,DT_L1,DT_L2,DT_CL,DT_L1_CL,DT_L2_CL,DT_SR,DT_SA} eDist;
typedef enum {DG_RAND=1,DG_BERNOULI,DG_MARKOV,DG_RVECTOR} eDataGen;
typedef enum {ST_AUTO=1,ST_NAUTO,ST_LCENT,ST_ADAP} eSearch;
typedef enum {CT_CLASSIC=1,CT_SRAND,CT_SEMI,CT_RAND,CT_PNN} eCentroidType;
typedef enum {MOD_NONE=1,MOD_CLASSIFY,MOD_SPLIT,MOD_JOIN,MOD_REPORT,MOD_TREE,MOD_IDENT,MOD_COMPARE,MOD_BOOTSTRAP,MOD_GEN,MOD_CUMULATIVE,MOD_CENTROIDS,MOD_SORTP,MOD_MIXTURE,MOD_INTERSECT,MOD_FUNCTION,MOD_TEST1,MOD_TEST2} eModuleType;

#endif

