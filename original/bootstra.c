/*
Bootsrapping module
Kind of macrofunction which automatically runs set of
bootstraping with given parameters
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#include "vars.h"
#include "const.h"
#include "gendat.h"
#include "distmin.h"
#include "binset.h"
#include "centroid.h"
#include "vectors.h"
#include "distmin.h"
#include "adding.h"
#include "compare.h"
#include "format.h"
#include "bottom.h"
#include "glainf.h"

void bootstraper (ST *V, FILE *f, int number, int k, char *parfile);
void analyzer (ST *V, Partition *P, FILE *f, int number);
void run_bootstrap (char *datfile, char *btsfile, char *hdrfile, int k, char *parfile);
void bootstraper_statvalues (FILE *f, Vector *V, int number);

/* Mathematics: */

void mle_approx_2dim (Vector *Y, Vector *X, double *a, double *b);
/* calculate minimum least squared approximation from vectors Y and X */
/* and return the parameters of the line in a and b */
double correlation_coef (Vector *X, Vector *Y);
/* calculate correlation coefficient of the values in vectors Y and X */
double calculate_mse (double a, double b, Vector *X, Vector *Y);
/* calculate minimum squared error between line y = bx + a and vectors X and Y */

void mle_approx_2dim (Vector *Y, Vector *X, double *a, double *b) {
  double A,B;
  double D,sumx,sumy,sumxy,sumx2;
  int i,n;
  
  n = Y->l;
  
  /* calculate y = a + bx */
  
  sumy = 0.0;
  for (i=1;i<(n+1);i++) {
    sumy = sumy + Y->el[i];
  }
  sumx = 0.0;
  for (i=1;i<(n+1);i++) {
    sumx = sumx + X->el[i];
  }
  sumxy = 0.0;
  for (i=1;i<(n+1);i++) {
    sumxy = sumxy + (X->el[i] * Y->el[i]);
  }
  sumx2 = 0.0;
  for (i=1;i<(n+1);i++) {
    sumx2 = sumx2 + pow(X->el[i],2.0);
  }
  D = ((double) n * sumx2) - (sumx * sumx);
  
  A = (1.0 / D) * ((sumy * sumx2) - (sumxy * sumx));
  B = (1.0 / D) * ((n * sumxy) - (sumx * sumy));
  
  *a = A;
  *b = B;
}

double correlation_coef (Vector *X, Vector *Y) {
  double xm,ym,cov,sdx,sdy,cc,xd,yd;
  int n,i;
  double s;

  n = X->l;
  s = (double) (n-1);

  /* calculate artithmetical mean for x and y components */
  xm = 0.0;
  ym = 0.0;
  for (i=1;i<n;i++) {
    xm += X->el[i];
    ym += Y->el[i];
  }
  xm /= s;
  ym /= s;
  
  /* calculate co-variance of x and y components */
  cov = 0.0;
  for (i=1;i<n;i++) {
    cov += ((X->el[i] - xm) * (Y->el[i] - ym));
  }
  cov /= s;
  
  /* calculate empirical standard deviations od x and y components */
  sdx = 0.0;
  sdy = 0.0;
  for (i=1;i<n;i++) {
    xd = (X->el[i] - xm);
    sdx  += (xd * xd);
    yd = (Y->el[i] - ym);
    sdy  += (yd * yd);
  }
  sdx /= (s-1.0);
  sdy /= (s-1.0);
  sdx =  sqrt(sdx);
  sdy =  sqrt(sdy);
  
  /* calculate correlation coefficient from co-variance and stardard deviations */
  cc = cov / (sdx * sdy);
  return cc;
}

