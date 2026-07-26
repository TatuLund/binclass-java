
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "vars.h"
#include "bottom.h"

/* prototypes */

int parse (int ac, char *av[]);
void help_text (FILE *f);

int parse_identify (int ac, char *av[]);
int parse_classify (int ac, char *av[]);
int parse_compare (int ac, char *av[]);
int parse_report (int ac, char *av[]);
int parse_generate (int ac, char *av[]);
int parse_bootstrap (int ac, char *av[]);
int parse_fclassify (int ac, char *av[]);
int parse_cumulative (int ac, char *av[]);
int parse_sclassify (int ac, char *av[]);
int parse_tree (int ac, char *av[]);
int parse_centroids (int ac, char *av[]);
int parse_sortpart (int ac, char *av[]);
int parse_mixture (int ac, char *av[]);
int parse_cut (int ac, char *av[]);
int parse_function (int ac, char *av[]);
#ifdef _TEST_ALG1
int parse_test1 (int ac, char *av[]);
#endif
#ifdef _TEST_ALG2
int parse_test2 (int ac, char *av[]);
#endif

void help_text_identify (FILE *f);
void help_text_classify (FILE *f);
void help_text_compare (FILE *f);
void help_text_generate (FILE *f);
void help_text_bootstrap (FILE *f);
void help_text_report(FILE *f);
void help_text_fclassify (FILE *f);
void help_text_sclassify (FILE *f);
void help_text_tree (FILE *f);
void help_text_cumulative (FILE *f);
void help_text_centroids (FILE *f);
void help_text_sortpart (FILE *f);
void help_text_mixture (FILE *f);
void help_text_cut (FILE *f);
void help_text_function (FILE *f);
#ifdef _TEST_ALG1
void help_text_test1 (FILE *f);
#endif
#ifdef _TEST_ALG2
void help_text_test2 (FILE *f);
#endif

/* implementation */

