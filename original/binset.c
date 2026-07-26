
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bottom.h"
#include "binstuff.h"
#include "vars.h"
#include "math.h"
#include "distmin.h"
#include "format.h"
#include "vectors.h"
#include "adding.h"
#include "centroid.h"


/* prototypes */

/* Set functions, set is presented as singly linked list (see const.h) */

/* basic functions */

/* purpose of these macros is to make some parts of the code more understandable */
#define get_element(V) V->el
/* give pointer to first set element of the set */
#define get_vector(V) V->el-el
/* give pointer to vector in first set element of the set */
#define next_element(V) V->next
/* give pointer to next element of the set */
#define no_elements(V) (V == NULL)
/* return true if no elements left */
#define elements_left(V) (V != NULL)
/* return true if there is elements in the set */

int size (ST *V);
/* return number of items in set */
void deallocate_set (ST *V);
/* deallocate all memory occuppied by set V including vectors */
ST *empty_set (ST *V);
/* deallocate set elements exluding vectors (note: returns allways NULL */
ST *add_element (ST *V, BV *x);
/* add vector x to set as first item */
ST *add_element_in_alpha (ST *V, BV *x);
/* add vector x to set in correct position by name (primary) and id (secondary). */
/* USED BY: report.c, cumulat.c, compare.c */
ST *add_element_in_order (ST *V, BV *x);
/* add vector x to set in correct position by id only. */ 
/* USED BY: cumulat.c, */
ST *add_element_tail (ST *V, BV *x);
/* add vector x to set as last item */
ST *del_element (ST *V);
/* delete first item from the list don't deallocate vector */
BV *get_vector_i (ST *V, int ind);
/* return pointer to the ith (ind) vector of the set */
BV *copy_vector_i (ST *V, int ind);
/* return copy of the ith (ind) vector of the set  */
ST *del_vector_i (ST *V, int ind);
/* delete ith (ind) item from the list don't deallocate vector */
ST *search_id (char *id, ST *V);
/* return pointer to set item containing vector whose id is id, otherwise return NULL */
int is_in_set (BV *x, ST *V);
/* return boolean value if the vector x is in set by its id */

/* set copying */

ST *join_class (ST *V1, ST *V2);
/* move elements from V2 to V1 and return new V1, V2 will be empty after this call */
ST *copy_set (ST *V);
ST *copy_set_fast (ST *V);
/* return deep copy of the set V (vectors copied too) */
/* fast version does not preserve order */
ST *copy_set_in_alpha (ST *V);
/* return deep copy of the set V (vectors copied too) */
/* vectors will be sorted with add_element_in_alpha */
/* essentially this is list insertion sort algorithm (=slowish) */

/* set IO */

ST *read_set (FILE *f, char *hdrfile);
/* read set from the readily opened file f with filedescription */
/* in hdrfile to the set and return pointer to set */
void coin_tosh (FILE *o, ST *V, char *misfile);
/* replace missing bits in set by random bits, save information about missing data */
/* for further use by report.c, this maybe nonfunctional feature and is currently hidden */
void coin_tosh_silent (ST *V);
/* replace missing bits in set by random bits */
void write_set (FILE *f, ST *V);
/* write vectors of set V to readily opened file f with currently active fileformat */
/* parameters */
void emp_write_set (FILE *f, ST *V, int no_print_cl);
/* write vectors of set V to readily opened file f with currently active fileformat */
/* parameters, emphase missing bits. This might be nonfunctional and therefore hidden */
void write_vector (FILE *f, Vector *x);

/* partition - set conversion functions */

ST *partition_to_set (Partition *P);
/* move items in partition P to set and return pointer to set */
ST *copy_to_set_in_alpha (Partition *P);
/* move items in partition P to set and return pointer to set */
/* items will be sorted with add_element_in_alpha */

/* partition functions: partition is set of sets of fixed number of elements, represented */
/* by an array of pointers to set */

Partition *allocate_partition (int k);
/* allocate space for partition of k classes */
void deallocate_partition (Partition *P);
/* deallocate space of partition including sets (see: deallocate_set) */
Partition *copy_partition (Partition *P);
/* generate deep copy of partition */
void sort_partition (char *hdrfile, char *parfile1, char *parfile2);
/* sort sets in partition according descending class sizes */

/* partition IO */