int generate_material (ST *V, int number, int k, InfCentroid **CC, Vector *SC, Vector *CL) {
  int i,l,n;
  int besttry = 1;
  Partition *P1 = NULL;
  InfCentroid *C = NULL;
  InfCentroid *Cnew;
  ST *W;
  double SCmin = 1000.0;
  double cl,sc;
  
  l = vec_len;
  
  if (verbose) fprintf(stdout,"Generating %d sets of centroids: ",number);
  n = size(V);

  /* generate n (=number) partitions, and save their centroids */
  for (i=1;i<(number+1);i++) {
    
    C = allocate_centroids(k,l);
    random_centroids(k,l,C,V);
    calculate_logs(C);
    /* allocate partition */
    P1 = allocate_partition(k);
    W = copy_set_fast(V);
    put_dot;

    /* form partition with random centroids */
    /* vectors are moved from W to P1 */
    gla(W,P1,C,&cl,n);
    CC[i] = C;
    
    sc = stochastic_complexity(P1,k,l);
    
    SC->el[i] = sc;
    CL->el[i] = cl;
    
    if (sc < SCmin) {
      SCmin = sc;
      besttry = i;
    }
    deallocate_partition(P1);
  }
  if (verbose) fprintf(stdout," ok\n");
  
  return besttry;
}


double calculate_mse (double a, double b, Vector *X, Vector *Y) {
  int n,i;
  double t,e;
  
  n = X->l;
  
  t = 0.0;
  for (i=1;i<(n+1);i++) {
    e = pow(((a + (b * Y->el[i])) - X->el[i]),2.0);
    t = t + e;
  }
  t = t / (double) n;
  return t;
}

void boots_sample (Vector *X, Vector *Y, Vector *Xr, Vector *Yr) {
  int n,i,rn;
  double r;
  
  n = X->l;

  for (i=1;i<(n+1);i++) {
    r = give_true_random();
    rn = (int)(r * (double) n);
    if (rn < 1) rn = 1;
    if (rn > n) rn = n;
    Xr->el[i] = X->el[rn];
    Yr->el[i] = Y->el[rn];
  }
  
}

void boots_resample_hist (FILE *f, Vector *CC, int iter) {
  int i,ind;
  int hist[10];

  for (i=0;i<10;i++) {
    hist[i] = 0;
  }
  for (i=1;i<(iter+1);i++) {
    ind = (int)(CC->el[i] * (double) 10);
    if (ind < 0) ind = 0;
    if (ind > 9) ind = 9; 
    hist[ind] = hist[ind]+1;
  }
  fprintf(f,"  Histogram:\n");
  for (i=0;i<10;i++) {
    fprintf(f,"                      %.1f %4d\n",(double)((double)i / (double) 10),hist[i]);
  }
}

void boots_resample (FILE *f, int iter, Vector *X, Vector *Y) {
  int n,i;
  Vector *Xr;
  Vector *Yr;
  double a,b,cc,mse;
  Vector *CC;
  
  n = X->l;
  
  Xr = allocate_dvector(n);
  Yr = allocate_dvector(n);
  CC = allocate_dvector(iter);
  
  if (verbose) fprintf(stdout,"\nResampling ");
  fprintf(f,"\nResampling\n");
  
  for (i=1;i<(iter+1);i++) {
    boots_sample(X,Y,Xr,Yr);
    mle_approx_2dim(Xr,Yr,&a,&b);
    cc = correlation_coef(Xr,Yr);
    mse = calculate_mse(a,b,X,Y);
    CC->el[i] = cc;
    fprintf(f," %4d: a = % .4f, b = % .4f, e = % .4f cc = % .4f\n",i,a,b,mse,cc);
    fflush(f);
    if ((verbose) && ((i % 10) == 0)) {
      fputc('.',stdout);
      fflush(stdout);
    }
  }
  
  if (verbose) fprintf(stdout," ok\n");
  
  deallocate_dvector(Xr);
  deallocate_dvector(Yr);
  
  fprintf(f,"\nCorrelation coefficient\n");
  bootstraper_statvalues(f,CC,iter);

  boots_resample_hist(f,CC,iter);

  deallocate_dvector(CC);

}


