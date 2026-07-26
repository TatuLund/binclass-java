/*
This is module for generating report from output of binary classification
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "const.h"
#include "vars.h"
#include "bottom.h"
#include "binset.h"
#include "distmin.h"
#include "binstuff.h"
#include "centroid.h"
#include "cumulat.h"
#include "vectors.h"
#include "adding.h"
#include "format.h"
#include "tree.h"
#include "mixture.h"

/* types */

typedef struct {
  char *spec;
  int count;
  int *all;
  int *ones;
  void *next;
} FList;

typedef struct {
  FList *freqs;
  int size;
  int species;
  int *all;
  int *ones;
  void *next;
} CList;

/* prototypes */

/* public */
void generate_report (FILE *f, FILE *r, char *misfile, char *hdrfile);
double class_nearness (ST *V1, ST *V2);

/* private */
FList *new_freq (BV *x);
/* allocate new entry of frequency list and initialize it with values of x (name,frequencies) */
FList *add_in_alfabet (FList *fl, FList *fe);
/* add entry fe to list fl to correct position, returned pointer is different */
/* in case added entry was the first one */
FList *add_freq (FList *fl, BV *x, int *added);
/* update frequencies with x, if the name was new, allocate new entry and add it to */
/* the list with add_in_alfabet and return true in added */
FList *collect_freqs (Partition *P, FList *fl, int *spec, int *total);
CList *new_class (FList * fl);
CList *add_class (CList *cl, FList *fl);
CList *last_class (CList *cl);
CList *collect_freqs_by_class (Partition *P, CList *cl, int *classes);
ST *search_id_p (char *id, Partition *P);
int percent (int x1, int x2);
void write_freqs (FILE *f, FList *fl);
int total_count (char *s, FList *fl);
void write_freqs_by_class (FILE *f, Partition *P, InfCentroid *C, CList *cl, FList *fl, double *cds, int k, Matrix *M);
int ham_dist (int *x, int *y);
Matrix *generate_nearness_matrix (Partition *P);
Matrix *generate_hellinger_matrix (InfCentroid *C);
void write_nearness_matrix (FILE *f, Matrix *M);

/* implementation */

int TRASH = FALSE;
int PRINT_PREDFIT = FALSE;

FList *new_freq (BV *x) {
  FList *fe;
  int i;
  char *s;
  int *vec;

  vec = x->el;
  s = x->clasname;
  if ( (fe = (FList *) malloc(sizeof(FList))) == NULL ) out_of_mem();
  if ( (fe->all = (int *) malloc(sizeof(int)*vec_len)) == NULL ) out_of_mem();
  if ( (fe->ones = (int *) malloc(sizeof(int)*vec_len)) == NULL ) out_of_mem();
  fe->spec = s;
  fe->count = 1;
  for (i=1;i<vec_len;i++) {
    fe->all[i] = 1;
    if (vec[i] == 0) fe->ones[i] = 0;
    else fe->ones[i] = 1;
  }
  fe->next = NULL;
  put_dot;
  return fe;
}

FList *add_in_alfabet (FList *fl, FList *fe) {
  FList *tmp;
  FList *l;
  
  tmp = fl;
  l = tmp;
  while ((l != NULL) && before(fe->spec,l->spec)) {
    l = l->next;
  }
  if (l != fl) {
    tmp = fl;
    while (tmp->next != l) {
      tmp = tmp->next;
    }
    fe->next = l;
    tmp->next = fe;
  } else {
    fe->next = fl;
    fl = fe;
  }
  return fl;
}

FList *add_freq (FList *fl, BV *x, int *added) {
  FList *tmp;
  int f,i;
  int *vec;
  char *s;

  vec = x->el;
  s = x->clasname;
  tmp = fl;
  f = FALSE;
  *added = FALSE;
  if (fl == NULL) {
    fl = new_freq(x);
    *added = TRUE;
  } else {
    while ((tmp != NULL) && (!f)) {
      if (strcmp(s,tmp->spec) == 0) {
	tmp->count++;
	for (i=1;i<vec_len;i++) {
	  tmp->all[i]++;
	  tmp->ones[i] += vec[i];
	}
	f = TRUE;
      } else {
	tmp = tmp->next;
      }
    }
    if (!f) {
      tmp = new_freq(x);
      fl = add_in_alfabet(fl,tmp);
      *added = TRUE;
    }
  }
  return fl;
}