int parse_identify (int ac, char *av[]) {
  const char *func = "parse_identify";
  int i,a,tmp;
  char buf[10];
  char b[10];
  char *p;
  char *s;
  FILE *f;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-E") == 0) {
	if (strlen(s) == 2) decreasing_epsilon = TRUE;
	else {
	  p = av[i]+2;
	  epsilon = atof(strcpy(buf,p));
	  if (!(epsilon < 0.5)) return FALSE;
	}
      } else if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-t") == 0) {
	trashcan = TRUE;
      } else if (strcmp(s,"-f") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 1) distance_type = DT_HAM;
	else if (tmp == 2) distance_type = DT_L1;
	else if (tmp == 3) distance_type = DT_L2;
	else if (tmp == 4) distance_type = DT_CL;
	else if (tmp == 5) distance_type = DT_L1_CL;
	else if (tmp == 6) distance_type = DT_L2_CL;
	else if (tmp == 7) distance_type = DT_SR;
	else if (tmp == 8) distance_type = DT_SA;
	else return FALSE;
      } else if (strcmp(s,"-J") == 0) {
	use_jeffreys_prior = TRUE;
      } else if (strcmp(s,"-w") == 0) {
	use_class_weights = TRUE;
      } else if (strcmp(s,"-M") == 0) {
	exact_matches = TRUE;
      } else if (strcmp(s,"-P") == 0) {
	if ( (new_parfile = (char *) malloc(strlen(av[i])-2)) == NULL) out_of_mem();
	store_partition = TRUE;
	strcpy(new_parfile,&av[i][2]);
	if ((f = fopen(new_parfile,"r")) != NULL) {
	  fclose(f);
	  file_exists(new_parfile,(char *)func);
	}
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_classify (int ac, char *av[]) {
  const char *func = "parse_classify";
  int i,a,tmp;
  double tmp2;
  char buf[10];
  char b[10];
  char *p;
  char *s;
  FILE *f;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-b") == 0) {
	p = av[i]+2;
	kstart = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-s") == 0) {
	p = av[i]+2;
	kstop = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-S") == 0) {
	p = av[i]+2;
	kstopwhen = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-E") == 0) {
	if (strlen(s) == 2) decreasing_epsilon = TRUE;
	else {
	  p = av[i]+2;
	  epsilon = atof(strcpy(buf,p));
	  if (!(epsilon < 0.5)) return FALSE;
	}
      } else if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-r") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 1) ls_heuristic = HEUR_SPLITJOIN1;
	else if (tmp == 2) ls_heuristic = HEUR_REPLACEWORST;
	else if (tmp == 3) ls_heuristic = HEUR_REPLACESMALLEST;
	else if (tmp == 4) ls_heuristic = HEUR_RANDOMSWAP;
	else if (tmp == 5) ls_heuristic = HEUR_SPLITJOIN2;
	else if (tmp == 6) ls_heuristic = HEUR_RANDOMSWAP2;
	else if (tmp == 7) {
	  ls_heuristic_cycler = TRUE;
	  ls_heuristic = HEUR_SPLITJOIN1;
	}
	else if (tmp == 8) {
	  ls_adaptive_heuristic = TRUE;
	  ls_heuristic = HEUR_SPLITJOIN1;
	}
	else return FALSE;
      } else if (strcmp(s,"-t") == 0) {
	trashcan = TRUE;
      } else if (strcmp(s,"-e") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 1) {
	  alternate_worst_match = FALSE;
	  alternate_empty_cell_fix = FALSE;
	} else if (tmp == 2) {
	  alternate_worst_match = TRUE;
	  alternate_empty_cell_fix = FALSE;
	} else if (tmp == 3) {
	  alternate_empty_cell_fix = TRUE;
	  alternate_worst_match = FALSE;
	} else if (tmp == 4) {
	  alternate_empty_cell_fix = TRUE;
	  alternate_worst_match = TRUE;
	}
      } else if (strcmp(s,"-m") == 0) {
	analyse_missing = TRUE;
      } else if (strcmp(s,"-c") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 1) centroid_type = CT_CLASSIC;
	else if (tmp == 2) centroid_type = CT_SRAND;
	else if (tmp == 3) centroid_type = CT_SEMI;
	else if (tmp == 4) centroid_type = CT_RAND;
	else if (tmp == 5) centroid_type = CT_PNN;
	else return FALSE;
      } else if (strcmp(s,"-l") == 0) {
	log_centroids = TRUE;
      } else if (strcmp(s,"-J") == 0) {
	use_jeffreys_prior = TRUE;
      } else if (strcmp(s,"-w") == 0) {
	use_class_weights = TRUE;
      } else if (strcmp(s,"-B") == 0) {
	require_better = TRUE;
	p = av[i]+2;
	tmp2 = atof(strcpy(buf,p));
	if (tmp2 > 0) first_d = tmp2;
      } else if (strcmp(s,"-C") == 0) {
	if (best_code_length) return FALSE;
	best_code_length = TRUE;
      } else if (strcmp(s,"-R") == 0) {
	if (rounded_centroids) return FALSE;
	rounded_centroids = TRUE;
      } else if (strcmp(s,"-f") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (centroid_type == CT_PNN) {
	  if (tmp == 2) distance_type = DT_L1;
	  else if (tmp == 3) distance_type = DT_L2;
	  else return FALSE;
	} else {
	  if (tmp == 1) distance_type = DT_HAM;
	  else if (tmp == 2) distance_type = DT_L1;
	  else if (tmp == 3) distance_type = DT_L2;
	  else if (tmp == 4) distance_type = DT_CL;
	  else if (tmp == 5) distance_type = DT_L1_CL;
	  else if (tmp == 6) distance_type = DT_L2_CL;
/*	  else if (tmp == 7) distance_type = DT_SR; */
/*	  else if (tmp == 8) distance_type = DT_SA; */
	  else return FALSE;
	}
      } else if (strcmp(s,"-n") == 0) {
	if (centroid_type == CT_PNN) return FALSE;
	if (search_type != ST_AUTO) return FALSE;
	search_type = ST_NAUTO;
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp != 0) max_iter = tmp;
      } else if (strcmp(s,"-j") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp != 0) ls_heuristic_count = tmp+1;
      } else if (strcmp(s,"-F") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if ((tmp > max_iter) && (tmp < 10000)) safety_limit = tmp;
	else return FALSE;
      } else if (strcmp(s,"-a") == 0) {
	if (centroid_type == CT_PNN) return FALSE;
	if (search_type != ST_AUTO) return FALSE;
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp != 0) iter_base = tmp;
      } else if (strcmp(s,"-d") == 0) {
	do_dump = TRUE;
	if ( (dumpfile = (char *) malloc(strlen(av[i])-2)) == NULL) out_of_mem();
	strcpy(dumpfile,&av[i][2]);
	if ((f = fopen(dumpfile,"r")) != NULL) {
	  fclose(f);
	  file_exists(dumpfile,(char *)func);
	}
      } else if (strcmp(s,"-L") == 0) {
	if (search_type > 1) return FALSE;
	search_type = ST_LCENT;
	if ( (centroidfile = (char *) malloc(strlen(av[i])-2)) == NULL) out_of_mem();
	strcpy(centroidfile,&av[i][2]);
	if ((f = fopen(centroidfile,"r")) == NULL) file_error(centroidfile,(char *)func);
      } else {
	return FALSE;
      }
    }
  }
  if (kstart < 1) return FALSE;
  if ((kstart > kstop) && (kstop != 0)) return FALSE;
  if (ls_heuristic != HEUR_NONE) safety_limit = 1;
  if ((ls_heuristic != HEUR_NONE) && (search_type == ST_AUTO)) max_iter = 1;
  filebase = av[ac-1];
  return TRUE;
}

int parse_compare (int ac, char *av[]) {
  int i,a,tmp;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-M") == 0) {
	exact_matches = TRUE;
      } else if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-V") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 1) print_values = TRUE;
	else if (tmp == 2) print_percetanges = TRUE;
	else if (tmp == 3) print_matches = TRUE;
	else return FALSE;
      } else {
	return FALSE;
      }
    }
  }
  if (!(print_values || print_percetanges)) return FALSE;
  filebase = av[ac-1];
  return TRUE;
}


