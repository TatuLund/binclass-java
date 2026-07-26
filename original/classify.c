/*
Functions for "training" classification, ie. optimization
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "const.h"
#include "vars.h"
#include "binset.h"
#include "distmin.h"
#include "binstuff.h"
#include "glainf.h"
#include "bottom.h"
#include "report.h"
#include "centroid.h"
#include "logfile.h"

/* prototypes */

/* INTERFACE FOR CLASSIFICATION MODULE */

void classify_vectors (char *datfile, char* outfile, char *parfile, char *ctrfile, char *misfile, char *hdrfile);
/* according to parameters in vars.c call suitable search algorithm */


/* NONAUTOMATIC SEARCH */

void search_classes_nonautomatic (ST *V, char *outfile, char *ctrfile, char *parfile);
/* apply use_gla for given interval of k-values (kstart,kstop) with given number */
/* of trials (max_iter) */

void search_loaded (ST *V, char *outfile, char *parfile);
/* apply use_gla_load_centroids for predefined centroids */

/* OTHER */

void search_classes (ST *V, char *outfile, char *ctrfile, char *parfile);
/* old search mehtod UNUSED! */

/* AUTOMATIC SEARCH */

void sca_main (ST *V, char *outfile, char *ctrfile, char *parfile);
/* interface for automatic search algorithm, first call sca_scanner and */
/* after that call sca_pingpong */
ST *sca_scanner (ST *V, double *scs, double *scmin, int *kmin, int *lastk, time_t stm, time_t *ltm, char *outfile, char *parfile, char *ctrfile);
/* forward scanner: apply use_gla starting from given k-value (kstart) until */
/* there are no enhacement in SC in given (kstowhen) steps */
ST *sca_pingpong (ST *V, double *scs, double scmin, int kmin, int lastk, time_t stm, time_t ltm, char *outfile, char *parfile, char *ctrfile);
/* enhancement procedure: test further trials with use_gla to k-values whose SC */
/* values fill certain criteria */
void sca_messages (time_t stm, time_t *ltm, FILE *o);
/* auxiliary subroutine: output timestamp and some other values */

/* SUBROUTINES */

void methods (FILE *o);
/* output description of main parameters to file o */
void print_profile (FILE *f, int l, int s);
/* output statistical profile of data set to file f */
double calculate_criteria (FILE *f, Partition *P, InfCentroid *C, double *lasti);
/* output and calculate critical values of the classification */

/* implementation */

void print_profile (FILE *f, int l, int s) {
  int i;
  double p;
  
  fprintf(f,"\nStatistical profile of ones after coin toshing:\n");
  for (i=1;i<l;i++) {
    p = (total_freqs[i]/(double)s);
    fprintf(f," %3d: %4d (%1.4f)\n",i,((int)total_freqs[i]),p);
    total_freqs[i] = p;
  }
}

double calculate_criteria (FILE *f, Partition *P, InfCentroid *C, double *lasti) {
  double sc,i1,i2;
  double d = 0.0;
  int k;
  
  /* Calculate all criteria */
  if (verbose) fprintf(stdout,"Calculating stochastic complexity\n");
  k = P->k;
  sc = C->SC;

  if (distance_type == DT_L1) d = overall_MAE(P,C);
  else if (distance_type == DT_L2) d = overall_MSE(P,C);
  else if (distance_type == DT_HAM) d = overall_distortion(P,C);
  i2 = 0.0;
  if (distance_type > DT_L2) {
    i1 = C->I;
    i2 = shannon_entropy(P,C,TRUE);
    C->I2 = i2;
  } else {
    i1 = average_codelength(P,C,FALSE);
    C->I = i1;
    i2 = shannon_entropy(P,C,FALSE);
    C->I2 = i2;
  }
  if (distance_type == DT_L1_CL) d = i1;
  else if (distance_type == DT_L2_CL) d = i1;
  else if (distance_type == DT_CL) d = i1;
  else if (distance_type == DT_SA) d = i1;
  else if (distance_type == DT_SR) d = i1;

  if (log_file) fprintf(f,"Results: ak  = %d\n sc  = %1.5f \n cl1 = %1.5f\n cl2 = %1.5f\n d   = %1.5f\n",(k-1),sc,i1,i2,d);
  if (verbose) fprintf(stdout,"ak = %2d, SC = %2.4f, I1 = %2.4f, Itp = %2.4f  (d = %2.4f)\n",(k-1),sc,i1,i2,d);

  if (*lasti < d) {
    fprintf(stdout,"WARNING: There might be too few trials!\n");
  }
  *lasti = d;

  return sc;
}