FList *collect_freqs (Partition *P, FList *fl, int *spec, int *total) {
  int a,cs,ct,k,i;
  ST *V;
  BV *x;

  a = FALSE;
  cs = 0;
  ct = 0;
  k = P->k;
  if (verbose) fprintf(stdout,"Counting frequencies: ");
  for(i=1;i<k;i++) {
    V = P->el[i];
    while(!no_elements(V)) {
      x = get_element(V);
      fl = add_freq(fl,x,&a);
      if (a) cs++;
      ct++;
      V = next_element(V);
    }
  }
  *spec = cs;
  *total = ct;
  return fl;
}

CList *new_class (FList * fl) {
  CList *ce;
  int i;
  
  if ( (ce = (CList *) malloc(sizeof(CList))) == NULL ) {
    fprintf(stderr,"Out of memory\n");
    exit(1);
  } else {
    ce->freqs = fl;
    ce->next = NULL;
    ce->size = 0;
    ce->species = 0;
    if ( (ce->all = (int *) malloc(sizeof(int)*vec_len)) == NULL ) out_of_mem();
    if ( (ce->ones = (int *) malloc(sizeof(int)*vec_len)) == NULL ) out_of_mem();
    for (i=0;i<vec_len;i++) {
      ce->all[i] = 0;
      ce->ones[i] = 0;
    }
  }
  return ce;
}

CList *add_class (CList *cl, FList *fl) {
  CList *tmp;
  
  tmp = cl;
  if (cl == NULL) {
    cl = new_class(fl);
  } else {
    while (tmp->next != NULL) tmp = tmp->next;
    tmp->next = new_class(fl);
  }
  return cl;
}

CList *last_class (CList *cl) {
  CList *tmp;
  
  tmp = cl;
  while (tmp->next != NULL) tmp = tmp->next;
  return tmp;
}

CList *collect_freqs_by_class (Partition *P, CList *cl, int *classes) {
  int a,i,j,k;
  FList *fl;
  CList *tmp;
  ST *V;
  BV *x;

  if (verbose) fprintf(stdout,"\nCounting frequencies in classes: ");
  a = FALSE;
  tmp = NULL;
  k = P->k;
  *classes = k-1;
  for (i=1;i<k;i++) {
    V = P->el[i];
    fl = NULL;
    cl = add_class(cl,fl);
    if (verbose) {
      fprintf(stdout,"(%d)",i);
      fflush(stdout);
    }
    tmp = last_class(cl);
    while(!no_elements(V)) {
      x = get_element(V);
      tmp->size++;
      fl = tmp->freqs;
      fl = add_freq(fl,x,&a);
      for (j=1;j<vec_len;j++) {
	tmp->all[j]++;
	tmp->ones[j] += x->el[j];
      }
      tmp->freqs = fl;
      if (a) tmp->species++;
      V = next_element(V);
    }
  }
  return cl;
}

ST *search_id_p (char *id, Partition *P) {
  ST *tmp;
  ST *tmp2 = NULL;
  int found,i,k;
  
  k = P->k;
  found = FALSE;
  i = 0;
  while ((i<k) && (!found)) {
    tmp = P->el[i];
    while ((tmp != NULL) && (!found)) {
      found = (strcmp(id,tmp->el->strain) == 0);
      tmp2 = tmp;
      tmp = tmp->next;
    }
    i++;
  }
  if (!found) tmp2 = NULL;
  return tmp2;
}

int percent (int x1, int x2) {
  if (x2 == 0) return 0;
  else return (int)(((double)x1/(double)x2)*100);
}