int parse_tree (int ac, char *av[]) {
  int i,a,tmp;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-H") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 1) use_hellinger = TRUE;
	else if (tmp == 2) use_custom = TRUE;
	else if (tmp == 4) use_parsimony = TRUE;
	else return FALSE;
      } else if (strcmp(s,"-J") == 0) {
	use_jeffreys_prior = TRUE;
      } else if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_centroids (int ac, char *av[]) {
  int i,a;
  char b[10];
  char *s;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_sortpart (int ac, char *av[]) {
  int i,a;
  char b[10];
  char *s;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_function (int ac, char *av[]) {
  int i,a,tmp;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-w") == 0) {
	use_class_weights = TRUE;
      } else if (strcmp(s,"-f") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 1) distance_type = DT_HAM;
	else if (tmp == 2) distance_type = DT_L1;
	else if (tmp == 3) distance_type = DT_L2;
	else if (tmp == 4) distance_type = DT_CL;
	else if (tmp == 5) distance_type = DT_L1_CL;
	else if (tmp == 6) distance_type = DT_L2_CL;
/*	else if (tmp == 7) distance_type = DT_SR; */
/*	else if (tmp == 8) distance_type = DT_SA; */
	else return FALSE;
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_cut (int ac, char *av[]) {
  int i,a;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-r") == 0) {
	relative_int = TRUE;
      } else if (strcmp(s,"-s") == 0) {
	minimal_int = TRUE;
      } else if (strcmp(s,"-m") == 0) {
	maximal_int = TRUE;
      } else if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-A") == 0) {
	analyse_int_stab = TRUE;
	p = av[i]+2;
	kstart = atoi(strcpy(buf,p))+1;
	if (kstart == 0) return FALSE;
      } else if (strcmp(s,"-a") == 0) {
	analyse_int = TRUE;
	p = av[i]+2;
	kstart = atoi(strcpy(buf,p))+1;
	if (kstart == 0) return FALSE;
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_report (int ac, char *av[]) {
  int i,a,tmp;
  char buf[10];
  char b[10];
  char *p;
  char *s;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-E") == 0) {
	if (strlen(s) == 2) decreasing_epsilon = TRUE;
	else {
	  p = av[i]+2;
	  epsilon = atof(strcpy(buf,p));
	  if (!(epsilon < 0.5)) return FALSE;
	}
      } else if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-d") == 0) {
	print_digits = TRUE;
      } else if (strcmp(s,"-a") == 0) {
	affinity_matrix = TRUE;
      } else if (strcmp(s,"-w") == 0) {
	use_class_weights = TRUE;
      } else if (strcmp(s,"-h") == 0) {
	use_hellinger = TRUE;
      } else if (strcmp(s,"-p") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 0) report_params = (RP_NEARNESS | RP_TOTALFREQ | RP_PARTITION | RP_MATCH | RP_NEIGHBOR | RP_FREQ | RP_MATRIX);
	else if (tmp == 1) report_params = (report_params | RP_NEARNESS);
	else if (tmp == 2) report_params = (report_params | RP_TOTALFREQ);
	else if (tmp == 3) report_params = (report_params | RP_PARTITION);
	else if (tmp == 4) report_params = (report_params | RP_MATCH);
	else if (tmp == 5) report_params = (report_params | RP_NEIGHBOR);
	else if (tmp == 6) report_params = (report_params | RP_FREQ);
	else if (tmp == 7) report_params = (report_params | RP_MATRIX);
	else return FALSE;
      } else if (strcmp(s,"-l") == 0) {
	if (log_centroids) return FALSE;
	log_centroids = TRUE;
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_generate (int ac, char *av[]) {
  int i,a,tmp;
  char buf[10];
  char b[10];
  char *p;
  char *s;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-v") == 0) {
	p = av[i]+2;
	vecs_to_gen = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-u") == 0) {
	unique_vectors = TRUE;
      } else if (strcmp(s,"-G") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 1) data_generator = DG_RAND;
	else if (tmp == 2) data_generator = DG_BERNOULI;
	else if (tmp == 3) data_generator = DG_MARKOV;
	else if (tmp == 4) data_generator = DG_RVECTOR;
	else return FALSE;
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_fclassify (int ac, char *av[]) {
  int i,a;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-A") == 0) {
	use_abs_match = TRUE;
      } else if (strcmp(s,"-S") == 0) {
	p = av[i]+2;
	kstopwhen = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-J") == 0) {
	use_jeffreys_prior = TRUE;
      } else if (strcmp(s,"-E") == 0) {
	if (strlen(s) == 2) decreasing_epsilon = TRUE;
	else {
	  p = av[i]+2;
	  epsilon = atof(strcpy(buf,p));
	  if (!(epsilon < 0.5)) return FALSE;
	}
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_cumulative (int ac, char *av[]) {
  int i,a;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-O") == 0) {
	if (cumulative_input_order) return FALSE;
	if (cumulative_analysis > 0) return FALSE;
	cumulative_in_order = TRUE;
      } else if (strcmp(s,"-I") == 0) {
	if (cumulative_in_order) return FALSE;
	if (cumulative_analysis > 0) return FALSE;
	cumulative_input_order = TRUE;
      } else if (strcmp(s,"-S") == 0) {
	bayesian_predictive = FALSE;
      } else if (strcmp(s,"-F") == 0) {
	test_feature_significance = TRUE;
      } else if (strcmp(s,"-c") == 0) {
	cum_save_by_pf = FALSE;
      } else if (strcmp(s,"-n") == 0) {
	cum_no_new_classes = TRUE;
      } else if (strcmp(s,"-E") == 0) {
	p = av[i]+2;
	epsilon = atof(strcpy(buf,p));
	if (!(epsilon < 0.5)) return FALSE;
      } else if (strcmp(s,"-N") == 0) {
	if (cumulative_input_order) return FALSE;
	if (cumulative_in_order) return FALSE;
	p = av[i]+2;
	cumulative_analysis = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-s") == 0) {
	if (cumulative_input_order) return FALSE;
	if (cumulative_in_order) return FALSE;
	p = av[i]+2;
	cumulative_samples = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-D") == 0) {
	if (fixed_delta == FALSE) return FALSE;
	p = av[i]+2;
	real_delta_value = atoi(strcpy(buf,p));
	if (real_delta_value < 0) return FALSE;
      } else if (strcmp(s,"-d") == 0) {
	p = av[i]+2;
	fixed_delta = FALSE;
	real_delta_value = atoi(strcpy(buf,p));
	if (real_delta_value < 0) return FALSE;
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_sclassify (int ac, char *av[]) {
  int i,a;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-A") == 0) {
	use_abs_match = TRUE;
      } else if (strcmp(s,"-E") == 0) {
	if (strlen(s) == 2) decreasing_epsilon = TRUE;
	else {
	  p = av[i]+2;
	  epsilon = atof(strcpy(buf,p));
	  if (!(epsilon < 0.5)) return FALSE;
	}
      } else if (strcmp(s,"-J") == 0) {
	use_jeffreys_prior = TRUE;
      } else if (strcmp(s,"-j") == 0) {
	p = av[i]+2;
	join_target = atoi(strcpy(buf,p));
	if (join_target < 2) return FALSE;
      } else if (strcmp(s,"-T") == 0) {
	p = av[i]+2;
	gla_treshold = atof(strcpy(buf,p));
	if (!(gla_treshold > 1.0)) return FALSE;
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}

int parse_mixture (int ac, char *av[]) {
  int i,a;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-E") == 0) {
	p = av[i]+2;
	epsilon = atof(strcpy(buf,p));
	if (!(epsilon < 0.5)) return FALSE;
      } else if (strcmp(s,"-k") == 0) {
	p = av[i]+2;
	mixture_classes = atoi(strcpy(buf,p))+1;
      } else if (strcmp(s,"-s") == 0) {
	p = av[i]+2;
	sample_mixture = atoi(strcpy(buf,p))+1;
      } else {
	return FALSE;
      }
    }
  }
  if (mixture_classes == 0) return FALSE;
  filebase = av[ac-1];
  return TRUE;
}

