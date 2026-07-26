/*
This is module for doing mixture classification with EM-algorithm
(EM stands for expectation maximization).
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#include "const.h"
#include "vars.h"
#include "bottom.h"
#include "binstuff.h"
#include "binset.h"
#include "centroid.h"
#include "vectors.h"
#include "distmin.h"
#include "bootstra.h"
#include "compare.h"

#define _L_EPSILON 0.1
#define _STD_PRIOR

void apply_mixture_classifier(char *datfile, char *outfile, char *parfile1, char* parfile2, char *resfile, char *hdrfile, int m);
void apply_mixture_classifier_once(char *datfile, char *outfile, char *parfile, char *hdrfile, int m);


double m_fx (int *x, double *b, int d);
/* calculate probability of binary vector x with bernoulli formula */
/* with distribution b  for vector of length d */
/* note: underflow risk with values of d > 50 when double is 64-bit float */

double m_fM (int *x, InfCentroid *B, double *w, int m, int d);
/* sum probability of binary vector x (m_fx) of length d over all m */
/* distributions B weighted with class weights given in w */

void calculate_matrix (Matrix *P, InfCentroid *B, ST *X, Vector *W, int m, int d, int n);
/* calculate new probability matrix (n*m) from set of n binary vectors */
/* X of length d and parameters B (m distributions of x) and W (m weights of x) */

void random_weights (Vector *W, int m);
/* draw such a random m weights to vector W that the sum of weights is 1.0 */

double m_fx (int *x, double *b, int d) {
  int i;
  double p;

  p = 1.0;
  for (i=1;i<d;i++) p *= (x[i] ? b[i] : (1.0 - b[i]));
  return p;
}


double m_fM (int *x, InfCentroid *B, double *w, int m, int d) {
  double p;
  int j;

  p = 0.0;
  for (j=1;j<m;j++) p += (w[j] * m_fx(x,B->el[j]->el,d));
  return p;
}


void calculate_matrix (Matrix *P, InfCentroid *B, ST *X, Vector *W, int m, int d, int n) {
  double *p;
  double *w;
  double h;
  int *x;
  int i,j;

  w = W->el;
  for (i=1;i<n;i++) {
    p = P->el[i]->el;
    x = get_vector(X);
    h = m_fM(x,B,w,m,d);
    for (j=1;j<m;j++) p[j] = (w[j] * m_fx(x,B->el[j]->el,d)) / h;
    X = next_element(X);
  }
}

long double m_p(int *x, Centroid *c, int d) {
  int j;
  double *b;
  long double p;

  b = c->el;
  p = 1.0;
  for (j=1;j<d;j++) p *= (x[j] ? (long double)b[j] : (1.0 - (long double)b[j]));
  return p;
}

long double m_sum(int *x, Vector *W, InfCentroid *C, int m, int d) {
  int r;
  double *b;
  long double p;

  p = 0.0;
  for (r=1;r<m;r++) p += ((long double)W->el[r] * m_p(x,C->el[r],d));
  return p;
}

void calculate_matrix2 (Matrix *P, InfCentroid *B, ST *X, Vector *W, int m, int d, int n) {
  double *p;
  double *w;
  long double h;
  int *x;
  int i,j;

  w = W->el;
  for (i=1;i<n;i++) {
    p = P->el[i]->el;
    x = get_vector(X);
    h = m_sum(x,W,B,m,d);
    for (j=1;j<m;j++) p[j] = (W->el[j] * m_p(x,B->el[j],d)) / h;
    X = next_element(X);
  }
}

void random_weights (Vector *W, int m) {
  int i;
  double *w;
  double s;

  m = W->l;
  w = W->el;
  s = 0.0;
  for (i=1;i<m;i++) {
    w[i] = give_true_random() + 0.05;
    s += w[i];
  }
  for (i=1;i<m;i++) w[i] /= s;
}


void update_weights (Vector *W, Matrix *P, int m, int n) {
  int j,i;
  double *p;
  double *w;

  w = W->el;
  for (j=1;j<m;j++) w[j] = 0.0;
  for (i=1;i<n;i++) {
    p = P->el[i]->el;
    for (j=1;j<m;j++) w[j] += p[j];
  }
#ifdef _STD_PRIOR
  for (j=1;j<m;j++) w[j] /= (double) n;
#else
  for (j=1;j<m;j++) w[j] = (1.0 + w[j]) / (2.0 + (double) n);
#endif
}


