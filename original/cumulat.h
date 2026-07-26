
extern int delta_value (int k);
extern DynPartition *dp_initialize (BV *x);
extern ST *dp_read_set (FILE *f, char *hdrfile);
extern ST *dp_redraw (ST *V);
extern void dp_extend (DynPartition *P, BV *x);
extern void dp_put_vector (DynPartition *P, BV *x, int i);
extern Partition *dp_convert (DynPartition *DP);
extern double dp_predictive_fit (DynPartition *P);
extern double predictive_fit (Partition *P);
extern void do_cumulative_classification (char *datfile, char* basfile, char* outfile, char *parfile, char *hdrfile);
extern void analyse_cumulative (char *datfile, char *basfile, char *cmpfile, char *outfile, char *parfile1, char *parfile2, char *ordfile1, char *ordfile2, char *hdrfile);
extern void reidentification_analysis  (char *datfile, char *outfile, char *hdrfile);

/* end of cumulat.h */