#ifdef _TEST_ALG1
int parse_test1 (int ac, char *av[]) {
  int i,a;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  kstart = 0;
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-k") == 0) {
	p = av[i]+2;
	kstart = atoi(strcpy(buf,p))+1;
      } else if (strcmp(s,"-r") == 0) {
	p = av[i]+2;
	t1_rs_count = atoi(strcpy(buf,p))+1;
      } else if (strcmp(s,"-t") == 0) {
	p = av[i]+2;
	t1_trials = atoi(strcpy(buf,p))+1;
      } else if (strcmp(s,"-e") == 0) {
	t1_extra_iter = TRUE;
      } else {
	return FALSE;
      }
    }
  }
  if (kstart == 0) return FALSE;
  filebase = av[ac-1];
  return TRUE;
}
#endif

#ifdef _TEST_ALG2
int parse_test2 (int ac, char *av[]) {
  int i,a;
  char buf[10];
  char b[10];
  char *s;
  char *p;
  
  if (ac < 3) {
    return FALSE;
  }
  kstart = 0;
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-t") == 0) {
	p = av[i]+2;
	t2_treshold = atoi(strcpy(buf,p));
      } else {
	return FALSE;
      }
    }
  }
  filebase = av[ac-1];
  return TRUE;
}
#endif