void search_loaded (ST *V, char *outfile, char *parfile) {
  FILE *p;
  Partition *P;
  const char *func = "search_loaded";

  P = use_gla_load_centroids(V,outfile);
  
  if (verbose) fprintf(stdout,"  Saving classification ..");
  if ((p = fopen(parfile,"w")) == NULL) file_error((char *)parfile,(char *)func);
  inf_write_partition(p,P);
  fclose(p);
  if (verbose) fprintf(stdout,".. ok\n");
  
  V = partition_to_set(P);
  /* Deallocating space of parition */
  deallocate_partition(P);
  
  deallocate_set(V);
}

void search_classes (ST *V, char *outfile, char *ctrfile, char *parfile) {
  FILE *o;
  FILE *p;
  int ii,a,aa,kc,k,i,s;
  int kmin = 1;
  double scmin,sc,lasti;
  double *scs;
  time_t ltm;
  time_t stm;
  InfCentroid *C;
  Partition *P;
  const char *func = "search_classes";
  
  if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  
  s = size(V);
  /* print_profile(o,(V->el->length),s);*/
  
  fprintf(o,"SCAN\n");
  if (verbose) fprintf(stdout,"\nStarting search\n\n");
  scmin = unassigned_sc();
  
  a = 4;
  max_iter = iter_base;
  if (kstop == 0) {
    a = 4;
    max_iter = iter_base;
  }
  else if ((kstop-kstart) < 8) {
    a = 3;
    max_iter = iter_base * 2;
  }
  else if ((kstop-kstart) < 4) {
    a = 2;
    max_iter = iter_base * 4;
  }
  else if ((kstop-kstart) < 2) {
    a = 1;
    max_iter = iter_base * 8;
  }
  aa = a+1;
  
  /* Get time constant and intialize seed */
  stm = time(&stm);
  set_rand(stm);
  ltm = stm;
  fprintf(o,"time:%d\n",(int) stm);
  if (verbose) fprintf(stdout,"Starting time constant: %d\n",(int) stm);
  kc = 0;
  fclose(o);
  if (log_centroids) {
    p = fopen(ctrfile,"w");
    fprintf(p,"Centroids\n--\n\n");
    fclose(p);
  }
  if ((scs = malloc((s+1)*sizeof(double))) == NULL) out_of_mem();
  for (i=0;i<(s+1);i++) scs[i] = unassigned_sc();
  
  lasti = unassigned_sc();
  
  for (ii=1;ii<aa;ii++) {
    k = kstart;
    while (((kstop != 0) && (k < (kstop+1))) || ((kstop == 0) && (kc < kstopwhen))) {
      
      /* Allocating space for parition */
      P = allocate_partition(k+1);

      C = use_gla(V,P,(k+1),outfile,lasti,require_better,filter_exact_k,(-1.0));

      if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
      /* Calculate all criteria */
      sc = calculate_criteria(o,P,C,&lasti);
      if (scs[(C->k)-1] > sc) scs[(C->k)-1] = sc;
      
      if (log_centroids) {
	p = fopen(ctrfile,"a");
	save_centroids(p,C);
	fclose(p);
      }
      if (sc < scmin) {
	if (verbose) {
	  fprintf(stdout,"  New best classification: %d\n",k);
	  fprintf(stdout,"  Actual classes: %d\n",((C->k)-1));
	  fprintf(stdout,"  Saving classification ..");
	}
	p = fopen(parfile,"w");
	inf_write_partition(p,P);
	kmin = k;
	scmin = sc;
	fclose(p);
	if (verbose) fprintf(stdout,".. ok\n");
	kc = 0;
      } else {
	kc++;
      }
      V = partition_to_set(P);
      deallocate_partition(P);
      deallocate_centroids(C);
      
      /* Messages */
      sca_messages(stm,&ltm,o);
      k++;
      fclose(o);
    }
    max_iter = (max_iter * 2);
    kstart = (kmin - a);
    if (kstart < 1) kstart = 1;
    kstop = (kmin + a);
    a = a / 2;
    lasti = unassigned_sc();
  }
  if (verbose) fprintf(stdout,"Partition Ready\n");
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    if (log_file) fprintf(o,"/SEARCH\n");
    log_function(o,scs,kstop);
    fclose(o);
  }
  deallocate_set(V);
}


