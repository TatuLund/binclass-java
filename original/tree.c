/*
Form a tree from partition with nearest neighbor rule
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>

#include "const.h"
#include "vars.h"
#include "bottom.h"
#include "binset.h"
#include "vectors.h"
#include "centroid.h"
#include "report.h"
#include "distmin.h"
#include "format.h"
#include "adding.h"
#include "binstuff.h"

typedef struct {
  char *name;
  int count;
  void *next;
} NameList;

#define digitize(x,n) ((x > (n / 2)) ? 1 : 0)
#define inf_loss(V1,V2) ((inf_content(V1) + inf_content(V2)) - inf_content_joint(V1,V2))
#define link_freqs(F,i,j) F[i]->linked = TRUE; F[i]->linkage = F[j]

void make_tree (char *parfile, char *trefile1, char *trefil2, char *hdrfile);
void make_joint (char *parfile1, char *parfile2, char *hdrfile);

void inf_average12 (ST *V, Centroid *x);

TreeNode *make_tree_pnn (FILE *f, InfCentroid *C, Partition *P, Vector *SC);
TreeNode *make_tree_pnn2 (FILE *f, InfCentroid *C, Partition *P, Vector *SC);
double hellinger_distance (double *x, double *y, int l);

double special_complexity (Frequencies **F, int k, int d);
double special_complexity_u (Frequencies **F, int k, int d);
double special_complexity_j (Frequencies **F, int k, int d);

void inf_average12 (ST *V, Centroid *x) {
  /* Take the average of V and round it (Bayes Posterior Predictive version) */
  /* ie. Generate new centroids for next round of the GLA */
  
  const char *func = "inf_average12";
  ST *W;
  IntVector *U;
  int *el;
  int n,l,i;
  
  if (V == NULL) internal_error((char *)func);
  if (x == NULL) internal_error((char *)func);
  
  l = V->el->length;
  U = allocate_ivector(l);
  el = U->el;
  n = 0; /* amount of vectors sofar */
  W = V; /* pointer to current element in the set */
  while (!no_elements(W)) {
    if (W->el == NULL) internal_error((char *)func);
    for (i=1;i<l;i++) el[i] += W->el->el[i];
    W = next_element(W);
    n++;
  }
  x->l = l;
  /* Additions of 1 and 2 are results of Bayes rule */
  for (i=1;i<l;i++) {
    x->el[i] = (double) ((double) (el[i]+1) / (double) (n+2));
  }
  deallocate_ivector(U);
}

double hellinger_distance (double *x, double *y, int l) {
  /* This is distance function for probabilities */
  double d,h;
  int i;
  
  d = 1.0;
  for (i=1;i<l;i++) {
    h = (sqrt(1.0-x[i])*sqrt(1.0-y[i])) + (sqrt(x[i])*sqrt(y[i]));
    d*=h;
  }
  return (1.0-d);
}


TreeNode *alloc_node(void) {
  TreeNode *node;
  if ((node = (TreeNode *) malloc(sizeof(TreeNode))) == NULL) out_of_mem();
  if ((node->el = (double *) malloc(sizeof(double)*(vec_len))) == NULL) out_of_mem();
  if ((node->name = (char *) malloc(sizeof(char)*(name_len+1))) == NULL) out_of_mem();
  node->k = 0;
  node->num = 0;
  node->size = 0;
  node->left = NULL;
  node->right = NULL;
  return node;
}

void traverse_tree (FILE *f, int depth, TreeNode *node) {
  int i;
  if (node->left != NULL) traverse_tree(f,depth+1,node->left);
  if (node->num == 0) {
    for (i=1;i<depth;i++) fputc(' ',f);
    if (depth == 1) fprintf(f,"*[ %.4f, %.4f, %d\n",node->dist,node->sc,node->k);
    else fprintf(f," [ %.4f, %.4f, %d\n",node->dist,node->sc,node->k);
  } else {
    for (i=1;i<depth;i++) fputc(' ',f);
    fprintf(f," %d %s\n",node->num,node->name);
  }
  if (node->right != NULL)  traverse_tree(f,depth+1,node->right);
}

