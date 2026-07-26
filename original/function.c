/*
Recalculate SC(uniform), SC(Jeffrey's), Shannon-entropy and Codelength
values as function of k (=number of classes) from saved centroids
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "const.h"
#include "bottom.h"
#include "centroid.h"
#include "vars.h"
#include "vectors.h"
#include "binset.h"
#include "distmin.h"

void render_functions (char *datfile, char *outfile, char *ctrfile, char *hdrfile);

double a1 (Vector *y, int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += y->el[i] / ((double)i);
  return s;
}

double a2 (int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += 1.0 / ((double)(i*i));
  return s;
}

double b1 (Vector *y, int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += y->el[i] * ((double)i);
  return s;
}

double b2 (int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += (double)(i*i);
  return s;
}

double c1 (int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += 1.0 / ((double)i);
  return s;
}

double c2 (int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += ((double)i);
  return s;
}

double c3 (Vector *y, int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += y->el[i];
  return s;
}

double d1 (int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += pow(log_2((double)i),2.0);
  return s;
}

double d2 (int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += log_2((double)i) * ((double)n);
  return s;
}

double d3 (int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += log_2((double)i);
  return s;
}

double d4 (Vector *Y, int n) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += Y->el[i] * log_2((double)i);
  return s;
}

double fit_error (Vector *Y, int n, double a, double b, double c) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += pow(Y->el[i] - (a/((double)i)) - (b * ((double)i)) - c,2.0);
  return s;
}

double fit_error_logl (Vector *Y, int n, double a, double b, double c) {
  int i;
  double s;

  s = 0.0;
  for (i=1;i<n;i++) s += pow(Y->el[i] - (a*log_2((double)i)) - (b * ((double)i)) - c,2.0);
  return s;
}

void print_matrix3 (Matrix *M) {
  int i;

  for (i=1;i<5;i++) fprintf(stdout,"%.4f ",M->el[1]->el[i]);
  fprintf(stdout,"\n");
  for (i=1;i<5;i++) fprintf(stdout,"%.4f ",M->el[2]->el[i]);
  fprintf(stdout,"\n");
  for (i=1;i<5;i++) fprintf(stdout,"%.4f ",M->el[3]->el[i]);
  fprintf(stdout,"\n");
  fprintf(stdout,"\n");
}

void gauss_eliminate3 (Matrix *M) {
  int i;
  double C;

  C = -M->el[2]->el[1] / M->el[1]->el[1];
  for (i=1;i<5;i++) M->el[2]->el[i] += C * M->el[1]->el[i];
  C = -M->el[3]->el[1] / M->el[1]->el[1];
  for (i=1;i<5;i++) M->el[3]->el[i] += C * M->el[1]->el[i];
  C = -M->el[3]->el[2] / M->el[2]->el[2];
  for (i=1;i<5;i++) M->el[3]->el[i] += C * M->el[2]->el[i];
  C = -M->el[2]->el[3] / M->el[3]->el[3];
  for (i=1;i<5;i++) M->el[2]->el[i] += C * M->el[3]->el[i];
  C = -M->el[1]->el[3] / M->el[3]->el[3];
  for (i=1;i<5;i++) M->el[1]->el[i] += C * M->el[3]->el[i];
  C = -M->el[1]->el[2] / M->el[2]->el[2];
  for (i=1;i<5;i++) M->el[1]->el[i] += C * M->el[2]->el[i];
  M->el[1]->el[4] /= M->el[1]->el[1];
  M->el[2]->el[4] /= M->el[2]->el[2];
  M->el[3]->el[4] /= M->el[3]->el[3];
}

void fit_function (FILE *o, Vector *SC, int n) {
  /* fit a/x + bx + c to data with least squares method */
  double A1,A2,B1,B2,C1,C2,C3,N,a,b,c;
  Matrix *M;

  /* calculate co-efficients */
  A1 = a1(SC,n);
  A2 = a2(n);
  B1 = b1(SC,n);
  B2 = b2(n);
  C1 = c1(n);
  C2 = c2(n);
  C3 = c3(SC,n);
  N = (double)n;

  M = allocate_dmatrix(3,4);

  /* we have three equations */
  /* aA2 + bN + cC1 = A1 */
  M->el[1]->el[1] = A2;
  M->el[1]->el[2] = N;
  M->el[1]->el[3] = C1;
  M->el[1]->el[4] = A1;
  /* aN + bB2 + cC2 = B1 */
  M->el[2]->el[1] = N;
  M->el[2]->el[2] = B2;
  M->el[2]->el[3] = C2;
  M->el[2]->el[4] = B1;
  /* aC1 + bC2 + cN = C3 */
  M->el[3]->el[1] = C1;
  M->el[3]->el[2] = C2;
  M->el[3]->el[3] = N;
  M->el[3]->el[4] = C3;

  gauss_eliminate3(M);
  a = M->el[1]->el[4];
  b = M->el[2]->el[4];
  c = M->el[3]->el[4];

  fprintf(o,"F(x)  = %.4f/x + %.4fx + %.4f\n",a,b,c);
  fprintf(o,"Error = %.4f\n",fit_error(SC,n,a,b,c));
}