void write_freqs (FILE *f, FList *fl) {
  int i,j;
  double pr;
  
  fprintf(f,"\nTOTAL FREQUENCIES:\n-----------------\n\n");
  if (fl != NULL) {
    while (fl != NULL) {
      fprintf(f," %s: %4d   ",fl->spec,fl->count);
      if (print_digits) {
	for (i=1;i<vec_len;i++) {
	  pr = (double)((double) fl->ones[i] / (double) fl->all[i]);
	  if (pr > 0.5) fputc('1',f);
	  else if (pr < 0.5) fputc('0',f);
	  else fputc('-',f);
	  if (i != (vec_len-1)) {
	    if ((i % 60) == 0) {
	      fprintf(f,"\n          ");
	      for (j=0;j<name_len;j++) fputc(' ',f);
	    }
	  }
	}
      } else {
	for (i=1;i<vec_len;i++) {
	  fprintf(f,"%3d",percent(fl->ones[i],fl->all[i]));
	  if (i != (vec_len-1)) {
	    fputc(',',f);
	    if ((i % 15) == 0) {
	      fprintf(f,"\n          ");
	      for (j=0;j<name_len;j++) fputc(' ',f);
	    }
	  }
	}
      }
      fprintf(f,"\n");
      fl = fl->next;
    }
  }
}


int total_count (char *s, FList *fl) {
  int f,c;
  FList *tmp;
  
  if (fl == NULL) return 0;
  
  c = 0;
  f = FALSE;
  tmp = fl;
  if (strcmp(tmp->spec,s) == 0) {
    c = tmp->count;
  } else {
    while ((tmp != NULL) && (!f)) {
      if (strcmp(tmp->spec,s) == 0) {
	c = tmp->count;
	f = TRUE;
      } else {
	tmp = tmp->next;
      }
    }
  }
  return c;
}

int nearest_class (int c, Matrix *M) {
  double dmin,d;
  int i,s,c1;
  int imin = 1;
  
  s = (M->s)-1;
  c1 = c+1;
  dmin = 10000.0;
  for (i=1;i<c;i++) {
    d = M->el[c]->el[i];
    if (d < dmin) {
      dmin = d;
      imin = i;
    }
  }
  for (i=c1;i<s;i++) {
    d = M->el[i]->el[c];
    if (d < dmin) {
      dmin = d;
      imin = i;
    }
  }
  return imin;
}

int farest_class (int c, Matrix *M) {
  double dmax,d;
  int i,s,c1;
  int imax = 1;
  
  s = (M->s)-1;
  c1 = c + 1;
  dmax = 0.0;
  for (i=1;i<c1;i++) {
    d = M->el[c]->el[i];
    if (d > dmax) {
      dmax = d;
      imax = i;
    }
  }
  for (i=c;i<s;i++) {
    d = M->el[i]->el[c];
    if (d > dmax) {
      dmax = d;
      imax = i;
    }
  }
  return imax;
}

void copy_distances (int c, Matrix *M, Vector *X) {
  int s,i,c1;
  
  s = (M->s);
  c1 = c + 1;
  for (i=1;i<c1;i++) X->el[i] = M->el[c]->el[i];
  for (i=c;i<s;i++) X->el[i] = M->el[i]->el[c];
  X->el[c] = 0.0;
}

void print_neighbors (FILE *f, Vector *X, IntVector *I) {
  int l,i;
  double avg;

  avg = 0.0;
  l = (X->l)-1;
  fprintf(f,"Neighbors:  ");
  for (i=1;i<l;i++) {
    fprintf(f,"[%3d,",I->el[i+1]);
    if ((X->el[i+1]) < 9.995) fputc(' ',f);
    fprintf(f,"%1.2f] ",X->el[i+1]);
    avg += X->el[i+1];
    if (((i % 5) == 0) && (i != (l-1))) fprintf(f,"\n            ");
  }
  avg /= (double) (l-2);
  fprintf(f,"\n");
  fprintf(f,"Average:    %1.2f\n",avg);
}