void search_classes_nonautomatic (ST *V, char *outfile, char *ctrfile, char *parfile) {
  FILE *o;
  FILE *p;
  int kc,k,i,mk;
  double scmin,sc,lasti;
  double *scs;
  time_t ltm;
  time_t stm;
  time_t dtm;
  time_t tm;
  InfCentroid *C;
  Partition *P;
  const char *func = "search_classes_nonautomatic";
  
  if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  
  /* print_profile(o,(V->el->length),size(V));*/
  
  fprintf(o,"--\n\nStarting search\n\n");
  if (verbose) fprintf(stdout,"\nStarting search\n\n");
  scmin = 10000.0;
  
  /* Get time constant and intialize seed */
  stm = time(&stm);
  set_rand(stm);
  ltm = stm;
  fprintf(o,"Starting time constant: %d\n",(int) stm);
  if (verbose) fprintf(stdout,"Starting time constant: %d\n",(int) stm);
  kc = 0;
  fclose(o);
  if (log_centroids) {
    p = fopen(ctrfile,"w");
    fprintf(p,"Centroids\n--\n\n");
    fclose(p);
  }
  mk = kstop+1;
  if (mk > maximum_class_number) mk = maximum_class_number;

  if ((scs = malloc(mk*sizeof(double))) == NULL) out_of_mem();
  for (i=0;i<mk;i++) scs[i] = 1000.0;
  lasti = 1000.0;
  
  k = kstart;
  if (k > maximum_class_number) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    fprintf(o,"\nOperation halted: More classes requested than possible!\n\n");
    fclose(o);
    fprintf(stdout,"\nMore classes requested than possible\n");
    exit(1);
  }

  while (k < mk) {
    
    /* Allocating space for parition */
    P = allocate_partition(k+1);
    
    C = use_gla(V,P,(k+1),outfile,lasti,require_better,filter_exact_k,(-1.0));

    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);

    /* Calculate all criteria */
    sc = calculate_criteria(o,P,C,&lasti);
    if (scs[(C->k)-1] > sc) scs[(C->k)-1] = sc;
    
    if (log_centroids) {
      p = fopen(ctrfile,"a");
      save_centroids(p,C);
      fclose(p);
    }
    if (sc < scmin) {
      if (verbose) {
	fprintf(stdout,"  New best classification: %d\n",k);
	fprintf(stdout,"  Actual classes: %d\n",((C->k)-1));
	fprintf(stdout,"  Saving classification ..");
      }
      fprintf(o,"  New best classification: %d\n",k);
      fprintf(o,"  Actual classes: %d\n",((C->k)-1));
      p = fopen(parfile,"w");
      inf_write_partition(p,P);
      scmin = sc;
      fclose(p);
      if (verbose) fprintf(stdout,".. ok\n");
      kc = 0;
    } else {
      kc++;
      fprintf(o,"  Tries since best classification: %d\n",kc);
    }
    V = partition_to_set(P);
    /* Deallocating space of parition */
    deallocate_partition(P);
    /* Deallocating space of last best centroids */
    deallocate_centroids(C);
    /* Messages */
    tm = time(&tm);
    dtm = (tm - stm);
    fprintf(o,"Time ellapsed since start:                ");
    print_time(o,dtm);
    if (verbose) {
      fprintf(stdout,"Time ellapsed since start:                ");
      print_time(stdout,dtm);
    }
    dtm = (tm - ltm);
    fprintf(o,"Time ellapsed for current classification: ");
    print_time(o,dtm);
    if (verbose) {
      fprintf(stdout,"Time ellapsed for current classification: ");
      print_time(stdout,dtm);
    }
    ltm = tm;
    if (verbose) fprintf(stdout,"--\n\n");
    fprintf(o,"--\n\n");
    k++;
    fclose(o);
  }
  if (verbose) fprintf(stdout,"Partition Ready\n");
  if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  fprintf(o,"\nSC as function of k\n--\n");
  for (i=0;i<mk;i++) {
    if (scs[i] < 1000.0)	fprintf(o,"%3d: %2.4f\n",i,scs[i]);
  }
  fclose(o);
  deallocate_set(V);
}