void tree_file (FILE *f, int left, int k, int d, TreeNode *node) {
  if (left) fprintf(f,"(");
  if (node->left != NULL) tree_file(f,TRUE,((node->k)-d),(node->k),node->left);
  if (node->num == 0) fprintf(f,",");
  if (node->num != 0) fprintf(f,"%d [%d] %s:%.1f",node->num,node->size,node->name,(double)((node->k)-d));
  if (node->right != NULL) tree_file(f,FALSE,((node->k)-d),(node->k),node->right);
  if (!left) fprintf(f,"):%.1f",(double)k);
}

ST *copy_to_class (ST *V1, ST *V2) {
  ST *tmp;
  
  tmp = V2;
  while (elements_left(tmp)) {
    V1 = add_element(V1,get_element(tmp));
    tmp = next_element(tmp);
  }
  return V1;
}

ST *join_copy_class (ST *V1, ST *V2) {
  ST *tmp;
  
  tmp = NULL;
  tmp = copy_to_class(tmp,V1);
  tmp = copy_to_class(tmp,V2);
  return tmp;
}

void link_classes (ST *V1, ST *V2) {
  ST *tmp;

  tmp = V1->last;
  tmp->next = V2;
}

void unlink_classes (ST *V) {
  ST *tmp;

  tmp = V->last;
  tmp->next = NULL;
}

void find_name2 (ST *V, char *s) {
  ST *t;
  BV *x;
  int i,l;

  l = name_len;

  t = V;
  x = t->el;
  for(i=0;i<l;i++) s[i] = x->clasname[i];
  s[name_len] = 0;

}

void copy_name (char *s1, char *s2) {
/*
Copy s1 to s2
*/
  int l,i;

  l = name_len;

  for(i=0;i<l;i++) {
    s1[i] = s2[i];
  }
  s1[name_len] = 0;
}

NameList *alloc_namenode (char *s) {
/*
Allocate memory for new namenode, with given name s and value 1
*/
  NameList *node;

  if ((node = (NameList *) malloc(sizeof(NameList))) == NULL) out_of_mem();
  if ((node->name = (char *) malloc(sizeof(char)*(name_len+1))) == NULL) out_of_mem();
  copy_name(node->name,s);
  node->count = 1;
  node->next = NULL;
  return node;
}

void dealloc_namenode (NameList *node) {
  free(node->name);
  free(node);
}

NameList *increase_count (NameList *L, char *s) {
/*
If matching name is found in the list, increase its value, otherwise add new
name at the begining of the list with value 1
*/
  int increased=FALSE;
  NameList *tmp;

  tmp = L;
  while ((tmp != NULL) && (!increased)) {
    if (strcmp(tmp->name,s) == 0) {
      increased = TRUE;
      tmp->count=tmp->count+1;
    }
    tmp = tmp->next;
  }
  if (!increased) {
    tmp = alloc_namenode(s);
    tmp->next = L;
    L = tmp;
  }
  return L;
} 

void find_name (ST *V, char *s) {
  ST *t;
  BV *x;
  int max;
  NameList *L=NULL;
  NameList *tmp;
  NameList *tmp2;

  /* scan vectors of the set */
  t = V;
  while (elements_left(t)) {
    x = get_element(t);
    L = increase_count(L,x->clasname);
    t = next_element(t);
  }

  /* find whose count was biggest, deallocate nodes */
  tmp = L;
  max = 0;
  while (tmp != NULL) {
    if (tmp->count > max) {
      max = tmp->count;
      copy_name(s,tmp->name);
    }
    tmp2 = tmp;
    tmp = tmp->next;
    dealloc_namenode(tmp2);
  }

}


