/*
Identification vectors to partiotion with closest match
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
#include "bottom.h"
#include "centroid.h"
#include "report.h"
#include "vectors.h"
#include "format.h"

/* prototypes */

void identify_vectors_by_classification (FILE *o, ST *V, Partition *P, InfCentroid *C);
Partition *identifier_by_class (ST *V, Partition *P);
void identify_vectors (char *datfile, char *vecfile, char *outfile, char *hdrfile);

/* implementation */


void identify_vectors_by_classification (FILE *o, ST *V, Partition *P, InfCentroid *C) {
  /*Make a partition*/
  const char *es1 = "No centroids";
  const char *es2 = "Empty set";
  const char *func = "identify_vectors_by_classification";
  int k,i,j,closest,si;
  int mi = 1;
  BV *x;
  double mindist,dist;
  int minhdist;
  Partition *Pa = NULL;
  Partition *Pnew = NULL;
  FILE *f;
  Vector *clX;
  IntVector *hX;
  
  if (C == NULL) internal_error((char *)func);
  if (P == NULL) internal_error((char *)func);
  
  k = C->k;
  if (k == 0) stop_error((char *)es1,(char *)func);
  if (V == NULL) stop_error((char *)es2,(char *)func);
  
  /* Allocating space for partition */
  if (exact_matches) {
    Pa = allocate_partition(k);
  }
  
  /* Allocating space for partition */
  if (store_partition) {
    Pnew = allocate_partition(k);
  }
  
  fprintf(o,"\nLIST OF MATCHES:\n---------------\n");
  
  clX = allocate_dvector(k);
  hX = allocate_ivector(k);
  
  si = (trashcan) ? 0 : 1;
  while (!no_elements(V)) {
    for (i=0;i<k;i++) {
      clX->el[i] = 0.0;
      hX->el[i] = 0;
    }
    x = get_element(V);
    closest = si;
    mindist = (use_class_weights) ? code_length2(x,C->el[si]) : code_length(x,C->el[si]);
    minhdist = hamming_distance(x,C->el[si]);
    clX->el[1] = mindist;
    for (i=(si+1);i<k;i++) {
      dist = (use_class_weights) ? code_length2(x,C->el[i]) : code_length(x,C->el[i]);
      clX->el[i] = dist;
      if (dist < mindist) {
	closest = i;
	mindist = dist;
	minhdist = hamming_distance(x,C->el[i]);
      }
    }
    x->dist = mindist;
    x->hdist = minhdist;
    hX->el[closest] = 1;
    fprintf(o,"%4d: %s %s  %2d ",closest,x->clasname,x->strain,minhdist);
    if (mindist < 9.995) fputc(' ',o);
    fprintf(o,"%1.2f [",mindist);
    for (j=0;j<4;j++) {
      mindist = 10000.0;
      for (i=si;i<k;i++) {
	dist = clX->el[i];
	if ((dist < mindist) && (hX->el[i] == 0)) {
	  mi = i;
	  mindist = dist;
	}
      }
      hX->el[mi] = 1;
      if (mindist < (double)(vec_len-1)) {
	fprintf(o,"%3d ",mi);
	if (mindist < 9.995) fputc(' ',o);
	fprintf(o,"%1.2f ",mindist);
      }
    }
    fprintf(o,"]\n");
    if (store_partition) {
      Pnew->el[closest] = add_element(Pnew->el[closest],x);
    }
    if (exact_matches) {
      if (is_in_set(x,P->el[closest])) {
	Pa->el[closest] = add_element_in_alpha(Pa->el[closest],x);
      }
    }
    V = del_element(V);
  }
  deallocate_dvector(clX);
  deallocate_ivector(hX);
  if (exact_matches) {
    fprintf(o,"--\n\n List of exact matches\n--\n");
    inf_write_partition(o,Pa);
    fprintf(o,"--\n");
  }
  if (store_partition) {
    if ((f = fopen(new_parfile,"w")) == NULL) file_error(new_parfile,(char *)func);
    inf_write_partition(f,Pnew);
    fclose(f);
  }
}

Partition *identifier_by_class (ST *V, Partition *P) {
  /*Make a partition*/
  const char *es1 = "No centroids";
  const char *es2 = "Empty set";
  const char *func = "identifier_by_class";
  int k,i,l,closest,si,s;
  BV *x;
  ST *tmp;
  double mindist,dist;
  int minhdist;
  Partition *Pnew;
  InfCentroid *C;
  
  if (P == NULL) internal_error((char *)func);
  if (V == NULL) stop_error((char *)es2,(char *)func);
  k = P->k;
  l = vec_len;
  
  C = allocate_centroids(k,l);
  for (i=1;i<l;i++) C->el[0]->el[i] = 0.5;
  s = 0;
  for (i=1;i<k;i++) s += size(P->el[i]);
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,s);
  }
  calculate_logs(C);
  
  if (k == 0) stop_error((char *)es1,(char *)func);
  
  /* Allocating space for parition */
  Pnew = allocate_partition(k);
  
  si = (trashcan) ? 0 : 1;
  tmp = V;
  while (elements_left(tmp)) {
    x = get_element(tmp);
    closest = si;
    mindist = code_length(x,C->el[si]);
    minhdist = hamming_distance(x,C->el[si]);
    for (i=(si+1);i<k;i++) {
      dist = code_length(x,C->el[i]);
      if (dist < mindist) {
	closest = i;
	mindist = dist;
	minhdist = hamming_distance(x,C->el[i]);
      }
    }
    x->dist = mindist;
    x->hdist = minhdist;
    Pnew->el[closest] = add_element(Pnew->el[closest],bv_copy(x));
    tmp = next_element(tmp);
  }
  inf_remove_empty(Pnew,C,s);
  deallocate_centroids(C);
  return Pnew;
}

void identify_vectors (char *datfile, char *vecfile, char *outfile, char *hdrfile) {
  FILE *f;
  Partition *P;
  InfCentroid *C;
  ST *V;
  int k,i;
  int l,s;
  const char *func = "identify_vectors";
  
  read_header(hdrfile);
  l = vec_len;
  
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  P = read_partition(f,FALSE);
  k = P->k;
  fclose(f);
  
  if (verbose) fprintf(stdout,"Reading vectors ..");
  if ((f = fopen(vecfile,"r")) == NULL) file_error(vecfile,(char *)func);
  V = read_set(f,hdrfile);
  coin_tosh_silent(V);
  fclose(f);
  if (verbose) fprintf(stdout,".. about to identify %d vectors\n\n",size(V));
  
  C = allocate_centroids(k,l);
  for (i=1;i<l;i++) C->el[0]->el[i] = 0.5;
  s = 0;
  for (i=1;i<k;i++) s += size(P->el[i]);
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,s);
  }
  calculate_logs(C);
  
  if ((f = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
  start_text(f);
  identify_vectors_by_classification(f,V,P,C);
  fclose(f);
}

/* end of adding.c */