void write_freqs_by_class (FILE *f, Partition *P, InfCentroid *C, CList *cl, FList *fl, double *cds, int k, Matrix *M) {
  FList *tmp;
  CList *t;
  int c,x1,x2,i,j,p,st;
  double d,pr;
  Vector *X;
  IntVector *I;
  
  X = allocate_dvector(k);
  I = allocate_ivector(k);
  fprintf(f,"\nLIST OF CLASSES:\n---------------\n\n");
  if (TRASH) c = -1;
  else c = 0;
  t = cl;
  if (t != NULL) {
    while (t != NULL) {
      c++;
      if (c == 0) fprintf(f,"\nClass:      %d (Trash)\n",c);
      else fprintf(f,"\nClass:      %d / %d\n",c,(k-1));
      if ((report_params & RP_NEIGHBOR) == RP_NEIGHBOR) {
	for (i=1;i<k;i++) I->el[i] = i;
	copy_distances(c,M,X);
	indexed_qsort(X,I);
	print_neighbors(f,X,I);
      }
      if ((report_params & (RP_NEARNESS | RP_NEIGHBOR)) == RP_NEARNESS) {
	st = (affinity_matrix) ? farest_class(c,M) : nearest_class(c,M);
	d = M->el[c]->el[st];
	if (d < EPS) d = M->el[st]->el[c];
	fprintf(f,"Nearest:    %d (%1.2f)\n",st,d);
	st = (affinity_matrix) ? nearest_class(c,M) :farest_class(c,M);
	d = M->el[c]->el[st];
	if (d < EPS) d = M->el[st]->el[c];
	fprintf(f,"Farest:     %d (%1.2f)\n",st,d);
      }
      fprintf(f,"Size:       %d\n",t->size);
      fprintf(f,"Species:    %d\n",t->species);
      fprintf(f,"HMO:        ");
      for (i=1;i<vec_len;i++) {
	p = percent(t->ones[i],t->all[i]);
	if (p < 50) fputc('0',f);
	else if (p > 50) fputc('1',f);
	else fputc('-',f);
/*	if ((i % 65) == 0) fprintf(f,"\n            "); */
      }
/*      fprintf(f,"\nCentroid:   ");
      for (i=1;i<vec_len;i++) {
	fprintf(f,"%3d",percent(t->ones[i],t->all[i]));
	if (i != (vec_len-1)) {
	  fputc(',',f);
	  if ((i % 12) == 0) fprintf(f,"\n            ");
	}
      } */
      fprintf(f,"\n");
      fprintf(f,"Codelength: %2.4f\n",cds[c]); 
      fprintf(f,"Distortion: %2.4f\n",class_distortion(P->el[c],C->el[c]));
      fprintf(f,"--------------\n");
      tmp = t->freqs;
      if ((report_params & RP_FREQ) == RP_FREQ) {
	while (tmp != NULL) {
	  x1 = tmp->count;
	  x2 = total_count(tmp->spec,fl);
	  if (x2 != 0) {
	    fprintf(f," %s: %4d / %4d (%3d)  ",tmp->spec,x1,x2,percent(x1,x2));
	    if (print_digits) {
	      for (i=1;i<vec_len;i++) {
		pr = (double)((double) tmp->ones[i] / (double) tmp->all[i]);
		if (pr > 0.5) fputc('1',f);
		else if (pr < 0.5) fputc('0',f);
		else fputc('-',f);
/*		if (i != (vec_len-1)) {
		  if ((i % 48) == 0) {
		    fprintf(f,"\n                      "); 
		    for (j=0;j<name_len;j++) fputc(' ',f);
		  }
		} */
	      }
	    } else {
	      for (i=1;i<vec_len;i++) {
		fprintf(f,"%3d",percent(tmp->ones[i],tmp->all[i]));
		if (i != (vec_len-1)) {
		  fputc(',',f);
		  if ((i % 12) == 0) {
		    fprintf(f,"\n                      ");
		    for (j=0;j<name_len;j++) fputc(' ',f);
		  }
		}
	      }
	    }
	    fprintf(f,"\n");
	  }
	  tmp = tmp->next;
	}
      }
      if ((report_params & RP_PARTITION) == RP_PARTITION) {
	fprintf(f,"Partition:\n--------------\n");
	emp_write_set(f,P->el[c],PRINT_PREDFIT);
      }
      fprintf(f,"\n");
      fputc(12,f);
      t = t->next;
    }
  }
  deallocate_dvector(X);
  deallocate_ivector(I);
}

