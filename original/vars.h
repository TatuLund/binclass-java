
#include "const.h"

extern const int RP_TOTALFREQ;            /* reporting parameters */
extern const int RP_NEARNESS;
extern const int RP_PARTITION;
extern const int RP_MATCH;
extern const int RP_NEIGHBOR;
extern const int RP_FREQ;
extern const int RP_MATRIX;

/* Parameters */
extern int kstart;
extern int kstop;
extern int kstopwhen;
extern int max_iter;
extern int safety_limit;
extern int vecs_to_gen;
extern double epsilon;
extern double first_d;
extern double treshold;
extern int vec_len;
extern int vec_offs;
extern int id_offs;
extern int id_len;
extern char *id_ord;
extern int name_len;
extern int bootstrap_size;
extern int bootstrap_k;
extern int bootstrap_i;
extern int join_target;
extern double gla_treshold;
extern int real_delta_value;

extern char *filebase;
extern char *centroidfile;
extern char *dumpfile;
extern char *new_parfile;
extern int iter_base;
extern int report_params;
extern int cumulative_analysis;
extern int cumulative_samples;
extern int mixture_classes;
extern int sample_mixture;
extern int ls_heuristic_count;
extern int maximum_class_number;
extern int t1_trials;
extern int t1_rs_count;
extern int t2_treshold;

extern eDist distance_type;
extern eDataGen data_generator;
extern eSearch search_type;
extern eCentroidType centroid_type;
extern eModuleType module;
extern eHeuristic ls_heuristic;

/* Options (Boolean)*/
extern int print_percetanges;
extern int print_values;
extern int print_matches;

extern int save_best_boots;
extern int unique_vectors;
extern int log_file;
extern int filter_exact_k;
extern int best_code_length;
extern int require_better;
extern int exact_matches;
extern int store_partition;
extern int ordered_input;
extern int dump_only;
extern int do_dump;
extern int verbose;
extern int trashcan;
extern int log_centroids;
extern int analyse_missing;
extern int remove_empty_sets;
extern int rounded_centroids;
extern int source_error;
extern int continue_search;
extern int use_abs_match;
extern int use_hellinger;
extern int use_parsimony;
extern int use_custom;
extern int print_digits;
extern int affinity_matrix;
extern int cumulative_in_order;
extern int cumulative_input_order;
extern int bayesian_predictive;
extern int fixed_delta;
extern int relative_int;
extern int minimal_int;
extern int maximal_int;
extern int analyse_int;
extern int analyse_int_stab;
extern int cum_no_new_classes;
extern int use_class_weights;
extern int alternate_empty_cell_fix;
extern int check_input_set;
extern int decreasing_epsilon;
extern int use_jeffreys_prior;
extern int cum_save_by_pf;
extern int t1_extra_iter;
extern int ls_heuristic_cycler;
extern int ls_adaptive_heuristic;
extern int alternate_worst_match;
extern int test_feature_significance;

/* Globals */
extern double *total_freqs;
extern double *log2_factorials;

/* End of vars.h */