TreeNode *make_tree_pnn (FILE *f, InfCentroid *C, Partition *P, Vector *SC) {
  int k,l,i,j;
  int imin = 1;
  int jmin = 1;
  TreeNode **t;
  TreeNode *node;
  double dmin,d;
  
  k = C->k;
  l = vec_len;

  if ((t = malloc(sizeof(void *)*k)) == NULL) out_of_mem();
  for (i=1;i<k;i++) {
    t[i] = alloc_node();
    t[i]->size = size(P->el[i]);
    find_name(P->el[i],t[i]->name);
    t[i]->dist = 0.0;
    t[i]->k = k-1;
    for (j=1;j<l;j++) {
      t[i]->el[j] = C->el[i]->el[j];
    }
    t[i]->num = i;
  }
  if (verbose) fprintf(stdout,"Forming a tree: ");
  while (k > 2) {
    dmin = (double)l+1.0;
    for (i=1;i<k;i++) {
      for (j=(i+1);j<k;j++) {
	d = hellinger_distance(t[i]->el,t[j]->el,l);
	if (d < dmin) {
	  dmin = d;
	  jmin = j;
	  imin = i;
	}
      }
    }
    node = alloc_node();
    node->k = k-2;
    node->dist = dmin;
    node->size = t[imin]->size + t[jmin]->size;
    for (i=1;i<l;i++) {
      node->el[i] = ((((t[imin]->el[i] * ((double)t[imin]->size + 2.0)) - 1.0) + ((t[jmin]->el[i] * ((double)t[jmin]->size + 2.0)) - 1.0)) + 1.0) / (((double)t[imin]->size + (double)t[jmin]->size) + 2.0);
    }
    node->right = t[imin];
    node->left = t[jmin];
    
    P->el[imin] = join_class(P->el[imin],P->el[jmin]);
    P->el[jmin] = P->el[k-1];
    P->el[k-1] = NULL;
    
    t[imin] = node;
    t[jmin] = t[k-1];
    t[k-1] = NULL;
    k--;
    SC->el[k-1] = stochastic_complexity(P,k,l);
    node->sc = SC->el[k-1];
    put_dot;
  }
  node = t[1];
  if (verbose) fprintf(stdout," ok\nWriting tree ..");
  /* Write more readable tree */
  fprintf(f,"\n\nTREE: (hellinger distance)\n----\n\n");
  traverse_tree(f,1,node);
  if (verbose) fprintf(stdout,".. ok\n");
  return node;
}

TreeNode *make_tree_pnn2 (FILE *f, InfCentroid *C, Partition *P, Vector *SC) {
  int k,l,i,j;
  int imin = 1;
  int jmin = 1;
  TreeNode **t;
  TreeNode *node;
  double dmin,d;
  
  k = C->k;
  l = vec_len;
  
  if ((t = malloc(sizeof(void *)*k)) == NULL) out_of_mem();
  for (i=1;i<k;i++) {
    t[i] = alloc_node();
    t[i]->size = size(P->el[i]);
    find_name(P->el[i],t[i]->name);
    t[i]->dist = 0.0;
    t[i]->k = k-1;
    for (j=1;j<l;j++) {
      t[i]->el[j] = C->el[i]->el[j];
    }
    t[i]->num = i;
  }
  if (verbose) fprintf(stdout,"Forming a tree: ");
  while (k > 2) {
    dmin = (double)l+1.0;
    for (i=1;i<k;i++) {
      for (j=i+1;j<k;j++) {
	d = class_nearness(P->el[i],P->el[j]);
	if (d < dmin) {
	  dmin = d;
	  jmin = j;
	  imin = i;
	}
      }
    }
    node = alloc_node();
    node->k = k-2;
    node->dist = dmin;
    node->size = t[imin]->size + t[jmin]->size;
    for (i=1;i<l;i++) {
      node->el[i] = ((((double)t[imin]->size * t[imin]->el[i]) + (double)(t[jmin]->size * t[jmin]->el[i])) / (double)(node->size));
    }
    node->right = t[imin];
    node->left = t[jmin];
    
    P->el[imin] = join_class(P->el[imin],P->el[jmin]);
    P->el[jmin] = P->el[k-1];
    P->el[k-1] = NULL;

    t[imin] = node;
    t[jmin] = t[k-1];
    t[k-1] = NULL;
    k--;
    SC->el[k-1] = stochastic_complexity(P,k,l);
    node->sc = SC->el[k-1];
    put_dot;
  }
  node = t[1];
  if (verbose) fprintf(stdout," ok\nWriting tree ..");
  /* Write more readable tree */
  fprintf(f,"\n\nTREE: (custom distance)\n----\n\n");
  traverse_tree(f,1,node);
  if (verbose) fprintf(stdout,".. ok\n");
  return node;
}