void update_centroids (InfCentroid *B, Matrix *P, ST *X, int m, int n, int d) {
  int i,j,k;
  double *p;
  double *a;
  double *b;
  int *x;
  Vector *Ta;
  Vector *Tb;
  ST *tmp;

  Ta = allocate_dvector(m);
  a = Ta->el;

  Tb = allocate_dvector(m);
  b = Tb->el;

  for (k=1;k<d;k++) {

    for (j=1;j<m;j++) {
      a[j] = 0.0;
      b[j] = 0.0;
    }

    tmp = X;
    for (i=1;i<n;i++) {
      x = get_vector(tmp);
      p = P->el[i]->el;
      for (j=1;j<m;j++) {
	a[j] += p[j];
	b[j] += ((double)x[k]) * p[j];
      }
      tmp = next_element(tmp);
    }

    for (j=1;j<m;j++) B->el[j]->el[k] = (1.0 / a[j]) * b[j];

  }

  deallocate_dvector(Ta);
  deallocate_dvector(Tb);

}

int class_of_vector (int i, int m, Matrix *P) {
  double max;
  int j,maxj;

  max = P->el[i]->el[1];
  maxj = 1;
  for (j=2;j<m;j++) {
    if (max < P->el[i]->el[j]) {
      max = P->el[i]->el[j];
      maxj = j;
    }
  }
  return maxj;
}

void update_centroids2 (InfCentroid *B, Matrix *P, ST *X, int m, int n, int d) {
  int j,k,i;
  Vector *T;
  int *x;

  T = allocate_dvector(m);
  for(j=1;j<m;j++) for (k=1;k<d;k++) B->el[j]->el[k] = 0.0;

  for (i=1;i<n;i++) {
    x = get_vector(X);
    j = class_of_vector(i,m,P);
    T->el[j] += 1.0;
    for (k=1;k<d;k++) B->el[j]->el[k] += (double) x[j];
    X = next_element(X);
  }
#ifdef _STD_PRIOR
  for(j=1;j<m;j++) for (k=1;k<d;k++) B->el[j]->el[k] = (T->el[j] == 0) ? 0.0 : B->el[j]->el[k] / T->el[j];
#else
  for(j=1;j<m;j++) for (k=1;k<d;k++) B->el[j]->el[k] = (1.0 + B->el[j]->el[k]) / (2.0 + T->el[j]);
#endif

  deallocate_dvector(T);

}

double mixture_likelihood (InfCentroid *B, Vector *W, ST *X, int m, int n, int d) {
  int i;
  double L;
  double *w;

  L = 0.0;
  w = W->el;
  for (i=1;i<n;i++) {
    L += log_2(m_fM(get_vector(X),B,w,m,d));
    X = next_element(X);
  }
  return L;
}

double mixture_likelihood2 (InfCentroid *B, Vector *W, Matrix *P, ST *X, int m, int n, int d) {
  int i,j;
  double h,L;
  ST *tmp;

  L = 0.0;
  for (j=1;j<m;j++) {
    h = 0.0;
    for (i=1;i<n;i++) h += P->el[i]->el[j];
    L += (h * log_2(W->el[j]));
  }

  for (j=1;j<m;j++) {
    tmp = X;
    for (i=1;i<n;i++) {
      L += P->el[i]->el[j] * log_2(m_fx(X->el->el,B->el[j]->el,d));
      tmp = next_element(tmp);
    }
  }

  for (i=1;i<n;i++) {
    for (j=1;j<m;j++) L -= (P->el[i]->el[j] * log_2(P->el[i]->el[j]));
  }

  return L;
}