int parse_bootstrap (int ac, char *av[]) {
  int i,a,tmp;
  char buf[10];
  char b[10];
  char *p;
  char *s;
  
  if (ac < 3) {
    return FALSE;
  }
  a = ac-1;
  if (ac > 2) {
    for (i=2;i<a;i++) {
      s = strncpy(b,av[i],2);
      s[2] = 0;
      if (strcmp(s,"-v") == 0) {
	p = av[i]+2;
	vecs_to_gen = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-P") == 0) {
	save_best_boots = TRUE;
      } else if (strcmp(s,"-J") == 0) {
	use_jeffreys_prior = TRUE;
      } else if (strcmp(s,"-r") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (tmp == 1) ls_heuristic = HEUR_SPLITJOIN1;
	else if (tmp == 2) ls_heuristic = HEUR_REPLACEWORST;
	else if (tmp == 3) ls_heuristic = HEUR_REPLACESMALLEST;
	else if (tmp == 4) ls_heuristic = HEUR_RANDOMSWAP;
	else if (tmp == 5) ls_heuristic = HEUR_SPLITJOIN2;
	else if (tmp == 6) {
	  ls_heuristic_cycler = TRUE;
	  ls_heuristic = HEUR_SPLITJOIN1;
	}
	else return FALSE;
      } else if (strcmp(s,"-N") == 0) {
	p = av[i]+2;
	bootstrap_size = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-K") == 0) {
	p = av[i]+2;
	bootstrap_k = atoi(strcpy(buf,p))+1;
      } else if (strcmp(s,"-E") == 0) {
	if (strlen(s) == 2) decreasing_epsilon = TRUE;
	else {
	  p = av[i]+2;
	  epsilon = atof(strcpy(buf,p));
	  if (!(epsilon < 0.5)) return FALSE;
	}
      } else if (strcmp(s,"-I") == 0) {
	p = av[i]+2;
	bootstrap_i = atoi(strcpy(buf,p));
      } else if (strcmp(s,"-q") == 0) {
	verbose = FALSE;
      } else if (strcmp(s,"-w") == 0) {
	use_class_weights = TRUE;
      } else if (strcmp(s,"-c") == 0) {
	p = av[i]+2;
	tmp = atoi(strcpy(buf,p));
	if (centroid_type != CT_RAND) return FALSE;
	if (tmp == 1) centroid_type = CT_CLASSIC;
	else if (tmp == 2) centroid_type = CT_SRAND;
	else if (tmp == 3) centroid_type = CT_SEMI;
	else if (tmp == 5) {
	  centroid_type = CT_PNN;
	  search_type = ST_NAUTO;
	  if ((distance_type == DT_L1_CL) || (distance_type == DT_L2_CL)) distance_type = DT_L2;
	  max_iter = 1;
	}
	else return FALSE;
      } else {
	return FALSE;
      }
    }
  }
  if (bootstrap_k == 0) return FALSE;
  filebase = av[ac-1];
  return TRUE;
}