Partition *read_partition (FILE *f, int do_sort);
/* read partition from readily opened file f and return pointer to it */
void inf_write_partition (FILE *f, Partition *P);
/* write partition to readily opened file f, sets appear in descending size order */
void inf_write_partition_po (FILE *f, Partition *P);
/* write partition to readily opened file f, sets appear in order where they are */
void inf_write_partition_po_delta (FILE *f, Partition *P, int delta);
/* write partition to readily opened file f, sets appear in order where they are */
/* value delta appear at first line (used by cumulat.c) */

/* other stuff */

void inf_remove_empty (Partition *P, InfCentroid *C, int s);
/* fix orphaned centroid alias empty cell problem related to GLA */
void remove_empty (Partition *P, InfCentroid *C);
/* fix orphaned centroid alias empty cell problem related to GLA */

/*
NOTES:
- contains some undocumented and possibly unused functions
- order of routines is messy 
*/

/* implementation */

Partition *allocate_partition (int k) {
  Partition *P;
  int i;
  
  if ((P = (Partition *) malloc(sizeof(Partition))) == NULL) out_of_mem();
  if ((P->el = malloc(k*sizeof(void *))) == NULL) out_of_mem();
  P->k = k;
  for (i=0;i<k;i++) P->el[i] = NULL;
  
  return P;
}

/*
void add_class (InfCentroid *P) {
  int k,i;
  ST **t;
  ST **el;

  k = P->k+1;

  el = P->el;
  if ((t = malloc(k*sizeof(void *))) == NULL) out_of_mem();
  for (i=0;i<(k-1);i++) t[i] = el[i];
  t[k-1] = NULL;
  P->el = t;
  P->k = k;
  free(el);
}
*/

void deallocate_partition (Partition *P) {
  int i;
  int k;
  
  if (P != NULL) {
    k = P->k;
    for (i=0;i<k;i++) if (P->el[i] != NULL) deallocate_set(P->el[i]);
    free(P->el);
    free(P);
  }
}

Partition *copy_partition (Partition *P1) {
  Partition *P2;
  int k,i;
  
  k = P1->k;
  P2 = allocate_partition(k);
  for (i=1;i<k;i++) {
    P2->el[i] = copy_set_fast(P1->el[i]);
  }
  return P2;
}

void deallocate_set (ST *V) {
  BV *x;
  
  while (elements_left(V)) {
    x = get_element(V);
    if (x != NULL) bv_deallocate(x);
    V = del_element(V);
  }
}


ST *join_class (ST *V1, ST *V2) {
  BV *x;
  
  while (elements_left(V2)) {
    x = get_element(V2);
    V1 = add_element(V1,x);
    V2 = del_element(V2);
  }
  return V1;
}

ST *empty_set (ST *V) {
  /* empty a set if it is not */
  ST *W;
  
  W = V;
  while (elements_left(W))	{
    V = W;
    W = next_element(W);
    free(V);
  }
  return NULL;
}

ST *add_element (ST *V, BV *x) {
  /* put x in V, as first element */
  ST *W;
  
  if ( (W = (ST *) malloc (sizeof(ST))) == NULL ) out_of_mem();
  W->el = x;
  W->next = V;
  W->last = (V == NULL) ? W : V->last;
  return W;
}

ST *add_element_tail (ST *V, BV *x) {
  /* put x in V, as last element */
  ST *W;
  ST *tmp;
  
  if ( (W = (ST *) malloc (sizeof(ST))) == NULL ) out_of_mem();
  W->el = x;
  W->next = NULL;
  W->last = W;
  if (no_elements(V)) V = W;
  else {
    tmp = V->last;
    tmp->next = W;
    V->last = W;
  }
  return V;
}


BV *get_vector_i (ST *V, int ind) {
  int i;
  
  i = 1;
  while ((elements_left(V)) && (i<ind)) {
    V = next_element(V);
    i++;
  }
  return (V == NULL) ? NULL : get_element(V);
}

ST *del_vector_i (ST *V, int ind) {
  ST *tmp;
  int i;
  
  tmp = V;
  i = 1;
  if (ind == 1) {
    V = del_element(V);
  } else {
    ind--;
    while (i < ind) {
      tmp = next_element(tmp);
      i++;
    }
    tmp->next = del_element(next_element(tmp));
  }
  return V;
}

BV *copy_vector_i (ST *V, int ind) {
  ST *tmp;
  int i;
  
  tmp = V;
  i = 1;
  while ((elements_left(tmp)) && (i<ind)) {
    tmp = next_element(tmp);
    i++;
  }
  return (tmp == NULL) ? NULL : bv_copy(get_element(tmp));
}