int ham_dist (int *ex, int *ey) {
  int l,i,d;

  d = 0;
  l = vec_len-1;
  for (i=1;i<l;i+=2) {
    d += (ex[i] != ey[i]);
    d += (ex[i+1] != ey[i+1]);
  }
  if (l % 2) d += (ex[l] != ey[l]);
  return d;
}

double class_nearness (ST *V1, ST *V2) {
  ST *t1;
  ST *t2;
  int n,sum,d,dmin;
  double n1,n2;
  
  t1 = V1;
  sum = 0;
  n = 0;
  while (elements_left(t1)) {
    n++;
    t2 = V2;
    dmin = ham_dist(t1->el->el,t2->el->el);
    t2 = next_element(t2);
    while (elements_left(t2)) {
      d = ham_dist(t1->el->el,t2->el->el);
      if (d < dmin) dmin = d;
      t2 = next_element(t2);
    }
    t1 = next_element(t1);
    sum += dmin;
  }
  n1 = ((double)sum)/((double)n);
  t1 = V2;
  sum = 0;
  n = 0;
  while (elements_left(t1)) {
    n++;
    t2 = V1;
    dmin = ham_dist(t1->el->el,t2->el->el);
    t2 = next_element(t2);
    while (elements_left(t2)) {
      d = ham_dist(t1->el->el,t2->el->el);
      if (d < dmin) dmin = d;
      t2 = next_element(t2);
    }
    t1 = next_element(t1);
    sum+=dmin;
  }
  n2 = ((double)sum)/((double)n);
  return ((n1 + n2) * 0.5);
}

Matrix *generate_nearness_matrix (Partition *P) {
  int k,i,j;
  double d,l;
  Matrix *M;

  l = (double)(vec_len-1);
  k = P->k;
  M = allocate_dmatrix(k,k);
  for (i=1;i<k;i++) {
    for (j=1;j<i;j++) {
      d = class_nearness(P->el[i],P->el[j]);
      if (affinity_matrix) d = ((l - d) / l);
      M->el[i]->el[j] = d;
    }
    put_dot;
  }
  return M;
}

Matrix *generate_hellinger_matrix (InfCentroid *C) {
  int k,i,j,l;
  double d;
  Matrix *M;
  
  k = C->k;
  M = allocate_dmatrix(k,k);
  l = vec_len;
  for (i=1;i<k;i++) {
    for (j=1;j<i;j++) {
      d = hellinger_distance(C->el[i]->el,C->el[j]->el,l);
      M->el[i]->el[j] = d;
    }
    put_dot;
  }
  return M;
}

void write_nearness_matrix (FILE *f, Matrix *M) {
  int k,i,j,b,e,e1;
  double d;
  
  k = (M->s);
  fprintf(f,"\nNEARNESS MATRIX:\n---------------\n\n");
  for (b=1;b<k;b+=10) {
    e = b+10;
    if (e > k) e = k;
    for (i=b;i<k;i++) {
      fprintf(f,"%3d: ",i);
      if (i < e) e1 = i;
      else e1 = e;
      for (j=b;j<e1;j++) {
	d = M->el[i]->el[j];
	if (use_hellinger) {
	  fprintf(f,"%1.4f ",d);
	} else {
	  if (d < 99.995) fputc(' ',f);
	  if (d < 9.995) fputc(' ',f);
	  fprintf(f,"%1.2f ",d);
	}
      }
      fprintf(f,"\n");
    }
    fprintf(f,"k     ");
    for (i=b;i<e;i++) {
      fprintf(f,"%4d   ",i);
    }
    fprintf(f,"\n\n");
  }
}