int parse (int ac, char *av[]) {
  char b[10];
  char *s;
  
  if (ac < 2) {
    return FALSE;
  }
  s = strncpy(b,av[1],8);
  s[8] = 0;
  if (strcmp(s,"identify") == 0) {
    module = MOD_IDENT;
  } else {
    s = strncpy(b,av[1],8);
    s[8] = 0;
    if (strcmp(s,"classify") == 0) {
      module = MOD_CLASSIFY;
    } else {
      s = strncpy(b,av[1],7);
      s[7] = 0;
      if (strcmp(s,"report") == 0) {
	module = MOD_REPORT;
      } else {
	s = strncpy(b,av[1],8);
	s[8] = 0;
	if (strcmp(s,"generate") == 0) {
	  module = MOD_GEN;
	} else {
	  s = strncpy(b,av[1],7);
	  s[7] = 0;
	  if (strcmp(s,"compare") == 0) {
	    module = MOD_COMPARE;
	  } else {
	    s = strncpy(b,av[1],9);
	    s[9] = 0;
	    if (strcmp(s,"bootstrap") == 0) {
	      module = MOD_BOOTSTRAP;
	    } else {
	      s = strncpy(b,av[1],8);
	      s[8] = 0;
	      if (strcmp(s,"splitgla") == 0) {
		module = MOD_SPLIT;
	      } else {
		s = strncpy(b,av[1],7);
		s[7] = 0;
		if (strcmp(s,"joingla") == 0) {
		  module = MOD_JOIN;
		} else {
		  s = strncpy(b,av[1],4);
		  s[4] = 0;
		  if (strcmp(s,"tree") == 0) {
		    module = MOD_TREE;
		  } else {
		    s = strncpy(b,av[1],10);
		    s[10] = 0;
		    if (strcmp(s,"cumulative") == 0) {
		      module = MOD_CUMULATIVE;
		    } else {
		      s = strncpy(b,av[1],9);
		      s[9] = 0;
		      if (strcmp(s,"centroids") == 0) {
			module = MOD_CENTROIDS;
		      } else {
			s = strncpy(b,av[1],8);
			s[8] = 0;
			if (strcmp(s,"sortpart") == 0) {
			  module = MOD_SORTP;
			} else {
			  s = strncpy(b,av[1],7);
			  s[7] = 0;
			  if (strcmp(s,"mixture") == 0) {
			    module = MOD_MIXTURE;
			  } else {
			    s = strncpy(b,av[1],9);
			    s[9] = 0;
			    if (strcmp(s,"intersect") == 0) {
			      module = MOD_INTERSECT;
			    } else {
			      s = strncpy(b,av[1],8);
			      s[8] = 0;
			      if (strcmp(s,"function") == 0) {
				module = MOD_FUNCTION;
#ifdef _TEST_ALG1
			      } else {
				s = strncpy(b,av[1],5);
				s[5] = 0;
				if (strcmp(s,"test1") == 0) {
				  module = MOD_TEST1;
#endif
#ifdef _TEST_ALG2
			      } else {
				s = strncpy(b,av[1],5);
				s[5] = 0;
				if (strcmp(s,"test2") == 0) {
				  module = MOD_TEST2;
#endif
				} else {
				  return FALSE;
				}
#ifdef _TEST_ALG1
			      }
#endif
#ifdef _TEST_ALG2
			      }
#endif
			    }
			  }
			}
		      }
		    }
		  }
		}
	      }
	    }
	  }
	}
      }
    }
  }
  switch (module) {
  case MOD_IDENT:      if (!parse_identify(ac,av)) help_text_identify(stdout); break;
  case MOD_CLASSIFY:   if (!parse_classify(ac,av)) help_text_classify(stdout); break;
  case MOD_REPORT:     if (!parse_report(ac,av)) help_text_report(stdout); break;
  case MOD_GEN:        if (!parse_generate(ac,av)) help_text_generate(stdout); break;
  case MOD_COMPARE:    if (!parse_compare(ac,av)) help_text_compare(stdout); break;
  case MOD_BOOTSTRAP:  if (!parse_bootstrap(ac,av)) help_text_bootstrap(stdout); break;
  case MOD_SPLIT:      if (!parse_fclassify(ac,av)) help_text_fclassify(stdout); break;
  case MOD_JOIN:       if (!parse_sclassify(ac,av)) help_text_sclassify(stdout); break;
  case MOD_TREE:       if (!parse_tree(ac,av)) help_text_tree(stdout); break;
  case MOD_CUMULATIVE: if (!parse_cumulative(ac,av)) help_text_cumulative(stdout); break;
  case MOD_CENTROIDS:  if (!parse_centroids(ac,av)) help_text_centroids(stdout); break;
  case MOD_SORTP:      if (!parse_sortpart(ac,av)) help_text_sortpart(stdout); break;
  case MOD_INTERSECT:  if (!parse_cut(ac,av)) help_text_cut(stdout); break;
  case MOD_MIXTURE:    if (!parse_mixture(ac,av)) help_text_mixture(stdout); break;
  case MOD_FUNCTION:   if (!parse_function(ac,av)) help_text_function(stdout); break;
#ifdef _TEST_ALG1
  case MOD_TEST1:      if (!parse_test1(ac,av)) help_text_test1(stdout); break;
#endif
#ifdef _TEST_ALG2
  case MOD_TEST2:      if (!parse_test2(ac,av)) help_text_test2(stdout); break;
#endif
  default: return FALSE;
  }
  return TRUE;
}

void help_text_identify (FILE *f) {
  help_text(f);
  fprintf(f,"identify:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -M            Check for exact matches by id\n");
  fprintf(f,"  -Pfilename    Save partition with new vectors\n");
  fprintf(f,"  -t            Use trashcan class\n");
  fprintf(f,"  -EFF          Set Epsilon to FF, must be bellow one (default: %f)\n",epsilon);
  fprintf(f,"  -fX           Set error function\n");
  fprintf(f,"                1: Hamming Distance (Gower), 2: L1 norm (MAE),\n");
  fprintf(f,"                3: L2 norm (MSE), 4: Shannon codelength,\n");
  fprintf(f,"                5: L1+Codelength (Default), 6: L2+Codelength \n");
/*  fprintf(f,"                7: Codelength with SR, 8: Codelength with SA\n"); */
  fprintf(f,"  -w            Use class weighted codelength instead of basic one\n");
  exit(1);
}