ST *copy_set (ST *V) {
  ST *W = NULL;
  ST *tmp;
  
  tmp = V;
  while (elements_left(tmp)) {
    W = add_element_tail(W,bv_copy(get_element(tmp)));
    tmp = next_element(tmp);
  }
  return W;
}

ST *copy_set_fast (ST *V) {
  ST *W = NULL;

  while (elements_left(V)) {
    W = add_element(W,bv_copy(get_element(V)));
    V = next_element(V);
  }
  return W;
}

ST *copy_set_in_alpha (ST *V) {
  ST *W = NULL;
  ST *tmp;
  
  tmp = V;
  while (elements_left(tmp)) {
    W = add_element_in_alpha(W,bv_copy(get_element(tmp)));
    tmp = next_element(tmp);
  }
  return W;
}

ST *add_element_in_alpha (ST *V, BV *x) {
  /* put x in V, as first element */
  /* After this routinen last pointers are not legal */
  ST *W;
  ST *l;
  ST *tmp;
  ST *fl;
  
  if ( (W = (ST *) malloc (sizeof(ST))) == NULL ) out_of_mem();
  W->el = x;
  
  l = V;
  fl = V;
  
  while ((elements_left(l)) && before(W->el->clasname,l->el->clasname)) l = next_element(l);
  while ((elements_left(l)) && (strcmp(W->el->clasname,l->el->clasname) == 0) && before_strain(W->el->strain,l->el->strain)) l = next_element(l);

  if (l != fl) {
    tmp = fl;
    while (next_element(tmp) != l) tmp = next_element(tmp);
    W->next = l;
    tmp->next = W;
  } else {
    W->next = fl;
    fl = W;
  }
  
  return fl;
}

ST *add_element_in_order (ST *V, BV *x) {
  /* put x in V, as first element */
  /* After this routinen last pointers are not legal */
  ST *W;
  ST *l;
  ST *tmp;
  ST *fl;
  
  if ( (W = (ST *) malloc (sizeof(ST))) == NULL ) out_of_mem();
  W->el = x;
  
  l = V;
  fl = V;
  
  while ((elements_left(l)) && (!before_strain(W->el->strain,l->el->strain))) l = next_element(l);
  if (l != fl) {
    tmp = fl;
    while (next_element(tmp) != l) tmp = next_element(tmp);
    W->next = l;
    tmp->next = W;
  } else {
    W->next = fl;
    fl = W;
  }
  
  return fl;
}


ST *del_element (ST *V) {
  /* remove first element from V */
  ST *W;
  
  W = next_element(V);
  free(V);
  return W;
}

int straincmp (char *s1, char *s2) {
  int i,match;

  match = TRUE;
  i = 0;
  while ((i<id_len) && match) {
    match = (s1[i] == s2[i]);
    i++;
  }
  return (match) ? 0 : 1;
}


ST *search_id (char *id, ST *V) {
  ST *tmp;
  ST *tmp2 = NULL;
  int found;
  
  tmp = V;
  found = FALSE;
  while ((elements_left(tmp)) && (!found)) {
    found = (straincmp(id,tmp->el->strain) == 0);
    tmp2 = tmp;
    tmp = next_element(tmp);
  }
  return (!found) ? NULL : tmp2;
}

int is_in_set (BV *x, ST *V) {
  ST *tmp;
  int found;
  int i,match;
  
  tmp = V;
  found = FALSE;
  while ((elements_left(tmp)) && (!found)) {
    match = TRUE;
    i = 0;
    while ((i<id_len) && match) {
/*    while ((i<4) && match) {  */
      match = (tmp->el->strain[i] == x->strain[i]);
      i++;
    }
    found = match;
    tmp = next_element(tmp);
  }
  return found;
}

int veccmp (int *x1, int *x2) {
  int i;
  int match;

  i = 0;
  match = TRUE;
  while ((i < vec_len) && (match)) {
    i++;
    match = (x1[i] == x2[i]);
  }
  return match;
}

int vector_in_set (BV *x, ST *V) {
  ST *tmp;
  BV *y;
  int found,match,i;
  
  tmp = V;
  found = FALSE;
  while ((elements_left(tmp)) && (!found)) {
    y = get_element(tmp);
    i = 0;
    match = TRUE;
    while ((i < vec_len) && (match)) {
      i++;
      match = (x->el[i] == y->el[i]);
    }
    found = match;
    tmp = next_element(tmp);
  }
  return found;
}