void sca_messages (time_t stm, time_t *ltm, FILE *o) {
  time_t tm;
  time_t dtm;
  
  /* Messages */
  tm = time(&tm);
  dtm = (tm - stm);
  if (log_file) {
    fprintf(o,"Total runtime: ");
    print_time(o,dtm);
  }
  if (verbose) {
    fprintf(stdout,"Time ellapsed since start:                ");
    print_time(stdout,dtm);
  }
  dtm = (tm - *ltm);
  if (log_file) {
    fprintf(o,"Time:          ");
    print_time(o,dtm);
  }
  if (verbose) {
    fprintf(stdout,"Time ellapsed for current classification: ");
    print_time(stdout,dtm);
  }
  *ltm = tm;
  if (verbose) fprintf(stdout,"--\n\n");
}

ST *sca_scanner (ST *V, double *scs, double *scmin, int *kmin, int *lastk, time_t stm, time_t *ltm, char *outfile, char *parfile, char *ctrfile) {
  const char *func = "sca_scanner";
  int k,kc;
  double lasti;
  double sc;
  FILE *o = NULL;
  FILE *p;
  Partition *P;
  InfCentroid *C;
  
  if (V == NULL) internal_error((char *)func);
  
  /* Scanning phase: */
  /* - Search classification for each subsequent k, whose codelength is better */
  /* - Stop when no better SC in kstopwhen steps */
  
  if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  if (log_file) fprintf(o,"Scanning from %d until no enahnace in %d steps\n",kstart,kstopwhen);
  if (log_file) fclose(o);
  if (verbose) fprintf(stdout,"Scanning from %d until no enhance in %d steps\n",kstart,kstopwhen);
  max_iter = iter_base;
  lasti = first_d;
  k = kstart;
  if (k > maximum_class_number) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    fprintf(o,"\nOperation halted: More classes requested than possible!\n\n");
    fclose(o);
    fprintf(stdout,"\nMore classes requested than possible\n");
    exit(1);
  }
  kc = 0;
  
  while ((kc < kstopwhen) && (k < (maximum_class_number+1))) {
    P = allocate_partition(k+2);
    C = use_gla(V,P,(k+1),outfile,lasti,TRUE,filter_exact_k,(-1.0));
    
    if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    sc = calculate_criteria(o,P,C,&lasti);
    
    if (scs[(C->k)-1] > sc) scs[(C->k)-1] = sc;
    
    if (log_centroids) {
      p = fopen(ctrfile,"a");
      save_centroids(p,C);
      fclose(p);
    }
    if (sc < *scmin) {
      if (verbose) fprintf(stdout,"  New best classification: %d\n",k);
      if (verbose) fprintf(stdout,"  Actual classes: %d\n",((C->k)-1));
      if (verbose) fprintf(stdout,"  Saving classification ..");
      
      p = fopen(parfile,"w");
      inf_write_partition(p,P);
      fclose(p);
      
      *kmin = k;
      *scmin = sc;
      if (verbose) fprintf(stdout,".. ok\n");
      kc = 0;
    } else {
      kc++;
    }
    V = partition_to_set(P);
    deallocate_partition(P);
    deallocate_centroids(C);
    
    /* Messages */
    sca_messages(stm,ltm,o);
    *lastk = k;
    k++;
    if (log_file) fclose(o);
  }
  return V;
}