void dump_matrix_P (FILE *f, Matrix *P, ST *X, Partition *C, int m, int n) {
  int i,j,jmax,k,t;
  double *p;
  double h;
  BV *x;

  /* dump matrix P */
  fprintf(f,"\nP[%d,%d]\n",n-1,m-1);
  for (i=1;i<n;i++) {
    x = X->el;
    fprintf(f,"%s %s",x->clasname,x->strain);
    p = P->el[i]->el;
    t = 0;
    h = p[1];
    jmax = 1;
    for (j=2;j<m;j++) {
      if (p[j] > h) {
	jmax = j;
	h = p[j];
      }
    }
    for (j=1;j<m;j++) {
      if (is_in_set(x,C->el[j])) fprintf(f," %4d:%f %4d:%f",j,p[j],jmax,h);
    }
    fprintf(f,"\n");
    X = next_element(X);
  }

  fflush(f);
}

void dump_mixture_param_P (FILE *f, Matrix *P, ST *X, int m, int n) {
  int i,j,k,t;
  double *p;
  BV *x;

  /* dump matrix P */
  fprintf(f,"\nP[%d,%d]\n",n-1,m-1);
  for (i=1;i<n;i++) {
    x = X->el;
    fprintf(f,"%s %s",x->clasname,x->strain);
    p = P->el[i]->el;
    t = 0;
    for (j=1;j<m;j++) {
#ifdef _WITH_LAYOUT
      if (p[j] > 0.005) {
	t++;
	fprintf(f," %4d %.2f",j,p[j]);
	if (((t % 5) == 0) && (t != (m-1))) {
	  fprintf(f,"\n ");
	  for (k=0;k<id_len;k++) fputc(' ',f);
	}
      }
#else
      fprintf(f," %4d:%f",j,p[j]);
#endif
    }
    fprintf(f,"\n");
    X = next_element(X);
  }

  fflush(f);

}

void dump_mixture_param_WB (FILE *f, Vector *W, InfCentroid *B, int d, int m) {
  int i,j;
  double *w;
  double *b;

  /* dump mixture parameters W and B */
  fprintf(f,"\nW[%d],B[%d,%d]\n",m-1,m-1,d-1);
  w = W->el;
  for (i=1;i<m;i++) {
    fprintf(f,"%.2f:",w[i]);
    b = B->el[i]->el;
    for (j=1;j<d;j++) {
      fprintf(f," %.2f",b[j]);
      if (((j % 14) == 0) && (j != (d-1))) fprintf(f,"\n     ");
    }
    fprintf(f,"\n");
  }

  fflush(f);

}

void dump_mixture_parameters (FILE *f, Vector *W, InfCentroid *B, Matrix *P, ST *X, int d, int m, int n) {

  dump_mixture_param_WB(f,W,B,m,n);
  dump_mixture_param_P(f,P,X,d,m);
}