void map_missing_bits (ST *V, int l, char *misfile) {
  const char *func = "map_missing_bits";
  const char *es1 = "vector not in set, conflict";
  const char *es2 = "invalid index";
  char *id;
  char *ic;
  char *xs;
  FILE *m;
  ST *v;
  int i,ie,base,ind;
  
  if (V == NULL) internal_error((char *)func);
  if (misfile == NULL) internal_error((char *)func);
  
  if ( (xs = (char *) malloc(sizeof(char)*MAX_LENGTH)) == NULL ) out_of_mem();
  if (verbose) fprintf(stdout,".. mapping missing bits ..");
  if ((id = (char *) malloc(sizeof(char)*8)) == NULL) out_of_mem();
  if ((ic = (char *) malloc(sizeof(char)*3)) == NULL) out_of_mem();
  if ((m = fopen(misfile,"r")) == NULL) file_error(misfile,(char *)func);
  ic[2] = '\0';
  while (!feof(m)) {
    read_line(m,xs,MAX_LENGTH);
    if (!feof(m)) {
      /* Copy ID of the entry */
      strncpy(id,&xs[15],7);
      id[7] = '\0';
      if ((v = search_id(id,V)) == NULL) {
	stop_error((char *)es1,(char *)func);
      }
      ic[0] = xs[25];
      ic[1] = xs[26];
      ie = atoi(ic)+1;
      if (ie > (l-1)) stop_error((char *)es2,(char *)func);
      base = 29;
      if (ie>0) {
	for (i=1;i<ie;i++) {
	  ic[0] = xs[base];
	  ic[1] = xs[base+1];
	  ind = atoi(ic);
	  if (ind > (l-1)) stop_error((char *)es2,(char *)func);
	  v->el->miss[ind] = 1;
	  base = (base+3);
	}
      }
    }
  }
  free(id);
  free(ic);
  fclose(m);
  free(xs);
}

ST *read_set_old (FILE *f, char *misfile, char *hdrfile) {
  /* read set V from input f */
  /* if exists m read info on missing data too */
  /* output goes to o and stdout */
  char *xs;
  BV *x;
  ST *V = NULL;
  FILE *m;
  int l,i,sl,n;
  int read_missing = FALSE;
  
  if ((misfile != NULL) && (!analyse_missing)) {
    if ((m = fopen(misfile,"r")) != NULL) {
      read_missing = TRUE;
      fclose(m);
    } else {
      if (!analyse_missing) {
	if (verbose) fprintf(stdout,".. no info on missing\n");
      }
    }
  }
  
  read_header(hdrfile);
  
  if ( (xs = (char *) malloc(sizeof(char)*MAX_LENGTH)) == NULL ) out_of_mem();
  sl = vec_len;
  l = vec_len;
  n = 0;
  while (!feof(f)) {
    read_line(f,xs,MAX_LENGTH);
    if (!feof(f)) {
      n++;
      /* allocate space for new vector */
      x = bv_allocate();
      x->num = n;
      /* file empty spaces */
      l = strlen(&xs[vec_offs])+1;
      if (l < sl) {
	for (i=l;i<sl;i++) xs[vec_offs+l-1] = ' ';
	xs[vec_offs+sl-1] = 0;
      }
      x->length = sl;
      /* convert string to vector */
      pic_read_bv(x,xs);
      x->dist = 0.0;
      x->hdist = 0;
      /* sort data only if necessary */
      if (is_in_set(x,V)) fprintf(stderr,"\nWARNING: Identifier conflict: %s!",x->strain);
      if (analyse_missing) {
	V = add_element_in_alpha(V,x);
      } else {
	V = add_element(V,x);
      }
    }
  }
  free(xs);
  
  log2_factorials = prepare_log2_factorials((n+n));
  
  if (read_missing && (!analyse_missing)) {
    map_missing_bits(V,l,misfile);
  }
  return V;
}

ST *read_set (FILE *f, char *hdrfile) {
  /* read set V from input f */
  /* output goes to o and stdout */
  char *xs;
  BV *x;
  ST *V = NULL;
  int l,i,sl,n,mc;
  
  read_header(hdrfile);
  
  if ( (xs = (char *) malloc(sizeof(char)*MAX_LENGTH)) == NULL ) out_of_mem();
  sl = vec_len;
  n = 0;
  mc = 0;
  while (!feof(f)) {
    read_line(f,xs,MAX_LENGTH);
    if (!feof(f)) {
      n++;
      /* allocate space for new vector */
      x = bv_allocate();
      x->num = n;
      /* file empty spaces */
      l = strlen(&xs[vec_offs])+1;
      if (l < sl) {
	for (i=l;i<sl;i++) xs[vec_offs+l-1] = ' ';
	xs[vec_offs+sl-1] = 0;
      }
      x->length = sl;
      /* convert string to vector */
      pic_read_bv(x,xs);
      x->dist = 0.0;
      x->hdist = 0;
      if (check_input_set) {
	if (!vector_in_set(x,V)) mc++;
	if (is_in_set(x,V)) fprintf(stderr,"\nWARNING: Identifier conflict: %s!",x->strain);
      }
      V = add_element(V,x);
    }
  }
  free(xs);
  if (check_input_set) maximum_class_number = mc;
  else maximum_class_number = n;

  log2_factorials = prepare_log2_factorials((n+n));

  return V;
}