ST *sca_pingpong (ST *V, double *scs, double scmin, int kmin, int lastk, time_t stm, time_t ltm, char *outfile, char *parfile, char *ctrfile) {
  const char *func = "sca_pingpong";
  int k,nkm,enh;
  double sc,lasti;
  InfCentroid *C;
  Partition *P;
  FILE *o = NULL;
  FILE *p;
  
  if (V == NULL) internal_error((char *)func);

  /* Second phase */
  /* - Ping pong until no better classification */
  
  best_code_length = FALSE;
  enh = TRUE;
  nkm = kmin;
  
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    fprintf(o,"\nEnhancing\n");
    fclose(o);
  }
  
  while (enh) {
    
    if (log_file) {
      if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
      fprintf(o,"Rescanning from %d to %d (ping)\n",kstart,kmin);
      fclose(o);
    }
    if (verbose) fprintf(stdout,"\nRescanning from %d to %d\n",kstart,kmin);
    
    enh = FALSE;
    lasti = (double) unassigned_sc();
    k = kstart;
    while ((k < kmin) && ((scs[k]) > (scs[kmin]))) {
      if ((scs[k]) < (scs[k+1])) {
	P = allocate_partition(k+3);
	lasti = (double) vec_len + 1;
	C = use_gla(V,P,(k+2),outfile,lasti,FALSE,filter_exact_k,(scs[k]));
	if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
	
	/* Calculate all criteria */
	sc = calculate_criteria(o,P,C,&lasti);
	
	if (scs[(C->k)-1] > sc) scs[(C->k)-1] = sc;
	if (log_centroids) {
	  p = fopen(ctrfile,"a");
	  save_centroids(p,C);
	  fclose(p);
	}
	if (sc < scmin) {

	  if (verbose) {
	    fprintf(stdout,"  New best classification: %d\n",(k+1));
	    fprintf(stdout,"  Actual classes: %d\n",((C->k)-1));
	    fprintf(stdout,"  Saving classification ..");
	  }
	  p = fopen(parfile,"w");
	  inf_write_partition(p,P);
	  fclose(p);
	  
	  nkm = (k+1);
	  enh = TRUE;
	  scmin = sc;
	  if (verbose) fprintf(stdout,".. ok\n");
	}
	V = partition_to_set(P);
	deallocate_partition(P);
	deallocate_centroids(C);
	/* Messages */
	sca_messages(stm,&ltm,o);
	if (log_file) fclose(o);
      }
      k++;
    }
    
    kmin = nkm;

    if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    fprintf(o,"Rescanning from %d to %d (pong)\n",lastk,kmin);
    if (log_file) fclose(o);
    if (verbose) fprintf(stdout,"\nRescanning from %d to %d\n",lastk,kmin);
    
    k = lastk;
    while ((k > kmin) && ((scs[k]) > (scs[kmin]))) {
      if ((scs[k]) < (scs[k-1])) {
	P = allocate_partition(k+1);
	lasti = (double) vec_len + 1;
	C = use_gla(V,P,k,outfile,lasti,FALSE,filter_exact_k,(scs[k]));
	if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
	
	/* Calculate all criteria */
	sc = calculate_criteria(o,P,C,&lasti);
	
	if (scs[(C->k)-1] > sc) scs[(C->k)-1] = sc;
	if (log_centroids) {
	  p = fopen(ctrfile,"a");
	  save_centroids(p,C);
	  fclose(p);
	}
	if (sc < scmin) {
	  if (verbose) {
	    fprintf(stdout,"  New best classification: %d\n",(k-1));
	    fprintf(stdout,"  Actual classes: %d\n",((C->k)-1));
	    fprintf(stdout,"  Saving classification ..");
	  }
	  p = fopen(parfile,"w");
	  inf_write_partition(p,P);
	  fclose(p);
	  nkm = (k-1);
	  scmin = sc;
	  enh = TRUE;
	  if (verbose) fprintf(stdout,".. ok\n");
	}
	V = partition_to_set(P);
	deallocate_partition(P);
	deallocate_centroids(C);
	/* Messages */
	sca_messages(stm,&ltm,o);
	if (log_file) fclose(o);
      }
      k = k - 1;
    }
    kmin = nkm;
  }
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    fprintf(o,"End of enhancement phase\n\n");
    fclose(o);
  }
  return V;
}