Partition *construct_partition (Partition *P, int k1, int k2) {
  int k,i;
  Partition *NP;
  k = P->k;
  NP = allocate_partition(k-1);
  link_classes(P->el[k1],P->el[k2]);
  for (i=1;i<(k-1);i++) {
    if (i<k2) NP->el[i] = P->el[i];
    else NP->el[i] = P->el[i+1];
  }
  return NP;
}

void unlink_freqs (Frequencies **F, int i) {
#ifdef _MY_DEBUG
  const char *func = "unlink_freqs";
  if (!F[i]->linked) internal_error((char *)func);
#endif  
  F[i]->linked = FALSE;
  F[i]->linkage = NULL;
}

void join_freqs (Frequencies **F, int l, int k1, int k2) {
  int *x1;
  int *x2;
  IntVector *tmp;
  int i;
  
  tmp = F[k1]->freq;
  x1 = tmp->el;
  tmp = F[k2]->freq;
  x2 = tmp->el;
  F[k1]->size = F[k1]->size + F[k2]->size;
  for (i=1;i<l;i++) x1[i] = x1[i] + x2[i];
}

Frequencies **construct_freqs (Frequencies **F, int k, int k1, int k2) {
  int i;
  Frequencies **NF;
  
  if ((NF = (Frequencies**) malloc(sizeof(void *)*(k-1))) == NULL) out_of_mem();
  link_freqs(F,k1,k2);
  for (i=1;i<(k-1);i++) NF[i] = (i<k2) ? F[i] : F[i+1];
  return NF;
}

double special_complexity (Frequencies **F, int k, int l) {
  return (use_jeffreys_prior) ? special_complexity_j(F,k,l) : special_complexity_u(F,k,l);
}

double special_complexity_j (Frequencies **F, int k, int d) {
  /* calculate the stochastic complexity (Jeffrey's prior) */
  const char *func = "special_complexity_j";
  int i,j,tj,tij,t;
  double h1,h2;
  Frequencies *tmp;
  IntVector *x;
  int *tij1;
  int *tij2;
  int tj1,tj2,K,D;

  if (F == NULL) internal_error((char *)func);
  if (log2_factorials == NULL) internal_error((char *)func);

  K = k-1;
  D = d-1;

  /* calculate total number of vectors */
  t = 0;
  for (j=1;j<k;j++) {
    if (F[j] == NULL) internal_error((char *)func);
    tj = F[j]->size;
    if (F[j]->linked) {
      tmp = F[j]->linkage;
      tj += tmp->size;
    }
    t += tj;
  }

  /* the part for coding the class no */
  h1 = ((D * K) + (K/2.0)) * LPI;
  h1 += log2_gamma(K/2.0);
  h1 += log2_gamma((double)t + (K/2.0));
  for (j=1;j<k;j++) {
    tj = F[j]->size;
    if (F[j]->linked) {
      tmp = F[j]->linkage;
      tj += tmp->size;
    }
    h1 -= log2_gamma((double)tj + 0.5);
  }

  /* the part for coding the bits */
  h2 = 0.0;
  for (j=1;j<k;j++) {
    x = F[j]->freq;
    if (x == NULL) internal_error((char *)func);
    tij1 = x->el;
    tj1 = F[j]->size;
    if (F[j]->linked) {
      tmp = F[j]->linkage;
      x = tmp->freq;
      tij2 = x->el;
      tj2 = tmp->size;
      for (i=1;i<d;i++) {
	tj = tj1+tj2;
	tij = tij1[i]+tij2[i];
	h2 += log2_factorial(tj);
	h2 -= log2_gamma((double)tij+0.5);
	h2 -= log2_gamma((double)(tj - tij) + 0.5);
      }
    } else {
      for (i=1;i<d;i++) {
	h2 += log2_factorial(tj1);
	h2 -= log2_gamma((double)(tij1[i])+0.5);
	h2 -= log2_gamma((double)(tj1 - tij1[i]) + 0.5);
      }
    }
  }
  return ((h1+h2)/(double)t);
}