int size (ST *V) {
  /* number of elements of V */
  int s;
  ST *W;
  
  if (no_elements(V)) return 0;
  W = V;
  s = 0;
  while (elements_left(W)) {
    W = next_element(W);
    s++;
  }
  /* s = V->size; */
  return s;
}

void coin_tosh (FILE *o, ST *V, char *misfile) {
  const char *func = "coin_tosh";
  ST *h;
  double *mf = NULL;
  int s,l,ms,i;
  FILE *m = NULL;
  
  h = V;
  l = h->el->length;
  s = size(V);
  if (module == MOD_CLASSIFY) {
    if ( (total_freqs = (double *) malloc(sizeof(double)*(l+1))) == NULL ) out_of_mem();
    for (i=1;i<(l+1);i++) total_freqs[i] = 0.0;
  }
  if (analyse_missing) {
    if ((m = fopen(misfile,"w")) == NULL) file_error(misfile,(char *)func);
    if ( (mf = (double *) malloc(sizeof(double)*(l+1))) == NULL ) out_of_mem();
    for (i=1;i<(l+1);i++) mf[i] = 0.0;
  }
  while (elements_left(h)) {
    if (analyse_missing) {
      ms = 0;
      for (i=1;i<l;i++) {
	if (h->el->el[i] == 2) {
	  h->el->miss[i] = 1;
	  mf[i] += 1.0;
	  ms++;
	}
      }
      pic_write_ms(m,ms,h->el);
    }
    for (i=1;i<l;i++) {
      if (h->el->el[i] == 2 ) h->el->el[i] = (give_true_random() < 0.5) ? FALSE : TRUE;
      if ((module == MOD_CLASSIFY) && (h->el->el[i])) total_freqs[i] += 1.0;
    }
    h = next_element(h);
  }
  fclose(m);
  if (analyse_missing) {
    fprintf(o,"MISSING\n");
    for (i=1;i<l;i++) fprintf(o,"%d:%d:%1.5f\n",i,(int)mf[i],(mf[i]/(double)s));
    fprintf(o,"/MISSING\n");
    free(mf);
  }
}

void coin_tosh_silent (ST *V) {
  ST *h;
  int i,l;
  
  if (module == MOD_CLASSIFY) {
    if ( (total_freqs = (double *) malloc(sizeof(double)*MAX_LENGTH)) == NULL ) out_of_mem();
    for (i=1;i<MAX_LENGTH;i++) total_freqs[i] = 0.0;
  }
  h = V;
  l = h->el->length;
  while (elements_left(h)) {
    for (i=1;i<l;i++) {
      if (h->el->el[i] == 2 ) h->el->el[i] = (give_true_random() < 0.5) ? FALSE : TRUE;
      if ((module == MOD_CLASSIFY) && (h->el->el[i])) total_freqs[i] += 1.0;
    }
    h = next_element(h);
  }
}

void write_set (FILE *f, ST *V) {
  /* write V to outout */
  const char *func = "write_set";
  ST *h;
  
  if (V == NULL) internal_error((char *)func);
  if (f == NULL) internal_error((char *)func);
  h = V;
  while (elements_left(h)) {
    pic_write_bv(f,get_element(h));
    h = next_element(h);
  }
}

void emp_write_set (FILE *f, ST *V, int no_print_cl) {
  /* write V to outout */
  const char *func = "emp_write_set";
  ST *h;
  
  if (V == NULL) internal_error((char *)func);
  if (f == NULL) internal_error((char *)func);
  h = V;
  while (elements_left(h)) {
    emp_write_bv(f,get_element(h),no_print_cl);
    h = next_element(h);
  }
}

