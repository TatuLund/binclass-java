/*
Definitions of routines in binclass.c
*/

#include <stdio.h>

#include "const.h"

extern void pic_write_bv (FILE *f, BV *x);
extern void emp_write_bv (FILE *f, BV *x, int no_print_cl);
extern void pic_write_ms (FILE *f, int i, BV *x);
extern void pic_read_bv (BV *x, char *xs);
extern int distance (BV *x, BV *y);

extern void bv_deallocate (BV *x);
extern BV *bv_allocate (void);
extern BV *bv_copy (BV *x);
extern void bv_set_name (BV *x, char *s);
extern void bv_set_id (BV *x, char *s);
int bv_dist (BV *x, BV *y);

/* End of binstuff.h */