double special_complexity_u (Frequencies **F, int k, int d) {
  /* calculate the stochastic complexity (uniform prior) */
  const char *func = "special_complexity_u";
  int i,j,t,tj,tij;
  double h1,h2;
  Frequencies *tmp;
  IntVector *x;
  int *tij1;
  int *tij2;
  int tj1,tj2,K;
  
  if (F == NULL) internal_error((char *)func);
  if (log2_factorials == NULL) internal_error((char *)func);

  K = k-1;

  /* calculate total number of vectors */
  t = 0;
  for (j=1;j<k;j++) {
    if (F[j] == NULL) internal_error((char *)func);
    tj = F[j]->size;
    if (F[j]->linked) {
      tmp = F[j]->linkage;
      tj += tmp->size;
    }
    t += tj;
  }
  
  /* the part for coding the class no */
  h1 = log2_factorial(t);
  for (j=1;j<k;j++) {
    tj = F[j]->size;
    if (F[j]->linked) {
      tmp = F[j]->linkage;
      tj += tmp->size;
    }
    h1 -= log2_factorial(tj);
  }
  h1 += log2_factorial(t+K-1);
  h1 -= log2_factorial(t);
  h1 -= log2_factorial(K-1);
  
  /* the part for coding the bits */
  h2 = 0.0;
  for (j=1;j<k;j++) {
    x = F[j]->freq;
    if (x == NULL) internal_error((char *)func);
    tij1 = x->el;
    tj1 = F[j]->size;
    if (F[j]->linked) {
      tmp = F[j]->linkage;
      x = tmp->freq;
      tij2 = x->el;
      tj2 = tmp->size;
      for (i=1;i<d;i++) {
	tj = tj1 + tj2;
	tij = tij1[i]+tij2[i];
	h2 += log2_factorial(tj+1);
	h2 -= log2_factorial(tij);
	h2 -= log2_factorial(tj-tij);
      }
    } else {
      for (i=1;i<d;i++) {
	h2 += log2_factorial(tj1+1);
	h2 -= log2_factorial(tij1[i]);
	h2 -= log2_factorial(tj1-tij1[i]);
      }
    }
  }
  return ((h1+h2)/(double)t);
}

void reduce_sc (Frequencies **F, Vector *SC, int k, int *imin, int *jmin) {
  Frequencies **NF;
  int i,j,l,m;
  double scmin,sc;
  
  l = vec_len;
  scmin = l * 2.0;
  for (i=1;i<k;i++) {
    for (j=(i+1);j<k;j++) {
      NF = construct_freqs(F,k,i,j);
      sc = special_complexity(NF,k-1,l);
      if (sc < scmin) {
	scmin = sc;
	*imin = i;
	*jmin = j;
      }
      unlink_freqs(F,i);
      for (m=1;m<(k-1);m++) NF[m] = NULL;
      free(NF);
    }
  }
  SC->el[k-2] = scmin;
}

TreeNode *sc_min_tree (FILE *f, InfCentroid *C, Frequencies **F, Partition *P, Vector *SC) {
  int k,l,i,j,imin,jmin;
  TreeNode **t;
  TreeNode *node;
  
  k = C->k;
  l = vec_len;
  
  if ((t = malloc(sizeof(void *)*k)) == NULL) out_of_mem();
  for (i=1;i<k;i++) {
    t[i] = alloc_node();
    t[i]->size = F[i]->size;
    find_name(P->el[i],t[i]->name);
    t[i]->dist = 0.0;
    t[i]->k = k-1;
    for (j=1;j<l;j++) {
      t[i]->el[j] = C->el[i]->el[j];
    }
    t[i]->num = i;
  }
  if (verbose) {
    fprintf(stdout,"Forming a tree: ");
    fflush(stdout);
  }
  while (k > 2) {
    reduce_sc(F,SC,k,&imin,&jmin);
    node = alloc_node();
    node->k = k-2;
    node->dist = 0.0;
    node->size = t[imin]->size + t[jmin]->size;
    for (i=1;i<l;i++) {
      node->el[i] = ((((double)t[imin]->size * t[imin]->el[i]) + (double)(t[jmin]->size * t[jmin]->el[i])) / (double)(node->size));
    }
    node->right = t[imin];
    node->left = t[jmin];
    
    join_freqs(F,l,imin,jmin);
    deallocate_ivector(F[jmin]->freq);
    free(F[jmin]);
    F[jmin] = F[k-1];
    F[k-1] = NULL;
    
    t[imin] = node;
    t[jmin] = t[k-1];
    t[k-1] = NULL;
    k--;
    node->sc = SC->el[k-1];
    put_dot;
  }
  node = t[1];
  if (verbose) fprintf(stdout," ok\nWriting tree ..");
  /* Write more readable tree */
  fprintf(f,"\n\nTREE: (sc minimizer)\n----\n\n");
  traverse_tree(f,1,node);
  if (verbose) fprintf(stdout,".. ok\n");
  return node;
}

