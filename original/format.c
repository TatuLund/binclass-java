/*
Functions for file format interpretation and formated output.
*/

#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "bottom.h"
#include "vars.h"

void read_header (char *hdrfile);

void read_header_old (char *hdrfile) {
  const char *func = "read_header";
  char *s;
  FILE *f;
  
  if ((f = fopen(hdrfile,"r")) != NULL) {
    if ((s = (char *) malloc (20*sizeof(char))) == NULL) out_of_mem();
    /* get length of vector */
    read_line(f,s,20);
    vec_len = atoi(s)+1;
    /* get offset of vector */
    read_line(f,s,20);
    vec_offs = atoi(s);
    free(s);
    fclose(f);
  } else {
    file_error(hdrfile,(char *)func);
  }
}

void parse_hdr_str (char *s, char *a, char *v) {
  const char *func = "parse_hdr_str";
  char *p;
  int n;
  
  p = strchr(s,(int)'=');
  if (p == NULL) internal_error((char *)func);
  n = p-s;
  strncpy(a,s,n);
  a[n] = 0;
  strncpy(v,&s[n+1],(strlen(s)-n));
  v[strlen(s)-n] = 0;
}

char conv_hex(char c) {
  switch (c) {
  case '0' : return 0;
  case '1' : return 1;
  case '2' : return 2;
  case '3' : return 3;
  case '4' : return 4;
  case '5' : return 5;
  case '6' : return 6;
  case '7' : return 7;
  case '8' : return 8;
  case '9' : return 9;
  case 'A' : return 10;
  case 'B' : return 11;
  case 'C' : return 12;
  case 'D' : return 13;
  case 'E' : return 14;
  case 'F' : return 15;
  case '#' : return 16;
  default : return 0;
  }
}

void header_error (void) {
  /*quit the program and display the message s*/
  fprintf(stderr,"\nERROR: File format header file invalid!\n");
  exit(1);
}

void read_header (char *hdrfile) {
  /* This tries to be a robust header file parser	*/
  const char *func = "read_header";
  char *s;
  char *a;
  char *v;
  int i;
  FILE *f;
  
  id_len = -1;
  id_offs = -1;
  id_ord = NULL;
  name_len = -1;
  vec_offs = -1;
  vec_len = -1;
  if ((f = fopen(hdrfile,"r")) != NULL) {
    if ((s = (char *) malloc (255*sizeof(char))) == NULL) out_of_mem();
    if ((a = (char *) malloc (255*sizeof(char))) == NULL) out_of_mem();
    if ((v = (char *) malloc (255*sizeof(char))) == NULL) out_of_mem();
    while (!feof(f)) {
      read_line(f,s,255);
      if (!feof(f)) {
	parse_hdr_str(s,a,v);
	/* fprintf(stdout,":%s:%s:\n",a,v); */
	if (strcmp(a,"veclen") == 0) {
	  if (vec_len != -1) header_error();
	  vec_len = atoi(v)+1;
	} else if (strcmp(a,"vecoffs") == 0) {
	  if (vec_offs != -1) header_error();
	  vec_offs = atoi(v);
	} else if (strcmp(a,"namelen") == 0) {
	  if (name_len != -1) header_error();
	  name_len = atoi(v);
	} else if (strcmp(a,"idlen") == 0) {
	  if (id_len != -1) header_error();
	  id_len = atoi(v);
	  if (id_len > 50) header_error();
	} else if (strcmp(a,"idoffs") == 0) {
	  if (id_offs != -1) header_error();
	  id_offs = atoi(v);
	} else if (strcmp(a,"idord") == 0) {
	  if (id_len == -1) header_error();
	  id_ord = strdup(v);
	  for (i=0;i<id_len;i++) id_ord[i] = conv_hex(id_ord[i]);
	} else {
	  header_error();
	}
      }
    }
    free(s);
    free(a);
    free(v);
    fclose(f);
    if (id_len < 0) header_error();
    if (name_len < 0) header_error();
    if (vec_len < 0) header_error();
    if (id_offs < 0) header_error();
    if (vec_offs < 0) header_error();
    if (id_ord == NULL) header_error();
  } else {
    file_error(hdrfile,(char *)func);
  }
  if (id_offs < name_len) header_error();
  if (vec_offs < (id_offs+id_len) ) header_error();
  if (strlen(id_ord) != (unsigned int)id_len) header_error();
}

/* end of format.c */
