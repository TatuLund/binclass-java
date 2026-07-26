#include <sys/types.h>
#include <stdlib.h>
#include <string.h>

#include "vars.h"
#include "bottom.h"

void log_profile (FILE *o, int l, int s);
void log_function (FILE *o, double *scs, int lastk);

void log_profile (FILE *o, int l, int s) {
  int i;
  double p;
  
  fprintf(o,"\n\nStatistical profile:\n");
  for (i=1;i<l;i++) {
    p = (total_freqs[i]/(double)s);
    fprintf(o,"%4d: %4d, %1.5f\n",i,((int)total_freqs[i]),p);
    total_freqs[i] = p;
  }
  fprintf(o,"--\n\n");
}

void log_function (FILE *o, double *scs, int lastk) {
  int i;
  
  for (i=0;i<(lastk+1);i++) {
    if (scs[i] < unassigned_sc()) fprintf(o,"%4d: %1.5f\n",i,scs[i]);
  }
}
/* end of logfile.c */