ST *partition_to_set (Partition *P) {
  /* Put elements of P together again */
  const char *func = "partition_to_set";
  int i,k,si;
  BV *x;
  ST *V;
  
  if (P == NULL) internal_error((char *)func);
  V = NULL;
  k = P->k;
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++) {
    /* Do over all partitions */
    while (elements_left(P->el[i])) {
      x = get_element(P->el[i]);
      P->el[i] = del_element(P->el[i]);
      x->dist = 0.0;
      x->hdist = 0;
      V = add_element(V,x);
    }
  }
  return V;
}

ST *copy_to_set_in_alpha (Partition *P) {
  /* Put elements of P together again */
  const char *func = "copy_to_set_in_alpha";
  int i,k,si;
  BV *x;
  ST *V;
  ST *tmp;
  
  if (P == NULL) internal_error((char *)func);
  V = NULL;
  k = P->k;
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++) {
    /* Do over all partitions */
    tmp = P->el[i];
    while (elements_left(tmp)) {
      x = get_element(tmp);
      x->dist = 0.0;
      x->hdist = 0;
      V = add_element_in_alpha(V,x);
      tmp = next_element(tmp);
    }
  }
  return V;
}

BV *absolute_worst_match (Partition *P, InfCentroid *C) {
  const char *func = "absolute_worst_match";
  int k,i,dist,d;
  int wi = 0;
  BV *x;
  BV *wx;
  ST *V;
  ST *Vp;
  ST *wV = NULL;
  ST *wVp = NULL;
  
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  dist = 0;
  wx = NULL;
  for (i=1;i<k;i++) {
    V = P->el[i];
    Vp = NULL;
    if ((elements_left(V)) && ((size(V)) > 1)) {
      while (elements_left(V)) {
	x = get_element(V);
	d = x->hdist;
	if (d > dist) {
	  dist = d;
	  wx = x;
	  wV = V;
	  wVp = Vp;
	  wi = i;
	}
	Vp = V;
	V = next_element(V);
      }
    }
  }
  if (wx == NULL) internal_error((char *)func);
  wx->hdist = 0;
  if (wVp == NULL) {
    P->el[wi] = del_element(P->el[wi]);
  } else {
    wVp->next = next_element(wV);
    free(wV);
  }
  return wx;
}

void do_remove_them (Partition *P, InfCentroid *C) {
  /* Remove empty sets */
  const char *func = "do_remove_them";
  int k,i,j;
  Centroid *t;
  
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k; /* no empty ones sofar */
  i = 1;
  while (i < k) {
    if ((P->el[i]) == NULL) {
      k = k - 1;
      t = C->el[i];
      for (j=i;j<k;j++) {
	C->el[j] = C->el[j+1];
	P->el[j] = P->el[j+1];
      }
      C->el[k] = t;
      P->el[k] = NULL;
    } else {
      i++;
    }
  }
  C->k = k;
  P->k = k;
}

BV *worst_match (Partition *P, InfCentroid *C, int *c) {
  const char *func = "worst_match";
  int k,i,wi,dist,d;
  double cdist,new_cdist;
  BV *x;
  BV *wx;
  ST *V;
  ST *Vp;
  ST *wV = NULL;
  ST *wVp = NULL;
  
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  cdist = 0.0;
  /* search for most inconsistent class */
  wi = k;
  for (i=1;i<k;i++) {
    if (((P->el[i]) != NULL) && (size(P->el[i]) > 1)) {
      new_cdist = class_distortion(P->el[i],C->el[i]);
      if (new_cdist > cdist) {
	cdist = new_cdist;
	wi = i;
      }
    }
  }
  if (wi == k) internal_error((char *)func);
  *c = wi;

  V = P->el[wi];
  if (V == NULL) internal_error((char *)func);
  
  Vp = NULL;
  wx = NULL;
  
  /* search for worst match in the class */
  dist = 0;
  while (elements_left(V)) {
    x = get_element(V);
    d = x->hdist;
    if (d > dist) {
      dist = d;
      wx = x;
      wV = V;
      wVp = Vp;
    }
    Vp = V;
    V = next_element(V);
  }

  if (wx == NULL) internal_error((char *)func);
  wx->hdist = 0;
  if (wVp == NULL) {
    P->el[wi] = del_element(P->el[wi]);
  } else {
    wVp->next = next_element(wV);
    wV->el = NULL;
    free(wV);
  }

  return wx;
}

