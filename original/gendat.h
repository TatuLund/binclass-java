
extern void bernouli_generator (char *parfile, char *genfile, int amount, char *hdrfile);
extern ST *bernouli_gen (Partition *P, int amount);

extern void markov_generator (char *datfile, char *genfile, int amount, char *hdrfile);
extern ST *markov_gen (ST *V, int amount);

extern void vector_generator (char *datfile, char *genfile1, char *genfile2, int amount, char *hdrfile);
extern ST *vector_gen (ST *V, int amount);

extern void random_generator (char *genfile, int amount, char *hdrfile);
extern ST *random_gen (int amount);

/* end of gendat.h */