Matrix *mixture_classifier (FILE *f, ST *X, double *L, int *I, int m, int n, int dump_param) {
  const char *func = "mixture_classifier";
  InfCentroid *B;
  Matrix *P;
  Vector *W;
  double Lp;
  int d,improvement,i;

  if (X == NULL) internal_error((char *)func);

  d = X->el->length;

  B = allocate_centroids(m,d);
  P = allocate_dmatrix(n,m);
  W = allocate_dvector(m);

  /* step 0: initialization */
  random_weights(W,m);
  pick_centroids(m,d,B,X);
  calculate_matrix(P,B,X,W,m,d,n);

  Lp = mixture_likelihood(B,W,X,m,n,d);

  improvement = TRUE;

  put_dot;

  if ((log_file) && dump_param) { 
    fprintf(f,"\nL = %.4f\n",Lp);
    fflush(f);
  }

  i=1;

  /* step 1: iteration */
#ifdef _ITERATION_LIMITER
  while ((improvement) && (i<100)) {
#else
  while (improvement) {
#endif
    update_weights(W,P,m,n);
    update_centroids(B,P,X,m,n,d);
    calculate_matrix(P,B,X,W,m,d,n);

    *L = mixture_likelihood(B,W,X,m,n,d);
    if ((log_file) && dump_param) {
      fprintf(f,"L = %.4f\n",*L);
      fflush(f);
    }

    if (fabs(*L - Lp) > _L_EPSILON) {
      Lp = *L;
    } else {
      improvement = FALSE;
    }
    put_dot;
    i++;
  }

  if ((log_file) && dump_param) {
    dump_mixture_parameters(f,W,B,P,X,d,m,n+1);
    fprintf(f,"\nL = %.4f\n",*L);
    fflush(f);
  }

  deallocate_centroids(B);
  deallocate_dvector(W);

  *I = i;
  return P;

}



Partition *build_cluster (Matrix *P, ST *X, int m, int n) {
  Partition *C;
  int i,j,closest;
  double dist,maxdist;
  BV *x;

  C = allocate_partition(m);
  C->k = m;

  for (j=0;j<m;j++) C->el[j] = NULL;
  for (i=1;i<n;i++) {
    x = get_element(X);
    closest = 1;
    maxdist = P->el[i]->el[1];
    for (j=2;j<m;j++) {
      dist = P->el[i]->el[j];
      if (dist > maxdist) {
	closest = j;
	maxdist = dist;
      }
    }
    C->el[closest] = add_element(C->el[closest],x);
    X = del_element(X);
  }

  return C;
}

Partition *build_cluster_and_map (Matrix *P, ST *X, IntVector *M, int m, int n) {
  Partition *C;
  int i,j,closest;
  double dist,maxdist;
  BV *x;

  C = allocate_partition(m);
  C->k = m;

  for (j=0;j<m;j++) C->el[j] = NULL;
  for (i=1;i<n;i++) {
    x = get_element(X);
    closest = 1;
    maxdist = P->el[i]->el[1];
    for (j=2;j<m;j++) {
      dist = P->el[i]->el[j];
      if (dist > maxdist) {
	closest = j;
	maxdist = dist;
      }
    }
    M->el[i] = closest;
    C->el[closest] = add_element(C->el[closest],x);
    X = del_element(X);
  }

  return C;
}


Partition *build_cluster_from_map (IntVector *M, ST *X, int m, int n) {
  Partition *C;
  int i,closest;
  BV *x;
  
  C = allocate_partition(m);
  C->k = m;
  
  for (i=0;i<m;i++) C->el[i] = NULL;
  for (i=1;i<n;i++) {
    x = get_element(X);
    closest = M->el[i];
    C->el[closest] = add_element(C->el[closest],x);
    X = del_element(X);
  }

  return C;
}


void trim_cluster (Partition *P) {
  /* Remove empty sets */
  const char *func = "trim_cluster";
  int k,i,j;
  
  if (P == NULL) internal_error((char *)func);
  
  k = P->k; /* no empty ones sofar */
  i = 1;
  while (i < k) {
    if (P->el[i] == NULL) {
      k--;
      for (j=i;j<k;j++) {
	P->el[j] = P->el[j+1];
      }
      P->el[k] = NULL;
    } else {
      i++;
    }
  }
  P->k = k;
}

ST *sort_set (ST *W) {
  ST *V = NULL;
  BV *x;

  while (elements_left(W)) {
    x = get_element(W);
    V = add_element_in_order(V,x);
    W = del_element(W);
  }

  return V;
}

void apply_mixture_classifier_once (char *datfile, char *outfile, char *parfile, char *hdrfile, int m) {
  const char *func = "apply_mixture_classifier_once";
  time_t tm;
  FILE *o = NULL;
  FILE *f;
  ST *X;
  int n,i;
  double l;
  Matrix *P;
  Partition *C;


  tm = time(&tm);
  set_rand(tm);

  if (log_file) {
    if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
    start_text(o);
    fflush(o);
  }

  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..");
  X = read_set(f,hdrfile);
  X = sort_set(X);
  n = size(X);
  if (log2_factorials == NULL) log2_factorials = prepare_log2_factorials(n+n);
  fclose(f);
  if (log_file) {
    fprintf(o,"MIXTURE CLASSIFICATION\n\nSet size: %d\nClasses:  %d\n\n",n,m-1);
    fflush(o);
  }
  if (verbose) fprintf(stdout,".. read %d vectors of data\n\nCalculating mixture parameters: ",n);
  
  P = mixture_classifier(o,X,&l,&i,m,n+1,TRUE);
  
  if (verbose) fprintf(stdout," ok\nSaving partition ..");
  
  C = build_cluster(P,X,m,n+1);
  
  if ((f = fopen(parfile,"w")) == NULL) file_error(parfile,(char *)func);
  inf_write_partition_po(f,C);
  fclose(f);
  
  if (verbose) fprintf(stdout,".. ok\n");
  
  deallocate_dmatrix(P);

  fclose(o);
}

void mixture_dump_stat (FILE *f, Vector *X, int n) {
  double sd,me,avg;
  int i;

  avg = 0.0;
  for (i=1;i<n;i++) avg = avg + X->el[i];
  avg = (avg / (double) (n-1));
  sd = 0.0;
  if (avg > 0.0) for (i=1;i<n;i++) sd = sd + pow((X->el[i] - avg),2.0);
  sd = (sd > 0.0) ? sqrt(sd / (double) (n-1)) : 0.0;
  me = sd / sqrt((double) (n-1));
  fprintf(f," Average:            %1.5f\n",avg);
  fprintf(f," Standard deviation: %1.5f\n",sd);
  fprintf(f," Mean error:         %1.5f\n",me);
  fprintf(f," Variance:           %1.5f\n",pow(sd,2.0));
}

void mixture_dump_stat2 (FILE *f, IntVector *X, int n) {
  double sd,me,avg;
  int i;

  avg = 0.0;
  for (i=1;i<n;i++) avg = avg + ((double)(X->el[i]));
  avg = (avg / (double) (n-1));
  sd = 0.0;
  for (i=1;i<n;i++) sd = sd + pow((((double)(X->el[i])) - avg),2.0);
  sd = sqrt(sd / (double) (n-1));
  me = sd / sqrt((double) (n-1));
  fprintf(f," Average:            %1.2f\n",avg);
  fprintf(f," Standard deviation: %1.3f\n",sd);
  fprintf(f," Mean error:         %1.3f\n",me);
  fprintf(f," Variance:           %1.3f\n",pow(sd,2.0));
}

void apply_mixture_classifier (char *datfile, char *outfile, char *parfile1, char *parfile2, char *resfile, char *hdrfile, int m) {
  const char *func = "apply_mixture_classifier";
  time_t tm;
  FILE *o = NULL;
  FILE *f;
  ST *X;
  int n,i,it,d;
  double l,lmax;
  Vector *L;
  Vector *SC;
  IntVector *I;
  double sc,scmin;
  double sclmax = 0.0;
  Matrix *P;
  Partition *C;
  double a,b,mse,ccoef;

  tm = time(&tm);
  set_rand(tm);

  if (log_file) {
    if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
    start_text(o);
    fflush(o);
  }

  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..");
  X = read_set(f,hdrfile);
  X = sort_set(X);
  n = size(X);
  if (log2_factorials == NULL) log2_factorials = prepare_log2_factorials(n+n);
  fclose(f);
  if (log_file) {
    fprintf(o,"MIXTURE CLASSIFICATION (ANALYSIS)\n\nSet size: %d\nClasses:  %d\n\n",n,m-1);
    fflush(o);
  }

  L = allocate_dvector(sample_mixture);
  SC = allocate_dvector(sample_mixture);
  I = allocate_ivector(sample_mixture);
  
  d = vec_len;
  if (verbose) {
    fprintf(stdout,".. read %d vectors of data\n\nRunning for %d samples\n\n",n,sample_mixture-1);
    fprintf(stdout,"  1: ");
  }
  if (log_file) fprintf(o,"\nTRY: 1\n");
  P = mixture_classifier(o,X,&l,&it,m,n+1,FALSE);
  C = build_cluster(P,X,m,n+1);
  trim_cluster(C);
  sc = stochastic_complexity(C,(C->k),d);
  L->el[1] = -l;
  SC->el[1] = sc;
  I->el[1] = it;
  lmax = l;
  scmin = sc;
  if ((f = fopen(parfile1,"w")) == NULL) file_error(parfile1,(char *)func);
  inf_write_partition_po(f,C);
  fclose(f);
  if ((f = fopen(parfile2,"w")) == NULL) file_error(parfile2,(char *)func);
  inf_write_partition_po(f,C);
  fclose(f);
  if (log_file) {
    fprintf(o,"  m  = %d\n",((C->k)-1));
    fprintf(o,"  L  = %.4f\n",l);
    fprintf(o,"  SC = %.4f\n",sc);
    fprintf(o,"  I  = %d\n",it);
    fflush(o);
  }
  if (verbose) {
    fprintf(stdout," ok\nL  = %.0f\n",l);
    fprintf(stdout,"SC = %.4f\n",sc);
    fflush(stdout);
  }
  deallocate_dmatrix(P);
  X = partition_to_set(C);
  deallocate_partition(C);
  
  for (i=2;i<sample_mixture;i++) {
    if (verbose) fprintf(stdout,"%3d: ",i);
    if (log_file) fprintf(o,"TRY: %d\n",i);
    P = mixture_classifier(o,X,&l,&it,m,n+1,FALSE);
    C = build_cluster(P,X,m,n+1);
    trim_cluster(C);
    sc = stochastic_complexity(C,(C->k),d);
    L->el[i] = -l;
    SC->el[i] = sc;
    I->el[i] = it;
    if (verbose) {
      fprintf(stdout," ok\nL  = %.0f\n",l);
      fprintf(stdout,"SC = %.4f\n",sc);
      fflush(stdout);
    }
    if (log_file) {
      fprintf(o,"  m  = %d\n",((C->k)-1));
      fprintf(o,"  L  = %.4f\n",l);
      fprintf(o,"  SC = %.4f\n",sc);
      fprintf(o,"  I  = %d\n",it);
      fflush(o);
    }
    if (sc < scmin) {
      scmin = sc;
      if ((f = fopen(parfile2,"w")) == NULL) file_error(parfile2,(char *)func);
      inf_write_partition_po(f,C);
      fclose(f);
    }
    if (l > lmax) {
      lmax = l;
      sclmax = sc;
      if ((f = fopen(parfile1,"w")) == NULL) file_error(parfile1,(char *)func);
      inf_write_partition_po(f,C);
      fclose(f);
      
      X = partition_to_set(C);
      deallocate_partition(C);
      
      if ((f = fopen(resfile,"w")) == NULL) file_error(resfile,(char *)func);
      dump_mixture_param_P(f,P,X,m,n+1);
      fclose(f);
      
      deallocate_dmatrix(P);
    } else {
      deallocate_dmatrix(P);
      X = partition_to_set(C);
      deallocate_partition(C);
    }
  }
  if (log_file) {
    /* statistical data */
    /* SC */
    fprintf(o,"\nL\n");
    fprintf(o," Lmax:               %1.5f\n",lmax);
    mixture_dump_stat(o,L,sample_mixture);
    fprintf(o,"\nSC\n");
    fprintf(o," SCmin:              %1.5f\n",scmin);
    fprintf(o," SC(Lmax):           %1.5f\n",sclmax);
    mixture_dump_stat(o,SC,sample_mixture);
    fprintf(o,"\nIterations\n");
    mixture_dump_stat2(o,I,sample_mixture);

    /* correlation */
    fprintf(o,"\nMLE line and Correlation (L,SC)\n");
    mle_approx_2dim(L,SC,&a,&b);
    fprintf(o,"  a                   %.4f\n",a);
    fprintf(o,"  b                   %.4f\n",b);
    mse = calculate_mse(a,b,L,SC);
    fprintf(o,"  mse                 %.4f\n",mse);
    ccoef = correlation_coef(L,SC);
    fprintf(o,"  correlation         %.4f\n\n",ccoef);
  }
  
  fclose(o);
}

int count_well_fit (Matrix *P) {
  int i,j,n,m,wf;

  wf = 0;
  n = P->s;
  for (i=1;i<n;i++) {
    m = P->el[i]->l;
    for (j=1;j<m;j++) if (P->el[i]->el[j] >= 0.995) wf++;
  }
  return wf;
}

double aff (double d, int n) {
  return (d / ((double) n));
}

void perform_robustness_test (char *datfile, char *outfile, char *parfile1, char *parfile2, char *resfile, char *hdrfile, int m) {
  const char *func = "perform_robustness_test";
  time_t tm;
  FILE *o = NULL;
  FILE *f;
  ST *X;
  ST *V;
  ST *W;
  int n,i,it,d,wf,wfmin,imin;
  double l,lmax,h;
  Vector *L;
  Vector *SC;
  Vector *D;
  IntVector *I;
  IntVector *WF;
  double sc,scmin;
  double sclmax = 0.0;
  Matrix *P;
  IntVector **M;
  Partition *C;
  Partition *Cb;
  double a,b,mse,ccoef;

  tm = time(&tm);
  set_rand(tm);

  exact_matches = TRUE;
  
  if (log_file) {
    if ((o = fopen(outfile,"w")) == NULL) file_error(outfile,(char *)func);
    start_text(o);
    fflush(o);
  }

  if ((f = fopen(datfile,"r")) == NULL) file_error(datfile,(char *)func);
  if (verbose) fprintf(stdout,"Starting ..");
  X = read_set(f,hdrfile);
  X = sort_set(X);
  n = size(X);
  if (log2_factorials == NULL) log2_factorials = prepare_log2_factorials(n+n);
  fclose(f);
  if (log_file) {
    fprintf(o,"MIXTURE CLASSIFICATION (ANALYSIS)\n\nSet size: %d\nClasses:  %d\n\n",n,m-1);
    fflush(o);
  }

  L = allocate_dvector(sample_mixture);
  SC = allocate_dvector(sample_mixture);
  D = allocate_dvector(sample_mixture);
  I = allocate_ivector(sample_mixture);
  WF = allocate_ivector(sample_mixture);
  if ((M = (IntVector **) malloc(sizeof(void *)*sample_mixture+1)) == NULL) out_of_mem();
  for (i=0;i<sample_mixture;i++) M[i] = NULL;


  d = vec_len;
  if (verbose) {
    fprintf(stdout,".. read %d vectors of data\n\nRunning for %d samples\n\n",n,sample_mixture-1);
    fprintf(stdout,"  1: ");
  }
  if (log_file) fprintf(o,"\nTRY: %3d",1);
  P = mixture_classifier(o,X,&l,&it,m,n+1,FALSE);
  wf = count_well_fit(P);
  wfmin = wf;
  M[1] = allocate_ivector(n+1);
  C = build_cluster_and_map(P,X,M[1],m,n+1);
  trim_cluster(C);
  sc = stochastic_complexity(C,(C->k),d);
  WF->el[1] = wf;
  L->el[1] = -l;
  SC->el[1] = sc;
  I->el[1] = it;
  lmax = l;
  sclmax = sc;
  scmin = sc;
  if ((f = fopen(parfile1,"w")) == NULL) file_error(parfile1,(char *)func);
  inf_write_partition_po(f,C);
  fclose(f);
  if ((f = fopen(parfile2,"w")) == NULL) file_error(parfile2,(char *)func);
  inf_write_partition_po(f,C);
  fclose(f);
  if (log_file) {
    fprintf(o," m  = %4d,",((C->k)-1));
    fprintf(o," wf = %6d,",wf);
    fprintf(o," L  = %6d,",(int)floor(l+0.5));
    fprintf(o," SC = %.4f,",sc);
    fprintf(o," I  = %4d\n",it);
    fflush(o);
  }
  if (verbose) {
    fprintf(stdout," ok\nL  = %.0f\n",l);
    fprintf(stdout,"SC = %.4f\n",sc);
    fflush(stdout);
  }
  X = partition_to_set(C);
  X = sort_set(X);
  deallocate_partition(C);
  if ((f = fopen(resfile,"w")) == NULL) file_error(resfile,(char *)func);
  dump_mixture_param_P(f,P,X,m,n+1);
  fclose(f);
  imin = 1;
  deallocate_dmatrix(P);

  for (i=2;i<sample_mixture;i++) {
    if (verbose) fprintf(stdout,"%3d: ",i);
    if (log_file) fprintf(o,"TRY: %3d",i);
    P = mixture_classifier(o,X,&l,&it,m,n+1,FALSE);
    wf = count_well_fit(P);
    WF->el[i] = wf;
    M[i] = allocate_ivector(n+1);
    C = build_cluster_and_map(P,X,M[i],m,n+1);
    trim_cluster(C);
    sc = stochastic_complexity(C,(C->k),d);
    L->el[i] = -l;
    SC->el[i] = sc;
    I->el[i] = it;
    if (verbose) {
      fprintf(stdout," ok\nL  = %.0f\n",l);
      fprintf(stdout,"SC = %.4f\n",sc);
      fflush(stdout);
    }
    if (log_file) {
      fprintf(o," m  = %4d,",((C->k)-1));
      fprintf(o," wf = %6d,",wf);
      fprintf(o," L  = %6d,",(int)floor(l+0.5));
      fprintf(o," SC = %.4f,",sc);
      fprintf(o," I  = %4d\n",it);
      fflush(o);
    }
    if (sc < scmin) {
      scmin = sc;
      imin = i;
      if ((f = fopen(parfile2,"w")) == NULL) file_error(parfile2,(char *)func);
      inf_write_partition_po(f,C);
      fclose(f);
    }
    if (l > lmax) {
      lmax = l;
      sclmax = sc;
    }
    if (wf < wfmin) {
      wfmin = wf;
      if ((f = fopen(parfile1,"w")) == NULL) file_error(parfile1,(char *)func);
      inf_write_partition_po(f,C);
      fclose(f);
      
      X = partition_to_set(C);
      X = sort_set(X);
      deallocate_partition(C);
      
      if ((f = fopen(resfile,"w")) == NULL) file_error(resfile,(char *)func);
      dump_mixture_param_P(f,P,X,m,n+1);
      fclose(f);
      
    } else {
      X = partition_to_set(C);
      X = sort_set(X);
      deallocate_partition(C);
    }
    deallocate_dmatrix(P);
  }


  if (verbose) fprintf(stdout,"\nCaculating distances to best classification (%d):\n",imin);
  if (log_file) {
    fprintf(o,"\n\nDistances\n");
    fflush(o);
  }
  V = copy_set_fast(X);
  V = sort_set(V);
  W = copy_set_fast(X);
  W = sort_set(W);
  Cb = build_cluster_from_map(M[imin],V,m,n+1);
  trim_cluster(Cb);
  for (i=1;i<sample_mixture;i++) {
    if (verbose) fprintf(stdout,"%d ",i);
    C = build_cluster_from_map(M[i],X,m,n+1);
    trim_cluster(C);
    sc = stochastic_complexity(C,(C->k),d);
    h = calculate_distance(W,Cb,C);
    D->el[i] = aff(h,n);
    X = partition_to_set(C);
    X = sort_set(X);
    deallocate_partition(C);
    deallocate_dvector(M[i]);
    if (log_file) {
      fprintf(o," D(%4d,%4d) = %.2f (%.1f)\n",i,imin,D->el[i],h);
      fflush(o);
    }
  }

  if (log_file) {
    /* statistical data */
    /* SC */
    fprintf(o,"\nL\n");
    fprintf(o," Lmax:               %1.5f\n",lmax);
    mixture_dump_stat(o,L,sample_mixture);
    fprintf(o,"\nSC\n");
    fprintf(o," SCmin:              %1.5f\n",scmin);
    fprintf(o," SC(Lmax):           %1.5f\n",sclmax);
    mixture_dump_stat(o,SC,sample_mixture);
    fprintf(o,"\nDistances\n");
    mixture_dump_stat(o,D,sample_mixture);
    fprintf(o,"\nIterations\n");
    mixture_dump_stat2(o,I,sample_mixture);
    fprintf(o,"\nWell-fit\n");
    fprintf(o," WFmin:              %d\n",wfmin);
    mixture_dump_stat2(o,WF,sample_mixture);
    
    /* correlation */
    fprintf(o,"\nMLE line and Correlation (L,SC)\n");
    mle_approx_2dim(L,SC,&a,&b);
    fprintf(o,"  a                   %.4f\n",a);
    fprintf(o,"  b                   %.4f\n",b);
    mse = calculate_mse(a,b,L,SC);
    fprintf(o,"  mse                 %.4f\n",mse);
    ccoef = correlation_coef(L,SC);
    fprintf(o,"  correlation         %.4f\n\n",ccoef);

    /* correlation */
    fprintf(o,"\nMLE line and Correlation (D,SC)\n");
    mle_approx_2dim(D,SC,&a,&b);
    fprintf(o,"  a                   %.4f\n",a);
    fprintf(o,"  b                   %.4f\n",b);
    mse = calculate_mse(a,b,D,SC);
    fprintf(o,"  mse                 %.4f\n",mse);
    ccoef = correlation_coef(D,SC);
    fprintf(o,"  correlation         %.4f\n\n",ccoef);
  }
  
  fclose(o);
}

/* end of mixture.c */