void help_text_classify (FILE *f) {
  help_text(f);
  fprintf(f,"classify:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -EFF          Set Epsilon to FF, must be bellow one (default: %f)\n",epsilon);
  fprintf(f,"  -n[XX]        Non automatic search (use at least XX trials)\n");
  fprintf(f,"  -aXX          Start automatic search on at least XX trials\n");
  /* fprintf(f,"  -t            Use trashcan class\n"); */
  fprintf(f,"  -Lfilename    Load centroids from file and run one trial\n");
  fprintf(f,"  -l            Log centroids of each classificiation\n");
  fprintf(f,"  -C            Choose trial by smallest overall distortion\n");
  fprintf(f,"                (depends on -f) instead of SC\n");
  fprintf(f,"  -R            Use rounded centroids\n");
  /* fprintf(f,"  -m            Analyse missing data, save for future use\n"); */
  fprintf(f,"  -B[FF]        Require smaller overall distortion (depends on -f)\n"); 
  fprintf(f,"                on each subsequent k\n");
  fprintf(f,"                Require first overall distortion to be smaller than FF\n");
  fprintf(f,"  -bXX          Begin of XX classes (default: %d)\n",kstart);
  fprintf(f,"  -sXX          Search until XX classes, 0 = automatic (default: %d)\n",kstop);
  fprintf(f,"  -SXX          Stop if no improvement in XX classes (default: %d)\n",kstopwhen);
  fprintf(f,"  -cX           Set initial centroid method\n");
  fprintf(f,"                1: Random, 2: Statistically random,\n");
  fprintf(f,"                3: Statistically cointoshed, 4: Random vectors,\n");
  fprintf(f,"                5: Use RPNN for centroids\n");
  fprintf(f,"  -FX           Safety limit for trials (default: %d)\n",safety_limit);
  fprintf(f,"  -fX           Set error function\n");
  fprintf(f,"                1: Hamming Distance (Gower), 2: L1 norm (MAE),\n");
  fprintf(f,"                3: L2 norm (MSE), 4: Shannon codelength,\n");
  fprintf(f,"                5: L1+Codelength (Default), 6: L2+Codelength \n");
/*  fprintf(f,"                7: Codelength with SR, 8: Codelength with SA\n"); */
  fprintf(f,"  -r            Intialize with MSE and perform Local Search with strategy\n");
  fprintf(f,"                1: Split and Join v1, 2: Replace worst class,\n");
  fprintf(f,"                3: Replace smallest class, 4: Random swap\n");
  fprintf(f,"                5: Split and Join v2, 6: Random swap v2\n");
  fprintf(f,"                7: Cylce all strategies\n");
  fprintf(f,"                Enhance result using GLA as set by -f1,2,3,4\n");
  fprintf(f,"  -jX           Set heuristic step length to XX (default=%d)\n",ls_heuristic_count);
  fprintf(f,"  -w            Use class weighted codelength instead of basic one\n");
  fprintf(f,"                for (-f4,5,6,7)\n");
  fprintf(f,"  -eX           Empty cell (orphaned centroid) fix method\n");
  fprintf(f,"                1. Replace by worst match, 2. Replace by random vector\n");
  fprintf(f,"                3. 1. + extra iteration (for -w only), 4. 2. + extra iteration\n");
  fprintf(f,"  -J            Use stochastic complexity with Jeffrey's prior\n");
  exit(1);
}

void help_text_compare (FILE *f) {
  help_text(f);
  fprintf(f,"compare:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -M            Check for exact matches by id\n");
  fprintf(f,"  -V1           Print values\n");
  fprintf(f,"  -V2           Print percentanges\n");
  fprintf(f,"  -V3           Print match lists\n");
  exit(1);
}

#ifdef _TEST_ALG1
void help_text_test1 (FILE *f) {
  help_text(f);
  fprintf(f,"test1:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -kxx          Generate XX classes (mandatory)\n");
  fprintf(f,"  -txx          Try algorithm for XX times\n");
  fprintf(f,"  -rxx          Perform XX random swaps for each trial\n");
  fprintf(f,"  -e            Perform extra iteration after random swap\n");
  exit(1);
}
#endif

#ifdef _TEST_ALG2
void help_text_test2 (FILE *f) {
  help_text(f);
  fprintf(f,"test2:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -txx          Use additional treshold of XX\n");
  exit(1);
}
#endif

void help_text_bootstrap (FILE *f) {
  help_text(f);
  fprintf(f,"bootstrap:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -P            Save best partition\n");
  fprintf(f,"  -EFF          Set Epsilon to FF, must be bellow one (default: %f)\n",epsilon);
  fprintf(f,"  -NXX          Run bootstrap for sample of XX partitions\n");
  fprintf(f,"  -KXX          Run bootstrap for XX classes\n");
  fprintf(f,"  -IXX          Define XX iterations for monte-carlo bootstrap analysis\n");
  fprintf(f,"  -J            Use stochastic complexity with Jeffrey's prior\n");
  fprintf(f,"  -w            Use class weighted codelength instead of basic one\n");
  exit(1);
}

void help_text_generate (FILE *f) {
  help_text(f);
  fprintf(f,"generate:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -vXX          Generate XX vectors\n");
  fprintf(f,"  -GX           Set data generator type\n");
  fprintf(f,"                1: Random, 2: Multivariate Bernouli,\n");
  fprintf(f,"                3: Markov autmata, 4: Random vectors\n");
  /* fprintf(f,"                5: Clone HMOs with error\n"); */
  /* fprintf(f,"  -e[FF]        Amount of error in -G5\n"); */
  fprintf(f,"  -u            Pick unique vectors in -G4\n");
  exit(1);
}

void help_text_report (FILE *f) {
  help_text(f);
  fprintf(f,"report:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -d            Print digits instead of percentanges\n");
  fprintf(f,"  -a            Print affinity matrix instead of distances when -p1\n");
  fprintf(f,"  -w            Use class weighted codelength instead of basic one\n");
  fprintf(f,"  -pX           Reporting parameters\n");
  fprintf(f,"                1: Nearness matrix, 2: Total frequencies,\n");
  fprintf(f,"                3: Partition, 4: Matches, 5: Neighborhood\n");
  fprintf(f,"                6: Frequencies, 0: All\n");
  exit(1);
}