void sca_main (ST *V, char *outfile, char *ctrfile, char *parfile) {
  const char *func = "sca_main";
  int lastk,kmin,s,i;
  double scmin;
  double *scs;
  FILE *c;
  FILE *o = NULL;
  time_t stm;
  time_t ltm;
  
  if (V == NULL) internal_error((char *)func);
  
  if (log_file) if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  s = size(V);
  if (log_file) log_profile(o,(V->el->length),s);
  
  if (verbose) fprintf(stdout,"\nStarting search\n\n");
  
  /* Get time constant and intialize seed */
  stm = time(&stm);
  set_rand(stm);
  ltm = stm;
  if (log_file) fprintf(o,"Starting time: %d\n",(int)stm);
  if (verbose) fprintf(stdout,"Starting time constant: %d\n",(int)stm);
  if (log_file) fclose(o);
  if (log_centroids) {
    c = fopen(ctrfile,"w");
    fprintf(c,"Centroids\n--\n\n");
    fclose(c);
  }
  /* Initialize vector for saved values of SC */
  if ((scs = malloc((s+1)*sizeof(double))) == NULL) out_of_mem();
  for (i=0;i<(s+1);i++) scs[i] = unassigned_sc();
  
  scmin = unassigned_sc();

  V = sca_scanner(V,scs,&scmin,&kmin,&lastk,stm,&ltm,outfile,parfile,ctrfile);
  
  V = sca_pingpong(V,scs,scmin,kmin,lastk,stm,ltm,outfile,parfile,ctrfile);
  
  if (verbose) fprintf(stdout,"Partition Ready\n");
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
    if (log_file) fprintf(o,"---- ok, the end!\n\n");
    log_function(o,scs,lastk);
    fclose(o);
  }
  deallocate_set(V);
}


void methods (FILE *o) {
  fprintf(o,"\nMethods:\n");
  if (use_jeffreys_prior) fprintf(o,"  Using stochastic complexity with Jeffrey's prior\n");
  else fprintf(o,"  Using stochastic complexity with uniform prior\n");
  if (distance_type == DT_L1_CL) fprintf(o,"  Hybrid L1/Codelength minimization\n");
  else if (distance_type == DT_L2_CL) fprintf(o,"  Hybrid L2/Codelength minimization\n");
  else if (distance_type == DT_SA) fprintf(o,"  Codelength minimization with simulated annealing (SA)\n");
  else if (distance_type == DT_SR) fprintf(o,"  Codelength minimization with stochastic relaxation (SR)\n");
  else if (distance_type == DT_CL) fprintf(o,"  Codelength minimization\n");
  else if (distance_type == DT_L1) fprintf(o,"  Mean absolute error minimization (L1/MAE)\n");
  else if (distance_type == DT_L2) fprintf(o,"  Mean square error minimization (L2/MSE)\n");
  else fprintf(o,"  Average hamming distance minimzation (Gower)\n");
  if (search_type != ST_LCENT) {
    if (best_code_length) fprintf(o,"  Choosing by codelength\n");
    else fprintf(o,"  Choosing by stochatic complexity (SC)\n");
  }
  if ((use_class_weights) && ((distance_type == DT_L1_CL) || (distance_type == DT_L2_CL) || (distance_type == DT_SA) || (distance_type == DT_SR) || (distance_type == DT_CL))) fprintf(o,"  Using class size weighted version of codelength\n");
  if (filter_exact_k) fprintf(o,"  Filter ak=k\n");
  if (alternate_empty_cell_fix && use_class_weights) fprintf(o,"  Using extra iteration in orphaned centroids fix\n");
  if (require_better) fprintf(o,"  Better codelength for k+1 required\n");
  if (rounded_centroids) fprintf(o,"  Rounded centroids are used\n");
  if (ls_heuristic_cycler) fprintf(o,"  Cycling all strategies for Local Search\n");
  else if (ls_heuristic == HEUR_SPLITJOIN1) fprintf(o,"  Using split and join (variation 1) strategy for Local Search\n");
  else if (ls_heuristic == HEUR_SPLITJOIN2) fprintf(o,"  Using split and join (variation 2) strategy for Local Search\n");
  else if (ls_heuristic == HEUR_REPLACEWORST) fprintf(o,"  Using replace worst strategy for Local Search\n");
  else if (ls_heuristic == HEUR_REPLACESMALLEST) fprintf(o,"  Using replace smallest strategy for Local Search\n");
  else if (ls_heuristic == HEUR_RANDOMSWAP) fprintf(o,"  Using random swap strategy for Local Search\n");
  if (search_type == ST_AUTO) fprintf(o,"  Automatic search\n");
  else if (search_type == ST_NAUTO) fprintf(o,"  Search in arbitrary range %d..%d\n",kstart,kstop);
  else if (search_type == ST_ADAP) fprintf(o,"  Adaptive search with trshold: %.4f\n",treshold);
  if (search_type == ST_LCENT) fprintf(o,"  Loading predefined centroids\n");
  else if (centroid_type == CT_SEMI) fprintf(o,"  Semirandom initial centroids\n");
  else if (centroid_type == CT_CLASSIC) fprintf(o,"  Random initial centroids\n");
  else if (centroid_type == CT_SRAND) fprintf(o,"  Statistically cointoshed initial centroids\n");
  else if (centroid_type == CT_PNN)fprintf(o,"  Using PNN algorithm for initial centroids\n");
  else if (centroid_type == CT_RAND )fprintf(o,"  Picking random vectors for initial centroids\n");
  if (trashcan) fprintf(o,"  Trash class is used\n");
}

