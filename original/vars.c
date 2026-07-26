
#include <sys/types.h>
#include <stdio.h>

#ifndef BINCLASS_TYPES
#include "const.h"
#endif

double epsilon = 0.001;
double first_d = 1000.0;
double treshold;

const int RP_TOTALFREQ = 1;           /* reporting parameters */
const int RP_NEARNESS = 2;
const int RP_PARTITION = 4;
const int RP_MATCH = 8;
const int RP_NEIGHBOR = 16;
const int RP_FREQ = 32;
const int RP_MATRIX = 64;

char *filebase;
char *dumpfile;
char *centroidfile;
char *new_parfile;

int vec_len;
int vec_offs;
int id_offs;
int id_len;
char *id_ord;
int name_len;

int kstart = 1;
int kstop = 0;
int kstopwhen = 10;
int max_iter = 20;
int safety_limit = 500;
int vecs_to_gen = 500;
int iter_base = 1;
int report_params = 0;
int bootstrap_size = 50;
int bootstrap_k = 0;
int bootstrap_i = 100;
int join_target = 0;
double gla_treshold = 1.1;
int real_delta_value = 8000;
int cumulative_analysis = 0;
int cumulative_samples = 100;
int mixture_classes = 0;
int sample_mixture = 0;
int ls_heuristic_count = 50;
int maximum_class_number = 200;
int t1_rs_count = 101;
int t1_trials = 101;
int t2_treshold = 0;

eDist distance_type = DT_L1_CL;
eDataGen data_generator = DG_RVECTOR;
eSearch search_type = ST_AUTO;
eCentroidType centroid_type = CT_RAND;
eModuleType module = MOD_NONE;
eHeuristic ls_heuristic = HEUR_NONE;

int print_percetanges = FALSE;
int print_values = FALSE;
int print_matches = FALSE;

int save_best_boots = FALSE;
int unique_vectors = FALSE;
int log_file = TRUE;
int filter_exact_k = FALSE;
int best_code_length = FALSE;
int require_better = FALSE;
int exact_matches = FALSE;
int store_partition = FALSE;
int ordered_input = FALSE;
int dump_only = FALSE;
int do_dump = FALSE;
int verbose = TRUE;
int trashcan = FALSE;
int log_centroids = FALSE;
int analyse_missing = FALSE;
int remove_empty_sets = FALSE;
int rounded_centroids = FALSE;
int source_error = TRUE;
int continue_search = FALSE;
int use_abs_match = FALSE;
int use_hellinger = FALSE;
int use_custom = FALSE;
int use_parsimony = FALSE;
int print_digits = FALSE;
int affinity_matrix = FALSE;
int cumulative_in_order = FALSE;
int cumulative_input_order = FALSE;
int bayesian_predictive = TRUE;
int fixed_delta = TRUE;
int relative_int = FALSE;
int maximal_int = FALSE;
int minimal_int = FALSE;
int analyse_int = FALSE;
int analyse_int_stab = FALSE;
int cum_no_new_classes = FALSE;
int use_class_weights = FALSE;
int alternate_empty_cell_fix = FALSE;
int check_input_set = TRUE;
int decreasing_epsilon = FALSE;
int use_jeffreys_prior = FALSE;
int cum_save_by_pf = TRUE;
int t1_extra_iter = FALSE;
int ls_heuristic_cycler = FALSE;
int ls_adaptive_heuristic = FALSE;
int alternate_worst_match = TRUE;
int test_feature_significance = FALSE;

double *total_freqs = NULL;
double *log2_factorials = NULL;

/* End of vars.c */