int inf_content2 (ST *V1, ST *V2) {
  ST *t;
  BV *x;
  IntVector *hmo;
  int i,n,l;

  l = vec_len;
  hmo = allocate_ivector(l);

  /* calculate hmo */
  /* count one bits */
  t = V1;
  n = 0;
  while (elements_left(t)) {
    n++;
    x = get_element(t);
    for(i=1;i<l;i++) hmo->el[i] += x->el[i];
    t = next_element(t);
  }
  t = V2;
  while (elements_left(t)) {
    n++;
    x = get_element(t);
    for(i=1;i<l;i++) hmo->el[i] += x->el[i];
    t = next_element(t);
  }
  /* digitize to one/zero */
  for(i=1;i<l;i++) hmo->el[i] = digitize(hmo->el[i],n);

  /* calculate number of differing bits */
  t = V1;
  n = 0;
  while (elements_left(t)) {
    x = get_element(t);
    for(i=1;i<l;i++) n += (hmo->el[i] != x->el[i]);
    t = next_element(t);
  }
  t = V2;
  while (t != NULL) {
    x = get_element(t);
    for(i=1;i<l;i++) n += (hmo->el[i] != x->el[i]);
    t = next_element(t);
  }

  return n;
}


int inf_content (ST *V) {
  ST *t;
  int *x;
  IntVector *f;
  int *el;
  int i,n,l,c,fi;

  l = vec_len-1;
  f = allocate_ivector(l);
  el = f->el;

  /* calculate hmo */
  /* count one bits */
  t = V;
  n = 0;
  while (elements_left(t)) {
    n++;
    x = get_vector(t);
    for(i=1;i<l;i+=2) {
      el[i] += x[i];
      el[i+1] += x[i+1];
    }
    if (l % 2) el[l] += x[l];
    t = next_element(t);
  }
  /* el[i] is now t_ij */
  c = 0;
  for (i=1;i<l;i+=2) {
    fi = el[i];
    c += (fi > (n - fi)) ? fi : (n-fi);
    fi = el[i+1];
    c += (fi > (n - fi)) ? fi : (n-fi);
  }
  if (l % 2) {
    fi = el[l];
    c += (fi > (n - fi)) ? fi : (n-fi);
  }
  return c;
}

int inf_content_joint (ST *V1, ST *V2) {
  ST *t;
  int *x;
  int *el;
  IntVector *f;
  int i,n,l,c,fi;

  l = vec_len-1;
  f = allocate_ivector(l);
  el = f->el;

  /* calculate hmo */
  /* count one bits */
  t = V1;
  n = 0;
  while (elements_left(t)) {
    n++;
    x = get_vector(t);
    for(i=1;i<l;i+=2) {
      el[i] += x[i];
      el[i+1] += x[i+1];
    }
    if (l % 2) el[l] += x[l];
    t = next_element(t);
  }
  t = V2;
  while (elements_left(t)) {
    n++;
    x = get_vector(t);
    for(i=1;i<l;i+=2) {
      el[i] += x[i];
      el[i+1] += x[i+1];
    }
    if (l % 2) el[l] += x[l];
    t = next_element(t);
  }
  c = 0;
  for (i=1;i<l;i+=2) {
    fi = f->el[i];
    c += (fi > (n - fi)) ? fi : (n-fi);
    fi = f->el[i+1];
    c += (fi > (n - fi)) ? fi : (n-fi);
  }
  if (l % 2) {
    fi = f->el[l];
    c += (fi > (n - fi)) ? fi : (n-fi); 
  }

  return c;
}

