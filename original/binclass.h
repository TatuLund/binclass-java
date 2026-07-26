/*
BinClass bases on program developed in Pascal by Martin Verlaan.
This is portable and more userfriendly C-version
  of the program by Tatu J. Lund.
Tested on:
  Windows NT 3.5x    - Borland C++ 4.51 & Cygnus GNU-CC v2.7.1
  Solaris 2.1 & 1.1  - GNU CC
  Linux              - GNU CC v2.6.8
  Amiga OS 3.1       - SAS/C++ 6.55 & GNU-CC v2.6.3
  SGI IRIX           - Native & GNU CC v2.7.0
  IBM AIX            - Native & GNU CC v2.7.0
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
	char *misfile;
	char *resfile;
	#ifdef __MSDOS__
	const char *dat = ".dat";
	const char *out = ".out";
	const char *rep = ".rep";
	const char *par = ".par";
	const char *par1 = ".pa1";
	const char *par2 = ".pa2";
	const char *ctr = ".ctr";
	const char *gen = ".gen";
	const char *mis = ".mis";
	const char *res = ".res";
	#else
	const char *dat = ".data";
	const char *out = ".output";
	const char *rep = ".report";
	const char *par = ".partition";
	const char *par1 = ".partition1";
	const char *par2 = ".partition2";
	const char *ctr = ".centroids";
	const char *gen = ".generated";
	const char *mis = ".missing";
	const char *res = ".result";
	#endif
	const char *func = "main";
	ST *V;

	start_text(stdout);
	if (!parse(argc, argv)) {
		help_text(stdout);
		return 1;
	}

	l = 2048;
	if ((datfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
	if ((outfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
	if ((parfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
	if ((parfile1 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
	if ((parfile2 = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
	if ((repfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
	if ((ctrfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
	if ((genfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
	if ((misfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();
	if ((resfile = (char *) malloc (l*sizeof(char))) == NULL) out_of_mem();

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
	strcpy(misfile,filebase);
	strcat(misfile,mis);
	strcpy(resfile,filebase);
	strcat(resfile,res);

	if (dump_only) {
		if (verbose) fprintf(stdout,"Dump file: %s\n",dumpfile);
		if (verbose) fprintf(stdout,"Doing only dumpfile\n\n");
		if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
		if (verbose) fprintf(stdout,"Reding set");
		V = read_set(f,NULL,NULL);
		coin_tosh_silent(V);
		fclose(f);
		if (verbose) fprintf(stdout,"Writing dump file\n",size(V));
		if ((f = fopen(dumpfile,"w")) == NULL) file_error(dumpfile,(char *)func);
		write_set(f,V);
		fclose(f);
	} else if (classification) {
		if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
		start_text(o);
		fclose(o);
		classify_vectors(datfile,outfile,parfile,repfile,ctrfile,misfile);
	} else if (addition) {
		if (verbose) fprintf(stdout,"Input file (vectors): %s\n",datfile);
		if (verbose) fprintf(stdout,"Input file (classification): %s\n\n",parfile);
		if (verbose) fprintf(stdout,"Output file: %s\n",outfile);
		identify_vectors (parfile,datfile,outfile);
	} else if (generation) {
		if (data_generator == DG_RAND) {
			if (verbose) fprintf(stdout,"Generated file: %s\n",genfile);
			random_generator (genfile,vecs_to_gen);
		} else if (data_generator == DG_BERNOULI) {
			if (verbose) fprintf(stdout,"Input file: %s\n",parfile);
			if (verbose) fprintf(stdout,"Generated file: %s\n",genfile);
			bernouli_generator (parfile,genfile,vecs_to_gen);
		} else if (data_generator == DG_MARKOV) {
			if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
			if (verbose) fprintf(stdout,"Generated file: %s\n",genfile);
			markov_generator (datfile,genfile,vecs_to_gen);
		} else if (data_generator == DG_RVECTOR)  {
			if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
			if (verbose) fprintf(stdout,"Generated file: %s\n",genfile);
			vector_generator (datfile,genfile,vecs_to_gen);
		}
	} else if (report_only) {
		if (verbose) fprintf(stdout,"Partition file: %s\n",parfile);
		if (verbose) fprintf(stdout,"Report file: %s\n",repfile);
		if ((f = fopen(parfile,"r")) == NULL) file_error(parfile,(char *)func);
		if ((o = fopen(repfile,"w")) == NULL) file_error(repfile,(char *)func);
		generate_report(f,o,vec_len,vec_offs,misfile);
		fclose(f);
		fclose(o);
	} else if (comparition) {
		if (verbose) fprintf(stdout,"Input file: %s\n",datfile);
		if (verbose) fprintf(stdout,"Partition file 1: %s\n",parfile1);
		if (verbose) fprintf(stdout,"Partition file 2: %s\n",parfile2);
		if (verbose) fprintf(stdout,"Results file: %s\n",resfile);
		compare_partitions(datfile,parfile1,parfile2,resfile);
	}

	return 0;
}

/* End of binclass.c */

