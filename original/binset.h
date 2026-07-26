/*
Definitions of functions in module binseti.c
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define get_element(V) (V->el)
#define get_vector(V) (V->el->el)
#define next_element(V) (V->next)
#define no_elements(V) (V == NULL)
#define elements_left(V) (V != NULL)
#define vector_component(j) (V->el->el[j])

extern ST *join_class (ST *V1, ST *V2);
extern void deallocate_set (ST *V);
extern ST *empty_set (ST *V);
extern ST *add_element (ST *V, BV *x);
extern ST *add_element_in_alpha (ST *V, BV *x);
extern ST *add_element_in_order (ST *V, BV *x);
extern ST *add_element_tail (ST *V, BV *x);
extern ST *del_element (ST *V);
extern BV *get_vector_i (ST *V, int ind);
extern BV *copy_vector_i (ST *V, int ind);
extern ST *del_vector_i (ST *V, int ind);
extern int is_in_set (BV *x, ST *V);
extern ST *read_set (FILE *f, char *hdrfile);
extern ST *copy_set (ST *V);
extern ST *copy_set_fast (ST *V);
extern ST *copy_set_in_alpha (ST *V);
extern void coin_tosh (FILE *o, ST *V, char *misfile);
extern void coin_tosh_silent (ST *V);
extern void write_set (FILE *f, ST *V);
extern void emp_write_set (FILE *f, ST *V, int no_print_cl);
extern int size (ST *V);
extern void write_vector (FILE *f, Vector *x);
extern ST *partition_to_set (Partition *P);
extern ST *copy_to_set_in_alpha (Partition *P);
extern void inf_write_partition (FILE *f, Partition *P);
extern void inf_write_partition_po_delta (FILE *f, Partition *P, int delta);
extern void inf_write_partition_po (FILE *f, Partition *P);
extern void inf_remove_empty (Partition *P, InfCentroid *C, int s);
extern void remove_empty (Partition *P, InfCentroid *C);
extern Partition *allocate_partition (int k);
extern void deallocate_partition (Partition *P);
extern Partition *copy_partition (Partition *P1);
extern void sort_partition (char *hdrfile, char *parfile1, char *parfile2);
extern Partition *read_partition (FILE *f, int do_sort);

/* End of binseti.h */