void generate_report (FILE *f, FILE *r, char *misfile, char *hdrfile) {
  int k,i,l;
  FList *fl;
  CList *cl;
  InfCentroid *C;
  int total,spec,classes;
  Partition *P;
  ST *V = NULL;
  FILE *m;
  double sc,scj,cd,cd2,dist,mse;
  double pf=1.0;
  double *cds;
  Matrix *M = NULL;
  Vector *W;

  read_header(hdrfile);
  if (verbose) fprintf(stdout,"\nFrequency report generation\n");
  fl = NULL;
  cl = NULL;
  
  l = vec_len;
  
  classes = 0;
  total = 0;
  spec = 0;
     
  P = read_partition(f,TRUE);
  k = P->k;
  fl = collect_freqs(P,fl,&spec,&total);
  cl = collect_freqs_by_class(P,cl,&classes);
  if (log2_factorials == NULL) log2_factorials = prepare_log2_factorials(total+classes);
  k = classes+1;
  m = fopen(misfile,"r");
  if (m != NULL) fclose(m);
  C = allocate_centroids(k,l);
  if ((cds = (double *) malloc(sizeof(double)*k)) == NULL) out_of_mem();
  for (i=1;i<vec_len;i++) C->el[0]->el[i] = 0.5;
  if (TRASH) cds[0] = class_code_length(P,C,0,total);
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,total);
  }
  calculate_logs(C);
  for (i=1;i<k;i++) {
    cds[i] = class_code_length(P,C,i,total);
  }
  if (verbose) fprintf(stdout,"\nCalculating stochastic complexity\n");
  sc = stochastic_complexity_u(P,k,l);
  scj = stochastic_complexity_j(P,k,l);
  if (PRINT_PREDFIT) pf = predictive_fit(P);
  cd = average_codelength(P,C,TRUE);
  cd2 = shannon_entropy(P,C,TRUE);
  mse = overall_MSE(P,C);
  dist = overall_distortion(P,C);
  
  if (verbose) fprintf(stdout,"\nWriting report\n");
  start_text(r);
  fprintf(r,"\nREPORT\n======\n\n");
  fprintf(r,"CLASSES:    %d\n",classes);
  fprintf(r,"SC:         %2.4f (%2.4f)\n",sc,scj);
  if (PRINT_PREDFIT) fprintf(r,"DELTA:      %d\n",delta_value(classes));
  if (PRINT_PREDFIT) fprintf(r,"PF:         %2.4f\n",pf);
  fprintf(r,"CODELENGTH: %2.4f\n",cd);
  fprintf(r,"            %2.4f\n",cd2);
  fprintf(r,"MSE:        %2.4f (%2.4f)\n",mse,mse / (double) vec_len);
  fprintf(r,"DISTORTION: %2.4f (%2.4f)\n",dist,dist / (double) vec_len);
  fprintf(r,"--------------\n\n\n");
  fprintf(r,"CONTENTS:\n======\n\n");
  if (classes == 1) {
    fprintf(stdout,"NOTE: One class only\n  Nearness, neighborhood, matching and totalfrequency information is irrelevant\n  Omitting\n");
    report_params = (report_params & ~(RP_NEIGHBOR | RP_TOTALFREQ | RP_NEARNESS | RP_MATCH | RP_MATRIX));
  }
  if ((report_params & RP_NEARNESS) == RP_NEARNESS) fprintf(r,"  1. Nearness matrix\n");
  fprintf(r,"  2. List of classes\n");
  if ((report_params & RP_NEIGHBOR) == RP_NEIGHBOR) fprintf(r,"     - Neighborhood\n");
  if ((report_params & RP_FREQ) == RP_NEIGHBOR) fprintf(r,"     - Frequencies\n");
  if ((report_params & RP_PARTITION) == RP_PARTITION) fprintf(r,"     - Partitions\n");
  if ((report_params & RP_TOTALFREQ) == RP_TOTALFREQ) fprintf(r,"  3. List of total frequencies\n");
  if ((report_params & RP_MATCH) == RP_MATCH) fprintf(r,"  4. List of matches\n");
  if ((report_params & RP_MATRIX) == RP_MATRIX) fprintf(r,"  5. Probability matrix\n");
  fprintf(r,"--------------\n");
  fputc(12,r);
  if (((report_params & RP_NEARNESS) == RP_NEARNESS) || ((report_params & RP_NEIGHBOR) == RP_NEIGHBOR)) {
    if (verbose) fprintf(stdout,"Generating nearness matrix ");
    if (use_hellinger) {
      for (i=1;i<k;i++) inf_average12(P->el[i],C->el[i]);
      M = generate_hellinger_matrix(C);
    }
    else M = generate_nearness_matrix(P);
    if (verbose) fprintf(stdout," ok\n");
  }
  if ((report_params & RP_NEARNESS) == RP_NEARNESS) {
    if (verbose) fprintf(stdout,"Writing nearness matrix\n");
    write_nearness_matrix(r,M);
    fprintf(r,"--------------\n");
    fputc(12,r);
  }
  write_freqs_by_class(r,P,C,cl,fl,cds,classes+1,M);
  if (((report_params & RP_NEARNESS) == RP_NEARNESS) || ((report_params & RP_NEIGHBOR) == RP_NEIGHBOR)) deallocate_dmatrix(M);
  if ((report_params & RP_TOTALFREQ) == RP_TOTALFREQ) {
    write_freqs(r,fl);
    fprintf(r,"--------------\n");
    fprintf(r,"TOTAL:      %d\n",total);
    fprintf(r,"SPECIES:    %d\n",spec);
    fputc(12,r);
  }
  if (((report_params & RP_MATCH) == RP_MATCH) || ((report_params & RP_MATRIX) == RP_MATRIX)) V = copy_to_set_in_alpha(P);
  if ((report_params & RP_MATCH) == RP_MATCH) {
    exact_matches = FALSE;
    store_partition = FALSE;
    if (verbose) fprintf(stdout,"Generating matches .. sorting .");
    if (verbose) fprintf(stdout,". identifying .");
    identify_vectors_by_classification(r,V,P,C);
    fprintf(r,"--------------\n");
    if (verbose) fprintf(stdout,". ok\n");
    fputc(12,r);
  }
  if ((report_params & RP_MATRIX) == RP_MATRIX) {
    if (verbose) fprintf(stdout,"Generating probability matrix ..");
#ifdef EM_PROB_MATRIX
    W = allocate_dvector(k);
    for (i=1;i<k;i++) W->el[i] = ((double)size(P->el[i]))/((double)total);
    if (verbose) fprintf(stdout,".. calculating ");
    M = allocate_dmatrix(total+1,k);
    calculate_matrix(M,C,V,W,k,vec_len,total+1);
    if (verbose) fprintf(stdout," writing ..");
    dump_mixture_param_P(r,M,V,k,total+1);
    if (verbose) fprintf(stdout,".. ok\n");
    deallocate_dmatrix(M);
    deallocate_dvector(W);
#else
    W = allocate_dvector(k);
    M = allocate_dmatrix(total+1,k);
    for (i=1;i<k;i++) {
      W->el[i] = ((double)(size(P->el[i])+1))/((double)(total+k-1));
      inf_average_12(P->el[i],C->el[i],total);
    }
    if (verbose) fprintf(stdout,".. calculating ");
    calculate_matrix2(M,C,V,W,k,vec_len,total+1);
    if (verbose) fprintf(stdout," writing ..");
    dump_matrix_P(r,M,V,P,k,total+1);
    if (verbose) fprintf(stdout,".. ok\n");
    deallocate_dmatrix(M);
    deallocate_dvector(W);
#endif
  }
  fclose(r);
  deallocate_centroids(C);
  free(cds);
}