void bootstraper_start (FILE *f, int k, int s, int i, int number) {
  fprintf(f,"\nBootstrap\n");
  fprintf(f,"  Partitions:        %d\n",k);
  fprintf(f,"  Vectors in set:    %d\n",s);
  fprintf(f,"  Resamplings   :    %d\n",i);
  fprintf(f,"  Size of sample:    %d\n\n",number);
}

void bootstraper_statvalues (FILE *f, Vector *V, int number) {
  double avg,me,sd,var,min,max;
  int i;

  /* compute average */
  min = V->el[1];
  max = V->el[1];
  avg = 0.0;
  for (i=1;i<(number+1);i++) {
    avg = avg + V->el[i];
    if (V->el[i] < min) min = V->el[i];
    if (V->el[i] > max) max = V->el[i];
  }
  avg = (avg / (double) number);
  /* compute standard deviation */
  sd = 0.0;
  for (i=1;i<(number+1);i++) sd = sd + pow((V->el[i] - avg),2.0);
  sd = sqrt(sd / (double) number);
  /* compute mean error */
  me = sd / sqrt((double) number);
  /* compute variance */
  var = pow(sd,2.0);
  fprintf(f,"  Average:            %.4f\n",avg);
  fprintf(f,"  Standard deviation: %.4f\n",sd);
  fprintf(f,"  Mean error:         %.4f\n",me);
  fprintf(f,"  Variance:           %.4f\n",var);
  fprintf(f,"  Minimum:            %.4f\n",min);
  fprintf(f,"  Maximum:            %.4f\n",max);
}

void bootstraper (ST *V, FILE *f, int number, int k, char *parfile) {
  const char *func = "bootstrap";
  Vector *SC;
  Vector *dist;
  Vector *CL;
  InfCentroid *C;
  InfCentroid **CC;
  Partition *P;
  Partition *P1;
  ST *W;
  FILE *p = NULL;
  int i,tot,besttry;
  double d,sc;
  double cl,a,b,mse,ccoef;

  if (verbose) bootstraper_start(stdout,(k-1),size(V),bootstrap_i,number);
  
  start_text(f);
  bootstraper_start(f,(k-1),size(V),bootstrap_i,number);

  tot = size(V);
  if (vecs_to_gen > tot) tot = vecs_to_gen;
  if (log2_factorials == NULL) log2_factorials = prepare_log2_factorials(tot+k);
  
  if ((CC = malloc((number+1)*sizeof(InfCentroid *))) == NULL) out_of_mem();
  
  dist = allocate_dvector(number);
  SC = allocate_dvector(number);
  CL = allocate_dvector(number);
  
  /* generate n (=number) partitions, and save their centroids in CC*/
  besttry = generate_material(V,number,k,CC,SC,CL);
  
  /* restore best partition to P */
  C = CC[besttry];
  W = copy_set(V);
  P = allocate_partition(k);
  inf_nearest_neighbour(W,P,C,use_class_weights);
  if (save_best_boots) {
    if ((p = fopen(parfile,"w")) == NULL) file_error(parfile,(char *)func);
    inf_write_partition(p,P);
    fclose(p);
  }
  
  /* calculate distances of P[i] to P[besttry] */
  for (i=1;i<(number+1);i++) {
    
    if (verbose) fprintf(stdout,"Round %4d: ",i);
    C = CC[i];
    P1 = allocate_partition(k);
    W = copy_set(V);
    inf_nearest_neighbour(W,P1,C,use_class_weights);
    
    sc = SC->el[i];
    cl = CL->el[i];
    d = calculate_distance(V,P,P1);
    dist->el[i] = d;
    fprintf(f,"Round: %3d   Distance = ",i);
    if (d < 999.95) fputc(' ',f);
    if (d < 99.95) fputc(' ',f);
    if (d < 9.95) fputc(' ',f);
    fprintf(f,"%.1f, Codelength = %.4f, SC = %.4f\n",d,cl,sc);
    fflush(f);
    /* deallocate partitions and all vectors in them */
    deallocate_partition(P1);
    deallocate_centroids(C);
  }

  free(CC);

  fprintf(f,"\nStochastic complexity\n");
  bootstraper_statvalues(f,SC,number);
  fprintf(f,"\nCodelength\n");
  bootstraper_statvalues(f,CL,number);
  fprintf(f,"\nDistance from minimum\n");
  bootstraper_statvalues(f,dist,number);

  mle_approx_2dim(SC,dist,&a,&b);
  
  fprintf(f,"\nMLE line (y = a + bx)\n");
  fprintf(f,"  a                   %.4f\n",a);
  fprintf(f,"  b                   %.4f\n",b);
  
  mse = calculate_mse(a,b,SC,dist);
  
  fprintf(f,"  error               %.4f\n",mse);
  ccoef = correlation_coef(SC,dist);
  fprintf(f,"  correlation         %.4f\n",ccoef);
  
  boots_resample(f,bootstrap_i,SC,dist);
  
  deallocate_dvector(CL);
  deallocate_dvector(dist);
  deallocate_dvector(SC);
}

