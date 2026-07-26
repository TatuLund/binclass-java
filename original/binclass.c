/*
BinClass bases on program developed in Pascal by Martin Verlaan.
This is portable and more userfriendly C-version
  of the program by Tatu J. Lund.
Tested on:
  Windows NT 3.5x    - Borland C++ 4.52 & Cygnus GNU-CC v2.7.2 b17.1
  Solaris 2.1        - Native & GNU CC
  Solaris 1.1        - GNU CC
  Linux              - GNU CC v2.7.0
  Amiga OS 3.1       - SAS/C++ 6.57
  SGI IRIX           - Native & GNU CC v2.7.0
  IBM AIX            - Native & GNU CC v2.7.0
  Digital UNIX 4.0   - Native & GNU-CC v2.7.2.1
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "const.h"
#include "bottom.h"
#include "parser.h"
#include "report.h"
#include "vars.h"
#include "adding.h"
#include "classify.h"
#include "gendat.h"
#include "binset.h"
#include "compare.h"
#include "bootstra.h"
#include "logfile.h"
#include "splitgla.h"
#include "joingla.h"
#include "tree.h"
#include "cumulat.h"
#include "centroid.h"
#include "mixture.h"
#include "cut.h"
#include "function.h"
#ifdef _TEST_ALG1
#include "t_alg_1.h"
#endif
#ifdef _TEST_ALG2
#include "t_alg_2.h"
#endif

int main (int argc, char *argv[]) {
  FILE *o;
  FILE *f;
  int l;
  char *datfile;
  char *outfile;
  char *repfile;
  char *parfile;
  char *parfile1;
  char *parfile2;
  char *ctrfile;
  char *genfile;
  char *genfile1;
  char *genfile2;
  char *misfile;
  char *resfile;
  char *hdrfile;
  char *btsfile;
  char *trefile1;
  char *trefile2;
  char *basfile;
  char *cmpfile;
  char *ordfile1;
  char *ordfile2;
  /* Two versions of filename suffixes */
#ifdef __MSDOS__
  const char *dat = ".dat";
  const char *out = ".out";
  const char *rep = ".rep";
  const char *par = ".par";
  const char *par1 = ".pa1";
  const char *par2 = ".pa2";
  const char *ctr = ".ctr";
  const char *gen = ".gen";
  const char *gen1 = ".ge1";
  const char *gen2 = ".ge2";
  const char *mis = ".mis";
  const char *res = ".res";
  const char *hdr = ".hdr";
  const char *bts = ".bts";
  const char *tre1 = ".tr1";
  const char *tre2 = ".tr2";
  const char *bas = ".bas";
  const char *cmp = ".cmp";
  const char *ord1 = ".or1";
  const char *ord2 = ".or2";
#else
  const char *dat = ".data";
  const char *out = ".output";
  const char *rep = ".report";
  const char *par = ".partition";
  const char *par1 = ".partition1";
  const char *par2 = ".partition2";
  const char *ctr = ".centroids";
  const char *gen = ".generated";
  const char *gen1 = ".generated1";
  const char *gen2 = ".generated2";
  const char *mis = ".missing";
  const char *res = ".result";
  const char *hdr = ".header";
  const char *bts = ".bootstrap";
  const char *tre1 = ".tree";
  const char *tre2 = ".treefile";
  const char *bas = ".base";
  const char *cmp = ".compare";
  const char *ord1 = ".order1";
  const char *ord2 = ".order2";