TreeNode *parsimony_tree (FILE *f, InfCentroid *C, Partition *P, Vector *SC) {
  int k,l,i,j;
  int imin = 1;
  int jmin = 1;
  TreeNode **t;
  TreeNode *node;
  int ic = 0;
  int icmin,max;
  
  k = C->k;
  l = vec_len;
  
  max = 0;
  if ((t = malloc(sizeof(void *)*k)) == NULL) out_of_mem();
  for (i=1;i<k;i++) {
    t[i] = alloc_node();
    t[i]->size = size(P->el[i]);
    find_name(P->el[i],t[i]->name);
    max += t[i]->size;
    t[i]->dist = 0.0;
    t[i]->k = k-1;
    for (j=1;j<l;j++) {
      t[i]->el[j] = C->el[i]->el[j];
    }
    t[i]->num = i;
  }
  max = max * l;
  if (verbose) fprintf(stdout,"Forming a tree: ");
  while (k > 2) {
    icmin = max;
    for (i=1;i<k;i++) {
      for (j=i+1;j<k;j++) {
	ic = inf_loss(P->el[i],P->el[j]);
	if (ic < icmin) {
	  icmin = ic;
	  jmin = j;
	  imin = i;
	}
      }
    }
    node = alloc_node();
    node->k = k-2;
    node->dist = (double) ic / (double) (t[imin]->size + t[jmin]->size);
    node->size = t[imin]->size + t[jmin]->size;
    for (i=1;i<l;i++) {
      node->el[i] = ((((double)t[imin]->size * t[imin]->el[i]) + (double)(t[jmin]->size * t[jmin]->el[i])) / (double)(node->size));
    }
    node->right = t[imin];
    node->left = t[jmin];
    
    P->el[imin] = join_class(P->el[imin],P->el[jmin]);
    P->el[jmin] = P->el[k-1];
    P->el[k-1] = NULL;

    t[imin] = node;
    t[jmin] = t[k-1];
    t[k-1] = NULL;
    k--;
    SC->el[k-1] = stochastic_complexity(P,k,l);
    node->sc = SC->el[k-1];
    put_dot;
  }
  node = t[1];
  if (verbose) fprintf(stdout," ok\nWriting tree ..");
  /* Write more readable tree */
  fprintf(f,"\n\nTREE: (parsimony)\n----\n\n");
  traverse_tree(f,1,node);
  if (verbose) fprintf(stdout,".. ok\n");
  return node;
}

void sc_join (InfCentroid *C, Frequencies **F, Partition *P, Vector *SC) {
  int k,l,imin,jmin;
  
  k = C->k;
  l = vec_len;
  
  if (verbose) {
    fprintf(stdout,"Joining classes to %d: ",join_target);
    fflush(stdout);
  }

  while (k > (join_target+1)) {
    reduce_sc(F,SC,k,&imin,&jmin);
    
    P->el[imin] = join_class(P->el[imin],P->el[jmin]);
    P->el[jmin] = P->el[k-1];
    P->el[k-1] = NULL;
    P->k = k-1;
    
    join_freqs(F,l,imin,jmin);
    deallocate_ivector(F[jmin]->freq);
    free(F[jmin]);
    F[jmin] = F[k-1];
    F[k-1] = NULL;
    
    k--;
    put_dot;
  }
  if (verbose) fprintf(stdout," ok\n");
}