void fit_function_logl (FILE *o, Vector *SC, int n) {
  /* fit alog(x) + bx + c to data with least squares method */
  double B1,B2,C2,C3,D1,D2,D3,D4,N,a,b,c;
  Matrix *M;

  /* calculate co-efficients */
  B1 = b1(SC,n);
  B2 = b2(n);
  C2 = c2(n);
  C3 = c3(SC,n);
  D1 = d1(n);
  D2 = d2(n);
  D3 = d3(n);
  D4 = d4(SC,n);
  N = (double)n;

  M = allocate_dmatrix(3,4);

  /* we have three equations */
  /* aD1 + bD2 + cD3 = D4 */
  M->el[1]->el[1] = D1;
  M->el[1]->el[2] = D2;
  M->el[1]->el[3] = D3;
  M->el[1]->el[4] = D4;
  /* aD2 + bB2 + cC2 = B1 */
  M->el[2]->el[1] = D2;
  M->el[2]->el[2] = B2;
  M->el[2]->el[3] = C2;
  M->el[2]->el[4] = B1;
  /* aD3 + bC2 + cN = C3 */
  M->el[3]->el[1] = -D3;
  M->el[3]->el[2] = C2;
  M->el[3]->el[3] = N;
  M->el[3]->el[4] = C3;

  gauss_eliminate3(M);
  a = M->el[1]->el[4];
  b = M->el[2]->el[4];
  c = M->el[3]->el[4];

  fprintf(o,"F(x)  = %.4flog(x) + %.4fx + %.4f\n",a,b,c);
  fprintf(o,"Error = %.4f\n",fit_error_logl(SC,n,a,b,c));
}



void calculate_functions (FILE *f, FILE *o, ST *V) {
  InfCentroid *C;
  Partition *P;
  int k,l,t,i;
  double scu,scj,cl2,cl;
  Vector *SCu;
  Vector *SCj;
  Vector *CL2;
  Vector *CL;
  char *s;

  t = size(V);

  log2_factorials = prepare_log2_factorials(t+t);

  SCu = allocate_dvector(t);
  SCj = allocate_dvector(t);
  CL2 = allocate_dvector(t);
  CL = allocate_dvector(t);
  for (i=1;i<t;i++) {
    SCu->el[i] = unassigned_sc();
    SCj->el[i] = unassigned_sc();
    CL2->el[i] = unassigned_sc();
    CL->el[i] = unassigned_sc();
  }

  if ((s = (char *) malloc (20*sizeof(char))) == NULL) out_of_mem();
  read_line(f,s,20);
  read_line(f,s,20);
  read_line(f,s,20);

  if (verbose) fprintf(stdout,"\nCalculating: ");
  fprintf(o,"\nCALCULATING:\n");

  while (!feof(f)) {
    C = load_centroids(f);
    if (C != NULL) {
      k = C->k;
      l = C->el[1]->l;
      P = allocate_partition(k);
      
      if (distance_type == DT_L1_CL) inf_nearest_neighbour(V,P,C,use_class_weights);
      else if (distance_type == DT_L2_CL) inf_nearest_neighbour(V,P,C,use_class_weights);
      else if (distance_type == DT_SA) inf_nearest_neighbour(V,P,C,use_class_weights);
      else if (distance_type == DT_SR) inf_nearest_neighbour(V,P,C,use_class_weights);
      else if (distance_type == DT_CL) inf_nearest_neighbour(V,P,C,use_class_weights);
      else if (distance_type == DT_L1) MAE_nearest_neighbour(V,P,C);
      else if (distance_type == DT_L2) MSE_nearest_neighbour(V,P,C);
      else fast_nearest_neighbour(V,P,C);

      fprintf(o,"%4d: ",k-1);
      fflush(o);
      scu = stochastic_complexity_u(P,k,l);
      fprintf(o,"%1.5f ",scu);
      fflush(o);
      scj = stochastic_complexity_j(P,k,l);
      fprintf(o,"%1.5f ",scj);
      fflush(o);
      cl2 = shannon_entropy(P,C,FALSE);
      fprintf(o,"%1.5f ",cl2);
      fflush(o);
      cl = average_codelength(P,C,FALSE);
      fprintf(o,"%1.5f\n",cl);
      fflush(o);
      
      SCu->el[k-1] = (scu < SCu->el[k-1]) ? scu : SCu->el[k-1];
      SCj->el[k-1] = (scj < SCj->el[k-1]) ? scj : SCj->el[k-1];
      CL2->el[k-1] = (cl2 < CL2->el[k-1]) ? cl2 : CL2->el[k-1];
      CL->el[k-1] = (cl < CL->el[k-1]) ? cl : CL->el[k-1];
      
      V = partition_to_set(P);
      deallocate_partition(P);
      
      deallocate_centroids(C);
      put_dot;
      read_line(f,s,20);
    }
  }

  if (verbose) fprintf(stdout," ok!\n");

  fprintf(o,"\nFUNCTION:\n\n");
  i=1;
  while(SCu->el[i] < unassigned_sc()) {
    fprintf(o,"%4d: %1.5f %1.5f %1.5f %1.5f\n",i,SCu->el[i],SCj->el[i],CL2->el[i],CL->el[i]);
    i++;
  }
  fprintf(o,"\nMLE ESTIMATES:\n\n");
  fit_function(o,SCu,i);
  fit_function_logl(o,SCu,i);

  deallocate_dvector(SCu);
  deallocate_dvector(SCj);
  deallocate_dvector(CL2);
  deallocate_dvector(CL);

}

void render_functions (char *datfile, char *outfile, char *ctrfile, char *hdrfile) {
  const char *func = "render_functions";
  FILE *f;
  FILE *o;
  ST *V;

  if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
  start_text(o);
  fflush(o);

  /* Read input data */
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  V = read_set(f,hdrfile);
  fclose(f);

  if ((f = fopen(ctrfile,"r")) == NULL) file_error(outfile,(char *)func);

  calculate_functions(f,o,V);

}

/* end of function.c */