void help_text_fclassify (FILE *f) {
  help_text(f);
  fprintf(f,"splitgla:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -A            Use absolute worst match instead of random version\n");
  fprintf(f,"  -EFF          Set Epsilon to FF, must be bellow one (default: %f)\n",epsilon);
  fprintf(f,"  -SXX          Stop if no improvement in XX classes (default: 10)\n");
  fprintf(f,"  -J            Use stochastic complexity with Jeffrey's prior\n");
  exit(1);
}

void help_text_mixture (FILE *f) {
  help_text(f);
  fprintf(f,"mixture:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -EFF          Set Epsilon to FF, must be bellow one (default: %f)\n",epsilon);
  fprintf(f,"  -kXX          Do k classes (mandatory)\n");
  fprintf(f,"  -sXX          Sample best partition by likelihood from XX tries\n");
  exit(1);
}

void help_text_sclassify (FILE *f) {
  help_text(f);
  fprintf(f,"joingla:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -A            Use absolute best match instead of random version\n");
  fprintf(f,"  -EFF          Set Epsilon to FF, must be bellow one (default: %f)\n",epsilon);
  fprintf(f,"  -TFF          GLA starting distance trexhold (default: %.4f)\n",gla_treshold);
  fprintf(f,"  -jXX          Use SC joining method to XX partitions\n");
  fprintf(f,"  -J            Use stochastic complexity with Jeffrey's prior\n");
  exit(1);
}

void help_text_tree (FILE *f) {
  help_text(f);
  fprintf(f,"tree:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -H1           Use hellinger distance\n");
  fprintf(f,"  -H2           Use custom distance\n");
  fprintf(f,"  -H3           Use sc minimzer\n");
  fprintf(f,"  -H4           Use parsimony\n");
  fprintf(f,"  -J            Use stochastic complexity with Jeffrey's prior\n");
  exit(1);
}

void help_text_cumulative (FILE *f) {
  help_text(f);
  fprintf(f,"cumulative:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -O            Apply set in order of ID information\n");
  fprintf(f,"  -I            Apply set in input order of the training set\n");
  fprintf(f,"  -S            Use stochastic complexity instead of bayesian predictivity\n");
  fprintf(f,"  -c            Save by stochastic complexity instead of predictive fit\n");
  fprintf(f,"  -n            Do not generate new classes, use exsisting classes only\n");
  fprintf(f,"  -sXX          Try to get XX samples in cumulative anylisis tools\n");
  fprintf(f,"  -EFF          Set Epsilon to FF, must be bellow one (default: %f)\n",epsilon);
  fprintf(f,"  -NXX          Run analysis tool with XX samples\n");
  fprintf(f,"  -DXX          Fixed delta value (default: %d)\n",real_delta_value);
  fprintf(f,"  -dXX          Chaning delta value (XX-k)\n");
  exit(1);
}

void help_text_centroids (FILE *f) {
  help_text(f);
  fprintf(f,"centroids:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  exit(1);
}

void help_text_sortpart (FILE *f) {
  help_text(f);
  fprintf(f,"sortpart:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  exit(1);
}

void help_text_function (FILE *f) {
  help_text(f);
  fprintf(f,"function:\n");
  fprintf(f,"  -EFF          Set Epsilon to FF, must be bellow one (default: %f)\n",epsilon);
  fprintf(f,"  -fX           Set error function\n");
  fprintf(f,"                1: Hamming Distance (Gower), 2: L1 norm (MAE),\n");
  fprintf(f,"                3: L2 norm (MSE), 4: Shannon codelength,\n");
  fprintf(f,"                5: L1+Codelength (Default), 6: L2+Codelength \n");
/*   fprintf(f,"                7: Codelength with SR, 8: Codelength with SA\n"); */
  fprintf(f,"  -w            Use class weighted codelength instead of basic one\n");
  fprintf(f,"                for (-f4,5,6,7)\n");
  fprintf(f,"  -q            No output (quiet)\n");
  exit(1);
}

void help_text_cut (FILE *f) {
  help_text(f);
  fprintf(f,"intersect:\n");
  fprintf(f,"  -q            No output (quiet)\n");
  fprintf(f,"  -r            Perform relative intersection operation\n");
  fprintf(f,"  -s            Perform minimal intersection operation\n");
  fprintf(f,"  -m            Perform maximal intersection operation\n");
  fprintf(f,"  -aXX          Analyse data with maximal intersection operation (use XX classes)\n");
  fprintf(f,"  -AXX          Analyse stability of maximal intersection operation (use XX classes)\n");
  exit(1);
}

void help_text (FILE *f) {
  fprintf(f,"USAGE: binclass <help|generate|classify|joingla|splitgla|report|compare|bootstrap|tree|cumulative|centroids|sortpart|intersect|mixture|function> [options] <filebase>\n\n");
}