BV *worst_match2 (Partition *P, InfCentroid *C, int *c) {
  const char *func = "worst_match";
  int k,i,s,wi;
  double cdist,new_cdist;
  BV *wx;
  ST *V;
  ST *Vp;
  
  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  cdist = 0.0;
  /* search for most inconsistent class */
  wi = k;
  for (i=1;i<k;i++) {
    if (((P->el[i]) != NULL) && (size(P->el[i]) > 1)) {
      new_cdist = class_distortion(P->el[i],C->el[i]);
      if (new_cdist > cdist) {
	cdist = new_cdist;
	wi = i;
      }
    }
  }
  if (wi == k) internal_error((char *)func);
  *c = wi;

  V = P->el[wi];
  if (V == NULL) internal_error((char *)func);
  
  Vp = NULL;
  wx = NULL;

  /* draw a new centroid */
  s = size(P->el[wi]);
  i = random_index(s);
  wx = get_vector_i(P->el[wi],i);
  P->el[wi] = del_vector_i(P->el[wi],i);

  return wx;
}


void inf_remove_empty (Partition *P, InfCentroid *C, int s) {
  /* Remove empty sets */
  const char *func = "inf_remove_empty";
  int k,i,j,l,c;
  Centroid *t;
  BV *x;

  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k;
  i = 1;
  /* check all classes */
  while (i < k) {
    /* do empty cell fix if there is no elements in class */
    if (no_elements(P->el[i])) {
      /* this branch is actually newer used (ie. remove_empty_sets is always FALSE */
      /* check the else part */
      if (remove_empty_sets) {
	k = k - 1;
	t = C->el[i];
	for (j=i;j<k;j++) {
	  C->el[j] = C->el[j+1];
	  P->el[j] = P->el[j+1];
	}
	C->el[k] = t;
	P->el[k] = NULL;
      /* empty cell fix */
      } else {
	/* let x be worst matching vector of the classification */
	x = (alternate_worst_match) ? worst_match2(P,C,&c) : worst_match(P,C,&c);
	if (x == NULL) internal_error((char *)func);
	P->el[i] = add_element(P->el[i],x);
	t = C->el[i];
	l = x->length;
	/* fix the centroid */
	for (j=1;j<l;j++) {
	  t->el[j] = (1.0 + (double) (x->el[j])) / 3.0;
	  t->log0[j] = log_2(t->el[j]);
	  t->log1[j] = log_2(1.0-(t->el[j]));
	}
	if (alternate_empty_cell_fix || use_class_weights) {
	  local_repartition(c,P,C);
	}
      }
    } else {
      i++;
    }
  }
  C->k = k;
  P->k = k;
  if (alternate_empty_cell_fix || use_class_weights) {
    for (i=1;i<k;i++) {
      inf_average(P->el[i],C->el[i],rounded_centroids,s);
    }
  }
}

void remove_empty (Partition *P, InfCentroid *C) {
  /* Remove empty sets */
  const char *func = "inf_remove_empty";
  int k,i,j,l,c;
  Centroid *t;
  BV *x;

  if (P == NULL) internal_error((char *)func);
  if (C == NULL) internal_error((char *)func);
  
  k = C->k; /* no empty ones sofar */
  i = 1;
  while (i < k) {
    if (no_elements(P->el[i])) {
      if (remove_empty_sets) {
	k = k - 1;
	t = C->el[i];
	for (j=i;j<k;j++) {
	  C->el[j] = C->el[j+1];
	  P->el[j] = P->el[j+1];
	}
	C->el[k] = t;
	P->el[k] = NULL;
      } else {
	x = (alternate_worst_match) ? worst_match2(P,C,&c) : worst_match(P,C,&c);
	if (x == NULL) internal_error((char *)func);
	P->el[i] = add_element(P->el[i],x);
	t = C->el[i];
	l = x->length;
	for (j=1;j<l;j++) t->el[j] = (1.0 + (double) (x->el[j])) / 3.0;
	if (alternate_empty_cell_fix) {
	  local_repartition(c,P,C);
	}
      }
    } else {
      i++;
    }
  }
  C->k = k;
  P->k = k;
}


void inf_write_partition (FILE *f, Partition *P) {
  /* write partition in descending order by size */
  const char *func = "inf_write_partition";
  int i,j,si,k,x,y;
  ST *t; 
  IntVector *I;
  
  if (P == NULL) internal_error((char *)func);
  k = P->k;
  /* ever heard about bubblesort */
  I = allocate_ivector(k-1);
  for (i=1;i<k;i++) I->el[i] = size(P->el[i]);
  for (i=1;i<(k-1);i++) {
    for (j=(i+1);j<k;j++) {
      x = I->el[i];
      y = I->el[j];
      if (x < y) {
	I->el[j] = x;
	I->el[i] = y;
	t = P->el[j];
	P->el[j] = P->el[i];
	P->el[i] = t;
      }
    }
  }
  deallocate_ivector(I);
  /* ok now save */
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++) {
    if (elements_left(P->el[i])) {
      if (i == 0) fprintf(f,"Class (Trash)\n");
      else fprintf(f,"Class %d\n",i);
      write_set(f,P->el[i]);
    }
  }
}