void classify_vectors (char *datfile, char* outfile, char *parfile, char *ctrfile, char *misfile, char *hdrfile) {
  FILE *o = NULL;
  FILE *f;
  ST *V;
  int s;
  
  const char *func = "classify_vectors";
  
  if (log_file) {
    if ((o = fopen(outfile,"a")) == NULL) file_error(outfile,(char *)func);
  }

  if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
  if (log_file) fprintf(o,"FILES:\n Input file:     %s\n",datfile);
  if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
  if (log_file) fprintf(o," Output file:    %s\n",outfile);
  if (verbose) fprintf(stdout,"Partition file: %s\n",parfile);
  if (log_file) fprintf(o," Partition file: %s\n",parfile);
  if (log_centroids) {
    if (verbose) fprintf(stdout,"Centroid file: %s\n\n",ctrfile);
    if (log_file) fprintf(o," Centroid file:  %s\n",ctrfile);
  }
  
  if (do_dump) {
    if (verbose) fprintf(stdout,"Dump file: %s\n\n",dumpfile);
    if (log_file) fprintf(o,"Dump file:%s\n",dumpfile);
  }
  
  /* Read input data */
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..\n");
  V = read_set(f,hdrfile);
  fclose(f);
  s = size(V);
  if (log_file) {
    fprintf(o,"Size: %d",s);
    if (check_input_set) fprintf(o," (%d)",maximum_class_number); 
    else fprintf(o,"\n");
  }
  coin_tosh(o,V,misfile);
  if (verbose) {
    fprintf(stdout,"Read %d vectors of data\n",s);
    if (check_input_set) fprintf(stdout,"     %d of vecors were different\n",maximum_class_number);
  }
  
  if (do_dump) {
    if (verbose) fprintf(stdout,"Writing dump file\n");
    if ((f = fopen(dumpfile,"w")) == NULL) file_error(dumpfile,(char *)func);
    write_set(f,V);
    fclose(f);
    do_dump = FALSE;
  }
  
  if (verbose) methods(stdout);
  if (log_file) methods(o);
  
  if (log_file) fclose(o);
  if (search_type == ST_AUTO) {
    sca_main(V,outfile,ctrfile,parfile);
  } else if (search_type == ST_NAUTO) {
    search_classes_nonautomatic(V,outfile,ctrfile,parfile);
  } else if (search_type == ST_LCENT) {
    search_loaded(V,outfile,parfile);
  } else if (search_type == ST_ADAP) {
    search_loaded(V,outfile,parfile);
  } else {
    internal_error((char *)func);
  }
  /* Free frequency vector */
  free(total_freqs);
  
}

/* End of clasify.c */