void analyzer (ST *V, Partition *P, FILE *f, int number) {
  Vector *SC;
  Vector *dist;
  Vector *CL;
  InfCentroid *C;
  Partition *P1;
  Partition *P2 = NULL;
  ST *W;
  int k,i,l,j,tot,s;
  double d,sc;
  double SCavg,SCme,SCsd,SCvar,SCme2,SCsd2,SCvar2;
  double Davg,Dme,Dsd,Dvar;
  double CLavg,CLme,CLsd,CLvar,CLme2,CLsd2,CLvar2;
  double SCmax = 0;
  double SCmin = 1000.0;
  double Dmax = 0;
  double Dmin = 1000.0;
  double CLmax = 0;
  double CLmin = 1000.0;
  double CLopt;
  double SCopt;
  double cl;

  k = P->k;
  l = vec_len;
  
  if (verbose) fprintf(stdout,"\nAnalyzer\n");
  if (verbose) {
    if (source_error) fprintf(stdout,"  Source error\n");
    else fprintf(stdout,"  Method error\n");
  }
  tot = size(V);
  if (verbose) fprintf(stdout,"  Partitions:        %d\n",(k-1));
  if (verbose) fprintf(stdout,"  Vectors in set:    %d\n",tot);
  if (verbose) fprintf(stdout,"  Vectors generated: %d\n",vecs_to_gen);
  if (verbose) fprintf(stdout,"  Number of rounds:  %d\n\n",number);
  
  start_text(f);
  
  if (verbose) fprintf(f,"\nAnalyzer\n");
  if (verbose) {
    if (source_error) fprintf(f,"  Source error\n");
    else fprintf(f,"  Method error\n");
  }
  if (verbose) fprintf(f,"  Partitions:        %d\n",(k-1));
  if (verbose) fprintf(f,"  Vectors in set:    %d\n",tot);
  if (verbose) fprintf(f,"  Vectors generated: %d\n",vecs_to_gen);
  if (verbose) fprintf(f,"  Number of rounds:  %d\n\n",number);
  
  C = allocate_centroids(k,l);
  for (j=1;j<l;j++) C->el[0]->el[j] = 0.5;
  s = 0;
  for (i=1;i<k;i++) s += size(P->el[i]);
  for (j=1;j<k;j++) {
    inf_average(P->el[j],C->el[j],rounded_centroids,s);
  }
  calculate_logs(C);
  CLopt = average_codelength(P,C,FALSE);
  deallocate_centroids(C);

  if (vecs_to_gen > tot) tot = vecs_to_gen;
  if (log2_factorials == NULL) log2_factorials = prepare_log2_factorials(tot+k);
  SCopt = stochastic_complexity(P,k,l);
  
  dist = allocate_dvector(number);
  SC = allocate_dvector(number);
  CL = allocate_dvector(number);

  for (i=1;i<(number+1);i++) {
    C = allocate_centroids(k,l);
    if (source_error) {
      for (j=1;j<l;j++) C->el[0]->el[j] = 0.5;
      for (j=1;j<k;j++) {
	inf_average(P->el[j],C->el[j],rounded_centroids,s);
      }
    } else {
      random_centroids(k,l,C,V);
    }
    calculate_logs(C);
    /* allocate partition */
    P1 = allocate_partition(k);
    if (source_error) {
      if (verbose) fprintf(stdout,"Round: %d\n Generating data ..",i);
      /* generate data */
      W = vector_gen(V,vecs_to_gen);
    } else {
      W = copy_set(V);
      if (verbose) fprintf(stdout,"Round: %d\n Forming partition ..",i);
    }
    if (source_error) {
      if (verbose) fprintf(stdout,".. forming partition ..");
    }
    /* form partition with optimal centroids on new data */
    /* vectors are moved from W to P1 */
    if (source_error) {
      gla(W,P1,C,&cl,tot);
      if (verbose) fprintf(stdout,".. identifying ..");
      /* Identify given set with new partition */
      /* Vectors are copied from V to P2 */
      P2 = identifier_by_class(V,P1);
    } else {
      gla(W,P1,C,&cl,tot);
    }
    if (verbose) fprintf(stdout,".. ok\n ");
    
    if (source_error) {
      d = calculate_distance(V,P,P2);
      sc = stochastic_complexity(P2,k,l);
    } else {
      d = calculate_distance(V,P,P1);
      sc = stochastic_complexity(P1,k,l);
    }
    dist->el[i] = d;
    SC->el[i] = sc;
    CL->el[i] = cl;
    if (cl < CLmin) CLmin = cl;
    if (cl > CLmax) CLmax = cl;
    if (d < Dmin) Dmin = d;
    if (d > Dmax) Dmax = d;
    if (sc < SCmin) SCmin = sc;
    if (sc > SCmax) SCmax = sc;
    fprintf(f,"Round: %3d   Distance = ",i);
    if (d < 100.0) fputc(' ',f);
    if (d < 10.0) fputc(' ',f);
    fprintf(f,"%.1f, Codelength = %.4f, SC = %.4f\n",d,cl,sc);
    fflush(f);
    /* deallocate partitions and all vectors in them */
    if (source_error) deallocate_partition(P2);
    deallocate_partition(P1);
    deallocate_centroids(C);
  }
  
  /* compute average */
  SCavg = 0.0;
  for (i=1;i<(number+1);i++) SCavg = SCavg + SC->el[i];
  SCavg = (SCavg / (double) number);
  /* compute standard deviation */
  SCsd = 0.0;
  for (i=1;i<(number+1);i++) SCsd = SCsd + pow((SC->el[i] - SCavg),2.0);
  SCsd = sqrt(SCsd / (double) number);
  SCsd2 = 0.0;
  for (i=1;i<(number+1);i++) SCsd2 = SCsd2 + pow((SC->el[i] - SCopt),2.0);
  SCsd2 = sqrt(SCsd2 / (double) number);
  /* compute mean error */
  SCme = SCsd / sqrt((double) number);
  SCme2 = SCsd2 / sqrt((double) number);
  /* compute variance */
  SCvar = pow(SCsd,2.0);
  SCvar2 = pow(SCsd2,2.0);
  fprintf(f,"\nStochastic complexity\n");
  fprintf(f,"  Average:            %.4f\n",SCavg);
  fprintf(f,"  Standard deviation: %.4f\n",SCsd2);
  fprintf(f,"                      %.4f\n",SCsd);
  fprintf(f,"  Mean error:         %.4f\n",SCme2);
  fprintf(f,"                      %.4f\n",SCme);
  fprintf(f,"  Variance:           %.4f\n",SCvar2);
  fprintf(f,"                      %.4f\n",SCvar);
  fprintf(f,"  Optimum:            %.4f\n",SCopt);
  fprintf(f,"  Minimum:            %.4f\n",SCmin);
  fprintf(f,"  Maximum:            %.4f\n",SCmax);
  
  /* compute average */
  CLavg = 0.0;
  for (i=1;i<(number+1);i++) CLavg = CLavg + CL->el[i];
  CLavg = (CLavg / (double) number);
  /* compute standard deviation */
  CLsd = 0.0;
  for (i=1;i<(number+1);i++) CLsd = CLsd + pow((CL->el[i] - CLavg),2.0);
  CLsd = sqrt(CLsd / (double) number);
  CLsd2 = 0.0;
  for (i=1;i<(number+1);i++) CLsd2 = CLsd2 + pow((CL->el[i] - CLopt),2.0);
  CLsd2 = sqrt(CLsd2 / (double) number);
  /* compute mean error */
  CLme = CLsd / sqrt((double) number);
  CLme2 = CLsd2 / sqrt((double) number);
  /* compute variance */
  CLvar = pow(CLsd,2.0);
  CLvar2 = pow(CLsd2,2.0);
  fprintf(f,"\nCodelength\n");
  fprintf(f,"  Average:            %.4f\n",CLavg);
  fprintf(f,"  Standard deviation: %.4f\n",CLsd2);
  fprintf(f,"                      %.4f\n",CLsd);
  fprintf(f,"  Mean error:         %.4f\n",CLme2);
  fprintf(f,"                      %.4f\n",CLme);
  fprintf(f,"  Variance:           %.4f\n",CLvar2);
  fprintf(f,"                      %.4f\n",CLvar);
  fprintf(f,"  Optimum:            %.4f\n",CLopt);
  fprintf(f,"  Minimum:            %.4f\n",CLmin);
  fprintf(f,"  Maximum:            %.4f\n",CLmax);
  
  /* compute average */
  Davg = 0.0;
  for (i=1;i<(number+1);i++) Davg = Davg + dist->el[i];
  Davg = (Davg / (double) number);
  /* compute standard deviation */
  Dsd = 0.0;
  for (i=1;i<(number+1);i++) Dsd = Dsd + pow((dist->el[i] - Davg),2.0);
  Dsd = sqrt(Dsd / (double) number);
  /* compute mean error */
  Dme = Dsd / sqrt((double) number);
  /* compute variance */
  Dvar = pow(Dsd,2.0);
  fprintf(f,"\nDistance from optimum\n");
  fprintf(f,"  Average:            %.3f\n",Davg);
  fprintf(f,"  Standard deviation: %.3f\n",Dsd);
  fprintf(f,"  Mean error:         %.3f\n",Dme);
  fprintf(f,"  Variance:           %.3f\n",Dvar);
  fprintf(f,"  Minimum:            %.1f\n",Dmin);
  fprintf(f,"  Maximum:            %.1f\n",Dmax);
  
  deallocate_dvector(CL);
  deallocate_dvector(dist);
  deallocate_dvector(SC);
}

void run_analyze (char *datfile, char *parfile, char *btsfile, char *hdrfile) {
  const char *func = "run_bootstrap";
  ST *V;
  Partition *P;
  FILE *f;
  
  read_header(hdrfile);
  
  if ((f = fopen(parfile,"r")) == NULL) file_error(parfile,(char *)func);
  P = read_partition(f,FALSE);
  fclose(f);
  
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  V = read_set(f,hdrfile);
  fclose(f);
  
  if ((f = fopen(btsfile,"w")) == NULL) file_error(btsfile,(char *)func);
  analyzer(V,P,f,bootstrap_size);
  fclose(f);
  
}

void run_bootstrap (char *datfile, char *btsfile, char *hdrfile, int k, char *parfile) {
  const char *func = "run_bootstrap";
  ST *V;
  FILE *f;
  time_t tm;
  
  tm = time(&tm);
  set_rand((unsigned) tm);
  
  read_header(hdrfile);
  
  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  V = read_set(f,hdrfile);
  fclose(f);
  
  if ((f = fopen(btsfile,"w")) == NULL) file_error(btsfile,(char *)func);
  bootstraper(V,f,bootstrap_size,k,parfile);
  fclose(f);
  
}

/* end of bootstra.c */