void inf_write_partition_po_delta (FILE *f, Partition *P, int delta) {
  /* write partition in order where it is */
  const char *func = "inf_write_partition_po";
  int i,si,k;
  
  if (P == NULL) internal_error((char *)func);
  k = P->k;
  fprintf(f,"Delta %d\n",delta);
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++) {
    if (elements_left(P->el[i])) {
      if (i == 0) fprintf(f,"Class (Trash)\n");
      else fprintf(f,"Class %d\n",i);
      write_set(f,P->el[i]);
    }
  }
}

void inf_write_partition_po (FILE *f, Partition *P) {
  /* write partition in order where it is */
  const char *func = "inf_write_partition_po";
  int i,si,k;
  
  if (P == NULL) internal_error((char *)func);
  k = P->k;
  si = (trashcan) ? 0 : 1;
  for (i=si;i<k;i++) {
    if (elements_left(P->el[i])) {
      if (i == 0) fprintf(f,"Class (Trash)\n");
      else fprintf(f,"Class %d\n",i);
      write_set(f,P->el[i]);
    }
  }
}

void sort_partition (char *hdrfile, char *parfile1, char *parfile2) {
  const char *func = "sort_partition";
  FILE *f;
  Partition *P;

  read_header(hdrfile);

  /* read it */
  if ((f = fopen(parfile1,"r")) == NULL) file_error(parfile1,(char *)func);
  P = read_partition(f,FALSE);
  fclose(f);

  /* save it, routine sorts it automatically */
  if ((f = fopen(parfile2,"w")) == NULL) file_error(parfile2,(char *)func);
  inf_write_partition(f,P);
  fclose(f);

}

Partition *read_partition (FILE *f, int do_sort) {
  char *s;
  char buf[MAX_LENGTH];
  char *t;
  int c;
  Partition *P;
  BV *x;
  ST *V;
  int k;
  const char *func = "read_partition";
  const char *es = "No classes in partition file";
  const char *clas_id = "Class";
  const char *tclas_id = "Class (Trash)";
  const int VEC_OFFS = vec_offs;
  const int VEC_LEN = vec_len;
  
  s = (char *) &buf;
  c = 0;
  k = 0;
  /* Scanning for number of partitions */
  if (verbose) fprintf(stdout,"Scanning partitions ..");
  while (!feof(f)) {
    read_line(f,s,MAX_LENGTH);
    t = (char *) malloc(6);
    strncpy(t,s,5);
    t[5] = '\0';
    if (strcmp(t,clas_id) == 0) {
      k++;
      free(t);
      t = (char *) malloc(14);
      strncpy(t,s,13);
      t[13] = '\0';
      if (strcmp(t,tclas_id) == 0) k = 0;
      free(t);
    }
  }
  if (k == 0) stop_error((char *)es,(char *)func);
  rewind(f);
  if (verbose) fprintf(stdout,".. found %d, reading ",k);
  k++;
  
  /* Allocating space for parition */
  P = allocate_partition(k);
  
  /* Read partition */
  while (!feof(f)) {
    read_line(f,s,MAX_LENGTH);
    t = (char *) malloc(6);
    strncpy(t,s,5);
    t[5] = '\0';
    if (strcmp(t,clas_id) == 0) {
      c++;
      free(t);
      t = (char *) malloc(14);
      strncpy(t,s,13);
      t[13] = '\0';
      if (strcmp(t,tclas_id) == 0) c = 0;
      free(t);
      put_dot;
    } else
      /* if ((strlen(s) >= (VEC_OFFS+VEC_LEN-1)) && (buf[4] == ' ') && (buf[0] != ' ')) { */
      if ((strlen(s) >= (unsigned int)(VEC_OFFS+VEC_LEN-1)) && (buf[0] != ' ')) {
	free(t);
	/* We have a vector */
	x = bv_allocate();
	pic_read_bv(x,s);
	V = P->el[c];
	V = (do_sort) ? add_element_in_alpha(V,x) : add_element(V,x);
	P->el[c] = V;
      }
  }
  if (verbose) {
    fprintf(stdout," ok\n");
    fflush(stdout);
  }
  return P;
}

/* end of binset.c */