void make_tree (char *parfile, char *trefile1, char *trefile2, char *hdrfile) {
  const char *func = "make_tree";
  Vector *SC;
  InfCentroid *C;
  Partition *P;
  Frequencies **F = NULL;
  Frequencies *tmp;
  int k,i,l,total,n;
  IntVector *x;
  double sc;
  FILE *f;
  TreeNode *T;
  FILE *tf;
  
  read_header(hdrfile);
  l = vec_len;
  
  if ((f = fopen(parfile,"r")) == NULL) file_error(parfile,(char *)func);
  P = read_partition(f,FALSE);
  fclose(f);
  k = P->k;
  
  total = 0;
  for (i=1;i<k;i++) total+=size(P->el[i]);
  C = allocate_centroids(k,l);
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,total);
  }
  calculate_logs(C);
  if (log2_factorials == NULL) log2_factorials = prepare_log2_factorials(total+k);
  sc = stochastic_complexity(P,k,l);
  
  if (use_hellinger) {
    for (i=1;i<k;i++) {
      inf_average12(P->el[i],C->el[i]);
    }
  }
  
  if (!use_hellinger && !use_custom && !use_parsimony) {
    if ((F = (Frequencies **) malloc(sizeof(void *)*k)) == NULL) out_of_mem();
    for (i=1;i<k;i++) {
      x = allocate_ivector(l);
      n = freq(P->el[i],x,l);
      /* deallocate_set(P->el[i]); */
      /* P->el[i] = NULL; */
      if ((tmp = (Frequencies *) malloc(sizeof(Frequencies))) == NULL) out_of_mem();
      tmp->freq = x;
      tmp->size = n;
      tmp->linked = FALSE;
      tmp->linkage = NULL;
      F[i] = tmp;
    }
  }
  
  k = P->k;
  SC = allocate_dvector(k);
  SC->el[k-1] = sc;
  if ((f = fopen(trefile1,"w")) == NULL) file_error(trefile1,(char *)func);
  if (use_hellinger) T = make_tree_pnn(f,C,P,SC);
  else if (use_custom) T = make_tree_pnn2(f,C,P,SC);
  else if (use_parsimony) T = parsimony_tree(f,C,P,SC); 
  else T = sc_min_tree(f,C,F,P,SC);
  
  /* Write data for Phylip Drawtree/gram programs */
  if ((tf = fopen(trefile2,"w")) == NULL) file_error(trefile2,(char *)func);
  tree_file(tf,TRUE,1,1,T);
  fprintf(tf,");\n");
  fclose(tf);
  
  fprintf(f,"\nSC Function:\n");
  for (i=1;i<k;i++) {
    fprintf(f,"%4d: %.4f\n",i,SC->el[i]);
  }
  fprintf(f,"--------------\n");
  fclose(f);
  deallocate_dvector(SC);
  
  deallocate_centroids(C);
  deallocate_partition(P);
}

void make_joint (char *parfile1, char *parfile2, char *hdrfile) {
  const char *func = "make_joint";
  Vector *SC;
  InfCentroid *C;
  Partition *P;
  Frequencies **F;
  Frequencies *tmp;
  int k,i,l,total,n;
  IntVector *x;
  double sc;
  FILE *f;
  
  read_header(hdrfile);
  l = vec_len;
  
  if ((f = fopen(parfile1,"r")) == NULL) file_error(parfile1,(char *)func);
  P = read_partition(f,FALSE);
  fclose(f);
  k = P->k;
  if (k < join_target) stop_error((char *)"Too few classes",(char *)func);
  
  total = 0;
  for (i=1;i<k;i++) total+=size(P->el[i]);
  C = allocate_centroids(k,l);
  for (i=1;i<k;i++) {
    inf_average(P->el[i],C->el[i],rounded_centroids,total);
    total+=size(P->el[i]);
  }
  calculate_logs(C);
  if (log2_factorials == NULL) log2_factorials = prepare_log2_factorials(total+k);
  sc = stochastic_complexity(P,k,l);
  
  if ((F = (Frequencies **) malloc(sizeof(void *)*k)) == NULL) out_of_mem();
  for (i=1;i<k;i++) {
    x = allocate_ivector(l);
    n = freq(P->el[i],x,l);
    if ((tmp = (Frequencies *) malloc(sizeof(Frequencies))) == NULL) out_of_mem();
    tmp->freq = x;
    tmp->size = n;
    tmp->linked = FALSE;
    tmp->linkage = NULL;
    F[i] = tmp;
  }
  
  k = P->k;
  SC = allocate_dvector(k);
  SC->el[k-1] = sc;
  sc_join(C,F,P,SC);
  if (verbose) fprintf(stdout,"Writing partition of %d ..",((P->k)-1));
  if ((f = fopen(parfile2,"w")) == NULL) file_error(parfile2,(char *)func);
  inf_write_partition(f,P);
  fclose(f);
  if (verbose) fprintf(stdout,".. ok\n");
  
  deallocate_dvector(SC);
  
  deallocate_centroids(C);
  deallocate_partition(P);
}

/* end of tree.c */