#endif
  const char *func = "main";
  ST *V;
  
  start_text(stdout);
  if (!parse(argc, argv)) {
    help_text(stdout);
    return 1;
  }
  
  l = 2048;
  /* allocate space for filenames */
  if ((datfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((outfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((parfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((parfile1 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((parfile2 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((repfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((ctrfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((genfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((genfile1 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((genfile2 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((misfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((resfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((hdrfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((btsfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((trefile1 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((trefile2 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((basfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((cmpfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((ordfile1 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  if ((ordfile2 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
  
  /* form filenames with appropiate suffixes */
  strcpy(datfile,filebase);
  strcat(datfile,dat);
  strcpy(outfile,filebase);
  strcat(outfile,out);
  strcpy(repfile,filebase);
  strcat(repfile,rep);
  strcpy(parfile,filebase);
  strcat(parfile,par);
  strcpy(parfile1,filebase);
  strcat(parfile1,par1);
  strcpy(parfile2,filebase);
  strcat(parfile2,par2);
  strcpy(ctrfile,filebase);
  strcat(ctrfile,ctr);
  strcpy(genfile,filebase);
  strcat(genfile,gen);
  strcpy(genfile1,filebase);
  strcat(genfile1,gen1);
  strcpy(genfile2,filebase);
  strcat(genfile2,gen2);
  strcpy(misfile,filebase);
  strcat(misfile,mis);
  strcpy(resfile,filebase);
  strcat(resfile,res);
  strcpy(hdrfile,filebase);
  strcat(hdrfile,hdr);
  strcpy(btsfile,filebase);
  strcat(btsfile,bts);
  strcpy(trefile1,filebase);
  strcat(trefile1,tre1);
  strcpy(trefile2,filebase);
  strcat(trefile2,tre2);
  strcpy(basfile,filebase);
  strcat(basfile,bas);
  strcpy(cmpfile,filebase);
  strcat(cmpfile,cmp);
  strcpy(ordfile1,filebase);
  strcat(ordfile1,ord1);
  strcpy(ordfile2,filebase);
  strcat(ordfile2,ord2);
  
  if (dump_only) {
    if (verbose) fprintf(stdout,"Dump file: %s\n",dumpfile);
    if (verbose) fprintf(stdout,"Doing only dumpfile\n\n");
    if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
    if (verbose) fprintf(stdout,"Reding set");
    V = read_set(f,hdrfile);
    coin_tosh_silent(V);
    fclose(f);
    if (verbose) fprintf(stdout,"Writing dump file\n");
    if ((f = fopen(dumpfile,"w")) == NULL) file_error(dumpfile,(char *)func);
    write_set(f,V);
    fclose(f);
  }
  
  switch (module) {
  case MOD_CLASSIFY: {
    if (log_file) {
      if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
      start_text(o);
      fclose(o);
    }
    classify_vectors(datfile,outfile,parfile,ctrfile,misfile,hdrfile);
  }; break;
  case MOD_IDENT: {
    if (verbose) fprintf(stdout,"Input file (vectors): %s\n",datfile);
    if (verbose) fprintf(stdout,"Input file (classification): %s\n\n",parfile);
    if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
    identify_vectors (parfile,datfile,outfile,hdrfile);
  }; break;
  case MOD_GEN: {
    switch (data_generator) {
    case DG_RAND: {
      if (verbose) fprintf(stdout,"Generated file: %s\n",genfile);
      random_generator (genfile,vecs_to_gen,hdrfile);
    }; break;
    case DG_BERNOULI: {
      if (verbose) fprintf(stdout,"Input file: %s\n",parfile);
      if (verbose) fprintf(stdout,"Generated file: %s\n",genfile);
      bernouli_generator (parfile,genfile,vecs_to_gen,hdrfile);
    }; break;
    case DG_MARKOV: {
      if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
      if (verbose) fprintf(stdout,"Generated file: %s\n",genfile);
      markov_generator (datfile,genfile,vecs_to_gen,hdrfile);
    }; break;
    case DG_RVECTOR:  {
      if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
      if (verbose) fprintf(stdout,"Generated file: %s\n",genfile1);
      if (verbose) fprintf(stdout,"Vectors left out file: %s\n",genfile2);
      vector_generator (datfile,genfile1,genfile2,vecs_to_gen,hdrfile);
    }
    }
  }; break;
  case MOD_REPORT: {
    if (verbose) fprintf(stdout,"Partition file: %s\n",parfile);
    if (verbose) fprintf(stdout,"Report file: %s\n",repfile);
    if ((f = fopen(parfile,"r")) == NULL) file_error(parfile,(char *)func);
    if ((o = fopen(repfile,"w")) == NULL) file_error(repfile,(char *)func);
    generate_report(f,o,misfile,hdrfile);
    fclose(f);
    fclose(o);
  }; break;
  case MOD_COMPARE: {
    if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
    if (verbose) fprintf(stdout,"Partition file 1: %s\n",parfile1);
    if (verbose) fprintf(stdout,"Partition file 2: %s\n",parfile2);
    if (verbose) fprintf(stdout,"Results file: %s\n",resfile);
    compare_partitions(datfile,parfile1,parfile2,resfile,hdrfile);
  }; break;
  case MOD_SPLIT: {
    if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
    if (verbose) fprintf(stdout,"Partition file %s\n",parfile);
    if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
    fclassify_vectors(datfile,outfile,parfile,hdrfile);
  }; break;
  case MOD_JOIN: {
    if (join_target > 0) {
      if (verbose) fprintf(stdout,"Partition file 1: %s\n",parfile1);
      if (verbose) fprintf(stdout,"Partition file 2: %s\n",parfile2);
      make_joint(parfile1,parfile2,hdrfile);
    } else {
      if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
      if (verbose) fprintf(stdout,"Partition file %s\n",parfile);
      if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
      use_join_gla(datfile,outfile,parfile,hdrfile);
    }
  }; break;
  case MOD_BOOTSTRAP: {
    if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
    if (verbose) fprintf(stdout,"Partition file: %s\n",parfile);
    if (verbose) fprintf(stdout,"Results file: %s\n",btsfile);
    run_bootstrap(datfile,btsfile,hdrfile,bootstrap_k,parfile);
  }; break;
  case MOD_MIXTURE: {
    if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
    if (verbose) fprintf(stdout,"Results file: %s\n",resfile);
    if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
    if (sample_mixture > 0) {
      if (verbose) fprintf(stdout,"Partition file: %s\n",parfile1);
      if (verbose) fprintf(stdout,"Partition file: %s\n",parfile2);
      /* apply_mixture_classifier(datfile,outfile,parfile1,parfile2,resfile,hdrfile,mixture_classes); */
      perform_robustness_test(datfile,outfile,parfile1,parfile2,resfile,hdrfile,mixture_classes);
    } else {
      if (verbose) fprintf(stdout,"Partition file: %s\n",parfile);
      apply_mixture_classifier_once(datfile,outfile,parfile,hdrfile,mixture_classes);
    }
  }; break;
  case MOD_TREE: {
    if (verbose) fprintf(stdout,"Partition file: %s\n",parfile);
    if (verbose) fprintf(stdout,"Tree file: %s\n",trefile1);
    if (verbose) fprintf(stdout,"Tree source file: %s\n",trefile2);
    make_tree(parfile,trefile1,trefile2,hdrfile);
  }; break;
  case MOD_CUMULATIVE: {
    if (log_file) {
      if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
      start_text(o);
      fclose(o);
    }
    if (test_feature_significance) {
      if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
      if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
      reidentification_analysis(datfile,outfile,hdrfile);
    } else {
      if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
      if (verbose) fprintf(stdout,"Partition file %s\n",parfile);
      if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
      if (cumulative_analysis > 0) analyse_cumulative(datfile,basfile,cmpfile,outfile,parfile1,parfile2,ordfile1,ordfile2,hdrfile);
      else do_cumulative_classification(datfile,basfile,outfile,parfile,hdrfile);
    }
  }; break;
  case MOD_CENTROIDS: {
    if (verbose) fprintf(stdout,"Partition file: %s\n",parfile);
    if (verbose) fprintf(stdout,"Centroid file: %s\n",ctrfile);
    do_save_centroids(parfile,ctrfile,hdrfile);
  }; break;
  case MOD_SORTP: {
    sort_partition(hdrfile,parfile1,parfile2);
  }; break;
  case MOD_FUNCTION: {
    if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
    if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
    if (verbose) fprintf(stdout,"Centroid file: %s\n",ctrfile);
    render_functions(datfile,outfile,ctrfile,hdrfile);
  }; break;
  case MOD_INTERSECT: {
    if (analyse_int) {
      if (verbose) fprintf(stdout,"Source data file: %s\n",datfile);
      if (verbose) fprintf(stdout,"Result partition file: %s\n",parfile);
    } else {
      if (verbose) fprintf(stdout,"Source partition file 1: %s\n",parfile1);
      if (verbose) fprintf(stdout,"Source partition file 2: %s\n",parfile2);
      if (verbose) fprintf(stdout,"Result partition file: %s\n",parfile);
    }
    int_partitions(parfile,datfile,parfile1,parfile2,hdrfile);
  }; break;
#ifdef _TEST_ALG1
  case MOD_TEST1: {
    if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
    if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
    if (verbose) fprintf(stdout,"Partition file: %s\n",parfile);
    apply_alg1(datfile,outfile,parfile,hdrfile);
  }; break;
#endif
#ifdef _TEST_ALG2
  case MOD_TEST2: {
    if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
    if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
    if (verbose) fprintf(stdout,"Partition file: %s\n",parfile);
    apply_alg2(datfile,outfile,parfile,hdrfile);
  }; break;
#endif
  case MOD_NONE: {
    fprintf(stderr,"No action!\n");
  }
  }
  return 0;
}

/* End of binclass.c */

