
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "const.h"
#include "bottom.h"
#include "vars.h"

/* prototypes */

#define read_bit(c) ((c == ' ') ? ((give_true_random() > 0.5) ? FALSE : TRUE) : (c != '0'))

void write_bold (FILE *f, int c);
void pic_write_bv (FILE *f, BV *x);
void emp_write_bv (FILE *f, BV *x, int no_print_cl);
void pic_write_ms (FILE *f, int i, BV *x);
void pic_read_bv (BV *x, char *xs);

void bv_deallocate (BV *x);
BV *bv_allocate (void);
BV *bv_copy (BV *x);
void bv_set_name (BV *x, char *s);
void bv_set_id (BV *x, char *s);
int bv_dist (BV *x, BV *y);

/* implementation */

const int overstrike = FALSE;
const int ansibold = FALSE;

void write_bold (FILE *f, int c) {
  char ch;
  
  if (c == 1) ch = '1';
  else ch = '0';
  if (overstrike) {
  }
  else if (ansibold) {
  }
  else {
    if (ch == '1') fputc('#',f);
    else fputc('.',f);
  }
}

void pic_write_bv (FILE *f, BV *x) {
  /*write to output BV in picture format*/
  int j,l;

  if (x != NULL) {
    l = x->length;
    fprintf(f,"%s",x->clasname);
    for (j=name_len;j<id_offs;j++) fputc(' ',f);
    fprintf(f,"%s",x->strain);
    for (j=(id_offs+id_len);j<vec_offs;j++) fputc(' ',f);
    for (j=1;j<l;j++) {
      if ((x->el[j]) == 0) fputc('0',f);
      else fputc('1',f);
    }
    fprintf(f,"\n");
  }
}

void emp_write_bv (FILE *f, BV *x, int no_print_cl) {
  /*write to output BV in picture format*/
  int j,i,l;
  int lst = 0;

  l = x->length;
  fprintf(f,"%s",x->clasname);
  for (j=name_len;j<id_offs;j++) fputc(' ',f);
  fprintf(f,"%s",x->strain);
  for (j=(id_offs+id_len);j<vec_offs;j++) fputc(' ',f);
  for (j=1;j<l;j++) {
    if (x->miss[j]) {
      write_bold(f,x->el[j]);
    } else {
      if ((x->el[j]) == 0) fputc('0',f);
      else fputc('1',f);
    }
/*    lst = ((vec_offs + j + 10) % 80);
    if ((lst == 0) && (j != (l-1))) {
      fprintf(f,"\n");
      for (i=0;i<vec_offs;i++) fputc(' ',f);
    } */
  } 
  if (lst != 0) for (i=lst;i<59;i++) fputc(' ',f);
  if (no_print_cl) fprintf(f," %2d\n",x->hdist);
  else fprintf(f," %2d %1.2f\n",x->hdist,x->dist);
}

void pic_write_ms (FILE *f, int i, BV *x) {
  /*write to output BV's missing element positions */
  int j,l;
  
  l = x->length;
  fprintf(f,"%s",x->clasname);
  fprintf(f,"      ");
  fprintf(f,"%s",x->strain);
  fprintf(f,"  [%2d] ",i);
  for (j=1;j<l;j++) if (x->el[j] == 2) fprintf(f,"%2d ",j);
  fprintf(f,"\n");
}

void pic_read_bv (BV *x, char *xs) {
  /*read BV in picture form*/
#ifdef _MY_DEBUG  
  const char *func = "pic_read_bv";
#endif
  int i,l;
#ifdef _MY_DEBUG  
  if (x == NULL) internal_error((char *)func);
#endif

  /* Copy name of the entry */
  bv_set_name(x,xs);
  
  /* Copy ID of the entry */
  bv_set_id(x,&xs[id_offs]);
  
  /* Convert ASCII data to integers */
  /* l = strlen(&xs[offs])+1; */
  /* l should be parametrized */
  l = vec_len;
  x->length = l;
  for (i=0;i<(l-1);i++) x->el[i+1] = (xs[i+vec_offs] == ' ') ? 2 : (int)(xs[i+vec_offs] != '0');
}

void bv_set_name (BV *x, char *s) {
  strncpy(x->clasname,s,name_len);
  x->clasname[name_len] = '\0';
}

void bv_set_id (BV *x, char *s) {
  strncpy(x->strain,s,id_len);
  x->strain[id_len] = '\0';
}

void bv_deallocate (BV *x) {
  if (x != NULL) {
    if (x->el != NULL) free(x->el);
    if (x->miss != NULL) free(x->miss);
    if (x->strain != NULL) free(x->strain);
    if (x->clasname != NULL) free(x->clasname);
    free(x);
  }
}

BV *bv_allocate (void) {
  BV *x;
  int i;

  /* allocate space for new vector */
  if ( (x = (BV *) malloc(sizeof(BV))) == NULL ) out_of_mem();
  if ( (x->el = (int *) malloc(sizeof(int)*vec_len)) == NULL ) out_of_mem();
  if ( (x->miss = (int *) malloc(sizeof(int)*vec_len)) == NULL ) out_of_mem();
  if ( (x->clasname = (char *) malloc(sizeof(char)*(name_len+1))) == NULL) out_of_mem();
  if ( (x->strain = (char *) malloc(sizeof(char)*(id_len+1))) == NULL) out_of_mem();
  for (i=0;i<vec_len;i++) x->miss[i] = 0;
  x->length = vec_len;
  x->dist = 0.0;
  x->hdist = 0;
  x->num = 0;

  return x;
}

BV *bv_copy (BV *x) {
  BV *y;
  int l,i;
  
  y = bv_allocate();
  l = x->length;
  for (i=1;i<l;i++) {
    y->el[i] = x->el[i];
    y->miss[i] = x->miss[i];
  }
  strcpy(y->clasname,x->clasname);
  strcpy(y->strain,x->strain);
  y->hdist = x->hdist;
  y->dist = x->dist;
  y->num = x->num;
  return y;
}

int bv_dist (BV *x, BV *y) {
  int l,i,d;
  int *ex;
  int *ey;

  ex = x->el;
  ey = y->el;
  d = 0;
  l = vec_len-1;
  for (i=1;i<l;i+=2) {
    d += (ex[i] != ey[i]);
    d += (ex[i+1] != ey[i+1]);
  }
  if (l % 2) d += (ex[l] != ey[l]);
  return d;
}

/*End of binstuff.c*/

